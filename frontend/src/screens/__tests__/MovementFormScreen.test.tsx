import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { Alert } from 'react-native';
import MovementFormScreen from '../movements/MovementFormScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';

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
    expect(getByText(/Nuevo Movimiento/)).toBeTruthy();
    expect(getByText('Guardar')).toBeTruthy();
    expect(getByText('Cancelar')).toBeTruthy();
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

  it('shows validation error when no category selected', async () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Guardar'));
    expect(Alert.alert).toHaveBeenCalledWith('Validacion', 'Debes seleccionar una categoria');
  });

  it('shows category picker placeholder', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText(/Seleccionar categoría/)).toBeTruthy();
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
    expect(getByText(/Editar Movimiento/)).toBeTruthy();
    expect(getByText('Actualizar')).toBeTruthy();
  });

  it('cancel navigates back', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Cancelar'));
    expect(mockNavigation.goBack).toHaveBeenCalled();
  });

  it('shows note about auto-confirm', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <MovementFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText(/confirman automáticamente/)).toBeTruthy();
  });
});
