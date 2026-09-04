import React from 'react';
import { View, StyleSheet } from 'react-native';
import AppText from '../atoms/Text';
import { useTheme } from '../../contexts/ThemeContext';
import { formatCurrency } from '../../utils/format';
import { textoLegibleSobre } from '../../theme/contraste';
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
                  {/* El texto era blanco fijo. La barra del presupuesto se
                      pinta con el color del borde, que en el tema claro es un
                      beige: blanco sobre beige no se lee. Ahora el color se
                      calcula a partir del fondo real de cada barra. */}
                  <AppText
                    variant="caption"
                    color={textoLegibleSobre(colors.border)}
                    style={styles.barText}
                    numberOfLines={1}
                  >
                    {formatCurrency(budget)}
                  </AppText>
                </View>
              )}
              <View style={[styles.bar, styles.realBar, {
                width: `${Math.max(realWidth, 5)}%`,
                backgroundColor: overBudget ? colors.expense : (cat.color || colors.primary),
              }]}>
                {/* Igual aqui: el color de la categoria lo elige el usuario y
                    puede ser un amarillo claro. */}
                <AppText
                  variant="caption"
                  color={textoLegibleSobre(overBudget ? colors.expense : (cat.color || colors.primary))}
                  style={styles.barText}
                  numberOfLines={1}
                >
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
    height: 26,
    borderRadius: 6,
    justifyContent: 'center',
    paddingHorizontal: 8,
    minWidth: 40,
  },
  budgetBar: { opacity: 0.5 },
  realBar: {},
  /* Era fontSize 11 con el color escrito a mano. La auditoria pedia
     justamente subir los textos de 10 y 11 px; el color ahora lo pone quien
     pinta la barra, porque depende del fondo de cada una. */
  barText: { fontSize: 14, fontWeight: '600' },
  legend: { flexDirection: 'row', gap: 16, marginTop: 8 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  legendDot: { width: 10, height: 10, borderRadius: 5 },
});

export default BudgetVsReal;
