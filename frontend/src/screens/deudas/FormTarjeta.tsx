import React, { useEffect, useState } from 'react';
import { View, StyleSheet, TouchableOpacity } from 'react-native';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import Icono from '../../components/atoms/Icono';
import { useTheme } from '../../contexts/ThemeContext';
import { tintaSobre } from '../../utils/tinta';
import Hoja from './Hoja';

export const COLORES_TARJETA = ['#6C5CE7', '#2196F3', '#00B894', '#E8A838', '#E74C3C', '#E91E63', '#455A64', '#1A1A1A'];

export interface DatosTarjeta {
  name: string;
  paymentDay: number | null;
  color: string;
}

/** Revisa lo escrito y devuelve los errores por campo (vacio si todo esta bien). */
export const validarTarjeta = (nombre: string, dia: string): { nombre?: string; dia?: string } => {
  const errores: { nombre?: string; dia?: string } = {};
  const n = nombre.trim();
  if (!n) errores.nombre = 'Ponle un nombre a la tarjeta';
  else if (n.length > 60) errores.nombre = 'Máximo 60 caracteres';
  const d = dia.trim();
  if (d && (!/^\d{1,2}$/.test(d) || Number(d) < 1 || Number(d) > 31)) errores.dia = 'Un día entre 1 y 31';
  return errores;
};

interface Props {
  visible: boolean;
  ocupado?: boolean;
  onCerrar: () => void;
  onGuardar: (t: DatosTarjeta) => void;
}

const FormTarjeta: React.FC<Props> = ({ visible, ocupado, onCerrar, onGuardar }) => {
  const { colors } = useTheme();
  const [nombre, setNombre] = useState('');
  const [dia, setDia] = useState('');
  const [color, setColor] = useState(COLORES_TARJETA[0]);
  const [errores, setErrores] = useState<{ nombre?: string; dia?: string }>({});

  useEffect(() => {
    if (visible) { setNombre(''); setDia(''); setColor(COLORES_TARJETA[0]); setErrores({}); }
  }, [visible]);

  const guardar = () => {
    const e = validarTarjeta(nombre, dia);
    setErrores(e);
    if (e.nombre || e.dia) return;
    onGuardar({ name: nombre.trim(), paymentDay: dia.trim() ? Number(dia.trim()) : null, color });
  };

  return (
    <Hoja visible={visible} titulo="Agregar tarjeta" onCerrar={onCerrar} textoConfirmar="Guardar" onConfirmar={guardar} ocupado={ocupado}>
      <Input label="Nombre" value={nombre} onChangeText={setNombre} placeholder="Ej: Visa Bancolombia" error={errores.nombre} />
      <Input label="Día de pago (opcional)" value={dia} onChangeText={setDia} placeholder="1 a 31" keyboardType="number-pad" error={errores.dia} />
      <AppText variant="label">Color</AppText>
      <View style={styles.colores}>
        {COLORES_TARJETA.map((c) => (
          <TouchableOpacity key={c} onPress={() => setColor(c)}
            style={[styles.chip, { backgroundColor: c, borderColor: color === c ? colors.text : 'transparent' }]}
            accessibilityRole="radio" accessibilityState={{ selected: color === c }} accessibilityLabel={`Color ${c}`}>
            {color === c && <Icono nombre="check" color={tintaSobre(c)} tamano={20} />}
          </TouchableOpacity>
        ))}
      </View>
    </Hoja>
  );
};

const styles = StyleSheet.create({
  colores: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  chip: { width: 44, height: 44, borderRadius: 22, borderWidth: 3, alignItems: 'center', justifyContent: 'center' },
});

export default FormTarjeta;
