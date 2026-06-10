import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import MovementsListScreen from '../movements/MovementsListScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { movementService } from '../../services/movementService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/movementService', () => ({
  movementService: { getAll: jest.fn(), confirm: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('MovementsListScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (movementService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [] } });
  });

  it('fetches movements on mount', async () => {
    render(<MovementsListScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(movementService.getAll).toHaveBeenCalled());
  });
});
