import React, { useState, useCallback } from 'react';
import {
  View,
  StyleSheet,
  ScrollView,
  RefreshControl,
  Modal,
  TouchableOpacity,
} from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import Card from '../../components/atoms/Card';
import Spinner from '../../components/atoms/Spinner';
import EmptyState from '../../components/molecules/EmptyState';
import PeriodNavigator from '../../components/molecules/PeriodNavigator';
import { spacing } from '../../theme';
import { budgetAllocationService } from '../../services/budgetAllocationService';
import { categoryService } from '../../services/categoryService';
import { movementService } from '../../services/movementService';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { formatCurrency } from '../../utils/format';
import { confirmarBorrado } from '../../utils/confirmar';
import { getStartOfPeriod, getEndOfPeriod, navigatePeriod } from '../../utils/periodUtils';
import { CategoryTree } from '../../types';

interface AllocationItem {
  id: number;
  categoryId: number;
  categoryName: string;
  categoryIcon?: string;
  categoryColor?: string;
  amount: number;
  actualSpent: number;
  percentage: number;
  variance: number;
}

const BudgetScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();

  const budgetDay = user?.budgetStartDay || 1;
  const [periodStart, setPeriodStart] = useState(() => getStartOfPeriod(budgetDay, new Date()));
  const [periodEnd, setPeriodEnd] = useState(() => getEndOfPeriod(budgetDay, getStartOfPeriod(budgetDay, new Date())));

  const [allocations, setAllocations] = useState<AllocationItem[]>([]);
  const [summaryData, setSummaryData] = useState<any>(null);
  const [categories, setCategories] = useState<CategoryTree[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const navigation = useNavigation<any>();

  // Add allocation modal
  const [showAddModal, setShowAddModal] = useState(false);
  const [newAmount, setNewAmount] = useState('');
  const [newNotes, setNewNotes] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);

  // Transfer modal
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [transferAmount, setTransferAmount] = useState('');
  const [transferDescription, setTransferDescription] = useState('');
  const [transferFromId, setTransferFromId] = useState<number | null>(null);
  const [transferToId, setTransferToId] = useState<number | null>(null);
  const [transferring, setTransferring] = useState(false);

  const fetchData = useCallback(async (refDate: string) => {
    if (!user) return;
    try {
      const [listRes, summaryRes, catRes] = await Promise.all([
        budgetAllocationService.list(user.userId, budgetDay, refDate),
        budgetAllocationService.summary(user.userId, budgetDay, refDate),
        categoryService.getTree(user.userId),
      ]);
      if (listRes.correct) {
        setAllocations(listRes.object || []);
      }
      if (summaryRes.correct) {
        setSummaryData(summaryRes.object);
      }
      if (catRes.correct) {
        setCategories(catRes.object || []);
      }
    } catch {
      showToast('error', 'Error', 'No se pudieron cargar los presupuestos');
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

  // Flatten all expense categories (parents + children) for allocation
  const flattenCategories = (trees: CategoryTree[]): CategoryTree[] => {
    const result: CategoryTree[] = [];
    for (const cat of trees) {
      if (cat.type === 'EXPENSE') {
        result.push(cat);
      }
      if (cat.children?.length) {
        result.push(...flattenCategories(cat.children));
      }
    }
    return result;
  };

  const allExpenseCategories = flattenCategories(categories);
  const allocatedCategoryIds = new Set(allocations.map(a => a.categoryId));
  const availableCategories = allExpenseCategories.filter(c => !allocatedCategoryIds.has(Number(c.id)));

  // Shared categories (source for transfers)
  const sharedCategories = allExpenseCategories.filter(c => c.shared);
  // Personal categories (destination for transfers)
  const personalCategories = allExpenseCategories.filter(c => !c.shared);

  const handleSaveAllocation = async () => {
    if (!user || !selectedCategoryId || !newAmount) return;
    const amount = parseFloat(newAmount);
    if (isNaN(amount) || amount <= 0) {
      showToast('error', 'Error', 'Ingresa un monto valido');
      return;
    }
    setSaving(true);
    try {
      const res = await budgetAllocationService.save({
        userId: user.userId,
        categoryId: selectedCategoryId,
        amount,
        budgetStartDay: budgetDay,
        notes: newNotes || undefined,
        referenceDate: periodStart,
      });
      if (res.correct) {
        showToast('success', 'Guardado', 'Presupuesto asignado correctamente');
        setShowAddModal(false);
        setNewAmount('');
        setNewNotes('');
        setSelectedCategoryId(null);
        fetchData(periodStart);
      } else {
        showToast('error', 'Error', res.message || 'No se pudo guardar');
      }
    } catch {
      showToast('error', 'Error', 'No se pudo guardar el presupuesto');
    } finally {
      setSaving(false);
    }
  };

  // El presupuesto de una categoria se borraba al primer toque, en una fila
  // llena de otras filas: demasiado facil de pulsar sin querer.
  const handleDeleteAllocation = (id: number, categoria: string) =>
    confirmarBorrado(`el presupuesto de "${categoria}"`, () => { borrarAsignacion(id); });

  const borrarAsignacion = async (id: number) => {
    try {
      const res = await budgetAllocationService.delete(id);
      if (res.correct) {
        showToast('success', 'Eliminado', 'Presupuesto eliminado');
        fetchData(periodStart);
      } else {
        showToast('error', 'Error', res.message || 'No se pudo eliminar');
      }
    } catch {
      showToast('error', 'Error', 'No se pudo eliminar');
    }
  };

  const handleTransfer = async () => {
    if (!user || !transferFromId || !transferToId || !transferAmount) return;
    const amount = parseFloat(transferAmount);
    if (isNaN(amount) || amount <= 0) {
      showToast('error', 'Error', 'Ingresa un monto valido');
      return;
    }
    setTransferring(true);
    try {
      const res = await movementService.transfer({
        userId: user.userId,
        fromCategoryId: transferFromId,
        toCategoryId: transferToId,
        amount,
        description: transferDescription || undefined,
      });
      if (res.correct) {
        showToast('success', 'Transferido', 'Transferencia realizada correctamente');
        setShowTransferModal(false);
        setTransferAmount('');
        setTransferDescription('');
        setTransferFromId(null);
        setTransferToId(null);
        fetchData(periodStart);
      } else {
        showToast('error', 'Error', res.message || 'No se pudo transferir');
      }
    } catch {
      showToast('error', 'Error', 'No se pudo realizar la transferencia');
    } finally {
      setTransferring(false);
    }
  };

  const openAddModal = () => {
    setNewAmount('');
    setNewNotes('');
    setSelectedCategoryId(null);
    setShowAddModal(true);
  };

  const openTransferModal = () => {
    setTransferAmount('');
    setTransferDescription('');
    setTransferFromId(sharedCategories.length > 0 ? Number(sharedCategories[0].id) : null);
    setTransferToId(null);
    setShowTransferModal(true);
  };

  if (loading) return <Spinner fullScreen />;

  const totalBudgeted = summaryData?.totalBudgeted ?? allocations.reduce((sum: number, a: AllocationItem) => sum + a.amount, 0);
  const totalActual = summaryData?.totalActual ?? allocations.reduce((sum: number, a: AllocationItem) => sum + a.actualSpent, 0);
  const totalVariance = totalBudgeted - totalActual;

  return (
    <>
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

        {/* Header + Add button */}
        <View style={styles.sectionHeader}>
          <AppText variant="subtitle">Presupuestos</AppText>
          <TouchableOpacity
            style={[styles.addButton, { backgroundColor: colors.primary }]}
            onPress={openAddModal}
          >
            <AppText variant="subtitle" color="#FFF">+</AppText>
          </TouchableOpacity>
        </View>

        {allocations.length === 0 ? (
          <EmptyState
            title="Sin presupuestos"
            message="Agrega presupuestos para controlar tus gastos por categoria"
          />
        ) : (
          allocations.map((alloc) => {
            const pct = alloc.amount > 0 ? (alloc.actualSpent / alloc.amount) * 100 : 0;
            const isOver = alloc.actualSpent > alloc.amount;
            const barColor = isOver ? colors.expense : colors.income;
            const barWidth = Math.min(pct, 100);
            const variance = alloc.amount - alloc.actualSpent;

            return (
              <Card key={alloc.id} style={styles.allocationCard}>
                <View style={styles.allocationHeader}>
                  <View style={styles.categoryInfo}>
                    {alloc.categoryColor && (
                      <View style={[styles.colorDot, { backgroundColor: alloc.categoryColor }]} />
                    )}
                    {alloc.categoryIcon ? (
                      <AppText variant="body" style={styles.categoryIcon}>{alloc.categoryIcon}</AppText>
                    ) : null}
                    <AppText variant="body" style={styles.categoryName}>{alloc.categoryName}</AppText>
                  </View>
                  <TouchableOpacity onPress={() => handleDeleteAllocation(alloc.id, alloc.categoryName)}>
                    <AppText variant="caption" color={colors.expense}>Eliminar</AppText>
                  </TouchableOpacity>
                </View>

                {/* Progress bar */}
                <View style={[styles.progressBarBg, { backgroundColor: colors.border }]}>
                  <View
                    style={[
                      styles.progressBarFill,
                      { width: `${barWidth}%`, backgroundColor: barColor },
                    ]}
                  />
                </View>

                <View style={styles.allocationFooter}>
                  <AppText variant="caption" color={colors.textSecondary}>
                    {formatCurrency(alloc.actualSpent)} de {formatCurrency(alloc.amount)}
                  </AppText>
                  <AppText variant="caption" color={colors.textSecondary}>
                    {Math.round(pct)}%
                  </AppText>
                </View>

                <AppText
                  variant="caption"
                  color={variance >= 0 ? colors.income : colors.expense}
                  style={styles.varianceText}
                >
                  {variance >= 0
                    ? `+${formatCurrency(variance)} favorable`
                    : `-${formatCurrency(Math.abs(variance))} excedido`}
                </AppText>
              </Card>
            );
          })
        )}

        {/* Transfer section */}
        <View style={styles.sectionHeader}>
          <AppText variant="subtitle">Disponibilizar</AppText>
        </View>
        <Card style={styles.transferCard}>
          <AppText variant="body" color={colors.textSecondary} style={styles.transferDesc}>
            Transfiere fondos de categorias compartidas a categorias personales
          </AppText>
          <Button
            title="Nueva transferencia"
            onPress={openTransferModal}
            variant="outline"
            size="small"
          />
        </Card>

        {/* Summary */}
        <Card style={styles.summaryCard}>
          <AppText variant="subtitle" style={styles.summaryTitle}>Resumen</AppText>
          <View style={styles.summaryRow}>
            <AppText variant="body" color={colors.textSecondary}>Total presupuestado</AppText>
            <AppText variant="body">{formatCurrency(totalBudgeted)}</AppText>
          </View>
          <View style={styles.summaryRow}>
            <AppText variant="body" color={colors.textSecondary}>Total ejecutado</AppText>
            <AppText variant="body">{formatCurrency(totalActual)}</AppText>
          </View>
          <View style={[styles.divider, { backgroundColor: colors.border }]} />
          <View style={styles.summaryRow}>
            <AppText variant="body" color={colors.textSecondary}>Varianza</AppText>
            <AppText
              variant="body"
              color={totalVariance >= 0 ? colors.income : colors.expense}
              style={{ fontWeight: '700' }}
            >
              {totalVariance >= 0 ? '+' : ''}{formatCurrency(totalVariance)}
            </AppText>
          </View>
        </Card>

        {/* Consolidated button */}
        <Button
          title="Ver consolidado mensual"
          onPress={() => navigation.navigate('Consolidated')}
          variant="outline"
          style={{ marginTop: spacing.sm }}
        />
      </ScrollView>

      {/* Add Allocation Modal */}
      <Modal visible={showAddModal} transparent animationType="fade" onRequestClose={() => setShowAddModal(false)}>
        <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setShowAddModal(false)}>
          <TouchableOpacity activeOpacity={1} style={[styles.modalContent, { backgroundColor: colors.surface }]}>
            <AppText variant="subtitle" style={styles.modalTitle}>Nuevo presupuesto</AppText>

            <AppText variant="caption" color={colors.textSecondary} style={styles.modalLabel}>
              Categoria
            </AppText>
            <ScrollView style={styles.categoryList} nestedScrollEnabled>
              {availableCategories.length === 0 ? (
                <AppText variant="caption" color={colors.textMuted} style={styles.noCatText}>
                  Todas las categorias ya tienen presupuesto asignado
                </AppText>
              ) : (
                availableCategories.map((cat) => (
                  <TouchableOpacity
                    key={cat.id}
                    style={[
                      styles.categoryOption,
                      { borderColor: colors.border },
                      selectedCategoryId === Number(cat.id) && { borderColor: colors.primary, backgroundColor: 'rgba(232,168,56,0.1)' },
                    ]}
                    onPress={() => setSelectedCategoryId(Number(cat.id))}
                  >
                    <View style={styles.categoryOptionRow}>
                      {cat.color && <View style={[styles.colorDot, { backgroundColor: cat.color }]} />}
                      {cat.icon ? <AppText variant="body" style={styles.categoryIcon}>{cat.icon}</AppText> : null}
                      <AppText variant="body">{cat.name}</AppText>
                    </View>
                    {selectedCategoryId === Number(cat.id) && (
                      <AppText variant="body" color={colors.primary}>*</AppText>
                    )}
                  </TouchableOpacity>
                ))
              )}
            </ScrollView>

            <Input
              label="Monto"
              value={newAmount}
              onChangeText={setNewAmount}
              keyboardType="numeric"
              placeholder="0"
            />
            <Input
              label="Notas (opcional)"
              value={newNotes}
              onChangeText={setNewNotes}
              placeholder="Notas del presupuesto"
            />

            <View style={styles.modalActions}>
              <Button title="Cancelar" onPress={() => setShowAddModal(false)} variant="outline" style={styles.modalBtn} />
              <Button
                title="Guardar"
                onPress={handleSaveAllocation}
                loading={saving}
                disabled={!selectedCategoryId || !newAmount}
                style={styles.modalBtn}
              />
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>

      {/* Transfer Modal */}
      <Modal visible={showTransferModal} transparent animationType="fade" onRequestClose={() => setShowTransferModal(false)}>
        <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setShowTransferModal(false)}>
          <TouchableOpacity activeOpacity={1} style={[styles.modalContent, { backgroundColor: colors.surface }]}>
            <AppText variant="subtitle" style={styles.modalTitle}>Transferencia</AppText>

            <AppText variant="caption" color={colors.textSecondary} style={styles.modalLabel}>
              Origen (categoria compartida)
            </AppText>
            <ScrollView style={styles.categoryListSmall} nestedScrollEnabled>
              {sharedCategories.map((cat) => (
                <TouchableOpacity
                  key={cat.id}
                  style={[
                    styles.categoryOption,
                    { borderColor: colors.border },
                    transferFromId === Number(cat.id) && { borderColor: colors.primary, backgroundColor: 'rgba(232,168,56,0.1)' },
                  ]}
                  onPress={() => setTransferFromId(Number(cat.id))}
                >
                  <View style={styles.categoryOptionRow}>
                    {cat.color && <View style={[styles.colorDot, { backgroundColor: cat.color }]} />}
                    <AppText variant="body">{cat.name}</AppText>
                  </View>
                </TouchableOpacity>
              ))}
            </ScrollView>

            <AppText variant="caption" color={colors.textSecondary} style={styles.modalLabel}>
              Destino (categoria personal)
            </AppText>
            <ScrollView style={styles.categoryListSmall} nestedScrollEnabled>
              {personalCategories.map((cat) => (
                <TouchableOpacity
                  key={cat.id}
                  style={[
                    styles.categoryOption,
                    { borderColor: colors.border },
                    transferToId === Number(cat.id) && { borderColor: colors.primary, backgroundColor: 'rgba(232,168,56,0.1)' },
                  ]}
                  onPress={() => setTransferToId(Number(cat.id))}
                >
                  <View style={styles.categoryOptionRow}>
                    {cat.color && <View style={[styles.colorDot, { backgroundColor: cat.color }]} />}
                    <AppText variant="body">{cat.name}</AppText>
                  </View>
                </TouchableOpacity>
              ))}
            </ScrollView>

            <Input
              label="Monto"
              value={transferAmount}
              onChangeText={setTransferAmount}
              keyboardType="numeric"
              placeholder="0"
            />
            <Input
              label="Descripcion (opcional)"
              value={transferDescription}
              onChangeText={setTransferDescription}
              placeholder="Motivo de la transferencia"
            />

            <View style={styles.modalActions}>
              <Button title="Cancelar" onPress={() => setShowTransferModal(false)} variant="outline" style={styles.modalBtn} />
              <Button
                title="Transferir"
                onPress={handleTransfer}
                loading={transferring}
                disabled={!transferFromId || !transferToId || !transferAmount}
                style={styles.modalBtn}
              />
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
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: spacing.md,
    marginBottom: spacing.sm,
  },
  addButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  allocationCard: {
    marginBottom: spacing.sm,
  },
  allocationHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  categoryInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  colorDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginRight: spacing.xs,
  },
  categoryIcon: {
    marginRight: spacing.xs,
  },
  categoryName: {
    fontWeight: '600',
  },
  progressBarBg: {
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
    marginBottom: spacing.xs,
  },
  progressBarFill: {
    height: 8,
    borderRadius: 4,
  },
  allocationFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  varianceText: {
    marginTop: spacing.xs,
  },
  transferCard: {
    marginBottom: spacing.sm,
  },
  transferDesc: {
    marginBottom: spacing.sm,
  },
  summaryCard: {
    marginTop: spacing.md,
    marginBottom: spacing.md,
  },
  summaryTitle: {
    marginBottom: spacing.sm,
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.xs,
  },
  divider: {
    height: 1,
    marginVertical: spacing.sm,
  },
  // Modal styles
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
    maxHeight: '85%',
  },
  modalTitle: {
    marginBottom: spacing.md,
  },
  modalLabel: {
    marginBottom: spacing.xs,
    marginLeft: spacing.xs,
  },
  categoryList: {
    maxHeight: 180,
    marginBottom: spacing.md,
  },
  categoryListSmall: {
    maxHeight: 120,
    marginBottom: spacing.sm,
  },
  categoryOption: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderWidth: 1,
    borderRadius: 10,
    marginBottom: spacing.xs,
  },
  categoryOptionRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  noCatText: {
    textAlign: 'center',
    paddingVertical: spacing.md,
  },
  modalActions: {
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.sm,
  },
  modalBtn: {
    flex: 1,
  },
});

export default BudgetScreen;
