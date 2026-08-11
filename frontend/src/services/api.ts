import axios, { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';

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
type AlCaducarLaSesion = () => void;
let alCaducarLaSesion: AlCaducarLaSesion | null = null;

export const registrarCierreDeSesion = (fn: AlCaducarLaSesion | null) => {
  alCaducarLaSesion = fn;
};

api.interceptors.response.use(
  (response: AxiosResponse) => response,
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
    if (error?.response?.status === 401 && alCaducarLaSesion) {
      alCaducarLaSesion();
    }
    return Promise.reject(error);
  },
);

export { TOKEN_KEY };
export default api;
