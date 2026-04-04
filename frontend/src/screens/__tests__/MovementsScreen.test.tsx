import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import MovementsScreen from '../movements/MovementsScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { movementService } from '../../services/movementService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: () => (() => void) | void) => {
    const { useEffect } = require('react');
    useEffect(() => { const cleanup = cb(); return typeof cleanup === 'function' ? cleanup : undefined; }, []);
  },
}));

jest.mock('../../services/movementService', () => ({
  movementService: {
    getAll: jest.fn(),
    confirm: jest.fn(),
  },
}));


const mockNavigation = { navigate: jest.fn() } as any;
const mockRoute = { params: {} } as any;

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('MovementsScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (movementService.getAll as jest.Mock).mockResolvedValue({
      correct: true,
      object: { list: [
        { id: '1', userId: '1', categoryId: 'c1', categoryName: 'Salario', categoryType: 'INCOME', amount: 3500000, description: 'Pago', date: '2026-03-01', confirmed: true },
      ], page: 0, size: 50, totalPage: 1 },
    });
  });

  it('renders title', async () => {
    const { getByText } = render(
      <MovementsScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('Movimientos')).toBeTruthy());
  });

  it('renders movement items', async () => {
    const { getByText } = render(
      <MovementsScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('Pago')).toBeTruthy());
  });

  it('shows empty state when no movements', async () => {
    (movementService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [] } });
    const { getByText } = render(
      <MovementsScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('Sin movimientos')).toBeTruthy());
  });

  it('renders FAB button', async () => {
    const { getByText } = render(
      <MovementsScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('+')).toBeTruthy());
  });

  it('shows error when movements fail', async () => {
    (movementService.getAll as jest.Mock).mockRejectedValue(new Error('fail'));
    const { getByText } = render(
      <MovementsScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText(/Error al cargar movimientos/)).toBeTruthy());
  });
});
