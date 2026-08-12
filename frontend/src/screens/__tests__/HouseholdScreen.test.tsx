import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import { Alert } from 'react-native';
import HouseholdScreen from '../settings/HouseholdScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { householdService } from '../../services/householdService';
import { confirmarUltimaAlerta } from '../../test-utils';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/householdService', () => ({
  householdService: {
    getByUser: jest.fn(), create: jest.fn(), addMember: jest.fn(),
    inviteByEmail: jest.fn(), removeMember: jest.fn(),
  },
}));
jest.mock('../../services/authService', () => ({
  authService: { getUser: jest.fn().mockResolvedValue({ correct: true, object: { userId: '2', name: 'Otro', email: 'otro@b.com' } }) },
}));

jest.spyOn(Alert, 'alert');

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

const hogarConDosMiembros = {
  correct: true,
  object: [{
    householdId: 100, name: 'Familia', role: 'OWNER', memberCount: 2,
    members: [{ userId: 1, role: 'OWNER' }, { userId: 2, role: 'MEMBER' }],
  }],
};

describe('HouseholdScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (householdService.getByUser as jest.Mock).mockResolvedValue({ correct: true, object: [] });
  });

  it('loads the user households and shows the create option', async () => {
    const { getByText } = render(<HouseholdScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(householdService.getByUser).toHaveBeenCalledWith('1'));
    expect(getByText(/Crear nucleo familiar/)).toBeTruthy();
  });

  /* Esta prueba certificaba el bug: invitaba escribiendo "5" en un campo
     numerico ("Ej: 4"), que es el id de fila de la base de datos. Nadie
     conoce su id, asi que el nucleo familiar no se podia usar. Ahora se
     exige que la pantalla invite por CORREO. */
  it('renders a household with members and invites by email (owner)', async () => {
    (householdService.getByUser as jest.Mock).mockResolvedValue(hogarConDosMiembros);
    (householdService.inviteByEmail as jest.Mock).mockResolvedValue({ correct: true });

    const { getByText, getByPlaceholderText } = render(<HouseholdScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText(/Familia/)).toBeTruthy());
    expect(getByText('2 miembros')).toBeTruthy();
    fireEvent.changeText(getByPlaceholderText('Ej: pareja@correo.com'), 'pareja@correo.com');
    fireEvent.press(getByText('Invitar'));
    await waitFor(() =>
      expect(householdService.inviteByEmail).toHaveBeenCalledWith(100, 'pareja@correo.com'));
  });

  it('warns when inviting without an email', async () => {
    (householdService.getByUser as jest.Mock).mockResolvedValue(hogarConDosMiembros);
    const { getByText } = render(<HouseholdScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText(/Familia/)).toBeTruthy());
    fireEvent.press(getByText('Invitar'));
    expect((globalThis as any).__mockShowToast).toHaveBeenCalledWith('warning', 'Correo requerido');
    expect(householdService.inviteByEmail).not.toHaveBeenCalled();
  });

  /* removeMember existia en el backend y la pantalla no lo llamaba: no habia
     forma de sacar a nadie del nucleo familiar. */
  it('lets the owner remove a member, after confirming', async () => {
    (householdService.getByUser as jest.Mock).mockResolvedValue(hogarConDosMiembros);
    (householdService.removeMember as jest.Mock).mockResolvedValue({ correct: true });

    const { getByText } = render(<HouseholdScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText(/Familia/)).toBeTruthy());

    fireEvent.press(getByText('Sacar'));
    expect(householdService.removeMember).not.toHaveBeenCalled(); // pregunta antes
    confirmarUltimaAlerta();
    await waitFor(() => expect(householdService.removeMember).toHaveBeenCalledWith(100, 2));
  });

  it('a plain member sees no way to invite or remove anybody', async () => {
    (householdService.getByUser as jest.Mock).mockResolvedValue({
      correct: true,
      object: [{
        householdId: 100, name: 'Familia', role: 'MEMBER', memberCount: 2,
        members: [{ userId: 1, role: 'OWNER' }, { userId: 2, role: 'MEMBER' }],
      }],
    });

    const { getByText, queryByText } = render(<HouseholdScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText(/Familia/)).toBeTruthy());
    expect(queryByText('Invitar')).toBeNull();
    expect(queryByText('Sacar')).toBeNull();
  });

  it('creates a new household', async () => {
    (householdService.create as jest.Mock).mockResolvedValue({ correct: true });
    const { getByText, getByPlaceholderText } = render(<HouseholdScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(householdService.getByUser).toHaveBeenCalled());
    fireEvent.changeText(getByPlaceholderText('Ej: Familia'), 'Mi Hogar');
    fireEvent.press(getByText('Crear'));
    await waitFor(() => expect(householdService.create).toHaveBeenCalledWith('Mi Hogar', '1'));
  });

  it('warns when creating a household without a name', async () => {
    const { getByText } = render(<HouseholdScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(householdService.getByUser).toHaveBeenCalled());
    fireEvent.press(getByText('Crear'));
    expect((globalThis as any).__mockShowToast).toHaveBeenCalledWith('warning', 'Nombre requerido');
  });

  /* Era la unica pantalla de datos sin los tres estados: un fallo al consultar
     el nucleo se pintaba como "Sin nucleo familiar", empujando al usuario a
     crear un hogar duplicado encima de uno que ya tenia. */
  it('muestra el mensaje del backend en vez del vacio cuando la carga falla', async () => {
    (householdService.getByUser as jest.Mock).mockResolvedValue({
      correct: false, message: 'No se pudo consultar el nucleo familiar',
    });

    const { getByText, queryByText } = render(<HouseholdScreen />, { wrapper: Wrapper });

    await waitFor(() => expect(getByText('No se pudo consultar el nucleo familiar')).toBeTruthy());
    expect(getByText('Reintentar')).toBeTruthy();
    expect(queryByText('Sin nucleo familiar')).toBeNull();
  });

  it('reintentar vuelve a consultar y pinta el nucleo', async () => {
    (householdService.getByUser as jest.Mock)
      .mockResolvedValueOnce({ correct: false, message: 'Servicio no disponible' })
      .mockResolvedValue(hogarConDosMiembros);

    const { getByText } = render(<HouseholdScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText('Servicio no disponible')).toBeTruthy());

    fireEvent.press(getByText('Reintentar'));

    await waitFor(() => expect(getByText(/Familia/)).toBeTruthy());
  });

  it('sin nucleos y sin fallo se sigue viendo el estado vacio, no un error', async () => {
    const { getByText, queryByText } = render(<HouseholdScreen />, { wrapper: Wrapper });

    await waitFor(() => expect(getByText('Sin nucleo familiar')).toBeTruthy());
    expect(queryByText('Reintentar')).toBeNull();
  });
});
