import React from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Button from '../../components/atoms/Button';
import { spacing } from '../../theme';
import { confirmarAccion } from '../../utils/confirmar';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { CategoryTree } from '../../types';

type SettingsStackParamList = {
  SettingsMain: undefined;
  CategoriesList: undefined;
  AccountsList: undefined;
  Balances: undefined;
  CategoryForm: { category?: CategoryTree };
  ExportImport: undefined;
  PeriodConfig: undefined;
  Household: undefined;
  Consolidated: undefined;
};

type Props = NativeStackScreenProps<SettingsStackParamList, 'SettingsMain'>;

const SettingsScreen: React.FC<Props> = ({ navigation }) => {
  const { colors, isDark, toggleTheme } = useTheme();
  const { user, logout } = useAuth();

  const handleLogout = () =>
    confirmarAccion({
      titulo: 'Cerrar sesion',
      mensaje: 'Estas seguro?',
      textoConfirmar: 'Si, salir',
      onConfirmar: logout,
    });

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
    >
      {/* User info */}
      <View style={styles.avatarSection}>
        <AppText variant="title" style={styles.avatar}>🐿️</AppText>
        <AppText variant="subtitle">{user?.name || 'Usuario'}</AppText>
        <AppText variant="caption" color={colors.textSecondary}>{user?.email}</AppText>
      </View>

      {/* Menu items */}
      <Card style={styles.menuItem} onPress={() => navigation.navigate('ExportImport')}>
        <View style={styles.menuRow}>
          <AppText variant="body">📊 Exportar / Importar</AppText>
          <AppText variant="body" color={colors.textMuted}>›</AppText>
        </View>
      </Card>

      <Card style={styles.menuItem} onPress={() => navigation.navigate('PeriodConfig')}>
        <View style={styles.menuRow}>
          <AppText variant="body">📅 Configuracion de periodo</AppText>
          <AppText variant="body" color={colors.textMuted}>›</AppText>
        </View>
      </Card>

      <Card style={styles.menuItem} onPress={() => navigation.navigate('Household')}>
        <View style={styles.menuRow}>
          <AppText variant="body">🏠 Nucleo Familiar</AppText>
          <AppText variant="body" color={colors.textMuted}>›</AppText>
        </View>
      </Card>

      <Card style={styles.menuItem} onPress={() => navigation.navigate('AccountsList')}>
        <View style={styles.menuRow}>
          <AppText variant="body">🏦 Cuentas y saldos</AppText>
          <AppText variant="body" color={colors.textMuted}>›</AppText>
        </View>
      </Card>

      <Card style={styles.menuItem} onPress={() => navigation.navigate('Balances')}>
        <View style={styles.menuRow}>
          <AppText variant="body">🤝 Cuentas entre nosotros</AppText>
          <AppText variant="body" color={colors.textMuted}>›</AppText>
        </View>
      </Card>

      <Card style={styles.menuItem} onPress={() => navigation.navigate('CategoriesList')}>
        <View style={styles.menuRow}>
          <AppText variant="body">📁 Gestionar Categorias</AppText>
          <AppText variant="body" color={colors.textMuted}>›</AppText>
        </View>
      </Card>

      <Card style={styles.menuItem} onPress={() => navigation.navigate('Consolidated')}>
        <View style={styles.menuRow}>
          <AppText variant="body">📊 Consolidado Mensual</AppText>
          <AppText variant="body" color={colors.textMuted}>›</AppText>
        </View>
      </Card>

      <Card style={styles.menuItem}>
        <View style={styles.menuRow}>
          <AppText variant="body">🎨 Apariencia</AppText>
          <Button
            title={isDark ? 'Modo claro' : 'Modo oscuro'}
            onPress={toggleTheme}
            variant="outline"
            size="small"
          />
        </View>
      </Card>

      {/* Logout */}
      <Button title="Cerrar Sesion" onPress={handleLogout} variant="danger" style={styles.logoutBtn} />

      <AppText variant="caption" align="center" color={colors.textMuted} style={styles.version}>
        Oh Churus! v3.0.0
      </AppText>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.xl },
  avatarSection: { alignItems: 'center', marginBottom: spacing.xl },
  avatar: { fontSize: 64, marginBottom: spacing.sm },
  menuItem: { marginBottom: spacing.sm },
  menuRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  logoutBtn: { marginTop: spacing.xl },
  version: { marginTop: spacing.xl },
});

export default SettingsScreen;
