import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useAuth } from './AuthContext';
import { householdService } from '../services/householdService';
import { Alcance } from '../services/estadoService';
import { getStartOfPeriod, getEndOfPeriod, navigatePeriod } from '../utils/periodUtils';

/*
 * Lo que todas las pantallas comparten: de QUIEN se habla (el hogar, Andy o
 * Samy) y de QUE periodo. Antes cada pantalla calculaba su periodo una vez al
 * montarse, y al cambiar el dia de corte del 1 al 28 seguian ensenando el
 * periodo viejo hasta recargar. Aqui el periodo se recalcula cuando cambia el
 * dia de corte, y al cambiar de pantalla no se pierde lo elegido.
 */

export interface Miembro {
  userId: number;
  nombre: string;
}

interface Valor {
  alcance: Alcance;
  setAlcance: (a: Alcance) => void;
  miembros: Miembro[];
  nombreDe: (userId?: number | string) => string;
  etiquetaAlcance: string;
  corte: number;
  periodo: { inicio: string; fin: string };
  anterior: () => void;
  siguiente: () => void;
  puedeSeguir: boolean;
  irAHoy: () => void;
  recargarMiembros: () => void;
}

const Contexto = createContext<Valor | null>(null);

/* Hasta tres periodos hacia adelante: lo suficiente para planear, sin dejar
   navegar a un futuro donde nada esta generado. */
const PERIODOS_FUTUROS = 3;

export const AlcanceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  const corte = user?.budgetStartDay || 1;
  const [alcance, setAlcance] = useState<Alcance>({ tipo: 'HOGAR' });
  const [miembros, setMiembros] = useState<Miembro[]>([]);
  const [inicio, setInicio] = useState(() => getStartOfPeriod(corte, new Date()));

  useEffect(() => {
    setInicio(getStartOfPeriod(corte, new Date()));
  }, [corte]);

  const recargarMiembros = useCallback(async () => {
    if (!user) return;
    try {
      const r = await householdService.getByUser(user.userId);
      const lista = r.correct && r.object ? r.object.flatMap((h) => h.members) : [];
      const unicos = lista.filter((m, i) => lista.findIndex((x) => x.userId === m.userId) === i);
      setMiembros(unicos.map((m) => ({
        userId: m.userId,
        nombre: m.displayName || (String(m.userId) === String(user.userId) ? user.name : `Persona ${m.userId}`),
      })));
    } catch {
      setMiembros([]);
    }
  }, [user]);

  useEffect(() => { recargarMiembros(); }, [recargarMiembros]);

  const nombreDe = useCallback((userId?: number | string) => {
    if (userId === undefined || userId === null) return '';
    return miembros.find((m) => String(m.userId) === String(userId))?.nombre || '';
  }, [miembros]);

  const limite = useMemo(() => {
    let p = getStartOfPeriod(corte, new Date());
    for (let i = 0; i < PERIODOS_FUTUROS; i++) p = navigatePeriod(p, corte, 'next').start;
    return p;
  }, [corte]);

  const valor: Valor = {
    alcance,
    setAlcance,
    miembros,
    nombreDe,
    etiquetaAlcance: alcance.tipo === 'HOGAR' ? 'Hogar' : nombreDe(alcance.userId) || 'Yo',
    corte,
    periodo: { inicio, fin: getEndOfPeriod(corte, inicio) },
    anterior: () => setInicio(navigatePeriod(inicio, corte, 'prev').start),
    siguiente: () => { if (inicio < limite) setInicio(navigatePeriod(inicio, corte, 'next').start); },
    puedeSeguir: inicio < limite,
    irAHoy: () => setInicio(getStartOfPeriod(corte, new Date())),
    recargarMiembros,
  };
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
};

export function useAlcance(): Valor {
  const v = useContext(Contexto);
  if (!v) throw new Error('useAlcance fuera de AlcanceProvider');
  return v;
}
