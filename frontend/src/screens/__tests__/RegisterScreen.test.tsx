import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import RegisterScreen from '../auth/RegisterScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import { AuthProvider } from '../../contexts/AuthContext';

jest.mock('../../services/authService', () => ({
  authService: {
    login: jest.fn(),
    register: jest.fn().mockResolvedValue({ correct: true, object: { token: 't', userId: '1', name: 'N', email: 'e' } }),
  },
}));

const mockNavigation = { navigate: jest.fn(), goBack: jest.fn() } as any;
const mockRoute = { params: {} } as any;

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider><AuthProvider>{children}</AuthProvider></ThemeProvider>
);

describe('RegisterScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders register form elements', () => {
    const { getByText, getByPlaceholderText } = render(
      <RegisterScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    expect(getByText('Oh Churus!')).toBeTruthy();
    expect(getByText('Crear cuenta')).toBeTruthy();
    expect(getByPlaceholderText('Tu nombre')).toBeTruthy();
    expect(getByPlaceholderText('tu@correo.com')).toBeTruthy();
    expect(getByPlaceholderText('Mínimo 8, con letras y números')).toBeTruthy();
  });

  it('shows validation errors when fields are empty', async () => {
    const { getByText, queryByText } = render(
      <RegisterScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Crear cuenta'));
    await waitFor(() => {
      // Al menos uno de los mensajes de validacion debe aparecer
      const hasNameError = queryByText(/Nombre.*obligatorio/);
      const hasEmailError = queryByText(/correo.*obligatorio/i);
      const hasPassError = queryByText(/contraseña.*obligatoria/i);
      expect(hasNameError || hasEmailError || hasPassError).toBeTruthy();
    });
  });

  it('shows password length error for short passwords', async () => {
    const { getByText, getByPlaceholderText } = render(
      <RegisterScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    fireEvent.changeText(getByPlaceholderText('Tu nombre'), 'Test');
    fireEvent.changeText(getByPlaceholderText('tu@correo.com'), 'a@b.com');
    fireEvent.changeText(getByPlaceholderText('Mínimo 8, con letras y números'), '123');
    fireEvent.press(getByText('Crear cuenta'));
    await waitFor(() => {
      expect(getByText('Mínimo 8 caracteres, con al menos una letra y un número')).toBeTruthy();
    });
  });

  it('navigates back to login', () => {
    const { getByText } = render(
      <RegisterScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    fireEvent.press(getByText('Inicia sesión'));
    expect(mockNavigation.goBack).toHaveBeenCalled();
  });

  it('shows tagline', () => {
    const { getByText } = render(
      <RegisterScreen navigation={mockNavigation} route={mockRoute} />,
      { wrapper: Wrapper },
    );
    expect(getByText('Crea tu cuenta y empieza a organizar tu vida')).toBeTruthy();
  });
});
