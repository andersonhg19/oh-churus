import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import * as AlcanceContext from '../../contexts/AlcanceContext';
import InicioScreen from '../inicio/InicioScreen';
import {
  ubicarCategorias, ordenarPorGasto, porcentaje, CategoriaEnDona, MAXIMO_ICONOS,
} from '../../components/molecules/DonaDeCategorias';
import { estadoService } from '../../services/estadoService';
import { dashboardService } from '../../services/dashboardService';
import { deudaService } from '../../services/deudaService';
import { categoryService } from '../../services/categoryService';

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
jest.mock('../../services/deudaService', () => ({ deudaService: { todas: jest.fn() } }));
jest.mock('../../services/categoryService', () => ({ categoryService: { getAll: jest.fn() } }));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '3', name: 'Andy', email: 'a@b.com', budgetStartDay: 28 },
  token: 't', loading: false, isAuthenticated: true, esAdmin: false,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
} as any);
jest.spyOn(AlcanceContext, 'useAlcance').mockReturnValue({
  alcance: { tipo: 'HOGAR' }, setAlcance: jest.fn(), miembros: [], nombreDe: () => '',
  etiquetaAlcance: 'Hogar', corte: 28, periodo: { inicio: '2026-10-28', fin: '2026-11-27' },
  anterior: jest.fn(), siguiente: jest.fn(), puedeSeguir: true, irAHoy: jest.fn(), recargarMiembros: jest.fn(),
} as any);

const Envoltura: React.FC<{ children: React.ReactNode }> = ({ children }) => <ThemeProvider>{children}</ThemeProvider>;
const nav = () => ({ navigate: mockNavegar, goBack: jest.fn() });

const cat = (id: number, valor: number, nombre = `C${id}`): CategoriaEnDona => ({ id, nombre, color: '#123456', valor });
const dist = (a: number, b: number) => { const d = Math.abs(a - b) % 360; return Math.min(d, 360 - d); };

describe('La dona: dónde va cada categoría', () => {
  it('ordena por lo gastado y, sin gasto o empatadas, por nombre', () => {
    const o = ordenarPorGasto([cat(1, 0, 'Zapatos'), cat(2, 50, 'Mercado'), cat(3, 0, 'Aseo'), cat(4, 50, 'Casa'), cat(5, 90, 'Luz')]);
    expect(o.map((c) => c.nombre)).toEqual(['Luz', 'Casa', 'Mercado', 'Aseo', 'Zapatos']);
  });

  it('todas las categorías van alrededor, las de gasto con su %, cerca de su porción', () => {
    const cs = [cat(1, 600), cat(2, 300), cat(3, 100), cat(4, 0), cat(5, 0)];
    const { ubicadas, porciones } = ubicarCategorias(cs);
    expect(ubicadas).toHaveLength(5);
    porciones.forEach((p) => {
      const u = ubicadas.find((x) => x.categoria.id === p.c.id)!;
      expect(dist(u.angulo, p.medio)).toBeLessThanOrEqual(45);
    });
    expect(ubicadas.find((u) => u.categoria.id === 1)!.fraccion).toBeCloseTo(0.6);
    expect(new Set(ubicadas.map((u) => u.angulo)).size).toBe(5);
  });

  it('con más de 16, las de gasto siempre se ven y el último lugar es "Más"', () => {
    const cs = Array.from({ length: 20 }, (_, i) => cat(i + 1, i >= 17 ? 100 : 0));
    const r = ubicarCategorias(cs);
    expect(r.ubicadas).toHaveLength(MAXIMO_ICONOS - 1);
    [18, 19, 20].forEach((id) => expect(r.ubicadas.some((u) => u.categoria.id === id)).toBe(true));
    expect(r.mas).toEqual({ angulo: expect.any(Number), cuantas: 5 });
  });

  it('el porcentaje no dice 0% de algo que sí tiene gasto', () => {
    expect(porcentaje(0.004)).toBe('<1%');
    expect(porcentaje(0.345)).toBe('35%');
  });
});

const categoria = (over: object) => ({
  categoryId: 1, categoryName: 'Mercado', shared: true, kind: 'EXPENSE', allocated: 1000000, actual: 600000,
  committed: 0, available: 0, difference: 0, executionPct: 60, status: 'DENTRO', hasAllocation: true,
  plannedInItems: 0, provision: 0, provisionLeft: 0, otherSpending: 0, byPerson: [], items: [], ...over,
});

const estadoCon = (categories: object[], totals: object = {}) => ({
  scope: 'HOGAR', periodStart: '2026-10-28', periodEnd: '2026-11-27', closed: false,
  totals: { incomeExpected: 5000000, incomeReceived: 2000000, incomePending: 0, payrollDeductions: 0, budgeted: 3000000,
    unassigned: 0, spent: 1000000, committed: 0, savingsGoal: 0, saved: 0, savingsRatePct: 0, unbudgetedSpending: 0,
    provisionsLeft: 0, toPay: 0, freeToSpend: 2000000, freeNow: 0, executionPct: 0, savingsBalance: 3200000, ...totals },
  compliance: {}, categories, pendingCount: 0, incomeByPerson: [], incomeItems: [], responsibilities: [],
});

const normal = estadoCon([
  categoria({}),
  categoria({ categoryId: 2, categoryName: 'Transporte', actual: 380000, allocated: 0 }),
  categoria({ categoryId: 3, categoryName: 'Café', actual: 10000 }),
  categoria({ categoryId: 4, categoryName: 'Dulces', actual: 10000 }),
  categoria({ categoryId: 5, categoryName: 'Regalo', actual: 0 }),
  categoria({ categoryId: 6, categoryName: 'Ahorro viaje', kind: 'SAVING', actual: 900000 }),
]);

beforeEach(() => {
  jest.clearAllMocks();
  (categoryService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [
    { id: 1, name: 'Mercado', type: 'EXPENSE', active: true, householdId: 1 },
    { id: 9, name: 'Mascotas', type: 'EXPENSE', active: true, householdId: 1 },
    { id: 10, name: 'Salario', type: 'INCOME', active: true, householdId: 1 },
  ] } });
  (estadoService.periodo as jest.Mock).mockResolvedValue({ correct: true, object: normal });
  (dashboardService.getPending as jest.Mock).mockResolvedValue({ correct: true, object: [] });
  (deudaService.todas as jest.Mock).mockResolvedValue({ correct: true, object: {
    totalOwed: 1500000, owedOnCards: 900000, owedInInstallments: 600000, monthlyInstallments: 250000,
    cards: [{ id: 1, name: 'Nu', userId: 3, mine: true, owed: 900000, dueNow: 0, upcoming: 900000, purchases: [] }],
    loans: [{ scheduledMovementId: 7, name: 'Crédito', installmentAmount: 250000, totalInstallments: 4, paidInstallments: 2,
      remainingInstallments: 2, remainingTotal: 600000, endDate: '2027-01-10', monthlyFreed: 250000, kind: 'PAYMENT', payerUserId: 3, categoryId: 28 }] } });
});

const pintar = () => render(<Envoltura><InicioScreen navigation={nav()} /></Envoltura>);

describe('Inicio al estilo Monefy', () => {
  it('todas las categorías alrededor con su %, y adentro lo que entró y lo que salió', async () => {
    const { findByLabelText, getByLabelText } = pintar();
    expect(await findByLabelText(/^Mercado: .*600.000, 60% de lo gastado/)).toBeTruthy();
    expect(getByLabelText(/^Transporte: .*380.000, 38% de lo gastado/)).toBeTruthy();
    expect(getByLabelText(/^Café: .*10.000, 1% de lo gastado/)).toBeTruthy();
    expect(getByLabelText('Regalo, sin gasto')).toBeTruthy();
    expect(getByLabelText('Ahorro viaje, sin gasto')).toBeTruthy();
    /* La que no tiene fila en el estado igual sale, para anotar en ella. */
    expect(getByLabelText('Mascotas, sin gasto')).toBeTruthy();
    expect(getByLabelText(/^Ingresos .*2.000.000, gastos .*1.000.000 de .*3.000.000 presupuestado/)).toBeTruthy();
  });

  it('lo pagado con el ahorro sale en su porción pero no en lo gastado del sueldo', async () => {
    (estadoService.periodo as jest.Mock).mockResolvedValue({ correct: true, object: estadoCon(
      [categoria({ actual: 1000000 })], { spent: 1000000, fromSavings: 300000 }) });
    const { findByLabelText, getByText } = pintar();
    expect(await findByLabelText(/gastos .*700.000 de .*3.000.000 presupuestado y .*300.000 pagados con ahorros/)).toBeTruthy();
    expect(getByText(/con ahorros$/)).toBeTruthy();
  });

  it('el centro abre el presupuesto', async () => {
    fireEvent.press(await pintar().findByLabelText(/^Ingresos .*presupuestado/));
    expect(mockNavegar).toHaveBeenCalledWith('Presupuesto');
  });

  it('tocar una categoría ofrece anotar, ver sus movimientos o su presupuesto', async () => {
    const { findByLabelText, getByLabelText } = pintar();
    fireEvent.press(await findByLabelText(/^Mercado:/));
    fireEvent.press(getByLabelText('Anotar en Mercado'));
    expect(mockNavegar).toHaveBeenCalledWith('Anotar', { tipo: 'EXPENSE', categoriaId: 1 });
    fireEvent.press(getByLabelText(/^Transporte:/));
    fireEvent.press(getByLabelText('Ver los movimientos de Transporte'));
    expect(mockNavegar).toHaveBeenCalledWith('Movimientos', { categoriaId: 2 });
  });

  it('lo libre para gastar abre la lista de todos los movimientos', async () => {
    fireEvent.press(await pintar().findByLabelText(/^Libre para gastar .*2.000.000\. Ver todos los movimientos/));
    expect(mockNavegar).toHaveBeenCalledWith('Movimientos');
  });
});

describe('Los recuadros de Inicio', () => {
  it('Ahorros dice lo que hay en todos los ahorros y abre Ahorros', async () => {
    const { findByLabelText } = pintar();
    const ahorros = await findByLabelText(/^Ahorros: hay .*3.200.000/);
    fireEvent.press(ahorros);
    expect(mockNavegar).toHaveBeenCalledWith('Ahorros');
  });

  it('Deudas dice lo que se debe y la cuota del mes, y abre Deudas', async () => {
    const { findByLabelText } = pintar();
    const deudas = await findByLabelText(/^Deudas: se deben .*1.500.000, este mes .*250.000/);
    fireEvent.press(deudas);
    expect(mockNavegar).toHaveBeenCalledWith('Deudas');
  });

  it('si las deudas fallan, el Inicio igual carga', async () => {
    (deudaService.todas as jest.Mock).mockRejectedValue(new Error('caido'));
    const aviso = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    const { findByLabelText, getByLabelText } = pintar();
    expect(await findByLabelText('Deudas: no se pudieron cargar. Ver las deudas')).toBeTruthy();
    expect(getByLabelText(/^Mercado:/)).toBeTruthy();
    await waitFor(() => expect(aviso).toHaveBeenCalled());
    aviso.mockRestore();
  });

  it('sin pagos míos por confirmar no sale el aviso', async () => {
    const { findByLabelText, queryByText } = pintar();
    await findByLabelText(/^Ahorros:/);
    expect(queryByText(/por confirmar/)).toBeNull();
  });
});
