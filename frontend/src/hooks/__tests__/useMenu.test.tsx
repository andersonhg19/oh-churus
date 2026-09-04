import { renderHook, waitFor } from '@testing-library/react-native';
import { useMenu } from '../useMenu';
import { menuService } from '../../services/adminService';
import * as AuthContext from '../../contexts/AuthContext';

jest.mock('../../services/adminService');

const sesion = (isAuthenticated: boolean, esAdmin: boolean) =>
  jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
    user: isAuthenticated
      ? { userId: '1', name: 'A', email: 'a@b.com', budgetStartDay: 1, roles: esAdmin ? ['ADMIN'] : ['USER'] }
      : null,
    token: isAuthenticated ? 'tok' : null,
    loading: false,
    isAuthenticated,
    esAdmin,
    login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
  });

const menuCon = (rutas: string[]) => ({
  correct: true,
  message: 'OK',
  errorCode: 0,
  object: {
    modules: [
      {
        code: 'finanzas',
        name: 'Finanzas',
        sortOrder: 1,
        pages: rutas.map((route, i) => ({
          code: `p${i}`, name: route, route, inMenu: true, sortOrder: i,
          canRead: true, canCreate: false, canUpdate: false, canDelete: false,
        })),
      },
    ],
  },
});

describe('useMenu', () => {
  beforeEach(() => jest.clearAllMocks());

  it('trae del servidor lo que esa persona puede ver', async () => {
    sesion(true, false);
    (menuService.mio as jest.Mock).mockResolvedValue(menuCon(['Dashboard', 'Summary']));

    const { result } = renderHook(() => useMenu());

    await waitFor(() => expect(result.current.modules).not.toBeNull());
    expect(result.current.puedeVer('Dashboard')).toBe(true);
    expect(result.current.puedeVer('AdminUsers', true)).toBe(false);
  });

  it('el permiso de cada pagina llega con la pagina', async () => {
    sesion(true, false);
    (menuService.mio as jest.Mock).mockResolvedValue(menuCon(['Dashboard']));

    const { result } = renderHook(() => useMenu());

    await waitFor(() => expect(result.current.modules).not.toBeNull());
    expect(result.current.permisosDe('Dashboard')?.canRead).toBe(true);
    expect(result.current.permisosDe('Dashboard')?.canDelete).toBe(false);
    expect(result.current.permisosDe('NoExiste')).toBeUndefined();
  });

  it('SI EL MENU NO LLEGA, LA APP SIGUE con el rol del token', async () => {
    /* Una app que no se puede usar porque no pudo preguntar que ensenar es
       peor que una que le ensena de mas al administrador — sobre todo porque
       el backend comprueba igual y responde 403. */
    sesion(true, true);
    (menuService.mio as jest.Mock).mockRejectedValue(new Error('sin red'));

    const { result } = renderHook(() => useMenu());

    await waitFor(() => expect(result.current.cargando).toBe(false));
    expect(result.current.modules).toBeNull();
    expect(result.current.puedeVer('AdminUsers', true)).toBe(true);
    expect(result.current.puedeVer('Dashboard')).toBe(true);
  });

  it('y sin menu, quien no es administrador no ve la administracion', async () => {
    sesion(true, false);
    (menuService.mio as jest.Mock).mockRejectedValue(new Error('sin red'));

    const { result } = renderHook(() => useMenu());

    await waitFor(() => expect(result.current.cargando).toBe(false));
    expect(result.current.puedeVer('AdminUsers', true)).toBe(false);
  });

  it('una respuesta con correct false se trata como si no hubiera menu', async () => {
    sesion(true, true);
    (menuService.mio as jest.Mock).mockResolvedValue({
      correct: false, message: 'no', errorCode: 1, object: null,
    });

    const { result } = renderHook(() => useMenu());

    await waitFor(() => expect(result.current.cargando).toBe(false));
    expect(result.current.modules).toBeNull();
    expect(result.current.puedeVer('AdminUsers', true)).toBe(true);
  });

  it('sin sesion no se pregunta nada', async () => {
    sesion(false, false);

    const { result } = renderHook(() => useMenu());

    await waitFor(() => expect(result.current.modules).toBeNull());
    expect(menuService.mio).not.toHaveBeenCalled();
  });

  it('un menu vacio es un menu, no un fallo: no se ve nada', async () => {
    sesion(true, true);
    (menuService.mio as jest.Mock).mockResolvedValue({
      correct: true, message: 'OK', errorCode: 0, object: { modules: [] },
    });

    const { result } = renderHook(() => useMenu());

    await waitFor(() => expect(result.current.modules).toEqual([]));
    expect(result.current.puedeVer('AdminUsers', true)).toBe(false);
    expect(result.current.puedeVer('Dashboard')).toBe(false);
  });
});
