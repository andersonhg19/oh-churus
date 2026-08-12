import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import CategoriesScreen from '../categories/CategoriesScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { categoryService } from '../../services/categoryService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: () => (() => void) | void) => {
    const { useEffect } = require('react');
    useEffect(() => { const cleanup = cb(); return typeof cleanup === 'function' ? cleanup : undefined; }, []);
  },
}));

jest.mock('../../services/categoryService', () => ({
  categoryService: { getTree: jest.fn() },
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

describe('CategoriesScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (categoryService.getTree as jest.Mock).mockResolvedValue({
      correct: true,
      object: [
        {
          id: '1', userId: '1', name: 'Salario', type: 'INCOME', children: [
            { id: '2', userId: '1', name: 'Freelance', type: 'INCOME', children: [] },
          ],
        },
        { id: '3', userId: '1', name: 'Vivienda', type: 'EXPENSE', children: [] },
      ],
    });
  });

  it('renders title', async () => {
    const { getByText } = render(
      <CategoriesScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('Categorias')).toBeTruthy());
  });

  it('renders category items', async () => {
    const { getByText } = render(
      <CategoriesScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => {
      expect(getByText('Salario')).toBeTruthy();
      expect(getByText('Vivienda')).toBeTruthy();
    });
  });

  it('expands category to show children', async () => {
    const { getByText, getAllByText, queryByText } = render(
      <CategoriesScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('Salario')).toBeTruthy());
    // Children not visible initially
    expect(queryByText('Freelance')).toBeNull();
    // Click the expand button (first '+' that's not the FAB)
    const plusButtons = getAllByText('+');
    // The category expand button is the first one (FAB is the last)
    fireEvent.press(plusButtons[0]);
    expect(getByText('Freelance')).toBeTruthy();
  });

  it('shows empty state when no categories', async () => {
    (categoryService.getTree as jest.Mock).mockResolvedValue({ correct: true, object: [] });
    const { getByText } = render(
      <CategoriesScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('Sin categorias')).toBeTruthy());
  });

  it('renders FAB', async () => {
    const { getAllByText } = render(
      <CategoriesScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getAllByText('+').length).toBeGreaterThanOrEqual(1));
  });

  it('muestra el mensaje del backend cuando correct es false', async () => {
    (categoryService.getTree as jest.Mock).mockResolvedValue({
      correct: false, message: 'El arbol de categorias no esta disponible', object: null,
    });
    const { getByText, queryByText } = render(
      <CategoriesScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText('El arbol de categorias no esta disponible')).toBeTruthy());
    expect(queryByText('Sin categorias')).toBeNull();
  });

  it('shows error on fetch failure', async () => {
    (categoryService.getTree as jest.Mock).mockRejectedValue(new Error('fail'));
    const { getByText } = render(
      <CategoriesScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(getByText(/Error al cargar datos/)).toBeTruthy());
  });
});
