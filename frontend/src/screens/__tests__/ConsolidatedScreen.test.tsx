import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
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
});
