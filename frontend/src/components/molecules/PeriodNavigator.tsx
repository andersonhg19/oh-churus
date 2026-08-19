import React from 'react';
import { View, TouchableOpacity, StyleSheet } from 'react-native';
import AppText from '../atoms/Text';
import { useTheme } from '../../contexts/ThemeContext';
import { spacing } from '../../theme';
import { getMonthName } from '../../utils/format';

interface PeriodNavigatorProps {
  periodStart: string;
  periodEnd: string;
  onPrevious: () => void;
  onNext: () => void;
  canGoNext?: boolean;
}

const PeriodNavigator: React.FC<PeriodNavigatorProps> = ({
  periodStart,
  periodEnd,
  onPrevious,
  onNext,
  canGoNext = true,
}) => {
  const { colors } = useTheme();

  // Mostrar el mes con mayor cobertura (punto medio del periodo)
  const startDate = new Date(periodStart + 'T12:00:00');
  const endDate = new Date(periodEnd + 'T12:00:00');
  const midDate = new Date((startDate.getTime() + endDate.getTime()) / 2);
  const label = `${getMonthName(midDate.getMonth())} ${midDate.getFullYear()}`;

  return (
    <View style={[styles.container, { backgroundColor: colors.card, borderColor: colors.border }]}>
      {/*
        * Un lector de pantalla lee "<" como "menor que" y ">" como "mayor
        * que". Sin etiqueta, los dos botones que mueven el periodo —que son la
        * navegacion principal de media app— no significan nada.
        */}
      <TouchableOpacity
        onPress={onPrevious}
        style={styles.arrow}
        accessibilityRole="button"
        accessibilityLabel="Periodo anterior"
      >
        <AppText variant="subtitle" color={colors.primary}>{'<'}</AppText>
      </TouchableOpacity>
      {/* El periodo se agrupa en UN elemento: si no, el lector dice el mes por
          un lado y las dos fechas por otro, como si fueran tres cosas. */}
      <View
        style={styles.center}
        accessible
        accessibilityLabel={`Periodo ${label}, del ${periodStart} al ${periodEnd}`}
      >
        <AppText variant="body" style={styles.label}>{label}</AppText>
        <AppText variant="caption" color={colors.textMuted}>
          {periodStart} → {periodEnd}
        </AppText>
      </View>
      <TouchableOpacity
        onPress={onNext}
        style={styles.arrow}
        disabled={!canGoNext}
        accessibilityRole="button"
        accessibilityLabel="Periodo siguiente"
        /* Sin el estado, quien no ve el gris pulsa y no pasa nada, sin saber
           que es que ya esta en el mes actual. */
        accessibilityState={{ disabled: !canGoNext }}
        accessibilityHint={canGoNext ? undefined : 'Ya estas en el periodo actual'}
      >
        <AppText variant="subtitle" color={canGoNext ? colors.primary : colors.textMuted}>{'>'}</AppText>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: spacing.sm,
  },
  arrow: {
    padding: spacing.sm,
    minWidth: 40,
    alignItems: 'center',
  },
  center: {
    flex: 1,
    alignItems: 'center',
  },
  label: {
    fontWeight: '700',
  },
});

export default PeriodNavigator;
