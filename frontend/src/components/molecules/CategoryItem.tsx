import React from 'react';
import { View, TouchableOpacity, StyleSheet } from 'react-native';
import AppText from '../atoms/Text';
import { useTheme } from '../../contexts/ThemeContext';
import { spacing } from '../../theme';
import { CategoryTree } from '../../types';
import { getIconEmoji } from '../../utils/iconMap';
import { getCategoryTypeLabel } from '../../utils/labels';

interface CategoryItemProps {
  category: CategoryTree;
  onPress?: () => void;
  depth?: number;
  expanded?: boolean;
  onToggle?: () => void;
}

const CategoryItem: React.FC<CategoryItemProps> = ({
  category,
  onPress,
  depth = 0,
  expanded = false,
  onToggle,
}) => {
  const { colors } = useTheme();
  const hasChildren = category.children && category.children.length > 0;

  return (
    <TouchableOpacity
      accessibilityRole="button"
      accessibilityLabel={`Categoria ${category.name}`}
      style={[
        styles.container,
        {
          backgroundColor: colors.card,
          borderColor: colors.border,
          marginLeft: depth * spacing.lg,
        },
      ]}
      onPress={onPress}
      activeOpacity={0.7}
    >
      <View style={styles.left}>
        {/* El "+" y el "-" que despliegan las subcategorias: sin nombre el
            lector dice "mas" y "menos", que no significan nada aqui. */}
        {hasChildren && (
          <TouchableOpacity
            onPress={onToggle}
            style={styles.expandBtn}
            accessibilityRole="button"
            accessibilityLabel={expanded
              ? `Plegar las subcategorias de ${category.name}`
              : `Desplegar las subcategorias de ${category.name}`}
            accessibilityState={{ expanded }}
          >
            <AppText variant="body">{expanded ? '-' : '+'}</AppText>
          </TouchableOpacity>
        )}
        <View
          style={[
            styles.colorDot,
            { backgroundColor: category.color || colors.primary },
          ]}
        />
        <AppText variant="body" style={styles.icon}>
          {getIconEmoji(category.icon, category.type === 'INCOME' ? '💰' : '💸')}
        </AppText>
        <View style={styles.info}>
          <AppText variant="body" numberOfLines={1}>{category.name}</AppText>
          <View style={styles.badges}>
            <AppText variant="caption">{getCategoryTypeLabel(category.type)}</AppText>
            <AppText variant="caption" color={category.shared ? colors.secondary : colors.textMuted}>
              {category.shared ? ' · 🏠 Compartida' : ' · Personal'}
            </AppText>
          </View>
        </View>
      </View>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.md,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: spacing.xs,
  },
  left: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  expandBtn: {
    width: 28,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.xs,
  },
  colorDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginRight: spacing.sm,
  },
  icon: {
    fontSize: 20,
    marginRight: spacing.sm,
  },
  info: {
    flex: 1,
  },
  badges: {
    flexDirection: 'row',
    alignItems: 'center',
  },
});

export default CategoryItem;
