import React from 'react';
import { View, StyleSheet } from 'react-native';
import AppText from '../atoms/Text';
import { useTheme } from '../../contexts/ThemeContext';
import { spacing } from '../../theme';

interface EmptyStateProps {
  title: string;
  message: string;
  icon?: string;
}

const EmptyState: React.FC<EmptyStateProps> = ({
  title,
  message,
  icon = '🐿️',
}) => {
  const { colors } = useTheme();

  return (
    <View style={styles.container}>
      <AppText variant="title" style={styles.icon}>{icon}</AppText>
      <AppText variant="subtitle" align="center" style={styles.title}>
        {title}
      </AppText>
      <AppText variant="body" color={colors.textSecondary} align="center">
        {message}
      </AppText>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
  },
  icon: {
    fontSize: 64,
    marginBottom: spacing.md,
  },
  title: {
    marginBottom: spacing.sm,
  },
});

export default EmptyState;
