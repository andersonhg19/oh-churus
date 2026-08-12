import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import FastingDashboardScreen from '../fasting/FastingDashboardScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { fastingService } from '../../services/fastingService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/fastingService', () => ({
  fastingService: {
    getPlan: jest.fn(),
    getActiveSession: jest.fn(),
    getWaterToday: jest.fn(),
    getAchievements: jest.fn(),
    startFasting: jest.fn(),
    stopFasting: jest.fn(),
    cancelFasting: jest.fn(),
    addWater: jest.fn(),
  },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '3', name: 'Anderson', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('FastingDashboardScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (fastingService.getPlan as jest.Mock).mockResolvedValue({ correct: false, object: null });
    (fastingService.getActiveSession as jest.Mock).mockResolvedValue({ correct: true, object: null });
    (fastingService.getWaterToday as jest.Mock).mockResolvedValue({ correct: true, object: { glasses: 2, goalGlasses: 8 } });
    (fastingService.getAchievements as jest.Mock).mockResolvedValue({ correct: true, object: [] });
  });

  it('renders the no-plan state when there is no configured plan', async () => {
    const { getByText } = render(<FastingDashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText(/No tienes un plan configurado/)).toBeTruthy());
    expect(fastingService.getPlan).toHaveBeenCalledWith('3');
  });

  it('renders an active plan with water tracker', async () => {
    (fastingService.getPlan as jest.Mock).mockResolvedValue({
      correct: true, object: { id: 1, planType: 'PLAN_16_8', fastingHours: 16, eatingHours: 8 },
    });
    const { getByText } = render(<FastingDashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(fastingService.getWaterToday).toHaveBeenCalled());
    expect(getByText(/Plan activo/)).toBeTruthy();
  });

  const activePlan = { id: 1, planType: 'PLAN_16_8', fastingHours: 16, eatingHours: 8 };

  it('renders an active fasting session (timer + finish/cancel)', async () => {
    (fastingService.getPlan as jest.Mock).mockResolvedValue({ correct: true, object: activePlan });
    (fastingService.getActiveSession as jest.Mock).mockResolvedValue({
      correct: true,
      object: { status: 'IN_PROGRESS', startTime: new Date(Date.now() - 3600000).toISOString(),
                targetEndTime: new Date(Date.now() + 15 * 3600000).toISOString() },
    });
    const { getByText } = render(<FastingDashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText('Finalizar Ayuno')).toBeTruthy());
    expect(getByText('Cancelar')).toBeTruthy();
  });

  it('opens the start modal and starts a fast', async () => {
    (fastingService.getPlan as jest.Mock).mockResolvedValue({ correct: true, object: activePlan });
    (fastingService.startFasting as jest.Mock).mockResolvedValue({
      correct: true, object: { status: 'IN_PROGRESS', startTime: new Date().toISOString() },
    });
    const { getByText } = render(<FastingDashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText('Iniciar Ayuno')).toBeTruthy());
    fireEvent.press(getByText('Iniciar Ayuno'));
    fireEvent.press(getByText('Confirmar'));
    await waitFor(() => expect(fastingService.startFasting).toHaveBeenCalled());
  });

  it('adds a glass of water', async () => {
    (fastingService.getPlan as jest.Mock).mockResolvedValue({ correct: true, object: activePlan });
    (fastingService.addWater as jest.Mock).mockResolvedValue({ correct: true, object: { glasses: 3, goalGlasses: 8 } });
    const { getByText } = render(<FastingDashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText('+ 1 vaso')).toBeTruthy());
    fireEvent.press(getByText('+ 1 vaso'));
    await waitFor(() => expect(fastingService.addWater).toHaveBeenCalledWith('3', 1));
  });

  it('muestra el mensaje del backend cuando la sesion activa falla', async () => {
    (fastingService.getPlan as jest.Mock).mockResolvedValue({ correct: true, object: activePlan });
    (fastingService.getActiveSession as jest.Mock).mockResolvedValue({
      correct: false, message: 'No se pudo leer tu ayuno en curso', object: null,
    });
    const { getByText, queryByText } = render(<FastingDashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText('No se pudo leer tu ayuno en curso')).toBeTruthy());
    expect(queryByText('Iniciar Ayuno')).toBeNull();
  });

  it('sin plan configurado NO es un error: sigue siendo estado vacio', async () => {
    // getPlan responde correct:false con 404 cuando el usuario aun no elige plan.
    const { getByText, queryByText } = render(<FastingDashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText(/No tienes un plan configurado/)).toBeTruthy());
    expect(queryByText('Reintentar')).toBeNull();
  });

  it('cancels an active fast', async () => {
    (fastingService.getPlan as jest.Mock).mockResolvedValue({ correct: true, object: activePlan });
    (fastingService.getActiveSession as jest.Mock).mockResolvedValue({
      correct: true,
      object: { status: 'IN_PROGRESS', startTime: new Date().toISOString(),
                targetEndTime: new Date(Date.now() + 16 * 3600000).toISOString() },
    });
    (fastingService.cancelFasting as jest.Mock).mockResolvedValue({ correct: true });
    const { getByText } = render(<FastingDashboardScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText('Cancelar')).toBeTruthy());
    fireEvent.press(getByText('Cancelar'));
    await waitFor(() => expect(fastingService.cancelFasting).toHaveBeenCalled());
  });
});
