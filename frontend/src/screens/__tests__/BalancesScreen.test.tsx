import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import BalancesScreen from '../splits/BalancesScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import { ToastProvider } from '../../contexts/ToastContext';
import * as AuthContext from '../../contexts/AuthContext';
import { splitService } from '../../services/splitService';
import { householdService } from '../../services/householdService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: () => (() => void) | void) => {
    const { useEffect } = require('react');
    useEffect(() => { const cleanup = cb(); return typeof cleanup === 'function' ? cleanup : undefined; }, []);
  },
}));

jest.mock('../../services/splitService', () => ({
  splitService: { balances: jest.fn(), settle: jest.fn() },
}));

jest.mock('../../services/householdService', () => ({
  householdService: { getByUser: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'Ana', email: 'ana@ohchurus.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider><ToastProvider>{children}</ToastProvider></ThemeProvider>
);

const pintar = () => render(<Wrapper><BalancesScreen /></Wrapper>);

/**
 * Lo que se defiende aqui es que la pantalla se pueda leer sin pensar.
 *
 * Un balance mal presentado es peor que no tenerlo: si hay que deducir del
 * color o del signo quien debe a quien, la gente se equivoca al pagar. Por eso
 * cada fila lleva la palabra ("Te debe" / "Le debes") junto al numero, y el
 * numero siempre en positivo.
 */
describe('BalancesScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (householdService.getByUser as jest.Mock).mockResolvedValue({
      correct: true,
      object: [{ householdId: 5, name: 'Casa', role: 'OWNER', memberCount: 2,
                 members: [{ userId: 1, role: 'OWNER' }, { userId: 2, role: 'MEMBER' }] }],
    });
    (splitService.balances as jest.Mock).mockResolvedValue({
      correct: true,
      object: {
        list: [
          { userId: 2, net: 40000, label: 'Te debe', amount: 40000 },
          { userId: 3, net: -15000, label: 'Le debes', amount: 15000 },
        ],
        totalOwedToMe: 40000,
        totalIOwe: 15000,
        net: 25000,
      },
    });
  });

  it('ensena el neto y no la suma de las dos direcciones', async () => {
    const { getByTestId } = pintar();
    await waitFor(() => expect(getByTestId('neto-total')).toBeTruthy());
    expect(getByTestId('neto-total').props.children)
      .toContain('25.000');
  });

  it('los importes van SIEMPRE en positivo, con la palabra al lado', async () => {
    const { getByTestId, getByText } = pintar();
    await waitFor(() => expect(getByTestId('importe-3')).toBeTruthy());

    expect(getByTestId('importe-3').props.children)
      .not.toContain('-');
    expect(getByText('Le debes')).toBeTruthy();
    expect(getByText('Te debe')).toBeTruthy();
  });

  it('cuando no hay deudas explica cuando apareceran, no solo que no hay', async () => {
    (splitService.balances as jest.Mock).mockResolvedValue({
      correct: true,
      object: { list: [], totalOwedToMe: 0, totalIOwe: 0, net: 0 },
    });
    const { getByText } = pintar();
    await waitFor(() => expect(getByText('Estan en paz')).toBeTruthy());
    expect(getByText(/repartas un gasto/)).toBeTruthy();
  });

  it('si el backend dice que no, se ve el error y no una lista vacia', async () => {
    (splitService.balances as jest.Mock).mockResolvedValue({
      correct: false, message: 'Sesion expirada',
    });
    const { getByText } = pintar();
    await waitFor(() => expect(getByText('Sesion expirada')).toBeTruthy());
  });

  it('si los nombres no cargan, el balance se sigue viendo', async () => {
    /* El nombre es adorno; el balance es la informacion. Un fallo al cargar el
       hogar no puede dejar a Anderson sin saber cuanto debe. */
    (householdService.getByUser as jest.Mock).mockRejectedValue(new Error('sin red'));
    const { getByTestId } = pintar();
    await waitFor(() => expect(getByTestId('importe-2')).toBeTruthy());
    expect(getByTestId('importe-2').props.children).toContain('40.000');
  });

  it('tocar una persona pide confirmacion antes de anotar el pago', async () => {
    const { getByTestId } = pintar();
    await waitFor(() => expect(getByTestId('balance-2')).toBeTruthy());

    fireEvent.press(getByTestId('balance-2'));

    /* No se llama a settle de una: liquidar escribe un movimiento, y escribir
       sin preguntar es como se acaban anotando pagos que no ocurrieron. */
    await waitFor(() => expect(splitService.settle).not.toHaveBeenCalled());
  });
});
