import React, { useEffect, useState } from 'react';
import { View, StyleSheet, TouchableOpacity } from 'react-native';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import { useTheme } from '../../contexts/ThemeContext';
import { formatCurrency, fechaLocalISO } from '../../utils/format';
import Hoja from './Hoja';

export interface DatosDeuda {
  name: string;
  amount: number;
  durationMonths: number;
  dayOfMonth: number;
  categoryId: string;
}

export interface CategoriaSimple { id: string; name: string }

/** Revisa lo escrito y devuelve los errores por campo. */
export const validarDeuda = (nombre: string, cuota: string, cuotas: string, dia: string) => {
  const e: { nombre?: string; cuota?: string; cuotas?: string; dia?: string } = {};
  if (!nombre.trim()) e.nombre = 'Ponle un nombre (ej: Crédito Bancolombia)';
  else if (nombre.trim().length > 100) e.nombre = 'Máximo 100 caracteres';
  const c = Number(cuota.replace(/\D/g, ''));
  if (!c || c <= 0) e.cuota = 'Escribe el valor de la cuota';
  const n = Number(cuotas);
  if (!/^\d{1,3}$/.test(cuotas.trim()) || n < 1 || n > 360) e.cuotas = 'Entre 1 y 360 cuotas';
  const d = Number(dia);
  if (!/^\d{1,2}$/.test(dia.trim()) || d < 1 || d > 31) e.dia = 'Un día entre 1 y 31';
  return e;
};

/** La primera fecha de pago: este mes si el dia aun no paso, si no el siguiente. Devuelve el 1 de ese mes. */
export const inicioDeLaDeuda = (dia: number, hoy = new Date()) => {
  const primera = hoy.getDate() <= dia ? new Date(hoy.getFullYear(), hoy.getMonth(), 1) : new Date(hoy.getFullYear(), hoy.getMonth() + 1, 1);
  return fechaLocalISO(primera);
};

interface Props {
  visible: boolean;
  ocupado?: boolean;
  categorias: CategoriaSimple[];
  onCerrar: () => void;
  onGuardar: (d: DatosDeuda) => void;
}

/*
 * Una deuda en cuotas, sin intereses ni tablas: nombre, cuanto es la cuota,
 * cuantas son y que dia se pagan. Cada mes aparece su cuota por pagar.
 */
const FormDeuda: React.FC<Props> = ({ visible, ocupado, categorias, onCerrar, onGuardar }) => {
  const { colors } = useTheme();
  const [nombre, setNombre] = useState('');
  const [cuota, setCuota] = useState('');
  const [cuotas, setCuotas] = useState('');
  const [dia, setDia] = useState('');
  const [categoria, setCategoria] = useState<string>('');
  const [errores, setErrores] = useState<ReturnType<typeof validarDeuda>>({});

  /* Al abrir se limpia; las categorias llegan despues y NO deben borrar lo
     que ya se escribio: solo eligen "Deuda" si aun no hay categoria. */
  useEffect(() => {
    if (!visible) return;
    setNombre(''); setCuota(''); setCuotas(''); setDia(''); setErrores({}); setCategoria('');
  }, [visible]);
  useEffect(() => {
    if (!visible || !categorias.length) return;
    setCategoria((actual) => actual || categorias.find((c) => /deuda/i.test(c.name))?.id || categorias[0].id);
  }, [visible, categorias]);

  const guardar = () => {
    const e = validarDeuda(nombre, cuota, cuotas, dia);
    setErrores(e);
    if (Object.keys(e).length || !categoria) return;
    onGuardar({ name: nombre.trim(), amount: Number(cuota.replace(/\D/g, '')), durationMonths: Number(cuotas),
      dayOfMonth: Number(dia), categoryId: categoria });
  };

  const valor = Number(cuota.replace(/\D/g, '')) || 0;
  const n = Number(cuotas) || 0;

  return (
    <Hoja visible={visible} titulo="Nueva deuda en cuotas" onCerrar={onCerrar} textoConfirmar="Guardar" onConfirmar={guardar} ocupado={ocupado}>
      <Input label="Nombre" value={nombre} onChangeText={setNombre} placeholder="Ej: Crédito libre inversión" error={errores.nombre} />
      <View style={styles.fila}>
        <View style={{ flex: 1.4 }}>
          <Input label="Valor de la cuota" value={cuota} onChangeText={setCuota} placeholder="500.000" keyboardType="number-pad" error={errores.cuota} />
        </View>
        <View style={{ flex: 1 }}>
          <Input label="Cuotas" value={cuotas} onChangeText={setCuotas} placeholder="24" keyboardType="number-pad" error={errores.cuotas} />
        </View>
        <View style={{ flex: 1 }}>
          <Input label="Día de pago" value={dia} onChangeText={setDia} placeholder="10" keyboardType="number-pad" error={errores.dia} />
        </View>
      </View>
      {valor > 0 && n > 0 && (
        <AppText variant="caption" color={colors.textSecondary}>{`${n} cuotas de ${formatCurrency(valor)}: en total ${formatCurrency(valor * n)}.`}</AppText>
      )}
      <AppText variant="label">Categoría</AppText>
      <View style={styles.chips}>
        {categorias.map((c) => (
          <TouchableOpacity key={c.id} onPress={() => setCategoria(c.id)}
            style={[styles.chip, { backgroundColor: categoria === c.id ? colors.primary : colors.surfaceLight }]}
            accessibilityRole="radio" accessibilityState={{ selected: categoria === c.id }} accessibilityLabel={`Categoría ${c.name}`}>
            <AppText variant="caption" style={{ fontWeight: '700', color: categoria === c.id ? '#1A1405' : colors.textSecondary }}>{c.name}</AppText>
          </TouchableOpacity>
        ))}
      </View>
    </Hoja>
  );
};

const styles = StyleSheet.create({
  fila: { flexDirection: 'row', gap: 8 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { minHeight: 44, paddingHorizontal: 14, borderRadius: 22, justifyContent: 'center' },
});

export default FormDeuda;
