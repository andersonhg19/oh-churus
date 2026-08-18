import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import ExportImportScreen from '../settings/ExportImportScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import api from '../../services/api';

jest.mock('../../services/api', () => ({
  __esModule: true,
  default: { post: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

/** Imita el Blob que axios entrega con responseType 'blob'. */
const blobDe = (contenido: string, type: string) => ({
  type,
  text: () => Promise.resolve(contenido),
});

const mockNavigation = { navigate: jest.fn() } as any;

describe('ExportImportScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  /*
   * Esta prueba comprobaba antes que existiera el texto "Importar desde
   * Excel", que era un boton de PROXIMAMENTE: al tocarlo salia un aviso de
   * "esta funcionalidad estara disponible pronto". O sea que verificaba con
   * todo rigor que la promesa incumplida siguiera en su sitio.
   *
   * Ya hay importacion de verdad (ola 3.4), asi que ahora comprueba que el
   * boton LLEVE A ALGUNA PARTE, que es lo que de verdad importa: un boton que
   * no navega es otra vez un placeholder, solo que sin avisar.
   */
  it('el boton de importar lleva a la pantalla de importacion, no a un aviso', () => {
    const { getByText, getByTestId } = render(<ExportImportScreen navigation={mockNavigation} />, { wrapper: Wrapper });
    expect(getByText(/Exportar Reporte Excel/)).toBeTruthy();

    fireEvent.press(getByTestId('ir-a-importar'));
    expect(mockNavigation.navigate).toHaveBeenCalledWith('Import');
  });

  /* Cuando falla la generacion el backend responde 200 con un ResultDTO en
     JSON. Al ser un 200 axios no lanza, asi que sin mirar el tipo de la
     respuesta la pantalla anunciaba "Excel descargado" y descargaba el JSON
     del error renombrado a .xlsx. */
  it('un fallo que llega como JSON se muestra como error, no como descarga correcta', async () => {
    (api.post as jest.Mock).mockResolvedValue({
      data: blobDe(
        JSON.stringify({ correct: false, message: 'No se pudo generar el Excel del periodo', errorCode: 500 }),
        'application/json',
      ),
    });

    const { getByText } = render(<ExportImportScreen navigation={mockNavigation} />, { wrapper: Wrapper });
    fireEvent.press(getByText('Descargar Excel'));

    await waitFor(() =>
      expect((globalThis as any).__mockShowToast).toHaveBeenCalledWith(
        'error',
        'Error',
        'No se pudo generar el Excel del periodo',
      ),
    );
    expect((globalThis as any).__mockShowToast).not.toHaveBeenCalledWith(
      'success',
      'Exportado',
      expect.anything(),
    );
  });

  it('un Excel de verdad no se confunde con un fallo', async () => {
    (api.post as jest.Mock).mockResolvedValue({
      data: blobDe('PK-contenido-binario', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'),
    });

    const { getByText } = render(<ExportImportScreen navigation={mockNavigation} />, { wrapper: Wrapper });
    fireEvent.press(getByText('Descargar Excel'));

    await waitFor(() => expect((globalThis as any).__mockShowToast).toHaveBeenCalled());
    expect((globalThis as any).__mockShowToast).not.toHaveBeenCalledWith(
      'error',
      'Error',
      expect.anything(),
    );
  });
});
