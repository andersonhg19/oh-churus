import React, { useCallback, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import AppText from '../../components/atoms/Text';
import Spinner from '../../components/atoms/Spinner';
import Encabezado from '../../components/molecules/Encabezado';
import EstadoError from '../../components/molecules/EstadoError';
import { useTheme } from '../../contexts/ThemeContext';
import { useAlcance } from '../../contexts/AlcanceContext';
import { useCarga, exigir } from '../../hooks/useCarga';
import { estadoService, PeriodoDelHistorial } from '../../services/estadoService';
import { formatCurrency, formatCompacto, formatDateShort } from '../../utils/format';
import { ESTADOS, colorDeTono, fraseDelCierre } from '../../utils/estadoTextos';

/*
 * EL CIERRE DE CADA MES: presupuestado contra real, categoria por categoria,
 * de los ultimos periodos. Es la pregunta de fin de mes: "para mascotas
 * esperabamos 500.000 y gastamos 700.000", "queriamos ahorrar 3.000.000 y no
 * ahorramos nada". Se ve un periodo a la vez, del mas reciente hacia atras.
 */
const CierreScreen: React.FC<{ navigation: any }> = () => {
  const { colors } = useTheme();
  const { alcance, corte, periodo } = useAlcance();
  const [periodos, setPeriodos] = useState<PeriodoDelHistorial[]>([]);
  const [elegido, setElegido] = useState(0);

  const traer = useCallback(async () => {
    const r = exigir(await estadoService.historial(corte, periodo.inicio, alcance, 6));
    const lista = [...(r?.periods || [])].reverse();
    setPeriodos(lista);
    setElegido(0);
  }, [corte, periodo.inicio, alcance]);
  const { cargando, error, cargar } = useCarga(traer);
  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  if (cargando && periodos.length === 0) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}><Encabezado titulo="Cierre" atras /><Spinner fullScreen /></View>;
  if (error) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}><Encabezado titulo="Cierre" atras /><EstadoError mensaje={error} onReintentar={cargar} /></View>;

  const p = periodos[elegido];
  return (
    <View style={[styles.pantalla, { backgroundColor: colors.background }]}>
      <Encabezado titulo="Cierre" atras />
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.meses}>
        {periodos.map((x, i) => (
          <TouchableOpacity key={x.periodStart} style={[styles.mes, { backgroundColor: i === elegido ? colors.primary : colors.surfaceLight }]}
            onPress={() => setElegido(i)} accessibilityRole="tab" accessibilityState={{ selected: i === elegido }}
            accessibilityLabel={`Periodo del ${formatDateShort(x.periodStart)} al ${formatDateShort(x.periodEnd)}`}>
            <AppText variant="caption" style={{ fontWeight: '700', color: i === elegido ? '#1A1405' : colors.textSecondary }}>
              {formatDateShort(x.periodStart)}{x.closed ? '' : ' · en curso'}
            </AppText>
          </TouchableOpacity>
        ))}
      </ScrollView>
      {p && (
        <ScrollView contentContainerStyle={styles.contenido}>
          <View style={[styles.resumen, { backgroundColor: colors.surfaceLight }]}>
            {[
              ['Ingresos', p.totals.incomeReceived, `de ${formatCompacto(p.totals.incomeExpected)}`],
              ['Gastado', p.totals.spent, `de ${formatCompacto(p.totals.budgeted - p.totals.savingsGoal)}`],
              ['Ahorrado', p.totals.saved, `de ${formatCompacto(p.totals.savingsGoal)}`],
            ].map(([t, v, d]) => (
              <View key={t as string} style={{ flex: 1, gap: 2 }}>
                <AppText variant="caption" color={colors.textSecondary}>{t as string}</AppText>
                <AppText variant="body" style={{ fontWeight: '800' }} numberOfLines={1}>{formatCompacto(v as number)}</AppText>
                <AppText variant="caption" color={colors.textMuted} numberOfLines={1}>{d as string}</AppText>
              </View>
            ))}
          </View>
          {p.categories.map((c) => {
            const e = ESTADOS[c.status];
            const tono = colorDeTono(e.tono, colors);
            return (
              <View key={c.categoryId} style={[styles.fila, { borderBottomColor: colors.border }]}>
                <View style={styles.entre}>
                  <AppText variant="body" style={{ fontWeight: '700', flex: 1 }} numberOfLines={1}>{c.categoryName}</AppText>
                  <AppText variant="caption" style={{ color: tono, fontWeight: '700' }}>{e.texto}</AppText>
                </View>
                <AppText variant="body" color={colors.textSecondary}>{fraseDelCierre(c, p.closed)}</AppText>
              </View>
            );
          })}
          {p.categories.length === 0 && (
            <AppText variant="body" color={colors.textSecondary} style={{ textAlign: 'center' }}>Sin presupuesto ni gastos en este periodo.</AppText>
          )}
        </ScrollView>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  pantalla: { flex: 1 },
  meses: { gap: 8, paddingHorizontal: 16, paddingVertical: 10 },
  mes: { paddingHorizontal: 14, minHeight: 40, borderRadius: 20, justifyContent: 'center' },
  contenido: { padding: 16, gap: 4, paddingBottom: 32 },
  resumen: { flexDirection: 'row', gap: 8, borderRadius: 14, padding: 12, marginBottom: 8 },
  fila: { paddingVertical: 12, borderBottomWidth: 1, gap: 4 },
  entre: { flexDirection: 'row', alignItems: 'center', gap: 8 },
});

export default CierreScreen;
