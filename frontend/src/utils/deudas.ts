import type { Deudas } from '../services/deudaService';

/**
 * Lo que se debe en la vista elegida: en el hogar, todo; en una persona, sus
 * tarjetas y lo que ella paga en cuotas. Asi Inicio y Deudas dicen lo mismo.
 */
export function deudasDelAlcance(d: Deudas, alcance: { tipo: string; userId?: string | number }) {
  const persona = alcance.tipo === 'PERSONA' && alcance.userId != null ? Number(alcance.userId) : null;
  const cards = (d.cards || []).filter((t) => persona == null || Number(t.userId) === persona);
  const loans = (d.loans || []).filter((l) => persona == null || Number(l.payerUserId) === persona);
  const owedOnCards = cards.reduce((a, t) => a + Number(t.owed || 0), 0);
  const owedInInstallments = loans.reduce((a, l) => a + Number(l.remainingTotal || 0), 0);
  return {
    cards, loans, owedOnCards, owedInInstallments,
    totalOwed: owedOnCards + owedInInstallments,
    monthlyInstallments: loans.reduce((a, l) => a + Number(l.monthlyFreed || l.installmentAmount || 0), 0),
  };
}
