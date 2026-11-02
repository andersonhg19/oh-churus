import React from 'react';
import { View, StyleSheet, TouchableOpacity } from 'react-native';
import AppText from '../atoms/Text';
import { useTheme } from '../../contexts/ThemeContext';
import { pulsar, Tecla } from '../../utils/calculadora';

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
          style={[styles.listo, { backgroundColor: listoDeshabilitado ? colors.border : colors.primary }]}
          onPress={onListo}
          disabled={listoDeshabilitado}
          accessibilityRole="button"
          accessibilityLabel={textoListo}
          accessibilityState={{ disabled: !!listoDeshabilitado }}
        >
          <AppText variant="subtitle" style={{ color: '#1A1405', fontWeight: '800' }}>{textoListo}</AppText>
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
