import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { Alert } from 'react-native';
import ScheduledFormScreen from '../scheduled/ScheduledFormScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { scheduledService } from '../../services/scheduledService';
import { confirmarUltimaAlerta } from '../../test-utils';

jest.mock('../../services/scheduledService', () => ({
  scheduledService: {
    save: jest.fn().mockResolvedValue({ correct: true }),
    delete: jest.fn().mockResolvedValue({ correct: true }),
  },
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

  it('no borra el programado hasta que se confirma el dialogo', async () => {
    const scheduled = {
      id: 's1', userId: '1', categoryId: 'c2', categoryType: 'EXPENSE' as const,
      name: 'Arriendo', amount: 1500000, frequency: 'MONTHLY' as const, startDate: '2026-01-01',
    };
    const route = { params: { scheduled } } as any;
    const { getByText } = render(
      <ScheduledFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Eliminar programado'));
    expect(scheduledService.delete).not.toHaveBeenCalled();

    confirmarUltimaAlerta();
    await waitFor(() => expect(scheduledService.delete).toHaveBeenCalledWith('s1'));
  });

  it('ofrece el patron "el tercer viernes" y la politica de fin de semana', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <ScheduledFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText('Semana del mes (opcional)')).toBeTruthy();
    expect(getByText('Ultima')).toBeTruthy();
    expect(getByText('Dia de la semana (opcional)')).toBeTruthy();
    expect(getByText('Vie')).toBeTruthy();
    expect(getByText('Si cae fin de semana')).toBeTruthy();
    expect(getByText('Viernes antes')).toBeTruthy();
  });

  it('media pareja del patron no se guarda: "el tercero" no dice de que', async () => {
    const route = { params: {} } as any;
    const { getByText, getByPlaceholderText } = render(
      <ScheduledFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText(/Seleccionar categoría/)).toBeTruthy());
    fireEvent.changeText(getByPlaceholderText('Nombre del movimiento'), 'Nomina');
    fireEvent.press(getByText(/Seleccionar categoría/));
    fireEvent.press(getByText(/Arriendo/));
    fireEvent.changeText(getByPlaceholderText('0'), '3000000');

    fireEvent.press(getByText('3a'));
    fireEvent.press(getByText('Guardar'));

    expect((globalThis as any).__mockShowToast).toHaveBeenCalledWith(
      'warning', 'Validacion', expect.stringContaining('tercer viernes'),
    );
    expect(scheduledService.save).not.toHaveBeenCalled();
  });

  it('guarda el patron completo y la politica elegida', async () => {
    const route = { params: {} } as any;
    const { getByText, getByPlaceholderText } = render(
      <ScheduledFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText(/Seleccionar categoría/)).toBeTruthy());
    fireEvent.changeText(getByPlaceholderText('Nombre del movimiento'), 'Nomina');
    fireEvent.press(getByText(/Seleccionar categoría/));
    fireEvent.press(getByText(/Arriendo/));
    fireEvent.changeText(getByPlaceholderText('0'), '3000000');

    fireEvent.press(getByText('3a'));
    fireEvent.press(getByText('Vie'));
    fireEvent.press(getByText('Viernes antes'));
    fireEvent.press(getByText('Guardar'));

    await waitFor(() => expect(scheduledService.save).toHaveBeenCalledWith(
      expect.objectContaining({
        weekOfMonth: 3,
        dayOfWeek: 5,
        weekendPolicy: 'PREVIOUS_BUSINESS_DAY',
      }),
    ));
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
