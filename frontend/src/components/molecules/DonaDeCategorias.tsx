import React, { useMemo, useRef } from 'react';
import { View, StyleSheet, TouchableOpacity, useWindowDimensions, PanResponder } from 'react-native';
import Svg, { Path, Circle } from 'react-native-svg';
import AppText from '../atoms/Text';
import Icono from '../atoms/Icono';
import { tintaSobre } from '../../utils/tinta';
import { useTheme } from '../../contexts/ThemeContext';
import { formatCurrency, formatCompacto } from '../../utils/format';

export interface CategoriaEnDona {
  id: number;
  nombre: string;
  icono?: string;
  color: string;
  /** Lo gastado de verdad: el tamano de su porcion. */
  valor: number;
}

/** Colores para las categorias que no traen uno propio. */
export const PALETA = ['#6C5CE7', '#0984E3', '#00CEC9', '#FDCB6E', '#636E72', '#00B894', '#E84393',
  '#2BB0A0', '#E17055', '#A29BFE', '#74B9FF', '#FAB1A0', '#55EFC4', '#FF7675'];

/* Hasta 16 caben alrededor; si hay mas, el ultimo lugar es "Mas". Las que
   tienen gasto siempre estan entre las visibles. */
export const MAXIMO_ICONOS = 16;

/** Orden de las categorias: la que mas gasto primero; sin gasto o empatadas, por nombre. */
export const ordenarPorGasto = <T extends { valor: number; nombre: string }>(cs: T[]): T[] =>
  [...cs].sort((a, b) => (b.valor || 0) - (a.valor || 0) || a.nombre.localeCompare(b.nombre, 'es'));

export interface Lugar { categoria: CategoriaEnDona; angulo: number; medio?: number; fraccion: number }

const distancia = (a: number, b: number) => {
  const d = Math.abs(((a - b) % 360 + 360) % 360);
  return Math.min(d, 360 - d);
};

/**
 * Donde va cada categoria alrededor de la dona, como en Monefy pero mejor: los
 * lugares son fijos y parejos, y cada categoria con gasto se sienta en el
 * lugar libre mas cercano a SU porcion (la mas grande elige primero), asi la
 * linea que las une es corta. Las que no tienen gasto llenan el resto por
 * orden alfabetico. Angulos en grados desde las 12, en el sentido del reloj.
 */
export function ubicarCategorias(categorias: CategoriaEnDona[], maximo = MAXIMO_ICONOS) {
  const conGasto = ordenarPorGasto(categorias.filter((c) => c.valor > 0));
  const sinGasto = ordenarPorGasto(categorias.filter((c) => !(c.valor > 0)));
  const total = conGasto.reduce((t, c) => t + c.valor, 0);
  const sobran = conGasto.length + sinGasto.length > maximo;
  const cupoSin = Math.max(0, (sobran ? maximo - 1 : maximo) - conGasto.length);
  const visibles = [...conGasto, ...sinGasto.slice(0, cupoSin)];
  const lugares = Math.max(8, visibles.length + (sobran ? 1 : 0));
  const angulos = Array.from({ length: lugares }, (_, i) => (i * 360) / lugares);
  const libres = new Set(angulos.map((_, i) => i));
  /* El lugar de "Mas" es el ultimo (justo antes de las 12). */
  if (sobran) libres.delete(lugares - 1);

  const porciones: { c: CategoriaEnDona; a0: number; a1: number; medio: number }[] = [];
  let acumulado = 0;
  conGasto.forEach((c) => {
    const barrido = (c.valor / total) * 360;
    porciones.push({ c, a0: acumulado, a1: acumulado + barrido, medio: acumulado + barrido / 2 });
    acumulado += barrido;
  });

  const ubicadas: Lugar[] = [];
  porciones.forEach((p) => {
    let mejor = -1;
    libres.forEach((i) => { if (mejor < 0 || distancia(angulos[i], p.medio) < distancia(angulos[mejor], p.medio)) mejor = i; });
    if (mejor < 0) return;
    libres.delete(mejor);
    ubicadas.push({ categoria: p.c, angulo: angulos[mejor], medio: p.medio, fraccion: p.c.valor / total });
  });
  const resto = [...libres].sort((a, b) => a - b);
  sinGasto.slice(0, cupoSin).forEach((c, i) => {
    if (i < resto.length) ubicadas.push({ categoria: c, angulo: angulos[resto[i]], fraccion: 0 });
  });
  return {
    ubicadas,
    porciones,
    total,
    mas: sobran ? { angulo: angulos[lugares - 1], cuantas: categorias.length - ubicadas.length } : null,
    lugares,
  };
}

/** "34%", y "<1%" para lo que casi no pesa (para que no diga 0% algo que si tiene gasto). */
export const porcentaje = (f: number) => (f > 0 && f < 0.01 ? '<1%' : `${Math.round(f * 100)}%`);

interface Props {
  categorias: CategoriaEnDona[];
  ingresos: number;
  gastos: number;
  onCategoria: (c: CategoriaEnDona) => void;
  onCentro?: () => void;
  etiquetaCentro?: string;
  onMas?: () => void;
  /** Deslizar a los lados cambia de periodo. */
  onAnterior?: () => void;
  onSiguiente?: () => void;
}

const rad = (g: number) => ((g - 90) * Math.PI) / 180;

/*
 * LA PANTALLA PRINCIPAL, como Monefy: la dona de lo gastado por categoria en
 * el centro, con lo que entro y lo que salio adentro, y TODAS las categorias
 * alrededor para anotar con un toque. Cada porcion tiene una linea delgada
 * hasta su categoria, que se sienta al lado de su porcion y dice su %. Las
 * categorias sin gasto se ven apagadas, pero se pueden tocar igual.
 */
const DonaDeCategorias: React.FC<Props> = ({
  categorias, ingresos, gastos, onCategoria, onCentro, etiquetaCentro, onMas, onAnterior, onSiguiente,
}) => {
  const { colors } = useTheme();
  const { width } = useWindowDimensions();
  const S = Math.min(width - 32, 360);
  const { ubicadas, porciones, mas, lugares } = useMemo(() => ubicarCategorias(categorias), [categorias]);
  const icono = lugares > 12 ? 40 : 46;
  const radioIconos = S / 2 - icono / 2 - 2;
  /* Entre la dona y los iconos queda aire para las lineas. */
  const dona = S - icono * 2 - 44;
  const grosor = Math.max(16, dona * 0.13);
  const r = dona / 2 - grosor / 2;
  const c = S / 2;
  const punto = (radio: number, ang: number) => [c + radio * Math.cos(rad(ang)), c + radio * Math.sin(rad(ang))];

  const anteriorRef = useRef(onAnterior);
  const siguienteRef = useRef(onSiguiente);
  anteriorRef.current = onAnterior;
  siguienteRef.current = onSiguiente;
  const deslizar = useMemo(() => PanResponder.create({
    onMoveShouldSetPanResponderCapture: (_e, g) => Math.abs(g.dx) > 24 && Math.abs(g.dx) > Math.abs(g.dy) * 1.6,
    onPanResponderRelease: (_e, g) => {
      if (g.dx > 60) anteriorRef.current?.();
      else if (g.dx < -60) siguienteRef.current?.();
    },
  }), []);

  const centroAncho = dona - grosor * 2 - 8;
  const ing = formatCurrency(ingresos);
  const gas = formatCurrency(gastos);
  const largo = Math.max(ing.length, gas.length);
  const tamano = Math.max(14, Math.min(24, centroAncho / (largo * 0.62)));
  const corta = tamano <= 14 && largo * 0.62 * 14 > centroAncho;

  return (
    <View style={{ width: S, height: S, alignSelf: 'center' }} {...deslizar.panHandlers}>
      <Svg width={S} height={S} style={StyleSheet.absoluteFill} accessible={false} pointerEvents="none">
        <Circle cx={c} cy={c} r={r} stroke={colors.border} strokeWidth={grosor} fill="none" />
        {porciones.length === 1 && (
          <Circle cx={c} cy={c} r={r} stroke={porciones[0].c.color} strokeWidth={grosor} fill="none" />
        )}
        {porciones.length > 1 && porciones.map((p) => {
          const hueco = Math.min(1.2, (p.a1 - p.a0) / 4);
          const [x0, y0] = punto(r, p.a0 + hueco / 2);
          const [x1, y1] = punto(r, p.a1 - hueco / 2);
          const grande = p.a1 - p.a0 > 180 ? 1 : 0;
          return (
            <Path key={p.c.id} d={`M ${x0} ${y0} A ${r} ${r} 0 ${grande} 1 ${x1} ${y1}`}
              stroke={p.c.color} strokeWidth={grosor} fill="none" />
          );
        })}
        {ubicadas.filter((u) => u.medio != null).map((u, i) => {
          /* La linea sale derecho de su porcion, corre por el aire entre la
             dona y los iconos hasta quedar frente a su categoria, y sube a
             ella: nunca cruza por encima de la dona. */
          const rMedio = dona / 2 + 8 + (i % 3) * 3;
          const [ax, ay] = punto(dona / 2 + 2, u.medio!);
          const [bx, by] = punto(rMedio, u.medio!);
          const [cx2, cy2] = punto(rMedio, u.angulo);
          const [dx, dy] = punto(radioIconos - icono / 2 - 2, u.angulo);
          const giro = ((u.angulo - u.medio! + 540) % 360) - 180;
          const d = Math.abs(giro) < 0.5
            ? `M ${ax} ${ay} L ${dx} ${dy}`
            : `M ${ax} ${ay} L ${bx} ${by} A ${rMedio} ${rMedio} 0 0 ${giro > 0 ? 1 : 0} ${cx2} ${cy2} L ${dx} ${dy}`;
          return (
            <Path key={`l-${u.categoria.id}`} d={d} stroke={u.categoria.color} strokeWidth={1.5} fill="none"
              strokeLinejoin="round" strokeLinecap="round" />
          );
        })}
      </Svg>

      <TouchableOpacity
        style={[styles.centro, { left: c - centroAncho / 2, top: c - centroAncho / 2, width: centroAncho, height: centroAncho }]}
        onPress={onCentro} disabled={!onCentro} accessibilityRole="button"
        accessibilityLabel={etiquetaCentro || `Ingresos ${ing}, gastos ${gas}`}>
        <AppText style={{ color: colors.income, fontWeight: '800', fontSize: tamano }} numberOfLines={1}>
          {corta ? formatCompacto(ingresos) : ing}
        </AppText>
        <AppText style={{ color: colors.expense, fontWeight: '800', fontSize: tamano }} numberOfLines={1}>
          {corta ? formatCompacto(gastos) : gas}
        </AppText>
        <AppText variant="caption" color={colors.textSecondary}>ingresos · gastos</AppText>
      </TouchableOpacity>

      {ubicadas.map((u) => {
        const [x, y] = punto(radioIconos, u.angulo);
        const con = u.categoria.valor > 0;
        return (
          <TouchableOpacity key={u.categoria.id}
            style={[styles.icono, { left: x - icono / 2, top: y - icono / 2, width: icono, height: icono, borderRadius: icono / 2,
              backgroundColor: u.categoria.color, opacity: con ? 1 : 0.4 }]}
            onPress={() => onCategoria(u.categoria)} accessibilityRole="button"
            accessibilityLabel={`${u.categoria.nombre}${con ? `: ${formatCurrency(u.categoria.valor)}, ${porcentaje(u.fraccion)} de lo gastado` : ', sin gasto'}`}>
            <Icono nombre={u.categoria.icono} color={tintaSobre(u.categoria.color)} tamano={icono > 40 ? 22 : 20} />
            {con && (
              <View style={[styles.pct, { backgroundColor: colors.surface, borderColor: u.categoria.color }]} pointerEvents="none">
                <AppText style={[styles.pctTexto, { color: colors.text }]}>{porcentaje(u.fraccion)}</AppText>
              </View>
            )}
          </TouchableOpacity>
        );
      })}
      {mas && (() => {
        const [x, y] = punto(radioIconos, mas.angulo);
        return (
          <TouchableOpacity
            style={[styles.icono, { left: x - icono / 2, top: y - icono / 2, width: icono, height: icono,
              borderRadius: icono / 2, backgroundColor: colors.surfaceLight }]}
            onPress={onMas} accessibilityRole="button"
            accessibilityLabel={`Ver las ${mas.cuantas} categorías que no caben`}>
            <Icono nombre="more" color={colors.text} tamano={22} />
          </TouchableOpacity>
        );
      })()}
    </View>
  );
};

const styles = StyleSheet.create({
  centro: { position: 'absolute', alignItems: 'center', justifyContent: 'center', borderRadius: 999 },
  icono: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  pct: { position: 'absolute', bottom: -10, minWidth: 34, paddingHorizontal: 3, borderRadius: 8, borderWidth: 1, alignItems: 'center' },
  pctTexto: { fontSize: 14, fontWeight: '800', lineHeight: 16 },
});

export default DonaDeCategorias;
