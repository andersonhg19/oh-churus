import React, { useState, useCallback } from 'react';
import { View, StyleSheet, FlatList, RefreshControl } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Spinner from '../../components/atoms/Spinner';
import EmptyState from '../../components/molecules/EmptyState';
import EstadoError from '../../components/molecules/EstadoError';
import MovementItem from '../../components/molecules/MovementItem';
import { spacing } from '../../theme';
import { movementService } from '../../services/movementService';
import { Movement } from '../../types';
import { useCarga, exigir } from '../../hooks/useCarga';
import { formatCurrency } from '../../utils/format';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type SummaryStackParamList = {
  SummaryMain: undefined;
  CategoryDrillDown: { categoryId: string; categoryName: string; color: string; startDate: string; endDate: string };
};

type Props = NativeStackScreenProps<SummaryStackParamList, 'CategoryDrillDown'>;

const CategoryDrillDownScreen: React.FC<Props> = ({ route }) => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const rootNavigation = useNavigation<any>();
  const { categoryId, categoryName, color, startDate, endDate } = route.params;

  const [movements, setMovements] = useState<Movement[]>([]);

  const traerMovimientos = useCallback(async () => {
    if (!user) return;
    const res = await movementService.getAll({
      userId: user.userId,
      categoryId: categoryId,
      startDate,
      endDate,
      page: 0,
      size: 100,
    });
    setMovements(exigir(res)?.list || []);
  }, [user, categoryId, startDate, endDate]);

  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traerMovimientos);

  useFocusEffect(
    useCallback(() => {
      cargar();
    }, [cargar]),
  );

  const total = movements.reduce((sum, m) => sum + (m.amount || 0), 0);

  if (cargando) return <Spinner fullScreen />;

  if (error) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background }]}>
        <EstadoError mensaje={error} onReintentar={cargar} />
      </View>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      {/* Nombre, total y cuantos movimientos son la cabecera entera: sueltos
          se oyen tres datos que no se sabe si van juntos. */}
      <View
        style={[styles.header, { borderBottomColor: colors.border }]}
        accessible
        accessibilityLabel={`${categoryName}: ${formatCurrency(total)} en ${movements.length} ${movements.length === 1 ? 'movimiento' : 'movimientos'}`}
      >
        <View style={styles.headerRow}>
          <View style={[styles.dot, { backgroundColor: color }]} />
          <AppText variant="subtitle">{categoryName}</AppText>
        </View>
        <AppText variant="body" style={{ color: color, fontWeight: '700', fontSize: 18 }}>
          {formatCurrency(total)}
        </AppText>
        <AppText variant="caption" style={{ color: colors.textSecondary }}>
          {movements.length} movimiento{movements.length !== 1 ? 's' : ''}
        </AppText>
      </View>

      <FlatList
        data={movements}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => (
          <MovementItem movement={item} onPress={() => rootNavigation.navigate('MovementFormModal', { movement: item })} onConfirm={async () => {
            await movementService.confirm(String(item.id)).catch(() => null);
            cargar();
          }} />
        )}
        contentContainerStyle={movements.length === 0 ? styles.emptyContainer : styles.listContent}
        ListEmptyComponent={
          <EmptyState title="Sin movimientos" message="No hay movimientos en esta categoria" icon="📭" />
        }
        refreshControl={
          <RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />
        }
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    padding: spacing.md,
    borderBottomWidth: 1,
    gap: 4,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  dot: {
    width: 16,
    height: 16,
    borderRadius: 8,
  },
  listContent: { padding: spacing.md },
  emptyContainer: { flex: 1 },
});

export default CategoryDrillDownScreen;
