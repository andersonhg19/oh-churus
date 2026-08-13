import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import MovementFormScreen from '../movements/MovementFormScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { movementService } from '../../services/movementService';
import { householdService } from '../../services/householdService';

jest.mock('../../services/movementService', () => ({
  movementService: {
    save: jest.fn().mockResolvedValue({ correct: true }),
    delete: jest.fn().mockResolvedValue({ correct: true }),
  },
}));

jest.mock('../../services/categoryService', () => ({
  categoryService: {
    getAll: jest.fn().mockResolvedValue({
      correct: true,
      object: { list: [{ id: 'c2', userId: '1', name: 'Restaurantes', type: 'EXPENSE' }] },
    }),
  },
}));

jest.mock('../../services/accountService', () => ({
  accountService: { getAll: jest.fn().mockResolvedValue({ correct: true, object: { list: [], netWorth: 0 } }) },
}));

jest.mock('../../services/householdService', () => ({
  householdService: { getByUser: jest.fn() },
}));

const mockNavigation = { navigate: jest.fn(), goBack: jest.fn() } as any;

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'Ana', email: 'ana@ohchurus.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

const conHogar = () =>
  (householdService.getByUser as jest.Mock).mockResolvedValue({
    correct: true,
    object: [{ householdId: 5, name: 'Casa', role: 'OWNER', memberCount: 2,
               members: [{ userId: 1, role: 'OWNER' }, { userId: 2, role: 'MEMBER' }] }],
  });

const pintar = () =>
  render(<MovementFormScreen navigation={mockNavigation} route={{ params: {} } as any} />,
    { wrapper: Wrapper });

/**
 * El reparto dentro del formulario de anotar un gasto.
 *
 * Dos cosas se defienden aqui, y las dos son de producto antes que de codigo:
 *
 *   1. QUE NO ESTORBE. La inmensa mayoria de los gastos son de una persona.
 *      Si el selector de reparto le sale delante a todo el mundo, anotar un
 *      cafe deja de ser un gesto y pasa a ser un formulario, y la gente deja
 *      de anotar. Por eso empieza apagado y no existe si no hay con quien.
 *   2. QUE YO VAYA SIEMPRE EN EL REPARTO. Repartir "con Bruno" significa entre
 *      Bruno y yo. Si me olvidara de incluirme, el gasto entero seria de
 *      Bruno y mi presupuesto diria que no gaste nada.
 */
describe('MovementFormScreen: el reparto', () => {
  beforeEach(() => jest.clearAllMocks());

  it('sin nucleo familiar el reparto NO aparece: no estorba a quien no lo usa', async () => {
    (householdService.getByUser as jest.Mock).mockResolvedValue({ correct: true, object: [] });

    const { queryByTestId } = pintar();

    await waitFor(() => expect(householdService.getByUser).toHaveBeenCalled());
    expect(queryByTestId('alternar-reparto')).toBeNull();
  });

  it('con nucleo familiar aparece, pero apagado', async () => {
    conHogar();
    const { getByTestId, queryByTestId } = pintar();

    await waitFor(() => expect(getByTestId('alternar-reparto')).toBeTruthy());
    expect(queryByTestId('modo-EQUAL')).toBeNull();
    expect(queryByTestId('persona-2')).toBeNull();
  });

  it('al encenderlo ofrece los cuatro modos y los companeros de hogar', async () => {
    conHogar();
    const { getByTestId } = pintar();

    await waitFor(() => expect(getByTestId('alternar-reparto')).toBeTruthy());
    fireEvent.press(getByTestId('alternar-reparto'));

    expect(getByTestId('modo-EQUAL')).toBeTruthy();
    expect(getByTestId('modo-SHARES')).toBeTruthy();
    expect(getByTestId('modo-PERCENT')).toBeTruthy();
    expect(getByTestId('modo-AMOUNT')).toBeTruthy();
    expect(getByTestId('persona-2')).toBeTruthy();
  });

  it('al guardar, YO voy siempre dentro del reparto', async () => {
    conHogar();
    const { getByTestId, getByText, getByPlaceholderText } = pintar();

    await waitFor(() => expect(getByTestId('alternar-reparto')).toBeTruthy());
    fireEvent.press(getByTestId('alternar-reparto'));
    fireEvent.press(getByTestId('persona-2'));
    fireEvent.changeText(getByPlaceholderText('0'), '120000');
    fireEvent.press(getByText('Restaurantes'));
    fireEvent.press(getByText('Guardar'));

    await waitFor(() => expect(movementService.save).toHaveBeenCalled());

    const enviado = (movementService.save as jest.Mock).mock.calls[0][0];
    expect(enviado.splitMode).toBe('EQUAL');
    expect(enviado.splits.map((s: any) => s.participantId))
      .toEqual(expect.arrayContaining([1, 2]));
    expect(enviado.amount).toBe(120000);
  });

  it('el importe que se manda sigue siendo el TOTAL, no mi parte', async () => {
    /* La mitad de la regla de oro que se decide en el cliente: si el
       formulario mandara ya dividido, la cuenta dejaria de cuadrar con el
       banco. El backend es quien calcula las partes. */
    conHogar();
    const { getByTestId, getByText, getByPlaceholderText } = pintar();

    await waitFor(() => expect(getByTestId('alternar-reparto')).toBeTruthy());
    fireEvent.press(getByTestId('alternar-reparto'));
    fireEvent.press(getByTestId('persona-2'));
    fireEvent.changeText(getByPlaceholderText('0'), '120000');
    fireEvent.press(getByText('Restaurantes'));
    fireEvent.press(getByText('Guardar'));

    await waitFor(() => expect(movementService.save).toHaveBeenCalled());
    expect((movementService.save as jest.Mock).mock.calls[0][0].amount).toBe(120000);
  });

  it('encendido pero sin elegir a nadie no manda reparto', async () => {
    conHogar();
    const { getByTestId, getByText, getByPlaceholderText } = pintar();

    await waitFor(() => expect(getByTestId('alternar-reparto')).toBeTruthy());
    fireEvent.press(getByTestId('alternar-reparto'));
    fireEvent.changeText(getByPlaceholderText('0'), '50000');
    fireEvent.press(getByText('Restaurantes'));
    fireEvent.press(getByText('Guardar'));

    await waitFor(() => expect(movementService.save).toHaveBeenCalled());
    expect((movementService.save as jest.Mock).mock.calls[0][0].splitMode)
      .toBeUndefined();
  });
});
