import React, { useCallback, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, RefreshControl } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import AppText from '../../components/atoms/Text';
import Spinner from '../../components/atoms/Spinner';
import Icono from '../../components/atoms/Icono';
import CirculoCategoria from '../../components/atoms/CirculoCategoria';
import Encabezado from '../../components/molecules/Encabezado';
import EstadoError from '../../components/molecules/EstadoError';
import EmptyState from '../../components/molecules/EmptyState';
import { useTheme } from '../../contexts/ThemeContext';
import { useAlcance } from '../../contexts/AlcanceContext';
import { useCarga, exigir } from '../../hooks/useCarga';
import { estadoService, CategoriaDelPeriodo } from '../../services/estadoService';
import { formatCurrency } from '../../utils/format';
import { diaMes } from '../deudas/fechas';
import { textoLegibleSobre } from '../../theme/contraste';

/*
 * El verde del AHORRO, distinto del de los ingresos: lo que entra es plata del
 * mes, lo ahorrado es plata guardada. Mismo tono en los dos temas, mas oscuro
 * en el claro para que las cifras se lean (5.3:1 sobre blanco).
 */
export const colorAhorro = (isDark: boolean) => (isDark ? '#1FA67A' : '#0E7A55');

/*
 * AHORROS: cuanto hay guardado en cada ahorro (sumando todos los meses), que
 * se metio y que se saco este mes, y en que se uso. Depositar es anotar un
 * gasto en la categoria de ahorro; usarlo es anotar un gasto "pagado con" ese
 * ahorro, que no baja la plata libre del mes.
 */
const AhorrosScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { colors, isDark } = useTheme();
  const verde = colorAhorro(isDark);
  const tinta = textoLegibleSobre(verde);
  const { alcance, corte, periodo } = useAlcance();
  const [ahorros, setAhorros] = useState<CategoriaDelPeriodo[] | null>(null);
  const [abierto, setAbierto] = useState<number | null>(null);

  const traer = useCallback(async () => {
    const e = exigir(await estadoService.periodo(corte, periodo.inicio, alcance));
    setAhorros((e?.categories || []).filter((c) => c.kind === 'SAVING'));
  }, [corte, periodo.inicio, alcance]);
  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traer);
  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  const cabeza = <Encabezado titulo="Ahorros" />;
  const marco = (contenido: React.ReactNode) => (
    <View style={[styles.pantalla, { backgroundColor: colors.background }]}>{cabeza}{contenido}</View>
  );
  if (cargando && !ahorros) return marco(<Spinner fullScreen />);
  if (error) return marco(<EstadoError mensaje={error} onReintentar={cargar} />);

  const lista = ahorros || [];
  const total = lista.reduce((s, c) => s + Number(c.balance || 0), 0);

  if (lista.length === 0) {
    return marco(
      <EmptyState
        icon="🐷"
        title="Aún no hay ahorros"
        message={'Un ahorro es una categoría de tipo Ahorro. Créala en Ajustes › Categorías, ponle una meta en el Presupuesto y deposita anotando un gasto en ella.'}
      />,
    );
  }

  const depositar = (c: CategoriaDelPeriodo) =>
    navigation.navigate('Anotar', { tipo: 'EXPENSE', categoriaId: c.categoryId });
  const usar = (c: CategoriaDelPeriodo) =>
    navigation.navigate('Anotar', { tipo: 'EXPENSE', pagadoCon: `ahorro:${c.categoryId}` });

  return marco(
    <ScrollView
      contentContainerStyle={styles.contenido}
      refreshControl={<RefreshControl refreshing={refrescando} onRefresh={refrescar} />}
    >
      <View style={[styles.total, { backgroundColor: colors.surfaceLight, borderColor: verde }]}
        accessible accessibilityLabel={`Hay ${formatCurrency(total)} ahorrado`}>
        <View style={[styles.cerdito, { backgroundColor: verde }]}>
          <Icono nombre="piggy-bank" color={tinta} tamano={26} />
        </View>
        <View style={{ flex: 1 }}>
          <AppText variant="caption" color={colors.textSecondary}>Ahorro</AppText>
          <AppText variant="title" color={verde} adjustsFontSizeToFit numberOfLines={1}>
            Hay {formatCurrency(total)}
          </AppText>
          <AppText variant="caption" color={colors.textSecondary}>ahorrado, sumando todos los meses</AppText>
        </View>
      </View>

      {lista.map((c) => {
        const saldo = Number(c.balance || 0);
        const metido = Number(c.actual || 0);
        const retiros = c.withdrawals || [];
        const usado = c.withdrawn != null ? Number(c.withdrawn) : retiros.reduce((s, w) => s + Number(w.amount || 0), 0);
        const meta = Number(c.allocated || 0);
        const pct = meta > 0 ? Math.round((metido / meta) * 100) : 0;
        const expandido = abierto === c.categoryId;
        const depositos = (c.items || []).filter((i) => i.status === 'PAGADO');
        const enDepositos = depositos.reduce((s, i) => s + Number(i.amount || 0), 0);
        const otros = metido - enDepositos;
        return (
          <View key={c.categoryId} style={[styles.tarjeta, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <TouchableOpacity
              onPress={() => setAbierto(expandido ? null : c.categoryId)}
              style={styles.cabezaTarjeta}
              accessibilityRole="button"
              accessibilityState={{ expanded: expandido }}
              accessibilityLabel={`${c.categoryName}: hay ${formatCurrency(saldo)}. Este mes entró ${formatCurrency(metido)} y se usó ${formatCurrency(usado)}. ${expandido ? 'Ocultar' : 'Ver'} el detalle`}
            >
              <CirculoCategoria icono={c.categoryIcon || 'piggy-bank'} color={c.categoryColor || verde} tamano={40} />
              <View style={{ flex: 1, gap: 2 }}>
                <AppText variant="body" style={{ fontWeight: '700' }} numberOfLines={1}>{c.categoryName}</AppText>
                <AppText variant="subtitle" color={saldo < 0 ? colors.danger : verde} style={{ fontWeight: '800' }}>
                  {formatCurrency(saldo)}
                </AppText>
                <AppText variant="caption" color={colors.textSecondary}>
                  Este mes: +{formatCurrency(metido)} · −{formatCurrency(usado)}
                </AppText>
              </View>
              <Icono nombre={expandido ? 'down' : 'right'} color={colors.textSecondary} tamano={20} />
            </TouchableOpacity>

            {meta > 0 && (
              <View style={{ gap: 4 }}>
                <AppText variant="caption" color={colors.textSecondary}>
                  Meta del mes {formatCurrency(meta)}: {pct}%
                </AppText>
                <View style={[styles.barra, { backgroundColor: colors.border }]}>
                  <View style={[styles.relleno, { backgroundColor: verde, width: `${Math.min(100, Math.max(0, pct))}%` }]} />
                </View>
              </View>
            )}

            <View style={styles.acciones}>
              <TouchableOpacity style={[styles.accion, { backgroundColor: verde }]} onPress={() => depositar(c)}
                accessibilityRole="button" accessibilityLabel={`Depositar en ${c.categoryName}`}>
                <Icono nombre="plus" color={tinta} tamano={18} />
                <AppText variant="label" color={tinta} style={{ fontWeight: '700' }}>Depositar</AppText>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.accion, { borderColor: verde, borderWidth: 2 }]} onPress={() => usar(c)}
                accessibilityRole="button" accessibilityLabel={`Usar plata de ${c.categoryName} para pagar un gasto`}>
                <Icono nombre="minus" color={verde} tamano={18} />
                <AppText variant="label" color={verde} style={{ fontWeight: '700' }}>Usar</AppText>
              </TouchableOpacity>
            </View>

            {expandido && (
              <View style={[styles.historial, { borderTopColor: colors.border }]}>
                <AppText variant="label">Metido este mes</AppText>
                {depositos.map((i, n) => (
                  <View key={`d-${i.scheduledMovementId}-${n}`} style={styles.linea}>
                    <AppText variant="body" color={colors.textSecondary} style={{ flex: 1 }} numberOfLines={1}>
                      {i.name}{i.payerName ? ` · ${i.payerName}` : ''} · {diaMes(i.date)}
                    </AppText>
                    <AppText variant="body" color={verde} style={{ fontWeight: '700' }}>+{formatCurrency(i.amount)}</AppText>
                  </View>
                ))}
                {otros > 0 && (
                  <View style={styles.linea}>
                    <AppText variant="body" color={colors.textSecondary} style={{ flex: 1 }}>
                      {depositos.length ? 'Otros depósitos' : 'Depósitos del mes'}
                    </AppText>
                    <AppText variant="body" color={verde} style={{ fontWeight: '700' }}>+{formatCurrency(otros)}</AppText>
                  </View>
                )}
                {metido <= 0 && (
                  <AppText variant="caption" color={colors.textMuted}>Este mes aún no se ha depositado nada.</AppText>
                )}
                {(c.byPerson || []).filter((p) => p.actual > 0).length > 1 && (
                  <AppText variant="caption" color={colors.textSecondary}>
                    {(c.byPerson || []).filter((p) => p.actual > 0).map((p) => `${p.name || 'Alguien'} ${formatCurrency(p.actual)}`).join(' · ')}
                  </AppText>
                )}

                <AppText variant="label" style={{ marginTop: 8 }}>Usado en</AppText>
                {retiros.map((w) => (
                  <View key={`w-${w.movementId}`} style={styles.linea}>
                    <AppText variant="body" color={colors.textSecondary} style={{ flex: 1 }} numberOfLines={2}>
                      {w.description || 'Gasto'} · Para {w.toCategoryName} · {diaMes(w.date)}
                    </AppText>
                    <AppText variant="body" color={colors.expense} style={{ fontWeight: '700' }}>−{formatCurrency(w.amount)}</AppText>
                  </View>
                ))}
                {retiros.length === 0 && (
                  <AppText variant="caption" color={colors.textMuted}>Este mes no se ha usado.</AppText>
                )}
              </View>
            )}
          </View>
        );
      })}
    </ScrollView>,
  );
};

const styles = StyleSheet.create({
  pantalla: { flex: 1 },
  contenido: { padding: 16, gap: 12, paddingBottom: 32, width: '100%', maxWidth: 720, alignSelf: 'center' },
  total: { flexDirection: 'row', alignItems: 'center', gap: 14, borderRadius: 16, padding: 16, borderWidth: 1 },
  cerdito: { width: 52, height: 52, borderRadius: 26, alignItems: 'center', justifyContent: 'center' },
  tarjeta: { borderRadius: 16, padding: 14, gap: 12, borderWidth: 1 },
  cabezaTarjeta: { flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 44 },
  barra: { height: 8, borderRadius: 4, overflow: 'hidden' },
  relleno: { height: 8, borderRadius: 4 },
  acciones: { flexDirection: 'row', gap: 10 },
  accion: { flex: 1, minHeight: 44, borderRadius: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  historial: { borderTopWidth: 1, paddingTop: 10, gap: 6 },
  linea: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8, minHeight: 28 },
});

export default AhorrosScreen;
