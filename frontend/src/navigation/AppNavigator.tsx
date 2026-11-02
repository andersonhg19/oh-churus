import React, { useState, useEffect } from 'react';
import { Text, View, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createDrawerNavigator, DrawerContentComponentProps } from '@react-navigation/drawer';
import { useTheme } from '../contexts/ThemeContext';
import { useAuth } from '../contexts/AuthContext';
import { AlcanceProvider } from '../contexts/AlcanceContext';
import OnboardingScreen, { isOnboardingDone } from '../screens/onboarding/OnboardingScreen';
import Spinner from '../components/atoms/Spinner';
import AppText from '../components/atoms/Text';
import Icono from '../components/atoms/Icono';

// Auth
import LoginScreen from '../screens/auth/LoginScreen';
import RegisterScreen from '../screens/auth/RegisterScreen';

// Lo nuevo (ola 6): la app al estilo Monefy
import InicioScreen from '../screens/inicio/InicioScreen';
import AnotarScreen from '../screens/anotar/AnotarScreen';
import PresupuestoScreen from '../screens/presupuesto/PresupuestoScreen';
import CategoriaScreen from '../screens/presupuesto/CategoriaScreen';
import CierreScreen from '../screens/presupuesto/CierreScreen';
import ResponsabilidadesScreen from '../screens/responsabilidades/ResponsabilidadesScreen';
import PorConfirmarScreen from '../screens/pendientes/PorConfirmarScreen';
import IngresosScreen from '../screens/ingresos/IngresosScreen';
import MovimientosScreen from '../screens/movimientos/MovimientosScreen';

// Lo que se conserva tal cual, ahora desde el menu
import CategoryDrillDownScreen from '../screens/summary/CategoryDrillDownScreen';
import MovementFormScreen from '../screens/movements/MovementFormScreen';
import MovementsListScreen from '../screens/movements/MovementsListScreen';
import ScheduledScreen from '../screens/scheduled/ScheduledScreen';
import ScheduledFormScreen from '../screens/scheduled/ScheduledFormScreen';
import SettingsScreen from '../screens/settings/SettingsScreen';
import ExportImportScreen from '../screens/settings/ExportImportScreen';
import PeriodConfigScreen from '../screens/settings/PeriodConfigScreen';
import CurrencyConfigScreen from '../screens/settings/CurrencyConfigScreen';
import HouseholdScreen from '../screens/settings/HouseholdScreen';
import CategoriesScreen from '../screens/categories/CategoriesScreen';
import CategoryFormScreen from '../screens/categories/CategoryFormScreen';
import AccountsScreen from '../screens/accounts/AccountsScreen';
import AccountFormScreen from '../screens/accounts/AccountFormScreen';
import ReconcileScreen from '../screens/accounts/ReconcileScreen';
import BalancesScreen from '../screens/splits/BalancesScreen';
import EnvelopesScreen from '../screens/budget/EnvelopesScreen';
import ImportScreen from '../screens/settings/ImportScreen';
import PlanScreen from '../screens/plan/PlanScreen';
import ConsolidatedScreen from '../screens/consolidated/ConsolidatedScreen';

// Administracion (ola 4): registradas siempre; quien no es administrador no
// tiene como llegar y el backend le responde 403.
import AdminUsersScreen from '../screens/admin/AdminUsersScreen';
import AdminRolesScreen from '../screens/admin/AdminRolesScreen';
import AdminModulesScreen from '../screens/admin/AdminModulesScreen';
import AdminAuditScreen from '../screens/admin/AdminAuditScreen';

// Ayuno
import FastingDashboardScreen from '../screens/fasting/FastingDashboardScreen';
import FastingHistoryScreen from '../screens/fasting/FastingHistoryScreen';
import FastingConfigScreen from '../screens/fasting/FastingConfigScreen';

/* Las pantallas de siempre tipan sus props con la pila en la que vivian; aqui
   todas comparten una sola, asi que se registran sin ese tipo. */
const pantalla = (c: React.ComponentType<any>) => c as React.ComponentType<any>;

const AuthStack = createNativeStackNavigator<any>();
const RootStack = createNativeStackNavigator<any>();
const Drawer = createDrawerNavigator();
const FastingTab = createBottomTabNavigator();
const Finanzas = createNativeStackNavigator<any>();

const AuthNavigator = () => {
  const { colors } = useTheme();
  return (
    <AuthStack.Navigator screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}>
      <AuthStack.Screen name="Login" component={pantalla(LoginScreen)} />
      <AuthStack.Screen name="Register" component={pantalla(RegisterScreen)} />
    </AuthStack.Navigator>
  );
};

/*
 * FINANZAS: una sola pila. Antes eran seis pestanas abajo (Inicio, Resumen,
 * Presupuesto, +, Programados, Ajustes) y cada una con su pila, asi que una
 * misma pantalla (el Plan, el Consolidado) vivia registrada dos veces. Ahora,
 * como en Monefy, la pantalla principal es la dona y todo lo demas se abre
 * desde el menu lateral. Las pantallas nuevas dibujan su propia barra; las de
 * siempre conservan la cabecera con flecha atras.
 */
const FinanzasNavigator = () => {
  const { colors } = useTheme();
  const cabecera = {
    headerShown: true,
    headerStyle: { backgroundColor: colors.surface },
    headerTintColor: colors.text,
    headerShadowVisible: false,
  };
  return (
    <Finanzas.Navigator screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}>
      <Finanzas.Screen name="Inicio" component={pantalla(InicioScreen)} />
      <Finanzas.Screen name="Presupuesto" component={pantalla(PresupuestoScreen)} />
      <Finanzas.Screen name="Categoria" component={pantalla(CategoriaScreen)} />
      <Finanzas.Screen name="Cierre" component={pantalla(CierreScreen)} />
      <Finanzas.Screen name="Responsabilidades" component={pantalla(ResponsabilidadesScreen)} />
      <Finanzas.Screen name="PorConfirmar" component={pantalla(PorConfirmarScreen)} />
      <Finanzas.Screen name="Ingresos" component={pantalla(IngresosScreen)} />
      <Finanzas.Screen name="Movimientos" component={pantalla(MovimientosScreen)} />

      <Finanzas.Group screenOptions={cabecera}>
        <Finanzas.Screen name="Plan" component={pantalla(PlanScreen)} options={{ title: 'Plan y quincenas' }} />
        <Finanzas.Screen name="ScheduledList" component={pantalla(ScheduledScreen)} options={{ title: 'Programados' }} />
        <Finanzas.Screen name="ScheduledForm" component={pantalla(ScheduledFormScreen)} options={{ title: 'Programado' }} />
        <Finanzas.Screen name="MovementsList" component={pantalla(MovementsListScreen)} options={{ title: 'Movimientos' }} />
        <Finanzas.Screen name="CategoryDrillDown" component={pantalla(CategoryDrillDownScreen)} options={({ route }: any) => ({ title: route.params?.categoryName || 'Categoría' })} />
        <Finanzas.Screen name="Envelopes" component={pantalla(EnvelopesScreen)} options={{ title: 'Sobres y arrastre' }} />
        <Finanzas.Screen name="Consolidated" component={pantalla(ConsolidatedScreen)} options={{ title: 'Consolidado' }} />
        <Finanzas.Screen name="Balances" component={pantalla(BalancesScreen)} options={{ title: 'Entre nosotros' }} />
        <Finanzas.Screen name="AccountsList" component={pantalla(AccountsScreen)} options={{ title: 'Cuentas' }} />
        <Finanzas.Screen name="AccountForm" component={pantalla(AccountFormScreen)} options={{ title: 'Cuenta' }} />
        <Finanzas.Screen name="Reconcile" component={pantalla(ReconcileScreen)} options={{ title: 'Conciliar' }} />
        <Finanzas.Screen name="SettingsMain" component={pantalla(SettingsScreen)} options={{ title: 'Ajustes' }} />
        <Finanzas.Screen name="ExportImport" component={pantalla(ExportImportScreen)} options={{ title: 'Exportar / Importar' }} />
        <Finanzas.Screen name="Import" component={pantalla(ImportScreen)} options={{ title: 'Importar del banco' }} />
        <Finanzas.Screen name="PeriodConfig" component={pantalla(PeriodConfigScreen)} options={{ title: 'Día de corte' }} />
        <Finanzas.Screen name="CurrencyConfig" component={pantalla(CurrencyConfigScreen)} options={{ title: 'Moneda y decimales' }} />
        <Finanzas.Screen name="Household" component={pantalla(HouseholdScreen)} options={{ title: 'Hogar' }} />
        <Finanzas.Screen name="CategoriesList" component={pantalla(CategoriesScreen)} options={{ title: 'Categorías' }} />
        <Finanzas.Screen name="CategoryForm" component={pantalla(CategoryFormScreen)} options={{ title: 'Categoría' }} />
        <Finanzas.Screen name="AdminUsers" component={pantalla(AdminUsersScreen)} options={{ title: 'Usuarios' }} />
        <Finanzas.Screen name="AdminRoles" component={pantalla(AdminRolesScreen)} options={{ title: 'Roles' }} />
        <Finanzas.Screen name="AdminModules" component={pantalla(AdminModulesScreen)} options={{ title: 'Módulos y permisos' }} />
        <Finanzas.Screen name="AdminAudit" component={pantalla(AdminAuditScreen)} options={{ title: 'Auditoría' }} />
      </Finanzas.Group>
    </Finanzas.Navigator>
  );
};

/** Lo que hay en el menu lateral, en el orden en que se usa. */
const MENU: { titulo: string; icono: string; destino: string; detalle?: string }[][] = [
  [
    { titulo: 'Inicio', icono: 'home', destino: 'Inicio' },
    { titulo: 'Presupuesto', icono: 'chart', destino: 'Presupuesto', detalle: 'Planeado, real y quién' },
    { titulo: 'Cierre de cada mes', icono: 'history', destino: 'Cierre', detalle: 'Lo esperado contra lo real' },
    { titulo: 'Por confirmar', icono: 'check', destino: 'PorConfirmar', detalle: 'Pagos del periodo' },
    { titulo: 'Responsabilidades', icono: 'people', destino: 'Responsabilidades', detalle: 'Lo mío y lo de los dos' },
    { titulo: 'Ingresos', icono: 'trending-up', destino: 'Ingresos' },
  ],
  [
    { titulo: 'Plan y quincenas', icono: 'calendar', destino: 'Plan', detalle: 'Calendario y proyección' },
    { titulo: 'Programados', icono: 'clock', destino: 'ScheduledList', detalle: 'Facturas, cuotas, ahorro, bolsas' },
    { titulo: 'Movimientos', icono: 'list', destino: 'Movimientos', detalle: 'Por categoría, desplegables' },
    { titulo: 'Buscar movimientos', icono: 'search', destino: 'MovementsList' },
    { titulo: 'Entre nosotros', icono: 'swap-horizontal', destino: 'Balances', detalle: 'Quién le debe a quién' },
    { titulo: 'Cuentas', icono: 'wallet', destino: 'AccountsList', detalle: 'Saldos y conciliar' },
  ],
  [
    { titulo: 'Consolidado', icono: 'bar-chart', destino: 'Consolidated' },
    { titulo: 'Exportar e importar', icono: 'upload', destino: 'ExportImport' },
    { titulo: 'Ajustes', icono: 'settings', destino: 'SettingsMain', detalle: 'Categorías, hogar, corte, moneda' },
  ],
];

const MenuFinanzas: React.FC<DrawerContentComponentProps> = ({ navigation }) => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const ir = (destino: string) => {
    navigation.closeDrawer();
    navigation.navigate('FinanceModule', destino === 'Inicio' ? { screen: 'Inicio' } : { screen: destino });
  };
  return (
    <ScrollView style={{ backgroundColor: colors.surface }} contentContainerStyle={menu.contenido}>
      <AppText variant="subtitle" style={{ marginBottom: 4 }}>Oh Churus</AppText>
      <AppText variant="caption" color={colors.textSecondary} style={{ marginBottom: 12 }}>{user?.name}</AppText>
      {MENU.map((grupo, g) => (
        <View key={g} style={[menu.grupo, g > 0 && { borderTopColor: colors.border, borderTopWidth: 1 }]}>
          {grupo.map((i) => (
            <TouchableOpacity key={i.destino} style={menu.item} onPress={() => ir(i.destino)}
              accessibilityRole="button" accessibilityLabel={i.detalle ? `${i.titulo}: ${i.detalle}` : i.titulo}>
              <Icono nombre={i.icono} color={colors.primary} />
              <View style={{ flex: 1 }}>
                <AppText variant="body">{i.titulo}</AppText>
                {!!i.detalle && <AppText variant="caption" color={colors.textMuted}>{i.detalle}</AppText>}
              </View>
            </TouchableOpacity>
          ))}
        </View>
      ))}
      <View style={[menu.grupo, { borderTopColor: colors.border, borderTopWidth: 1 }]}>
        <TouchableOpacity style={menu.item} onPress={() => { navigation.closeDrawer(); navigation.navigate('FastingModule'); }}
          accessibilityRole="button" accessibilityLabel="Ir al módulo de ayuno intermitente">
          <Icono nombre="clock" color={colors.textSecondary} />
          <AppText variant="body">Ayuno intermitente</AppText>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
};

const FastingTabs = () => {
  const { colors } = useTheme();
  return (
    <FastingTab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.border, borderTopWidth: 1, paddingBottom: 4, paddingTop: 4, height: 64 },
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarLabelStyle: { fontSize: 14, fontWeight: '600' },
      }}
    >
      <FastingTab.Screen name="FastingDashboard" component={pantalla(FastingDashboardScreen)}
        options={{ tabBarLabel: 'Dashboard', tabBarIcon: () => <Text style={{ fontSize: 22 }}>🕐</Text> }} />
      <FastingTab.Screen name="FastingHistory" component={pantalla(FastingHistoryScreen)}
        options={{ tabBarLabel: 'Historial', tabBarIcon: () => <Text style={{ fontSize: 22 }}>📋</Text> }} />
      <FastingTab.Screen name="FastingConfig" component={pantalla(FastingConfigScreen)}
        options={{ tabBarLabel: 'Config', tabBarIcon: () => <Text style={{ fontSize: 22 }}>⚙️</Text> }} />
    </FastingTab.Navigator>
  );
};

const ModuleDrawer = () => {
  const { colors } = useTheme();
  return (
    <Drawer.Navigator
      drawerContent={(props) => <MenuFinanzas {...props} />}
      screenOptions={{
        headerShown: false,
        drawerStyle: { backgroundColor: colors.surface, width: 300 },
      }}
    >
      <Drawer.Screen name="FinanceModule" component={FinanzasNavigator} options={{ title: 'Finanzas' }} />
      <Drawer.Screen name="FastingModule" component={FastingTabs}
        options={{ title: 'Ayuno Intermitente', headerShown: true, headerStyle: { backgroundColor: colors.surface }, headerTintColor: colors.text }} />
    </Drawer.Navigator>
  );
};

const AppNavigator: React.FC = () => {
  const { colors } = useTheme();
  const { isAuthenticated, loading } = useAuth();
  const [showOnboarding, setShowOnboarding] = useState<boolean | null>(null);

  useEffect(() => {
    isOnboardingDone().then((done) => setShowOnboarding(!done));
  }, []);

  if (loading || showOnboarding === null) return <Spinner fullScreen />;
  if (showOnboarding) return <OnboardingScreen onComplete={() => setShowOnboarding(false)} />;

  return (
    <NavigationContainer
      theme={{
        dark: true,
        colors: { primary: colors.primary, background: colors.background, card: colors.surface, text: colors.text, border: colors.border, notification: colors.danger },
        fonts: {
          regular: { fontFamily: 'System', fontWeight: '400' },
          medium: { fontFamily: 'System', fontWeight: '500' },
          bold: { fontFamily: 'System', fontWeight: '700' },
          heavy: { fontFamily: 'System', fontWeight: '800' },
        },
      }}
    >
      {isAuthenticated ? (
        <AlcanceProvider>
          <RootStack.Navigator screenOptions={{ headerShown: false }}>
            <RootStack.Screen name="MainTabs" component={ModuleDrawer} />
            <RootStack.Screen name="Anotar" component={pantalla(AnotarScreen)} options={{ presentation: 'fullScreenModal' }} />
            <RootStack.Screen
              name="MovementFormModal"
              component={pantalla(MovementFormScreen)}
              options={({ route }: any) => ({
                presentation: 'modal',
                headerShown: true,
                title: route.params?.movement ? 'Editar movimiento' : route.params?.parentMovement ? 'Sub-gasto' : 'Nuevo movimiento',
                headerStyle: { backgroundColor: colors.surface },
                headerTintColor: colors.text,
                headerShadowVisible: false,
              })}
            />
          </RootStack.Navigator>
        </AlcanceProvider>
      ) : (
        <AuthNavigator />
      )}
    </NavigationContainer>
  );
};

const menu = StyleSheet.create({
  contenido: { padding: 20, paddingTop: 40 },
  grupo: { paddingVertical: 8 },
  item: { flexDirection: 'row', alignItems: 'center', gap: 14, minHeight: 52, paddingVertical: 6 },
});

export default AppNavigator;
