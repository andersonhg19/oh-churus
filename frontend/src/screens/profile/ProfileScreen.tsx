import React, { useState } from 'react';
import { View, StyleSheet, ScrollView, Alert, Platform } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Button from '../../components/atoms/Button';
import Input from '../../components/atoms/Input';
import { spacing } from '../../theme';
import { authService } from '../../services/authService';
import { validateDayOfMonth } from '../../utils/validators';

const ProfileScreen: React.FC = () => {
  const { colors, isDark, toggleTheme } = useTheme();
  const { showToast } = useToast();
  const { user, logout, updateUser } = useAuth();
  const [budgetStartDay, setBudgetStartDay] = useState(String(user?.budgetStartDay || 1));
  const [saving, setSaving] = useState(false);

  const handleSaveBudgetDay = async () => {
    const dayError = validateDayOfMonth(budgetStartDay);
    if (dayError) {
      showToast('warning', 'Validacion', dayError);
      return;
    }
    const day = parseInt(budgetStartDay, 10);
    setSaving(true);
    try {
      const res = await authService.updateUser({
        id: user?.userId,
        budgetStartDay: day,
      });
      if (res.correct) {
        await updateUser({ budgetStartDay: day });
        showToast('success', 'Listo', 'Dia de inicio de presupuesto actualizado');
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo actualizar');
    } finally {
      setSaving(false);
    }
  };

  const handleLogout = () => {
    if (Platform.OS === 'web') {
      if (window.confirm('¿Estás seguro de que quieres cerrar sesión?')) {
        logout();
      }
    } else {
      Alert.alert('Cerrar sesión', '¿Estás seguro?', [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Sí, salir', style: 'destructive', onPress: logout },
      ]);
    }
  };

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
    >
      <View style={styles.avatarSection}>
        <AppText variant="title" style={styles.avatar}>🐿️</AppText>
        <AppText variant="subtitle">{user?.name || 'Usuario'}</AppText>
        <AppText variant="caption" color={colors.textSecondary}>{user?.email}</AppText>
      </View>

      <Card style={styles.section}>
        <AppText variant="label" style={styles.sectionTitle}>Configuracion</AppText>
        <Input
          label="Dia de inicio de presupuesto"
          value={budgetStartDay}
          onChangeText={setBudgetStartDay}
          placeholder="1-31"
          keyboardType="numeric"
        />
        <Button
          title="Guardar"
          onPress={handleSaveBudgetDay}
          loading={saving}
          size="small"
        />
      </Card>

      <Card style={styles.section}>
        <AppText variant="label" style={styles.sectionTitle}>Apariencia</AppText>
        <Button
          title={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
          onPress={toggleTheme}
          variant="outline"
        />
      </Card>

      <Button
        title="Cerrar Sesion"
        onPress={handleLogout}
        variant="danger"
        style={styles.logoutBtn}
      />

      <AppText variant="caption" align="center" color={colors.textMuted} style={styles.version}>
        Oh Churus! v1.0.0
      </AppText>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.xl },
  avatarSection: {
    alignItems: 'center',
    marginBottom: spacing.xl,
  },
  avatar: {
    fontSize: 64,
    marginBottom: spacing.sm,
  },
  section: {
    marginBottom: spacing.md,
  },
  sectionTitle: {
    marginBottom: spacing.md,
  },
  logoutBtn: {
    marginTop: spacing.lg,
  },
  version: {
    marginTop: spacing.xl,
  },
});

export default ProfileScreen;
