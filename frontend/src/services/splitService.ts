import api from './api';
import { ResultDTO, BalanceList, Settlement } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/splits';

/**
 * El reparto EN SI no vive aqui: viaja dentro del movimiento al guardarlo
 * (`splitMode` y `splits` en movementService.save). Es deliberado — repartir es
 * parte de anotar el gasto, no un segundo paso, y un flujo de dos pasos
 * garantiza que a la mitad de los gastos se les olvide el segundo.
 *
 * Aqui esta lo que no cuelga de un movimiento concreto: con quien estas en
 * deuda, y anotar que ya se pago.
 */
export const splitService = {
  /**
   * El balance neto con cada persona.
   *
   * No se pasa periodo a proposito: una deuda no caduca a fin de mes. Si el
   * mercado de marzo sigue sin saldarse, tiene que seguir apareciendo en
   * octubre. Es justo la diferencia entre esta pantalla y el panel.
   */
  balances: async () => {
    const response = await api.post<ResultDTO<BalanceList>>(`${BASE}/balances`, {});
    return response.data;
  },

  /**
   * Anotar que una deuda se pago.
   *
   * `amount` es opcional: sin el se salda el neto entero, que es lo que se
   * quiere casi siempre. Se admite parcial porque a veces se paga a plazos, y
   * obligar a todo o nada haria que no se anotara.
   *
   * Quien paga NO lo decide esta llamada: lo decide el signo del balance en el
   * backend. Si no, cualquiera podria "cobrarse" una deuda que en realidad tiene.
   */
  settle: async (withUserId: number, amount?: number, accountId?: string) => {
    const response = await api.post<ResultDTO<Settlement>>(`${BASE}/settle`, {
      withUserId,
      amount,
      accountId,
    });
    return response.data;
  },
};
