import React, { useState, useCallback, useEffect } from 'react';
import { View, StyleSheet, FlatList, RefreshControl } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import MovementItem from '../../components/molecules/MovementItem';
import EmptyState from '../../components/molecules/EmptyState';
import CapsuleToggle from '../../components/molecules/CapsuleToggle';
import Spinner from '../../components/atoms/Spinner';
import { spacing } from '../../theme';
import { movementService } from '../../services/movementService';
import { Movement } from '../../types';
import { useNavigation } from '@react-navigation/native';

type FilterMode = 'ALL' | 'CONFIRMED' | 'PENDING';

const MovementsListScreen: React.FC = () => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const rootNav = useNavigation<any>();

  const [movements, setMovements] = useState<Movement[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [search, setSearch] = useState('');
  const [filterMode, setFilterMode] = useState<FilterMode>('ALL');

  const fetchData = useCallback(async () => {
    if (!user) return;
    try {
      const filter: any = { userId: user.userId, page: 0, size: 100 };
      if (filterMode === 'CONFIRMED') filter.confirmed = true;
      if (filterMode === 'PENDING') filter.confirmed = false;

      const res = await movementService.getAll(filter);
      if (res.correct && res.object) {
        setMovements(res.object.list || []);
      }
    } catch {
      // silent
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [user, filterMode]);

  // Fetch on mount and when filter changes
  useEffect(() => {
    setLoading(true);
    fetchData();
  }, [fetchData]);

  const filtered = search.trim()
    ? movements.filter(m =>
        (m.description || '').toLowerCase().includes(search.toLowerCase()) ||
        (m.categoryName || '').toLowerCase().includes(search.toLowerCase()))
    : movements;

  if (loading) return <Spinner fullScreen />;

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={styles.header}>
        <Input
          label=""
          value={search}
          onChangeText={setSearch}
          placeholder="Buscar por descripcion o categoria..."
        />
        <CapsuleToggle
          options={[
            { label: 'Todos', value: 'ALL', color: colors.primary },
            { label: 'Ejecutados', value: 'CONFIRMED', color: colors.income },
            { label: 'Pendientes', value: 'PENDING', color: colors.warning },
          ]}
          selected={filterMode}
          onChange={(v) => setFilterMode(v as FilterMode)}
        />
      </View>

      <AppText variant="caption" color={colors.textSecondary} style={styles.count}>
        {filtered.length} movimiento{filtered.length !== 1 ? 's' : ''}
      </AppText>

      <FlatList
        data={filtered}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => (
          <MovementItem
            movement={item}
            onPress={() => rootNav.navigate('MovementFormModal', { movement: item })}
            onConfirm={async () => {
              try {
                await movementService.confirm(String(item.id));
                fetchData();
              } catch {}
            }}
          />
        )}
        contentContainerStyle={filtered.length === 0 ? styles.emptyContainer : styles.listContent}
        ListEmptyComponent={
          <EmptyState title="Sin resultados" message={search ? 'Intenta otra busqueda' : 'No hay movimientos'} icon="🔍" />
        }
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); fetchData(); }} tintColor={colors.primary} />
        }
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { padding: spacing.md, gap: spacing.sm },
  count: { paddingHorizontal: spacing.md, marginBottom: spacing.xs },
  listContent: { padding: spacing.md, paddingTop: 0 },
  emptyContainer: { flex: 1 },
});

export default MovementsListScreen;
