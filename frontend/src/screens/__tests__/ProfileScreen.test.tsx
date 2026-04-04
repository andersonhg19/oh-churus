import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import ProfileScreen from '../profile/ProfileScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';

jest.mock('../../services/authService', () => ({
  authService: {
    login: jest.fn(),
    register: jest.fn(),
    updateUser: jest.fn().mockResolvedValue({ correct: true, object: {} }),
  },
}));

const mockUser = {
  userId: '1',
  name: 'Anderson',
  email: 'anderson@test.com',
  budgetStartDay: 1,
};

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: mockUser,
  token: 'test-token',
  loading: false,
  isAuthenticated: true,
  login: jest.fn(),
  register: jest.fn(),
  logout: jest.fn(),
  updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('ProfileScreen', () => {
  it('renders user name', () => {
    const { getByText } = render(<ProfileScreen />, { wrapper: Wrapper });
    expect(getByText('Anderson')).toBeTruthy();
  });

  it('renders user email', () => {
    const { getByText } = render(<ProfileScreen />, { wrapper: Wrapper });
    expect(getByText('anderson@test.com')).toBeTruthy();
  });

  it('renders squirrel avatar', () => {
    const { getByText } = render(<ProfileScreen />, { wrapper: Wrapper });
    expect(getByText('🐿️')).toBeTruthy();
  });

  it('renders theme toggle button', () => {
    const { getByText } = render(<ProfileScreen />, { wrapper: Wrapper });
    expect(getByText('Cambiar a modo claro')).toBeTruthy();
  });

  it('renders logout button', () => {
    const { getByText } = render(<ProfileScreen />, { wrapper: Wrapper });
    expect(getByText('Cerrar Sesion')).toBeTruthy();
  });

  it('renders version text', () => {
    const { getByText } = render(<ProfileScreen />, { wrapper: Wrapper });
    expect(getByText('Oh Churus! v1.0.0')).toBeTruthy();
  });

  it('renders configuration section', () => {
    const { getByText } = render(<ProfileScreen />, { wrapper: Wrapper });
    expect(getByText('Configuracion')).toBeTruthy();
    expect(getByText('Guardar')).toBeTruthy();
  });
});
