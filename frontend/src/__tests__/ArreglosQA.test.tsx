import React from 'react';
import { Text, TouchableOpacity } from 'react-native';
import { render, fireEvent, waitFor, act } from '@testing-library/react-native';
import { getStateFromPath, getPathFromState } from '@react-navigation/native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import ErrorBoundary from '../components/organisms/ErrorBoundary';
import { ThemeProvider } from '../contexts/ThemeContext';
import { AuthProvider, useAuth, AVISO_SESION_EXPIRADA } from '../contexts/AuthContext';
import api, { registrarCierreDeSesion } from '../services/api';
import { RUTAS, sinParametrosObjeto, irAlInicio, volverOInicio, linking } from '../navigation/linking';
import { enEspanol, mensajeDelServidor } from '../utils/mensajes';
import { fechaHumana, humanizarAviso, plural } from '../utils/format';
import { validatePasswordSegura, MENSAJE_CONTRASENA_SEGURA } from '../utils/validators';

jest.mock('../services/authService', () => ({
  authService: { login: jest.fn(), register: jest.fn(), getUser: jest.fn().mockResolvedValue({ correct: false }) },
}));
jest.mock('../services/adminService', () => ({
  preferenciasService: { mias: jest.fn().mockResolvedValue({ correct: false }) },
}));

const Tema: React.FC<{ children: React.ReactNode }> = ({ children }) => <ThemeProvider>{children}</ThemeProvider>;

describe('Limite de errores por pantalla', () => {
  const Rota: React.FC<{ romper: { si: boolean } }> = ({ romper }) => {
    if (romper.si) throw new Error('etiqueta de undefined');
    return <Text>Pantalla bien</Text>;
  };

  it('un fallo al pintar muestra la tarjeta, lo deja en consola y "Reintentar" vuelve a intentar', () => {
    const espia = jest.spyOn(console, 'error').mockImplementation(() => {});
    const romper = { si: true };
    const alInicio = jest.fn();
    const { getByText, queryByText } = render(
      <Tema><ErrorBoundary nombre="Plan" onVolverAlInicio={alInicio}><Rota romper={romper} /></ErrorBoundary></Tema>);
    expect(getByText('Algo salió mal en esta pantalla')).toBeTruthy();
    expect(espia.mock.calls.some((c) => String(c[0]).includes('[ErrorBoundary:Plan]'))).toBe(true);
    romper.si = false;
    fireEvent.press(getByText('Reintentar'));
    expect(getByText('Pantalla bien')).toBeTruthy();
    expect(queryByText('Algo salió mal en esta pantalla')).toBeNull();
    espia.mockRestore();
  });

  it('"Volver al inicio" llama a la navegación; sin ella no se ofrece', () => {
    const espia = jest.spyOn(console, 'error').mockImplementation(() => {});
    const alInicio = jest.fn();
    const { getByText, rerender, queryByText } = render(
      <Tema><ErrorBoundary onVolverAlInicio={alInicio}><Rota romper={{ si: true }} /></ErrorBoundary></Tema>);
    fireEvent.press(getByText('Volver al inicio'));
    expect(alInicio).toHaveBeenCalled();
    rerender(<Tema><ErrorBoundary key="otro"><Rota romper={{ si: true }} /></ErrorBoundary></Tema>);
    expect(queryByText('Volver al inicio')).toBeNull();
    espia.mockRestore();
  });
});

describe('Rutas legibles (web)', () => {
  it('cada pantalla tiene su ruta y entrar directo deja el inicio debajo', () => {
    const estado: any = getStateFromPath('/presupuesto/categoria/5', RUTAS as any);
    const pila = estado.routes[0].state.routes[0].state;
    expect(pila.routes.map((r: any) => r.name)).toEqual(['Inicio', 'Categoria']);
    expect(pila.routes[1].params).toEqual({ categoryId: 5 });
  });

  it('un parámetro inválido no rompe: queda vacío', () => {
    const estado: any = getStateFromPath('/presupuesto/categoria/abc', RUTAS as any);
    const pila = estado.routes[0].state.routes[0].state;
    expect(pila.routes[1].params?.categoryId).toBeUndefined();
  });

  it('por-confirmar acepta "MIOS" o el id de una persona', () => {
    const a: any = getStateFromPath('/por-confirmar?quien=4', RUTAS as any);
    expect(a.routes[0].state.routes[0].state.routes[1].params).toEqual({ quien: 4 });
    const b: any = getStateFromPath('/por-confirmar?quien=DOS', RUTAS as any);
    expect(b.routes[0].state.routes[0].state.routes[1].params).toEqual({ quien: 'DOS' });
  });

  it('los objetos (un movimiento que se edita) no se escriben en la URL', () => {
    const estado = {
      routes: [{ name: 'MainTabs', state: { routes: [{ name: 'FinanceModule', state: { routes: [
        { name: 'Inicio' }, { name: 'CategoryForm', params: { category: { id: '1', name: 'Casa' } } },
      ] } }] } }],
    };
    const limpio = sinParametrosObjeto(estado);
    expect(limpio.routes[0].state.routes[0].state.routes[1].params).toBeUndefined();
    const ruta = linking.getPathFromState!(estado as any, RUTAS as any);
    expect(ruta).toBe('/ajustes/categorias/formulario');
    expect(ruta).not.toContain('object');
    expect(getPathFromState({ routes: [{ name: 'MainTabs', state: { routes: [{ name: 'FinanceModule', state: { routes: [{ name: 'Inicio' }, { name: 'Deudas' }] } }] } }] } as any, RUTAS as any)).toBe('/deudas');
  });

  it('volver: atrás si hay a dónde; si no, al inicio', () => {
    const conPila = { canGoBack: () => true, goBack: jest.fn(), navigate: jest.fn(), getState: () => ({ routeNames: ['Inicio'] }) };
    volverOInicio(conPila);
    expect(conPila.goBack).toHaveBeenCalled();
    const sinPila = { canGoBack: () => false, goBack: jest.fn(), navigate: jest.fn(), getState: () => ({ routeNames: ['Inicio', 'Presupuesto'] }) };
    volverOInicio(sinPila);
    expect(sinPila.navigate).toHaveBeenCalledWith('Inicio');
    const raiz = { canGoBack: () => false, navigate: jest.fn(), getState: () => ({ routeNames: ['MainTabs', 'Anotar'] }) };
    irAlInicio(raiz);
    expect(raiz.navigate).toHaveBeenCalledWith('MainTabs', { screen: 'FinanceModule', params: { screen: 'Inicio' } });
    expect(() => irAlInicio(undefined)).not.toThrow();
  });
});

describe('Sesión caducada (401/403)', () => {
  const rechazo = (api.interceptors.response as any).handlers[0].rejected;
  const exito = (api.interceptors.response as any).handlers[0].fulfilled;
  afterEach(() => registrarCierreDeSesion(null));

  it('401 cierra la sesión; 403 sin token también; 403 con token (administración) no', async () => {
    const cierre = jest.fn();
    registrarCierreDeSesion(cierre);
    await expect(rechazo({ response: { status: 401 }, config: { url: '/BUDGET-SERVICE/x', headers: { Authorization: 'Bearer t' } } })).rejects.toBeTruthy();
    expect(cierre).toHaveBeenLastCalledWith('EXPIRADA');
    await expect(rechazo({ response: { status: 403 }, config: { url: '/BUDGET-SERVICE/x', headers: {} } })).rejects.toBeTruthy();
    expect(cierre).toHaveBeenLastCalledWith('SIN_SESION');
    cierre.mockClear();
    await expect(rechazo({ response: { status: 403 }, config: { url: '/AUTH-SERVICE/oh-churus/v1/admin/audit/all', headers: { Authorization: 'Bearer t' } } })).rejects.toBeTruthy();
    await expect(rechazo({ response: { status: 401 }, config: { url: '/AUTH-SERVICE/oh-churus/v1/auth/login', headers: {} } })).rejects.toBeTruthy();
    await expect(rechazo({ response: { status: 500 }, config: { url: '/x', headers: {} } })).rejects.toBeTruthy();
    expect(cierre).not.toHaveBeenCalled();
  });

  it('los mensajes de error del servidor llegan en español', () => {
    const r = exito({ data: { correct: false, message: 'Invalid email or password' } });
    expect(r.data.message).toBe('Correo o contraseña incorrectos.');
    const ok = exito({ data: { correct: true, message: 'OK' } });
    expect(ok.data.message).toBe('OK');
  });

  it('con sesión abierta, un 401 lleva a la entrada y deja el aviso en español', async () => {
    (AsyncStorage.getItem as jest.Mock).mockImplementation((k: string) => Promise.resolve(
      k === '@oh_churus_token' ? 'tok' : k === '@oh_churus_user' ? JSON.stringify({ userId: '3', name: 'Andy', email: 'a@b.com' }) : null));
    const Sonda = () => {
      const { isAuthenticated, avisoDeSesion, limpiarAvisoDeSesion } = useAuth();
      return (
        <>
          <Text testID="dentro">{String(isAuthenticated)}</Text>
          <Text testID="aviso">{avisoDeSesion || 'ninguno'}</Text>
          <TouchableOpacity testID="limpiar" onPress={() => limpiarAvisoDeSesion?.()} />
        </>
      );
    };
    const { getByTestId } = render(<Tema><AuthProvider><Sonda /></AuthProvider></Tema>);
    await waitFor(() => expect(getByTestId('dentro').props.children).toBe('true'));
    await act(async () => {
      await rechazo({ response: { status: 401 }, config: { url: '/BUDGET-SERVICE/x', headers: { Authorization: 'Bearer tok' } } }).catch(() => {});
    });
    expect(getByTestId('dentro').props.children).toBe('false');
    expect(getByTestId('aviso').props.children).toBe(AVISO_SESION_EXPIRADA);
    expect(AsyncStorage.removeItem).toHaveBeenCalledWith('@oh_churus_token');
    fireEvent.press(getByTestId('limpiar'));
    expect(getByTestId('aviso').props.children).toBe('ninguno');
    (AsyncStorage.getItem as jest.Mock).mockImplementation(() => Promise.resolve(null));
  });
});

describe('Textos', () => {
  it('traduce los mensajes conocidos del backend y deja los demás', () => {
    expect(enEspanol('Category name already exists at this level')).toBe('Ya existe una categoría con ese nombre.');
    expect(enEspanol('Movement not found')).toBe('No se encontró el movimiento.');
    expect(enEspanol('Error saving movement')).toBe('Algo falló en el servidor. Inténtalo otra vez.');
    expect(enEspanol('amount: must be less than or equal to 500')).toBe('El valor debe ser como máximo 500.');
    expect(enEspanol('Demasiados intentos. Espera unos minutos y vuelve a intentarlo.'))
      .toBe('Demasiados intentos. Espera unos minutos y vuelve a intentarlo.');
    expect(enEspanol('')).toBe('');
  });

  it('del error HTTP se muestra el mensaje del servidor (el 429 del login, el 400 de invitar)', () => {
    expect(mensajeDelServidor({ response: { status: 429, data: { message: 'Demasiados intentos. Espera unos minutos y vuelve a intentarlo.' } } }, 'x'))
      .toBe('Demasiados intentos. Espera unos minutos y vuelve a intentarlo.');
    expect(mensajeDelServidor({ message: 'Request failed with status code 400' }, 'No se pudo')).toBe('No se pudo');
    expect(mensajeDelServidor({ message: 'Correo ocupado' }, 'No se pudo')).toBe('Correo ocupado');
  });

  it('fechas y cifras de los avisos, como las dice una persona', () => {
    const hoy = new Date(2026, 10, 10);
    expect(fechaHumana('2026-11-01', hoy)).toBe('1 nov');
    expect(fechaHumana('2027-01-03', hoy)).toBe('3 ene 2027');
    expect(humanizarAviso('Gym vencio el 2026-11-01', hoy)).toBe('Gym venció el 1 nov');
    expect(humanizarAviso('Carro: quedan 1 cuota(s); al terminar se liberan 190000.00 al mes', hoy))
      .toBe('Carro: quedan 1 cuota; al terminar se liberan $190.000 al mes');
    expect(humanizarAviso('Esta quincena no alcanza: faltan 50000. Revisa que provision se puede mover', hoy))
      .toBe('Esta quincena no alcanza: faltan $50.000. Revisa qué plata separada se puede mover');
    expect(plural(1, 'periodo')).toBe('1 periodo');
    expect(plural(3, 'periodo')).toBe('3 periodos');
  });

  it('la contraseña nueva pide 8 caracteres, una letra y un número', () => {
    expect(validatePasswordSegura('')).toBe('La contraseña es obligatoria');
    expect(validatePasswordSegura('abc12')).toBe(MENSAJE_CONTRASENA_SEGURA);
    expect(validatePasswordSegura('abcdefgh')).toBe(MENSAJE_CONTRASENA_SEGURA);
    expect(validatePasswordSegura('12345678')).toBe(MENSAJE_CONTRASENA_SEGURA);
    expect(validatePasswordSegura('clave2026')).toBeNull();
  });
});
