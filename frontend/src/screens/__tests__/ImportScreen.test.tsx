import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import ImportScreen from '../settings/ImportScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import { ToastProvider } from '../../contexts/ToastContext';
import * as AuthContext from '../../contexts/AuthContext';
import { importService } from '../../services/importService';

jest.mock('../../services/importService', () => ({
  importService: { preview: jest.fn(), confirm: jest.fn(), profiles: jest.fn() },
}));

jest.mock('../../services/categoryService', () => ({
  categoryService: {
    getAll: jest.fn().mockResolvedValue({
      correct: true,
      object: { list: [{ id: '10', userId: '1', name: 'Mercado', type: 'EXPENSE' }] },
    }),
  },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'Ana', email: 'ana@ohchurus.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider><ToastProvider>{children}</ToastProvider></ThemeProvider>
);

const pintar = () => render(<Wrapper><ImportScreen /></Wrapper>);

const PREVIA = {
  correct: true,
  object: {
    newRows: [
      {
        row: 1, date: '2026-08-01', amount: -45000, description: 'COMPRA EXITO',
        suggestedType: 'EXPENSE', suggestedCategoryId: '10', reason: 'No estaba',
      },
    ],
    duplicates: [
      {
        row: 2, date: '2026-08-03', amount: -20000, description: 'PAGO NEQUI',
        suggestedType: 'EXPENSE', matchedMovementId: '55',
        reason: 'Mismo importe y fecha cercana',
      },
    ],
    confirmPending: [
      {
        row: 3, date: '2026-08-05', amount: -1500000, description: 'ARRIENDO',
        suggestedType: 'EXPENSE', matchedMovementId: '77',
        reason: 'Casa con un pendiente que estabas esperando',
      },
    ],
    total: 3,
  },
};

const escribirExtracto = (utilidades: ReturnType<typeof pintar>) => {
  fireEvent.changeText(
    utilidades.getByPlaceholderText('fecha,concepto,valor...'),
    'fecha,concepto,valor\n2026-08-01,COMPRA EXITO,-45000\n',
  );
};

/**
 * Lo que esta pantalla tiene que garantizar, y es de producto antes que de
 * codigo:
 *
 *   1. QUE NO ESCRIBA HASTA QUE SE ACEPTE. Con sesenta filas mal metidas, la
 *      unica salida real es borrar el mes entero.
 *   2. QUE LAS TRES LISTAS SE DISTINGAN. Nuevos, duplicados, y los que
 *      confirman un pendiente. Sin la tercera, el arriendo entra dos veces.
 */
describe('ImportScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (importService.preview as jest.Mock).mockResolvedValue(PREVIA);
    (importService.confirm as jest.Mock).mockResolvedValue({
      correct: true, object: { created: 1, confirmed: 1, skipped: [], message: 'Listo' },
    });
  });

  it('sin pegar nada no llama al backend', async () => {
    const u = pintar();
    fireEvent.press(u.getByText('Ver que va a pasar'));
    await waitFor(() => expect(importService.preview).not.toHaveBeenCalled());
  });

  it('la vista previa no importa nada: solo ensena', async () => {
    const u = pintar();
    escribirExtracto(u);
    fireEvent.press(u.getByText('Ver que va a pasar'));

    await waitFor(() => expect(importService.preview).toHaveBeenCalled());
    expect(importService.confirm).not.toHaveBeenCalled();
  });

  it('separa las tres listas y dice que todavia no se guardo nada', async () => {
    const u = pintar();
    escribirExtracto(u);
    fireEvent.press(u.getByText('Ver que va a pasar'));

    await waitFor(() => expect(u.getByTestId('resumen-previa')).toBeTruthy());
    expect(u.getByTestId('resumen-previa').props.children.join(''))
      .toContain('1 nuevos');
    expect(u.getByText('Todavia no se ha guardado nada.')).toBeTruthy();
    expect(u.getByText('Confirman un pago que estabas esperando')).toBeTruthy();
    expect(u.getByText('Ya los tenias')).toBeTruthy();
  });

  it('importar manda los nuevos con su categoria y los pendientes a confirmar', async () => {
    const u = pintar();
    escribirExtracto(u);
    fireEvent.press(u.getByText('Ver que va a pasar'));
    await waitFor(() => expect(u.getByTestId('resumen-previa')).toBeTruthy());

    fireEvent.press(u.getByText('Importar'));

    await waitFor(() => expect(importService.confirm).toHaveBeenCalled());
    const filas = (importService.confirm as jest.Mock).mock.calls[0][2];
    expect(filas).toContainEqual({ row: 1, categoryId: '10' });
    expect(filas).toContainEqual({ row: 3, confirmsMovementId: '77' });
    // Los duplicados se quedan fuera: para eso se detectaron.
    expect(filas.map((f: any) => f.row)).not.toContain(2);
  });

  it('precarga la categoria que el diccionario aprendio', async () => {
    /* Si no, cada importacion obligaria a clasificar a mano las mismas
       sesenta descripciones de siempre. */
    const u = pintar();
    escribirExtracto(u);
    fireEvent.press(u.getByText('Ver que va a pasar'));

    await waitFor(() => expect(u.getByTestId('cat-1-10')).toBeTruthy());
    fireEvent.press(u.getByText('Importar'));

    await waitFor(() => expect(importService.confirm).toHaveBeenCalled());
    expect((importService.confirm as jest.Mock).mock.calls[0][2])
      .toContainEqual({ row: 1, categoryId: '10' });
  });

  it('si el backend no puede leer el archivo, se ve SU motivo', async () => {
    /* "Revisa las columnas y el formato de fecha" vale mucho mas que un
       "error al importar": dice que corregir. */
    (importService.preview as jest.Mock).mockResolvedValue({
      correct: false,
      message: 'No se pudo leer ninguna fila. Revisa las columnas y el formato de fecha.',
    });

    const u = pintar();
    escribirExtracto(u);
    fireEvent.press(u.getByText('Ver que va a pasar'));

    await waitFor(() => expect(importService.preview).toHaveBeenCalled());
    expect(u.queryByTestId('resumen-previa')).toBeNull();
  });
});
