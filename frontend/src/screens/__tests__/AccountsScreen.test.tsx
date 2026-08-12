import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import AccountsScreen from '../accounts/AccountsScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import { accountService } from '../../services/accountService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: () => (() => void) | void) => {
    const { useEffect } = require('react');
    useEffect(() => { const cleanup = cb(); return typeof cleanup === 'function' ? cleanup : undefined; }, []);
  },
}));

jest.mock('../../services/accountService', () => ({
  accountService: { getAll: jest.fn() },
}));

const mockNavigation = { navigate: jest.fn() } as any;
const mockRoute = { params: {} } as any;

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

const pintar = () =>
  render(
    <Wrapper>
      <AccountsScreen navigation={mockNavigation} route={mockRoute} />
    </Wrapper>,
  );

/**
 * Lo que se prueba aqui no es que la lista pinte: es que los DOS numeros que
 * la pantalla ensena signifiquen lo que dicen. Confundir el saldo confirmado
 * con el proyectado, o ensenar la deuda de una tarjeta en negativo junto a la
 * palabra "Tienes", convierte la comparacion con el banco en un acertijo.
 */
describe('AccountsScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (accountService.getAll as jest.Mock).mockResolvedValue({
      correct: true,
      object: {
        list: [
          {
            id: '1', userId: '1', name: 'Ahorros', kind: 'OWN',
            balance: 3500000, projectedBalance: 2700000,
          },
          {
            id: '2', userId: '1', name: 'Tarjeta', kind: 'LIABILITY',
            balance: -400000, projectedBalance: -400000,
          },
        ],
        netWorth: 3100000,
      },
    });
  });

  it('ensena el saldo confirmado de cada cuenta', async () => {
    const { getByTestId } = pintar();
    await waitFor(() => expect(getByTestId('saldo-1')).toBeTruthy());
    expect(getByTestId('saldo-1').props.children).toContain('3.500.000');
  });

  it('la deuda de un pasivo se lee en positivo con la palabra "Debes"', async () => {
    /* -400.000 junto a "Tarjeta" se lee como un error de la app, no como una
       deuda. El signo lo pone la palabra, no el numero. */
    const { getByTestId, getByText } = pintar();
    await waitFor(() => expect(getByTestId('saldo-2')).toBeTruthy());
    expect(getByTestId('saldo-2').props.children).not.toContain('-');
    expect(getByText('Debes')).toBeTruthy();
  });

  it('el patrimonio resta la deuda en vez de sumarla', async () => {
    const { getByTestId } = pintar();
    await waitFor(() => expect(getByTestId('patrimonio')).toBeTruthy());
    expect(getByTestId('patrimonio').props.children).toContain('3.100.000');
  });

  it('avisa de lo pendiente solo cuando el proyectado difiere del confirmado', async () => {
    /* La tarjeta tiene los dos saldos iguales: anadirle una linea de "con lo
       pendiente" repitiendo la misma cifra es ruido que entrena a no leer. */
    const { queryAllByText } = pintar();
    await waitFor(() => expect(queryAllByText(/Con lo pendiente/).length).toBe(1));
  });

  it('cuando no hay cuentas explica para que sirven, no solo que no hay', async () => {
    (accountService.getAll as jest.Mock).mockResolvedValue({
      correct: true,
      object: { list: [], netWorth: 0 },
    });
    const { getByText } = pintar();
    await waitFor(() => expect(getByText('Sin cuentas')).toBeTruthy());
    expect(getByText(/cuanta plata deberia haber/)).toBeTruthy();
  });

  it('si el backend dice que no, se ve el error y no una lista vacia', async () => {
    /* El fallo que la ola 1 encontro repetido: correct=false sin rama else
       dejaba la pantalla igual que "aun no tienes datos". */
    (accountService.getAll as jest.Mock).mockResolvedValue({
      correct: false,
      message: 'Sesion expirada',
    });
    const { getByText } = pintar();
    await waitFor(() => expect(getByText('Sesion expirada')).toBeTruthy());
  });

  it('mantener pulsada una cuenta lleva a conciliarla', async () => {
    const { getByTestId } = pintar();
    await waitFor(() => expect(getByTestId('cuenta-1')).toBeTruthy());
    fireEvent(getByTestId('cuenta-1'), 'longPress');
    expect(mockNavigation.navigate).toHaveBeenCalledWith('Reconcile', expect.anything());
  });
});
