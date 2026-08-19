import React, { useState } from 'react';
import { View, TextInput, Text, StyleSheet, KeyboardTypeOptions } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { fontSize, fontWeight, spacing } from '../../theme';

interface InputProps {
  label: string;
  value: string;
  onChangeText: (text: string) => void;
  placeholder?: string;
  secureTextEntry?: boolean;
  error?: string;
  keyboardType?: KeyboardTypeOptions;
  multiline?: boolean;
  /**
   * El nombre que se OYE, cuando no coincide con el que se VE.
   *
   * Hace falta porque hay campos que a proposito no llevan etiqueta encima
   * —el del MONTO, que ya se entiende por el tamano y la moneda, y el buscador
   * de movimientos— y pasan `label=""`. Sin esto, el atomo ponia una etiqueta
   * de accesibilidad VACIA y esos dos campos, que son los mas usados de la
   * app, simplemente no existian para quien no ve la pantalla.
   *
   * No basta con "poner siempre label": la etiqueta visible cambiaria el
   * diseno. Son dos cosas distintas y por eso son dos props.
   */
  accessibilityLabel?: string;
}

const Input: React.FC<InputProps> = ({
  label,
  value,
  onChangeText,
  placeholder,
  secureTextEntry = false,
  error,
  keyboardType = 'default',
  multiline = false,
  accessibilityLabel,
}) => {
  const { colors } = useTheme();
  const [isFocused, setIsFocused] = useState(false);

  const borderColor = error
    ? colors.danger
    : isFocused
    ? colors.primary
    : colors.border;

  return (
    <View style={styles.container}>
      <Text style={[styles.label, { color: isFocused ? colors.primary : colors.textSecondary }]}>
        {label}
      </Text>
      <TextInput
        /*
         * La etiqueta visual y el campo son dos elementos distintos para el
         * lector de pantalla: sin unirlos, anuncia "Nombre" por un lado y
         * "campo de texto, vacio" por otro, y con seis campos seguidos no hay
         * forma de saber cual es cual.
         *
         * El error va en accessibilityLabel y no solo pintado en rojo, porque
         * el color no es una senal para todo el mundo. Si no, el formulario
         * simplemente no se envia y nadie sabe por que.
         */
        accessibilityLabel={(() => {
          const nombre = accessibilityLabel || label;
          return error ? `${nombre}. Error: ${error}` : nombre;
        })()}
        accessibilityHint={placeholder}
        style={[
          styles.input,
          {
            color: colors.text,
            backgroundColor: colors.surface,
            borderColor,
          },
          multiline && styles.multiline,
        ]}
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.textMuted}
        secureTextEntry={secureTextEntry}
        keyboardType={keyboardType}
        multiline={multiline}
        onFocus={() => setIsFocused(true)}
        onBlur={() => setIsFocused(false)}
      />
      {error ? (
        <Text style={[styles.error, { color: colors.danger }]}>{error}</Text>
      ) : null}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginBottom: spacing.md,
  },
  label: {
    fontSize: fontSize.sm,
    fontWeight: fontWeight.medium,
    marginBottom: spacing.xs,
    marginLeft: spacing.xs,
  },
  input: {
    borderWidth: 1.5,
    borderRadius: 12,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm + 4,
    fontSize: fontSize.md,
  },
  multiline: {
    minHeight: 80,
    textAlignVertical: 'top',
  },
  error: {
    fontSize: fontSize.xs,
    marginTop: spacing.xs,
    marginLeft: spacing.xs,
  },
});

export default Input;
