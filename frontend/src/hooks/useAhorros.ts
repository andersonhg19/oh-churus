import { useEffect, useState } from 'react';
import { useAlcance } from '../contexts/AlcanceContext';
import { estadoService } from '../services/estadoService';
import { deudaService, Tarjeta } from '../services/deudaService';

export interface Ahorro {
  id: string;
  nombre: string;
  /** Cuanto hay en el ahorro (todo el hogar), al cierre del periodo. */
  saldo: number;
  color?: string | null;
  icono?: string | null;
}

/**
 * Las categorias de ahorro del hogar (las que tienen una meta de ahorro en el
 * plan), con su saldo: de ahi puede salir la plata de un gasto que no se pago
 * con lo del mes, como la reparacion del carro.
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
          .map((c) => ({
            id: String(c.categoryId), nombre: c.categoryName, saldo: Number(c.balance || 0),
            color: c.categoryColor, icono: c.categoryIcon,
          })));
      })
      .catch(() => { /* sin ahorros no se ofrece la opcion */ });
    return () => { vivo = false; };
  }, [corte, inicio]);
  return ahorros;
}

/** Mis tarjetas de credito (las que puedo elegir en "Pagado con"). */
export function useMisTarjetas(): Tarjeta[] {
  const [tarjetas, setTarjetas] = useState<Tarjeta[]>([]);
  useEffect(() => {
    let vivo = true;
    deudaService.misTarjetas()
      .then((r) => { if (vivo && r?.correct) setTarjetas(r.object || []); })
      .catch(() => { /* sin tarjetas no se ofrecen */ });
    return () => { vivo = false; };
  }, []);
  return tarjetas;
}
