import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react-native';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import * as AlcanceContext from '../../contexts/AlcanceContext';
import * as ToastContext from '../../contexts/ToastContext';
import PresupuestoScreen from '../presupuesto/PresupuestoScreen';
import CategoriaScreen from '../presupuesto/CategoriaScreen';
import IngresosScreen, { agruparIngresos } from '../ingresos/IngresosScreen';
import PlanScreen from '../plan/PlanScreen';
import LoginScreen from '../auth/LoginScreen';
import HouseholdScreen from '../settings/HouseholdScreen';
import CategoryFormScreen, { buscarNombreRepetido, normalizarNombre } from '../categories/CategoryFormScreen';
import { textoConfirmados } from '../pendientes/PorConfirmarScreen';
import { estadoService } from '../../services/estadoService';
import { categoryService } from '../../services/categoryService';
import { budgetAllocationService } from '../../services/budgetAllocationService';
import { planService } from '../../services/planService';
import { householdService } from '../../services/householdService';

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (cb: () => (() => void) | void) => {
    const { useEffect } = require('react');
    useEffect(() => { const c = cb(); return typeof c === 'function' ? c : undefined; }, [cb]);
  },
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn(), dispatch: jest.fn(), canGoBack: () => true }),
  DrawerActions: { openDrawer: () => ({ type: 'OPEN_DRAWER' }) },
}));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) }));
jest.mock('../../services/estadoService', () => ({ estadoService: { periodo: jest.fn(), historial: jest.fn() } }));
jest.mock('../../services/categoryService', () => ({ categoryService: { getAll: jest.fn(), save: jest.fn(), getTree: jest.fn(), delete: jest.fn() } }));
jest.mock('../../services/budgetAllocationService', () => ({
  budgetAllocationService: { save: jest.fn(), copyPrevious: jest.fn(), list: jest.fn(), delete: jest.fn() },
}));
jest.mock('../../services/movementService', () => ({ movementService: { unrelease: jest.fn() } }));
jest.mock('../../services/planService', () => ({
  planService: { quincena: jest.fn(), calendar: jest.fn(), responsibilities: jest.fn(), projection: jest.fn() },
}));
jest.mock('../../services/householdService', () => ({
  householdService: { getByUser: jest.fn(), create: jest.fn(), inviteByEmail: jest.fn(), removeMember: jest.fn() },
}));
jest.mock('../../services/authService', () => ({
  authService: { getUser: jest.fn().mockResolvedValue({ correct: true, object: { name: 'Andy', email: 'a@b.com' } }) },
}));

const toast = jest.fn();
jest.spyOn(ToastContext, 'useToast').mockReturnValue({ showToast: toast } as any);
const auth = {
  user: { userId: '4', name: 'Samy', email: 's@b.com', budgetStartDay: 28 },
  token: 't', loading: false, isAuthenticated: true, esAdmin: false,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
};
jest.spyOn(AuthContext, 'useAuth').mockReturnValue(auth as any);
const setAlcance = jest.fn();
const recargarMiembros = jest.fn();
const alcance = (a: any) => ({
  alcance: a, setAlcance,
  miembros: [{ userId: 3, nombre: 'Andy' }, { userId: 4, nombre: 'Samy' }],
  nombreDe: () => '', etiquetaAlcance: 'Samy', corte: 28, periodo: { inicio: '2026-10-28', fin: '2026-11-27' },
  anterior: jest.fn(), siguiente: jest.fn(), puedeSeguir: true, irAHoy: jest.fn(), recargarMiembros,
});
const espiaAlcance = jest.spyOn(AlcanceContext, 'useAlcance');
jest.spyOn(AlcanceContext, 'useAlcanceOpcional').mockImplementation(() => alcance({ tipo: 'PERSONA', userId: 4 }) as any);

const Envoltura: React.FC<{ children: React.ReactNode }> = ({ children }) => <ThemeProvider>{children}</ThemeProvider>;
const nav = () => ({ navigate: jest.fn(), goBack: jest.fn(), replace: jest.fn(), canGoBack: () => true });

const categoria = (over: object) => ({
  categoryId: 42, categoryName: 'Delivery', shared: true, kind: 'EXPENSE', allocated: 0, actual: 346248,
  committed: 0, available: -346248, difference: -346248, executionPct: 0, status: 'SIN_PRESUPUESTO', hasAllocation: false,
  plannedInItems: 0, provision: 0, provisionLeft: 0, otherSpending: 346248, byPerson: [], items: [], ...over,
});
const estado = (over: object = {}) => ({
  scope: 'PERSONA', periodStart: '2026-10-28', periodEnd: '2026-11-27', closed: false,
  totals: { incomeExpected: 0, incomeReceived: 0, incomePending: 0, payrollDeductions: 0, budgeted: 0, unassigned: 0,
    spent: 346248, committed: 0, savingsGoal: 0, saved: 0, savingsRatePct: 0, unbudgetedSpending: 346248,
    provisionsLeft: 0, toPay: 0, freeToSpend: 0, freeNow: 0, executionPct: 0 },
  compliance: {}, categories: [categoria({})], pendingCount: 0, incomeByPerson: [], incomeItems: [], responsibilities: [],
  ...over,
});

beforeEach(() => {
  jest.clearAllMocks();
  espiaAlcance.mockReturnValue(alcance({ tipo: 'PERSONA', userId: 4 }) as any);
  (estadoService.periodo as jest.Mock).mockResolvedValue({ correct: true, object: estado() });
  (categoryService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [
    { id: '42', name: 'Delivery', type: 'EXPENSE', householdId: 1, userId: '3', active: true },
    { id: '27', name: 'Casa', type: 'EXPENSE', householdId: 1, userId: '3', active: true },
    { id: '50', name: 'Uñas Samy', type: 'EXPENSE', userId: '4', active: true },
    { id: '51', name: 'Personal Andy', type: 'EXPENSE', userId: '3', active: true },
    { id: '60', name: 'Salario', type: 'INCOME', householdId: 1, active: true },
  ] } });
  (budgetAllocationService.list as jest.Mock).mockResolvedValue({ correct: true, object: [{ id: 9, categoryId: 27 }] });
});

describe('Presupuesto: se puede presupuestar una categoría que ya tiene gastos', () => {
  it('en la vista propia ofrece Delivery (común, con gastos) y lo privado propio, no lo ya asignado', async () => {
    const { findByLabelText, getByLabelText, queryByLabelText, queryByText } = render(
      <Envoltura><PresupuestoScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Presupuestar otra categoría'));
    expect(await findByLabelText('Presupuestar Delivery')).toBeTruthy();
    expect(getByLabelText('Presupuestar Uñas Samy')).toBeTruthy();
    expect(queryByLabelText('Presupuestar Casa')).toBeNull();
    expect(queryByLabelText('Presupuestar Personal Andy')).toBeNull();
    expect(queryByLabelText('Presupuestar Salario')).toBeNull();
    expect(queryByText(/ya tienen presupuesto/)).toBeNull();
    expect(budgetAllocationService.list).toHaveBeenCalledWith('4', 28, '2026-10-28');
  });

  it('"Copiar del periodo anterior" dice la verdad cuando no copió nada', async () => {
    (budgetAllocationService.copyPrevious as jest.Mock).mockResolvedValue({ correct: true, object: { copied: 0 } });
    const { findByLabelText } = render(<Envoltura><PresupuestoScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Copiar el presupuesto del periodo anterior'));
    await waitFor(() => expect(toast).toHaveBeenCalledWith('info', 'No había nada nuevo para copiar', expect.any(String)));
    expect(toast).not.toHaveBeenCalledWith('success', 'Presupuesto copiado', expect.anything());
  });

  it('y cuando sí copió, dice cuántas', async () => {
    (budgetAllocationService.copyPrevious as jest.Mock).mockResolvedValue({ correct: true, object: { copied: 3 } });
    const { findByLabelText } = render(<Envoltura><PresupuestoScreen navigation={nav()} /></Envoltura>);
    fireEvent.press(await findByLabelText('Copiar el presupuesto del periodo anterior'));
    await waitFor(() => expect(toast).toHaveBeenCalledWith('success', 'Presupuesto copiado', expect.stringContaining('3 categorías')));
  });

  it('en el detalle de una categoría común vista desde lo propio, ofrece presupuestarla en el hogar', async () => {
    const { findByLabelText } = render(
      <Envoltura><CategoriaScreen navigation={nav()} route={{ params: { categoryId: 42 } }} /></Envoltura>);
    fireEvent.press(await findByLabelText('Presupuestar Delivery para el hogar'));
    expect(setAlcance).toHaveBeenCalledWith({ tipo: 'HOGAR' });
  });

  it('un estado de categoría desconocido no tumba la pantalla', async () => {
    (estadoService.periodo as jest.Mock).mockResolvedValue({ correct: true, object: estado({ categories: [categoria({ status: 'NUEVO' })] }) });
    const { findByText } = render(<Envoltura><PresupuestoScreen navigation={nav()} /></Envoltura>);
    expect(await findByText('Delivery')).toBeTruthy();
  });
});

describe('Ingresos: la lista suma lo mismo que el encabezado', () => {
  const conIngresos = estado({
    incomeByPerson: [{ userId: 4, name: 'Samy', expected: 7442294, received: 4142294 }],
    incomeItems: [
      { scheduledMovementId: 7, name: 'Salario Samy', date: '2026-11-01', amount: 3642294, status: 'PAGADO', kind: 'PAYMENT', payerUserId: 4 },
      { scheduledMovementId: null, movementId: 68, name: 'Prima', date: '2026-11-02', amount: 500000, status: 'PAGADO', kind: 'PAYMENT', payerUserId: 4 },
      { scheduledMovementId: 8, name: 'Salario Samy', date: '2026-11-15', amount: 3000000, status: 'PREVISTO', kind: 'PAYMENT', payerUserId: 4 },
    ],
  });

  it('muestra los ingresos no programados y lo que falte sale como "otros"', async () => {
    (estadoService.periodo as jest.Mock).mockResolvedValue({ correct: true, object: conIngresos });
    const { findByText, getByText, getByLabelText } = render(<Envoltura><IngresosScreen navigation={nav()} /></Envoltura>);
    expect(await findByText(/Prima · 02\/11 · no programado/)).toBeTruthy();
    expect(getByLabelText(/Otros ingresos sin detalle: .*300.000/)).toBeTruthy();
    expect(getByText(/4.142.294 de .*7.442.294/)).toBeTruthy();
  });

  it('agruparIngresos: cada peso tiene su línea', () => {
    const g = agruparIngresos(conIngresos as any);
    const suma = g[0].items.reduce((t, i) => t + i.amount, 0) + g[0].sinDetalle;
    expect(suma).toBe(7442294);
    const cuadra = agruparIngresos({ incomeByPerson: [{ userId: 4, expected: 100, received: 0 }],
      incomeItems: [{ scheduledMovementId: null, payerUserId: 4, amount: 100, status: 'PENDIENTE' } as any] });
    expect(cuadra[0].sinDetalle).toBe(0);
    const huerfano = agruparIngresos({ incomeByPerson: [], incomeItems: [{ scheduledMovementId: null, payerUserId: 9, amount: 50, status: 'PAGADO' } as any] });
    expect(huerfano[0]).toEqual(expect.objectContaining({ nombre: 'Otros ingresos', esperado: 50, recibido: 50 }));
  });
});

describe('Plan: estados y fechas legibles', () => {
  it('lo liberado se dice "Liberado" y los avisos traen fechas y cifras humanas', async () => {
    const item = (name: string, status: string) => ({ scheduledMovementId: name.length, name, date: '2026-11-01', amount: 120000,
      status, kind: 'PAYMENT', income: false, categoryId: 1, categoryName: 'X', shared: true, payerUserId: 4 });
    const ventana = { start: '2026-11-01', end: '2026-11-14', grossIncome: 0, deductions: 0, netIncome: 0, payments: 0,
      paymentsPaid: 0, paymentsLeft: 0, savings: 0, provisions: 0, leftover: 0, tight: false,
      incomeItems: [], deductionItems: [], paymentItems: [item('Parqueadero', 'LIBERADO'), item('Raro', 'OTRO')], savingItems: [], provisionItems: [] };
    (planService.quincena as jest.Mock).mockResolvedValue({ correct: true, object: {
      today: '2026-11-05', payDates: [], hasIncome: true, current: ventana, next: ventana, overdueFromBefore: [], installments: [],
      alerts: [{ type: 'VENCIDO', message: 'Gym vencio el 2026-11-01' }, { type: 'CUOTA_TERMINA', message: 'Carro: quedan 2 cuota(s); al terminar se liberan 190000.00 al mes' }],
    } });
    const { findByText, getAllByText, getByText } = render(<Envoltura><PlanScreen /></Envoltura>);
    expect(await findByText(/Gym venció el 1 nov/)).toBeTruthy();
    expect(getByText(/quedan 2 cuotas; al terminar se liberan \$190\.000 al mes/)).toBeTruthy();
    expect(getAllByText(/Liberado \(no se paga\)/).length).toBeGreaterThan(0);
    expect(getAllByText(/Previsto/).length).toBeGreaterThan(0);
  });
});

describe('Por confirmar', () => {
  it('singular y plural en el aviso', () => {
    expect(textoConfirmados(1)).toBe('1 confirmado');
    expect(textoConfirmados(3)).toBe('3 confirmados');
  });
});

describe('Entrar', () => {
  it('si la sesión caducó, la entrada lo dice', () => {
    jest.spyOn(AuthContext, 'useAuth').mockReturnValueOnce({ ...auth, isAuthenticated: false,
      avisoDeSesion: AuthContext.AVISO_SESION_EXPIRADA, limpiarAvisoDeSesion: jest.fn() } as any);
    const { getByText } = render(<Envoltura><LoginScreen navigation={nav() as any} route={{} as any} /></Envoltura>);
    expect(getByText(AuthContext.AVISO_SESION_EXPIRADA)).toBeTruthy();
  });

  it('un 429 muestra el mensaje del servidor tal cual', async () => {
    const login = jest.fn().mockRejectedValue({ response: { status: 429, data: { message: 'Demasiados intentos. Espera unos minutos y vuelve a intentarlo.' } } });
    jest.spyOn(AuthContext, 'useAuth').mockReturnValue({ ...auth, isAuthenticated: false, login } as any);
    const { getByText, getByPlaceholderText } = render(<Envoltura><LoginScreen navigation={nav() as any} route={{} as any} /></Envoltura>);
    fireEvent.changeText(getByPlaceholderText('tu@correo.com'), 'a@b.com');
    fireEvent.changeText(getByPlaceholderText('Tu contraseña'), 'clave123');
    fireEvent.press(getByText('Iniciar sesión'));
    await waitFor(() => expect(toast).toHaveBeenCalledWith('error', 'No se pudo entrar', 'Demasiados intentos. Espera unos minutos y vuelve a intentarlo.'));
    jest.spyOn(AuthContext, 'useAuth').mockReturnValue(auth as any);
  });
});

describe('Hogar', () => {
  const hogar = { correct: true, object: [{ householdId: 1, name: 'Casa', role: 'MEMBER', memberCount: 2,
    members: [{ userId: 3, role: 'OWNER' }, { userId: 4, role: 'MEMBER' }] }] };

  it('quien no es dueño puede salir del hogar, con confirmación dentro de la app', async () => {
    (householdService.getByUser as jest.Mock).mockResolvedValue(hogar);
    (householdService.removeMember as jest.Mock).mockResolvedValue({ correct: true });
    const { findByLabelText, getByText, queryByText } = render(<Envoltura><HouseholdScreen /></Envoltura>);
    fireEvent.press(await findByLabelText('Salir del hogar Casa'));
    expect(getByText('¿Salir de Casa?')).toBeTruthy();
    expect(householdService.removeMember).not.toHaveBeenCalled();
    fireEvent.press(getByText('Sí, salir del hogar'));
    await waitFor(() => expect(householdService.removeMember).toHaveBeenCalledWith(1, 4));
    expect(toast).toHaveBeenCalledWith('success', 'Saliste del hogar', 'Casa');
    expect(recargarMiembros).toHaveBeenCalled();
    expect(queryByText('Crear hogar')).toBeNull();
  });

  it('al invitar, el 400 del servidor se muestra tal cual', async () => {
    const msg = 'No se pudo agregar a esa persona. Revisa el correo; si ya está en un núcleo familiar, primero debe salir de él.';
    (householdService.getByUser as jest.Mock).mockResolvedValue({ correct: true, object: [{ ...hogar.object[0], role: 'OWNER',
      members: [{ userId: 4, role: 'OWNER' }] }] });
    (householdService.inviteByEmail as jest.Mock).mockRejectedValue({ message: 'Request failed with status code 400', response: { status: 400, data: { message: msg } } });
    const { findByPlaceholderText, getByText } = render(<Envoltura><HouseholdScreen /></Envoltura>);
    fireEvent.changeText(await findByPlaceholderText('Ej: pareja@correo.com'), 'x@y.com');
    fireEvent.press(getByText('Invitar'));
    await waitFor(() => expect(toast).toHaveBeenCalledWith('error', 'No se pudo invitar', msg));
  });
});

describe('Categorías: sin duplicados por mayúsculas o tildes', () => {
  it('normaliza y compara en el mismo nivel', () => {
    expect(normalizarNombre('  Café  Bar ')).toBe('cafe bar');
    const lista: any[] = [{ id: '1', name: 'Casa', active: true }, { id: '2', name: 'Agua', parentId: '1', active: true }];
    expect(buscarNombreRepetido(lista, 'casa', null, null)?.id).toBe('1');
    expect(buscarNombreRepetido(lista, 'CASA', null, '1')).toBeUndefined();
    expect(buscarNombreRepetido(lista, 'agua', null, null)).toBeUndefined();
    expect(buscarNombreRepetido(lista, 'Água', '1', null)?.id).toBe('2');
  });

  it('el formulario no guarda "casa" si ya existe "Casa"', async () => {
    (categoryService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [{ id: '27', name: 'Casa', active: true }] } });
    (householdService.getByUser as jest.Mock).mockResolvedValue({ correct: true, object: [] });
    const { getByText, getByPlaceholderText } = render(
      <Envoltura><CategoryFormScreen navigation={{ goBack: jest.fn(), navigate: jest.fn() } as any} route={{ params: {} } as any} /></Envoltura>);
    fireEvent.changeText(getByPlaceholderText(/Nombre/), 'casa');
    fireEvent.press(getByText('Guardar'));
    await waitFor(() => expect(toast).toHaveBeenCalledWith('warning', 'Ese nombre ya existe', expect.stringContaining('"Casa"')));
    expect(categoryService.save).not.toHaveBeenCalled();
  });
});
