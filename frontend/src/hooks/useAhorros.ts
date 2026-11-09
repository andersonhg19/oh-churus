import { useEffect, useState } from 'react';
import { useAlcance } from '../contexts/AlcanceContext';
import { estadoService } from '../services/estadoService';

export interface Ahorro { id: string; nombre: string }

/**
 * Las categorias de ahorro del hogar (las que tienen una meta de ahorro en el
 * plan): de ahi puede salir la plata de un gasto que no se pago con lo del
 * mes, como la reparacion del carro.
 */
export function useAhorros(): Ahorro[] {
  /* Fuera del proveedor (una pantalla suelta) simplemente no hay ahorros. */
  let alc: ReturnType<typeof useAlcance> | null = null;
  try { alc = useAlcance(); } catch { alc = null; }
  const corte = alc?.corte;
  const inicio = alc?.periodo.inicio;
  const [ahorros, setAhorros] = useState<Ahorro[]>([]);
  useEffect(() => {
    if (!corte || !inicio) return undefined;
    let vivo = true;
    estadoService.periodo(corte, inicio, { tipo: 'HOGAR' })
      .then((r) => {
        if (!vivo || !r?.correct) return;
        setAhorros((r.object?.categories || []).filter((c) => c.kind === 'SAVING')
          .map((c) => ({ id: String(c.categoryId), nombre: c.categoryName })));
      })
      .catch(() => { /* sin ahorros no se ofrece la opcion */ });
    return () => { vivo = false; };
  }, [corte, inicio]);
  return ahorros;
}
