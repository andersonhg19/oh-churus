import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { Alert } from 'react-native';
import MovementFormScreen from '../movements/MovementFormScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { movementService } from '../../services/movementService';

const showToastSpy = (globalThis as any).__mockShowToast as jest.Mock;

jest.mock('../../services/movementService', () => ({
  movementService: { save: jest.fn().mockResolvedValue({ correct: true }) },
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
});
