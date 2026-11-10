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
      showToast('warning', 'Revisa el dato', dayError);
      return;
    }
    const day = parseInt(budgetStartDay, 10);
    setSaving(true);
    try {
      /* El servidor exige nombre y correo en cada guardado del usuario: sin
         ellos rechazaba la peticion y el dia de corte no cambiaba nunca, por
         mucho que se pulsara Guardar. Moneda ya lo hacia asi. */
      const res = await authService.updateUser({
        id: user?.userId,
        name: user?.name,
        email: user?.email,
        budgetStartDay: day,
      } as any);
      if (res.correct) {
        await updateUser({ budgetStartDay: day });
        showToast('success', 'Listo', 'Día de corte actualizado al ' + day);
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
        <AppText variant="label" style={styles.sectionTitle}>Día de corte del periodo</AppText>
        <AppText variant="caption" color={colors.textSecondary} style={styles.hint}>
          Tu ciclo de presupuesto va del día que elijas hasta el día anterior del mes siguiente. Ejemplo: si eliges 28, tu ciclo va del 28 al 27.
        </AppText>
        <Input
          label="Día (1-31)"
          value={budgetStartDay}
          onChangeText={setBudgetStartDay}
          placeholder="28"
          keyboardType="numeric"
        />
        <AppText variant="caption" color={colors.textMuted} style={styles.currentLabel}>
          Valor actual: día {user?.budgetStartDay || 1}
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
