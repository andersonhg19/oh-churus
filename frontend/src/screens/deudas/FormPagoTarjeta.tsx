import React, { useEffect, useState } from 'react';
import { View, StyleSheet, TouchableOpacity } from 'react-native';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import Calendario from '../../components/molecules/Calendario';
import { useTheme } from '../../contexts/ThemeContext';
import { formatCurrency, fechaLocalISO, parseMonto } from '../../utils/format';
import { Tarjeta } from '../../services/deudaService';
import { diaMes } from './fechas';
import Hoja from './Hoja';

/** Lo que se puede pagar hoy en la tarjeta: todo lo comprado hasta hoy, menos el abono. */
export const pagableHoy = (t: Tarjeta) => Number(t.payableNow ?? (t.dueNow + (t.currentCycle || 0)));

/** Revisa el monto: mayor que cero y, si es con fecha de hoy, no mas de lo que se debe. */
export const validarPago = (texto: string, maximo: number, esHoy: boolean): string | undefined => {
  const n = parseMonto(texto);
  if (!texto.trim() || Number.isNaN(n) || n <= 0) return 'Escribe cuánto pagaste (mayor que cero)';
  if (esHoy && n > maximo) return `Hoy se deben ${formatCurrency(maximo)}: no puedes pagar más que eso`;
  return undefined;
};

interface Props {
  tarjeta: Tarjeta | null;
  ocupado?: boolean;
  onCerrar: () => void;
  onPagar: (monto: number, fecha: string) => void;
}

/*
 * PAGAR LA TARJETA: cuanto y con que fecha. Por defecto todo lo que se debe
 * hoy; se puede pagar menos (un abono). No es un gasto nuevo: las compras ya
 * contaron cuando se hicieron.
 */
const FormPagoTarjeta: React.FC<Props> = ({ tarjeta, ocupado, onCerrar, onPagar }) => {
  const { colors } = useTheme();
  const hoy = fechaLocalISO();
  const maximo = tarjeta ? pagableHoy(tarjeta) : 0;
  const [monto, setMonto] = useState('');
  const [fecha, setFecha] = useState(hoy);
  const [error, setError] = useState<string | undefined>();
  const [eligiendo, setEligiendo] = useState(false);

  useEffect(() => {
    if (!tarjeta) return;
    setMonto(String(Math.round(pagableHoy(tarjeta))));
    setFecha(fechaLocalISO());
    setError(undefined);
  }, [tarjeta]);

  const pagar = () => {
    const e = validarPago(monto, maximo, fecha === hoy);
    setError(e);
    if (!e) onPagar(parseMonto(monto), fecha);
  };

  const n = parseMonto(monto);
  return (
    <>
      <Hoja visible={!!tarjeta} titulo={tarjeta ? `Pagar ${tarjeta.name}` : ''} onCerrar={onCerrar}
        textoConfirmar="Pagar" onConfirmar={pagar} ocupado={ocupado}>
        <AppText variant="body" color={colors.textSecondary}>
          {`Hoy se deben ${formatCurrency(maximo)}. Puedes pagar todo o una parte.`}
        </AppText>
        <Input label="¿Cuánto pagaste?" value={monto} onChangeText={(v) => { setMonto(v); setError(undefined); }}
          keyboardType="number-pad" error={error} />
        <View style={styles.fila}>
          {!Number.isNaN(n) && n !== maximo && maximo > 0 && (
            <TouchableOpacity style={[styles.chip, { backgroundColor: colors.surfaceLight }]}
              onPress={() => { setMonto(String(Math.round(maximo))); setError(undefined); }}
              accessibilityRole="button" accessibilityLabel="Pagar todo">
              <AppText variant="caption" style={{ fontWeight: '700' }}>{`Todo: ${formatCurrency(maximo)}`}</AppText>
            </TouchableOpacity>
          )}
          <TouchableOpacity style={[styles.chip, { backgroundColor: colors.surfaceLight }]} onPress={() => setEligiendo(true)}
            accessibilityRole="button" accessibilityLabel="Fecha del pago">
            <AppText variant="caption" style={{ fontWeight: '700' }}>{fecha === hoy ? 'Fecha: hoy' : `Fecha: ${diaMes(fecha)}`}</AppText>
          </TouchableOpacity>
        </View>
        <AppText variant="caption" color={colors.textMuted}>
          No es un gasto nuevo: las compras ya contaron cuando las hiciste.
        </AppText>
      </Hoja>
      <Calendario visible={eligiendo} fecha={fecha} maximo={hoy} titulo="Fecha del pago"
        onElegir={(f) => { setFecha(f); setError(undefined); }} onCerrar={() => setEligiendo(false)} />
    </>
  );
};

const styles = StyleSheet.create({
  fila: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { minHeight: 44, paddingHorizontal: 14, borderRadius: 22, justifyContent: 'center' },
});

export default FormPagoTarjeta;
