import api from './api';
import { ResultDTO, Account, AccountList, AccountKind, Reconciliation } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/accounts';

/**
 * Fijate en lo que NO se manda en ninguna llamada: userId.
 *
 * La identidad sale del token, y el backend ignora cualquier userId del
 * cuerpo. Mandarlo aqui no romperia nada, pero volveria a sugerir que el
 * cliente elige de quien son los datos — que es el agujero que costo dos olas
 * cerrar. Hay una prueba de arquitectura que rompe el build si reaparece.
 */
export const accountService = {
  save: async (data: Partial<Account> & { openingBalance?: number; openingDate?: string }) => {
    const response = await api.post<ResultDTO<Account>>(`${BASE}/save`, data);
    return response.data;
  },

  getById: async (id: string) => {
    const response = await api.post<ResultDTO<Account>>(`${BASE}/get/${id}`);
    return response.data;
  },

  getAll: async () => {
    const response = await api.post<ResultDTO<AccountList>>(`${BASE}/all`, {});
    return response.data;
  },

  delete: async (id: string) => {
    const response = await api.post<ResultDTO<null>>(`${BASE}/delete/${id}`);
    return response.data;
  },

  /**
   * Conciliar contra el extracto.
   *
   * Con `apply` en falso solo pregunta y devuelve la diferencia; con `apply`
   * en cierto anota el movimiento de ajuste. Se deja explicito porque casi
   * siempre la respuesta correcta a "no cuadra" no es ajustar: es acordarse
   * del gasto que falta por anotar.
   */
  reconcile: async (accountId: string, realBalance: number, apply: boolean, date?: string) => {
    const response = await api.post<ResultDTO<Reconciliation>>(`${BASE}/reconcile`, {
      accountId,
      realBalance,
      apply,
      date,
    });
    return response.data;
  },

  kindList: async () => {
    const response = await api.post<ResultDTO<Array<{ code: AccountKind; label: string }>>>(
      `${BASE}/kind-list`,
    );
    return response.data;
  },
};
