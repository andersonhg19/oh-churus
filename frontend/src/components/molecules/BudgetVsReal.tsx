import React from 'react';
import { View, StyleSheet } from 'react-native';
import AppText from '../atoms/Text';
import { useTheme } from '../../contexts/ThemeContext';
import { formatCurrency } from '../../utils/format';
import { CategorySummary } from '../../types';

interface BudgetVsRealProps {
  categories: CategorySummary[];
  budgetByCategory: { [categoryId: string]: number };
}

const BudgetVsReal: React.FC<BudgetVsRealProps> = ({ categories, budgetByCategory }) => {
  const { colors } = useTheme();

  const expenseCategories = categories.filter(c => c.categoryType === 'EXPENSE' && c.total > 0);
  if (expenseCategories.length === 0) return null;

  const maxValue = Math.max(
    ...expenseCategories.map(c => Math.max(c.total, budgetByCategory[String(c.categoryId)] || 0))
  );

  return (
    <View style={styles.container}>
      <AppText variant="label" style={styles.title}>Presupuesto vs Real</AppText>
      {expenseCategories.map(cat => {
        const budget = budgetByCategory[String(cat.categoryId)] || 0;
        const real = cat.total;
        const budgetWidth = maxValue > 0 ? (budget / maxValue) * 100 : 0;
        const realWidth = maxValue > 0 ? (real / maxValue) * 100 : 0;
        const overBudget = real > budget && budget > 0;

        return (
          <View key={String(cat.categoryId)} style={styles.row}>
            <AppText variant="caption" style={styles.label} numberOfLines={1}>
              {cat.categoryName}
            </AppText>
            <View style={styles.barsContainer}>
              {budget > 0 && (
                <View style={[styles.bar, styles.budgetBar, { width: `${budgetWidth}%`, backgroundColor: colors.border }]}>
                  <AppText variant="caption" style={styles.barText} numberOfLines={1}>
                    {formatCurrency(budget)}
                  </AppText>
                </View>
              )}
              <View style={[styles.bar, styles.realBar, {
                width: `${Math.max(realWidth, 5)}%`,
                backgroundColor: overBudget ? colors.expense : (cat.color || colors.primary),
              }]}>
                <AppText variant="caption" style={styles.barText} numberOfLines={1}>
                  {formatCurrency(real)}
                </AppText>
              </View>
            </View>
          </View>
        );
      })}
      <View style={styles.legend}>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: colors.border }]} />
          <AppText variant="caption" color={colors.textMuted}>Presupuesto</AppText>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: colors.primary }]} />
          <AppText variant="caption" color={colors.textMuted}>Real</AppText>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: colors.expense }]} />
          <AppText variant="caption" color={colors.textMuted}>Excedido</AppText>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { marginTop: 16 },
  title: { marginBottom: 12 },
  row: { marginBottom: 10 },
  label: { marginBottom: 4, fontWeight: '600' },
  barsContainer: { gap: 3 },
  bar: {
    height: 22,
    borderRadius: 6,
    justifyContent: 'center',
    paddingHorizontal: 8,
    minWidth: 40,
  },
  budgetBar: { opacity: 0.5 },
  realBar: {},
  barText: { color: '#FFF', fontSize: 11, fontWeight: '600' },
  legend: { flexDirection: 'row', gap: 16, marginTop: 8 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  legendDot: { width: 10, height: 10, borderRadius: 5 },
});

export default BudgetVsReal;
