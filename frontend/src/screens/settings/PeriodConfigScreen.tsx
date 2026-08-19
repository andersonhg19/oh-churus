import React, { useState } from 'react';
import { StyleSheet, ScrollView } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import { spacing } from '../../theme';
import { authService } from '../../services/authService';
import { validateDayOfMonth } from '../../utils/validators';

const PeriodConfigScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user, updateUser } = useAuth();
  const [budgetStartDay, setBudgetStartDay] = useState(String(user?.budgetStartDay || 1));
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    const dayError = validateDayOfMonth(budgetStartDay);
    if (dayError) {
      showToast('warning', 'Validacion', dayError);
      return;
    }
    const day = parseInt(budgetStartDay, 10);
    setSaving(true);
    try {
      const res = await authService.updateUser({ id: user?.userId, budgetStartDay: day });
      if (res.correct) {
        await updateUser({ budgetStartDay: day });
        showToast('success', 'Listo', 'Dia de inicio actualizado a ' + day);
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo actualizar');
    } finally {
      setSaving(false);
    }
  };

  return (
    <ScrollView style={[styles.container, { backgroundColor: colors.background }]} contentContainerStyle={styles.content}>
      <Card style={styles.section}>
        <AppText variant="label" style={styles.sectionTitle}>Dia de inicio del periodo</AppText>
        <AppText variant="caption" color={colors.textSecondary} style={styles.hint}>
          Tu ciclo de presupuesto va del dia que elijas hasta el dia anterior del mes siguiente. Ejemplo: si eliges 28, tu ciclo va del 28 al 27.
        </AppText>
        <Input
          label="Dia (1-31)"
          value={budgetStartDay}
          onChangeText={setBudgetStartDay}
          placeholder="28"
          keyboardType="numeric"
        />
        <AppText variant="caption" color={colors.textMuted} style={styles.currentLabel}>
          Valor actual: dia {user?.budgetStartDay || 1}
        </AppText>
        <Button
          title="Guardar"
          accessibilityLabel="Guardar el día en que empieza tu mes"
          onPress={handleSave}
          loading={saving}
          style={styles.saveBtn}
        />
      </Card>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.xl },
  section: { marginBottom: spacing.md },
  sectionTitle: { marginBottom: spacing.xs },
  hint: { marginBottom: spacing.md },
  currentLabel: { marginBottom: spacing.md, fontStyle: 'italic' },
  saveBtn: { marginTop: spacing.sm },
});

export default PeriodConfigScreen;
