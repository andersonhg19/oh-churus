import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import BudgetScreen from '../budget/BudgetScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { budgetAllocationService } from '../../services/budgetAllocationService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/budgetAllocationService', () => ({
  budgetAllocationService: { list: jest.fn(), summary: jest.fn(), save: jest.fn(), delete: jest.fn() },
}));
jest.mock('../../services/categoryService', () => ({
  categoryService: { getTree: jest.fn().mockResolvedValue({ correct: true, object: [] }) },
}));
jest.mock('../../services/movementService', () => ({
  movementService: { transfer: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('BudgetScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (budgetAllocationService.list as jest.Mock).mockResolvedValue({ correct: true, object: [] });
    (budgetAllocationService.summary as jest.Mock).mockResolvedValue({
      correct: true, object: { totalBudgeted: 0, totalActual: 0, totalVariance: 0, categories: [] },
    });
  });

  it('loads allocations and summary on mount', async () => {
    render(<BudgetScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(budgetAllocationService.summary).toHaveBeenCalled());
    expect(budgetAllocationService.list).toHaveBeenCalled();
  });
});
