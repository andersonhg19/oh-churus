import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import MovementsListScreen from '../movements/MovementsListScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { movementService } from '../../services/movementService';

const mockFocusEffectSpy = jest.fn();
jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => {
    const { useEffect } = require('react');
    mockFocusEffectSpy(cb);
    useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []);
  },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/movementService', () => ({
  movementService: { getAll: jest.fn(), confirm: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('MovementsListScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (movementService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [] } });
  });

  it('fetches movements on mount', async () => {
    render(<MovementsListScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(movementService.getAll).toHaveBeenCalled());
  });

  it('muestra el mensaje del backend cuando correct es false, no una lista vacia', async () => {
    (movementService.getAll as jest.Mock).mockResolvedValue({
      correct: false, message: 'Tu sesion expiro, vuelve a entrar', object: null,
    });
    const { getByText, queryByText } = render(<MovementsListScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText('Tu sesion expiro, vuelve a entrar')).toBeTruthy());
    // Lo que NO debe verse: el mismo pixel que "aun no tienes movimientos".
    expect(queryByText('No hay movimientos')).toBeNull();
  });

  it('permite reintentar sin salir de la pantalla', async () => {
    (movementService.getAll as jest.Mock).mockResolvedValueOnce({
      correct: false, message: 'Servicio no disponible', object: null,
    });
    const { getByText } = render(<MovementsListScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText('Servicio no disponible')).toBeTruthy());

    (movementService.getAll as jest.Mock).mockResolvedValue({
      correct: true,
      object: { list: [{ id: 'm1', amount: 1000, date: '2026-03-01', description: 'Arriendo', categoryName: 'Vivienda', categoryType: 'EXPENSE', confirmed: true }] },
    });
    fireEvent.press(getByText('Reintentar'));
    await waitFor(() => expect(getByText('Arriendo')).toBeTruthy());
  });

  it('vuelve a pedir la lista al enfocar la pantalla', async () => {
    render(<MovementsListScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(movementService.getAll).toHaveBeenCalled());
    // useFocusEffect (y no useEffect) es lo que refresca al volver del formulario.
    expect(mockFocusEffectSpy).toHaveBeenCalled();
  });

  describe('navegacion por periodo', () => {
    /* El navegador de periodo vivia en MovementsScreen, una pantalla completa
       que nunca se registro en ninguna ruta: nadie podia abrirla. Sin el, la
       lista mostraba los ultimos 100 movimientos sueltos y no habia forma de
       mirar el mes pasado, que es lo primero que se quiere hacer en una app
       de presupuesto mensual. */

    it('pide los movimientos acotados al periodo actual', async () => {
      render(<MovementsListScreen />, { wrapper: Wrapper });
      await waitFor(() => expect(movementService.getAll).toHaveBeenCalled());

      const filtro = (movementService.getAll as jest.Mock).mock.calls[0][0];
      expect(filtro.startDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(filtro.endDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(filtro.startDate < filtro.endDate).toBe(true);
    });

    it('al ir al periodo anterior vuelve a pedir, con fechas mas antiguas', async () => {
      const { getByText } = render(<MovementsListScreen />, { wrapper: Wrapper });
      await waitFor(() => expect(movementService.getAll).toHaveBeenCalled());
      const primero = (movementService.getAll as jest.Mock).mock.calls[0][0];

      fireEvent.press(getByText('<'));

      await waitFor(() =>
        expect((movementService.getAll as jest.Mock).mock.calls.length).toBeGreaterThan(1));
      const llamadas = (movementService.getAll as jest.Mock).mock.calls;
      const ultimo = llamadas[llamadas.length - 1][0];
      expect(ultimo.startDate < primero.startDate).toBe(true);
    });

    it('no deja avanzar a un periodo que todavia no ha empezado', async () => {
      const { getByText } = render(<MovementsListScreen />, { wrapper: Wrapper });
      await waitFor(() => expect(movementService.getAll).toHaveBeenCalled());
      const antes = (movementService.getAll as jest.Mock).mock.calls.length;

      fireEvent.press(getByText('>'));

      /* Un periodo futuro esta vacio por definicion: dejar avanzar solo
         confunde. */
      await new Promise(r => setTimeout(r, 50));
      expect((movementService.getAll as jest.Mock).mock.calls.length).toBe(antes);
    });
  });
});
