import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import HouseholdScreen from '../settings/HouseholdScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { householdService } from '../../services/householdService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/householdService', () => ({
  householdService: { getByUser: jest.fn(), create: jest.fn(), addMember: jest.fn(), removeMember: jest.fn() },
}));
jest.mock('../../services/authService', () => ({
  authService: { getUser: jest.fn().mockResolvedValue({ correct: true, object: { userId: '2', name: 'Otro' } }) },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('HouseholdScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (householdService.getByUser as jest.Mock).mockResolvedValue({ correct: true, object: [] });
  });

  it('loads the user households and shows the create option', async () => {
    const { getByText } = render(<HouseholdScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(householdService.getByUser).toHaveBeenCalledWith('1'));
    expect(getByText(/Crear nucleo familiar/)).toBeTruthy();
  });
});
