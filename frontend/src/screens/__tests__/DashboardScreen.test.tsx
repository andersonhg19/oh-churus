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
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/dashboardService', () => ({
  dashboardService: {
    getSummary: jest.fn(),
    getPending: jest.fn(),
    getSplitSummary: jest.fn(),
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
    (dashboardService.getSplitSummary as jest.Mock).mockResolvedValue({
      correct: true,
      object: {
        personalIncome: 0, personalExpense: 0, personalBalance: 0,
        sharedIncome: 0, sharedExpense: 0, sharedBalance: 0,
        totalIncome: 2000000, totalExpense: 1000000, totalBalance: 1000000,
        personalPendingCount: 0, sharedPendingCount: 0,
      },
    });
  });

  it('renders greeting with user name', async () => {
    const { getByText } = render(<DashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(getByText(/Hola, Anderson/)).toBeTruthy();
    });
  });

  it('renders stat cards', async () => {
    const { getByText, getAllByText } = render(<DashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(getAllByText(/Balance/).length).toBeGreaterThan(0);
      expect(getByText('Ingresos')).toBeTruthy();
      expect(getByText('Gastos')).toBeTruthy();
      expect(getByText('Presupuesto')).toBeTruthy();
      expect(getByText('Pendientes')).toBeTruthy();
    });
  });

  it('renders pending movements section', async () => {
    const { getByText, getAllByText } = render(<DashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(getByText('Arriendo')).toBeTruthy();
      expect(getByText(/Por confirmar/)).toBeTruthy();
      expect(getAllByText('Confirmar').length).toBeGreaterThan(0);
    });
  });

  it('shows empty state when no pending', async () => {
    (dashboardService.getPending as jest.Mock).mockResolvedValue({ correct: true, object: [] });
    const { getByText } = render(<DashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(getByText('Todo al dia')).toBeTruthy();
    });
  });

  it('opens the confirm modal and confirms the movement', async () => {
    (movementService.confirm as jest.Mock).mockResolvedValue({ correct: true });
    const { getByText, getAllByText } = render(<DashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getAllByText('Confirmar').length).toBeGreaterThan(0));
    // Press the inline confirm button -> opens the confirm modal
    const inline = getAllByText('Confirmar');
    fireEvent(inline[inline.length - 1], 'press', { stopPropagation: jest.fn() });
    expect(getByText('Confirmar movimiento')).toBeTruthy();
    // Press the modal confirm button
    const all = getAllByText('Confirmar');
    fireEvent.press(all[all.length - 1]);
    await waitFor(() => expect(movementService.confirm).toHaveBeenCalled());
    expect((movementService.confirm as jest.Mock).mock.calls[0][0]).toBe('p1');
  });

  it('shows error on fetch failure', async () => {
    (dashboardService.getSummary as jest.Mock).mockRejectedValue(new Error('fail'));
    const { getByText } = render(<DashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => {
      expect(getByText(/Error al cargar datos/)).toBeTruthy();
    });
  });
});
