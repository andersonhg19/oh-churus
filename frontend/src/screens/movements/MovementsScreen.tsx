import React, { useState, useCallback } from 'react';
import { View, StyleSheet, FlatList, RefreshControl, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import MovementItem from '../../components/molecules/MovementItem';
import EmptyState from '../../components/molecules/EmptyState';
import PeriodNavigator from '../../components/molecules/PeriodNavigator';
import Spinner from '../../components/atoms/Spinner';
import { spacing } from '../../theme';
import { movementService } from '../../services/movementService';
import { Movement } from '../../types';
import { useFocusEffect } from '@react-navigation/native';
import { getStartOfPeriod, getEndOfPeriod, navigatePeriod } from '../../utils/periodUtils';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type MovementsStackParamList = {
  MovementsList: undefined;
  MovementForm: { movement?: Movement };
};

type Props = NativeStackScreenProps<MovementsStackParamList, 'MovementsList'>;

const MovementsScreen: React.FC<Props> = ({ navigation }) => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const budgetDay = user?.budgetStartDay || 1;

  const [movements, setMovements] = useState<Movement[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [periodStart, setPeriodStart] = useState(() => getStartOfPeriod(budgetDay, new Date()));
  const [periodEnd, setPeriodEnd] = useState(() => getEndOfPeriod(budgetDay, getStartOfPeriod(budgetDay, new Date())));

  const fetchData = useCallback(async () => {
    if (!user) return;
    setError(null);
    try {
      const res = await movementService.getAll({
        userId: user.userId,
        startDate: periodStart,
        endDate: periodEnd,
        page: 0,
        size: 100,
      });
      if (res.correct && res.object) {
        setMovements(res.object.list || []);
      }
    } catch {
      setError('Error al cargar movimientos.');
    }
    setLoading(false);
    setRefreshing(false);
  }, [user, periodStart, periodEnd]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      fetchData();
    }, [fetchData]),
  );

  const onRefresh = () => {
    setRefreshing(true);
    fetchData();
  };

  const handleConfirm = async (id: string) => {
    try {
      await movementService.confirm(id);
      fetchData();
    } catch { /* silent */ }
  };

  const handlePeriodChange = (direction: 'prev' | 'next') => {
    const { start, end } = navigatePeriod(periodStart, budgetDay, direction);
    setPeriodStart(start);
    setPeriodEnd(end);
    setLoading(true);
  };

  const currentPeriodStart = getStartOfPeriod(budgetDay, new Date());
  const canGoNext = periodStart < currentPeriodStart;

  if (loading) return <Spinner fullScreen />;

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={styles.header}>
        <AppText variant="subtitle">Movimientos</AppText>
      </View>

      <View style={styles.navigatorContainer}>
        <PeriodNavigator
          periodStart={periodStart}
          periodEnd={periodEnd}
          onPrevious={() => handlePeriodChange('prev')}
          onNext={() => handlePeriodChange('next')}
          canGoNext={canGoNext}
        />
      </View>

      {error && <AppText variant="body" color={colors.danger} style={styles.errorText}>{error}</AppText>}

      <FlatList
        data={movements}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => (
          <MovementItem
            movement={item}
            onPress={() => navigation.navigate('MovementForm', { movement: item })}
            onConfirm={!item.confirmed ? () => handleConfirm(String(item.id)) : undefined}
          />
        )}
        contentContainerStyle={movements.length === 0 ? styles.emptyContainer : styles.listContent}
        ListEmptyComponent={
          <EmptyState title="Sin movimientos" message="No hay movimientos en este periodo" icon="📋" />
        }
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />}
      />

      <TouchableOpacity
        style={[styles.fab, { backgroundColor: colors.primary }]}
        onPress={() => navigation.navigate('MovementForm', {})}
        activeOpacity={0.8}
      >
        <AppText variant="title" color="#FFFFFF" style={styles.fabText}>+</AppText>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { padding: spacing.md, paddingBottom: spacing.xs },
  navigatorContainer: { paddingHorizontal: spacing.md },
  errorText: { textAlign: 'center', padding: spacing.sm },
  listContent: { padding: spacing.md, paddingTop: spacing.xs },
  emptyContainer: { flex: 1 },
  fab: {
    position: 'absolute', right: spacing.md, bottom: spacing.lg,
    width: 56, height: 56, borderRadius: 28,
    alignItems: 'center', justifyContent: 'center',
    shadowColor: '#000', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.3, shadowRadius: 6, elevation: 6,
  },
  fabText: { fontSize: 28, marginTop: -2 },
});

export default MovementsScreen;
