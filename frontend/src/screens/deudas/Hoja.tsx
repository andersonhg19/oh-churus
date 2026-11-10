import React from 'react';
import { View, StyleSheet, TouchableOpacity, Modal } from 'react-native';
import AppText from '../../components/atoms/Text';
import Button from '../../components/atoms/Button';
import { useTheme } from '../../contexts/ThemeContext';

interface Props {
  visible: boolean;
  titulo: string;
  onCerrar: () => void;
  children?: React.ReactNode;
  /** Con boton principal: confirmar. Sin el, la hoja solo muestra su contenido. */
  textoConfirmar?: string;
  onConfirmar?: () => void;
  ocupado?: boolean;
  deshabilitado?: boolean;
}

/*
 * La hoja que sube desde abajo para confirmar algo. Sustituye a window.confirm
 * y Alert, que en la web salen con el aspecto del navegador y no dejan
 * explicar en dos lineas que pagar la tarjeta NO es un gasto nuevo.
 */
const Hoja: React.FC<Props> = ({ visible, titulo, onCerrar, children, textoConfirmar, onConfirmar, ocupado, deshabilitado }) => {
  const { colors } = useTheme();
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCerrar}>
      <View style={styles.fondo}>
        <TouchableOpacity style={StyleSheet.absoluteFill} activeOpacity={1} onPress={onCerrar}
          accessibilityRole="button" accessibilityLabel="Cerrar sin hacer nada" />
        <View style={[styles.hoja, { backgroundColor: colors.surface }]} accessibilityViewIsModal>
          <AppText variant="subtitle">{titulo}</AppText>
          {children}
          <View style={styles.botones}>
            <Button title="Cancelar" variant="outline" onPress={onCerrar} style={styles.boton} />
            {!!textoConfirmar && onConfirmar && (
              <Button title={textoConfirmar} onPress={onConfirmar} loading={ocupado} disabled={deshabilitado} style={styles.boton} />
            )}
          </View>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  fondo: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' },
  hoja: { padding: 20, paddingBottom: 28, borderTopLeftRadius: 24, borderTopRightRadius: 24, gap: 12, width: '100%', maxWidth: 560, alignSelf: 'center' },
  botones: { flexDirection: 'row', gap: 12, marginTop: 4 },
  boton: { flex: 1, minHeight: 48 },
});

export default Hoja;
