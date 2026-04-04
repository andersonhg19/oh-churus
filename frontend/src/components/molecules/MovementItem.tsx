import React, { useRef, useMemo } from 'react';
import { View, TouchableOpacity, StyleSheet, Animated, PanResponder } from 'react-native';
import AppText from '../atoms/Text';
import { useTheme } from '../../contexts/ThemeContext';
import { spacing } from '../../theme';
import { formatCurrency, formatDate } from '../../utils/format';
import { Movement } from '../../types';

interface MovementItemProps {
  movement: Movement;
  onPress?: () => void;
  onConfirm?: () => void;
  onSwipeConfirm?: () => void;
}

const SWIPE_THRESHOLD = 80;

const MovementItem: React.FC<MovementItemProps> = ({ movement, onPress, onConfirm, onSwipeConfirm }) => {
  const { colors } = useTheme();
  const translateX = useRef(new Animated.Value(0)).current;
  const swipeAction = onSwipeConfirm || onConfirm;
  const canSwipe = !movement.confirmed && !!swipeAction;

  // Use refs to avoid stale closures in PanResponder
  const swipeActionRef = useRef(swipeAction);
  swipeActionRef.current = swipeAction;
  const canSwipeRef = useRef(canSwipe);
  canSwipeRef.current = canSwipe;

  const panResponder = useMemo(() =>
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gesture) =>
        canSwipeRef.current && Math.abs(gesture.dx) > 15 && Math.abs(gesture.dx) > Math.abs(gesture.dy * 2),
      onPanResponderMove: (_, gesture) => {
        if (gesture.dx > 0) {
          translateX.setValue(Math.min(gesture.dx, 120));
        }
      },
      onPanResponderRelease: (_, gesture) => {
        if (gesture.dx > SWIPE_THRESHOLD && swipeActionRef.current) {
          Animated.timing(translateX, { toValue: 300, duration: 200, useNativeDriver: true }).start(() => {
            swipeActionRef.current?.();
            setTimeout(() => translateX.setValue(0), 500);
          });
        } else {
          Animated.spring(translateX, { toValue: 0, useNativeDriver: true }).start();
        }
      },
    }),
  [translateX]);

  const amountColor = !movement.confirmed
    ? colors.pending
    : movement.categoryType === 'INCOME'
    ? colors.income
    : colors.expense;

  const sign = movement.categoryType === 'INCOME' ? '+' : '-';

  return (
    <View style={styles.wrapper}>
      {canSwipe && (
        <View style={[styles.swipeAction, { backgroundColor: colors.income }]}>
          <AppText variant="body" color="#FFF" style={styles.swipeText}>Confirmar</AppText>
        </View>
      )}

      <Animated.View
        style={[{ transform: [{ translateX }] }]}
        {...(canSwipe ? panResponder.panHandlers : {})}
      >
        <TouchableOpacity
          style={[styles.container, { backgroundColor: colors.card, borderColor: colors.border }]}
          onPress={onPress}
          activeOpacity={0.7}
        >
          <View style={styles.left}>
            <AppText variant="body" numberOfLines={1}>
              {movement.description || movement.categoryName || 'Movimiento'}
            </AppText>
            <AppText variant="caption">
              {formatDate(movement.date)}
              {movement.categoryName ? ` - ${movement.categoryName}` : ''}
            </AppText>
          </View>
          <View style={styles.right}>
            <AppText variant="body" color={amountColor} style={styles.amount}>
              {sign}{formatCurrency(movement.amount)}
            </AppText>
            {!movement.confirmed && onConfirm && (
              <TouchableOpacity
                style={[styles.confirmBtn, { backgroundColor: colors.accent }]}
                onPress={() => onConfirm()}
              >
                <AppText variant="caption" color="#FFFFFF">Confirmar</AppText>
              </TouchableOpacity>
            )}
          </View>
        </TouchableOpacity>
      </Animated.View>
    </View>
  );
};

const styles = StyleSheet.create({
  wrapper: {
    marginBottom: spacing.sm,
    position: 'relative',
    overflow: 'hidden',
    borderRadius: 12,
  },
  swipeAction: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    width: 120,
    borderRadius: 12,
    justifyContent: 'center',
    paddingLeft: spacing.md,
  },
  swipeText: {
    fontWeight: '700',
  },
  container: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.md,
    borderRadius: 12,
    borderWidth: 1,
  },
  left: {
    flex: 1,
    marginRight: spacing.sm,
  },
  right: {
    alignItems: 'flex-end',
  },
  amount: {
    fontWeight: '600',
  },
  confirmBtn: {
    marginTop: spacing.xs,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    borderRadius: 8,
  },
});

export default MovementItem;
