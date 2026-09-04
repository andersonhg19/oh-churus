import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import CurrencyConfigScreen from '../settings/CurrencyConfigScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { preferenciasService } from '../../services/adminService';
import { authService } from '../../services/authService';
import { formatCurrency, configurarFormatoDeMoneda } from '../../utils/format';

jest.mock('../../services/adminService');
jest.mock('../../services/authService');
jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => {
    const { useEffect } = require('react');
    useEffect(() => {
      const limpieza = cb();
      return typeof limpieza === 'function' ? limpieza : undefined;
    }, [cb]);
  },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'Anderson', email: 'a@b.com', budgetStartDay: 1, roles: ['USER'] },
  token: 'tok', loading: false, isAuthenticated: true, esAdmin: false,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

const preferencias = (currency: string, decimalPlaces: number, propia: boolean) => ({
  correct: true, message: 'OK', errorCode: 0,
  object: { currency, decimalPlaces, locale: 'es-CO', propia },
});

describe('CurrencyConfigScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    configurarFormatoDeMoneda({ currency: 'COP', decimalPlaces: 0, locale: 'es-CO' });
    (preferenciasService.mias as jest.Mock).mockResolvedValue(preferencias('COP', 0, false));
  });

  it('dice cuando lo que usas viene de la configuracion global', async () => {
    const { getByText } = render(<CurrencyConfigScreen />, { wrapper: Wrapper });

    await waitFor(() => expect(getByText(/toda la aplicacion/i)).toBeTruthy());
  });

  it('y no lo dice cuando ya elegiste', async () => {
    (preferenciasService.mias as jest.Mock).mockResolvedValue(preferencias('EUR', 2, true));
    const { queryByText, getByTestId } = render(<CurrencyConfigScreen />, { wrapper: Wrapper });

    await waitFor(() => expect(getByTestId('vista-previa')).toBeTruthy());
    expect(queryByText(/toda la aplicacion/i)).toBeNull();
  });

  it('LA VISTA PREVIA CAMBIA ANTES DE GUARDAR', async () => {
    /* Los decimales son la clase de ajuste que solo se entiende viendolo. */
    const { getByTestId } = render(<CurrencyConfigScreen />, { wrapper: Wrapper });

    await waitFor(() => expect(getByTestId('vista-previa')).toBeTruthy());
    expect(getByTestId('vista-previa').props.children).toContain('$');

    fireEvent.press(getByTestId('moneda-EUR'));
    fireEvent.press(getByTestId('decimales-2'));

    await waitFor(() =>
      expect(getByTestId('vista-previa').props.children).toContain('€'),
    );
    expect(getByTestId('vista-previa').props.children).toContain(',89');
  });

  it('PERO NO TOCA EL FORMATO DE LA APP HASTA QUE SE GUARDA', async () => {
    /* Si la vista previa configurara el formateador global, cambiar de opcion
       y salirse sin guardar dejaria la app entera con una moneda que nadie
       confirmo. */
    const { getByTestId } = render(<CurrencyConfigScreen />, { wrapper: Wrapper });

    await waitFor(() => expect(getByTestId('moneda-EUR')).toBeTruthy());
    fireEvent.press(getByTestId('moneda-EUR'));

    expect(formatCurrency(1000)).toContain('$');
    expect(formatCurrency(1000)).not.toContain('€');
  });

  it('al guardar, se manda al servidor y se aplica en la app', async () => {
    (authService.updateUser as jest.Mock).mockResolvedValue({
      correct: true, message: 'OK', errorCode: 0, object: {},
    });
    const { getByTestId } = render(<CurrencyConfigScreen />, { wrapper: Wrapper });

    await waitFor(() => expect(getByTestId('moneda-EUR')).toBeTruthy());
    fireEvent.press(getByTestId('moneda-EUR'));
    fireEvent.press(getByTestId('decimales-2'));
    fireEvent.press(getByTestId('guardar-moneda'));

    await waitFor(() =>
      expect(authService.updateUser).toHaveBeenCalledWith(
        expect.objectContaining({ currency: 'EUR', decimalPlaces: 2 }),
      ),
    );
    await waitFor(() => expect(formatCurrency(1000)).toContain('€'));
  });

  it('si el servidor se niega, no se aplica nada', async () => {
    (authService.updateUser as jest.Mock).mockResolvedValue({
      correct: false, message: 'no se pudo', errorCode: 500, object: null,
    });
    const { getByTestId } = render(<CurrencyConfigScreen />, { wrapper: Wrapper });

    await waitFor(() => expect(getByTestId('moneda-EUR')).toBeTruthy());
    fireEvent.press(getByTestId('moneda-EUR'));
    fireEvent.press(getByTestId('guardar-moneda'));

    await waitFor(() =>
      expect(globalThis.__mockShowToast).toHaveBeenCalledWith('error', 'no se pudo'),
    );
    expect(formatCurrency(1000)).not.toContain('€');
  });

  it('un fallo de carga se ve', async () => {
    (preferenciasService.mias as jest.Mock).mockResolvedValue({
      correct: false, message: 'sin conexion', errorCode: 500, object: null,
    });
    const { getByText } = render(<CurrencyConfigScreen />, { wrapper: Wrapper });

    await waitFor(() => expect(getByText('sin conexion')).toBeTruthy());
  });
});
