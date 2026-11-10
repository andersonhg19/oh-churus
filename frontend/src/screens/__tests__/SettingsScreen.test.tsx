import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import SettingsScreen from '../settings/SettingsScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';

const logout = jest.fn();
jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'Anderson', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true, esAdmin: false,
  login: jest.fn(), register: jest.fn(), logout, updateUser: jest.fn(),
});

const mockNavigation = { navigate: jest.fn() } as any;
const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('SettingsScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders user info, menu and version', () => {
    const { getByText } = render(
      <SettingsScreen navigation={mockNavigation} route={{} as any} />, { wrapper: Wrapper },
    );
    expect(getByText('Anderson')).toBeTruthy();
    expect(getByText('a@b.com')).toBeTruthy();
    expect(getByText(/Exportar a Excel/)).toBeTruthy();
    expect(getByText('Oh Churus! v3.0.0')).toBeTruthy();
  });

  it('navigates to a menu section', () => {
    const { getByText } = render(
      <SettingsScreen navigation={mockNavigation} route={{} as any} />, { wrapper: Wrapper },
    );
    fireEvent.press(getByText(/Día de corte del periodo/));
    expect(mockNavigation.navigate).toHaveBeenCalledWith('PeriodConfig');
  });

  it('quien no es administrador NO ve la seccion de administracion', () => {
    /* El menu no llega en las pruebas, asi que se cae al rol del token: aqui
       esAdmin es false. Esconder los botones no es la seguridad —la seguridad
       es el 403 del backend— pero ensenar puertas que no se pueden abrir es
       una mala pantalla. */
    const { queryByText } = render(
      <SettingsScreen navigation={mockNavigation} route={{} as any} />, { wrapper: Wrapper },
    );
    expect(queryByText(/Usuarios y roles/)).toBeNull();
    expect(queryByText(/Modulos y permisos/)).toBeNull();
  });

  it('y quien si lo es, la ve y puede entrar', () => {
    jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
      user: { userId: '1', name: 'Anderson', email: 'a@b.com', budgetStartDay: 1, roles: ['ADMIN'] },
      token: 'tok', loading: false, isAuthenticated: true, esAdmin: true,
      login: jest.fn(), register: jest.fn(), logout, updateUser: jest.fn(),
    });

    const { getByText } = render(
      <SettingsScreen navigation={mockNavigation} route={{} as any} />, { wrapper: Wrapper },
    );
    fireEvent.press(getByText(/Usuarios y roles/));
    expect(mockNavigation.navigate).toHaveBeenCalledWith('AdminUsers');
  });

  it('toggles the theme', () => {
    const { getByText } = render(
      <SettingsScreen navigation={mockNavigation} route={{} as any} />, { wrapper: Wrapper },
    );
    // default theme is dark -> button offers "Modo claro"
    fireEvent.press(getByText('Modo claro'));
    expect(getByText('Modo oscuro')).toBeTruthy();
  });
});
