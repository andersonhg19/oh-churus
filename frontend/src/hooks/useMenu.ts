import { useCallback, useEffect, useMemo, useState } from 'react';
import { menuService } from '../services/adminService';
import { ModuloDelMenu, PaginaDelMenu } from '../types';
import { useAuth } from '../contexts/AuthContext';

/**
 * El menu que el SERVIDOR dice que le corresponde a esta persona.
 *
 * Hasta la ola 4, lo que se veia al entrar era un array escrito a mano en
 * AppNavigator.tsx. Ahora sale de la base de datos, y este hook es por donde
 * entra.
 *
 * DOS COSAS QUE NO HACE, y las dos a proposito:
 *
 *  1. NO DECIDE NADA IMPORTANTE. Esconder un boton es cortesia con el usuario;
 *     la seguridad es que la ruta este cerrada en el servidor. Si algun dia
 *     algo que importa depende solo de esto, es un fallo, no una optimizacion.
 *
 *  2. NO BLOQUEA LA APP SI FALLA. Cuando el menu no llega —backend caido,
 *     token viejo, red mala— se cae al rol que trae el token, que es lo que
 *     habia antes de esta ola. Una app que no se puede usar porque no pudo
 *     preguntar que ensenar es peor que una que ensena de mas al administrador.
 */
export function useMenu() {
  const { isAuthenticated, esAdmin } = useAuth();
  const [modules, setModules] = useState<ModuloDelMenu[] | null>(null);
  const [cargando, setCargando] = useState(false);

  const cargar = useCallback(async () => {
    if (!isAuthenticated) {
      setModules(null);
      return;
    }
    setCargando(true);
    try {
      const res = await menuService.mio();
      setModules(res.correct ? (res.object?.modules ?? []) : null);
    } catch {
      /* Sin menu se sigue adelante con el rol del token; ver arriba. */
      setModules(null);
    } finally {
      setCargando(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const rutas = useMemo(() => {
    if (modules === null) return null;
    const mapa = new Map<string, PaginaDelMenu>();
    modules.forEach((modulo) => modulo.pages.forEach((p) => mapa.set(p.route, p)));
    return mapa;
  }, [modules]);

  /**
   * ¿Esta persona tiene esta pantalla?
   *
   * Si el menu no llego, se responde con el rol del token: quien es
   * administrador ve la administracion. Es el comportamiento de antes de la
   * ola 4, que sigue siendo seguro porque el backend comprueba igual.
   */
  const puedeVer = useCallback(
    (route: string, soloAdmin = false) => {
      if (rutas === null) return soloAdmin ? esAdmin : true;
      return rutas.has(route);
    },
    [rutas, esAdmin],
  );

  const permisosDe = useCallback(
    (route: string): PaginaDelMenu | undefined => rutas?.get(route),
    [rutas],
  );

  return { modules, cargando, puedeVer, permisosDe, recargar: cargar };
}
