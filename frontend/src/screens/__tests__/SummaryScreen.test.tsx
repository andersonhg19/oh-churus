import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import SummaryScreen from '../summary/SummaryScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { dashboardService } from '../../services/dashboardService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/dashboardService', () => ({
  dashboardService: { getSummary: jest.fn(), getByCategory: jest.fn() },
}));
jest.mock('../../services/movementService', () => ({
  movementService: { getAll: jest.fn().mockResolvedValue({ correct: true, object: { list: [] } }) },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const mockNavigation = { navigate: jest.fn(), goBack: jest.fn() } as any;
const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('SummaryScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (dashboardService.getSummary as jest.Mock).mockResolvedValue({
      correct: true, object: { totalIncome: 0, totalExpense: 0, balance: 0, budgetTotal: 0, pendingCount: 0, pendingAmount: 0 },
    });
    (dashboardService.getByCategory as jest.Mock).mockResolvedValue({ correct: true, object: [] });
  });

  it('muestra el mensaje del backend cuando el resumen falla', async () => {
    (dashboardService.getByCategory as jest.Mock).mockResolvedValue({
      correct: false, message: 'No se pudo calcular el periodo', object: null,
    });
    const { getByText, queryByText } = render(
      <SummaryScreen navigation={mockNavigation} route={{ params: {} } as any} />, { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('No se pudo calcular el periodo')).toBeTruthy());
    // "Sin datos" es la pantalla del usuario nuevo; un fallo no puede parecerse a eso.
    expect(queryByText('Sin datos')).toBeNull();
  });

  it('loads category breakdown on mount', async () => {
    render(<SummaryScreen navigation={mockNavigation} route={{ params: {} } as any} />, { wrapper: Wrapper });
    await waitFor(() => expect(dashboardService.getByCategory).toHaveBeenCalled());
  });

  it('renders the donut, switches view modes and drills into a category', async () => {
    (dashboardService.getByCategory as jest.Mock).mockResolvedValue({
      correct: true,
      object: [
        { categoryId: 10, categoryName: 'Arriendo', categoryType: 'EXPENSE', total: 300000, color: '#FF0000' },
        { categoryId: 20, categoryName: 'Salario', categoryType: 'INCOME', total: 500000, color: '#00FF00' },
      ],
    });
    (dashboardService.getSummary as jest.Mock).mockResolvedValue({
      correct: true, object: { totalIncome: 500000, totalExpense: 300000, balance: 200000, budgetTotal: 0, pendingCount: 0, pendingAmount: 0 },
    });

    const { getByText, getAllByText } = render(
      <SummaryScreen navigation={mockNavigation} route={{ params: {} } as any} />, { wrapper: Wrapper },
    );
    // default BALANCE view -> expense donut shows "Arriendo" in the legend
    await waitFor(() => expect(getAllByText('Arriendo').length).toBeGreaterThan(0));
    // drill into a category slice (donut legend is the first occurrence)
    fireEvent.press(getAllByText('Arriendo')[0]);
    expect(mockNavigation.navigate).toHaveBeenCalledWith('CategoryDrillDown', expect.objectContaining({ categoryId: '10' }));
    // switch to Ingresos -> income donut shows "Salario"
    fireEvent.press(getByText('Ingresos'));
    await waitFor(() => expect(getByText('Salario')).toBeTruthy());
    // switch to Gastos
    fireEvent.press(getByText('Gastos'));
    await waitFor(() => expect(getAllByText('Arriendo').length).toBeGreaterThan(0));
  });
});
