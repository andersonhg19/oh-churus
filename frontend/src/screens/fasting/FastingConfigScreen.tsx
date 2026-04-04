import React, { useState, useCallback, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import { spacing } from '../../theme';
import { fastingService } from '../../services/fastingService';
import { useFocusEffect } from '@react-navigation/native';

interface Preset {
  planType: string;
  label: string;
  fastingHours: number;
  eatingHours: number;
}

const FastingConfigScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();

  const [presets, setPresets] = useState<Preset[]>([]);
  const [selectedPlan, setSelectedPlan] = useState<string>('PLAN_16_8');
  const [customFasting, setCustomFasting] = useState('16');
  const [customEating, setCustomEating] = useState('8');
  const [suggestedStart, setSuggestedStart] = useState('20:00');
  const [reminders, setReminders] = useState(false);
  const [saving, setSaving] = useState(false);
  const [currentPlan, setCurrentPlan] = useState<any>(null);

  useEffect(() => {
    const loadPresets = async () => {
      try {
        const res = await fastingService.getPresets();
        if (res.correct) setPresets(res.object || []);
      } catch { /* silent */ }
    };
    loadPresets();
  }, []);

  const fetchPlan = useCallback(async () => {
    if (!user) return;
    try {
      const res = await fastingService.getPlan(user.userId);
      if (res.correct && res.object) {
        const p = res.object;
        setCurrentPlan(p);
        setSelectedPlan(p.planType);
        setCustomFasting(String(p.fastingHours));
        setCustomEating(String(p.eatingHours));
        setSuggestedStart(p.suggestedStartTime || '20:00');
        setReminders(p.remindersEnabled || false);
      }
    } catch { /* silent */ }
  }, [user]);

  useFocusEffect(useCallback(() => { fetchPlan(); }, [fetchPlan]));

  const isCustom = selectedPlan === 'CUSTOM';

  const handleSelectPreset = (preset: Preset) => {
    setSelectedPlan(preset.planType);
    setCustomFasting(String(preset.fastingHours));
    setCustomEating(String(preset.eatingHours));
  };

  const handleSave = async () => {
    const fh = parseInt(customFasting) || 0;
    const eh = parseInt(customEating) || 0;
    if (fh + eh !== 24) {
      showToast('warning', 'Validacion', 'Las horas de ayuno + comida deben sumar 24');
      return;
    }
    setSaving(true);
    try {
      const res = await fastingService.savePlan({
        userId: user!.userId,
        planType: selectedPlan,
        fastingHours: fh,
        eatingHours: eh,
        suggestedStartTime: suggestedStart,
        remindersEnabled: reminders,
      });
      if (res.correct) {
        setCurrentPlan(res.object);
        showToast('success', 'Plan guardado', `${fh}:${eh}`);
      } else showToast('error', 'Error', res.message);
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo guardar');
    } finally { setSaving(false); }
  };

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
    >
      <AppText variant="subtitle" style={styles.title}>Configuracion</AppText>

      {/* Current plan */}
      {currentPlan && (
        <Card style={styles.currentCard}>
          <AppText variant="caption" color={colors.textSecondary}>Plan actual</AppText>
          <AppText variant="subtitle" color={colors.primary}>
            {currentPlan.fastingHours}:{currentPlan.eatingHours} ({currentPlan.planType.replace('PLAN_', '').replace('_', ':')})
          </AppText>
        </Card>
      )}

      {/* Presets */}
      <AppText variant="label" style={styles.sectionLabel}>Planes predefinidos</AppText>
      <View style={styles.presetsGrid}>
        {presets.map(p => {
          const isSelected = selectedPlan === p.planType;
          return (
            <TouchableOpacity
              key={p.planType}
              style={[styles.presetChip, {
                backgroundColor: isSelected ? colors.primary : colors.surface,
                borderColor: isSelected ? colors.primary : colors.border,
              }]}
              onPress={() => handleSelectPreset(p)}
            >
              <AppText variant="body" style={{ color: isSelected ? '#FFF' : colors.text, fontWeight: isSelected ? '700' : '400' }}>
                {p.label}
              </AppText>
              <AppText variant="caption" style={{ color: isSelected ? '#FFF' : colors.textMuted }}>
                {p.fastingHours}h ayuno
              </AppText>
            </TouchableOpacity>
          );
        })}
        <TouchableOpacity
          style={[styles.presetChip, {
            backgroundColor: isCustom ? colors.secondary : colors.surface,
            borderColor: isCustom ? colors.secondary : colors.border,
          }]}
          onPress={() => setSelectedPlan('CUSTOM')}
        >
          <AppText variant="body" style={{ color: isCustom ? '#FFF' : colors.text, fontWeight: isCustom ? '700' : '400' }}>
            Custom
          </AppText>
        </TouchableOpacity>
      </View>

      {/* Custom fields */}
      {isCustom && (
        <Card style={styles.customCard}>
          <View style={styles.customRow}>
            <View style={{ flex: 1 }}>
              <Input label="Horas ayuno" value={customFasting} onChangeText={setCustomFasting} keyboardType="numeric" />
            </View>
            <View style={{ flex: 1 }}>
              <Input label="Horas comida" value={customEating} onChangeText={setCustomEating} keyboardType="numeric" />
            </View>
          </View>
        </Card>
      )}

      {/* Additional settings */}
      <Input label="Hora sugerida de inicio" value={suggestedStart} onChangeText={setSuggestedStart} placeholder="20:00" />

      <TouchableOpacity
        style={[styles.reminderRow, { borderColor: colors.border }]}
        onPress={() => setReminders(!reminders)}
      >
        <AppText variant="body">Recordatorios</AppText>
        <AppText variant="body" color={reminders ? colors.income : colors.textMuted}>
          {reminders ? 'Activados' : 'Desactivados'}
        </AppText>
      </TouchableOpacity>

      <Button title="Guardar Plan" onPress={handleSave} loading={saving} size="large" style={styles.saveBtn} />
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.xl, paddingBottom: spacing.xxl },
  title: { marginBottom: spacing.md },
  currentCard: { alignItems: 'center', marginBottom: spacing.md },
  sectionLabel: { marginBottom: spacing.sm },
  presetsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginBottom: spacing.md },
  presetChip: {
    paddingHorizontal: 16, paddingVertical: 12, borderRadius: 16,
    borderWidth: 1.5, alignItems: 'center', minWidth: 80,
  },
  customCard: { marginBottom: spacing.md },
  customRow: { flexDirection: 'row', gap: spacing.sm },
  reminderRow: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingVertical: spacing.md, borderBottomWidth: 1, marginBottom: spacing.md,
  },
  saveBtn: { marginTop: spacing.md },
});

export default FastingConfigScreen;
