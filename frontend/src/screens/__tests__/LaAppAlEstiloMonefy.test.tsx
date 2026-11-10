import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import * as AlcanceContext from '../../contexts/AlcanceContext';
import * as ToastContext from '../../contexts/ToastContext';
import InicioScreen from '../inicio/InicioScreen';
import PorConfirmarScreen from '../pendientes/PorConfirmarScreen';
import AnotarScreen from '../anotar/AnotarScreen';
import PresupuestoScreen from '../presupuesto/PresupuestoScreen';
import CategoriaScreen from '../presupuesto/CategoriaScreen';
import MovimientosScreen from '../movimientos/MovimientosScreen';
import { estadoService } from '../../services/estadoService';
import { dashboardService } from '../../services/dashboardService';
import { movementService } from '../../services/movementService';
import { categoryService } from '../../services/categoryService';
import { budgetAllocationService } from '../../services/budgetAllocationService';
import { deudaService } from '../../services/deudaService';
import { fraseDelCierre } from '../../utils/estadoTextos';

const mockNavegar = jest.fn();
jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: () => (() => void) | void) => {
    const { useEffect } = require('react');
    useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, [cb]);
  },
  useNavigation: () => ({ navigate: mockNavegar, goBack: jest.fn(), dispatch: jest.fn() }),
  DrawerActions: { openDrawer: () => ({ type: 'OPEN_DRAWER' }) },
}));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) }));
jest.mock('../../services/estadoService', () => ({ estadoService: { periodo: jest.fn(), historial: jest.fn() } }));
jest.mock('../../services/dashboardService', () => ({ dashboardService: { getPending: jest.fn() } }));
jest.mock('../../services/movementService', () => ({ movementService: { confirmBatch: jest.fn(), reassign: jest.fn(), save: jest.fn(), getAll: jest.fn(), release: jest.fn() } }));
jest.mock('../../services/categoryService', () => ({ categoryService: { getAll: jest.fn() } }));
jest.mock('../../services/budgetAllocationService', () => ({
  budgetAllocationService: { save: jest.fn(), copyPrevious: jest.fn(), list: jest.fn(), delete: jest.fn() },
}));
jest.mock('../../services/deudaService', () => ({ deudaService: {
  todas: jest.fn().mockResolvedValue({ correct: true, object: { totalOwed: 0, owedOnCards: 0, owedInInstallments: 0, monthlyInstallments: 0, cards: [], loans: [] } }),
  misTarjetas: jest.fn().mockResolvedValue({ correct: true, object: [] }),
} }));

const toast = jest.fn();
jest.spyOn(ToastContext, 'useToast').mockReturnValue({ showToast: toast } as any);
jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '3', name: 'Andy', email: 'a@b.com', budgetStartDay: 28 },
  token: 't', loading: false, isAuthenticated: true, esAdmin: false,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
} as any);
jest.spyOn(AlcanceContext, 'useAlcance').mockReturnValue({
  alcance: { tipo: 'HOGAR' }, setAlcance: jest.fn(),
  miembros: [{ userId: 3, nombre: 'Andy' }, { userId: 4, nombre: 'Samy' }],
  nombreDe: (id: any) => (String(id) === '3' ? 'Andy' : String(id) === '4' ? 'Samy' : ''),
  etiquetaAlcance: 'Hogar', corte: 28, periodo: { inicio: '2026-10-28', fin: '2026-11-27' },
  anterior: jest.fn(), siguiente: jest.fn(), puedeSeguir: true, irAHoy: jest.fn(), recargarMiembros: jest.fn(),
} as any);

const Envoltura: React.FC<{ children: React.ReactNode }> = ({ children }) => <ThemeProvider>{children}</ThemeProvider>;
const nav = () => ({ navigate: mockNavegar, goBack: jest.fn(), replace: jest.fn(), canGoBack: () => false });

const categoria = (over: object) => ({
  categoryId: 1, categoryName: 'Mascotas', shared: true, kind: 'EXPENSE', allocated: 500000, actual: 700000,
  committed: 0, available: -200000, difference: -200000, executionPct: 140, status: 'EXCEDIDO', hasAllocation: true,
  plannedInItems: 500000, provision: 500000, provisionLeft: 0, otherSpending: 700000,
  byPerson: [{ userId: 4, name: 'Samy', actual: 450000, committed: 0 }, { userId: 3, name: 'Andy', actual: 250000, committed: 0 }],
  items: [{ scheduledMovementId: 9, name: 'Mascotas', date: '2026-11-01', amount: 500000, status: 'PROVISION', kind: 'PROVISION', payerUserId: 4, payerName: 'Samy' }],
  ...over,
});

const estado = {
  scope: 'HOGAR', periodStart: '2026-10-28', periodEnd: '2026-11-27', closed: false,
  totals: { incomeExpected: 14560000, incomeReceived: 7960000, incomePending: 6600000, payrollDeductions: 0,
    budgeted: 12445000, unassigned: 2115000, spent: 85000, committed: 10626000, savingsGoal: 4500000, saved: 0,
    savingsRatePct: 0, unbudgetedSpending: 75500, provisionsLeft: 0, toPay: 10626000, freeToSpend: 965000, freeNow: -4801000, executionPct: 1 },
  compliance: { billsTotal: 24, billsPaid: 0, billsOverdue: 0, categoriesWithin: 5, categoriesOver: 1, savingsGoalsMet: 0, savingsGoalsMissed: 0 },
  categories: [categoria({}), categoria({ categoryId: 2, categoryName: 'Ahorro', kind: 'SAVING', allocated: 3000000, actual: 0, status: 'EN_CURSO', provision: 0, byPerson: [], items: [] })],
  pendingCount: 25, incomeByPerson: [], incomeItems: [], responsibilities: [],
};

const pendiente = (id: string, userId: string, desc: string, amount: number) => ({
  id, userId, categoryId: '27', categoryName: 'Casa', categoryType: 'EXPENSE', amount, description: desc,
  date: '2026-11-01', confirmed: false, shared: true,
});

beforeEach(() => {
  jest.clearAllMocks();
  (estadoService.periodo as jest.Mock).mockResolvedValue({ correct: true, object: estado });
  (dashboardService.getPending as jest.Mock).mockResolvedValue({ correct: true, object: [
    pendiente('1', '3', 'Arriendo', 2300000), pendiente('2', '3', 'Internet', 68000), pendiente('3', '4', 'Natillera', 315000),
  ] });
  (categoryService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [
    { id: '42', name: 'Delivery', type: 'EXPENSE', householdId: 1, active: true },
    { id: '27', name: 'Casa', type: 'EXPENSE', householdId: 1, active: true },
  ] } });
});

describe('Inicio', () => {
  it('muestra lo gastado contra lo presupuestado de gasto, los recuadros y solo MIS pendientes', async () => {
    const { findByText, getByLabelText, queryByLabelText } = render(<Envoltura><InicioScreen navigation={nav()} /></Envoltura>);
    expect(await findByText('Tienes 2 pagos por confirmar')).toBeTruthy();
    expect(getByLabelText(/Gastado .*85.000 de .*7.945.000/)).toBeTruthy();
    expect(getByLabelText(/Ingresos recibidos .*7.960.000 de .*14.560.000/)).toBeTruthy();
    /* Las cuentas se retiraron: ya no hay recuadro de fondos. */
    expect(queryByLabelText(/Fondos/)).toBeNull();
    expect(getByLabelText(/Libre para gastar .*965.000/)).toBeTruthy();
  });

  it('el botón Gasto abre el teclado de gasto y un ícono abre esa categoría', async () => {
    const { findByLabelText, getByLabelText } = render(<Envoltura><InicioScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Anotar un gasto'));
    expect(mockNavegar).toHaveBeenCalledWith('Anotar', { tipo: 'EXPENSE' });
    /* La etiqueta de la porcion: mantener abre sus movimientos; tocarla la
       elige, y el centro ofrece anotar en ella o ver su presupuesto. */
    fireEvent(getByLabelText(/^Mascotas: .*700.000, 100% del gasto/), 'longPress');
    expect(mockNavegar).toHaveBeenCalledWith('Movimientos', { categoriaId: 1 });
    fireEvent.press(getByLabelText(/^Mascotas: .*700.000, 100% del gasto/));
    fireEvent.press(getByLabelText('Ver el presupuesto de Mascotas'));
    expect(mockNavegar).toHaveBeenCalledWith('Categoria', { categoryId: 1 });
    fireEvent.press(getByLabelText('Anotar un gasto en Mascotas'));
    expect(mockNavegar).toHaveBeenCalledWith('Anotar', { tipo: 'EXPENSE', categoriaId: 1 });
  });
});

describe('Por confirmar', () => {
  it('por defecto solo los míos; confirma los marcados de una vez', async () => {
    (movementService.confirmBatch as jest.Mock).mockResolvedValue({ correct: true, object: { confirmed: 2, total: 2368000 } });
    const { findByLabelText, queryByText, getByLabelText } = render(<Envoltura><PorConfirmarScreen navigation={nav()} /></Envoltura>);
    await findByLabelText('Marcar Arriendo');
    expect(queryByText('Natillera')).toBeNull();
    fireEvent.press(getByLabelText('Marcar Arriendo'));
    fireEvent.press(getByLabelText('Marcar Internet'));
    fireEvent.press(getByLabelText(/Confirmar 2 pagos/));
    await waitFor(() => expect(movementService.confirmBatch).toHaveBeenCalledWith([
      { id: '1', amount: undefined }, { id: '2', amount: undefined },
    ]));
  });

  it('si el servidor rechaza el lote lo dice y no da nada por confirmado', async () => {
    (movementService.confirmBatch as jest.Mock).mockResolvedValue({ correct: false, message: 'Pago no encontrado: 1' });
    const { findByLabelText, getByLabelText } = render(<Envoltura><PorConfirmarScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Marcar Arriendo'));
    fireEvent.press(getByLabelText(/Confirmar 1 pago por/));
    await waitFor(() => expect(toast).toHaveBeenCalledWith('error', 'No se confirmó ninguno', 'Pago no encontrado: 1'));
  });

  it('"De los dos" muestra también los de Samy', async () => {
    const { findByLabelText, getByLabelText, findByText } = render(<Envoltura><PorConfirmarScreen navigation={nav()} /></Envoltura>);
    await findByLabelText('Marcar Arriendo');
    fireEvent.press(getByLabelText('Los de los dos'));
    expect(await findByText('Natillera')).toBeTruthy();
  });
});

describe('Por pagar: el recuadro y la lista dicen lo mismo', () => {
  it('en Hogar dice lo mismo que descuenta lo libre (toPay), cuenta los pagos comunes de la lista y abre esa lista', async () => {
    const { findByLabelText } = render(<Envoltura><InicioScreen navigation={nav()} /></Envoltura>);
    /* toPay (10.626.000) = 3 pagos de la lista (2.683.000) + lo programado que aun no se genera. */
    const recuadro = await findByLabelText(/^Por pagar .*10.626.000: 3 pagos por .*2.683.000 y .*7.943.000 programados/);
    fireEvent.press(recuadro);
    expect(mockNavegar).toHaveBeenCalledWith('PorConfirmar', { quien: 'DOS' });
  });

  it('la lista de los dos dice los mismos 3 pagos y el mismo total', async () => {
    const { findByText } = render(<Envoltura><PorConfirmarScreen navigation={nav()} route={{ params: { quien: 'DOS' } }} /></Envoltura>);
    expect(await findByText(/3 pagos · .*2.683.000/)).toBeTruthy();
    expect(await findByText('Natillera')).toBeTruthy();
  });

  it('"Marcar todos" marca, no filtra', async () => {
    const { findByLabelText, getByLabelText } = render(<Envoltura><PorConfirmarScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Marcar todos'));
    expect(getByLabelText(/Confirmar 2 pagos/)).toBeTruthy();
    expect(getByLabelText('Desmarcar todos')).toBeTruthy();
  });
});

describe('Liberar plata de un pago del plan', () => {
  it('libera lo que se escribe con el teclado y lo dice', async () => {
    (movementService as any).release = jest.fn().mockResolvedValue({ correct: true, object: { released: 2300000, totalReleased: 2300000, stillPending: 0 } });
    const { findByLabelText, getAllByLabelText, getByLabelText } = render(<Envoltura><PorConfirmarScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText(/Arriendo, .*Ver opciones/));
    fireEvent.press(getByLabelText(/Liberar todo o parte de este pago/));
    fireEvent.press(getAllByLabelText('Liberar')[0]);
    await waitFor(() => expect((movementService as any).release).toHaveBeenCalledWith('1', 2300000));
    expect(toast).toHaveBeenCalledWith('success', expect.stringMatching(/Liberaste .*2.300.000/), 'Ya está disponible para gastar');
  });
});

describe('Anotar', () => {
  it('el teclado suma y se guarda el resultado en la categoría elegida', async () => {
    (movementService.save as jest.Mock).mockResolvedValue({ correct: true, object: {} });
    const navegacion = nav();
    const { findByLabelText, getByLabelText } = render(
      <Envoltura><AnotarScreen navigation={navegacion} route={{ params: { tipo: 'EXPENSE', categoriaId: '42' } }} /></Envoltura>);
    await findByLabelText(/Categoría Delivery/);
    ['5', '0', 'tres ceros', 'más', '1', '5', '0', '0'].forEach((t) => fireEvent.press(getByLabelText(t)));
    fireEvent.press(getByLabelText(/^Anotar/));
    await waitFor(() => expect(movementService.save).toHaveBeenCalledWith(expect.objectContaining({
      categoryId: '42', amount: 51500, confirmed: true, userId: '3',
    })));
    /* Cuentas y repartos se retiraron: el movimiento ya no los lleva. */
    const enviado = (movementService.save as jest.Mock).mock.calls[0][0];
    expect(enviado).not.toHaveProperty('accountId');
    expect(enviado).not.toHaveProperty('splitMode');
    expect(enviado).not.toHaveProperty('splits');
  });

  it('un gasto pagado con el ahorro dice de qué ahorro salió y no deja de ser del mes por error', async () => {
    (movementService.save as jest.Mock).mockResolvedValue({ correct: true, object: {} });
    const { findByLabelText, getByLabelText, findByText } = render(
      <Envoltura><AnotarScreen navigation={nav()} route={{ params: { tipo: 'EXPENSE', categoriaId: '42' } }} /></Envoltura>);
    fireEvent.press(await findByLabelText(/Pagado con Fondos del mes/));
    fireEvent.press(getByLabelText(/^Ahorro\. Hay/));
    expect(getByLabelText(/Pagado con Del Ahorro/)).toBeTruthy();
    ['8', '0', '0', 'tres ceros'].forEach((t) => fireEvent.press(getByLabelText(t)));
    expect(await findByText(/Ojo: en Ahorro hay .*0; quedaría en/)).toBeTruthy();
    fireEvent.press(getByLabelText(/^Anotar/));
    await waitFor(() => expect(movementService.save).toHaveBeenCalledWith(expect.objectContaining({
      categoryId: '42', amount: 800000, fundedFromCategoryId: 2,
    })));
    expect((movementService.save as jest.Mock).mock.calls[0][0]).not.toHaveProperty('cardId');
  });

  it('con tarjeta a 12 cuotas manda la tarjeta y las cuotas, y ya no pregunta si está pagado', async () => {
    (movementService.save as jest.Mock).mockResolvedValue({ correct: true, object: {} });
    (deudaService.misTarjetas as jest.Mock).mockResolvedValueOnce({ correct: true, object: [
      { id: 7, name: 'NU', mine: true, owed: 0, dueNow: 0, upcoming: 0, purchases: [], userId: 3 },
    ] });
    const { findByLabelText, getByLabelText, queryByLabelText, findByText } = render(
      <Envoltura><AnotarScreen navigation={nav()} route={{ params: { tipo: 'EXPENSE', categoriaId: '27' } }} /></Envoltura>);
    fireEvent.press(await findByLabelText(/Pagado con Fondos del mes/));
    fireEvent.press(await findByLabelText(/^NU\./));
    fireEvent.press(getByLabelText('12 cuotas'));
    expect(queryByLabelText('Por pagar')).toBeNull();
    ['1', '2', '0', '0', 'tres ceros'].forEach((t) => fireEvent.press(getByLabelText(t)));
    expect(await findByText(/12 cuotas de .*100.000 al mes/)).toBeTruthy();
    fireEvent.press(getByLabelText(/^Anotar/));
    await waitFor(() => expect(movementService.save).toHaveBeenCalledWith(expect.objectContaining({
      categoryId: '27', amount: 1200000, cardId: 7, installments: 12, confirmed: true,
    })));
    expect((movementService.save as jest.Mock).mock.calls[0][0]).not.toHaveProperty('fundedFromCategoryId');
  });

  it('la categoría se elige de una lista con buscador', async () => {
    (movementService.save as jest.Mock).mockResolvedValue({ correct: true, object: {} });
    const { findByLabelText, getByLabelText, queryByLabelText } = render(
      <Envoltura><AnotarScreen navigation={nav()} route={{ params: { tipo: 'EXPENSE' } }} /></Envoltura>);
    fireEvent.press(await findByLabelText('Elegir la categoría'));
    fireEvent.changeText(getByLabelText('Buscar categoría'), 'cAsá');
    expect(queryByLabelText(/^Categoría Delivery$/)).toBeNull();
    fireEvent.press(getByLabelText(/^Categoría Casa$/));
    expect(getByLabelText(/Categoría Casa\. Cambiar/)).toBeTruthy();
    ['5', '0', 'tres ceros'].forEach((t) => fireEvent.press(getByLabelText(t)));
    fireEvent.press(getByLabelText(/^Anotar/));
    await waitFor(() => expect(movementService.save).toHaveBeenCalledWith(expect.objectContaining({ categoryId: '27', amount: 50000 })));
  });

  it('sin monto no deja anotar', async () => {
    const { findByLabelText, getByLabelText } = render(
      <Envoltura><AnotarScreen navigation={nav()} route={{ params: { tipo: 'EXPENSE' } }} /></Envoltura>);
    await findByLabelText('Elegir la categoría');
    fireEvent.press(getByLabelText(/^Anotar/));
    expect(movementService.save).not.toHaveBeenCalled();
  });
});

describe('Presupuesto', () => {
  it('cada categoría dice su estado y quién gastó', async () => {
    const { findByText, getByLabelText, getByText } = render(<Envoltura><PresupuestoScreen navigation={nav()} /></Envoltura>);
    expect(await findByText('Se pasó')).toBeTruthy();
    fireEvent.press(getByLabelText('Quién gastó'));
    expect(getByText(/Samy .*450.000 · Andy .*250.000/)).toBeTruthy();
  });
});

describe('Categoría', () => {
  it('fija lo presupuestado con el teclado de la app', async () => {
    (budgetAllocationService.save as jest.Mock).mockResolvedValue({ correct: true, object: {} });
    const { findByLabelText, getAllByLabelText } = render(
      <Envoltura><CategoriaScreen navigation={nav()} route={{ params: { categoryId: 1 } }} /></Envoltura>);
    fireEvent.press(await findByLabelText(/Fijar lo presupuestado de Mascotas/));
    fireEvent(getAllByLabelText('borrar el último dígito')[0], 'longPress');
    ['6', '0', '0', 'tres ceros'].forEach((t) => fireEvent.press(getAllByLabelText(t)[0]));
    fireEvent.press(getAllByLabelText('Guardar')[0]);
    await waitFor(() => expect(budgetAllocationService.save).toHaveBeenCalledWith(expect.objectContaining({
      categoryId: 1, amount: 600000, budgetStartDay: 28, referenceDate: '2026-10-28',
    })));
  });
});

describe('La frase del cierre', () => {
  it('dice lo esperado contra lo real como lo diría una persona', () => {
    expect(fraseDelCierre({ kind: 'EXPENSE', allocated: 500000, actual: 700000, status: 'EXCEDIDO' }, true))
      .toMatch(/Se esperaban .*500.000 y se gastaron .*700.000: .*200.000 de más/);
    expect(fraseDelCierre({ kind: 'SAVING', allocated: 3000000, actual: 0, status: 'META_INCUMPLIDA' }, true))
      .toMatch(/Se quería ahorrar .*3.000.000 y se ahorró .*0: faltaron .*3.000.000/);
    expect(fraseDelCierre({ kind: 'EXPENSE', allocated: 0, actual: 75500, status: 'SIN_PRESUPUESTO' }, false))
      .toMatch(/No tenía presupuesto y se llevan .*75.500/);
    expect(fraseDelCierre({ kind: 'EXPENSE', allocated: 510000, actual: 813100, fromSavings: 800000, status: 'DENTRO' }, true))
      .toMatch(/Se esperaban .*510.000 y se gastaron .*13.100: sobraron .*496.900. Además, .*800.000 se pagaron con el ahorro/);
    expect(fraseDelCierre({ kind: 'SAVING', allocated: 3000000, actual: 3000000, withdrawn: 800000, status: 'META_CUMPLIDA' }, true))
      .toMatch(/se ahorró .*3.000.000. Se sacaron .*800.000 del ahorro/);
  });
});

describe('Movimientos del periodo, como en Monefy', () => {
  const mov = (id: string, cat: string, nombre: string, amount: number, desc: string) => ({
    id, userId: '3', categoryId: cat, categoryName: nombre, categoryType: 'EXPENSE', amount, description: desc,
    date: '2026-10-31', confirmed: true, shared: true,
  });
  it('ordena las categorías de la que más movió a la que menos, deja las vacías al final y expande todo', async () => {
    (movementService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [
      mov('58', '42', 'Delivery', 51500, 'Pago Almuerzo'), mov('60', '42', 'Delivery', 24000, 'Compra de helado'),
      mov('59', '27', 'Casa', 9500, 'Crema'),
    ] } });
    (categoryService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [
      { id: '27', name: 'Casa', type: 'EXPENSE', householdId: 1, active: true },
      { id: '42', name: 'Delivery', type: 'EXPENSE', householdId: 1, active: true },
      { id: '34', name: 'Regalo', type: 'EXPENSE', householdId: 1, active: true },
    ] } });
    const { findAllByLabelText, getByLabelText, queryByText, getByText } = render(
      <Envoltura><MovimientosScreen navigation={nav()} /></Envoltura>);
    const filas = await findAllByLabelText(/\. (Expandir|Contraer)$/);
    expect(filas.map((f) => f.props.accessibilityLabel.split(':')[0])).toEqual(['Delivery', 'Casa', 'Regalo']);
    expect(queryByText('Compra de helado')).toBeNull();
    fireEvent.press(getByLabelText('Expandir todo'));
    expect(getByText('Compra de helado')).toBeTruthy();
    expect(getByText('Pago Almuerzo')).toBeTruthy();
    expect(getByText('Sin movimientos')).toBeTruthy();
  });

  /* La busqueda reemplaza a la antigua pantalla "Buscar movimientos". */
  it('busca por nota o categoría sin que importen mayúsculas ni tildes', async () => {
    (movementService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [
      mov('58', '42', 'Delivery', 51500, 'Pago Almuerzo'), mov('60', '42', 'Delivery', 24000, 'Compra de helado'),
      mov('59', '27', 'Casa', 9500, 'Crema de café'),
    ] } });
    (categoryService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [
      { id: '27', name: 'Casa', type: 'EXPENSE', householdId: 1, active: true },
      { id: '42', name: 'Delivery', type: 'EXPENSE', householdId: 1, active: true },
      { id: '34', name: 'Regalo', type: 'EXPENSE', householdId: 1, active: true },
    ] } });
    const { findByLabelText, getByLabelText, queryByText, getByText, queryByLabelText } = render(
      <Envoltura><MovimientosScreen navigation={nav()} /></Envoltura>);
    const caja = await findByLabelText('Buscar movimientos por nota o categoría');

    /* Por la nota, sin tilde: "cafe" encuentra "café", y se ve sin desplegar. */
    fireEvent.changeText(caja, 'CAFE');
    expect(getByText('Crema de café')).toBeTruthy();
    expect(queryByText('Pago Almuerzo')).toBeNull();
    expect(queryByLabelText(/^Regalo:/)).toBeNull();

    /* Por el nombre de la categoria: salen todos los suyos. */
    fireEvent.changeText(caja, 'deliv');
    expect(getByText('Pago Almuerzo')).toBeTruthy();
    expect(getByText('Compra de helado')).toBeTruthy();
    expect(queryByText('Crema de café')).toBeNull();

    fireEvent.changeText(caja, 'nada parecido');
    expect(getByText(/Nada en este periodo coincide/)).toBeTruthy();

    fireEvent.press(getByLabelText('Borrar la búsqueda'));
    expect(getByLabelText(/^Regalo:/)).toBeTruthy();
  });
});
