import React, { useState } from 'react';
import { View, StyleSheet, Dimensions } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import AppText from '../../components/atoms/Text';
import Button from '../../components/atoms/Button';
import { spacing } from '../../theme';
import AsyncStorage from '@react-native-async-storage/async-storage';

const ONBOARDING_KEY = '@oh_churus_onboarding_done';
const { width } = Dimensions.get('window');

interface OnboardingScreenProps {
  onComplete: () => void;
}

const steps = [
  {
    icon: '🐿️',
    title: 'Bienvenido a Oh Churus!',
    description: 'Tu asistente para la vida cotidiana. Finanzas, salud, habitos y mas, todo en un solo lugar.',
  },
  {
    icon: '📁',
    title: 'Organiza por categorias',
    description: 'Crea categorias personales o compartidas con tu nucleo familiar. Cada gasto o ingreso se clasifica para un mejor control.',
  },
  {
    icon: '📅',
    title: 'Programa tus pagos',
    description: 'Configura movimientos recurrentes como arriendo o salario. Se generan automaticamente cada periodo.',
  },
  {
    icon: '✅',
    title: 'Confirma cuando pagues',
    description: 'Los movimientos presupuestados aparecen como pendientes. Confirmalos cuando se ejecuten y ajusta el monto si cambio.',
  },
  {
    icon: '📊',
    title: 'Analiza tu presupuesto',
    description: 'Graficos de dona, comparaciones presupuesto vs real, y exporta tu reporte a Excel cuando quieras.',
  },
];

const OnboardingScreen: React.FC<OnboardingScreenProps> = ({ onComplete }) => {
  const { colors } = useTheme();
  const [step, setStep] = useState(0);

  const handleNext = async () => {
    if (step < steps.length - 1) {
      setStep(step + 1);
    } else {
      await AsyncStorage.setItem(ONBOARDING_KEY, 'true');
      onComplete();
    }
  };

  const handleSkip = async () => {
    await AsyncStorage.setItem(ONBOARDING_KEY, 'true');
    onComplete();
  };

  const current = steps[step];

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={styles.content}>
        <AppText style={styles.icon}>{current.icon}</AppText>
        <AppText variant="title" align="center" style={styles.title}>{current.title}</AppText>
        <AppText variant="body" align="center" color={colors.textSecondary} style={styles.desc}>
          {current.description}
        </AppText>
      </View>

      {/* Dots */}
      <View style={styles.dots}>
        {steps.map((_, i) => (
          <View
            key={i}
            style={[styles.dot, {
              backgroundColor: i === step ? colors.primary : colors.border,
              width: i === step ? 24 : 8,
            }]}
          />
        ))}
      </View>

      <View style={styles.actions}>
        {step < steps.length - 1 ? (
          <>
            <Button title="Saltar" onPress={handleSkip} variant="outline" style={styles.btn} />
            <Button title="Siguiente" onPress={handleNext} style={styles.btn} />
          </>
        ) : (
          <Button title="Comenzar" onPress={handleNext} size="large" style={{ width: '100%' }} />
        )}
      </View>
    </View>
  );
};

export const isOnboardingDone = async (): Promise<boolean> => {
  const val = await AsyncStorage.getItem(ONBOARDING_KEY);
  return val === 'true';
};

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'space-between', padding: spacing.xl },
  content: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  icon: { fontSize: 80, marginBottom: spacing.lg },
  title: { marginBottom: spacing.md },
  desc: { maxWidth: width * 0.8, lineHeight: 24 },
  dots: { flexDirection: 'row', justifyContent: 'center', gap: 6, marginBottom: spacing.xl },
  dot: { height: 8, borderRadius: 4 },
  actions: { flexDirection: 'row', gap: spacing.sm },
  btn: { flex: 1 },
});

export default OnboardingScreen;
