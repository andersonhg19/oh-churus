import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import ConsolidatedScreen from '../consolidated/ConsolidatedScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { dashboardService } from '../../services/dashboardService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/dashboardService', () => ({
  dashboardService: { getConsolidated: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('ConsolidatedScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (dashboardService.getConsolidated as jest.Mock).mockResolvedValue({
      correct: true,
      object: {
        periodStart: '2026-03-01', periodEnd: '2026-03-31',
        shared: { income: 0, expense: 0, transfersToPersonal: 0, balance: 0 },
        personal: { incomeFromTransfer: 0, incomeOther: 0, expense: 0, balance: 0 },
        total: { income: 0, expense: 0, balance: 0, status: 'SUPERAVIT' },
        budget: { totalBudgeted: 0, totalActualExpense: 0, executionPct: 0 },
      },
    });
  });

  it('loads the consolidated report on mount', async () => {
    render(<ConsolidatedScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(dashboardService.getConsolidated).toHaveBeenCalled());
  });

  it('renders metrics, the budget section and switches tabs', async () => {
    (dashboardService.getConsolidated as jest.Mock).mockResolvedValue({
      correct: true,
      object: {
        periodStart: '2026-03-01', periodEnd: '2026-03-31',
        shared: { income: 1000000, expense: 300000, transfersToPersonal: 50000, balance: 650000 },
        personal: { incomeFromTransfer: 50000, incomeOther: 500000, expense: 200000, balance: 350000 },
        total: { income: 1500000, expense: 500000, balance: 1000000, status: 'SUPERAVIT' },
        budget: { totalBudgeted: 400000, totalActualExpense: 300000, executionPct: 75 },
      },
    });

    const { getByText } = render(<ConsolidatedScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText('Ingresos')).toBeTruthy());
    // Total tab (default) -> budget execution section
    expect(getByText('Ejecucion presupuestal')).toBeTruthy();
    // Conjunto tab -> transfers extra card
    fireEvent.press(getByText('Conjunto'));
    await waitFor(() => expect(getByText('Transferencias a personal')).toBeTruthy());
    // Personal tab -> income from transfer extra card
    fireEvent.press(getByText('Personal'));
    await waitFor(() => expect(getByText('Ingreso por transferencia')).toBeTruthy());
  });
});
