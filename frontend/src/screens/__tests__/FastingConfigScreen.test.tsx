import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import FastingConfigScreen from '../fasting/FastingConfigScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { fastingService } from '../../services/fastingService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/fastingService', () => ({
  fastingService: { getPlan: jest.fn(), getPresets: jest.fn(), savePlan: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '3', name: 'Anderson', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('FastingConfigScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (fastingService.getPlan as jest.Mock).mockResolvedValue({ correct: true, object: { planType: 'PLAN_16_8', fastingHours: 16, eatingHours: 8 } });
    (fastingService.getPresets as jest.Mock).mockResolvedValue({
      correct: true,
      object: [
        { planType: 'PLAN_16_8', label: '16:8', fastingHours: 16, eatingHours: 8 },
        { planType: 'PLAN_18_6', label: '18:6', fastingHours: 18, eatingHours: 6 },
      ],
    });
  });

  it('renders presets after loading', async () => {
    const { getByText } = render(<FastingConfigScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(fastingService.getPresets).toHaveBeenCalled());
    expect(getByText(/Planes predefinidos/)).toBeTruthy();
  });
});
