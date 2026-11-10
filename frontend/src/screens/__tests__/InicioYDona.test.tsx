import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import * as AlcanceContext from '../../contexts/AlcanceContext';
import InicioScreen from '../inicio/InicioScreen';
import {
  agruparPorciones, repartirEtiquetas, geometria, CategoriaEnDona, ID_OTROS, MAX_PORCIONES, SEPARACION,
} from '../../components/molecules/DonaDeCategorias';
import { estadoService } from '../../services/estadoService';
import { dashboardService } from '../../services/dashboardService';
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
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) }));
jest.mock('../../services/estadoService', () => ({ estadoService: { periodo: jest.fn(), historial: jest.fn() } }));
jest.mock('../../services/dashboardService', () => ({ dashboardService: { getPending: jest.fn() } }));
jest.mock('../../services/deudaService', () => ({ deudaService: { todas: jest.fn() } }));

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

const cat = (id: number, valor: number): CategoriaEnDona => ({ id, nombre: `C${id}`, color: '#123456', valor });

describe('La dona: qué se dibuja', () => {
  it('junta en "Otros" lo que pesa menos del 3%', () => {
    const { porciones, total } = agruparPorciones([cat(1, 500), cat(2, 400), cat(3, 70), cat(4, 15), cat(5, 15)]);
    expect(total).toBe(1000);
    expect(porciones.map((p) => p.id)).toEqual([1, 2, 3, ID_OTROS]);
    const otros = porciones[3];
    expect(otros.valor).toBe(30);
    expect(otros.agrupadas!.map((c) => c.id).sort()).toEqual([4, 5]);
  });

  it('una sola categoría chica conserva su nombre', () => {
    const { porciones } = agruparPorciones([cat(1, 980), cat(2, 20)]);
    expect(porciones.map((p) => p.id)).toEqual([1, 2]);
  });

  it('no pasa de 8 porciones: las más chicas van a "Otros"', () => {
    const muchas = Array.from({ length: 12 }, (_, i) => cat(i + 1, 1000 - i * 10));
    const { porciones } = agruparPorciones(muchas);
    expect(porciones).toHaveLength(MAX_PORCIONES);
    expect(porciones[MAX_PORCIONES - 1].id).toBe(ID_OTROS);
    expect(porciones[MAX_PORCIONES - 1].agrupadas).toHaveLength(5);
    /* De la mas grande a la mas chica. */
    expect(porciones.slice(0, 7).map((p) => p.id)).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });

  it('lo que no tiene gasto no se dibuja', () => {
    expect(agruparPorciones([cat(1, 0), cat(2, 0)]).porciones).toEqual([]);
    expect(agruparPorciones([cat(1, 100), cat(2, 0)]).porciones.map((p) => p.id)).toEqual([1]);
  });
});

describe('La dona: las etiquetas no se montan', () => {
  const separadas = (ys: number[]) => ys.slice(1).every((y, i) => y - ys[i] >= SEPARACION - 1e-9);

  it('separa las que caen juntas al menos 30px y no se salen', () => {
    const ys = repartirEtiquetas([100, 101, 102, 103], 267);
    expect(separadas(ys)).toBe(true);
    ys.forEach((y) => { expect(y).toBeGreaterThanOrEqual(15); expect(y).toBeLessThanOrEqual(252); });
  });

  it('las amontonadas abajo suben en vez de salirse', () => {
    const ys = repartirEtiquetas([250, 251, 260], 267);
    expect(separadas(ys)).toBe(true);
    expect(ys[2]).toBeLessThanOrEqual(252);
  });

  it('con muchas en un lado la dona se hace más alta para que quepan', () => {
    const { porciones } = agruparPorciones(Array.from({ length: 8 }, (_, i) => cat(i + 1, 100)));
    const { alto, lados } = geometria(porciones, 100);
    const lado = Math.max(lados.derecha.length, lados.izquierda.length);
    expect(alto).toBeGreaterThanOrEqual(lado * SEPARACION);
    const ys = repartirEtiquetas(lados.derecha.map(() => alto / 2), alto);
    expect(separadas(ys)).toBe(true);
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
  (estadoService.periodo as jest.Mock).mockResolvedValue({ correct: true, object: normal });
  (dashboardService.getPending as jest.Mock).mockResolvedValue({ correct: true, object: [] });
  (deudaService.todas as jest.Mock).mockResolvedValue({ correct: true, object: {
    totalOwed: 1500000, owedOnCards: 900000, owedInInstallments: 600000, monthlyInstallments: 250000,
    cards: [{ id: 1, name: 'Nu', userId: 3, mine: true, owed: 900000, dueNow: 0, upcoming: 900000, purchases: [] }],
    loans: [{ scheduledMovementId: 7, name: 'Crédito', installmentAmount: 250000, totalInstallments: 4, paidInstallments: 2,
      remainingInstallments: 2, remainingTotal: 600000, endDate: '2027-01-10', monthlyFreed: 250000, kind: 'PAYMENT', payerUserId: 3, categoryId: 28 }] } });
});

const pintar = () => render(<Envoltura><InicioScreen navigation={nav()} /></Envoltura>);

describe('Inicio con la dona nueva', () => {
  it('dibuja lo gastado; no los ahorros ni lo que no tiene gasto, y junta lo chico en Otros', async () => {
    const { findByLabelText, getByLabelText, queryByLabelText, queryByText } = pintar();
    expect(await findByLabelText(/^Mercado: .*600.000, 60% del gasto/)).toBeTruthy();
    expect(getByLabelText(/^Transporte: .*380.000, 38% del gasto/)).toBeTruthy();
    expect(getByLabelText(/^Otros: .*20.000, 2% del gasto/)).toBeTruthy();
    expect(queryByLabelText(/^Regalo:/)).toBeNull();
    expect(queryByLabelText(/^Ahorro viaje:/)).toBeNull();
    expect(queryByLabelText(/^Café:/)).toBeNull();
    /* El "Libre" ya no va en una fila aparte: esta en el centro de la dona. */
    expect(getByLabelText(/^Libre para gastar .*2.000.000\. Gastado .*1.000.000 de .*3.000.000/)).toBeTruthy();
    expect(queryByText('Anota tu primer gasto con − Gasto')).toBeNull();
  });

  it('el centro abre el presupuesto', async () => {
    fireEvent.press(await pintar().findByLabelText(/^Libre para gastar/));
    expect(mockNavegar).toHaveBeenCalledWith('Presupuesto');
  });

  it('tocar una categoría la elige; "Anotar" lleva al teclado con ella; otro toque la suelta', async () => {
    const { findByLabelText, getByLabelText, queryByLabelText, getByText } = pintar();
    const mercado = await findByLabelText(/^Mercado:/);
    fireEvent.press(mercado);
    expect(queryByLabelText(/^Libre para gastar/)).toBeNull();
    expect(getByText(/de .*1 M presupuestado/)).toBeTruthy();
    fireEvent.press(getByLabelText('Anotar un gasto en Mercado'));
    expect(mockNavegar).toHaveBeenCalledWith('Anotar', { tipo: 'EXPENSE', categoriaId: 1 });
    fireEvent.press(getByLabelText(/^Mercado:/));
    expect(getByLabelText(/^Libre para gastar/)).toBeTruthy();
  });

  it('tocar la porción en el anillo también la elige', async () => {
    const { findByTestId, getByLabelText } = pintar();
    fireEvent.press(await findByTestId('porcion-2'));
    expect(getByLabelText('Anotar un gasto en Transporte')).toBeTruthy();
  });

  it('mantener presionado abre sus movimientos', async () => {
    fireEvent(await pintar().findByLabelText(/^Transporte:/), 'longPress');
    expect(mockNavegar).toHaveBeenCalledWith('Movimientos', { categoriaId: 2 });
  });

  it('"Otros" abre la lista de lo que se juntó, con monto y porcentaje', async () => {
    const { findByLabelText, getByLabelText } = pintar();
    fireEvent.press(await findByLabelText(/^Otros:/));
    expect(getByLabelText(/^Café: .*10.000, 1% del gasto\. Ver sus movimientos/)).toBeTruthy();
    fireEvent.press(getByLabelText(/^Dulces: .*10.000, 1% del gasto\. Ver sus movimientos/));
    expect(mockNavegar).toHaveBeenCalledWith('Movimientos', { categoriaId: 4 });
  });

  it('sin gastos: anillo gris, lo libre en el centro y la pista para empezar', async () => {
    (estadoService.periodo as jest.Mock).mockResolvedValue({ correct: true, object: estadoCon(
      [categoria({ actual: 0 })], { spent: 0, freeToSpend: -50000 }) });
    const { findByText, getByLabelText, queryByLabelText } = pintar();
    expect(await findByText('Anota tu primer gasto con − Gasto')).toBeTruthy();
    expect(getByLabelText(/^Libre para gastar -?.*50.000/)).toBeTruthy();
    expect(queryByLabelText(/^Mercado:/)).toBeNull();
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
