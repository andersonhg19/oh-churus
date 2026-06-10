import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import FastingDashboardScreen from '../fasting/FastingDashboardScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { fastingService } from '../../services/fastingService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/fastingService', () => ({
  fastingService: {
    getPlan: jest.fn(),
    getActiveSession: jest.fn(),
    getWaterToday: jest.fn(),
    getAchievements: jest.fn(),
    startFasting: jest.fn(),
    stopFasting: jest.fn(),
    cancelFasting: jest.fn(),
    addWater: jest.fn(),
  },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '3', name: 'Anderson', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('FastingDashboardScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (fastingService.getPlan as jest.Mock).mockResolvedValue({ correct: false, object: null });
    (fastingService.getActiveSession as jest.Mock).mockResolvedValue({ correct: true, object: null });
    (fastingService.getWaterToday as jest.Mock).mockResolvedValue({ correct: true, object: { glasses: 2, goalGlasses: 8 } });
    (fastingService.getAchievements as jest.Mock).mockResolvedValue({ correct: true, object: [] });
  });

  it('renders the no-plan state when there is no configured plan', async () => {
    const { getByText } = render(<FastingDashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText(/No tienes un plan configurado/)).toBeTruthy());
    expect(fastingService.getPlan).toHaveBeenCalledWith('3');
  });

  it('renders an active plan with water tracker', async () => {
    (fastingService.getPlan as jest.Mock).mockResolvedValue({
      correct: true, object: { id: 1, planType: 'PLAN_16_8', fastingHours: 16, eatingHours: 8 },
    });
    const { getByText } = render(<FastingDashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(fastingService.getWaterToday).toHaveBeenCalled());
    expect(getByText(/Plan activo/)).toBeTruthy();
  });
});
