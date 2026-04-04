import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Spinner from '../../components/atoms/Spinner';
import EmptyState from '../../components/molecules/EmptyState';
import PeriodNavigator from '../../components/molecules/PeriodNavigator';
import DonutChart from '../../components/molecules/DonutChart';
import CapsuleToggle from '../../components/molecules/CapsuleToggle';
import BudgetVsReal from '../../components/molecules/BudgetVsReal';
import { spacing } from '../../theme';
import { dashboardService } from '../../services/dashboardService';
import { movementService } from '../../services/movementService';
import { CategorySummary, DashboardSummary, Movement } from '../../types';
import { getStartOfPeriod, getEndOfPeriod, navigatePeriod } from '../../utils/periodUtils';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type ViewMode = 'EXPENSE' | 'INCOME' | 'BALANCE';

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
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const periodEnd = getEndOfPeriod(budgetDay, periodStart);

  const fetchData = useCallback(async (refDate: string) => {
    if (!user) return;
    try {
      // Fetch independently so one failure doesn't block others
      const [catRes, sumRes] = await Promise.all([
        dashboardService.getByCategory(user.userId, budgetDay, refDate),
        dashboardService.getSummary(user.userId, budgetDay, refDate),
      ]);
      if (catRes.correct && catRes.object) setCategoryData(catRes.object);
      if (sumRes.correct && sumRes.object) setSummary(sumRes.object);

      try {
        const pStart = getStartOfPeriod(budgetDay, new Date(refDate));
        const pEnd = getEndOfPeriod(budgetDay, pStart);
        const movRes = await movementService.getAll({ userId: user.userId, startDate: pStart, endDate: pEnd, page: 0, size: 200 });
        if (movRes.correct && movRes.object) setAllMovements(movRes.object.list || []);
      } catch { /* movements fetch is optional for budget bars */ }
    } catch {
      // silent
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [user, budgetDay]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      fetchData(periodStart);
    }, [fetchData, periodStart]),
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
  const canGoNext = periodStart < currentPeriodStart;

  // Filtrar data según modo
  const getChartData = () => {
    if (viewMode === 'INCOME') {
      const filtered = categoryData.filter(d => d.categoryType === 'INCOME');
      const total = filtered.reduce((s, d) => s + d.total, 0);
      return {
        slices: filtered.map(d => ({
          id: String(d.categoryId), label: d.categoryName, value: d.total,
          percentage: total > 0 ? (d.total / total) * 100 : 0,
          color: d.color || colors.income,
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
        color: d.color || colors.expense,
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

  if (loading) return <Spinner fullScreen />;

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); fetchData(periodStart); }} tintColor={colors.primary} />}
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
            centerLines: [
              { label: 'Ingresos ', value: summary?.totalIncome ?? 0, color: colors.income },
              { label: 'Gastos ', value: summary?.totalExpense ?? 0, color: colors.expense },
              { label: 'Balance ', value: summary?.balance ?? 0, color: colors.primary, bold: true },
            ],
          } : {
            centerLabel: viewMode === 'EXPENSE' ? 'Gastos' : 'Ingresos',
            centerValue: total,
          })}
        />
      ) : (
        <EmptyState
          title="Sin datos"
          message={`No hay ${viewMode === 'INCOME' ? 'ingresos' : 'gastos'} en este periodo`}
          icon="📊"
        />
      )}

      {/* Budget vs Real - solo en modo Gastos o Balance */}
      {(viewMode === 'EXPENSE' || viewMode === 'BALANCE') && categoryData.length > 0 && (
        <BudgetVsReal
          categories={categoryData}
          budgetByCategory={(() => {
            const budget: { [key: string]: number } = {};
            allMovements.filter(m => !m.confirmed).forEach(m => {
              const key = String(m.categoryId);
              budget[key] = (budget[key] || 0) + (m.amount || 0);
            });
            // Add confirmed amounts to budget too (total = confirmed + pending)
            allMovements.filter(m => m.confirmed).forEach(m => {
              const key = String(m.categoryId);
              budget[key] = (budget[key] || 0) + (m.amount || 0);
            });
            return budget;
          })()}
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
