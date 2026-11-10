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
import { estadoService, EstadoDelPeriodo, ElementoDelPlan } from '../../services/estadoService';
import { formatCurrency, formatCompacto, formatDateShort } from '../../utils/format';
import { ESTADOS_ELEMENTO, colorDeTono } from '../../utils/estadoTextos';

/*
 * INGRESOS: los mios y los de los dos. Cuanto se esperaba, cuanto llego y
 * cuanto falta, por persona y por fecha de pago (la quincena del 1 y la del
 * 15 de Samy, el salario del 28 de Andy). Los descuentos de nomina se ven
 * aparte porque salen del ingreso antes de que llegue.
 */
const estadoIngreso = (status: string) =>
  status === 'PAGADO' ? 'Recibido' : status === 'VENCIDO' ? 'Atrasado' : status === 'LIBERADO' ? 'Liberado' : 'Por recibir';

export interface GrupoDeIngresos {
  clave: string;
  nombre: string;
  esperado: number;
  recibido: number;
  items: ElementoDelPlan[];
  /** Lo que el total cuenta y la lista no detalla (0 si cuadran). */
  sinDetalle: number;
}

/**
 * Los ingresos por persona, con TODAS sus lineas: las de un programado (el
 * salario) y las anotadas sueltas (una prima, un bono), que el servidor manda
 * con scheduledMovementId en null. Antes solo se veian las programadas y la
 * lista sumaba menos que el encabezado.
 */
export const agruparIngresos = (estado: Pick<EstadoDelPeriodo, 'incomeByPerson' | 'incomeItems'>): GrupoDeIngresos[] => {
  const items = estado.incomeItems || [];
  const personas = estado.incomeByPerson || [];
  const conocidas = new Set(personas.map((p) => String(p.userId)));
  const grupos: GrupoDeIngresos[] = personas.map((p) => {
    const suyos = items.filter((i) => String(i.payerUserId) === String(p.userId));
    const suma = suyos.reduce((t, i) => t + (Number(i.amount) || 0), 0);
    return {
      clave: String(p.userId), nombre: p.name || 'Alguien', esperado: p.expected, recibido: p.received,
      items: suyos, sinDetalle: Math.round(((p.expected || 0) - suma) * 100) / 100,
    };
  });
  const huerfanos = items.filter((i) => !conocidas.has(String(i.payerUserId)));
  if (huerfanos.length > 0) {
    const suma = huerfanos.reduce((t, i) => t + (Number(i.amount) || 0), 0);
    grupos.push({
      clave: 'otros', nombre: 'Otros ingresos', esperado: suma,
      recibido: huerfanos.filter((i) => i.status === 'PAGADO').reduce((t, i) => t + (Number(i.amount) || 0), 0),
      items: huerfanos, sinDetalle: 0,
    });
  }
  return grupos;
};

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
  const grupos = agruparIngresos(estado!);

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

        {grupos.map((g) => (
          <View key={g.clave} style={[styles.persona, { backgroundColor: colors.surfaceLight }]}>
            <View style={styles.linea}>
              <AppText variant="subtitle">{g.nombre}</AppText>
              <AppText variant="body" style={{ fontWeight: '800' }}>{formatCurrency(g.recibido)} de {formatCurrency(g.esperado)}</AppText>
            </View>
            {g.items.map((i, n) => {
              const e = ESTADOS_ELEMENTO[i.status] || ESTADOS_ELEMENTO.PREVISTO;
              const extra = i.scheduledMovementId === null || i.scheduledMovementId === undefined;
              return (
                <View key={`${i.scheduledMovementId ?? 'extra'}-${i.movementId ?? ''}-${i.date}-${n}`} style={styles.linea}
                  accessible accessibilityLabel={`${i.name || 'Ingreso'}, ${formatDateShort(i.date)}, ${formatCurrency(i.amount)}, ${estadoIngreso(i.status)}`}>
                  <AppText variant="body" color={colors.textSecondary} style={{ flex: 1 }} numberOfLines={1}>
                    {i.name || 'Ingreso'} · {formatDateShort(i.date)}{extra ? ' · no programado' : ''}
                  </AppText>
                  <AppText variant="caption" style={{ color: colorDeTono(e.tono, colors), fontWeight: '700' }}>
                    {estadoIngreso(i.status)}
                  </AppText>
                  <AppText variant="body" style={{ fontWeight: '700', minWidth: 96, textAlign: 'right' }}>{formatCurrency(i.amount)}</AppText>
                </View>
              );
            })}
            {/* Cada peso del total tiene su linea: si el servidor cuenta algo
                que no viene en la lista, se ensena como "otros" en vez de que
                la lista sume menos que el encabezado. */}
            {Math.abs(g.sinDetalle) >= 1 && (
              <View style={styles.linea} accessible
                accessibilityLabel={`Otros ingresos sin detalle: ${formatCurrency(g.sinDetalle)}`}>
                <AppText variant="body" color={colors.textSecondary} style={{ flex: 1 }} numberOfLines={1}>
                  Otros ingresos sin detalle
                </AppText>
                <AppText variant="body" style={{ fontWeight: '700', minWidth: 96, textAlign: 'right' }}>{formatCurrency(g.sinDetalle)}</AppText>
              </View>
            )}
          </View>
        ))}
        {grupos.length === 0 && (
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
