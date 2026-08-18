import api from './api';
import { ResultDTO, ImportPreview, ImportResult, ImportProfile, ImportRowChoice } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/import';

export interface ImportMapping {
  profileId?: string;
  bankName?: string;
  dateColumn?: number;
  amountColumn?: number;
  descriptionColumn?: number;
  externalIdColumn?: number;
  datePattern?: string;
  decimalSeparator?: string;
  hasHeader?: boolean;
  invertSign?: boolean;
  rememberProfile?: boolean;
  accountId?: string;
}

/**
 * Importar el extracto del banco.
 *
 * DOS PASOS, y el primero NO ESCRIBE NADA. `preview` dice que va a pasar con
 * las sesenta filas; `confirm` hace solo lo que se acepto. Un importador que
 * escribe primero y deja arreglar el desastre despues es peor que no tener
 * importador: con sesenta filas mal metidas, la unica salida real es borrar el
 * mes entero.
 *
 * El CSV viaja tambien en la confirmacion a proposito. Podria parecer un
 * desperdicio, pero asi el servidor vuelve a leer EXACTAMENTE lo mismo que se
 * vio en la vista previa; mandar las filas ya interpretadas dejaria que lo que
 * se guarda no sea lo que se enseno.
 */
export const importService = {
  preview: async (csv: string, mapping: ImportMapping) => {
    const response = await api.post<ResultDTO<ImportPreview>>(`${BASE}/preview`, {
      csv,
      ...mapping,
    });
    return response.data;
  },

  confirm: async (csv: string, mapping: ImportMapping, rows: ImportRowChoice[]) => {
    const response = await api.post<ResultDTO<ImportResult>>(`${BASE}/confirm`, {
      csv,
      ...mapping,
      rows,
    });
    return response.data;
  },

  profiles: async () => {
    const response = await api.post<ResultDTO<ImportProfile[]>>(`${BASE}/profiles`, {});
    return response.data;
  },
};
