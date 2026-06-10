import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import SummaryScreen from '../summary/SummaryScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { dashboardService } from '../../services/dashboardService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/dashboardService', () => ({
  dashboardService: { getSummary: jest.fn(), getByCategory: jest.fn() },
}));
jest.mock('../../services/movementService', () => ({
  movementService: { getAll: jest.fn().mockResolvedValue({ correct: true, object: { list: [] } }) },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const mockNavigation = { navigate: jest.fn(), goBack: jest.fn() } as any;
const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('SummaryScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (dashboardService.getSummary as jest.Mock).mockResolvedValue({
      correct: true, object: { totalIncome: 0, totalExpense: 0, balance: 0, budgetTotal: 0, pendingCount: 0, pendingAmount: 0 },
    });
    (dashboardService.getByCategory as jest.Mock).mockResolvedValue({ correct: true, object: [] });
  });

  it('loads category breakdown on mount', async () => {
    render(<SummaryScreen navigation={mockNavigation} route={{ params: {} } as any} />, { wrapper: Wrapper });
    await waitFor(() => expect(dashboardService.getByCategory).toHaveBeenCalled());
  });
});
