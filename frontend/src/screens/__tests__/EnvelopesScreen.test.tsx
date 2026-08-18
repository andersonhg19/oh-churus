import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import EnvelopesScreen from '../budget/EnvelopesScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import { ToastProvider } from '../../contexts/ToastContext';
import * as AuthContext from '../../contexts/AuthContext';
import { envelopeService } from '../../services/envelopeService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: () => (() => void) | void) => {
    const { useEffect } = require('react');
    useEffect(() => { const cleanup = cb(); return typeof cleanup === 'function' ? cleanup : undefined; }, []);
  },
}));

jest.mock('../../services/envelopeService', () => ({
  envelopeService: { state: jest.fn(), move: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'Ana', email: 'ana@ohchurus.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider><ToastProvider>{children}</ToastProvider></ThemeProvider>
);

const pintar = () => render(<Wrapper><EnvelopesScreen /></Wrapper>);

/**
 * Lo que esta pantalla tiene que conseguir es que la regla asimetrica se
 * ENTIENDA sin haberla leido en ningun manual:
 *
 *   Lo que sobra en un sobre se queda. Lo que te pasaste se descuenta de lo
 *   que tienes para repartir.
 *
 * Por eso la frase esta escrita dentro de la app, "Para repartir" va arriba y
 * grande —es la unica cifra sobre la que se decide algo— y cada sobre lleva la
 * palabra junto al numero.
 */
describe('EnvelopesScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (envelopeService.state as jest.Mock).mockResolvedValue({
      correct: true,
      object: {
        periodStart: '2026-08-01',
        periodEnd: '2026-08-31',
        envelopes: [
          {
            categoryId: '10', categoryName: 'Mercado', reimbursable: false,
            allocated: 500000, carryover: 100000, spent: 400000,
            available: 200000, label: 'Disponible',
          },
          {
            categoryId: '20', categoryName: 'Restaurantes', reimbursable: true,
            allocated: 100000, carryover: 0, spent: 150000,
            available: -50000, label: 'Te pasaste',
          },
        ],
        totalAllocated: 600000,
        totalSpent: 550000,
        carriedDebt: 30000,
        toBudget: -30000,
      },
    });
  });

  it('explica la regla dentro de la app, no solo en el codigo', async () => {
    const { getByText } = pintar();
    await waitFor(() => expect(getByText(/Lo que sobra en un sobre se queda/)).toBeTruthy());
  });

  it('ensena el arrastre junto a lo asignado, para que se vea de donde sale', async () => {
    const { getByText } = pintar();
    await waitFor(() => expect(getByText(/del mes pasado/)).toBeTruthy());
  });

  it('el sobregiro se ensena en positivo con la palabra "Te pasaste"', async () => {
    /* -50.000 junto a "Restaurantes" se lee como un error de la app. El signo
       lo pone la palabra, no el numero. */
    const { getByTestId, getByText } = pintar();
    await waitFor(() => expect(getByTestId('disponible-20')).toBeTruthy());

    expect(getByTestId('disponible-20').props.children).not.toContain('-');
    expect(getByText('Te pasaste')).toBeTruthy();
  });

  it('avisa de que el mes arranca con menos si el anterior se paso', async () => {
    const { getByTestId } = pintar();
    await waitFor(() => expect(getByTestId('deuda-arrastrada')).toBeTruthy());
    expect(getByTestId('deuda-arrastrada').props.children.join(''))
      .toContain('30.000');
  });

  it('marca las categorias que esperan reembolso', async () => {
    const { getByText } = pintar();
    await waitFor(() => expect(getByText('Es dinero que te van a devolver')).toBeTruthy());
  });

  it('tocar un sobre abre el movimiento hacia otro, sin mover nada todavia', async () => {
    const { getByTestId, getByText } = pintar();
    await waitFor(() => expect(getByTestId('sobre-20')).toBeTruthy());

    fireEvent.press(getByTestId('sobre-20'));

    expect(getByText(/Sacar de "Restaurantes"/)).toBeTruthy();
    expect(envelopeService.move).not.toHaveBeenCalled();
  });

  it('mover pide origen, destino e importe antes de llamar al backend', async () => {
    (envelopeService.move as jest.Mock).mockResolvedValue({ correct: true, object: {} });
    const { getByTestId, getByText, getByPlaceholderText } = pintar();
    await waitFor(() => expect(getByTestId('sobre-10')).toBeTruthy());

    fireEvent.press(getByTestId('sobre-10'));
    fireEvent.press(getByText('Mover'));
    await waitFor(() => expect(envelopeService.move).not.toHaveBeenCalled());

    fireEvent.press(getByTestId('destino-20'));
    fireEvent.changeText(getByPlaceholderText('0'), '50000');
    fireEvent.press(getByText('Mover'));

    await waitFor(() => expect(envelopeService.move).toHaveBeenCalledWith('10', '20', 50000, 1));
  });

  it('si el backend se niega, se ve su motivo y no un error generico', async () => {
    (envelopeService.state as jest.Mock).mockResolvedValue({
      correct: false, message: 'Sesion expirada',
    });
    const { getByText } = pintar();
    await waitFor(() => expect(getByText('Sesion expirada')).toBeTruthy());
  });

  it('sin sobres explica para que sirven', async () => {
    (envelopeService.state as jest.Mock).mockResolvedValue({
      correct: true,
      object: {
        periodStart: '2026-08-01', periodEnd: '2026-08-31', envelopes: [],
        totalAllocated: 0, totalSpent: 0, carriedDebt: 0, toBudget: 0,
      },
    });
    const { getByText } = pintar();
    await waitFor(() => expect(getByText('Sin sobres todavia')).toBeTruthy());
    expect(getByText(/sobro del mes pasado/)).toBeTruthy();
  });
});
