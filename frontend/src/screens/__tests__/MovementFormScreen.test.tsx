import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { Alert } from 'react-native';
import MovementFormScreen from '../movements/MovementFormScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { movementService } from '../../services/movementService';
import { confirmarUltimaAlerta } from '../../test-utils';

const showToastSpy = (globalThis as any).__mockShowToast as jest.Mock;

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
      object: { list: [
        { id: 'c1', userId: '1', name: 'Salario', type: 'INCOME' },
        { id: 'c2', userId: '1', name: 'Arriendo', type: 'EXPENSE' },
      ] },
    }),
  },
}));

jest.spyOn(Alert, 'alert');

const mockNavigation = { navigate: jest.fn(), goBack: jest.fn() } as any;

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('MovementFormScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders new movement form', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText('MONTO')).toBeTruthy();
    expect(getByText('Guardar')).toBeTruthy();
  });

  it('renders type toggle buttons', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText(/Ingreso/)).toBeTruthy();
    expect(getByText(/Gasto/)).toBeTruthy();
  });

  it('blocks submit and warns when no category selected', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Guardar'));
    expect(showToastSpy).toHaveBeenCalledWith('warning', 'Validacion', expect.any(String));
    expect(movementService.save).not.toHaveBeenCalled();
  });

  it('renders date and note inputs', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText('Fecha')).toBeTruthy();
    expect(getByText('Nota')).toBeTruthy();
  });

  it('renders type toggle and can switch', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText(/Ingreso/));
    expect(getByText(/ingreso/)).toBeTruthy();
  });

  it('renders edit mode correctly', () => {
    const movement = { id: 'm1', userId: '1', categoryId: 'c2', categoryType: 'EXPENSE' as const, amount: 1500000, date: '2026-03-01', confirmed: true, description: 'Test' };
    const route = { params: { movement } } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText('Actualizar')).toBeTruthy();
    expect(getByText('Eliminar')).toBeTruthy();
  });

  it('shows payment status options', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText('Pagado')).toBeTruthy();
    expect(getByText('Por pagar')).toBeTruthy();
  });

  it('can switch payment status to "Por pagar"', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Por pagar'));
    expect(getByText('Por pagar')).toBeTruthy();
  });

  it('saves a movement successfully (happy path)', async () => {
    const route = { params: {} } as any;
    const { getByText, getByPlaceholderText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    // categories load async; EXPENSE is the default type -> "Arriendo" chip
    await waitFor(() => expect(getByText('Arriendo')).toBeTruthy());
    fireEvent.press(getByText('Arriendo'));
    fireEvent.changeText(getByPlaceholderText('0'), '150000');
    fireEvent.press(getByText('Guardar'));
    await waitFor(() => expect(movementService.save).toHaveBeenCalled());
    expect(mockNavigation.goBack).toHaveBeenCalled();
  });

  it('no borra el movimiento hasta que se confirma el dialogo', async () => {
    const movement = { id: 'm1', userId: '1', categoryId: 'c2', categoryType: 'EXPENSE' as const, amount: 1500000, date: '2026-03-01', confirmed: true, description: 'Arriendo' };
    const route = { params: { movement } } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Eliminar'));
    expect(movementService.delete).not.toHaveBeenCalled();
    expect(Alert.alert).toHaveBeenCalled();

    confirmarUltimaAlerta();
    await waitFor(() => expect(movementService.delete).toHaveBeenCalledWith('m1'));
  });

  it('un doble toque en Guardar no crea dos movimientos', async () => {
    const route = { params: {} } as any;
    const { getByText, getByPlaceholderText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('Arriendo')).toBeTruthy());
    fireEvent.press(getByText('Arriendo'));
    fireEvent.changeText(getByPlaceholderText('0'), '150000');

    const guardar = getByText('Guardar');
    fireEvent.press(guardar);
    fireEvent.press(guardar);

    await waitFor(() => expect(movementService.save).toHaveBeenCalled());
    expect(movementService.save).toHaveBeenCalledTimes(1);
  });

  it('shows an error toast when save fails', async () => {
    (movementService.save as jest.Mock).mockResolvedValueOnce({ correct: false, message: 'boom' });
    const route = { params: {} } as any;
    const { getByText, getByPlaceholderText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('Arriendo')).toBeTruthy());
    fireEvent.press(getByText('Arriendo'));
    fireEvent.changeText(getByPlaceholderText('0'), '150000');
    fireEvent.press(getByText('Guardar'));
    await waitFor(() => expect(showToastSpy).toHaveBeenCalledWith('error', 'Error', 'boom'));
  });
});
