import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { Alert } from 'react-native';
import CategoryFormScreen from '../categories/CategoryFormScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { categoryService } from '../../services/categoryService';

jest.mock('../../services/categoryService', () => ({
  categoryService: {
    save: jest.fn().mockResolvedValue({ correct: true }),
    getTree: jest.fn().mockResolvedValue({ correct: true, object: [] }),
  },
}));

jest.spyOn(Alert, 'alert');

const mockNavigation = { navigate: jest.fn(), goBack: jest.fn() } as any;

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('CategoryFormScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders new category form', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <CategoryFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText(/Nueva Categoría/)).toBeTruthy();
    expect(getByText('Guardar')).toBeTruthy();
    expect(getByText('Cancelar')).toBeTruthy();
  });

  it('renders type toggle', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <CategoryFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText(/Ingreso/)).toBeTruthy();
    expect(getByText(/Gasto/)).toBeTruthy();
  });

  it('renders color picker', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <CategoryFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText('Color')).toBeTruthy();
  });

  it('shows validation error when name is empty', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <CategoryFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Guardar'));
    expect((globalThis as any).__mockShowToast).toHaveBeenCalledWith('warning', 'Validacion', expect.any(String));
  });

  it('renders edit mode with existing data', () => {
    const category = { id: '1', userId: '1', name: 'Salario', type: 'INCOME' as const, children: [], description: 'Test', icon: 'wallet', color: '#4CAF50' };
    const route = { params: { category } } as any;
    const { getByText } = render(
      <CategoryFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    expect(getByText(/Editar Categoría/)).toBeTruthy();
    expect(getByText('Actualizar')).toBeTruthy();
  });

  it('cancel navigates back', () => {
    const route = { params: {} } as any;
    const { getByText } = render(
      <CategoryFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Cancelar'));
    expect(mockNavigation.goBack).toHaveBeenCalled();
  });

  it('shows preview when name is entered', () => {
    const route = { params: {} } as any;
    const { getByPlaceholderText, getByText } = render(
      <CategoryFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.changeText(getByPlaceholderText('Nombre de la categoría'), 'Mi Categoría');
    expect(getByText('Vista previa')).toBeTruthy();
  });

  it('saves a category successfully (happy path)', async () => {
    const route = { params: {} } as any;
    const { getByText, getByPlaceholderText } = render(
      <CategoryFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.changeText(getByPlaceholderText('Nombre de la categoría'), 'Mercado');
    fireEvent.press(getByText('Guardar'));
    await waitFor(() => expect(categoryService.save).toHaveBeenCalled());
    expect(mockNavigation.goBack).toHaveBeenCalled();
  });

  it('shows an error toast when save fails', async () => {
    (categoryService.save as jest.Mock).mockResolvedValueOnce({ correct: false, message: 'fallo' });
    const route = { params: {} } as any;
    const { getByText, getByPlaceholderText } = render(
      <CategoryFormScreen navigation={mockNavigation} route={route} />,
      { wrapper: Wrapper },
    );
    fireEvent.changeText(getByPlaceholderText('Nombre de la categoría'), 'Mercado');
    fireEvent.press(getByText('Guardar'));
    await waitFor(() => expect((globalThis as any).__mockShowToast).toHaveBeenCalledWith('error', 'Error', 'fallo'));
  });
});
