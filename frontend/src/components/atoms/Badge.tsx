import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { fontSize, fontWeight } from '../../theme';

interface BadgeProps {
  count: number;
  color?: string;
  size?: 'small' | 'medium' | 'large';
}

const Badge: React.FC<BadgeProps> = ({ count, color, size = 'medium' }) => {
  const { colors } = useTheme();
  const bgColor = color || colors.danger;

  const getDimension = (): number => {
    switch (size) {
      case 'small': return 18;
      case 'medium': return 24;
      case 'large': return 32;
      default: return 24;
    }
  };

  const getFontSize = (): number => {
    switch (size) {
      case 'small': return fontSize.xs - 2;
      case 'medium': return fontSize.xs;
      case 'large': return fontSize.sm;
      default: return fontSize.xs;
    }
  };

  const dim = getDimension();

  return (
    <View
      style={[
        styles.badge,
        {
          backgroundColor: bgColor,
          width: dim,
          height: dim,
          borderRadius: dim / 2,
          minWidth: dim,
        },
      ]}
    >
      <Text style={[styles.text, { fontSize: getFontSize() }]}>
        {count > 99 ? '99+' : count}
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  badge: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: {
    color: '#FFFFFF',
    fontWeight: '700',
    textAlign: 'center',
  },
});

export default Badge;
