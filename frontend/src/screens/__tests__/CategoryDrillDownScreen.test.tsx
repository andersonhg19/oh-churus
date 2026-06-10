import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import CategoryDrillDownScreen from '../summary/CategoryDrillDownScreen';
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

const route = {
  params: {
    categoryId: 'c1', categoryName: 'Vivienda', color: '#FF0000',
    startDate: '2026-03-01', endDate: '2026-03-31',
  },
} as any;

describe('CategoryDrillDownScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (movementService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [] } });
  });

  it('fetches movements for the category on mount', async () => {
    render(<CategoryDrillDownScreen route={route} navigation={{ navigate: jest.fn(), goBack: jest.fn() } as any} />, { wrapper: Wrapper });
    await waitFor(() => expect(movementService.getAll).toHaveBeenCalled());
  });
});
