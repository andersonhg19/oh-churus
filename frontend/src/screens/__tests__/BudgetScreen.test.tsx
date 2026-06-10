import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import BudgetScreen from '../budget/BudgetScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { budgetAllocationService } from '../../services/budgetAllocationService';
import { categoryService } from '../../services/categoryService';
import { movementService } from '../../services/movementService';

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

  it('renders an allocation card and deletes it', async () => {
    (budgetAllocationService.list as jest.Mock).mockResolvedValue({
      correct: true,
      object: [{ id: 1, categoryId: 10, categoryName: 'Arriendo', categoryColor: '#FF0000', amount: 500000, actualSpent: 300000, percentage: 60, variance: 200000 }],
    });
    (budgetAllocationService.delete as jest.Mock).mockResolvedValue({ correct: true });

    const { getByText } = render(<BudgetScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText('Arriendo')).toBeTruthy());
    expect(getByText(/favorable/)).toBeTruthy();
    fireEvent.press(getByText('Eliminar'));
    await waitFor(() => expect(budgetAllocationService.delete).toHaveBeenCalledWith(1));
  });

  it('adds a new allocation through the modal', async () => {
    (categoryService.getTree as jest.Mock).mockResolvedValue({
      correct: true, object: [{ id: '10', name: 'Comida', type: 'EXPENSE', children: [] }],
    });
    (budgetAllocationService.save as jest.Mock).mockResolvedValue({ correct: true });

    const { getByText, getByPlaceholderText } = render(<BudgetScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(budgetAllocationService.list).toHaveBeenCalled());
    fireEvent.press(getByText('+'));
    await waitFor(() => expect(getByText('Nuevo presupuesto')).toBeTruthy());
    fireEvent.press(getByText('Comida'));
    fireEvent.changeText(getByPlaceholderText('0'), '250000');
    fireEvent.press(getByText('Guardar'));
    await waitFor(() => expect(budgetAllocationService.save).toHaveBeenCalled());
  });

  it('performs a transfer through the modal', async () => {
    (categoryService.getTree as jest.Mock).mockResolvedValue({
      correct: true, object: [
        { id: '10', name: 'Compartida', type: 'EXPENSE', shared: true, children: [] },
        { id: '20', name: 'Personal', type: 'EXPENSE', shared: false, children: [] },
      ],
    });
    (movementService.transfer as jest.Mock).mockResolvedValue({ correct: true });

    const { getByText, getByPlaceholderText } = render(<BudgetScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(budgetAllocationService.list).toHaveBeenCalled());
    fireEvent.press(getByText('Nueva transferencia'));
    await waitFor(() => expect(getByText('Transferencia')).toBeTruthy());
    fireEvent.press(getByText('Personal')); // destino (origen se auto-selecciona)
    fireEvent.changeText(getByPlaceholderText('0'), '50000');
    fireEvent.press(getByText('Transferir'));
    await waitFor(() => expect(movementService.transfer).toHaveBeenCalled());
  });
});
