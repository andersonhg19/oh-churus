import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import DashboardScreen from '../dashboard/DashboardScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { dashboardService } from '../../services/dashboardService';
import { movementService } from '../../services/movementService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: () => (() => void) | void) => {
    const { useEffect } = require('react');
    useEffect(() => { const cleanup = cb(); return typeof cleanup === 'function' ? cleanup : undefined; }, []);
  },
}));

jest.mock('../../services/dashboardService', () => ({
  dashboardService: {
    getSummary: jest.fn(),
    getPending: jest.fn(),
  },
}));

jest.mock('../../services/movementService', () => ({
  movementService: {
    confirm: jest.fn(),
  },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'Anderson', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('DashboardScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (dashboardService.getSummary as jest.Mock).mockResolvedValue({
      correct: true,
      object: { budgetTotal: 5000000, confirmedTotal: 2000000, balance: 3000000, pendingCount: 2, pendingTotal: 500000, periodStart: '2026-03-01', periodEnd: '2026-03-31' },
    });
    (dashboardService.getPending as jest.Mock).mockResolvedValue({
      correct: true,
      object: [
        { id: 'p1', description: 'Arriendo', amount: 1500000, date: '2026-03-01', categoryName: 'Vivienda', categoryType: 'EXPENSE' },
      ],
    });
  });

  it('renders greeting with user name', async () => {
    const { getByText } = render(<DashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(getByText(/Hola, Anderson/)).toBeTruthy();
    });
  });

  it('renders stat cards', async () => {
    const { getByText } = render(<DashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(getByText('Balance')).toBeTruthy();
      expect(getByText('Ingresos')).toBeTruthy();
      expect(getByText('Gastos')).toBeTruthy();
      expect(getByText('Presupuesto')).toBeTruthy();
    });
  });

  it('renders pending movements section', async () => {
    const { getByText } = render(<DashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(getByText('Arriendo')).toBeTruthy();
      expect(getByText('Confirmar')).toBeTruthy();
    });
  });

  it('shows empty state when no pending', async () => {
    (dashboardService.getPending as jest.Mock).mockResolvedValue({ correct: true, object: [] });
    const { getByText } = render(<DashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(getByText('Todo al dia')).toBeTruthy();
    });
  });

  it('handles confirm action', async () => {
    (movementService.confirm as jest.Mock).mockResolvedValue({ correct: true });
    const { getByText } = render(<DashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText('Confirmar')).toBeTruthy());
    fireEvent(getByText('Confirmar'), 'press', { stopPropagation: jest.fn() });
    expect(movementService.confirm).toHaveBeenCalledWith('p1');
  });

  it('shows error on fetch failure', async () => {
    (dashboardService.getSummary as jest.Mock).mockRejectedValue(new Error('fail'));
    const { getByText } = render(<DashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(getByText(/Error al cargar datos/)).toBeTruthy();
    });
  });
});
