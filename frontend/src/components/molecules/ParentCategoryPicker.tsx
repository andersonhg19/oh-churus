import React, { useState, useEffect } from 'react';
import { View, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../atoms/Text';
import Spinner from '../atoms/Spinner';
import { spacing } from '../../theme';
import { categoryService } from '../../services/categoryService';
import { CategoryType } from '../../types';

interface ParentOption {
  id: string;
  name: string;
  icon?: string;
  type: CategoryType;
  depth: number;
}

interface ParentCategoryPickerProps {
  value: string | null;
  onChange: (id: string | null) => void;
  excludeId?: string;
  filterType?: CategoryType;
}

const ParentCategoryPicker: React.FC<ParentCategoryPickerProps> = ({
  value,
  onChange,
  excludeId,
  filterType,
}) => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const [expanded, setExpanded] = useState(false);
  const [options, setOptions] = useState<ParentOption[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (expanded && options.length === 0) {
      loadCategories();
    }
  }, [expanded]);

  const loadCategories = async () => {
    if (!user?.userId) return;
    setLoading(true);
    try {
      const res = await categoryService.getTree(user.userId);
      if (res.correct) {
        const flat: ParentOption[] = [];
        const flatten = (nodes: any[], depth: number) => {
          for (const node of nodes) {
            if (excludeId && node.id === excludeId) continue;
            if (depth >= 2) continue; // Max depth 3, so parents can be depth 0 or 1
            flat.push({
              id: node.id,
              name: node.name,
              icon: node.icon,
              type: node.type,
              depth,
            });
            if (node.children?.length > 0) {
              flatten(node.children, depth + 1);
            }
          }
        };
        flatten(res.object as any[], 0);
        setOptions(flat);
      }
    } catch {
      // Silent
    } finally {
      setLoading(false);
    }
  };

  const selectedOption = options.find(o => o.id === value);

  const filteredOptions = filterType
    ? options.filter(o => o.type === filterType)
    : options;

  return (
    <View style={styles.wrapper}>
      <AppText variant="label" style={styles.label}>Categoría padre</AppText>
      <TouchableOpacity
        style={[styles.selector, { borderColor: colors.border, backgroundColor: colors.surface }]}
        onPress={() => setExpanded(!expanded)}
        accessibilityRole="button"
        accessibilityLabel={selectedOption
          ? `Categoria padre: ${selectedOption.name}`
          : 'Elegir categoria padre'}
        accessibilityState={{ expanded }}
      >
        {selectedOption ? (
          <AppText variant="body" color={colors.text}>
            {'  '.repeat(selectedOption.depth)}{selectedOption.name}
          </AppText>
        ) : (
          <AppText variant="body" color={colors.textMuted}>
            Sin padre (categoría raíz)
          </AppText>
        )}
        <AppText variant="body" color={colors.textMuted}>{expanded ? '▲' : '▼'}</AppText>
      </TouchableOpacity>

      {expanded && (
        <View style={[styles.panel, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          {loading ? (
            <Spinner />
          ) : (
            <ScrollView style={{ maxHeight: 250 }}>
              <TouchableOpacity
                accessibilityRole="radio"
                accessibilityLabel="Sin padre, categoria raiz"
                accessibilityState={{ selected: !value }}
                style={[
                  styles.option,
                  { backgroundColor: !value ? colors.primary + '20' : 'transparent' },
                ]}
                onPress={() => { onChange(null); setExpanded(false); }}
              >
                <AppText variant="body" color={!value ? colors.primary : colors.text}>
                  Sin padre (categoría raíz)
                </AppText>
              </TouchableOpacity>

              {filteredOptions.map((opt) => (
                <TouchableOpacity
                  key={opt.id}
                  accessibilityRole="radio"
                  /* El tipo va en la etiqueta porque en pantalla se distingue
                     por COLOR, y el color no es una senal para todo el mundo. */
                  accessibilityLabel={`${opt.name}, ${opt.type === 'INCOME' ? 'ingreso' : 'gasto'}`}
                  accessibilityState={{ selected: value === opt.id }}
                  style={[
                    styles.option,
                    {
                      backgroundColor: value === opt.id ? colors.primary + '20' : 'transparent',
                      paddingLeft: spacing.md + (opt.depth * spacing.lg),
                    },
                  ]}
                  onPress={() => { onChange(opt.id); setExpanded(false); }}
                >
                  <AppText variant="body" color={value === opt.id ? colors.primary : colors.text}>
                    {opt.depth > 0 ? '└ ' : ''}{opt.name}
                  </AppText>
                  <AppText variant="caption" color={opt.type === 'INCOME' ? colors.income : colors.expense}>
                    {opt.type === 'INCOME' ? 'Ingreso' : 'Gasto'}
                  </AppText>
                </TouchableOpacity>
              ))}

              {filteredOptions.length === 0 && (
                <AppText variant="body" color={colors.textMuted} style={{ padding: spacing.md, textAlign: 'center' }}>
                  No hay categorías disponibles
                </AppText>
              )}
            </ScrollView>
          )}
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  wrapper: { marginBottom: spacing.md },
  label: { marginBottom: spacing.xs, marginLeft: spacing.xs },
  selector: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm + 4,
  },
  panel: {
    marginTop: spacing.xs,
    borderWidth: 1,
    borderRadius: 12,
    padding: spacing.xs,
  },
  option: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm + 2,
    borderRadius: 8,
  },
});

export default ParentCategoryPicker;
