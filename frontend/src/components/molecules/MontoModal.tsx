import React, { useEffect, useState } from 'react';
import { View, StyleSheet, Modal, TouchableOpacity } from 'react-native';
import AppText from '../atoms/Text';
import Icono from '../atoms/Icono';
import Teclado from './Teclado';
import { useTheme } from '../../contexts/ThemeContext';
import { evaluar, mostrar, tieneOperacion, desdeMonto } from '../../utils/calculadora';
import { formatCurrency } from '../../utils/format';

interface Props {
  visible: boolean;
  titulo: string;
  detalle?: string;
  inicial?: number;
  textoListo?: string;
  onListo: (monto: number) => void;
  onCerrar: () => void;
}

/*
 * Pedir un monto con el teclado de la app: confirmar un pago por otro valor,
 * fijar el presupuesto de una categoria. El mismo teclado en todas partes.
 */
const MontoModal: React.FC<Props> = ({ visible, titulo, detalle, inicial, textoListo = 'Listo', onListo, onCerrar }) => {
  const { colors } = useTheme();
  const [expresion, setExpresion] = useState(desdeMonto(inicial));
  useEffect(() => { if (visible) setExpresion(desdeMonto(inicial)); }, [visible, inicial]);
  const monto = evaluar(expresion);

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onCerrar}>
      <View style={styles.fondo}>
        <View style={[styles.hoja, { backgroundColor: colors.background }]}>
          <View style={[styles.cabeza, { backgroundColor: colors.surface }]}>
            <View style={{ flex: 1 }}>
              <AppText variant="body" style={{ fontWeight: '700' }}>{titulo}</AppText>
              {!!detalle && <AppText variant="caption" color={colors.textSecondary}>{detalle}</AppText>}
            </View>
            <TouchableOpacity onPress={onCerrar} style={styles.cerrar} accessibilityRole="button" accessibilityLabel="Cerrar sin guardar">
              <Icono nombre="close" color={colors.text} />
            </TouchableOpacity>
          </View>
          <View style={styles.pantalla} accessible accessibilityLabel={`Monto ${formatCurrency(monto)}`}>
            <AppText variant="title" style={{ textAlign: 'right' }}>{mostrar(expresion)}</AppText>
            {tieneOperacion(expresion) && (
              <AppText variant="body" color={colors.textSecondary} style={{ textAlign: 'right' }}>= {formatCurrency(monto)}</AppText>
            )}
          </View>
          <Teclado
            expresion={expresion}
            onCambio={setExpresion}
            onListo={() => monto > 0 && onListo(monto)}
            textoListo={textoListo}
            listoDeshabilitado={monto <= 0}
          />
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  fondo: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' },
  hoja: { borderTopLeftRadius: 24, borderTopRightRadius: 24, overflow: 'hidden', maxWidth: 520, width: '100%', alignSelf: 'center' },
  cabeza: { flexDirection: 'row', alignItems: 'center', padding: 16 },
  cerrar: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  pantalla: { padding: 16, minHeight: 90, justifyContent: 'center' },
});

export default MontoModal;
