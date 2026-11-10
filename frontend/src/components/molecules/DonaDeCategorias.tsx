import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  View, StyleSheet, TouchableOpacity, Pressable, useWindowDimensions, Animated, Easing,
  PanResponder, AccessibilityInfo, Modal, ScrollView,
} from 'react-native';
import Svg, { Path, Circle, Polyline } from 'react-native-svg';
import AppText from '../atoms/Text';
import Icono from '../atoms/Icono';
import CirculoCategoria from '../atoms/CirculoCategoria';
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
  /** Lo presupuestado para la categoria en el periodo. */
  presupuesto?: number;
}

/** Colores para las categorias que no traen uno propio. */
export const PALETA = ['#6C5CE7', '#0984E3', '#00CEC9', '#FDCB6E', '#636E72', '#00B894', '#E84393',
  '#2BB0A0', '#E17055', '#A29BFE', '#74B9FF', '#FAB1A0', '#55EFC4', '#FF7675'];

export const ID_OTROS = -1;
export const COLOR_OTROS = '#9AA0A6';
/** Porciones con nombre como maximo, contando "Otros". */
export const MAX_PORCIONES = 8;
/** Lo que pesa menos que esto del gasto total se junta en "Otros". */
export const UMBRAL_OTROS = 0.03;
/** Distancia minima, en px, entre dos etiquetas del mismo lado: cada una es
 *  una zona tactil de 44px de alto, asi que no pueden quedar mas juntas. */
export const SEPARACION = 44;
const MARGEN = 22;
const HUECO = 1.2;
const BARRIDO_MIN = 4;
const ANCHO_ETIQUETA = 26 + 4 + 32;

export interface Porcion extends CategoriaEnDona {
  fraccion: number;
  /** Solo en "Otros": las categorias que se juntaron en ella. */
  agrupadas?: CategoriaEnDona[];
}

/**
 * Las porciones que se dibujan: solo lo que tiene gasto, de la mas grande a la
 * mas chica; lo que pesa menos del 3% y lo que no cabe en 8 va a "Otros".
 */
export function agruparPorciones(categorias: CategoriaEnDona[]): { porciones: Porcion[]; total: number } {
  const conGasto = categorias.filter((c) => c.valor > 0).sort((a, b) => b.valor - a.valor);
  const total = conGasto.reduce((t, c) => t + c.valor, 0);
  if (total <= 0) return { porciones: [], total: 0 };
  let grandes = conGasto.filter((c) => c.valor / total >= UMBRAL_OTROS);
  let chicas = conGasto.filter((c) => c.valor / total < UMBRAL_OTROS);
  /* Un "Otros" con una sola categoria no ayuda: mejor su nombre. */
  if (chicas.length === 1 && grandes.length < MAX_PORCIONES) { grandes = [...grandes, chicas[0]]; chicas = []; }
  const cupo = chicas.length > 0 ? MAX_PORCIONES - 1 : MAX_PORCIONES;
  if (grandes.length > cupo) {
    chicas = [...grandes.slice(MAX_PORCIONES - 1), ...chicas];
    grandes = grandes.slice(0, MAX_PORCIONES - 1);
  }
  const porciones: Porcion[] = grandes.map((c) => ({ ...c, fraccion: c.valor / total }));
  if (chicas.length > 0) {
    const valor = chicas.reduce((t, c) => t + c.valor, 0);
    porciones.push({ id: ID_OTROS, nombre: 'Otros', icono: 'more', color: COLOR_OTROS, valor,
      fraccion: valor / total, agrupadas: chicas });
  }
  return { porciones, total };
}

/**
 * Reparte las etiquetas de un lado para que no se monten: cada una al menos
 * SEPARACION por debajo de la anterior, sin salirse de [MARGEN, alto - MARGEN].
 * Recibe las alturas ya ordenadas de arriba a abajo.
 */
export function repartirEtiquetas(ys: number[], alto: number, separacion = SEPARACION, margen = MARGEN): number[] {
  const y = [...ys];
  for (let i = 0; i < y.length; i++) {
    y[i] = i === 0 ? Math.max(y[i], margen) : Math.max(y[i], y[i - 1] + separacion);
  }
  for (let i = y.length - 1; i >= 0; i--) {
    y[i] = i === y.length - 1 ? Math.min(y[i], alto - margen) : Math.min(y[i], y[i + 1] - separacion);
  }
  return y.map((v) => Math.min(alto - margen, Math.max(margen, v)));
}

const rad = (g: number) => (g * Math.PI) / 180;

/** Ancho medio de un caracter, en "em", de las cifras en negrita. */
const ANCHO_CIFRA = 0.62;
/** Ancho medio de un caracter de texto normal (nombres), en "em". */
const ANCHO_LETRA = 0.55;

/** El tamano de letra con el que `texto` cabe en `ancho`, sin pasar de `max`. */
export const tamanoQueCabe = (texto: string, ancho: number, max: number, em = ANCHO_LETRA) =>
  Math.min(max, ancho / (Math.max(1, texto.length) * em));

/**
 * La cifra del centro NUNCA se corta con "…": si la cifra completa no cabe a
 * un tamano legible (min), se usa la corta ($2 M); y la letra se ajusta al
 * ancho que hay dentro del anillo. adjustsFontSizeToFit no existe en la web,
 * por eso se calcula.
 */
export function cifraQueCabe(monto: number, ancho: number, max = 28, min = 16): { texto: string; tamano: number } {
  let texto = formatCurrency(monto);
  if (tamanoQueCabe(texto, ancho, max, ANCHO_CIFRA) < min) texto = formatCompacto(monto);
  return { texto, tamano: Math.max(11, Math.floor(tamanoQueCabe(texto, ancho, max, ANCHO_CIFRA))) };
}

/**
 * El nombre de la etiqueta segun el espacio: completo si cabe, si no la
 * primera palabra ("Comidas fuera" -> "Comidas"), y si ni eso, nada.
 */
export function nombreQueCabe(nombre: string, espacio: number, tamano = 12): string | null {
  const cabe = (t: string) => t.length * tamano * ANCHO_LETRA <= espacio;
  if (espacio < 36) return null;
  if (cabe(nombre)) return nombre;
  const primera = nombre.split(/\s+/)[0];
  return primera && primera.length >= 3 && cabe(primera) ? primera : null;
}

export interface Arco { porcion: Porcion; a0: number; a1: number; medio: number }
export interface Etiqueta { porcion: Porcion; derecha: boolean; y: number; medio: number }

/** Angulos (grados, desde las 12 y en el sentido del reloj) y etiquetas ya repartidas. */
export function geometria(porciones: Porcion[], altoBase: number) {
  const n = porciones.length;
  let barridos = porciones.map((p) => p.fraccion * 360);
  if (n > 1) {
    const minimo = BARRIDO_MIN + HUECO;
    const ajustados = barridos.map((b) => Math.max(b, minimo));
    const exceso = ajustados.reduce((t, b) => t + b, 0) - 360;
    const holgura = ajustados.reduce((t, b) => t + (b - minimo), 0);
    barridos = ajustados.map((b) => (holgura > 0 ? b - (exceso * (b - minimo)) / holgura : 360 / n));
  }
  let acumulado = -90;
  const arcos: Arco[] = porciones.map((p, i) => {
    const a0 = acumulado;
    acumulado += barridos[i];
    return { porcion: p, a0, a1: acumulado, medio: (a0 + acumulado) / 2 };
  });

  /* Cada etiqueta va del lado donde cae su porcion; las casi verticales (±8°)
     van al lado que tenga menos. */
  const casiVertical = Math.sin(rad(8));
  const derecha: Arco[] = [];
  const izquierda: Arco[] = [];
  const dudosas: Arco[] = [];
  arcos.forEach((a) => {
    const c = Math.cos(rad(a.medio));
    if (Math.abs(c) < casiVertical) dudosas.push(a);
    else (c >= 0 ? derecha : izquierda).push(a);
  });
  dudosas.forEach((a) => (derecha.length <= izquierda.length ? derecha : izquierda).push(a));

  const alto = Math.max(altoBase, Math.max(derecha.length, izquierda.length) * SEPARACION);
  return { arcos, alto, lados: { derecha, izquierda } };
}

function sector(cx: number, cy: number, ro: number, ri: number, a0: number, a1: number) {
  const grande = a1 - a0 > 180 ? 1 : 0;
  const p = (r: number, a: number) => `${(cx + r * Math.cos(rad(a))).toFixed(2)} ${(cy + r * Math.sin(rad(a))).toFixed(2)}`;
  return `M ${p(ro, a0)} A ${ro} ${ro} 0 ${grande} 1 ${p(ro, a1)} L ${p(ri, a1)} A ${ri} ${ri} 0 ${grande} 0 ${p(ri, a0)} Z`;
}

const porcentaje = (f: number) => {
  const v = Math.round(f * 100);
  return v < 1 ? '<1%' : `${v}%`;
};

interface Props {
  categorias: CategoriaEnDona[];
  libre: number;
  gastado: number;
  presupuestado: number;
  onCentro?: () => void;
  onAnotar: (c: CategoriaEnDona) => void;
  onVerMovimientos: (c: CategoriaEnDona) => void;
  onVerCategoria: (c: CategoriaEnDona) => void;
  /** Deslizar a la derecha: periodo anterior. */
  onAnterior?: () => void;
  /** Deslizar a la izquierda: periodo siguiente. */
  onSiguiente?: () => void;
  /** Cuando cambia (otro periodo, otro alcance), la dona se vuelve a dibujar animada. */
  claveAnimacion?: string;
}

/*
 * La dona de Inicio: en que se fue la plata. Solo se dibuja lo gastado, cada
 * porcion con una linea que lleva a su icono y su porcentaje, al lado de donde
 * cae en el circulo. En el centro, lo libre para gastar; al tocar una porcion,
 * el detalle de esa categoria y el atajo para anotar en ella.
 */
const DonaDeCategorias: React.FC<Props> = ({
  categorias, libre, gastado, presupuestado, onCentro, onAnotar, onVerMovimientos, onVerCategoria,
  onAnterior, onSiguiente, claveAnimacion,
}) => {
  const { colors } = useTheme();
  const { width } = useWindowDimensions();
  const W = Math.max(240, width - 32);
  const S = Math.min(W, 360);
  const Ro = 0.27 * S;
  const T = 0.07 * S;
  const Ri = Ro - T;
  const cx = W / 2;

  const { porciones, total } = useMemo(() => agruparPorciones(categorias), [categorias]);
  const altoBase = Math.round(0.78 * S);
  const { arcos, alto: H, lados } = useMemo(() => geometria(porciones, altoBase), [porciones, altoBase]);
  const cy = H / 2;

  const etiquetas: Etiqueta[] = useMemo(() => {
    const ubicar = (lista: Arco[], der: boolean) => {
      const ordenadas = lista
        .map((a) => ({ a, y: cy + (Ro + 12) * Math.sin(rad(a.medio)) }))
        .sort((x, z) => x.y - z.y);
      const ys = repartirEtiquetas(ordenadas.map((o) => o.y), H);
      return ordenadas.map((o, i) => ({ porcion: o.a.porcion, derecha: der, y: ys[i], medio: o.a.medio }));
    };
    return [...ubicar(lados.derecha, true), ...ubicar(lados.izquierda, false)];
  }, [lados, cy, Ro, H]);

  const [elegidaId, setElegidaId] = useState<number | null>(null);
  const [verOtros, setVerOtros] = useState(false);
  const elegida = porciones.find((p) => p.id === elegidaId && p.id !== ID_OTROS) || null;

  /* Animacion: las porciones se abren en ~450ms y las etiquetas aparecen despues. */
  const avance = useRef(new Animated.Value(0)).current;
  const opacidad = useRef(new Animated.Value(0)).current;
  const [progreso, setProgreso] = useState(0);
  useEffect(() => {
    let vivo = true;
    const id = avance.addListener(({ value }) => { if (vivo) setProgreso(value); });
    avance.setValue(0);
    opacidad.setValue(0);
    setElegidaId(null);
    const anim = Animated.parallel([
      Animated.timing(avance, { toValue: 1, duration: 450, easing: Easing.out(Easing.cubic), useNativeDriver: false }),
      Animated.timing(opacidad, { toValue: 1, duration: 250, delay: 150, useNativeDriver: false }),
    ]);
    anim.start();
    try {
      Promise.resolve(AccessibilityInfo.isReduceMotionEnabled?.())
        .then((reducir) => {
          if (reducir && vivo) { anim.stop(); avance.setValue(1); opacidad.setValue(1); }
        })
        .catch(() => undefined);
    } catch { /* sin AccessibilityInfo se anima igual */ }
    return () => { vivo = false; anim.stop(); avance.removeListener(id); };
  }, [claveAnimacion, avance, opacidad]);

  /* Deslizar de lado cambia de periodo. */
  const anteriorRef = useRef(onAnterior);
  const siguienteRef = useRef(onSiguiente);
  anteriorRef.current = onAnterior;
  siguienteRef.current = onSiguiente;
  const deslizar = useMemo(() => PanResponder.create({
    onMoveShouldSetPanResponderCapture: (_e, g) => Math.abs(g.dx) > 20 && Math.abs(g.dx) > Math.abs(g.dy) * 1.5,
    onPanResponderRelease: (_e, g) => {
      if (g.dx > 60) anteriorRef.current?.();
      else if (g.dx < -60) siguienteRef.current?.();
    },
  }), []);

  const tocar = (p: Porcion) => {
    if (p.id === ID_OTROS) { setElegidaId(null); setVerOtros(true); return; }
    setElegidaId((actual) => (actual === p.id ? null : p.id));
  };
  const mantener = (p: Porcion) => {
    if (p.id === ID_OTROS) { setVerOtros(true); return; }
    onVerMovimientos(p);
  };
  const etiquetaAccesible = (p: Porcion) =>
    `${p.nombre}: ${formatCurrency(p.valor)}, ${porcentaje(p.fraccion)} del gasto`;

  const otros = porciones.find((p) => p.id === ID_OTROS);
  const libreTexto = formatCurrency(libre);
  const diametroCentro = Math.max(0, 2 * Ri - 8);
  /* Lo que cabe a lo ancho en el centro (la cuerda a la altura de cada linea). */
  const radioCentro = diametroCentro / 2;
  const cuerda = (dy: number) => Math.max(0, 2 * Math.sqrt(Math.max(0, radioCentro * radioCentro - dy * dy)) - 12);
  const cifra = cifraQueCabe(libre, cuerda(0));
  const lineaGastado = `Gastado ${formatCompacto(gastado)}`;
  const lineaDe = `de ${formatCompacto(presupuestado)}`;
  const tamGastado = Math.max(9, Math.min(12, tamanoQueCabe(lineaGastado, cuerda(cifra.tamano * 0.5 + 10), 12)));
  const tamDe = Math.max(9, Math.min(12, tamanoQueCabe(lineaDe, cuerda(cifra.tamano * 0.5 + 26), 12)));

  /* Que nombre lleva cada etiqueta; las que no tienen sitio (el celular) van
     en la leyenda de abajo, para no tener que adivinar que es "🏠 38%". */
  const nombres: Record<number, string | null> = {};
  etiquetas.forEach((e) => {
    const x2 = e.derecha ? cx + Ro + 26 : cx - Ro - 26;
    const espacio = e.derecha ? W - (x2 + 2 + ANCHO_ETIQUETA) - 4 : x2 - 2 - ANCHO_ETIQUETA - 4;
    nombres[e.porcion.id] = nombreQueCabe(e.porcion.nombre, Math.min(espacio, 110));
  });
  const sinNombre = porciones.filter((p) => !nombres[p.id]);

  return (
    <View style={{ alignSelf: 'center', width: W }}>
    <View style={{ width: W, height: H, alignSelf: 'center' }} {...deslizar.panHandlers}>
      {/* Tocar fuera de la dona suelta la categoria elegida. */}
      <Pressable style={StyleSheet.absoluteFill} onPress={() => setElegidaId(null)} accessible={false} />

      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        <Svg width={W} height={H} accessible={false}>
          {total <= 0 && (
            <Circle cx={cx} cy={cy} r={Ro - T / 2} stroke={colors.border} strokeWidth={T} fill="none" />
          )}
          {arcos.map((a) => {
            const hueco = arcos.length > 1 ? HUECO / 2 : 0;
            const desde = -90 + (a.a0 + 90) * progreso + hueco;
            const hasta = Math.min(-90 + (a.a1 + 90) * progreso - hueco, desde + 359.99);
            if (hasta - desde <= 0.05) return null;
            const sale = elegida?.id === a.porcion.id ? 6 : 0;
            const dx = sale * Math.cos(rad(a.medio));
            const dy = sale * Math.sin(rad(a.medio));
            return (
              <Path key={a.porcion.id} d={sector(cx + dx, cy + dy, Ro, Ri, desde, hasta)} fill={a.porcion.color}
                opacity={elegida && elegida.id !== a.porcion.id ? 0.35 : 1} />
            );
          })}
        </Svg>
      </View>

      {/* Las lineas guia y las etiquetas: aparecen cuando la dona ya se abrio. */}
      <Animated.View style={[StyleSheet.absoluteFill, { opacity: opacidad }]} pointerEvents="box-none">
        <View style={StyleSheet.absoluteFill} pointerEvents="none">
          <Svg width={W} height={H} accessible={false}>
            {etiquetas.map((e) => {
              const c = Math.cos(rad(e.medio));
              const s = Math.sin(rad(e.medio));
              const p0 = [cx + (Ro + 2) * c, cy + (Ro + 2) * s];
              const p1 = [cx + (Ro + 12) * c, e.y];
              const p2 = [e.derecha ? cx + Ro + 26 : cx - Ro - 26, e.y];
              const apagada = elegida && elegida.id !== e.porcion.id;
              return (
                <React.Fragment key={e.porcion.id}>
                  <Circle cx={p0[0]} cy={p0[1]} r={1.5} fill={e.porcion.color} opacity={apagada ? 0.35 : 0.9} />
                  <Polyline points={`${p0.join(',')} ${p1.join(',')} ${p2.join(',')}`} stroke={e.porcion.color}
                    strokeWidth={1} fill="none" opacity={apagada ? 0.25 : 0.7} />
                </React.Fragment>
              );
            })}
          </Svg>
        </View>

        {/* Zonas para tocar cada porcion (sobre el anillo). */}
        {arcos.map((a) => {
          const r = Ro - T / 2;
          return (
            <Pressable key={`zona-${a.porcion.id}`} accessible={false}
              testID={`porcion-${a.porcion.id}`}
              style={[styles.zona, { left: cx + r * Math.cos(rad(a.medio)) - 22, top: cy + r * Math.sin(rad(a.medio)) - 22 }]}
              onPress={() => tocar(a.porcion)} onLongPress={() => mantener(a.porcion)} />
          );
        })}

        {etiquetas.map((e) => {
          const p = e.porcion;
          const x2 = e.derecha ? cx + Ro + 26 : cx - Ro - 26;
          const nombreVisible = nombres[p.id];
          const apagada = !!elegida && elegida.id !== p.id;
          return (
            <TouchableOpacity
              key={`etiqueta-${p.id}`}
              style={[styles.etiqueta, { top: e.y - 22, flexDirection: e.derecha ? 'row' : 'row-reverse',
                opacity: apagada ? 0.35 : 1 },
                e.derecha ? { left: x2 + 2 } : { right: W - (x2 - 2) }]}
              hitSlop={{ left: 6, right: 6 }}
              onPress={() => tocar(p)}
              onLongPress={() => mantener(p)}
              accessibilityRole="button"
              accessibilityLabel={etiquetaAccesible(p)}
              accessibilityHint={p.id === ID_OTROS ? 'Ver las categorías agrupadas' : 'Ver el detalle. Mantén presionado para ver sus movimientos'}
              accessibilityState={{ selected: elegida?.id === p.id }}
              accessibilityActions={[{ name: 'longpress', label: 'Ver sus movimientos' }]}
              onAccessibilityAction={(ev) => { if (ev.nativeEvent.actionName === 'longpress') mantener(p); }}
            >
              <View style={[styles.circulo, { backgroundColor: p.color }]}>
                <Icono nombre={p.icono} color={tintaSobre(p.color)} tamano={15} />
              </View>
              <AppText variant="caption" style={[styles.pct, { color: colors.text }]}>{porcentaje(p.fraccion)}</AppText>
              {!!nombreVisible && (
                <AppText variant="caption" color={colors.textSecondary} numberOfLines={1}>{nombreVisible}</AppText>
              )}
            </TouchableOpacity>
          );
        })}
      </Animated.View>

      {/* El centro: lo libre para gastar, o el detalle de la categoria elegida. */}
      {elegida ? (
        <Pressable
          style={[styles.centro, { left: cx - diametroCentro / 2, top: cy - diametroCentro / 2, width: diametroCentro, height: diametroCentro }]}
          onPress={() => setElegidaId(null)}
          accessible={false}
        >
          <View style={styles.filaCentro}>
            <CirculoCategoria icono={elegida.icono} color={elegida.color} tamano={20} />
            <AppText variant="caption" style={{ fontWeight: '700', color: colors.text, flexShrink: 1 }} numberOfLines={1}>{elegida.nombre}</AppText>
          </View>
          <AppText variant="caption" style={{ fontWeight: '800', color: colors.text }} numberOfLines={1} adjustsFontSizeToFit>
            {formatCompacto(elegida.valor)} · {porcentaje(elegida.fraccion)}
          </AppText>
          <AppText variant="caption" color={colors.textMuted} numberOfLines={1} adjustsFontSizeToFit>
            {(elegida.presupuesto || 0) > 0 ? `de ${formatCompacto(elegida.presupuesto || 0)} presupuestado` : 'sin presupuesto'}
          </AppText>
          <View style={styles.acciones}>
            <TouchableOpacity style={[styles.pildora, { borderColor: colors.expense }]} hitSlop={{ top: 6, bottom: 6, left: 4, right: 4 }}
              onPress={() => onAnotar(elegida)} accessibilityRole="button" accessibilityLabel={`Anotar un gasto en ${elegida.nombre}`}>
              <AppText variant="caption" style={{ color: colors.expense, fontWeight: '800' }}>＋ Anotar</AppText>
            </TouchableOpacity>
            <TouchableOpacity style={styles.ver} hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }} onPress={() => onVerCategoria(elegida)}
              accessibilityRole="link" accessibilityLabel={`Ver el presupuesto de ${elegida.nombre}`}>
              <AppText variant="caption" style={{ color: colors.primary, fontWeight: '700' }}>Ver</AppText>
            </TouchableOpacity>
          </View>
        </Pressable>
      ) : (
        <TouchableOpacity
          style={[styles.centro, { left: cx - diametroCentro / 2, top: cy - diametroCentro / 2, width: diametroCentro, height: diametroCentro }]}
          onPress={onCentro}
          disabled={!onCentro}
          accessibilityRole="button"
          accessibilityLabel={`Libre para gastar ${libreTexto}. Gastado ${formatCurrency(gastado)} de ${formatCurrency(presupuestado)}. Ver el presupuesto`}
        >
          <AppText variant="caption" color={colors.textSecondary}>Libre para gastar</AppText>
          <AppText style={{ fontSize: cifra.tamano, lineHeight: Math.round(cifra.tamano * 1.2), fontWeight: '800',
            color: libre >= 0 ? colors.income : colors.expense }} numberOfLines={1}>
            {cifra.texto}
          </AppText>
          <AppText variant="caption" color={colors.textSecondary} numberOfLines={1} style={{ fontSize: tamGastado }}>
            {lineaGastado}
          </AppText>
          <AppText variant="caption" color={colors.textSecondary} numberOfLines={1} style={{ fontSize: tamDe }}>
            {lineaDe}
          </AppText>
          {total <= 0 && (
            <AppText variant="caption" color={colors.textSecondary} numberOfLines={2} style={styles.pista}>
              Anota tu primer gasto con − Gasto
            </AppText>
          )}
        </TouchableOpacity>
      )}

      <Modal visible={verOtros && !!otros} transparent animationType="slide" onRequestClose={() => setVerOtros(false)}>
        <TouchableOpacity style={styles.fondo} activeOpacity={1} onPress={() => setVerOtros(false)}
          accessibilityRole="button" accessibilityLabel="Cerrar">
          {otros && (
            <View style={[styles.hoja, { backgroundColor: colors.surface }]}>
              <AppText variant="subtitle">Otros · {formatCurrency(otros.valor)}</AppText>
              <ScrollView style={{ maxHeight: 360 }}>
                {(otros.agrupadas || []).map((c) => (
                  <TouchableOpacity key={c.id} style={[styles.fila, { borderColor: colors.border }]}
                    onPress={() => { setVerOtros(false); onVerMovimientos(c); }}
                    accessibilityRole="button"
                    accessibilityLabel={`${c.nombre}: ${formatCurrency(c.valor)}, ${porcentaje(c.valor / total)} del gasto. Ver sus movimientos`}>
                    <CirculoCategoria icono={c.icono} color={c.color} tamano={32} />
                    <AppText variant="body" style={{ flex: 1, color: colors.text }} numberOfLines={1}>{c.nombre}</AppText>
                    <AppText variant="body" style={{ fontWeight: '700', color: colors.text }}>{formatCurrency(c.valor)}</AppText>
                    <AppText variant="caption" color={colors.textSecondary} style={{ width: 40, textAlign: 'right' }}>
                      {porcentaje(c.valor / total)}
                    </AppText>
                  </TouchableOpacity>
                ))}
              </ScrollView>
            </View>
          )}
        </TouchableOpacity>
      </Modal>
    </View>

      {/* En el celular las etiquetas no tienen sitio para el nombre: aqui
          van todas con su nombre. Es un atajo visual; el lector de pantalla
          ya tiene cada porcion con su nombre en las etiquetas. */}
      {sinNombre.length > 0 && (
        <View style={styles.leyenda} accessible={false} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
          {porciones.map((p) => (
            <TouchableOpacity key={`ley-${p.id}`} testID={`leyenda-${p.id}`} accessible={false}
              style={[styles.itemLeyenda, { backgroundColor: elegida?.id === p.id ? colors.surfaceLight : 'transparent',
                opacity: elegida && elegida.id !== p.id ? 0.5 : 1 }]}
              onPress={() => tocar(p)} onLongPress={() => mantener(p)}>
              <View style={[styles.puntoLeyenda, { backgroundColor: p.color }]} />
              <AppText variant="caption" style={{ color: colors.text, fontWeight: '600' }} numberOfLines={1}>{p.nombre}</AppText>
              <AppText variant="caption" color={colors.textSecondary}>{porcentaje(p.fraccion)}</AppText>
            </TouchableOpacity>
          ))}
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  centro: { position: 'absolute', alignItems: 'center', justifyContent: 'center', borderRadius: 999, gap: 1, paddingHorizontal: 6 },
  zona: { position: 'absolute', width: 44, height: 44, borderRadius: 22 },
  etiqueta: { position: 'absolute', height: 44, minWidth: 44, alignItems: 'center', gap: 4 },
  circulo: { width: 26, height: 26, borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  pct: { fontWeight: '800', fontVariant: ['tabular-nums'] },
  acciones: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 2 },
  pildora: { borderWidth: 1, borderRadius: 999, paddingHorizontal: 10, minHeight: 32, justifyContent: 'center' },
  ver: { minHeight: 32, paddingHorizontal: 6, justifyContent: 'center' },
  leyenda: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: 4, marginTop: 4 },
  itemLeyenda: { flexDirection: 'row', alignItems: 'center', gap: 6, minHeight: 44, paddingHorizontal: 10, borderRadius: 22, maxWidth: '100%' },
  puntoLeyenda: { width: 10, height: 10, borderRadius: 5 },
  pista: { textAlign: 'center', marginTop: 2 },
  filaCentro: { flexDirection: 'row', alignItems: 'center', gap: 4, maxWidth: '100%' },
  fondo: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' },
  hoja: { padding: 20, gap: 10, borderTopLeftRadius: 24, borderTopRightRadius: 24 },
  fila: { flexDirection: 'row', alignItems: 'center', gap: 10, minHeight: 52, borderBottomWidth: StyleSheet.hairlineWidth },
});

export default DonaDeCategorias;
