import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import HouseholdScreen from '../settings/HouseholdScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { householdService } from '../../services/householdService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/householdService', () => ({
  householdService: { getByUser: jest.fn(), create: jest.fn(), addMember: jest.fn(), removeMember: jest.fn() },
}));
jest.mock('../../services/authService', () => ({
  authService: { getUser: jest.fn().mockResolvedValue({ correct: true, object: { userId: '2', name: 'Otro' } }) },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

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

  it('renders a household with members and adds a member (owner)', async () => {
    (householdService.getByUser as jest.Mock).mockResolvedValue({
      correct: true,
      object: [{
        householdId: 100, name: 'Familia', role: 'OWNER', memberCount: 2,
        members: [{ userId: 1, role: 'OWNER' }, { userId: 2, role: 'MEMBER' }],
      }],
    });
    (householdService.addMember as jest.Mock).mockResolvedValue({ correct: true });

    const { getByText, getByPlaceholderText } = render(<HouseholdScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText(/Familia/)).toBeTruthy());
    expect(getByText('2 miembros')).toBeTruthy();
    fireEvent.changeText(getByPlaceholderText('Ej: 4'), '5');
    fireEvent.press(getByText('Agregar'));
    await waitFor(() => expect(householdService.addMember).toHaveBeenCalledWith(100, 5));
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
});
