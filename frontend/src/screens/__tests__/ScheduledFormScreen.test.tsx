import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import { Alert } from 'react-native';
import ScheduledFormScreen from '../scheduled/ScheduledFormScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';

jest.mock('../../services/scheduledService', () => ({
  scheduledService: { save: jest.fn().mockResolvedValue({ correct: true }) },
}));

jest.mock('../../services/categoryService', () => ({
  categoryService: {
    getAll: jest.fn().mockResolvedValue({ correct: true, object: { list: [
      { id: 'c1', userId: '1', name: 'Salario', type: 'INCOME' },
      { id: 'c2', userId: '1', name: 'Arriendo', type: 'EXPENSE' },
    ] } }),
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

describe('ScheduledFormScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders new scheduled form', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <ScheduledFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText('Nuevo Programado')).toBeTruthy();
    expect(getByText('Guardar')).toBeTruthy();
    expect(getByText('Cancelar')).toBeTruthy();
  });

  it('renders frequency buttons', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <ScheduledFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText('Mensual')).toBeTruthy();
    expect(getByText('Semanal')).toBeTruthy();
    expect(getByText('Anual')).toBeTruthy();
    expect(getByText('Diario')).toBeTruthy();
  });

  it('renders type toggle', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <ScheduledFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText(/Ingreso/)).toBeTruthy();
    expect(getByText(/Gasto/)).toBeTruthy();
  });

  it('shows validation error when name is empty', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <ScheduledFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Guardar'));
    expect((globalThis as any).__mockShowToast).toHaveBeenCalledWith('warning', 'Validacion', expect.any(String));
  });

  it('renders edit mode', () => {
    const scheduled = {
      id: 's1', userId: '1', categoryId: 'c2', categoryType: 'EXPENSE' as const,
      name: 'Arriendo', amount: 1500000, frequency: 'MONTHLY' as const,
      startDate: '2026-01-01',
    };
    const route = { params: { scheduled } } as any;
    const { getByText } = render(
      <ScheduledFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText('Editar Programado')).toBeTruthy();
    expect(getByText('Actualizar')).toBeTruthy();
  });

  it('cancel navigates back', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <ScheduledFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Cancelar'));
    expect(mockNavigation.goBack).toHaveBeenCalled();
  });

  it('allows selecting frequency', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <ScheduledFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Semanal'));
    // Should not crash, frequency is now WEEKLY
    expect(getByText('Semanal')).toBeTruthy();
  });
});
