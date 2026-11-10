import React from 'react';
import * as fs from 'fs';
import * as path from 'path';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AlcanceContext from '../../contexts/AlcanceContext';
import * as ToastContext from '../../contexts/ToastContext';
import AhorrosScreen from '../ahorros/AhorrosScreen';
import DeudasScreen from '../deudas/DeudasScreen';
import { validarTarjeta } from '../deudas/FormTarjeta';
import { estadoService } from '../../services/estadoService';
import { deudaService } from '../../services/deudaService';

const mockNavegar = jest.fn();
jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: () => (() => void) | void) => {
    const { useEffect } = require('react');
    useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, [cb]);
  },
  useNavigation: () => ({ navigate: mockNavegar, goBack: jest.fn(), dispatch: jest.fn() }),
  DrawerActions: { openDrawer: () => ({ type: 'OPEN_DRAWER' }) },
}));
jest.mock('../../services/estadoService', () => ({ estadoService: { periodo: jest.fn(), historial: jest.fn() } }));
jest.mock('../../services/scheduledService', () => ({ scheduledService: { save: jest.fn() } }));
jest.mock('../../services/categoryService', () => ({ categoryService: { getAll: jest.fn() } }));
jest.mock('../../services/deudaService', () => ({
  deudaService: { todas: jest.fn(), pagarTarjeta: jest.fn(), guardarTarjeta: jest.fn(), borrarTarjeta: jest.fn(), adelantarCuota: jest.fn(), misTarjetas: jest.fn() },
}));

const toast = jest.fn();
jest.spyOn(ToastContext, 'useToast').mockReturnValue({ showToast: toast } as any);
jest.spyOn(AlcanceContext, 'useAlcance').mockReturnValue({
  alcance: { tipo: 'HOGAR' }, setAlcance: jest.fn(),
  miembros: [{ userId: 3, nombre: 'Andy' }, { userId: 4, nombre: 'Samy' }],
  nombreDe: () => '', etiquetaAlcance: 'Hogar', corte: 28, periodo: { inicio: '2026-10-28', fin: '2026-11-27' },
  anterior: jest.fn(), siguiente: jest.fn(), puedeSeguir: true, irAHoy: jest.fn(), recargarMiembros: jest.fn(),
} as any);

const Envoltura: React.FC<{ children: React.ReactNode }> = ({ children }) => <ThemeProvider>{children}</ThemeProvider>;
const nav = () => ({ navigate: mockNavegar, goBack: jest.fn() });

const ahorro = (over: object) => ({
  categoryId: 7, categoryName: 'Ahorro', shared: true, kind: 'SAVING', allocated: 3000000, actual: 1000000,
  committed: 0, available: 0, difference: 0, executionPct: 33, status: 'EN_CURSO', hasAllocation: true,
  plannedInItems: 0, provision: 0, provisionLeft: 0, otherSpending: 0, byPerson: [],
  items: [{ scheduledMovementId: 1, name: 'Ahorro quincena', date: '2026-11-01', amount: 1000000, status: 'PAGADO', kind: 'SAVING', payerUserId: 3, payerName: 'Andy' }],
  balance: 5200000, withdrawn: 800000,
  withdrawals: [{ movementId: 99, date: '2026-11-06', amount: 800000, description: 'Arreglo carro', toCategoryId: 12, toCategoryName: 'Carro', userId: 3 }],
  ...over,
});
const estado = (categories: object[]) => ({
  correct: true,
  object: { scope: 'HOGAR', periodStart: '2026-10-28', periodEnd: '2026-11-27', closed: false, totals: {}, compliance: {},
    categories, pendingCount: 0, incomeByPerson: [], incomeItems: [], responsibilities: [] },
});

const deudas = {
  totalOwed: 4600000, owedOnCards: 2200000, owedInInstallments: 2400000, monthlyInstallments: 600000,
  cards: [
    { id: 1, name: 'Visa', color: '#2196F3', paymentDay: 15, userId: 3, ownerName: 'Andy', mine: true, owed: 2200000, dueNow: 400000, upcoming: 1800000,
      purchases: [{ purchaseId: 5, description: 'Nevera', categoryId: 2, categoryName: 'Casa', date: '2026-09-15', total: 2400000, owed: 1800000, installments: 12, paidInstallments: 3, nextDate: '2026-12-15', nextAmount: 200000 }] },
    { id: 2, name: 'Master', userId: 4, ownerName: 'Samy', mine: false, owed: 0, dueNow: 0, upcoming: 0, purchases: [] },
    { id: 3, name: 'Vieja', userId: 3, ownerName: 'Andy', mine: true, owed: 0, dueNow: 0, upcoming: 0, purchases: [] },
  ],
  loans: [{ scheduledMovementId: 40, name: 'Crédito carro', installmentAmount: 600000, totalInstallments: 10, paidInstallments: 6,
    remainingInstallments: 4, remainingTotal: 2400000, nextDate: '2026-11-28', endDate: '2027-03-28', monthlyFreed: 600000,
    kind: 'PAYMENT', payerUserId: 3, payerName: 'Andy', categoryId: 12, categoryName: 'Carro' }],
};

beforeEach(() => {
  jest.clearAllMocks();
  (estadoService.periodo as jest.Mock).mockResolvedValue(estado([ahorro({}), ahorro({ categoryId: 8, categoryName: 'Viaje', balance: 300000, allocated: 0, actual: 0, withdrawn: 0, withdrawals: [], items: [] })]));
  (deudaService.todas as jest.Mock).mockResolvedValue({ correct: true, object: deudas });
});

describe('Ahorros', () => {
  it('suma lo que hay en todos los ahorros y muestra cada uno con su meta', async () => {
    const { findByLabelText, getByText, getByLabelText } = render(<Envoltura><AhorrosScreen navigation={nav()} /></Envoltura>);
    expect(await findByLabelText(/Hay \$5\.500\.000 ahorrado/)).toBeTruthy();
    expect(getByLabelText(/Ahorro: hay \$5\.200\.000/)).toBeTruthy();
    expect(getByText(/Este mes: \+\$1\.000\.000 · −\$800\.000/)).toBeTruthy();
    expect(getByText('Meta del mes $3.000.000: 33%')).toBeTruthy();
    expect(estadoService.periodo).toHaveBeenCalledWith(28, '2026-10-28', { tipo: 'HOGAR' });
  });

  it('Depositar y Usar abren Anotar con la categoría o el ahorro como medio de pago', async () => {
    const { findByLabelText, getByLabelText } = render(<Envoltura><AhorrosScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Depositar en Ahorro'));
    expect(mockNavegar).toHaveBeenCalledWith('Anotar', { tipo: 'EXPENSE', categoriaId: 7 });
    fireEvent.press(getByLabelText('Usar plata de Ahorro para pagar un gasto'));
    expect(mockNavegar).toHaveBeenCalledWith('Anotar', { tipo: 'EXPENSE', pagadoCon: 'ahorro:7' });
  });

  it('al tocar un ahorro se ve en qué se usó', async () => {
    const { findByLabelText, getByText, queryByText } = render(<Envoltura><AhorrosScreen navigation={nav()} /></Envoltura>);
    expect(queryByText(/Arreglo carro/)).toBeNull();
    fireEvent.press(await findByLabelText(/Ahorro: hay/));
    expect(getByText('Arreglo carro · Para Carro · 6 nov')).toBeTruthy();
    expect(getByText('Ahorro quincena · Andy · 1 nov')).toBeTruthy();
  });

  it('sin ahorros explica cómo empezar', async () => {
    (estadoService.periodo as jest.Mock).mockResolvedValue(estado([]));
    const { findByText } = render(<Envoltura><AhorrosScreen navigation={nav()} /></Envoltura>);
    expect(await findByText('Aún no hay ahorros')).toBeTruthy();
  });
});

describe('Deudas', () => {
  it('muestra lo que se debe, las tarjetas y las cuotas', async () => {
    const { findByLabelText, getByText, getByLabelText, queryByLabelText } = render(<Envoltura><DeudasScreen navigation={nav()} /></Envoltura>);
    expect(await findByLabelText(/Deben \$4\.600\.000\. Este mes en cuotas: \$600\.000/)).toBeTruthy();
    expect(getByText(/Ya toca pagar: \$400\.000/)).toBeTruthy();
    expect(getByText(/Cuotas de los próximos meses: \$1\.800\.000/)).toBeTruthy();
    expect(getByText('Master de Samy')).toBeTruthy();
    expect(getByText(/Cuota \$600\.000 · faltan 4 \(\$2\.400\.000\) · termina mar 2027/)).toBeTruthy();
    /* La de Samy no se paga ni se borra desde aquí; la propia sin deuda sí se puede borrar. */
    expect(queryByLabelText('Borrar tarjeta Master')).toBeNull();
    expect(getByLabelText('Borrar tarjeta Vieja')).toBeTruthy();
    expect(queryByLabelText('Borrar tarjeta Visa')).toBeNull();
    fireEvent.press(getByLabelText(/Tarjeta Visa: se debe/));
    expect(getByText('Nevera · 3/12')).toBeTruthy();
    expect(getByText(/faltan \$1\.800\.000 · próxima 15 dic/)).toBeTruthy();
  });

  it('pagar la tarjeta pide confirmación, llama al servicio y recarga', async () => {
    (deudaService.pagarTarjeta as jest.Mock).mockResolvedValue({ correct: true, object: { paidItems: 1, paidTotal: 400000 } });
    const { findByLabelText, getByText, getByLabelText } = render(<Envoltura><DeudasScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Pagar tarjeta Visa'));
    expect(getByText(/Quedan pagadas las compras hasta hoy: \$400\.000\. No es un gasto nuevo/)).toBeTruthy();
    expect(deudaService.pagarTarjeta).not.toHaveBeenCalled();
    fireEvent.press(getByLabelText('Pagar'));
    await waitFor(() => expect(deudaService.pagarTarjeta).toHaveBeenCalledWith(1));
    await waitFor(() => expect(deudaService.todas).toHaveBeenCalledTimes(2));
    expect(toast).toHaveBeenCalledWith('success', 'Tarjeta pagada', expect.stringContaining('400.000'));
  });

  it('si el servidor rechaza el pago lo dice', async () => {
    (deudaService.pagarTarjeta as jest.Mock).mockResolvedValue({ correct: false, message: 'Tarjeta no encontrada' });
    const { findByLabelText, getByLabelText } = render(<Envoltura><DeudasScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Pagar tarjeta Visa'));
    fireEvent.press(getByLabelText('Pagar'));
    await waitFor(() => expect(toast).toHaveBeenCalledWith('error', 'No se pudo', 'Tarjeta no encontrada'));
    expect(deudaService.todas).toHaveBeenCalledTimes(1);
  });

  it('adelantar una cuota confirma, llama al servicio y avisa', async () => {
    (deudaService.adelantarCuota as jest.Mock).mockResolvedValue({ correct: true, object: { movementId: 1, amount: 600000, remainingInstallments: 3 } });
    const { findByLabelText, getByText, getByLabelText } = render(<Envoltura><DeudasScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Adelantar una cuota de Crédito carro'));
    expect(getByText('Pagas hoy $600.000 y la deuda termina un mes antes.')).toBeTruthy();
    fireEvent.press(getByLabelText('Adelantar'));
    await waitFor(() => expect(deudaService.adelantarCuota).toHaveBeenCalledWith(40));
    await waitFor(() => expect(toast).toHaveBeenCalledWith('success', 'Cuota adelantada', 'Faltan 3 cuotas'));
    await waitFor(() => expect(deudaService.todas).toHaveBeenCalledTimes(2));
  });

  it('agregar tarjeta valida el nombre y el día antes de guardar', async () => {
    (deudaService.guardarTarjeta as jest.Mock).mockResolvedValue({ correct: true, object: { id: 9 } });
    const { findByLabelText, getByLabelText, getByText } = render(<Envoltura><DeudasScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Agregar tarjeta'));
    fireEvent.press(getByLabelText('Guardar'));
    expect(getByText('Ponle un nombre a la tarjeta')).toBeTruthy();
    expect(deudaService.guardarTarjeta).not.toHaveBeenCalled();
    fireEvent.changeText(getByLabelText(/^Nombre/), 'Amex');
    fireEvent.changeText(getByLabelText(/^Día de pago/), '40');
    fireEvent.press(getByLabelText('Guardar'));
    expect(getByText('Un día entre 1 y 31')).toBeTruthy();
    fireEvent.changeText(getByLabelText(/^Día de pago/), '5');
    fireEvent.press(getByLabelText('Guardar'));
    await waitFor(() => expect(deudaService.guardarTarjeta).toHaveBeenCalledWith({ name: 'Amex', paymentDay: 5, cutoffDay: null, color: '#6C5CE7' }));
  });

  it('para cambiar una deuda lleva a Programados', async () => {
    const { findByLabelText } = render(<Envoltura><DeudasScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Ver o editar en Programados'));
    expect(mockNavegar).toHaveBeenCalledWith('ScheduledList');
  });

  it('una deuda en cuotas se crea desde aquí, con su cuota, cuántas son y el día', async () => {
    const { categoryService } = require('../../services/categoryService');
    const { scheduledService } = require('../../services/scheduledService');
    (categoryService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [
      { id: '27', name: 'Casa', type: 'EXPENSE', active: true }, { id: '28', name: 'Deuda', type: 'EXPENSE', active: true },
    ] } });
    (scheduledService.save as jest.Mock).mockResolvedValue({ correct: true, object: { id: 99 } });
    const { findByLabelText, getByLabelText, getByText, findByText } = render(<Envoltura><DeudasScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Nueva deuda en cuotas'));
    fireEvent.press(getByLabelText('Guardar'));
    expect(getByText(/Ponle un nombre/)).toBeTruthy();
    fireEvent.changeText(getByLabelText(/^Nombre/), 'Crédito libre inversión');
    fireEvent.changeText(getByLabelText(/^Valor de la cuota/), '750.000');
    fireEvent.changeText(getByLabelText(/^Cuotas/), '24');
    fireEvent.changeText(getByLabelText(/^Día de pago/), '10');
    expect(await findByText(/24 cuotas de .*750.000: en total .*18.000.000/)).toBeTruthy();
    expect((await findByLabelText('Categoría Deuda')).props.accessibilityState).toEqual({ selected: true });
    fireEvent.press(getByLabelText('Guardar'));
    await waitFor(() => expect(scheduledService.save).toHaveBeenCalledWith(expect.objectContaining({
      name: 'Crédito libre inversión', amount: 750000, durationMonths: 24, dayOfMonth: 10, categoryId: '28', frequency: 'MONTHLY', kind: 'PAYMENT',
    })));
    expect(toast).toHaveBeenCalledWith('success', 'Deuda creada', expect.stringContaining('24 cuotas'));
  });

  it('en la vista de una persona solo salen sus tarjetas y sus cuotas', async () => {
    const base = (AlcanceContext.useAlcance as jest.Mock)();
    (AlcanceContext.useAlcance as jest.Mock).mockReturnValue({ ...base, alcance: { tipo: 'PERSONA', userId: 4 } });
    try {
      const { findByText, queryByText } = render(<Envoltura><DeudasScreen navigation={nav()} /></Envoltura>);
      expect(await findByText('Master de Samy')).toBeTruthy();
      expect(queryByText('Visa')).toBeNull();
      expect(queryByText('Crédito carro')).toBeNull();
    } finally {
      (AlcanceContext.useAlcance as jest.Mock).mockReturnValue(base);
    }
  });

  it('con día de corte dice qué extracto toca pagar y qué va en el siguiente', async () => {
    (deudaService.todas as jest.Mock).mockResolvedValueOnce({ correct: true, object: { ...deudas, cards: [
      { ...deudas.cards[0], cutoffDay: 5, lastCutoff: '2026-11-05', nextPaymentDate: '2026-11-20', currentCycle: 90000, upcoming: 0 },
    ] } });
    const { findByText, getByText } = render(<Envoltura><DeudasScreen navigation={nav()} /></Envoltura>);
    expect(await findByText(/Extracto del 5 nov: \$400\.000 · pagar antes del 20 nov/)).toBeTruthy();
    expect(getByText(/Este ciclo \(siguiente extracto\): \$90\.000/)).toBeTruthy();
  });
});

describe('validarTarjeta', () => {
  it('nombre obligatorio y de hasta 60; día opcional entre 1 y 31', () => {
    expect(validarTarjeta('', '').nombre).toBeTruthy();
    expect(validarTarjeta('x'.repeat(61), '').nombre).toBe('Máximo 60 caracteres');
    expect(validarTarjeta('Visa', '')).toEqual({});
    expect(validarTarjeta('Visa', '0').dia).toBeTruthy();
    expect(validarTarjeta('Visa', '31')).toEqual({});
  });
});

describe('Menú', () => {
  it('tiene Ahorros y Deudas justo después de Inicio, con su pantalla registrada', () => {
    const fuente = fs.readFileSync(path.join(__dirname, '../../navigation/AppNavigator.tsx'), 'utf8');
    const inicio = fuente.indexOf("{ titulo: 'Inicio'");
    const ahorros = fuente.indexOf("{ titulo: 'Ahorros', icono: 'piggy-bank', destino: 'Ahorros', detalle: 'Cuánto hay y en qué se usó' }");
    const deudas = fuente.indexOf("{ titulo: 'Deudas', icono: 'credit-card', destino: 'Deudas', detalle: 'Tarjetas y cuotas' }");
    expect(inicio).toBeGreaterThan(-1);
    expect(ahorros).toBeGreaterThan(inicio);
    expect(deudas).toBeGreaterThan(ahorros);
    expect(fuente).toContain('<Finanzas.Screen name="Ahorros" component={pantalla(AhorrosScreen)} />');
    expect(fuente).toContain('<Finanzas.Screen name="Deudas" component={pantalla(DeudasScreen)} />');
  });
});
