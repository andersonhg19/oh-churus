import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import { spacing } from '../../theme';
import { scheduledService } from '../../services/scheduledService';
import { categoryService } from '../../services/categoryService';
import { validateRequired, validateAmount, validateDate, validateCategory, validateDayOfMonth, validateDuration, validateAll } from '../../utils/validators';
import { ScheduledMovement, Frequency, Category, CategoryType } from '../../types';
import { getIconEmoji } from '../../utils/iconMap';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type ScheduledStackParamList = {
  ScheduledList: undefined;
  ScheduledForm: { scheduled?: ScheduledMovement };
};

type Props = NativeStackScreenProps<ScheduledStackParamList, 'ScheduledForm'>;

const FREQUENCIES: Frequency[] = [
  'DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY',
  'BIMONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL',
];

const FREQUENCY_LABELS: Record<Frequency, string> = {
  DAILY: 'Diario',
  WEEKLY: 'Semanal',
  BIWEEKLY: 'Quincenal',
  MONTHLY: 'Mensual',
  BIMONTHLY: 'Bimestral',
  QUARTERLY: 'Trimestral',
  SEMIANNUAL: 'Semestral',
  ANNUAL: 'Anual',
};

const ScheduledFormScreen: React.FC<Props> = ({ navigation, route }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();
  const existing = route.params?.scheduled;
  const isEdit = !!existing;

  const [name, setName] = useState(existing?.name || '');
  const [allCategories, setAllCategories] = useState<Category[]>([]);
  const [movementType, setMovementType] = useState<CategoryType>(existing?.categoryType || 'EXPENSE');
  const [selectedCategory, setSelectedCategory] = useState<Category | null>(null);
  const [showCategoryPicker, setShowCategoryPicker] = useState(false);

  const filteredCategories = allCategories.filter(c => c.type === movementType);
  const [amount, setAmount] = useState(existing ? String(existing.amount) : '');
  const [frequency, setFrequency] = useState<Frequency>(existing?.frequency || 'MONTHLY');
  const [startDate, setStartDate] = useState(existing?.startDate || new Date().toISOString().split('T')[0]);
  const [durationMonths, setDurationMonths] = useState(existing?.durationMonths ? String(existing.durationMonths) : '');
  const [dayOfMonth, setDayOfMonth] = useState(existing?.dayOfMonth ? String(existing.dayOfMonth) : '');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const loadCategories = async () => {
      if (!user) return;
      try {
        const res = await categoryService.getAll({ userId: user.userId, page: 0, size: 100 });
        if (res.correct && res.object) {
          const list = res.object.list || [];
          setAllCategories(list);
          if (existing?.categoryId) {
            const found = list.find((c: Category) => c.id === existing.categoryId);
            if (found) {
              setSelectedCategory(found);
              setMovementType(found.type);
            }
          }
        }
      } catch {
        // Silently fail
      }
    };
    loadCategories();
  }, [user, existing]);

  const handleDelete = async () => {
    if (!existing) return;
    try {
      const res = await scheduledService.delete(String(existing.id));
      if (res.correct) {
        showToast('success', 'Listo', 'Programado eliminado y sus pendientes cancelados');
        navigation.goBack();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo eliminar');
    }
  };

  const handleSave = async () => {
    const error = validateAll(
      validateRequired(name, 'Nombre'),
      validateCategory(selectedCategory?.id ? String(selectedCategory.id) : null),
      validateAmount(amount),
      validateDate(startDate),
      validateDayOfMonth(dayOfMonth),
      validateDuration(durationMonths),
    );
    if (error) {
      showToast('warning', 'Validacion', error);
      return;
    }
    setLoading(true);
    try {
      const data: Partial<ScheduledMovement> = {
        ...(isEdit && { id: existing.id }),
        userId: user?.userId,
        categoryId: selectedCategory!.id,
        name: name.trim(),
        amount: parseFloat(amount),
        frequency,
        startDate,
        durationMonths: durationMonths ? parseInt(durationMonths, 10) : undefined,
        dayOfMonth: dayOfMonth ? parseInt(dayOfMonth, 10) : undefined,
      };
      const res = await scheduledService.save(data);
      if (res.correct) {
        navigation.goBack();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo guardar');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      <AppText variant="subtitle" style={styles.title}>
        {isEdit ? 'Editar Programado' : 'Nuevo Programado'}
      </AppText>

      <Input label="Nombre" value={name} onChangeText={setName} placeholder="Nombre del movimiento" />

      <AppText variant="label" style={styles.pickerLabel}>Tipo</AppText>
      <View style={styles.typeRow}>
        <TouchableOpacity
          style={[styles.typeBtn, { backgroundColor: movementType === 'INCOME' ? colors.income : colors.surface, borderColor: movementType === 'INCOME' ? colors.income : colors.border }]}
          onPress={() => { setMovementType('INCOME'); if (selectedCategory?.type !== 'INCOME') setSelectedCategory(null); }}
        >
          <AppText variant="body" color={movementType === 'INCOME' ? '#FFF' : colors.text}>💰 Ingreso</AppText>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.typeBtn, { backgroundColor: movementType === 'EXPENSE' ? colors.expense : colors.surface, borderColor: movementType === 'EXPENSE' ? colors.expense : colors.border }]}
          onPress={() => { setMovementType('EXPENSE'); if (selectedCategory?.type !== 'EXPENSE') setSelectedCategory(null); }}
        >
          <AppText variant="body" color={movementType === 'EXPENSE' ? '#FFF' : colors.text}>💸 Gasto</AppText>
        </TouchableOpacity>
      </View>

      <AppText variant="label" style={styles.pickerLabel}>Categoría</AppText>
      <TouchableOpacity
        onPress={() => setShowCategoryPicker(!showCategoryPicker)}
        style={[styles.pickerButton, { backgroundColor: colors.surface, borderColor: colors.border }]}
      >
        <AppText variant="body" color={selectedCategory ? colors.text : colors.textMuted}>
          {selectedCategory ? `${getIconEmoji(selectedCategory.icon)} ${selectedCategory.name}` : `Seleccionar categoría de ${movementType === 'INCOME' ? 'ingreso' : 'gasto'}`}
        </AppText>
      </TouchableOpacity>
      {showCategoryPicker && (
        <ScrollView style={[styles.pickerList, { backgroundColor: colors.surface, borderColor: colors.border }]} nestedScrollEnabled>
          {filteredCategories.map((cat) => (
            <TouchableOpacity
              key={cat.id}
              style={[
                styles.pickerItem,
                cat.id === selectedCategory?.id && { backgroundColor: colors.primaryLight || colors.primary },
              ]}
              onPress={() => {
                setSelectedCategory(cat);
                setShowCategoryPicker(false);
              }}
            >
              <AppText
                variant="body"
                color={cat.id === selectedCategory?.id ? '#FFFFFF' : colors.text}
              >
                {getIconEmoji(cat.icon)} {cat.name}
              </AppText>
            </TouchableOpacity>
          ))}
          {filteredCategories.length === 0 && (
            <AppText variant="caption" style={styles.noCats}>No hay categorías de {movementType === 'INCOME' ? 'ingreso' : 'gasto'}</AppText>
          )}
        </ScrollView>
      )}

      <Input label="Monto" value={amount} onChangeText={setAmount} placeholder="0" keyboardType="numeric" />

      <AppText variant="label" style={styles.freqLabel}>Frecuencia</AppText>
      <View style={styles.freqGrid}>
        {FREQUENCIES.map((f) => (
          <TouchableOpacity
            key={f}
            style={[
              styles.freqBtn,
              {
                backgroundColor: frequency === f ? colors.secondary : colors.surface,
                borderColor: colors.border,
              },
            ]}
            onPress={() => setFrequency(f)}
          >
            <AppText variant="caption" color={frequency === f ? '#FFFFFF' : colors.text}>
              {FREQUENCY_LABELS[f]}
            </AppText>
          </TouchableOpacity>
        ))}
      </View>

      <Input label="Fecha inicio" value={startDate} onChangeText={setStartDate} placeholder="YYYY-MM-DD" />
      <Input label="Duracion (meses)" value={durationMonths} onChangeText={setDurationMonths} placeholder="Opcional" keyboardType="numeric" />
      <Input label="Dia del mes" value={dayOfMonth} onChangeText={setDayOfMonth} placeholder="1-31 (opcional)" keyboardType="numeric" />

      <Button title={isEdit ? 'Actualizar' : 'Guardar'} onPress={handleSave} loading={loading} size="large" style={styles.saveBtn} />
      {isEdit && (
        <Button title="Eliminar programado" onPress={handleDelete} variant="danger" style={styles.deleteBtn} />
      )}
      <Button title="Cancelar" onPress={() => navigation.goBack()} variant="outline" style={styles.cancelBtn} />
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.xl },
  title: { marginBottom: spacing.lg },
  pickerLabel: { marginBottom: spacing.xs, marginLeft: spacing.xs },
  pickerButton: {
    padding: spacing.md,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: spacing.sm,
  },
  pickerList: {
    maxHeight: 200,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: spacing.md,
  },
  pickerItem: {
    padding: spacing.sm,
    paddingHorizontal: spacing.md,
  },
  noCats: {
    padding: spacing.md,
    textAlign: 'center',
  },
  typeRow: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.md },
  typeBtn: {
    flex: 1,
    paddingVertical: spacing.sm + 4,
    borderRadius: 12,
    borderWidth: 1.5,
    alignItems: 'center',
  },
  freqLabel: { marginBottom: spacing.sm, marginLeft: spacing.xs },
  freqGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.xs,
    marginBottom: spacing.md,
  },
  freqBtn: {
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs + 2,
    borderRadius: 8,
    borderWidth: 1,
  },
  saveBtn: { marginTop: spacing.md },
  deleteBtn: { marginTop: spacing.sm },
  cancelBtn: { marginTop: spacing.sm },
});

export default ScheduledFormScreen;
