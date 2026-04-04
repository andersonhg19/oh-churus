import React, { useEffect, useRef } from 'react';
import { Animated, StyleSheet, TouchableOpacity, View } from 'react-native';
import AppText from './Text';
import { useTheme } from '../../contexts/ThemeContext';
import type { ToastType } from '../../contexts/ToastContext';

interface ToastProps {
  type: ToastType;
  title: string;
  message?: string;
  onDismiss: () => void;
}

const ICONS: Record<ToastType, string> = {
  success: '\u2713',
  error: '\u2717',
  warning: '!',
  info: 'i',
};

export const Toast: React.FC<ToastProps> = ({ type, title, message, onDismiss }) => {
  const { colors } = useTheme();
  const translateY = useRef(new Animated.Value(-100)).current;
  const opacity = useRef(new Animated.Value(0)).current;

  const accentColors: Record<ToastType, string> = {
    success: colors.income || '#00B894',
    error: colors.danger,
    warning: colors.warning,
    info: colors.primary,
  };

  useEffect(() => {
    Animated.parallel([
      Animated.spring(translateY, {
        toValue: 0,
        useNativeDriver: true,
        tension: 80,
        friction: 10,
      }),
      Animated.timing(opacity, {
        toValue: 1,
        duration: 200,
        useNativeDriver: true,
      }),
    ]).start();
  }, []);

  const accent = accentColors[type];

  return (
    <Animated.View style={[styles.container, { transform: [{ translateY }], opacity }]}>
      <TouchableOpacity
        activeOpacity={0.9}
        onPress={onDismiss}
        style={[
          styles.toast,
          {
            backgroundColor: colors.surface,
            borderLeftColor: accent,
            shadowColor: '#000',
          },
        ]}
      >
        <View style={[styles.iconCircle, { backgroundColor: accent }]}>
          <AppText style={styles.iconText}>{ICONS[type]}</AppText>
        </View>
        <View style={styles.textContainer}>
          <AppText variant="label" style={{ color: colors.text }}>{title}</AppText>
          {message ? (
            <AppText variant="caption" style={{ color: colors.textSecondary }}>{message}</AppText>
          ) : null}
        </View>
      </TouchableOpacity>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginBottom: 8,
    paddingHorizontal: 16,
  },
  toast: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    borderLeftWidth: 4,
    paddingVertical: 12,
    paddingHorizontal: 14,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 6,
  },
  iconCircle: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  iconText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
  textContainer: {
    flex: 1,
  },
});
