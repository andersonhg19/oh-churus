import React from 'react';
import { Text as RNText, TextStyle, StyleProp, StyleSheet } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { fontSize, fontWeight } from '../../theme';

type TextVariant = 'title' | 'subtitle' | 'body' | 'caption' | 'label';

interface TextProps {
  variant?: TextVariant;
  color?: string;
  align?: TextStyle['textAlign'];
  children: React.ReactNode;
  style?: StyleProp<TextStyle>;
  numberOfLines?: number;
}

const AppText: React.FC<TextProps> = ({
  variant = 'body',
  color,
  align,
  children,
  style,
  numberOfLines,
}) => {
  const { colors } = useTheme();

  const getVariantStyle = (): TextStyle => {
    switch (variant) {
      case 'title':
        return { fontSize: fontSize.xxl, fontWeight: fontWeight.bold };
      case 'subtitle':
        return { fontSize: fontSize.lg, fontWeight: fontWeight.semiBold };
      case 'body':
        return { fontSize: fontSize.md, fontWeight: fontWeight.regular };
      case 'caption':
        return { fontSize: fontSize.xs, fontWeight: fontWeight.regular };
      case 'label':
        return { fontSize: fontSize.sm, fontWeight: fontWeight.medium };
      default:
        return { fontSize: fontSize.md, fontWeight: fontWeight.regular };
    }
  };

  const getDefaultColor = (): string => {
    switch (variant) {
      case 'caption': return colors.textMuted;
      case 'label': return colors.textSecondary;
      default: return colors.text;
    }
  };

  return (
    <RNText
      style={[
        getVariantStyle(),
        { color: color || getDefaultColor(), textAlign: align },
        style,
      ]}
      numberOfLines={numberOfLines}
    >
      {children}
    </RNText>
  );
};

export default AppText;
