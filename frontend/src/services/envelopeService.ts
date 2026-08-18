import api from './api';
import { ResultDTO, EnvelopeState, EnvelopeMove } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/budget-allocation';

/**
 * Los sobres: cuanto tienes en cada categoria contando lo que sobro del mes
 * pasado.
 *
 * LA REGLA, en una frase, y es asimetrica a proposito: lo que SOBRA se queda
 * en la categoria; lo que te PASASTE no baja a la categoria, se descuenta de
 * lo que tienes para repartir este mes.
 *
 * Nada de esto se guarda: el arrastre se recalcula desde el primer periodo con
 * datos en cada peticion. Por eso aqui no hay que invalidar ninguna cache
 * cuando se edita un gasto viejo — el siguiente `state()` ya viene corregido.
 */
export const envelopeService = {
  state: async (budgetStartDay: number, referenceDate?: string) => {
    const response = await api.post<ResultDTO<EnvelopeState>>(`${BASE}/envelopes`, {
      budgetStartDay,
      referenceDate,
    });
    return response.data;
  },

  /**
   * Mover plata de un sobre a otro dentro del mismo periodo.
   *
   * Es lo que hace util la regla asimetrica: cuando te pasaste en
   * Restaurantes, la respuesta no es sentirse mal, es decidir de que otro
   * sobre sale.
   */
  move: async (
    fromCategoryId: string,
    toCategoryId: string,
    amount: number,
    budgetStartDay: number,
    referenceDate?: string,
  ) => {
    const response = await api.post<ResultDTO<EnvelopeMove>>(`${BASE}/move`, {
      fromCategoryId,
      toCategoryId,
      amount,
      budgetStartDay,
      referenceDate,
    });
    return response.data;
  },
};
