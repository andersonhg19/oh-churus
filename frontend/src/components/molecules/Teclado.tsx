import React, { useEffect, useRef } from 'react';
import { View, StyleSheet, TouchableOpacity, Platform } from 'react-native';
import AppText from '../atoms/Text';
import { useTheme } from '../../contexts/ThemeContext';
import { textoLegibleSobre } from '../../theme/contraste';
import { pulsar, Tecla } from '../../utils/calculadora';

/** Lo que hace una tecla del teclado fisico: una tecla de la calculadora, anotar, cerrar o nada. */
export type AccionFisica = Tecla | 'listo' | 'cerrar' | 'nada';

/**
 * Traduce una tecla del teclado del computador. Los montos son pesos enteros,
 * asi que "." y "," (que la gente escribe por costumbre como separador de
 * miles: "12.000") no hacen nada en vez de romper la cifra.
 */
export function accionDeTecla(key: string): AccionFisica | null {
  if (/^[0-9]$/.test(key)) return key as Tecla;
  switch (key) {
    case '+': return '+';
    case '-': case '−': return '-';
    case '*': case 'x': case 'X': case '×': return '×';
    case '/': case '÷': return '÷';
    case '=': return '=';
    case 'Backspace': return '⌫';
    case 'Delete': return 'C';
    case 'Enter': return 'listo';
    case 'Escape': return 'cerrar';
    case '.': case ',': return 'nada';
    default: return null;
  }
}

const escribiendoEnUnCampo = () => {
  const el: any = typeof document !== 'undefined' ? document.activeElement : null;
  if (!el) return false;
  const tag = String(el.tagName || '').toUpperCase();
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || !!el.isContentEditable;
};

/**
 * En la web (escritorio), el teclado fisico escribe en la calculadora:
 * digitos, + - * /, Retroceso, Enter (= anotar) y Escape (= cerrar). Si el
 * foco esta en un campo de texto (la nota, el buscador) no se toca nada salvo
 * Escape. En el celular no hace nada.
 */
export function useTecladoFisico(o: {
  activo: boolean;
  onTecla?: (t: Tecla) => void;
  onListo?: () => void;
  onCerrar?: () => void;
}) {
  const ref = useRef(o);
  ref.current = o;
  useEffect(() => {
    if (Platform.OS !== 'web' || typeof document === 'undefined' || !document.addEventListener) return undefined;
    const manejar = (e: KeyboardEvent) => {
      const actual = ref.current;
      if (!actual.activo || e.ctrlKey || e.metaKey || e.altKey) return;
      const accion = accionDeTecla(e.key);
      if (!accion) return;
      if (accion !== 'cerrar' && escribiendoEnUnCampo()) return;
      e.preventDefault?.();
      if (accion === 'nada') return;
      if (accion === 'cerrar') actual.onCerrar?.();
      else if (accion === 'listo') actual.onListo?.();
      else actual.onTecla?.(accion);
    };
    document.addEventListener('keydown', manejar);
    return () => document.removeEventListener('keydown', manejar);
  }, []);
}

interface Props {
  expresion: string;
  onCambio: (expresion: string) => void;
  onListo: () => void;
  textoListo?: string;
  listoDeshabilitado?: boolean;
}

const NOMBRES: Record<string, string> = {
  '+': 'más', '-': 'menos', '×': 'por', '÷': 'dividido', '⌫': 'borrar el último dígito',
  C: 'borrar todo', '=': 'calcular', '000': 'tres ceros',
};

const FILAS: Tecla[][] = [
  ['7', '8', '9', '÷'],
  ['4', '5', '6', '×'],
  ['1', '2', '3', '-'],
  ['000', '0', '⌫', '+'],
];

/*
 * El teclado propio, como el de Monefy. No hay TextInput detras, asi que el
 * teclado del celular no aparece; y las operaciones funcionan de verdad
 * (utils/calculadora). Las teclas de operar van en ambar para que no se
 * confundan con los digitos.
 */
const Teclado: React.FC<Props> = ({ expresion, onCambio, onListo, textoListo = 'Anotar', listoDeshabilitado }) => {
  const { colors } = useTheme();
  /* El texto de "Anotar" se calcula sobre su fondo: en el tema claro el ambar
     es oscuro y el texto casi negro no se leia. Deshabilitado no es "roto"
     (1,37:1 antes): fondo apagado con borde punteado y texto legible. */
  const tintaListo = listoDeshabilitado ? colors.textSecondary : textoLegibleSobre(colors.primary);
  const tecla = (t: Tecla) => {
    const op = ['+', '-', '×', '÷', '⌫'].includes(t);
    return (
      <TouchableOpacity
        key={t}
        style={[styles.tecla, { backgroundColor: colors.surface }]}
        onPress={() => onCambio(pulsar(expresion, t))}
        onLongPress={t === '⌫' ? () => onCambio(pulsar(expresion, 'C')) : undefined}
        accessibilityRole="button"
        accessibilityLabel={NOMBRES[t] || t}
      >
        <AppText variant="title" style={{ fontSize: 26, color: op ? colors.primary : colors.text }}>
          {t === '-' ? '−' : t}
        </AppText>
      </TouchableOpacity>
    );
  };
  return (
    <View style={[styles.cuadro, { backgroundColor: colors.border }]}>
      {FILAS.map((f, i) => <View key={i} style={styles.fila}>{f.map(tecla)}</View>)}
      <View style={styles.fila}>
        <TouchableOpacity
          style={[styles.tecla, { backgroundColor: colors.surface }]}
          onPress={() => onCambio(pulsar(expresion, '='))}
          accessibilityRole="button"
          accessibilityLabel="calcular"
        >
          <AppText variant="title" style={{ fontSize: 26, color: colors.primary }}>=</AppText>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.listo, listoDeshabilitado
            ? { backgroundColor: colors.surfaceLight, borderWidth: 1, borderStyle: 'dashed', borderColor: colors.textMuted }
            : { backgroundColor: colors.primary }]}
          onPress={onListo}
          disabled={listoDeshabilitado}
          accessibilityRole="button"
          accessibilityLabel={textoListo}
          accessibilityState={{ disabled: !!listoDeshabilitado }}
        >
          <AppText variant="subtitle" style={{ color: tintaListo, fontWeight: listoDeshabilitado ? '600' : '800' }}>{textoListo}</AppText>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  cuadro: { gap: 1 },
  fila: { flexDirection: 'row', gap: 1 },
  tecla: { flex: 1, minHeight: 58, alignItems: 'center', justifyContent: 'center' },
  listo: { flex: 3, minHeight: 58, alignItems: 'center', justifyContent: 'center' },
});

export default Teclado;
