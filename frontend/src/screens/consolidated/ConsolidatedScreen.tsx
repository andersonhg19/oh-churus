import React, { useState, useCallback } from 'react';
import {
  View,
  StyleSheet,
  ScrollView,
  RefreshControl,
} from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Spinner from '../../components/atoms/Spinner';
import PeriodNavigator from '../../components/molecules/PeriodNavigator';
import CapsuleToggle from '../../components/molecules/CapsuleToggle';
import { spacing } from '../../theme';
import { dashboardService } from '../../services/dashboardService';
import { useFocusEffect } from '@react-navigation/native';
import { formatCurrency } from '../../utils/format';
import { getStartOfPeriod, getEndOfPeriod, navigatePeriod } from '../../utils/periodUtils';

type TabValue = 'shared' | 'personal' | 'total';

const ConsolidatedScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();

  const budgetDay = user?.budgetStartDay || 1;
  const [periodStart, setPeriodStart] = useState(() => getStartOfPeriod(budgetDay, new Date()));
  const [periodEnd, setPeriodEnd] = useState(() => getEndOfPeriod(budgetDay, getStartOfPeriod(budgetDay, new Date())));

  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [activeTab, setActiveTab] = useState<TabValue>('total');

  const fetchData = useCallback(async (refDate: string) => {
    if (!user) return;
    try {
      const res = await dashboardService.getConsolidated(user.userId, budgetDay, refDate);
      if (res.correct) {
        setData(res.object);
      } else {
        showToast('error', 'Error', res.message || 'No se pudo cargar el consolidado');
      }
    } catch {
      showToast('error', 'Error', 'No se pudo cargar el reporte consolidado');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [user, budgetDay, showToast]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      fetchData(periodStart);
    }, [fetchData, periodStart]),
  );

  const onRefresh = () => {
    setRefreshing(true);
    fetchData(periodStart);
  };

  const handlePeriodChange = (direction: 'prev' | 'next') => {
    const { start, end } = navigatePeriod(periodStart, budgetDay, direction);
    setPeriodStart(start);
    setPeriodEnd(end);
  };

  const currentPeriodStart = getStartOfPeriod(budgetDay, new Date());
  const canGoNext = periodStart < currentPeriodStart;

  const tabOptions = [
    { label: 'Conjunto', value: 'shared', color: colors.primary },
    { label: 'Personal', value: 'personal', color: colors.primary },
    { label: 'Total', value: 'total', color: colors.primary },
  ];

  if (loading) return <Spinner fullScreen />;

  // Extract values based on active tab
  const getTabValues = () => {
    if (!data) return { income: 0, expense: 0, balance: 0, extra: null };
    switch (activeTab) {
      case 'shared':
        return {
          income: data.shared?.income ?? 0,
          expense: data.shared?.expense ?? 0,
          balance: data.shared?.balance ?? 0,
          extra: data.shared?.transfersToPersonal ? `Transferencias a personal: ${formatCurrency(data.shared.transfersToPersonal)}` : null,
        };
      case 'personal':
        return {
          income: (data.personal?.incomeFromTransfer ?? 0) + (data.personal?.incomeOther ?? 0),
          expense: data.personal?.expense ?? 0,
          balance: data.personal?.balance ?? 0,
          extra: data.personal?.incomeFromTransfer ? `Ingreso por transferencia: ${formatCurrency(data.personal.incomeFromTransfer)}` : null,
        };
      case 'total':
      default:
        return {
          income: data.total?.income ?? 0,
          expense: data.total?.expense ?? 0,
          balance: data.total?.balance ?? 0,
          extra: data.total?.status ?? null,
        };
    }
  };

  const tabValues = getTabValues();
  const { income, expense, balance } = tabValues;
  const extra = (tabValues as any).extra;
  const isDeficit = balance < 0;
  const balanceColor = isDeficit ? colors.expense : colors.income;
  const statusLabel = data?.total?.status === 'DEFICIT' ? 'DEFICIT' : 'SUPERAVIT';
  const statusColor = isDeficit ? colors.expense : colors.income;

  // Budget data (total tab only)
  const totalBudgeted = data?.budget?.totalBudgeted ?? 0;
  const totalActual = data?.budget?.totalActualExpense ?? 0;
  const budgetExecPct = data?.budget?.executionPct ?? 0;
  const budgetBarWidth = Math.min(budgetExecPct, 100);
  const budgetBarColor = budgetExecPct > 100 ? colors.expense : colors.income;

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />}
    >
      <PeriodNavigator
        periodStart={periodStart}
        periodEnd={periodEnd}
        onPrevious={() => handlePeriodChange('prev')}
        onNext={() => handlePeriodChange('next')}
        canGoNext={canGoNext}
      />

      <View style={styles.toggleContainer}>
        <CapsuleToggle
          options={tabOptions}
          selected={activeTab}
          onChange={(val) => setActiveTab(val as TabValue)}
        />
      </View>

      {/* Income Card */}
      <Card style={styles.metricCard}>
        <View style={styles.metricHeader}>
          <View style={[styles.metricIndicator, { backgroundColor: colors.income }]} />
          <AppText variant="body" color={colors.textSecondary}>Ingresos</AppText>
        </View>
        <AppText variant="title" color={colors.income} style={styles.metricValue}>
          {formatCurrency(income)}
        </AppText>
      </Card>

      {/* Expense Card */}
      <Card style={styles.metricCard}>
        <View style={styles.metricHeader}>
          <View style={[styles.metricIndicator, { backgroundColor: colors.expense }]} />
          <AppText variant="body" color={colors.textSecondary}>Gastos</AppText>
        </View>
        <AppText variant="title" color={colors.expense} style={styles.metricValue}>
          {formatCurrency(expense)}
        </AppText>
      </Card>

      {/* Balance Card */}
      <Card style={styles.metricCard}>
        <View style={styles.metricHeader}>
          <View style={[styles.metricIndicator, { backgroundColor: balanceColor }]} />
          <AppText variant="body" color={colors.textSecondary}>Balance</AppText>
        </View>
        <View style={styles.balanceRow}>
          <AppText variant="title" color={balanceColor} style={styles.metricValue}>
            {balance < 0 ? '-' : ''}{formatCurrency(balance)}
          </AppText>
          <View style={[styles.statusBadge, { backgroundColor: statusColor }]}>
            <AppText variant="caption" color="#FFF" style={styles.statusText}>
              {statusLabel}
            </AppText>
          </View>
        </View>
      </Card>

      {/* Tab-specific extra info */}
      {activeTab === 'shared' && (data.shared?.transfersToPersonal ?? 0) > 0 && (
        <Card style={styles.extraCard}>
          <AppText variant="body" color={colors.textSecondary}>
            Transferencias a personal
          </AppText>
          <AppText variant="subtitle" color={colors.primary} style={styles.extraValue}>
            {formatCurrency(data.shared.transfersToPersonal)}
          </AppText>
        </Card>
      )}

      {activeTab === 'personal' && (data.personal?.incomeFromTransfer ?? 0) > 0 && (
        <Card style={styles.extraCard}>
          <AppText variant="body" color={colors.textSecondary}>
            Ingreso por transferencia
          </AppText>
          <AppText variant="subtitle" color={colors.income} style={styles.extraValue}>
            {formatCurrency(data.personal.incomeFromTransfer)}
          </AppText>
        </Card>
      )}

      {/* Budget section - Total tab only */}
      {activeTab === 'total' && totalBudgeted > 0 && (
        <Card style={styles.budgetCard}>
          <AppText variant="subtitle" style={styles.budgetTitle}>Ejecucion presupuestal</AppText>

          <View style={styles.budgetRow}>
            <AppText variant="body" color={colors.textSecondary}>Presupuestado</AppText>
            <AppText variant="body">{formatCurrency(totalBudgeted)}</AppText>
          </View>
          <View style={styles.budgetRow}>
            <AppText variant="body" color={colors.textSecondary}>Ejecutado</AppText>
            <AppText variant="body">{formatCurrency(totalActual)}</AppText>
          </View>

          {/* Progress bar */}
          <View style={styles.budgetBarContainer}>
            <View style={[styles.budgetBarBg, { backgroundColor: colors.border }]}>
              <View
                style={[
                  styles.budgetBarFill,
                  { width: `${budgetBarWidth}%`, backgroundColor: budgetBarColor },
                ]}
              />
            </View>
            <AppText variant="caption" color={colors.textSecondary} style={styles.budgetPctText}>
              {Math.round(budgetExecPct)}%
            </AppText>
          </View>

          <View style={styles.budgetRow}>
            <AppText variant="body" color={colors.textSecondary}>Varianza</AppText>
            <AppText
              variant="body"
              color={totalBudgeted - totalActual >= 0 ? colors.income : colors.expense}
              style={{ fontWeight: '700' }}
            >
              {totalBudgeted - totalActual >= 0 ? '+' : '-'}
              {formatCurrency(Math.abs(totalBudgeted - totalActual))}
            </AppText>
          </View>
        </Card>
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.md, paddingBottom: spacing.xxl },
  toggleContainer: {
    marginBottom: spacing.md,
  },
  metricCard: {
    marginBottom: spacing.sm,
  },
  metricHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: spacing.xs,
  },
  metricIndicator: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: spacing.sm,
  },
  metricValue: {
    fontWeight: '700',
  },
  balanceRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  statusBadge: {
    paddingHorizontal: spacing.sm + 4,
    paddingVertical: spacing.xs,
    borderRadius: 12,
  },
  statusText: {
    fontWeight: '700',
    fontSize: 12,
  },
  extraCard: {
    marginBottom: spacing.sm,
  },
  extraValue: {
    marginTop: spacing.xs,
    fontWeight: '700',
  },
  budgetCard: {
    marginTop: spacing.sm,
    marginBottom: spacing.md,
  },
  budgetTitle: {
    marginBottom: spacing.sm,
  },
  budgetRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.xs,
  },
  budgetBarContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: spacing.sm,
    gap: spacing.sm,
  },
  budgetBarBg: {
    flex: 1,
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
  },
  budgetBarFill: {
    height: 8,
    borderRadius: 4,
  },
  budgetPctText: {
    minWidth: 36,
    textAlign: 'right',
  },
});

export default ConsolidatedScreen;
