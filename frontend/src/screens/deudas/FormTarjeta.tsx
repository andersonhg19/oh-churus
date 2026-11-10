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
  id?: number;
  name: string;
  paymentDay: number | null;
  /** Dia de corte: lo comprado hasta ese dia entra en el extracto que se paga. */
  cutoffDay?: number | null;
  color: string;
}

const diaValido = (d: string) => !d || (/^\d{1,2}$/.test(d) && Number(d) >= 1 && Number(d) <= 31);

/** Revisa lo escrito y devuelve los errores por campo (vacio si todo esta bien). */
export const validarTarjeta = (nombre: string, dia: string, corte = ''): { nombre?: string; dia?: string; corte?: string } => {
  const errores: { nombre?: string; dia?: string; corte?: string } = {};
  const n = nombre.trim();
  if (!n) errores.nombre = 'Ponle un nombre a la tarjeta';
  else if (n.length > 60) errores.nombre = 'Máximo 60 caracteres';
  if (!diaValido(dia.trim())) errores.dia = 'Un día entre 1 y 31';
  if (!diaValido(corte.trim())) errores.corte = 'Un día entre 1 y 31';
  return errores;
};

interface Props {
  visible: boolean;
  ocupado?: boolean;
  /** Si viene, se edita esa tarjeta. */
  inicial?: DatosTarjeta | null;
  onCerrar: () => void;
  onGuardar: (t: DatosTarjeta) => void;
}

const FormTarjeta: React.FC<Props> = ({ visible, ocupado, inicial, onCerrar, onGuardar }) => {
  const { colors } = useTheme();
  const [nombre, setNombre] = useState('');
  const [dia, setDia] = useState('');
  const [corte, setCorte] = useState('');
  const [color, setColor] = useState(COLORES_TARJETA[0]);
  const [errores, setErrores] = useState<{ nombre?: string; dia?: string; corte?: string }>({});

  useEffect(() => {
    if (visible) {
      setNombre(inicial?.name || '');
      setDia(inicial?.paymentDay ? String(inicial.paymentDay) : '');
      setCorte(inicial?.cutoffDay ? String(inicial.cutoffDay) : '');
      setColor(inicial?.color || COLORES_TARJETA[0]);
      setErrores({});
    }
  }, [visible, inicial]);

  const guardar = () => {
    const e = validarTarjeta(nombre, dia, corte);
    setErrores(e);
    if (e.nombre || e.dia || e.corte) return;
    onGuardar({
      ...(inicial?.id && { id: inicial.id }),
      name: nombre.trim(),
      paymentDay: dia.trim() ? Number(dia.trim()) : null,
      cutoffDay: corte.trim() ? Number(corte.trim()) : null,
      color,
    });
  };

  return (
    <Hoja visible={visible} titulo={inicial?.id ? 'Editar tarjeta' : 'Agregar tarjeta'} onCerrar={onCerrar}
      textoConfirmar="Guardar" onConfirmar={guardar} ocupado={ocupado}>
      <Input label="Nombre" value={nombre} onChangeText={setNombre} placeholder="Ej: Tarjeta de Crédito Nu" error={errores.nombre} />
      <View style={styles.dias}>
        <View style={{ flex: 1 }}>
          <Input label="Día de corte" value={corte} onChangeText={setCorte} placeholder="1 a 31" keyboardType="number-pad" error={errores.corte} />
        </View>
        <View style={{ flex: 1 }}>
          <Input label="Día de pago" value={dia} onChangeText={setDia} placeholder="1 a 31" keyboardType="number-pad" error={errores.dia} />
        </View>
      </View>
      <AppText variant="caption" color={colors.textSecondary}>
        Con el día de corte sabes qué compras entran en el extracto que toca pagar y cuáles van para el siguiente.
      </AppText>
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
  dias: { flexDirection: 'row', gap: 10 },
  colores: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  chip: { width: 44, height: 44, borderRadius: 22, borderWidth: 3, alignItems: 'center', justifyContent: 'center' },
});

export default FormTarjeta;
