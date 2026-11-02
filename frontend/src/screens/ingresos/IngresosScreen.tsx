import React, { useCallback, useState } from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import AppText from '../../components/atoms/Text';
import Spinner from '../../components/atoms/Spinner';
import Encabezado from '../../components/molecules/Encabezado';
import EstadoError from '../../components/molecules/EstadoError';
import { useTheme } from '../../contexts/ThemeContext';
import { useAlcance } from '../../contexts/AlcanceContext';
import { useCarga, exigir } from '../../hooks/useCarga';
import { estadoService, EstadoDelPeriodo } from '../../services/estadoService';
import { formatCurrency, formatCompacto, formatDateShort } from '../../utils/format';
import { ESTADOS_ELEMENTO, colorDeTono } from '../../utils/estadoTextos';

/*
 * INGRESOS: los mios y los de los dos. Cuanto se esperaba, cuanto llego y
 * cuanto falta, por persona y por fecha de pago (la quincena del 1 y la del
 * 15 de Samy, el salario del 28 de Andy). Los descuentos de nomina se ven
 * aparte porque salen del ingreso antes de que llegue.
 */
const IngresosScreen: React.FC<{ navigation: any }> = () => {
  const { colors } = useTheme();
  const { alcance, corte, periodo } = useAlcance();
  const [estado, setEstado] = useState<EstadoDelPeriodo | null>(null);

  const traer = useCallback(async () => {
    setEstado(exigir(await estadoService.periodo(corte, periodo.inicio, alcance)) || null);
  }, [corte, periodo.inicio, alcance]);
  const { cargando, error, cargar } = useCarga(traer);
  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  const cabeza = <Encabezado titulo="Ingresos" atras />;
  if (cargando && !estado) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}>{cabeza}<Spinner fullScreen /></View>;
  if (error) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}>{cabeza}<EstadoError mensaje={error} onReintentar={cargar} /></View>;
  const t = estado!.totals;

  return (
    <View style={[styles.pantalla, { backgroundColor: colors.background }]}>
      {cabeza}
      <ScrollView contentContainerStyle={styles.contenido}>
        <View style={[styles.resumen, { backgroundColor: colors.surfaceLight }]}>
          {[['Esperado', t.incomeExpected], ['Recibido', t.incomeReceived], ['Falta', t.incomePending]].map(([k, v]) => (
            <View key={k as string} style={{ flex: 1, gap: 2 }}>
              <AppText variant="caption" color={colors.textSecondary}>{k as string}</AppText>
              <AppText variant="body" style={{ fontWeight: '800' }} numberOfLines={1}>{formatCompacto(v as number)}</AppText>
            </View>
          ))}
        </View>
        {t.payrollDeductions > 0 && (
          <AppText variant="caption" color={colors.textSecondary}>
            Descuentos de nómina del periodo: {formatCurrency(t.payrollDeductions)}.
          </AppText>
        )}

        {estado!.incomeByPerson.map((p) => (
          <View key={p.userId} style={[styles.persona, { backgroundColor: colors.surfaceLight }]}>
            <View style={styles.linea}>
              <AppText variant="subtitle">{p.name || 'Alguien'}</AppText>
              <AppText variant="body" style={{ fontWeight: '800' }}>{formatCurrency(p.received)} de {formatCurrency(p.expected)}</AppText>
            </View>
            {estado!.incomeItems.filter((i) => i.payerUserId === p.userId).map((i, n) => {
              const e = ESTADOS_ELEMENTO[i.status];
              return (
                <View key={`${i.scheduledMovementId}-${n}`} style={styles.linea}>
                  <AppText variant="body" color={colors.textSecondary} style={{ flex: 1 }} numberOfLines={1}>
                    {i.name} · {formatDateShort(i.date)}
                  </AppText>
                  <AppText variant="caption" style={{ color: colorDeTono(e.tono, colors), fontWeight: '700' }}>
                    {i.status === 'PAGADO' ? 'Recibido' : i.status === 'VENCIDO' ? 'Atrasado' : 'Por recibir'}
                  </AppText>
                  <AppText variant="body" style={{ fontWeight: '700', minWidth: 96, textAlign: 'right' }}>{formatCurrency(i.amount)}</AppText>
                </View>
              );
            })}
          </View>
        ))}
        {estado!.incomeByPerson.length === 0 && (
          <AppText variant="body" color={colors.textSecondary} style={{ textAlign: 'center' }}>No hay ingresos en este periodo.</AppText>
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  pantalla: { flex: 1 },
  contenido: { padding: 16, gap: 12, paddingBottom: 32 },
  resumen: { flexDirection: 'row', gap: 8, borderRadius: 14, padding: 12 },
  persona: { borderRadius: 14, padding: 14, gap: 8 },
  linea: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
});

export default IngresosScreen;
