import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import PeriodConfigScreen from '../settings/PeriodConfigScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { authService } from '../../services/authService';

jest.mock('../../services/authService', () => ({
  authService: { updateUser: jest.fn() },
}));

const updateUser = jest.fn();
jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser,
});

const showToastSpy = (globalThis as any).__mockShowToast as jest.Mock;
const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('PeriodConfigScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders the period configuration', () => {
    const { getByText } = render(<PeriodConfigScreen />, { wrapper: Wrapper });
    expect(getByText('Dia de inicio del periodo')).toBeTruthy();
  });

  it('saves a valid budget start day', async () => {
    (authService.updateUser as jest.Mock).mockResolvedValue({ correct: true });
    const { getByText } = render(<PeriodConfigScreen />, { wrapper: Wrapper });
    fireEvent.press(getByText('Guardar'));
    await waitFor(() => expect(authService.updateUser).toHaveBeenCalled());
    expect(updateUser).toHaveBeenCalledWith({ budgetStartDay: 1 });
  });
});
