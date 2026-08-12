import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl, Modal, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import Card from '../../components/atoms/Card';
import StatCard from '../../components/molecules/StatCard';
import MovementItem from '../../components/molecules/MovementItem';
import EmptyState from '../../components/molecules/EmptyState';
import EstadoError from '../../components/molecules/EstadoError';
import PeriodNavigator from '../../components/molecules/PeriodNavigator';
import Spinner from '../../components/atoms/Spinner';
import { spacing } from '../../theme';
import { dashboardService } from '../../services/dashboardService';
import { movementService } from '../../services/movementService';
import { DashboardSummary, Movement } from '../../types';
import { useCarga, exigir } from '../../hooks/useCarga';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { formatCurrency } from '../../utils/format';
import { getStartOfPeriod, getEndOfPeriod, navigatePeriod } from '../../utils/periodUtils';

const DashboardScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const rootNavigation = useNavigation<any>();
  const { user } = useAuth();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [splitSummary, setSplitSummary] = useState<any>(null);
  const [pending, setPending] = useState<Movement[]>([]);
  const [viewMode, setViewMode] = useState<'total' | 'shared' | 'personal'>('total');

  // Confirm modal state
  const [confirmItem, setConfirmItem] = useState<Movement | null>(null);
  const [confirmAmount, setConfirmAmount] = useState('');

  const budgetDay = user?.budgetStartDay || 1;
  const [periodStart, setPeriodStart] = useState(() => getStartOfPeriod(budgetDay, new Date()));
  const [periodEnd, setPeriodEnd] = useState(() => getEndOfPeriod(budgetDay, getStartOfPeriod(budgetDay, new Date())));

  const traerPanel = useCallback(async () => {
    if (!user) return;
    const [summaryRes, pendingRes, splitRes] = await Promise.all([
      dashboardService.getSummary(user.userId, budgetDay, periodStart),
      dashboardService.getPending(user.userId, budgetDay, periodStart),
      dashboardService.getSplitSummary(user.userId, budgetDay, periodStart),
    ]);
    // Un correct:false aqui pintaba balance $0 y "Todo al dia": exactamente lo
    // mismo que un usuario nuevo sin datos. Ahora se cuenta lo que paso.
    setSummary(exigir(summaryRes) || null);
    setPending(exigir(pendingRes) || []);
    setSplitSummary(exigir(splitRes) || null);
  }, [user, budgetDay, periodStart]);

  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traerPanel);

  useFocusEffect(
    useCallback(() => {
      cargar();
    }, [cargar]),
  );

  const openConfirmModal = (item: Movement) => {
    setConfirmItem(item);
    setConfirmAmount(String(item.amount || 0));
  };

  const handleConfirm = async () => {
    if (!confirmItem) return;
    try {
      const originalAmount = confirmItem.amount || 0;
      const newAmount = parseFloat(confirmAmount) || originalAmount;
      const adjustedAmount = newAmount !== originalAmount ? newAmount : undefined;
      await movementService.confirm(String(confirmItem.id), adjustedAmount);
      showToast('success', 'Confirmado', confirmItem.description || 'Movimiento confirmado');
      setConfirmItem(null);
      cargar();
    } catch {
      showToast('error', 'Error', 'No se pudo confirmar');
    }
  };

  const handlePeriodChange = (direction: 'prev' | 'next') => {
    const { start, end } = navigatePeriod(periodStart, budgetDay, direction);
    setPeriodStart(start);
    setPeriodEnd(end);
  };

  const currentPeriodStart = getStartOfPeriod(budgetDay, new Date());
  const canGoNext = periodStart < currentPeriodStart;

  if (cargando) return <Spinner fullScreen />;

  if (error) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background }]}>
        <EstadoError mensaje={error} onReintentar={cargar} />
      </View>
    );
  }

  const balanceColor = (summary?.balance ?? 0) >= 0 ? colors.income : colors.expense;

  return (
    <>
      <ScrollView
        style={[styles.container, { backgroundColor: colors.background }]}
        contentContainerStyle={styles.content}
        refreshControl={<RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />}
      >
        <AppText variant="subtitle" style={styles.greeting}>
          Hola, {user?.name || 'Usuario'}! 🐿️
        </AppText>

        <PeriodNavigator
          periodStart={periodStart}
          periodEnd={periodEnd}
          onPrevious={() => handlePeriodChange('prev')}
          onNext={() => handlePeriodChange('next')}
          canGoNext={canGoNext}
        />

        {/* View mode toggle */}
        {splitSummary && (
          <View style={styles.toggleRow}>
            {(['total', 'shared', 'personal'] as const).map(mode => (
              <TouchableOpacity
                key={mode}
                style={[styles.toggleBtn, viewMode === mode && { backgroundColor: colors.primary }]}
                onPress={() => setViewMode(mode)}
              >
                <AppText variant="caption" style={{
                  color: viewMode === mode ? '#FFF' : colors.textSecondary,
                  fontWeight: viewMode === mode ? '700' : '400',
                }}>
                  {mode === 'total' ? 'Total' : mode === 'shared' ? 'Conjunto' : 'Personal'}
                </AppText>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {(() => {
          const s = splitSummary;
          const income = viewMode === 'personal' ? s?.personalIncome : viewMode === 'shared' ? s?.sharedIncome : summary?.totalIncome ?? 0;
          const expense = viewMode === 'personal' ? s?.personalExpense : viewMode === 'shared' ? s?.sharedExpense : summary?.totalExpense ?? 0;
          const bal = viewMode === 'personal' ? s?.personalBalance : viewMode === 'shared' ? s?.sharedBalance : summary?.balance ?? 0;
          const pCount = viewMode === 'personal' ? s?.personalPendingCount : viewMode === 'shared' ? s?.sharedPendingCount : summary?.pendingCount ?? 0;
          const bColor = (bal ?? 0) >= 0 ? colors.income : colors.expense;

          return (
            <>
              <View style={[styles.balanceCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <AppText variant="caption" color={colors.textSecondary}>
                  Balance {viewMode === 'shared' ? 'Conjunto' : viewMode === 'personal' ? 'Personal' : 'Total'}
                </AppText>
                <AppText variant="title" color={bColor} style={styles.balanceValue}>
                  {formatCurrency(bal ?? 0)}
                </AppText>
              </View>

              <View style={styles.statsRow}>
                <View style={styles.statHalf}>
                  <StatCard title="Ingresos" value={income ?? 0} icon="📈" color={colors.income} />
                </View>
                <View style={styles.statHalf}>
                  <StatCard title="Gastos" value={expense ?? 0} icon="📉" color={colors.expense} />
                </View>
              </View>
              <View style={styles.statsRow}>
                <View style={styles.statHalf}>
                  <StatCard title="Presupuesto" value={viewMode === 'total' ? (summary?.budgetTotal ?? 0) : (expense ?? 0)} icon="📊" color={colors.primary} />
                </View>
                <View style={styles.statHalf}>
                  <StatCard
                    title="Pendientes"
                    value={pCount ?? 0}
                    icon="⏳"
                    color={colors.pending}
                    isCurrency={false}
                    subtitle={summary?.pendingAmount ? formatCurrency(summary.pendingAmount) : undefined}
                  />
                </View>
              </View>
            </>
          );
        })()}

        <Card style={styles.allMovementsCard} onPress={() => rootNavigation.navigate('MovementsList')}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <AppText variant="body">🔍 Ver todos los movimientos</AppText>
            <AppText variant="body" color={colors.textMuted}>›</AppText>
          </View>
        </Card>

        {pending.length > 0 && (
          <>
            <AppText variant="subtitle" style={styles.sectionTitle}>
              Por confirmar ({pending.length})
            </AppText>
            {pending.map((item) => (
              <View key={item.id} style={styles.pendingRow}>
                <View style={{ flex: 1 }}>
                  <MovementItem
                    movement={item}
                    onPress={() => rootNavigation.navigate('MovementFormModal', { movement: item })}
                    onConfirm={() => openConfirmModal(item)}
                    onSwipeConfirm={async () => {
                      try {
                        await movementService.confirm(String(item.id));
                        showToast('success', 'Confirmado', item.description || 'Movimiento confirmado');
                        cargar();
                      } catch { showToast('error', 'Error', 'No se pudo confirmar'); }
                    }}
                  />
                </View>
                <TouchableOpacity
                  style={[styles.subGastoBtn, { backgroundColor: colors.primary + '20', borderColor: colors.primary }]}
                  onPress={() => rootNavigation.navigate('MovementFormModal', { parentMovement: item })}
                >
                  <AppText variant="caption" color={colors.primary} style={{ fontWeight: '700' }}>+</AppText>
                </TouchableOpacity>
              </View>
            ))}
          </>
        )}

        {pending.length === 0 && (
          <EmptyState title="Todo al dia" message="No tienes pagos pendientes por confirmar" icon="✅" />
        )}
      </ScrollView>

      {/* Modal de confirmación con monto ajustable */}
      <Modal visible={!!confirmItem} transparent animationType="fade" onRequestClose={() => setConfirmItem(null)}>
        <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setConfirmItem(null)}>
          <TouchableOpacity activeOpacity={1} style={[styles.modalContent, { backgroundColor: colors.surface }]}>
            <AppText variant="subtitle" style={styles.modalTitle}>Confirmar movimiento</AppText>
            <AppText variant="body" color={colors.textSecondary} style={styles.modalDesc}>
              {confirmItem?.description || 'Sin descripcion'}
            </AppText>

            <Input
              label="Monto (ajustable)"
              value={confirmAmount}
              onChangeText={setConfirmAmount}
              keyboardType="numeric"
              placeholder="0"
            />
            <AppText variant="caption" color={colors.textMuted} style={styles.modalHint}>
              Cambia el monto si el valor real fue diferente al presupuestado
            </AppText>

            <View style={styles.modalActions}>
              <Button title="Cancelar" onPress={() => setConfirmItem(null)} variant="outline" style={styles.modalBtn} />
              <Button title="Confirmar" onPress={handleConfirm} style={styles.modalBtn} />
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.md, paddingBottom: spacing.xxl },
  greeting: { marginBottom: spacing.sm },
  toggleRow: {
    flexDirection: 'row', gap: 8, marginBottom: spacing.sm,
  },
  toggleBtn: {
    flex: 1, paddingVertical: 8, borderRadius: 12, alignItems: 'center',
    backgroundColor: 'rgba(255,255,255,0.05)',
  },
  balanceCard: {
    padding: spacing.md,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  balanceValue: { marginVertical: 2 },
  statsRow: {
    flexDirection: 'row',
    gap: spacing.xs,
  },
  statHalf: {
    flex: 1,
    minWidth: 0,
  },
  allMovementsCard: {
    marginTop: spacing.sm,
    marginBottom: spacing.xs,
  },
  sectionTitle: {
    marginTop: spacing.sm,
    marginBottom: spacing.sm,
  },
  pendingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
  },
  subGastoBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: spacing.xl,
  },
  modalContent: {
    width: '100%',
    maxWidth: 400,
    borderRadius: 20,
    padding: spacing.xl,
  },
  modalTitle: { marginBottom: spacing.xs },
  modalDesc: { marginBottom: spacing.md },
  modalHint: { marginBottom: spacing.md, fontStyle: 'italic' },
  modalActions: {
    flexDirection: 'row',
    gap: spacing.sm,
  },
  modalBtn: { flex: 1 },
});

export default DashboardScreen;
