import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import { Alert } from 'react-native';
import ScheduledScreen from '../scheduled/ScheduledScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { scheduledService } from '../../services/scheduledService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: () => (() => void) | void) => {
    const { useEffect } = require('react');
    useEffect(() => { const cleanup = cb(); return typeof cleanup === 'function' ? cleanup : undefined; }, []);
  },
}));

jest.mock('../../services/scheduledService', () => ({
  scheduledService: {
    getAll: jest.fn(),
    generatePending: jest.fn(),
  },
}));

jest.spyOn(Alert, 'alert');

const mockNavigation = { navigate: jest.fn() } as any;
const mockRoute = { params: {} } as any;

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('ScheduledScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (scheduledService.getAll as jest.Mock).mockResolvedValue({
      correct: true,
      object: { list: [
        { id: '1', userId: '1', categoryId: 'c1', categoryName: 'Vivienda', categoryType: 'EXPENSE', name: 'Arriendo', amount: 1500000, frequency: 'MONTHLY', startDate: '2026-01-01' },
      ] },
    });
  });

  it('renders title', async () => {
    const { getByText } = render(
      <ScheduledScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('Programados')).toBeTruthy());
  });

  it('renders scheduled items', async () => {
    const { getByText } = render(
      <ScheduledScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => {
      expect(getByText('Arriendo')).toBeTruthy();
      expect(getByText(/Mensual/)).toBeTruthy();
    });
  });

  it('renders generate button', async () => {
    const { getByText } = render(
      <ScheduledScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('Generar')).toBeTruthy());
  });

  it('handles generate pending action', async () => {
    (scheduledService.generatePending as jest.Mock).mockResolvedValue({ correct: true, object: 3 });
    const { getByText } = render(
      <ScheduledScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('Generar')).toBeTruthy());
    fireEvent.press(getByText('Generar'));
    await waitFor(() => {
      expect(scheduledService.generatePending).toHaveBeenCalledWith('1', 1);
    });
  });

  it('shows empty state when no scheduled', async () => {
    (scheduledService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [] } });
    const { getByText } = render(
      <ScheduledScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('Sin programados')).toBeTruthy());
  });

  it('muestra el mensaje del backend cuando correct es false', async () => {
    (scheduledService.getAll as jest.Mock).mockResolvedValue({
      correct: false, message: 'No tienes acceso a estos programados', object: null,
    });
    const { getByText, queryByText } = render(
      <ScheduledScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('No tienes acceso a estos programados')).toBeTruthy());
    expect(queryByText('Sin programados')).toBeNull();
  });

  it('shows error on fetch failure', async () => {
    (scheduledService.getAll as jest.Mock).mockRejectedValue(new Error('fail'));
    const { getByText } = render(
      <ScheduledScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText(/Error al cargar datos/)).toBeTruthy());
  });
});
