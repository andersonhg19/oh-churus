import React, { useState } from 'react';
import { View, StyleSheet, TouchableOpacity, ScrollView, TextInput } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import AppText from '../atoms/Text';
import { spacing } from '../../theme';

interface IconOption {
  key: string;
  emoji: string;
  label: string;
}

const ICON_CATEGORIES: { title: string; icons: IconOption[] }[] = [
  {
    title: 'Dinero',
    icons: [
      { key: 'wallet', emoji: '👛', label: 'Billetera' },
      { key: 'money', emoji: '💰', label: 'Dinero' },
      { key: 'dollar', emoji: '💵', label: 'Billete' },
      { key: 'coin', emoji: '🪙', label: 'Moneda' },
      { key: 'bank', emoji: '🏦', label: 'Banco' },
      { key: 'credit-card', emoji: '💳', label: 'Tarjeta' },
      { key: 'piggy-bank', emoji: '🐷', label: 'Alcancía' },
      { key: 'chart', emoji: '📈', label: 'Gráfico' },
      { key: 'bar-chart', emoji: '📊', label: 'Estadística' },
      { key: 'percent', emoji: '💹', label: 'Porcentaje' },
    ],
  },
  {
    title: 'Hogar',
    icons: [
      { key: 'home', emoji: '🏠', label: 'Casa' },
      { key: 'key', emoji: '🔑', label: 'Llave' },
      { key: 'zap', emoji: '⚡', label: 'Energía' },
      { key: 'droplet', emoji: '💧', label: 'Agua' },
      { key: 'flame', emoji: '🔥', label: 'Gas' },
      { key: 'tool', emoji: '🔧', label: 'Herramienta' },
      { key: 'bulb', emoji: '💡', label: 'Luz' },
      { key: 'bed', emoji: '🛏️', label: 'Dormitorio' },
    ],
  },
  {
    title: 'Comida',
    icons: [
      { key: 'shopping-cart', emoji: '🛒', label: 'Carrito' },
      { key: 'shopping-bag', emoji: '🛍️', label: 'Bolsa' },
      { key: 'coffee', emoji: '☕', label: 'Café' },
      { key: 'pizza', emoji: '🍕', label: 'Pizza' },
      { key: 'apple', emoji: '🍎', label: 'Fruta' },
      { key: 'cooking', emoji: '🍳', label: 'Cocina' },
      { key: 'cake', emoji: '🎂', label: 'Pastel' },
      { key: 'wine', emoji: '🍷', label: 'Vino' },
    ],
  },
  {
    title: 'Transporte',
    icons: [
      { key: 'car', emoji: '🚗', label: 'Auto' },
      { key: 'bus', emoji: '🚌', label: 'Bus' },
      { key: 'truck', emoji: '🚛', label: 'Camión' },
      { key: 'bike', emoji: '🚲', label: 'Bici' },
      { key: 'plane', emoji: '✈️', label: 'Avión' },
      { key: 'fuel', emoji: '⛽', label: 'Gasolina' },
      { key: 'navigation', emoji: '🧭', label: 'Navegar' },
      { key: 'train', emoji: '🚆', label: 'Tren' },
    ],
  },
  {
    title: 'Trabajo',
    icons: [
      { key: 'briefcase', emoji: '💼', label: 'Maletín' },
      { key: 'laptop', emoji: '💻', label: 'Laptop' },
      { key: 'desktop', emoji: '🖥️', label: 'Escritorio' },
      { key: 'phone', emoji: '📱', label: 'Celular' },
      { key: 'mail', emoji: '📧', label: 'Correo' },
      { key: 'calendar', emoji: '📅', label: 'Calendario' },
      { key: 'clock', emoji: '⏰', label: 'Reloj' },
      { key: 'trending-up', emoji: '📈', label: 'Tendencia' },
    ],
  },
  {
    title: 'Salud y Educación',
    icons: [
      { key: 'heart', emoji: '❤️', label: 'Corazón' },
      { key: 'hospital', emoji: '🏥', label: 'Hospital' },
      { key: 'pill', emoji: '💊', label: 'Pastilla' },
      { key: 'thermometer', emoji: '🌡️', label: 'Termómetro' },
      { key: 'activity', emoji: '🩺', label: 'Consulta' },
      { key: 'book', emoji: '📚', label: 'Libros' },
      { key: 'graduation', emoji: '🎓', label: 'Graduación' },
      { key: 'award', emoji: '🏆', label: 'Premio' },
      { key: 'bookmark', emoji: '📖', label: 'Lectura' },
    ],
  },
  {
    title: 'Entretenimiento',
    icons: [
      { key: 'film', emoji: '🎬', label: 'Cine' },
      { key: 'music', emoji: '🎵', label: 'Música' },
      { key: 'tv', emoji: '📺', label: 'TV' },
      { key: 'gamepad', emoji: '🎮', label: 'Juegos' },
      { key: 'headphones', emoji: '🎧', label: 'Audífonos' },
      { key: 'party', emoji: '🎉', label: 'Fiesta' },
      { key: 'sports', emoji: '⚽', label: 'Deporte' },
      { key: 'gym', emoji: '🏋️', label: 'Gimnasio' },
    ],
  },
  {
    title: 'Otros',
    icons: [
      { key: 'gift', emoji: '🎁', label: 'Regalo' },
      { key: 'pet', emoji: '🐾', label: 'Mascota' },
      { key: 'baby', emoji: '👶', label: 'Bebé' },
      { key: 'clothes', emoji: '👕', label: 'Ropa' },
      { key: 'scissors', emoji: '✂️', label: 'Peluquería' },
      { key: 'star', emoji: '⭐', label: 'Favorito' },
      { key: 'shield', emoji: '🛡️', label: 'Seguro' },
      { key: 'squirrel', emoji: '🐿️', label: 'Ardilla' },
    ],
  },
];

interface IconPickerProps {
  value: string;
  onChange: (key: string, emoji: string) => void;
}

const IconPicker: React.FC<IconPickerProps> = ({ value, onChange }) => {
  const { colors } = useTheme();
  const [expanded, setExpanded] = useState(false);
  const [search, setSearch] = useState('');

  const selectedIcon = ICON_CATEGORIES
    .flatMap(c => c.icons)
    .find(i => i.key === value);

  const filteredCategories = search.trim()
    ? [{
        title: 'Resultados',
        icons: ICON_CATEGORIES
          .flatMap(c => c.icons)
          .filter(i => i.label.toLowerCase().includes(search.toLowerCase()) || i.key.includes(search.toLowerCase())),
      }]
    : ICON_CATEGORIES;

  return (
    <View style={styles.wrapper}>
      <AppText variant="label" style={styles.label}>Icono</AppText>
      {/* Igual que el de color: un emoji suelto no es un nombre. */}
      <TouchableOpacity
        style={[styles.selector, { borderColor: colors.border, backgroundColor: colors.surface }]}
        onPress={() => setExpanded(!expanded)}
        accessibilityRole="button"
        accessibilityLabel={selectedIcon ? `Icono elegido: ${selectedIcon.label}` : 'Elegir un icono'}
        accessibilityState={{ expanded }}
      >
        {selectedIcon ? (
          <View style={styles.selectedRow}>
            <AppText variant="subtitle">{selectedIcon.emoji}</AppText>
            <AppText variant="body" color={colors.text}>{selectedIcon.label}</AppText>
          </View>
        ) : (
          <AppText variant="body" color={colors.textMuted}>Seleccionar icono (opcional)</AppText>
        )}
        <AppText variant="body" color={colors.textMuted}>{expanded ? '▲' : '▼'}</AppText>
      </TouchableOpacity>

      {expanded && (
        <View style={[styles.panel, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          <TextInput
            style={[styles.searchInput, { color: colors.text, borderColor: colors.border, backgroundColor: colors.background }]}
            placeholder="Buscar icono..."
            placeholderTextColor={colors.textMuted}
            value={search}
            onChangeText={setSearch}
          />
          <ScrollView style={{ maxHeight: 280 }}>
            {filteredCategories.map((cat) => (
              <View key={cat.title}>
                <AppText variant="caption" color={colors.textSecondary} style={styles.catTitle}>
                  {cat.title}
                </AppText>
                <View style={styles.iconsRow}>
                  {cat.icons.map((ic) => (
                    <TouchableOpacity
                      key={ic.key}
                      accessibilityRole="radio"
                      accessibilityLabel={ic.label}
                      accessibilityState={{ selected: ic.key === value }}
                      style={[
                        styles.iconItem,
                        { backgroundColor: value === ic.key ? colors.primary + '30' : 'transparent' },
                      ]}
                      onPress={() => {
                        onChange(ic.key, ic.emoji);
                        setExpanded(false);
                        setSearch('');
                      }}
                    >
                      <AppText variant="subtitle">{ic.emoji}</AppText>
                      <AppText variant="caption" color={colors.textSecondary} style={styles.iconLabel}>
                        {ic.label}
                      </AppText>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>
            ))}
          </ScrollView>
          {value ? (
            <TouchableOpacity
              style={[styles.clearBtn, { borderColor: colors.border }]}
              onPress={() => { onChange('', ''); setExpanded(false); }}
              accessibilityRole="button"
              accessibilityLabel="Quitar el icono"
            >
              <AppText variant="caption" color={colors.textMuted}>Quitar icono</AppText>
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
  panel: {
    marginTop: spacing.xs,
    borderWidth: 1,
    borderRadius: 12,
    padding: spacing.sm,
  },
  searchInput: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs + 2,
    marginBottom: spacing.sm,
    fontSize: 14,
  },
  catTitle: { marginTop: spacing.sm, marginBottom: spacing.xs, fontWeight: '700' },
  iconsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.xs,
  },
  iconItem: {
    alignItems: 'center',
    padding: spacing.xs,
    borderRadius: 8,
    width: 64,
  },
  iconLabel: { fontSize: 10, textAlign: 'center' },
  clearBtn: {
    marginTop: spacing.sm,
    alignItems: 'center',
    paddingVertical: spacing.xs,
    borderTopWidth: 1,
  },
});

export default IconPicker;
