import React from 'react';
import {
  TouchableOpacity,
  Text,
  StyleSheet,
  ActivityIndicator,
  ViewStyle,
  TextStyle,
} from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { fontSize, fontWeight, spacing } from '../../theme';

type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'danger';
type ButtonSize = 'small' | 'medium' | 'large';

interface ButtonProps {
  title: string;
  onPress: () => void;
  variant?: ButtonVariant;
  loading?: boolean;
  disabled?: boolean;
  size?: ButtonSize;
  style?: ViewStyle;
  /* Para que las pruebas puedan senalar un boton concreto sin depender de su
     texto: hay pantallas donde el titulo de la seccion y el del boton dicen lo
     mismo, y getByText encuentra dos. */
  testID?: string;
  /**
   * Lo que anuncia un lector de pantalla. Por defecto, el propio titulo.
   *
   * Se pasa a mano cuando el titulo no basta por si solo: un boton que pone
   * "Eliminar" en una pantalla llena de cosas no dice QUE elimina, y quien no
   * ve la pantalla se queda sin saberlo.
   */
  accessibilityLabel?: string;
  /** El "para que sirve", cuando el nombre no lo deja claro del todo. */
  accessibilityHint?: string;
}

const Button: React.FC<ButtonProps> = ({
  title,
  onPress,
  variant = 'primary',
  loading = false,
  disabled = false,
  size = 'medium',
  style,
  testID,
  accessibilityLabel,
  accessibilityHint,
}) => {
  const { colors } = useTheme();

  const getBackgroundColor = (): string => {
    if (disabled) return colors.textMuted;
    switch (variant) {
      case 'primary': return colors.primary;
      case 'secondary': return colors.secondary;
      case 'outline': return 'transparent';
      case 'danger': return colors.danger;
      default: return colors.primary;
    }
  };

  const getTextColor = (): string => {
    if (variant === 'outline') return colors.primary;
    return '#FFFFFF';
  };

  const getPadding = (): number => {
    switch (size) {
      case 'small': return spacing.sm;
      case 'medium': return spacing.md;
      case 'large': return spacing.lg;
      default: return spacing.md;
    }
  };

  const getFontSize = (): number => {
    switch (size) {
      case 'small': return fontSize.sm;
      case 'medium': return fontSize.md;
      case 'large': return fontSize.lg;
      default: return fontSize.md;
    }
  };

  const containerStyle: ViewStyle = {
    backgroundColor: getBackgroundColor(),
    paddingVertical: getPadding(),
    paddingHorizontal: getPadding() * 1.5,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: variant === 'outline' ? 2 : 0,
    borderColor: variant === 'outline' ? colors.primary : undefined,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 4,
    elevation: 3,
  };

  const textStyle: TextStyle = {
    color: getTextColor(),
    fontSize: getFontSize(),
    fontWeight: fontWeight.semiBold,
  };

  return (
    <TouchableOpacity
      testID={testID}
      style={[containerStyle, style]}
      onPress={onPress}
      disabled={disabled || loading}
      activeOpacity={0.7}
      /*
       * accessible envuelve el boton entero en UN solo elemento para el lector.
       * Sin esto anuncia por separado el contenedor y el texto de dentro, y hay
       * que pasar dos veces por cada boton.
       */
      accessible
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel || title}
      accessibilityHint={accessibilityHint}
      /*
       * `busy` es lo que hace que cargando NO sea solo una animacion: sin el,
       * quien no ve la rueda pulsa otra vez creyendo que no paso nada. Y
       * `disabled` explica por que no responde, en vez de dejarlo mudo.
       */
      accessibilityState={{ disabled: disabled || loading, busy: loading }}
    >
      {loading ? (
        <ActivityIndicator color={getTextColor()} size="small" />
      ) : (
        <Text style={textStyle}>{title}</Text>
      )}
    </TouchableOpacity>
  );
};

export default Button;
