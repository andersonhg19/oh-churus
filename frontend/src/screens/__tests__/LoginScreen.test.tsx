import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import LoginScreen from '../auth/LoginScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import { AuthProvider } from '../../contexts/AuthContext';
import { Alert } from 'react-native';

jest.mock('../../services/authService', () => ({
  authService: {
    login: jest.fn().mockResolvedValue({ correct: true, object: { token: 't', userId: '1', name: 'N', email: 'e' } }),
    register: jest.fn(),
  },
}));

jest.spyOn(Alert, 'alert');

const mockNavigation = { navigate: jest.fn(), goBack: jest.fn() } as any;
const mockRoute = { params: {} } as any;

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider><AuthProvider>{children}</AuthProvider></ThemeProvider>
);

describe('LoginScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders login form elements', () => {
    const { getByText, getByPlaceholderText } = render(
      <LoginScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    expect(getByText('Oh Churus!')).toBeTruthy();
    expect(getByText('Iniciar Sesion')).toBeTruthy();
    expect(getByPlaceholderText('tu@correo.com')).toBeTruthy();
    expect(getByPlaceholderText('Tu contraseña')).toBeTruthy();
  });

  it('renders squirrel mascot', () => {
    const { getByText } = render(
      <LoginScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    expect(getByText('🐿️')).toBeTruthy();
  });

  it('shows validation errors when fields are empty', async () => {
    const { getByText } = render(
      <LoginScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Iniciar Sesion'));
    await waitFor(() => {
      expect(getByText('El correo electronico es obligatorio')).toBeTruthy();
      expect(getByText('La contrasena es obligatoria')).toBeTruthy();
    });
  });

  it('navigates to register screen', () => {
    const { getByText } = render(
      <LoginScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Registrate'));
    expect(mockNavigation.navigate).toHaveBeenCalledWith('Register');
  });

  it('renders tagline', () => {
    const { getByText } = render(
      <LoginScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    expect(getByText('Tu asistente para la vida cotidiana')).toBeTruthy();
  });
});
