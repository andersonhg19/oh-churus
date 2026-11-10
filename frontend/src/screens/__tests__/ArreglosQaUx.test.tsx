import React from 'react';
import { Platform, StyleSheet } from 'react-native';
import { render, waitFor, fireEvent, act } from '@testing-library/react-native';
import { ThemeProvider } from '../../contexts/ThemeContext';
import * as AuthContext from '../../contexts/AuthContext';
import * as AlcanceContext from '../../contexts/AlcanceContext';
import InicioScreen from '../inicio/InicioScreen';
import AnotarScreen from '../anotar/AnotarScreen';
import MovementFormScreen from '../movements/MovementFormScreen';
import MovimientosScreen from '../movimientos/MovimientosScreen';
import Teclado, { accionDeTecla, useTecladoFisico } from '../../components/molecules/Teclado';
import { casillasDelMes, fechaLarga, maximoPorDefecto, moverDias } from '../../components/molecules/Calendario';
import { cifraQueCabe, nombreQueCabe } from '../../components/molecules/DonaDeCategorias';
import { comoSePago, nombreTarjeta } from '../../utils/medioDePago';
import { contraste } from '../../theme/contraste';
import { darkColors, lightColors } from '../../theme/colors';
import { estadoService } from '../../services/estadoService';
import { dashboardService } from '../../services/dashboardService';
import { movementService } from '../../services/movementService';
import { categoryService } from '../../services/categoryService';
import { deudaService } from '../../services/deudaService';
import { fechaLocalISO } from '../../utils/format';

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
jest.mock('../../services/movementService', () => ({ movementService: { save: jest.fn(), getAll: jest.fn(), delete: jest.fn() } }));
jest.mock('../../services/categoryService', () => ({ categoryService: { getAll: jest.fn() } }));
jest.mock('../../services/deudaService', () => ({ deudaService: { todas: jest.fn(), misTarjetas: jest.fn() } }));

jest.spyOn(AuthContext, 'useAuth').mockReturnValue({
  user: { userId: '3', name: 'Andy', email: 'a@b.com', budgetStartDay: 28 },
  token: 't', loading: false, isAuthenticated: true, esAdmin: false,
  login: jest.fn(), register: jest.fn(), logout: jest.fn(), updateUser: jest.fn(),
} as any);
const alcanceBase = {
  alcance: { tipo: 'HOGAR' }, setAlcance: jest.fn(),
  miembros: [{ userId: 3, nombre: 'Andy' }, { userId: 4, nombre: 'Samy' }],
  nombreDe: (id: any) => (String(id) === '3' ? 'Andy' : String(id) === '4' ? 'Samy' : ''),
  etiquetaAlcance: 'Hogar', corte: 28, periodo: { inicio: '2026-10-28', fin: '2026-11-27' },
  anterior: jest.fn(), siguiente: jest.fn(), puedeSeguir: true, irAHoy: jest.fn(), recargarMiembros: jest.fn(),
};
const espiaAlcance = jest.spyOn(AlcanceContext, 'useAlcance').mockReturnValue(alcanceBase as any);

const Envoltura: React.FC<{ children: React.ReactNode }> = ({ children }) => <ThemeProvider>{children}</ThemeProvider>;
const nav = (extra: object = {}) => ({ navigate: mockNavegar, goBack: jest.fn(), replace: jest.fn(), dispatch: jest.fn(), ...extra });

const estado = {
  scope: 'HOGAR', periodStart: '2026-10-28', periodEnd: '2026-11-27', closed: false,
  totals: { incomeExpected: 5000000, incomeReceived: 2000000, incomePending: 0, payrollDeductions: 0, budgeted: 3000000,
    unassigned: 0, spent: 1000000, committed: 0, savingsGoal: 0, saved: 0, savingsRatePct: 0, unbudgetedSpending: 0,
    provisionsLeft: 0, toPay: 642000, freeToSpend: 2000000, freeNow: 0, executionPct: 0, savingsBalance: 0 },
  compliance: {}, pendingCount: 0, incomeByPerson: [], incomeItems: [], responsibilities: [],
  categories: [
    { categoryId: 1, categoryName: 'Mercado', kind: 'EXPENSE', actual: 600000, allocated: 1000000, items: [], byPerson: [] },
    { categoryId: 2, categoryName: 'Ahorro viaje', kind: 'SAVING', actual: 0, allocated: 0, balance: 900000, items: [], byPerson: [] },
  ],
};
const pendiente = (id: string, userId: string, desc: string, amount: number) => ({
  id, userId, categoryId: '27', categoryName: 'Casa', categoryType: 'EXPENSE', amount, description: desc,
  date: '2026-11-01', confirmed: false, shared: true,
});
const NU = { id: 7, name: 'Tarjeta de Crédito Nu', mine: true, owed: 0, dueNow: 0, upcoming: 0, purchases: [], userId: 3 };

beforeEach(() => {
  jest.clearAllMocks();
  espiaAlcance.mockReturnValue(alcanceBase as any);
  (estadoService.periodo as jest.Mock).mockResolvedValue({ correct: true, object: estado });
  (dashboardService.getPending as jest.Mock).mockResolvedValue({ correct: true, object: [
    pendiente('1', '3', 'Arriendo', 500000), pendiente('2', '3', 'Internet', 72000), pendiente('3', '4', 'Natillera', 315000),
  ] });
  (deudaService.todas as jest.Mock).mockResolvedValue({ correct: true, object: { totalOwed: 0, monthlyInstallments: 0 } });
  (deudaService.misTarjetas as jest.Mock).mockResolvedValue({ correct: true, object: [NU] });
  (categoryService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [
    { id: '42', name: 'Delivery', type: 'EXPENSE', householdId: 1, active: true },
    { id: '27', name: 'Casa', type: 'EXPENSE', householdId: 1, active: true },
  ] } });
  (movementService.save as jest.Mock).mockResolvedValue({ correct: true, object: {} });
});

const teclear = (getByLabelText: any, teclas: string[]) => teclas.forEach((t) => fireEvent.press(getByLabelText(t)));
const colorDe = (el: any) => StyleSheet.flatten(el.props.style)?.color;

describe('Anotar: "Más" no pierde nada', () => {
  it('pasa monto, categoría, fecha, nota, tarjeta y cuotas al formulario completo', async () => {
    const navegacion = nav();
    const { findByLabelText, getByLabelText } = render(
      <Envoltura><AnotarScreen navigation={navegacion} route={{ params: { tipo: 'EXPENSE', categoriaId: '27' } }} /></Envoltura>);
    fireEvent.press(await findByLabelText(/Pagado con Fondos del mes/));
    fireEvent.press(await findByLabelText(/^Tarjeta de Crédito Nu\./));
    /* El nombre ya dice "Tarjeta": no se repite. */
    expect(getByLabelText('Pagado con Tarjeta de Crédito Nu. Cambiar')).toBeTruthy();
    fireEvent.press(getByLabelText('6 cuotas'));
    teclear(getByLabelText, ['3', '5', 'tres ceros']);
    fireEvent.press(getByLabelText('Agregar una nota'));
    fireEvent.changeText(getByLabelText('Nota del movimiento'), 'mercado grande');
    fireEvent.press(getByLabelText(/^Más opciones/));
    expect(navegacion.replace).toHaveBeenCalledWith('MovementFormModal', { prefill: expect.objectContaining({
      amount: 35000, categoryId: '27', type: 'EXPENSE', date: fechaLocalISO(), description: 'mercado grande',
      confirmed: true, cardId: '7', installments: 6,
    }) });
  });

  it('con el ahorro, pasa de qué ahorro sale', async () => {
    const navegacion = nav();
    const { findByLabelText, getByLabelText } = render(
      <Envoltura><AnotarScreen navigation={navegacion} route={{ params: { tipo: 'EXPENSE', categoriaId: '42' } }} /></Envoltura>);
    fireEvent.press(await findByLabelText(/Pagado con Fondos del mes/));
    fireEvent.press(await findByLabelText(/^Ahorro viaje\. Hay/));
    teclear(getByLabelText, ['5', 'tres ceros']);
    fireEvent.press(getByLabelText(/^Más opciones/));
    const { prefill } = navegacion.replace.mock.calls[0][1];
    expect(prefill).toEqual(expect.objectContaining({ fundedFromCategoryId: '2', amount: 5000 }));
    expect(prefill).not.toHaveProperty('cardId');
  });

  it('el formulario completo lee el medio y las cuotas y los guarda', async () => {
    const navegacion = nav();
    const { findByLabelText, getByText } = render(
      <Envoltura><MovementFormScreen navigation={navegacion as any} route={{ params: { prefill: {
        amount: 35000, categoryId: '27', type: 'EXPENSE', date: '2026-11-05', description: 'mercado', confirmed: true,
        cardId: '7', installments: 6,
      } } } as any} /></Envoltura>);
    expect(await findByLabelText('Pagado con Tarjeta de Crédito Nu. Cambiar')).toBeTruthy();
    expect(await findByLabelText('6 cuotas')).toBeTruthy();
    await waitFor(() => expect(getByText('Casa')).toBeTruthy());
    fireEvent.press(getByText('Guardar'));
    await waitFor(() => expect(movementService.save).toHaveBeenCalledWith(expect.objectContaining({
      categoryId: '27', amount: 35000, cardId: 7, installments: 6, date: '2026-11-05', description: 'mercado',
    })));
  });
});

describe('Cerrar con algo escrito pregunta', () => {
  it('Anotar: la X pregunta "¿Descartar lo escrito?"; seguir no cierra, descartar sí', async () => {
    const navegacion = nav();
    const { findByLabelText, getByLabelText, queryByText, getByText } = render(
      <Envoltura><AnotarScreen navigation={navegacion} route={{ params: { tipo: 'EXPENSE' } }} /></Envoltura>);
    await findByLabelText('Elegir la categoría');
    teclear(getByLabelText, ['5', 'tres ceros']);
    fireEvent.press(getByLabelText('Cerrar sin anotar'));
    expect(getByText('¿Descartar lo escrito?')).toBeTruthy();
    expect(navegacion.goBack).not.toHaveBeenCalled();
    fireEvent.press(getByLabelText('Seguir escribiendo'));
    expect(queryByText('¿Descartar lo escrito?')).toBeNull();
    fireEvent.press(getByLabelText('Cerrar sin anotar'));
    fireEvent.press(getByLabelText('Descartar'));
    expect(navegacion.goBack).toHaveBeenCalled();
  });

  it('Anotar: sin nada escrito, la X cierra de una', async () => {
    const navegacion = nav();
    const { findByLabelText, getByLabelText, queryByText } = render(
      <Envoltura><AnotarScreen navigation={navegacion} route={{ params: { tipo: 'EXPENSE', categoriaId: '42' } }} /></Envoltura>);
    await findByLabelText(/Categoría Delivery/);
    fireEvent.press(getByLabelText('Cerrar sin anotar'));
    expect(queryByText('¿Descartar lo escrito?')).toBeNull();
    expect(navegacion.goBack).toHaveBeenCalled();
  });

  it('Anotar: el atrás del navegador también pregunta, y al descartar sigue con esa navegación', async () => {
    let alSalir: any = null;
    const navegacion = nav({ addListener: jest.fn((_ev: string, f: any) => { alSalir = f; return jest.fn(); }) });
    const { findByLabelText, getByLabelText, getByText } = render(
      <Envoltura><AnotarScreen navigation={navegacion} route={{ params: { tipo: 'EXPENSE' } }} /></Envoltura>);
    await findByLabelText('Elegir la categoría');
    teclear(getByLabelText, ['9']);
    const evento = { preventDefault: jest.fn(), data: { action: { type: 'GO_BACK' } } };
    act(() => alSalir(evento));
    expect(evento.preventDefault).toHaveBeenCalled();
    expect(getByText('¿Descartar lo escrito?')).toBeTruthy();
    fireEvent.press(getByLabelText('Descartar'));
    expect(navegacion.dispatch).toHaveBeenCalledWith({ type: 'GO_BACK' });
  });

  it('formulario completo: lo que llegó de "Más" no se pierde al cerrar sin preguntar', async () => {
    let alSalir: any = null;
    const navegacion = nav({ addListener: jest.fn((_ev: string, f: any) => { alSalir = f; return jest.fn(); }) });
    const { getByText } = render(
      <Envoltura><MovementFormScreen navigation={navegacion as any} route={{ params: { prefill: { amount: 5000, type: 'EXPENSE' } } } as any} /></Envoltura>);
    const evento = { preventDefault: jest.fn(), data: { action: { type: 'GO_BACK' } } };
    act(() => alSalir(evento));
    expect(evento.preventDefault).toHaveBeenCalled();
    expect(getByText('¿Descartar lo escrito?')).toBeTruthy();
  });

  it('formulario de edición sin cambios cierra sin preguntar', async () => {
    let alSalir: any = null;
    const navegacion = nav({ addListener: jest.fn((_ev: string, f: any) => { alSalir = f; return jest.fn(); }) });
    const movement = { id: 'm1', userId: '3', categoryId: '27', categoryType: 'EXPENSE', amount: 9000, date: '2026-11-01', confirmed: true };
    const { findByText } = render(
      <Envoltura><MovementFormScreen navigation={navegacion as any} route={{ params: { movement } } as any} /></Envoltura>);
    await findByText('Actualizar');
    await waitFor(() => expect(categoryService.getAll).toHaveBeenCalled());
    const evento = { preventDefault: jest.fn(), data: { action: { type: 'GO_BACK' } } };
    act(() => alSalir(evento));
    expect(evento.preventDefault).not.toHaveBeenCalled();
  });
});

describe('La fecha con calendario', () => {
  it('las casillas del mes empiezan el lunes y cierran la semana', () => {
    /* Noviembre de 2026 empieza en domingo: 6 huecos antes del 1. */
    const c = casillasDelMes(2026, 10);
    expect(c.slice(0, 7)).toEqual([null, null, null, null, null, null, 1]);
    expect(c.length % 7).toBe(0);
    expect(c.filter((x) => x !== null)).toHaveLength(30);
    expect(fechaLarga('2026-11-10')).toBe('10 de noviembre de 2026');
    expect(maximoPorDefecto('2026-11-10')).toBe('2027-11-10');
    expect(moverDias('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('Anotar: un toque en el día del calendario y se anota con esa fecha', async () => {
    const hoy = fechaLocalISO();
    const dia = hoy.endsWith('-01') ? moverDias(hoy, 1) : `${hoy.slice(0, 8)}01`;
    const { findByLabelText, getByLabelText } = render(
      <Envoltura><AnotarScreen navigation={nav()} route={{ params: { tipo: 'EXPENSE', categoriaId: '42' } }} /></Envoltura>);
    await findByLabelText(/Categoría Delivery/);
    fireEvent.press(getByLabelText('Fecha Hoy. Cambiar la fecha'));
    expect(getByLabelText(new RegExp(`^${fechaLarga(hoy)}, hoy$`))).toBeTruthy();
    fireEvent.press(getByLabelText(new RegExp(`^${fechaLarga(dia)}`)));
    teclear(getByLabelText, ['8', 'tres ceros']);
    fireEvent.press(getByLabelText(/^Anotar/));
    await waitFor(() => expect(movementService.save).toHaveBeenCalledWith(expect.objectContaining({ date: dia, amount: 8000 })));
  });

  it('no deja ir más allá de un año adelante', async () => {
    const { findByLabelText, getByLabelText } = render(
      <Envoltura><AnotarScreen navigation={nav()} route={{ params: { tipo: 'EXPENSE', categoriaId: '42' } }} /></Envoltura>);
    await findByLabelText(/Categoría Delivery/);
    fireEvent.press(getByLabelText('Fecha Hoy. Cambiar la fecha'));
    for (let i = 0; i < 12; i++) fireEvent.press(getByLabelText('Mes siguiente'));
    expect(getByLabelText('Mes siguiente').props.accessibilityState).toEqual(expect.objectContaining({ disabled: true }));
    const pasado = moverDias(maximoPorDefecto(), 1);
    if (pasado.slice(0, 7) === maximoPorDefecto().slice(0, 7)) {
      expect(getByLabelText(new RegExp(`^${fechaLarga(pasado)}`)).props.accessibilityState).toEqual(expect.objectContaining({ disabled: true }));
    }
  });

  it('el formulario completo ya no pide escribir "AAAA-MM-DD"', async () => {
    const { getByLabelText, queryByPlaceholderText } = render(
      <Envoltura><MovementFormScreen navigation={nav() as any} route={{ params: { prefill: { date: '2026-11-05' } } } as any} /></Envoltura>);
    expect(queryByPlaceholderText('YYYY-MM-DD')).toBeNull();
    fireEvent.press(getByLabelText('Fecha 5 de noviembre de 2026. Cambiar la fecha'));
    fireEvent.press(getByLabelText(/^3 de noviembre de 2026/));
    expect(getByLabelText('Fecha 3 de noviembre de 2026. Cambiar la fecha')).toBeTruthy();
  });
});

describe('Teclado físico en el computador', () => {
  it('traduce las teclas', () => {
    expect(accionDeTecla('7')).toBe('7');
    expect(accionDeTecla('*')).toBe('×');
    expect(accionDeTecla('/')).toBe('÷');
    expect(accionDeTecla('-')).toBe('-');
    expect(accionDeTecla('+')).toBe('+');
    expect(accionDeTecla('Backspace')).toBe('⌫');
    expect(accionDeTecla('Enter')).toBe('listo');
    expect(accionDeTecla('Escape')).toBe('cerrar');
    expect(accionDeTecla(',')).toBe('nada');
    expect(accionDeTecla('.')).toBe('nada');
    expect(accionDeTecla('a')).toBeNull();
  });

  describe('en la web', () => {
    const osAntes = Platform.OS;
    const docAntes = (globalThis as any).document;
    let oyentes: ((e: any) => void)[] = [];
    let enfocado: any = null;
    beforeEach(() => {
      oyentes = [];
      enfocado = null;
      (Platform as any).OS = 'web';
      (globalThis as any).document = {
        addEventListener: (_t: string, f: any) => oyentes.push(f),
        removeEventListener: (_t: string, f: any) => { oyentes = oyentes.filter((x) => x !== f); },
        get activeElement() { return enfocado; },
      };
    });
    afterEach(() => {
      (Platform as any).OS = osAntes;
      (globalThis as any).document = docAntes;
    });
    const tecla = (key: string) => act(() => { oyentes.forEach((f) => f({ key, preventDefault: jest.fn() })); });

    const Prueba: React.FC<{ onTecla: any; onListo: any; onCerrar: any }> = (p) => {
      useTecladoFisico({ activo: true, ...p });
      return null;
    };

    it('escribe, anota con Enter y cierra con Escape; no se mete en un campo de texto salvo Escape', () => {
      const onTecla = jest.fn(); const onListo = jest.fn(); const onCerrar = jest.fn();
      const r = render(<Prueba onTecla={onTecla} onListo={onListo} onCerrar={onCerrar} />);
      tecla('1'); tecla('*'); tecla('Backspace'); tecla(',');
      expect(onTecla.mock.calls.map((c) => c[0])).toEqual(['1', '×', '⌫']);
      tecla('Enter');
      expect(onListo).toHaveBeenCalledTimes(1);
      enfocado = { tagName: 'INPUT' };
      tecla('5');
      expect(onTecla).toHaveBeenCalledTimes(3);
      tecla('Escape');
      expect(onCerrar).toHaveBeenCalledTimes(1);
      r.unmount();
      expect(oyentes).toHaveLength(0);
    });
  });
});

describe('Contraste', () => {
  it('los botones Gasto e Ingreso de Inicio se leen (≥ 4,5:1)', async () => {
    const { findByText, getByText } = render(<Envoltura><InicioScreen navigation={nav()} /></Envoltura>);
    const gasto = await findByText('Gasto');
    expect(contraste(colorDe(gasto), darkColors.expense)).toBeGreaterThanOrEqual(4.5);
    expect(contraste(colorDe(getByText('Ingreso')), darkColors.income)).toBeGreaterThanOrEqual(4.5);
  });

  it('"Anotar" deshabilitado se lee (≥ 3:1) y habilitado ≥ 4,5:1', () => {
    const { getByText, rerender } = render(<Envoltura><Teclado expresion="" onCambio={jest.fn()} onListo={jest.fn()} textoListo="Anotar" listoDeshabilitado /></Envoltura>);
    expect(contraste(colorDe(getByText('Anotar')), darkColors.surfaceLight)).toBeGreaterThanOrEqual(3);
    rerender(<Envoltura><Teclado expresion="5" onCambio={jest.fn()} onListo={jest.fn()} textoListo="Anotar" /></Envoltura>);
    expect(contraste(colorDe(getByText('Anotar')), darkColors.primary)).toBeGreaterThanOrEqual(4.5);
    expect(lightColors.primary).toBeTruthy();
  });

  it('la cabecera de Anotar (gasto) escribe con tinta legible sobre el rojo', async () => {
    const { findByText } = render(
      <Envoltura><AnotarScreen navigation={nav()} route={{ params: { tipo: 'EXPENSE' } }} /></Envoltura>);
    expect(contraste(colorDe(await findByText('Gasto')), darkColors.expense)).toBeGreaterThanOrEqual(4.5);
  });
});

describe('La cifra del centro de la dona no se corta', () => {
  it('usa la cifra completa si cabe y la corta si no, con la letra que quepa', () => {
    const a = cifraQueCabe(-2000000, 124);
    expect(a.texto).toBe('-$2.000.000');
    expect(a.tamano * a.texto.length * 0.62).toBeLessThanOrEqual(124);
    const b = cifraQueCabe(1077000000, 124);
    expect(b.texto).toBe('$1.077 M');
    expect(b.tamano * b.texto.length * 0.62).toBeLessThanOrEqual(124);
    expect(cifraQueCabe(965000, 300).tamano).toBe(28);
  });

  it('los nombres de las etiquetas: completo, primera palabra o nada', () => {
    expect(nombreQueCabe('Mercado', 110)).toBe('Mercado');
    expect(nombreQueCabe('Comidas fuera de casa', 70)).toBe('Comidas');
    expect(nombreQueCabe('Mercado', 20)).toBeNull();
  });
});

describe('Cómo se pagó', () => {
  it('no repite "Tarjeta" y dice el nombre y la cuota', () => {
    expect(nombreTarjeta('Tarjeta de Crédito Nu')).toBe('Tarjeta de Crédito Nu');
    expect(nombreTarjeta('NU')).toBe('Tarjeta NU');
    expect(comoSePago({ cardId: 7, installmentNumber: 1, installmentTotal: 6 }, [], [NU])).toBe('Tarjeta de Crédito Nu · cuota 1/6');
    expect(comoSePago({ cardId: 99 }, [], [NU])).toBe('Con tarjeta');
    expect(comoSePago({ fundedFromCategoryId: 2 }, [{ id: '2', nombre: 'Ahorro viaje' }])).toBe('Con el Ahorro viaje');
    expect(comoSePago({ fundedFromCategoryId: 5 }, [{ id: '5', nombre: 'Carro' }])).toBe('Con el ahorro Carro');
    expect(comoSePago({ cardId: 0, fundedFromCategoryId: null })).toBe('Fondos del mes');
    expect(comoSePago({ categoryType: 'INCOME' })).toBe('');
  });

  it('editar: "¿Con qué se pagó?" es una lista vertical, con el nombre de la tarjeta sin repetir', async () => {
    const movement = { id: 'm1', userId: '3', categoryId: '27', categoryType: 'EXPENSE', amount: 5835, date: '2026-11-01',
      confirmed: true, cardId: 7, installmentNumber: 1, installmentTotal: 6 };
    const { findByLabelText, getByLabelText, queryByText } = render(
      <Envoltura><MovementFormScreen navigation={nav() as any} route={{ params: { movement } } as any} /></Envoltura>);
    fireEvent.press(await findByLabelText('Pagado con Tarjeta de Crédito Nu. Cambiar'));
    expect(queryByText(/Tarjeta Tarjeta/)).toBeNull();
    fireEvent.press(getByLabelText(/^Ahorro viaje\. Hay/));
    expect(getByLabelText('Pagado con Con el Ahorro viaje. Cambiar')).toBeTruthy();
  });
});

describe('Movimientos: lo pagado, lo por pagar y cómo se pagó', () => {
  const mov = (id: string, over: object) => ({
    id, userId: '3', categoryId: '27', categoryName: 'Casa', categoryType: 'EXPENSE', amount: 10000, description: `m${id}`,
    date: '2026-11-01', confirmed: true, shared: true, ...over,
  });
  it('el conteo separa pagados y por pagar, y cada fila dice con qué se pagó', async () => {
    (movementService.getAll as jest.Mock).mockResolvedValue({ correct: true, object: { list: [
      mov('1', { amount: 2300000, description: 'Arriendo' }),
      mov('2', { amount: 68000, description: 'Internet', cardId: 7, installmentNumber: 1, installmentTotal: 6 }),
      mov('3', { amount: 20000, description: 'Gas', fundedFromCategoryId: 2 }),
      mov('4', { amount: 90000, description: 'Energía', confirmed: false }),
      mov('5', { amount: 40000, description: 'Agua', confirmed: false }),
    ] } });
    const { findByLabelText, getByText, getByLabelText } = render(<Envoltura><MovimientosScreen navigation={nav()} /></Envoltura>);
    const fila = await findByLabelText(/^Casa: .*2.388.000 pagado, 3 pagados, 2 por pagar por .*130.000\. Expandir$/);
    expect(getByText('3 pagados · 2 por pagar · 100%')).toBeTruthy();
    expect(getByText(/^\+ .*130.000 por pagar$/)).toBeTruthy();
    fireEvent.press(fila);
    await waitFor(() => expect(getByText(/Tarjeta de Crédito Nu · cuota 1\/6/)).toBeTruthy());
    expect(getByText(/01\/11 · Fondos del mes · Andy/)).toBeTruthy();
    expect(getByText(/Con el Ahorro viaje/)).toBeTruthy();
    expect(getByLabelText(/^Energía, 01\/11 · por pagar · Fondos del mes/)).toBeTruthy();
  });
});

describe('Inicio: las cifras cuadran y siguen al selector', () => {
  it('"Por pagar" es lo mismo que descuenta lo libre (toPay)', async () => {
    const { findByLabelText, getByText } = render(<Envoltura><InicioScreen navigation={nav()} /></Envoltura>);
    expect(await findByLabelText(/^Por pagar .*642.000: 3 pagos por .*887.000/)).toBeTruthy();
    expect(getByText('$642 mil')).toBeTruthy();
  });

  it('viendo a Samy, el aviso dice los pagos de Samy y abre los suyos', async () => {
    espiaAlcance.mockReturnValue({ ...alcanceBase, alcance: { tipo: 'PERSONA', userId: 4 } } as any);
    const { findByText, getByLabelText } = render(<Envoltura><InicioScreen navigation={nav()} /></Envoltura>);
    expect(await findByText('Samy tiene 1 pago por confirmar')).toBeTruthy();
    fireEvent.press(getByLabelText(/^Samy tiene 1 pago por confirmar/));
    expect(mockNavegar).toHaveBeenCalledWith('PorConfirmar', { quien: 4 });
  });

  it('en Hogar, el aviso es lo mío', async () => {
    const { findByText } = render(<Envoltura><InicioScreen navigation={nav()} /></Envoltura>);
    expect(await findByText('Tienes 2 pagos por confirmar')).toBeTruthy();
  });
});
