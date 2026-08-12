import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import FastingConfigScreen from '../fasting/FastingConfigScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import { fastingService } from '../../services/fastingService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: any) => { const { useEffect } = require('react'); useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, []); },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../../services/fastingService', () => ({
  fastingService: { getPlan: jest.fn(), getPresets: jest.fn(), savePlan: jest.fn() },
}));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '3', name: 'Anderson', email: 'a@b.com', budgetStartDay: 1 },
  token: 'tok', loading: false, isAuthenticated: true,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
});

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('FastingConfigScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (fastingService.getPlan as jest.Mock).mockResolvedValue({ correct: true, object: { planType: 'PLAN_16_8', fastingHours: 16, eatingHours: 8 } });
    (fastingService.getPresets as jest.Mock).mockResolvedValue({
      correct: true,
      object: [
        { planType: 'PLAN_16_8', label: '16:8', fastingHours: 16, eatingHours: 8 },
        { planType: 'PLAN_18_6', label: '18:6', fastingHours: 18, eatingHours: 6 },
      ],
    });
  });

  it('renders presets after loading', async () => {
    const { getByText } = render(<FastingConfigScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(fastingService.getPresets).toHaveBeenCalled());
    expect(getByText(/Planes predefinidos/)).toBeTruthy();
  });

  /* El catch vacio dejaba la rejilla de planes vacia cuando fallaba la carga:
     igual que si el backend no ofreciera ningun plan predefinido. */
  it('muestra el mensaje del backend cuando fallan los presets', async () => {
    (fastingService.getPresets as jest.Mock).mockResolvedValue({
      correct: false, message: 'Presets no disponibles',
    });

    const { getByText } = render(<FastingConfigScreen />, { wrapper: Wrapper });

    await waitFor(() => expect(getByText('Presets no disponibles')).toBeTruthy());
    expect(getByText('Reintentar')).toBeTruthy();
  });

  it('reintentar vuelve a pedir los datos y pinta la pantalla', async () => {
    (fastingService.getPresets as jest.Mock).mockResolvedValueOnce({
      correct: false, message: 'Sin conexion con el servicio de ayuno',
    });

    const { getByText } = render(<FastingConfigScreen />, { wrapper: Wrapper });
    await waitFor(() => expect(getByText('Sin conexion con el servicio de ayuno')).toBeTruthy());

    fireEvent.press(getByText('Reintentar'));

    await waitFor(() => expect(getByText(/Planes predefinidos/)).toBeTruthy());
  });

  /* getPlan responde correct:false mientras el usuario no ha elegido plan:
     eso NO puede pintarse como fallo o nadie podria configurar el primero. */
  it('un usuario sin plan ve el formulario, no la pantalla de error', async () => {
    (fastingService.getPlan as jest.Mock).mockResolvedValue({
      correct: false, message: 'El usuario no tiene plan',
    });

    const { getByText, queryByText } = render(<FastingConfigScreen />, { wrapper: Wrapper });

    await waitFor(() => expect(getByText(/Planes predefinidos/)).toBeTruthy());
    expect(queryByText('El usuario no tiene plan')).toBeNull();
    expect(queryByText('Reintentar')).toBeNull();
  });
});
