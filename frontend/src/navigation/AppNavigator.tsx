import React, { useState, useEffect } from 'react';
import { Text, View, StyleSheet, TouchableOpacity } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createDrawerNavigator } from '@react-navigation/drawer';
import { useTheme } from '../contexts/ThemeContext';
import { useAuth } from '../contexts/AuthContext';
import OnboardingScreen, { isOnboardingDone } from '../screens/onboarding/OnboardingScreen';
import Spinner from '../components/atoms/Spinner';

// Auth screens
import LoginScreen from '../screens/auth/LoginScreen';
import RegisterScreen from '../screens/auth/RegisterScreen';

// Main screens
import DashboardScreen from '../screens/dashboard/DashboardScreen';
import SummaryScreen from '../screens/summary/SummaryScreen';
import CategoryDrillDownScreen from '../screens/summary/CategoryDrillDownScreen';
import MovementFormScreen from '../screens/movements/MovementFormScreen';
import MovementsListScreen from '../screens/movements/MovementsListScreen';
import ScheduledScreen from '../screens/scheduled/ScheduledScreen';
import ScheduledFormScreen from '../screens/scheduled/ScheduledFormScreen';
import SettingsScreen from '../screens/settings/SettingsScreen';
import ExportImportScreen from '../screens/settings/ExportImportScreen';
import PeriodConfigScreen from '../screens/settings/PeriodConfigScreen';
import HouseholdScreen from '../screens/settings/HouseholdScreen';
import CategoriesScreen from '../screens/categories/CategoriesScreen';
import CategoryFormScreen from '../screens/categories/CategoryFormScreen';

// Budget & Consolidated screens
import BudgetScreen from '../screens/budget/BudgetScreen';
import ConsolidatedScreen from '../screens/consolidated/ConsolidatedScreen';

// Fasting screens
import FastingDashboardScreen from '../screens/fasting/FastingDashboardScreen';
import FastingHistoryScreen from '../screens/fasting/FastingHistoryScreen';
import FastingConfigScreen from '../screens/fasting/FastingConfigScreen';

import { Movement, CategoryTree, ScheduledMovement } from '../types';

// --- Type definitions ---
type AuthStackParamList = {
  Login: undefined;
  Register: undefined;
};

type RootStackParamList = {
  MainTabs: undefined;
  MovementFormModal: { movement?: Movement; parentMovement?: Movement };
};

type DashboardStackParamList = {
  DashboardMain: undefined;
  MovementsList: undefined;
};

type SummaryStackParamList = {
  SummaryMain: undefined;
  CategoryDrillDown: { categoryId: string; categoryName: string; color: string; startDate: string; endDate: string };
};

type ScheduledStackParamList = {
  ScheduledList: undefined;
  ScheduledForm: { scheduled?: ScheduledMovement };
};

type BudgetStackParamList = {
  BudgetMain: undefined;
  Consolidated: undefined;
};

type SettingsStackParamList = {
  SettingsMain: undefined;
  ExportImport: undefined;
  PeriodConfig: undefined;
  Household: undefined;
  Consolidated: undefined;
  CategoriesList: undefined;
  CategoryForm: { category?: CategoryTree };
};

// --- Stacks ---
const AuthStack = createNativeStackNavigator<AuthStackParamList>();
const RootStack = createNativeStackNavigator<RootStackParamList>();
const Drawer = createDrawerNavigator();
const FastingTab = createBottomTabNavigator();
const DashboardStack = createNativeStackNavigator<DashboardStackParamList>();
const SummaryStack = createNativeStackNavigator<SummaryStackParamList>();
const ScheduledStack = createNativeStackNavigator<ScheduledStackParamList>();
const BudgetStack = createNativeStackNavigator<BudgetStackParamList>();
const SettingsStack = createNativeStackNavigator<SettingsStackParamList>();
const Tab = createBottomTabNavigator();

// --- Stack navigators ---
const AuthNavigator = () => {
  const { colors } = useTheme();
  return (
    <AuthStack.Navigator screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}>
      <AuthStack.Screen name="Login" component={LoginScreen} />
      <AuthStack.Screen name="Register" component={RegisterScreen} />
    </AuthStack.Navigator>
  );
};

const DashboardNavigator = () => {
  const { colors } = useTheme();
  return (
    <DashboardStack.Navigator screenOptions={{
      headerStyle: { backgroundColor: colors.surface },
      headerTintColor: colors.text,
      headerShadowVisible: false,
      contentStyle: { backgroundColor: colors.background },
    }}>
      <DashboardStack.Screen name="DashboardMain" component={DashboardScreen} options={{ headerShown: false }} />
      <DashboardStack.Screen name="MovementsList" component={MovementsListScreen} options={{ title: 'Movimientos' }} />
    </DashboardStack.Navigator>
  );
};

const SummaryNavigator = () => {
  const { colors } = useTheme();
  return (
    <SummaryStack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: colors.surface },
        headerTintColor: colors.text,
        headerShadowVisible: false,
        contentStyle: { backgroundColor: colors.background },
      }}
    >
      <SummaryStack.Screen name="SummaryMain" component={SummaryScreen} options={{ headerShown: false }} />
      <SummaryStack.Screen name="CategoryDrillDown" component={CategoryDrillDownScreen} options={({ route }) => ({ title: route.params.categoryName })} />
    </SummaryStack.Navigator>
  );
};

const ScheduledNavigator = () => {
  const { colors } = useTheme();
  return (
    <ScheduledStack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: colors.surface },
        headerTintColor: colors.text,
        headerShadowVisible: false,
        contentStyle: { backgroundColor: colors.background },
      }}
    >
      <ScheduledStack.Screen name="ScheduledList" component={ScheduledScreen} options={{ headerShown: false }} />
      <ScheduledStack.Screen name="ScheduledForm" component={ScheduledFormScreen} options={{ title: 'Programado' }} />
    </ScheduledStack.Navigator>
  );
};

const SettingsNavigator = () => {
  const { colors } = useTheme();
  return (
    <SettingsStack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: colors.surface },
        headerTintColor: colors.text,
        headerShadowVisible: false,
        contentStyle: { backgroundColor: colors.background },
      }}
    >
      <SettingsStack.Screen name="SettingsMain" component={SettingsScreen} options={{ headerShown: false }} />
      <SettingsStack.Screen name="ExportImport" component={ExportImportScreen} options={{ title: 'Exportar / Importar' }} />
      <SettingsStack.Screen name="PeriodConfig" component={PeriodConfigScreen} options={{ title: 'Configuracion de periodo' }} />
      <SettingsStack.Screen name="Household" component={HouseholdScreen} options={{ title: 'Nucleo Familiar' }} />
      <SettingsStack.Screen name="CategoriesList" component={CategoriesScreen} options={{ title: 'Categorias' }} />
      <SettingsStack.Screen name="CategoryForm" component={CategoryFormScreen} options={{ title: 'Categoria' }} />
      <SettingsStack.Screen name="Consolidated" component={ConsolidatedScreen} options={{ title: 'Consolidado Mensual' }} />
    </SettingsStack.Navigator>
  );
};

// --- Budget Navigator ---
const BudgetNavigator = () => {
  const { colors } = useTheme();
  return (
    <BudgetStack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: colors.surface },
        headerTintColor: colors.text,
        headerShadowVisible: false,
      }}
    >
      <BudgetStack.Screen name="BudgetMain" component={BudgetScreen} options={{ title: 'Presupuesto' }} />
      <BudgetStack.Screen name="Consolidated" component={ConsolidatedScreen} options={{ title: 'Consolidado' }} />
    </BudgetStack.Navigator>
  );
};

// --- Placeholder for center FAB tab ---
const EmptyScreen = () => <View />;

// --- Center FAB as proper component (avoids hooks violation) ---
const CenterTabButton: React.FC<any> = ({ onPress, ...rest }) => {
  const { colors } = useTheme();
  return (
    <TouchableOpacity
      style={fabStyles.wrapper}
      onPress={onPress}
      activeOpacity={0.85}
    >
      <View style={[fabStyles.fab, { backgroundColor: colors.primary, borderColor: colors.surface }]}>
        <Text style={[fabStyles.icon, { color: colors.surface }]}>+</Text>
      </View>
    </TouchableOpacity>
  );
};

// --- Main Tabs ---
const MainTabs = () => {
  const { colors } = useTheme();
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarStyle: {
          backgroundColor: colors.surface,
          borderTopColor: colors.border,
          borderTopWidth: 1,
          paddingBottom: 4,
          paddingTop: 4,
          height: 64,
          overflow: 'visible' as any,
        },
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: '600',
        },
      }}
    >
      <Tab.Screen
        name="Dashboard"
        component={DashboardNavigator}
        options={{
          tabBarLabel: 'Inicio',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 22 }}>🏠</Text>,
        }}
      />
      <Tab.Screen
        name="Summary"
        component={SummaryNavigator}
        options={{
          tabBarLabel: 'Resumen',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 22 }}>📊</Text>,
        }}
        listeners={({ navigation }) => ({
          tabPress: () => navigation.reset({ index: 0, routes: [{ name: 'Summary' }] }),
        })}
      />
      {/* Presupuesto estaba escrito, con su pantalla, su navegador y sus
          pruebas... y no se monto nunca en ninguna pestana. En una app de
          presupuestos, era la seccion que le da sentido al resto: sin ella
          la parte de "Ejecucion presupuestal" del Consolidado no tenia de
          donde salir y quedaba siempre vacia. */}
      <Tab.Screen
        name="Budget"
        component={BudgetNavigator}
        options={{
          tabBarLabel: 'Presupuesto',
          tabBarIcon: () => <Text style={{ fontSize: 22 }}>🎯</Text>,
        }}
      />
      <Tab.Screen
        name="AddAction"
        component={EmptyScreen}
        options={{
          tabBarLabel: '',
          tabBarIcon: () => null,
          tabBarButton: (props) => <CenterTabButton {...props} />,
        }}
        listeners={({ navigation }) => ({
          tabPress: (e) => {
            e.preventDefault();
            navigation.navigate('MovementFormModal');
          },
        })}
      />
      <Tab.Screen
        name="Scheduled"
        component={ScheduledNavigator}
        options={{
          tabBarLabel: 'Programados',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 22 }}>📅</Text>,
        }}
        listeners={({ navigation }) => ({
          tabPress: () => navigation.reset({ index: 0, routes: [{ name: 'Scheduled' }] }),
        })}
      />
      <Tab.Screen
        name="Settings"
        component={SettingsNavigator}
        options={{
          tabBarLabel: 'Ajustes',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 22 }}>⚙️</Text>,
        }}
        listeners={({ navigation }) => ({
          tabPress: () => navigation.reset({ index: 0, routes: [{ name: 'Settings' }] }),
        })}
      />
    </Tab.Navigator>
  );
};

// --- Fasting Tabs ---
const FastingTabs = () => {
  const { colors } = useTheme();
  return (
    <FastingTab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarStyle: {
          backgroundColor: colors.surface,
          borderTopColor: colors.border,
          borderTopWidth: 1,
          paddingBottom: 4,
          paddingTop: 4,
          height: 64,
        },
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarLabelStyle: { fontSize: 11, fontWeight: '600' },
      }}
    >
      <FastingTab.Screen
        name="FastingDashboard"
        component={FastingDashboardScreen}
        options={{ tabBarLabel: 'Dashboard', tabBarIcon: () => <Text style={{ fontSize: 22 }}>🕐</Text> }}
      />
      <FastingTab.Screen
        name="FastingHistory"
        component={FastingHistoryScreen}
        options={{ tabBarLabel: 'Historial', tabBarIcon: () => <Text style={{ fontSize: 22 }}>📋</Text> }}
      />
      <FastingTab.Screen
        name="FastingConfig"
        component={FastingConfigScreen}
        options={{ tabBarLabel: 'Config', tabBarIcon: () => <Text style={{ fontSize: 22 }}>⚙️</Text> }}
      />
    </FastingTab.Navigator>
  );
};

// --- Module Drawer ---
const ModuleDrawer = () => {
  const { colors } = useTheme();
  return (
    <Drawer.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: colors.surface },
        headerTintColor: colors.text,
        headerShadowVisible: false,
        drawerStyle: { backgroundColor: colors.surface, width: 280 },
        drawerActiveTintColor: colors.primary,
        drawerInactiveTintColor: colors.textSecondary,
        drawerLabelStyle: { fontSize: 16 },
      }}
    >
      <Drawer.Screen
        name="FinanceModule"
        component={MainTabs}
        options={{ title: 'Finanzas Personales', drawerIcon: () => <Text style={{ fontSize: 20 }}>💰</Text> }}
      />
      <Drawer.Screen
        name="FastingModule"
        component={FastingTabs}
        options={{ title: 'Ayuno Intermitente', drawerIcon: () => <Text style={{ fontSize: 20 }}>🕐</Text> }}
      />
    </Drawer.Navigator>
  );
};

const fabStyles = StyleSheet.create({
  wrapper: {
    top: -26,
    justifyContent: 'center',
    alignItems: 'center',
    width: 72,
    height: 72,
  },
  fab: {
    width: 60,
    height: 60,
    borderRadius: 30,
    borderWidth: 3,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 8,
    elevation: 10,
  },
  icon: {
    fontSize: 30,
    fontWeight: '400',
    marginTop: -2,
  },
});

// --- Root Navigator ---
const AppNavigator: React.FC = () => {
  const { colors } = useTheme();
  const { isAuthenticated, loading } = useAuth();
  const [showOnboarding, setShowOnboarding] = useState<boolean | null>(null);

  useEffect(() => {
    isOnboardingDone().then(done => setShowOnboarding(!done));
  }, []);

  if (loading || showOnboarding === null) {
    return <Spinner fullScreen />;
  }

  if (showOnboarding) {
    return <OnboardingScreen onComplete={() => setShowOnboarding(false)} />;
  }

  return (
    <NavigationContainer
      theme={{
        dark: true,
        colors: {
          primary: colors.primary,
          background: colors.background,
          card: colors.surface,
          text: colors.text,
          border: colors.border,
          notification: colors.danger,
        },
        fonts: {
          regular: { fontFamily: 'System', fontWeight: '400' },
          medium: { fontFamily: 'System', fontWeight: '500' },
          bold: { fontFamily: 'System', fontWeight: '700' },
          heavy: { fontFamily: 'System', fontWeight: '800' },
        },
      }}
    >
      {isAuthenticated ? (
        <RootStack.Navigator screenOptions={{ headerShown: false }}>
          <RootStack.Screen name="MainTabs" component={ModuleDrawer} />
          <RootStack.Screen
            name="MovementFormModal"
            component={MovementFormScreen}
            options={{
              presentation: 'modal',
              headerShown: true,
              title: 'Nuevo Movimiento',
              headerStyle: { backgroundColor: colors.surface },
              headerTintColor: colors.text,
              headerShadowVisible: false,
            }}
          />
        </RootStack.Navigator>
      ) : (
        <AuthNavigator />
      )}
    </NavigationContainer>
  );
};

export default AppNavigator;
