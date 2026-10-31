import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Spinner from '../../components/atoms/Spinner';
import EmptyState from '../../components/molecules/EmptyState';
import EstadoError from '../../components/molecules/EstadoError';
import PeriodNavigator from '../../components/molecules/PeriodNavigator';
import DonutChart from '../../components/molecules/DonutChart';
import CapsuleToggle from '../../components/molecules/CapsuleToggle';
import BudgetVsReal from '../../components/molecules/BudgetVsReal';
import { spacing } from '../../theme';
import { dashboardService } from '../../services/dashboardService';
import { movementService } from '../../services/movementService';
import { budgetAllocationService } from '../../services/budgetAllocationService';
import { CategorySummary, DashboardSummary, Movement } from '../../types';
import { useCarga, exigir } from '../../hooks/useCarga';
import { getStartOfPeriod, getEndOfPeriod, navigatePeriod } from '../../utils/periodUtils';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type ViewMode = 'EXPENSE' | 'INCOME' | 'BALANCE';
/* REAL: lo confirmado. PLAN: lo presupuestado (gastos) y lo programado
   (ingresos). La dona con los porcentajes de cada area solo existia en REAL, y
   con el mes recien empezado no tenia nada que dibujar. */
type Fuente = 'REAL' | 'PLAN';

/* Muchas categorias no tienen color propio; sin una paleta la dona salia de un
   solo color y no se distinguia un area de otra. */
const PALETA = ['#E8A838', '#4E79A7', '#F28E2B', '#59A14F', '#E15759', '#76B7B2',
  '#EDC948', '#B07AA1', '#FF9DA7', '#9C755F', '#86BCB6', '#D37295'];

type SummaryStackParamList = {
  SummaryMain: undefined;
  CategoryDrillDown: { categoryId: string; categoryName: string; color: string; startDate: string; endDate: string };
};

type Props = NativeStackScreenProps<SummaryStackParamList, 'SummaryMain'>;

const SummaryScreen: React.FC<Props> = ({ navigation }) => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const budgetDay = user?.budgetStartDay || 1;

  const [periodStart, setPeriodStart] = useState(() => getStartOfPeriod(budgetDay, new Date()));
  const [viewMode, setViewMode] = useState<ViewMode>('BALANCE');
  const [categoryData, setCategoryData] = useState<CategorySummary[]>([]);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [allMovements, setAllMovements] = useState<Movement[]>([]);
  /* Sin eleccion explicita: REAL si ya hay gastos confirmados, PLAN si no.
     Asi el mes recien empezado no abre en una dona vacia. */
  const [fuenteElegida, setFuente] = useState<Fuente | null>(null);
  const [asignado, setAsignado] = useState<any[]>([]);

  const periodEnd = getEndOfPeriod(budgetDay, periodStart);

  const traerResumen = useCallback(async () => {
    if (!user) return;
    const [catRes, sumRes] = await Promise.all([
      dashboardService.getByCategory(user.userId, budgetDay, periodStart),
      dashboardService.getSummary(user.userId, budgetDay, periodStart),
    ]);
    setCategoryData(exigir(catRes) || []);
    setSummary(exigir(sumRes) || null);

    // Los movimientos solo alimentan las barras presupuesto-vs-real: si fallan,
    // la dona y los totales siguen siendo correctos, asi que no tumban la pantalla.
    try {
      const pStart = getStartOfPeriod(budgetDay, new Date(periodStart));
      const pEnd = getEndOfPeriod(budgetDay, pStart);
      const movRes = await movementService.getAll({ userId: user.userId, startDate: pStart, endDate: pEnd, page: 0, size: 200 });
      if (movRes.correct && movRes.object) setAllMovements(movRes.object.list || []);
    } catch { /* las barras se quedan sin presupuesto, el resumen no */ }
    try {
      const b = await budgetAllocationService.summary(user.userId, budgetDay, periodStart);
      setAsignado(b.correct ? b.object?.categories || [] : []);
    } catch { setAsignado([]); }
  }, [user, budgetDay, periodStart]);

  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traerResumen);

  useFocusEffect(
    useCallback(() => {
      cargar();
    }, [cargar]),
  );

  const currentPeriodStart = getStartOfPeriod(budgetDay, new Date());
  const handlePrevPeriod = () => {
    const { start, end } = navigatePeriod(periodStart, budgetDay, 'prev');
    setPeriodStart(start);
  };
  const handleNextPeriod = () => {
    const { start, end } = navigatePeriod(periodStart, budgetDay, 'next');
    setPeriodStart(start);
  };
  const limiteFuturo = (() => {
    let p = currentPeriodStart;
    for (let i = 0; i < 3; i++) p = navigatePeriod(p, budgetDay, 'next').start;
    return p;
  })();
  const canGoNext = periodStart < limiteFuturo;

  /* Ingresos del plan: confirmados mas pendientes de categorias de ingreso. */
  const ingresosPlan = (() => {
    const porCat: Record<string, { label: string; value: number; color?: string }> = {};
    allMovements.forEach((m) => {
      if (m.categoryType !== 'INCOME') return;
      const k = String(m.categoryId);
      porCat[k] = porCat[k] || { label: m.categoryName || 'Ingreso', value: 0, color: m.categoryColor };
      porCat[k].value += m.amount || 0;
    });
    return porCat;
  })();
  const totalIngresosPlan = Object.values(ingresosPlan).reduce((s, x) => s + x.value, 0);
  const totalPresupuestado = asignado.reduce((s, a) => s + Number(a.allocatedAmount || 0), 0);
  const hayReal = categoryData.some((c) => c.total > 0);
  const fuente: Fuente = fuenteElegida ?? (hayReal ? 'REAL' : 'PLAN');

  // Filtrar data según modo
  const getChartData = () => {
    if (fuente === 'PLAN') {
      if (viewMode === 'INCOME') {
        const lista = Object.entries(ingresosPlan);
        return {
          slices: lista.map(([id, x], n) => ({
            id, label: x.label, value: x.value,
            percentage: totalIngresosPlan > 0 ? (x.value / totalIngresosPlan) * 100 : 0,
            color: x.color || PALETA[n % PALETA.length],
          })),
          total: totalIngresosPlan,
        };
      }
      return {
        slices: asignado.filter((a) => Number(a.allocatedAmount) > 0).map((a, n) => ({
          id: String(a.categoryId), label: a.categoryName, value: Number(a.allocatedAmount),
          percentage: totalPresupuestado > 0 ? (Number(a.allocatedAmount) / totalPresupuestado) * 100 : 0,
          color: a.categoryColor || PALETA[n % PALETA.length],
        })),
        total: totalPresupuestado,
      };
    }
    if (viewMode === 'INCOME') {
      const filtered = categoryData.filter(d => d.categoryType === 'INCOME');
      const total = filtered.reduce((s, d) => s + d.total, 0);
      return {
        slices: filtered.map(d => ({
          id: String(d.categoryId), label: d.categoryName, value: d.total,
          percentage: total > 0 ? (d.total / total) * 100 : 0,
          color: d.color || PALETA[filtered.indexOf(d) % PALETA.length],
        })),
        total,
      };
    }
    // EXPENSE y BALANCE muestran la misma dona (solo gastos), pero el centro cambia
    const filtered = categoryData.filter(d => d.categoryType === 'EXPENSE');
    const total = filtered.reduce((s, d) => s + d.total, 0);
    return {
      slices: filtered.map(d => ({
        id: String(d.categoryId), label: d.categoryName, value: d.total,
        percentage: total > 0 ? (d.total / total) * 100 : 0,
        color: d.color || PALETA[filtered.indexOf(d) % PALETA.length],
      })),
      total,
    };
  };

  const { slices, total } = getChartData();

  const handleSlicePress = (slice: { id: string; label: string; color: string }) => {
    navigation.navigate('CategoryDrillDown', {
      categoryId: slice.id,
      categoryName: slice.label,
      color: slice.color,
      startDate: periodStart,
      endDate: periodEnd,
    });
  };

  if (cargando) return <Spinner fullScreen />;

  if (error) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background }]}>
        <EstadoError mensaje={error} onReintentar={cargar} />
      </View>
    );
  }

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />}
    >
      <AppText variant="subtitle" style={styles.title}>Resumen</AppText>

      <PeriodNavigator
        periodStart={periodStart}
        periodEnd={periodEnd}
        onPrevious={handlePrevPeriod}
        onNext={handleNextPeriod}
        canGoNext={canGoNext}
      />

      <View style={styles.toggleContainer}>
        <CapsuleToggle
          options={[
            { label: 'Presupuestado', value: 'PLAN', color: colors.primary },
            { label: 'Real', value: 'REAL', color: colors.primary },
          ]}
          selected={fuente}
          onChange={(v) => setFuente(v as Fuente)}
        />
      </View>

      <View style={styles.toggleContainer}>
        <CapsuleToggle
          options={[
            { label: 'Gastos', value: 'EXPENSE', color: colors.expense },
            { label: 'Balance', value: 'BALANCE', color: colors.primary },
            { label: 'Ingresos', value: 'INCOME', color: colors.income },
          ]}
          selected={viewMode}
          onChange={(v) => setViewMode(v as ViewMode)}
        />
      </View>

      {slices.length > 0 ? (
        <DonutChart
          data={slices}
          onSlicePress={handleSlicePress}
          {...(viewMode === 'BALANCE' ? {
            centerLines: fuente === 'PLAN' ? [
              { label: 'Ingresos ', value: totalIngresosPlan, color: colors.income },
              { label: 'Presup. ', value: totalPresupuestado, color: colors.expense },
              { label: 'Queda ', value: totalIngresosPlan - totalPresupuestado, color: colors.primary, bold: true },
            ] : [
              { label: 'Ingresos ', value: summary?.totalIncome ?? 0, color: colors.income },
              { label: 'Gastos ', value: summary?.totalExpense ?? 0, color: colors.expense },
              { label: 'Balance ', value: summary?.balance ?? 0, color: colors.primary, bold: true },
            ],
          } : {
            centerLabel: viewMode === 'EXPENSE' ? (fuente === 'PLAN' ? 'Presupuesto' : 'Gastos') : 'Ingresos',
            centerValue: total,
          })}
        />
      ) : (
        <EmptyState
          title="Sin datos"
          message={fuente === 'PLAN'
            ? `No hay ${viewMode === 'INCOME' ? 'ingresos programados' : 'presupuesto'} en este periodo`
            : `No hay ${viewMode === 'INCOME' ? 'ingresos' : 'gastos'} confirmados en este periodo. Mira "Presupuestado".`}
          icon="📊"
        />
      )}

      {/* Budget vs Real - solo en modo Gastos o Balance */}
      {/* Presupuesto vs real contra lo ASIGNADO. Antes el "presupuesto" de
          cada barra era lo pendiente mas lo confirmado, asi que lo real nunca
          podia pasarse: comparaba lo gastado consigo mismo. */}
      {(viewMode === 'EXPENSE' || viewMode === 'BALANCE') && asignado.length > 0 && (
        <BudgetVsReal
          categories={(() => {
            const reales = categoryData.filter((c) => c.categoryType === 'EXPENSE');
            const faltan = asignado
              .filter((a) => !reales.some((c) => String(c.categoryId) === String(a.categoryId)))
              .map((a) => ({ categoryId: String(a.categoryId), categoryName: a.categoryName, categoryType: 'EXPENSE' as const,
                total: 0, percentage: 0, color: a.categoryColor, icon: a.categoryIcon }));
            return [...reales, ...faltan];
          })()}
          budgetByCategory={Object.fromEntries(asignado.map((a) => [String(a.categoryId), Number(a.allocatedAmount || 0)]))}
        />
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.md, paddingBottom: spacing.xxl },
  title: { marginBottom: spacing.sm },
  toggleContainer: { marginVertical: spacing.md },
});

export default SummaryScreen;
