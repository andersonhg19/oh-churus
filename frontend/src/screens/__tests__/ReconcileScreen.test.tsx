import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import ReconcileScreen from '../accounts/ReconcileScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import { ToastProvider } from '../../contexts/ToastContext';
import { accountService } from '../../services/accountService';

jest.mock('../../services/accountService', () => ({
  accountService: { reconcile: jest.fn() },
}));

const mockNavigation = { navigate: jest.fn(), goBack: jest.fn() } as any;
const cuenta = {
  id: '1', userId: '1', name: 'Ahorros', kind: 'OWN' as const,
  balance: 3500000, projectedBalance: 3500000,
};
const mockRoute = { params: { account: cuenta } } as any;

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider><ToastProvider>{children}</ToastProvider></ThemeProvider>
);

const pintar = () =>
  render(
    <Wrapper>
      <ReconcileScreen navigation={mockNavigation} route={mockRoute} />
    </Wrapper>,
  );

/**
 * El caso que estas pruebas defienden no es tecnico, es de producto:
 * PREGUNTAR NO PUEDE ESCRIBIR. Si comparar creara ya el ajuste, la app taparia
 * el descuadre en vez de ensenarlo, y el usuario perderia la unica pista que
 * tiene de que se le olvido anotar un gasto.
 */
describe('ReconcileScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('comparar consulta sin aplicar nada', async () => {
    (accountService.reconcile as jest.Mock).mockResolvedValue({
      correct: true,
      object: { difference: -45000, adjusted: false, message: 'te falta anotar algun gasto' },
    });

    const { getByText, getByPlaceholderText } = pintar();
    fireEvent.changeText(getByPlaceholderText('0'), '3455000');
    fireEvent.press(getByText('Comparar'));

    await waitFor(() => expect(accountService.reconcile).toHaveBeenCalled());
    expect(accountService.reconcile).toHaveBeenCalledWith('1', 3455000, false);
  });

  it('ensena la diferencia y el motivo antes de ofrecer el ajuste', async () => {
    (accountService.reconcile as jest.Mock).mockResolvedValue({
      correct: true,
      object: { difference: -45000, adjusted: false, message: 'te falta anotar algun gasto' },
    });

    const { getByText, getByPlaceholderText, getByTestId } = pintar();
    fireEvent.changeText(getByPlaceholderText('0'), '3455000');
    fireEvent.press(getByText('Comparar'));

    await waitFor(() => expect(getByTestId('veredicto')).toBeTruthy());
    expect(getByText(/te falta anotar algun gasto/)).toBeTruthy();
    expect(getByText('Anotar la diferencia como ajuste')).toBeTruthy();
  });

  it('cuando cuadra no ofrece ajustar nada', async () => {
    (accountService.reconcile as jest.Mock).mockResolvedValue({
      correct: true,
      object: { difference: 0, adjusted: false, message: 'La cuenta cuadra.' },
    });

    const { getByText, getByPlaceholderText, queryByText } = pintar();
    fireEvent.changeText(getByPlaceholderText('0'), '3500000');
    fireEvent.press(getByText('Comparar'));

    await waitFor(() => expect(getByText('✅ La cuenta cuadra')).toBeTruthy());
    expect(queryByText('Anotar la diferencia como ajuste')).toBeNull();
  });

  it('cambiar la cifra borra la comparacion anterior', async () => {
    /* Sin esto, el resultado de la comparacion vieja se queda en pantalla
       junto a una cifra nueva, y el boton de ajustar aplicaria un importe que
       ya no es el que se ve. */
    (accountService.reconcile as jest.Mock).mockResolvedValue({
      correct: true,
      object: { difference: -45000, adjusted: false, message: 'falta un gasto' },
    });

    const { getByText, getByPlaceholderText, queryByTestId } = pintar();
    fireEvent.changeText(getByPlaceholderText('0'), '3455000');
    fireEvent.press(getByText('Comparar'));
    await waitFor(() => expect(queryByTestId('veredicto')).toBeTruthy());

    fireEvent.changeText(getByPlaceholderText('0'), '3400000');
    expect(queryByTestId('veredicto')).toBeNull();
  });

  it('sin escribir nada no llama al backend', async () => {
    const { getByText } = pintar();
    fireEvent.press(getByText('Comparar'));
    await waitFor(() => expect(accountService.reconcile).not.toHaveBeenCalled());
  });
});
