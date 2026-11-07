import React from 'react';
import { View, StyleSheet, TouchableOpacity, useWindowDimensions } from 'react-native';
import Svg, { Circle } from 'react-native-svg';
import Icono from '../atoms/Icono';
import { tintaSobre } from '../../utils/tinta';
import { useTheme } from '../../contexts/ThemeContext';
import { formatCurrency } from '../../utils/format';

export interface CategoriaEnDona {
  id: number;
  nombre: string;
  icono?: string;
  color: string;
  /** Lo gastado de verdad: el tamano de su porcion. */
  valor: number;
}

interface Props {
  categorias: CategoriaEnDona[];
  onMas?: () => void;
  centro: React.ReactNode;
  onCategoria: (c: CategoriaEnDona) => void;
  onCentro?: () => void;
  etiquetaCentro?: string;
}

/** Colores para las categorias que no traen uno propio. */
export const PALETA = ['#6C5CE7', '#0984E3', '#00CEC9', '#FDCB6E', '#636E72', '#00B894', '#E84393',
  '#2BB0A0', '#E17055', '#A29BFE', '#74B9FF', '#FAB1A0', '#55EFC4', '#FF7675'];

/* Hasta 16 caben alrededor; si hay mas, el ultimo lugar es "Mas" y abre el
   teclado con todas las categorias para elegir. */
const MAXIMO_ICONOS = 16;

/*
 * La pantalla principal de Monefy: la dona de lo gastado por categoria y las
 * categorias alrededor. Tocar una categoria abre el teclado para anotar en
 * ella. Las que aun no tienen gasto se ven apagadas, pero se pueden tocar igual.
 */
const DonaDeCategorias: React.FC<Props> = ({ categorias, centro, onCategoria, onCentro, etiquetaCentro, onMas }) => {
  const { colors } = useTheme();
  const { width } = useWindowDimensions();
  const S = Math.min(width - 32, 340);
  const sobran = categorias.length > MAXIMO_ICONOS;
  const visibles = sobran ? categorias.slice(0, MAXIMO_ICONOS - 1) : categorias;
  const lugares = visibles.length + (sobran ? 1 : 0);
  /* Con muchas categorias los circulos se achican para que no se monten. */
  const icono = lugares > 12 ? 40 : 46;
  const radioIconos = S / 2 - icono / 2;
  const dona = S - icono * 2 - 24;
  const grosor = Math.max(18, dona * 0.12);
  const r = dona / 2 - grosor / 2;
  const circ = 2 * Math.PI * r;

  const total = categorias.reduce((t, c) => t + Math.max(0, c.valor), 0);
  let acumulado = 0;

  return (
    <View style={{ width: S, height: S, alignSelf: 'center' }}>
      <View style={[StyleSheet.absoluteFill, { alignItems: 'center', justifyContent: 'center' }]}>
        <Svg width={dona} height={dona} style={{ transform: [{ rotate: '-90deg' }] }} accessible={false}>
          <Circle cx={dona / 2} cy={dona / 2} r={r} stroke={colors.border} strokeWidth={grosor} fill="none" />
          {total > 0 && categorias.filter((c) => c.valor > 0).map((c) => {
            const parte = (c.valor / total) * circ;
            const el = (
              <Circle key={c.id} cx={dona / 2} cy={dona / 2} r={r} stroke={c.color} strokeWidth={grosor} fill="none"
                strokeDasharray={`${parte} ${circ - parte}`} strokeDashoffset={-acumulado} />
            );
            acumulado += parte;
            return el;
          })}
        </Svg>
      </View>

      <TouchableOpacity
        style={[styles.centro, { left: (S - dona * 0.62) / 2, top: (S - dona * 0.62) / 2, width: dona * 0.62, height: dona * 0.62 }]}
        onPress={onCentro}
        disabled={!onCentro}
        accessibilityRole="button"
        accessibilityLabel={etiquetaCentro || 'Ver el presupuesto'}
      >
        {centro}
      </TouchableOpacity>

      {visibles.map((c, i) => {
        const angulo = (i / Math.max(lugares, 8)) * 2 * Math.PI - Math.PI / 2;
        const x = S / 2 + radioIconos * Math.cos(angulo) - icono / 2;
        const y = S / 2 + radioIconos * Math.sin(angulo) - icono / 2;
        return (
          <TouchableOpacity
            key={c.id}
            style={[styles.icono, { left: x, top: y, width: icono, height: icono, borderRadius: icono / 2,
              backgroundColor: c.color, opacity: c.valor > 0 ? 1 : 0.4 }]}
            onPress={() => onCategoria(c)}
            accessibilityRole="button"
            accessibilityLabel={`Anotar en ${c.nombre}${c.valor > 0 ? `, llevan ${formatCurrency(c.valor)}` : ''}`}
          >
            <Icono nombre={c.icono} color={tintaSobre(c.color)} tamano={22} />
          </TouchableOpacity>
        );
      })}
      {sobran && (() => {
        const angulo = ((lugares - 1) / lugares) * 2 * Math.PI - Math.PI / 2;
        return (
          <TouchableOpacity
            style={[styles.icono, { left: S / 2 + radioIconos * Math.cos(angulo) - icono / 2,
              top: S / 2 + radioIconos * Math.sin(angulo) - icono / 2, width: icono, height: icono,
              borderRadius: icono / 2, backgroundColor: colors.surfaceLight }]}
            onPress={onMas}
            accessibilityRole="button"
            accessibilityLabel={`Ver las ${categorias.length - visibles.length} categorías que no caben`}
          >
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
});

export default DonaDeCategorias;
