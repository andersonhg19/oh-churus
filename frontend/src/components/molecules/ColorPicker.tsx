import React, { useState } from 'react';
import { View, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import AppText from '../atoms/Text';
import { spacing } from '../../theme';

const PRESET_COLORS = [
  // Rojos
  '#F44336', '#E91E63', '#FF5252', '#FF1744', '#D50000',
  // Naranjas / Amarillos
  '#FF9800', '#FFC107', '#FFEB3B', '#FF6D00', '#FFAB00',
  // Verdes
  '#4CAF50', '#00B894', '#27AE60', '#2ECC71', '#00E676',
  // Azules
  '#2196F3', '#03A9F4', '#00BCD4', '#0288D1', '#1565C0',
  // Morados / Rosas
  '#9C27B0', '#6C5CE7', '#AB47BC', '#E040FB', '#AA00FF',
  // Marrones / Grises
  '#795548', '#8D6E63', '#607D8B', '#455A64', '#78909C',
  // Extras
  '#E8A838', '#FF6B6B', '#FDCB6E', '#A1887F', '#26C6DA',
];

interface ColorPickerProps {
  value: string;
  onChange: (color: string) => void;
}

const ColorPicker: React.FC<ColorPickerProps> = ({ value, onChange }) => {
  const { colors } = useTheme();
  const [expanded, setExpanded] = useState(false);

  return (
    <View style={styles.wrapper}>
      <AppText variant="label" style={styles.label}>Color</AppText>
      {/* Un selector de COLOR es el caso extremo: sin nombre no hay
          absolutamente nada que oir, solo cuadraditos. */}
      <TouchableOpacity
        style={[styles.selector, { borderColor: colors.border, backgroundColor: colors.surface }]}
        onPress={() => setExpanded(!expanded)}
        accessibilityRole="button"
        accessibilityLabel={value ? `Color elegido: ${value}` : 'Elegir un color'}
        accessibilityState={{ expanded }}
      >
        {value ? (
          <View style={styles.selectedRow}>
            <View style={[styles.colorPreview, { backgroundColor: value }]} />
            <AppText variant="body" color={colors.text}>{value}</AppText>
          </View>
        ) : (
          <AppText variant="body" color={colors.textMuted}>Seleccionar color (opcional)</AppText>
        )}
        <AppText variant="body" color={colors.textMuted}>{expanded ? '▲' : '▼'}</AppText>
      </TouchableOpacity>

      {expanded && (
        <View style={[styles.grid, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          <ScrollView style={{ maxHeight: 200 }}>
            <View style={styles.colorsRow}>
              {PRESET_COLORS.map((c) => (
                <TouchableOpacity
                  key={c}
                  accessibilityRole="radio"
                  accessibilityLabel={`Color ${c}`}
                  accessibilityState={{ selected: c === value }}
                  style={[
                    styles.colorDot,
                    { backgroundColor: c },
                    value === c && styles.colorDotSelected,
                  ]}
                  onPress={() => {
                    onChange(c);
                    setExpanded(false);
                  }}
                />
              ))}
            </View>
          </ScrollView>
          {value ? (
            <TouchableOpacity
              style={[styles.clearBtn, { borderColor: colors.border }]}
              onPress={() => { onChange(''); setExpanded(false); }}
              accessibilityRole="button"
              accessibilityLabel="Quitar el color"
            >
              <AppText variant="caption" color={colors.textMuted}>Quitar color</AppText>
            </TouchableOpacity>
          ) : null}
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
  selectedRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  colorPreview: { width: 24, height: 24, borderRadius: 12 },
  grid: {
    marginTop: spacing.xs,
    borderWidth: 1,
    borderRadius: 12,
    padding: spacing.sm,
  },
  colorsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    justifyContent: 'center',
  },
  colorDot: {
    width: 36,
    height: 36,
    borderRadius: 18,
  },
  colorDotSelected: {
    borderWidth: 3,
    borderColor: '#FFFFFF',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.5,
    shadowRadius: 4,
    elevation: 5,
  },
  clearBtn: {
    marginTop: spacing.sm,
    alignItems: 'center',
    paddingVertical: spacing.xs,
    borderTopWidth: 1,
  },
});

export default ColorPicker;
