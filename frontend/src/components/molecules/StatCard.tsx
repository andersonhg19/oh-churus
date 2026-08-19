import React from 'react';
import { View, StyleSheet } from 'react-native';
import Card from '../atoms/Card';
import AppText from '../atoms/Text';
import { useTheme } from '../../contexts/ThemeContext';
import { spacing } from '../../theme';
import { formatCurrency } from '../../utils/format';

interface StatCardProps {
  title: string;
  value: number;
  subtitle?: string;
  icon: string;
  color?: string;
  trend?: number;
  isCurrency?: boolean;
}

const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  subtitle,
  icon,
  color,
  trend,
  isCurrency = true,
}) => {
  const { colors } = useTheme();
  const displayColor = color || colors.primary;

  /*
   * Las cifras del panel se agrupan en UN elemento con su titulo delante. Sin
   * esto el lector va soltando "Gastos", "1.240.000", "este mes" como tres
   * cosas sueltas, y con cuatro tarjetas seguidas no hay forma de saber que
   * numero era de cual.
   */
  const loQueSeOye = [title, subtitle].filter(Boolean).join(', ');

  return (
    <Card style={styles.card}
      accessible
      accessibilityLabel={loQueSeOye}>
      <View style={styles.header}>
        <AppText variant="body" style={styles.icon}>{icon}</AppText>
        {trend !== undefined && (
          <AppText
            variant="caption"
            color={trend >= 0 ? colors.income : colors.expense}
          >
            {trend >= 0 ? '+' : ''}{trend.toFixed(1)}%
          </AppText>
        )}
      </View>
      <AppText variant="caption" numberOfLines={1}>{title}</AppText>
      <AppText
        variant="body"
        color={displayColor}
        style={styles.value}
        numberOfLines={1}
      >
        {isCurrency ? formatCurrency(value) : String(value)}
      </AppText>
      {subtitle ? (
        <AppText variant="caption" numberOfLines={1}>{subtitle}</AppText>
      ) : null}
    </Card>
  );
};

const styles = StyleSheet.create({
  card: {
    marginBottom: spacing.xs,
    padding: spacing.sm,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 2,
  },
  icon: {
    fontSize: 20,
  },
  value: {
    marginTop: 2,
    fontWeight: '700',
  },
});

export default StatCard;
