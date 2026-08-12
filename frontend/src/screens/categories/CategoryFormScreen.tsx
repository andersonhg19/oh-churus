import React, { useState, useEffect, useCallback } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import { householdService } from '../../services/householdService';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import ColorPicker from '../../components/molecules/ColorPicker';
import IconPicker from '../../components/molecules/IconPicker';
import ParentCategoryPicker from '../../components/molecules/ParentCategoryPicker';
import { spacing } from '../../theme';
import { confirmarBorrado } from '../../utils/confirmar';
import { useAccionUnica } from '../../hooks/useAccionUnica';
import { validateRequired } from '../../utils/validators';
import { categoryService } from '../../services/categoryService';
import { Category, CategoryTree, CategoryType } from '../../types';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type CategoriesStackParamList = {
  CategoriesList: undefined;
  CategoryForm: { category?: CategoryTree };
};

type Props = NativeStackScreenProps<CategoriesStackParamList, 'CategoryForm'>;

const CategoryFormScreen: React.FC<Props> = ({ navigation, route }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();
  const existing = route.params?.category;
  const isEdit = !!existing;

  const [name, setName] = useState(existing?.name || '');
  const [description, setDescription] = useState(existing?.description || '');
  const [type, setType] = useState<CategoryType>(existing?.type || 'EXPENSE');
  const [parentId, setParentId] = useState<string | null>(
    existing?.parentId || null
  );
  const [icon, setIcon] = useState(existing?.icon || '');
  const [color, setColor] = useState(existing?.color || '');
  const [shared, setShared] = useState(!!existing?.householdId);
  const [householdId, setHouseholdId] = useState<number | null>(null);

  /*
   * Si el hogar no carga, el selector Personal/Compartida desaparece sin
   * explicacion y la categoria se guarda como personal creyendo el usuario que
   * la comparte. Se avisa por toast y no con pantalla de error: el resto del
   * formulario sigue siendo perfectamente usable sin hogar.
   */
  useEffect(() => {
    const cargarHogar = async () => {
      if (!user) return;
      try {
        const res = await householdService.getByUser(user.userId);
        if (!res.correct) {
          showToast('error', 'Error', res.message || 'No se pudo cargar el nucleo familiar');
          return;
        }
        if (res.object && res.object.length > 0) {
          setHouseholdId(res.object[0].householdId);
        }
      } catch (err: any) {
        showToast('error', 'Error', err.message || 'No se pudo cargar el nucleo familiar');
      }
    };
    cargarHogar();
  }, [user, showToast]);

  // Borrar una categoria arrastra sus movimientos: nunca al primer toque.
  const handleDelete = () =>
    confirmarBorrado(`la categoria "${existing?.name || ''}"`, () => { borrar(); });

  const borrarCategoria = useCallback(async () => {
    if (!existing) return;
    try {
      const res = await categoryService.delete(String(existing.id));
      if (res.correct) {
        navigation.goBack();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo eliminar');
    }
  }, [existing, navigation, showToast]);

  const { ejecutando: borrando, ejecutar: borrar } = useAccionUnica(borrarCategoria);

  const guardarCategoria = useCallback(async () => {
    const error = validateRequired(name, 'Nombre de la categoria');
    if (error) {
      showToast('warning', 'Validacion', error);
      return;
    }
    try {
      const data: any = {
        ...(isEdit && { id: existing.id }),
        userId: user?.userId,
        name: name.trim(),
        description: description.trim() || undefined,
        type,
        parentId: parentId || undefined,
        icon: icon || undefined,
        color: color || undefined,
        householdId: shared && householdId ? householdId : null,
      };
      const res = await categoryService.save(data);
      if (res.correct) {
        navigation.goBack();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo guardar');
    }
  }, [name, description, type, parentId, icon, color, shared, householdId, isEdit, existing, user, navigation, showToast]);

  const { ejecutando: guardando, ejecutar: handleSave } = useAccionUnica(guardarCategoria);

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      <AppText variant="subtitle" style={styles.title}>
        {isEdit ? 'Editar Categoría' : 'Nueva Categoría'} 🐿️
      </AppText>

      <Input
        label="Nombre"
        value={name}
        onChangeText={setName}
        placeholder="Nombre de la categoría"
      />
      <Input
        label="Descripción"
        value={description}
        onChangeText={setDescription}
        placeholder="Descripción (opcional)"
        multiline
      />

      {/* Tipo: Ingreso / Gasto */}
      <AppText variant="label" style={styles.typeLabel}>Tipo</AppText>
      <View style={styles.typeRow}>
        <TouchableOpacity
          style={[
            styles.typeBtn,
            {
              backgroundColor: type === 'INCOME' ? colors.income : colors.surface,
              borderColor: type === 'INCOME' ? colors.income : colors.border,
            },
          ]}
          onPress={() => setType('INCOME')}
        >
          <AppText variant="body" color={type === 'INCOME' ? '#FFFFFF' : colors.text}>
            💰 Ingreso
          </AppText>
        </TouchableOpacity>
        <TouchableOpacity
          style={[
            styles.typeBtn,
            {
              backgroundColor: type === 'EXPENSE' ? colors.expense : colors.surface,
              borderColor: type === 'EXPENSE' ? colors.expense : colors.border,
            },
          ]}
          onPress={() => setType('EXPENSE')}
        >
          <AppText variant="body" color={type === 'EXPENSE' ? '#FFFFFF' : colors.text}>
            💸 Gasto
          </AppText>
        </TouchableOpacity>
      </View>

      {/* Compartida con el hogar */}
      {householdId && (
        <>
          <AppText variant="label" style={styles.typeLabel}>Alcance</AppText>
          <View style={styles.typeRow}>
            <TouchableOpacity
              style={[styles.typeBtn, {
                backgroundColor: !shared ? colors.primary : colors.surface,
                borderColor: !shared ? colors.primary : colors.border,
              }]}
              onPress={() => setShared(false)}
            >
              <AppText variant="body" color={!shared ? '#FFF' : colors.text}>👤 Personal</AppText>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.typeBtn, {
                backgroundColor: shared ? colors.secondary : colors.surface,
                borderColor: shared ? colors.secondary : colors.border,
              }]}
              onPress={() => setShared(true)}
            >
              <AppText variant="body" color={shared ? '#FFF' : colors.text}>🏠 Compartida</AppText>
            </TouchableOpacity>
          </View>
        </>
      )}

      {/* Categoría padre - picker visual */}
      <ParentCategoryPicker
        value={parentId}
        onChange={setParentId}
        excludeId={isEdit ? existing.id : undefined}
        filterType={type}
      />

      {/* Icono - picker visual con emojis */}
      <IconPicker
        value={icon}
        onChange={(key) => setIcon(key)}
      />

      {/* Color - picker visual con paleta */}
      <ColorPicker
        value={color}
        onChange={setColor}
      />

      {/* Preview */}
      {Boolean(icon || color || name) && (
        <View style={[styles.preview, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          <AppText variant="caption" color={colors.textMuted} style={{ marginBottom: spacing.xs }}>
            Vista previa
          </AppText>
          <View style={styles.previewRow}>
            {color ? <View style={[styles.previewDot, { backgroundColor: color }]} /> : null}
            <AppText variant="body" color={colors.text}>
              {icon ? (ICON_EMOJIS[icon] || '📁') + ' ' : ''}{name || 'Nombre'}
            </AppText>
            <AppText variant="caption" color={type === 'INCOME' ? colors.income : colors.expense}>
              {type === 'INCOME' ? 'Ingreso' : 'Gasto'}
            </AppText>
          </View>
        </View>
      )}

      <Button
        title={isEdit ? 'Actualizar' : 'Guardar'}
        onPress={handleSave}
        loading={guardando}
        disabled={borrando}
        size="large"
        style={styles.saveBtn}
      />
      {isEdit && (
        <Button title="Eliminar" onPress={handleDelete} loading={borrando} disabled={guardando} variant="danger" style={styles.deleteBtn} />
      )}
      <Button
        title="Cancelar"
        onPress={() => navigation.goBack()}
        variant="outline"
        style={styles.cancelBtn}
      />
    </ScrollView>
  );
};

const ICON_EMOJIS: Record<string, string> = {
  wallet: '👛', money: '💰', dollar: '💵', coin: '🪙', bank: '🏦',
  'credit-card': '💳', 'piggy-bank': '🐷', chart: '📈', 'bar-chart': '📊',
  percent: '💹', home: '🏠', key: '🔑', zap: '⚡', droplet: '💧',
  flame: '🔥', tool: '🔧', bulb: '💡', bed: '🛏️',
  'shopping-cart': '🛒', 'shopping-bag': '🛍️', coffee: '☕', pizza: '🍕',
  apple: '🍎', cooking: '🍳', cake: '🎂', wine: '🍷',
  car: '🚗', bus: '🚌', truck: '🚛', bike: '🚲', plane: '✈️',
  fuel: '⛽', navigation: '🧭', train: '🚆',
  briefcase: '💼', laptop: '💻', desktop: '🖥️', phone: '📱',
  mail: '📧', calendar: '📅', clock: '⏰', 'trending-up': '📈',
  heart: '❤️', hospital: '🏥', pill: '💊', thermometer: '🌡️',
  activity: '🩺', book: '📚', graduation: '🎓', award: '🏆', bookmark: '📖',
  film: '🎬', music: '🎵', tv: '📺', gamepad: '🎮', headphones: '🎧',
  party: '🎉', sports: '⚽', gym: '🏋️',
  gift: '🎁', pet: '🐾', baby: '👶', clothes: '👕', scissors: '✂️',
  star: '⭐', shield: '🛡️', squirrel: '🐿️',
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.xl, paddingBottom: spacing.xxl * 2 },
  title: { marginBottom: spacing.lg },
  typeLabel: { marginBottom: spacing.sm, marginLeft: spacing.xs },
  typeRow: {
    flexDirection: 'row',
    gap: spacing.sm,
    marginBottom: spacing.md,
  },
  typeBtn: {
    flex: 1,
    paddingVertical: spacing.sm + 4,
    borderRadius: 12,
    borderWidth: 1.5,
    alignItems: 'center',
  },
  preview: {
    padding: spacing.md,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: spacing.md,
  },
  previewRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  previewDot: { width: 16, height: 16, borderRadius: 8 },
  saveBtn: { marginTop: spacing.md },
  deleteBtn: { marginTop: spacing.sm },
  cancelBtn: { marginTop: spacing.sm },
});

export default CategoryFormScreen;
