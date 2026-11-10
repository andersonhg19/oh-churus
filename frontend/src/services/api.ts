import axios, { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { enEspanol } from '../utils/mensajes';

const TOKEN_KEY = '@oh_churus_token';

const BASE_URL = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8820';

const api: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    try {
      const token = await AsyncStorage.getItem(TOKEN_KEY);
      if (token && config.headers) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    } catch {
      // Continue without token
    }
    return config;
  },
  (error) => Promise.reject(error),
);

/**
 * Que hacer cuando el token caduca.
 *
 * Lo registra AuthContext al arrancar. Vive aqui, y no en cada pantalla,
 * porque la sesion caduca en CUALQUIER peticion: si cada pantalla tuviera
 * que acordarse, siempre habria una que no lo hace.
 */
export type MotivoDeCierre = 'EXPIRADA' | 'SIN_SESION';
type AlCaducarLaSesion = (motivo: MotivoDeCierre) => void;
let alCaducarLaSesion: AlCaducarLaSesion | null = null;

export const registrarCierreDeSesion = (fn: AlCaducarLaSesion | null) => {
  alCaducarLaSesion = fn;
};

/** Si la peticion salio con token. */
const llevabaToken = (config: any): boolean => {
  const h = config?.headers;
  if (!h) return false;
  const v = typeof h.get === 'function' ? h.get('Authorization') : undefined;
  return !!(v || h.Authorization || h.authorization);
};

/** Login y registro no tienen sesion que cerrar: su 401/403 es "datos malos". */
const esDeEntrada = (config: any): boolean =>
  /\/auth\/(login|register)$/i.test(String(config?.url || '').split('?')[0]);

api.interceptors.response.use(
  (response: AxiosResponse) => {
    /* El backend devuelve sus errores con HTTP 200 y { correct: false,
       message }. Parte de esos mensajes vienen en ingles: se traducen aqui,
       una vez, para todas las pantallas. */
    const d: any = response?.data;
    if (d && typeof d === 'object' && d.correct === false && typeof d.message === 'string') {
      d.message = enEspanol(d.message);
    }
    return response;
  },
  async (error) => {
    /*
     * Antes, un 401 se dejaba pasar en silencio: cada pantalla lo recibia
     * como "fallo", se lo tragaba en un catch vacio y pintaba su estado
     * vacio. Resultado: tras unos dias sin abrir la app, entrabas sin pedir
     * contrasena y veias balance $0, "Sin datos" y "Sin nucleo familiar"
     * — indistinguible de haberlo perdido todo.
     *
     * Un token caducado no es una lista vacia. Es volver a la pantalla de
     * entrada, y decirlo.
     */
    const status = error?.response?.status;
    const config = error?.config;
    if (alCaducarLaSesion && !esDeEntrada(config)) {
      try {
        /* 401: el token caduco o no vale. 403 SIN token: la sesion se perdio
           (el gateway responde 403 vacio cuando no llega token). Un 403 CON
           token es otra cosa —p. ej. una pantalla de administracion para quien
           no lo es— y no cierra la sesion. */
        if (status === 401) alCaducarLaSesion('EXPIRADA');
        else if (status === 403 && !llevabaToken(config)) alCaducarLaSesion('SIN_SESION');
      } catch (e) {
        console.error('[api] no se pudo cerrar la sesion caducada', e);
      }
    }
    return Promise.reject(error);
  },
);

export { TOKEN_KEY };
export default api;
