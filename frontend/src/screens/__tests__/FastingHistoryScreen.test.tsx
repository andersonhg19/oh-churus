import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import FastingHistoryScreen from '../fasting/FastingHistoryScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { fastingService } from '../../services/fastingService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/fastingService', () => ({
  fastingService: { getHistory: jest.fn(), getSummary: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '3', name: 'Anderson', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('FastingHistoryScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (fastingService.getHistory as jest.Mock).mockResolvedValue({
      correct: true, object: { periodStart: '2026-03-01', periodEnd: '2026-03-31', days: [] },
    });
    (fastingService.getSummary as jest.Mock).mockResolvedValue({
      correct: true,
      object: { totalSessions: 5, completed: 3, incomplete: 2, inProgress: 0, complianceRate: 60, currentStreak: 2, bestStreak: 3 },
    });
  });

  it('loads history and summary for the period', async () => {
    render(<FastingHistoryScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(fastingService.getHistory).toHaveBeenCalled());
    expect(fastingService.getSummary).toHaveBeenCalled();
  });
});
