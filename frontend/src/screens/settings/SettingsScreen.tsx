import React from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Icono from '../../components/atoms/Icono';
import Card from '../../components/atoms/Card';
import Button from '../../components/atoms/Button';
import { spacing } from '../../theme';
import { confirmarAccion } from '../../utils/confirmar';
import { useMenu } from '../../hooks/useMenu';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { CategoryTree } from '../../types';

type SettingsStackParamList = {
  SettingsMain: undefined;
  CategoriesList: undefined;
  CategoryForm: { category?: CategoryTree };
  ExportImport: undefined;
  PeriodConfig: undefined;
  CurrencyConfig: undefined;
  Household: undefined;
  AdminUsers: undefined;
  AdminRoles: undefined;
  AdminModules: undefined;
  AdminAudit: undefined;
};

type Props = NativeStackScreenProps<SettingsStackParamList, 'SettingsMain'>;

const SettingsScreen: React.FC<Props> = ({ navigation }) => {
  const { colors, isDark, toggleTheme } = useTheme();
  const { user, logout } = useAuth();
  /* Que se pinta lo dice el servidor. Si no contesta, se cae al rol del
     token; ver useMenu. Y en cualquiera de los dos casos, quien no deba entrar
     recibe un 403 aunque llegue a la pantalla: esto es para no ensenar puertas
     que no se pueden abrir, no para cerrarlas. */
  const { puedeVer } = useMenu();
  const veLaAdministracion = puedeVer('AdminUsers', true);

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
      {/* El emoji, el nombre y el correo son una sola cosa: quien eres. */}
      <View
        style={styles.avatarSection}
        accessible
        accessibilityLabel={`Tu cuenta: ${user?.name || 'Usuario'}, ${user?.email || 'sin correo'}`}
      >
        <View style={[styles.avatar, { width: 64, height: 64, borderRadius: 32, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center', alignSelf: 'center' }]}>
          <AppText variant="title" style={{ color: '#1A1405', fontSize: 30 }}>{(user?.name || '?').charAt(0).toUpperCase()}</AppText>
        </View>
        <AppText variant="subtitle">{user?.name || 'Usuario'}</AppText>
        <AppText variant="caption" color={colors.textSecondary}>{user?.email}</AppText>
      </View>

      {/* Menu items */}
      <Card
        style={styles.menuItem}
        accessibilityLabel="Exportar tus datos a Excel"
        onPress={() => navigation.navigate('ExportImport')}
      >
        <View style={styles.menuRow}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Icono nombre="upload" color={colors.primary} /><AppText variant="body">Exportar a Excel</AppText></View>
          <AppText variant="body" color={colors.textMuted}>›</AppText>
        </View>
      </Card>

      <Card
        style={styles.menuItem}
        accessibilityLabel="Configurar el periodo del mes"
        onPress={() => navigation.navigate('PeriodConfig')}
      >
        <View style={styles.menuRow}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Icono nombre="calendar" color={colors.primary} /><AppText variant="body">Día de corte del periodo</AppText></View>
          <AppText variant="body" color={colors.textMuted}>›</AppText>
        </View>
      </Card>

      <Card
        style={styles.menuItem}
        accessibilityLabel="Elegir la moneda y los decimales"
        onPress={() => navigation.navigate('CurrencyConfig')}
      >
        <View style={styles.menuRow}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Icono nombre="coin" color={colors.primary} /><AppText variant="body">Moneda y decimales</AppText></View>
          <AppText variant="body" color={colors.textMuted}>›</AppText>
        </View>
      </Card>

      <Card
        style={styles.menuItem}
        accessibilityLabel="Núcleo familiar"
        onPress={() => navigation.navigate('Household')}
      >
        <View style={styles.menuRow}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Icono nombre="home" color={colors.primary} /><AppText variant="body">Hogar y apodos</AppText></View>
          <AppText variant="body" color={colors.textMuted}>›</AppText>
        </View>
      </Card>

      <Card
        style={styles.menuItem}
        accessibilityLabel="Gestionar las categorías"
        onPress={() => navigation.navigate('CategoriesList')}
      >
        <View style={styles.menuRow}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Icono nombre="list" color={colors.primary} /><AppText variant="body">Categorías</AppText></View>
          <AppText variant="body" color={colors.textMuted}>›</AppText>
        </View>
      </Card>

      {veLaAdministracion && (
        <>
          <AppText variant="caption" color={colors.textSecondary} style={styles.seccion}>
            Administracion
          </AppText>

          <Card
            style={styles.menuItem}
            accessibilityLabel="Administrar las cuentas y sus roles"
            onPress={() => navigation.navigate('AdminUsers')}
          >
            <View style={styles.menuRow}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Icono nombre="users" color={colors.primary} /><AppText variant="body">Usuarios y roles</AppText></View>
              <AppText variant="body" color={colors.textMuted}>›</AppText>
            </View>
          </Card>

          <Card
            style={styles.menuItem}
            accessibilityLabel="Administrar los roles que existen"
            onPress={() => navigation.navigate('AdminRoles')}
          >
            <View style={styles.menuRow}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Icono nombre="key" color={colors.primary} /><AppText variant="body">Roles</AppText></View>
              <AppText variant="body" color={colors.textMuted}>›</AppText>
            </View>
          </Card>

          <Card
            style={styles.menuItem}
            accessibilityLabel="Administrar los modulos y los permisos de cada rol"
            onPress={() => navigation.navigate('AdminModules')}
          >
            <View style={styles.menuRow}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Icono nombre="shield" color={colors.primary} /><AppText variant="body">Módulos y permisos</AppText></View>
              <AppText variant="body" color={colors.textMuted}>›</AppText>
            </View>
          </Card>

          <Card
            style={styles.menuItem}
            accessibilityLabel="Ver el registro de quien hizo que"
            onPress={() => navigation.navigate('AdminAudit')}
          >
            <View style={styles.menuRow}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Icono nombre="history" color={colors.primary} /><AppText variant="body">Auditoría</AppText></View>
              <AppText variant="body" color={colors.textMuted}>›</AppText>
            </View>
          </Card>
        </>
      )}

      <Card style={styles.menuItem}>
        <View style={styles.menuRow}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Icono nombre="settings" color={colors.primary} /><AppText variant="body">Apariencia</AppText></View>
          <Button
            title={isDark ? 'Modo claro' : 'Modo oscuro'}
            accessibilityLabel={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
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
  seccion: { marginTop: spacing.md, marginBottom: spacing.xs, textTransform: 'uppercase' },
  menuRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  logoutBtn: { marginTop: spacing.xl },
  version: { marginTop: spacing.xl },
});

export default SettingsScreen;
