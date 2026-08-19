import { fechaLocalISO } from '../../utils/format';
import React, { useState, useEffect, useCallback } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import { spacing } from '../../theme';
import { confirmarBorrado } from '../../utils/confirmar';
import { useAccionUnica } from '../../hooks/useAccionUnica';
import { scheduledService } from '../../services/scheduledService';
import { categoryService } from '../../services/categoryService';
import { validateRequired, validateAmount, validateDate, validateCategory, validateDayOfMonth, validateDuration, validateAll } from '../../utils/validators';
import { ScheduledMovement, Frequency, Category, CategoryType, WeekendPolicy } from '../../types';
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

// El 5 es "la ultima", no "la quinta o nada": un mes con cuatro viernes usa el
// cuarto en vez de irse al mes siguiente.
/* `seOye` es lo que dice el lector de pantalla. En el boton cabe "1a" o "Lun"
   porque hay cinco o siete seguidos y no hay sitio; leido en voz alta, "1a" no
   es nada y "Mar" tanto podria ser martes como marzo. */
const SEMANAS = [
  { valor: 1, etiqueta: '1a', seOye: 'primera semana del mes' },
  { valor: 2, etiqueta: '2a', seOye: 'segunda semana del mes' },
  { valor: 3, etiqueta: '3a', seOye: 'tercera semana del mes' },
  { valor: 4, etiqueta: '4a', seOye: 'cuarta semana del mes' },
  { valor: 5, etiqueta: 'Ultima', seOye: 'última semana del mes' },
];

// ISO: 1 es lunes y 7 es domingo, igual que en el backend.
const DIAS_DE_LA_SEMANA = [
  { valor: 1, etiqueta: 'Lun', seOye: 'lunes' },
  { valor: 2, etiqueta: 'Mar', seOye: 'martes' },
  { valor: 3, etiqueta: 'Mie', seOye: 'miércoles' },
  { valor: 4, etiqueta: 'Jue', seOye: 'jueves' },
  { valor: 5, etiqueta: 'Vie', seOye: 'viernes' },
  { valor: 6, etiqueta: 'Sab', seOye: 'sábado' },
  { valor: 7, etiqueta: 'Dom', seOye: 'domingo' },
];

const POLITICAS_FIN_DE_SEMANA: Array<{ valor: WeekendPolicy; etiqueta: string; seOye: string }> = [
  { valor: 'KEEP', etiqueta: 'Dejarla', seOye: 'Dejarla en el fin de semana' },
  { valor: 'PREVIOUS_BUSINESS_DAY', etiqueta: 'Viernes antes', seOye: 'Adelantarla al viernes anterior' },
  { valor: 'NEXT_BUSINESS_DAY', etiqueta: 'Lunes despues', seOye: 'Aplazarla al lunes siguiente' },
];

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
  const [startDate, setStartDate] = useState(existing?.startDate || fechaLocalISO());
  const [durationMonths, setDurationMonths] = useState(existing?.durationMonths ? String(existing.durationMonths) : '');
  const [dayOfMonth, setDayOfMonth] = useState(existing?.dayOfMonth ? String(existing.dayOfMonth) : '');
  const [weekOfMonth, setWeekOfMonth] = useState<number | null>(existing?.weekOfMonth ?? null);
  const [dayOfWeek, setDayOfWeek] = useState<number | null>(existing?.dayOfWeek ?? null);
  const [weekendPolicy, setWeekendPolicy] = useState<WeekendPolicy>(existing?.weekendPolicy || 'KEEP');

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

  // Borrar un programado cancela ademas sus pendientes: exige confirmacion.
  const handleDelete = () =>
    confirmarBorrado(`el programado "${existing?.name || ''}"`, () => { borrar(); });

  const borrarProgramado = useCallback(async () => {
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
  }, [existing, navigation, showToast]);

  const { ejecutando: borrando, ejecutar: borrar } = useAccionUnica(borrarProgramado);

  const guardarProgramado = useCallback(async () => {
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
    /* "El tercer viernes" son dos datos y ninguno vale solo: "el tercero" no
       dice de que y "un viernes" no dice cual. Se avisa aqui para no gastar un
       viaje al servidor, que tambien lo rechaza. */
    if ((weekOfMonth === null) !== (dayOfWeek === null)) {
      showToast('warning', 'Validacion',
        'El patron "el tercer viernes" necesita la semana y el dia de la semana juntos');
      return;
    }
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
        weekOfMonth: weekOfMonth ?? undefined,
        dayOfWeek: dayOfWeek ?? undefined,
        weekendPolicy,
      };
      const res = await scheduledService.save(data);
      if (res.correct) {
        navigation.goBack();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo guardar');
    }
  }, [name, selectedCategory, amount, frequency, startDate, durationMonths, dayOfMonth,
      weekOfMonth, dayOfWeek, weekendPolicy, isEdit, existing, user, navigation, showToast]);

  const { ejecutando: guardando, ejecutar: handleSave } = useAccionUnica(guardarProgramado);

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
          accessibilityRole="radio"
          accessibilityState={{ selected: movementType === 'INCOME' }}
          accessibilityLabel="Es un ingreso"
        >
          <AppText variant="body" color={movementType === 'INCOME' ? '#FFF' : colors.text}>💰 Ingreso</AppText>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.typeBtn, { backgroundColor: movementType === 'EXPENSE' ? colors.expense : colors.surface, borderColor: movementType === 'EXPENSE' ? colors.expense : colors.border }]}
          onPress={() => { setMovementType('EXPENSE'); if (selectedCategory?.type !== 'EXPENSE') setSelectedCategory(null); }}
          accessibilityRole="radio"
          accessibilityState={{ selected: movementType === 'EXPENSE' }}
          accessibilityLabel="Es un gasto"
        >
          <AppText variant="body" color={movementType === 'EXPENSE' ? '#FFF' : colors.text}>💸 Gasto</AppText>
        </TouchableOpacity>
      </View>

      <AppText variant="label" style={styles.pickerLabel}>Categoría</AppText>
      <TouchableOpacity
        onPress={() => setShowCategoryPicker(!showCategoryPicker)}
        style={[styles.pickerButton, { backgroundColor: colors.surface, borderColor: colors.border }]}
        accessibilityRole="button"
        /* `expanded` es lo que avisa de que la lista se abrio debajo. Sin el,
           al tocar no pasa nada audible y parece que el boton esta roto. */
        accessibilityState={{ expanded: showCategoryPicker }}
        accessibilityLabel={
          selectedCategory
            ? `Categoría elegida: ${selectedCategory.name}`
            : `Elegir la categoría de ${movementType === 'INCOME' ? 'ingreso' : 'gasto'}`
        }
        accessibilityHint={showCategoryPicker ? 'Cierra la lista de categorías' : 'Abre la lista de categorías'}
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
              accessibilityRole="radio"
              accessibilityState={{ selected: cat.id === selectedCategory?.id }}
              accessibilityLabel={`Categoría ${cat.name}`}
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
            accessibilityRole="radio"
            accessibilityState={{ selected: frequency === f }}
            accessibilityLabel={`Se repite ${FREQUENCY_LABELS[f].toLowerCase()}`}
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

      {/* "El tercer viernes" es como se paga la nomina, y no hay dia del mes
          que lo diga: en agosto es el 21 y en septiembre el 18. */}
      <AppText variant="label" style={styles.pickerLabel}>Semana del mes (opcional)</AppText>
      <View style={styles.freqGrid}>
        {SEMANAS.map((s) => (
          <TouchableOpacity
            key={s.valor}
            style={[
              styles.freqBtn,
              {
                backgroundColor: weekOfMonth === s.valor ? colors.secondary : colors.surface,
                borderColor: colors.border,
              },
            ]}
            onPress={() => setWeekOfMonth(weekOfMonth === s.valor ? null : s.valor)}
            accessibilityRole="radio"
            accessibilityState={{ selected: weekOfMonth === s.valor }}
            accessibilityLabel={s.seOye}
            accessibilityHint={weekOfMonth === s.valor ? 'Toca de nuevo para no usar semana del mes' : undefined}
          >
            <AppText variant="caption" color={weekOfMonth === s.valor ? '#FFFFFF' : colors.text}>
              {s.etiqueta}
            </AppText>
          </TouchableOpacity>
        ))}
      </View>

      <AppText variant="label" style={styles.pickerLabel}>Dia de la semana (opcional)</AppText>
      <View style={styles.freqGrid}>
        {DIAS_DE_LA_SEMANA.map((d) => (
          <TouchableOpacity
            key={d.valor}
            style={[
              styles.freqBtn,
              {
                backgroundColor: dayOfWeek === d.valor ? colors.secondary : colors.surface,
                borderColor: colors.border,
              },
            ]}
            onPress={() => setDayOfWeek(dayOfWeek === d.valor ? null : d.valor)}
            accessibilityRole="radio"
            accessibilityState={{ selected: dayOfWeek === d.valor }}
            accessibilityLabel={d.seOye}
            accessibilityHint={dayOfWeek === d.valor ? 'Toca de nuevo para no usar día de la semana' : undefined}
          >
            <AppText variant="caption" color={dayOfWeek === d.valor ? '#FFFFFF' : colors.text}>
              {d.etiqueta}
            </AppText>
          </TouchableOpacity>
        ))}
      </View>

      <AppText variant="label" style={styles.pickerLabel}>Si cae fin de semana</AppText>
      <View style={styles.freqGrid}>
        {POLITICAS_FIN_DE_SEMANA.map((p) => (
          <TouchableOpacity
            key={p.valor}
            style={[
              styles.freqBtn,
              {
                backgroundColor: weekendPolicy === p.valor ? colors.secondary : colors.surface,
                borderColor: colors.border,
              },
            ]}
            onPress={() => setWeekendPolicy(p.valor)}
            accessibilityRole="radio"
            accessibilityState={{ selected: weekendPolicy === p.valor }}
            accessibilityLabel={p.seOye}
          >
            <AppText variant="caption" color={weekendPolicy === p.valor ? '#FFFFFF' : colors.text}>
              {p.etiqueta}
            </AppText>
          </TouchableOpacity>
        ))}
      </View>

      <Button title={isEdit ? 'Actualizar' : 'Guardar'} onPress={handleSave} loading={guardando} disabled={borrando} size="large" style={styles.saveBtn} />
      {isEdit && (
        <Button
          title="Eliminar programado"
          accessibilityLabel={`Eliminar el programado ${existing?.name || ''}`.trim()}
          accessibilityHint="También cancela los movimientos que quedaban por generarse"
          onPress={handleDelete}
          loading={borrando}
          disabled={guardando}
          variant="danger"
          style={styles.deleteBtn}
        />
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
