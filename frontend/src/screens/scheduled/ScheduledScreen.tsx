import React, { useState, useCallback } from 'react';
import { View, StyleSheet, FlatList, RefreshControl, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Button from '../../components/atoms/Button';
import EmptyState from '../../components/molecules/EmptyState';
import EstadoError from '../../components/molecules/EstadoError';
import Spinner from '../../components/atoms/Spinner';
import { spacing } from '../../theme';
import { scheduledService } from '../../services/scheduledService';
import { ScheduledMovement, ProposedOccurrence } from '../../types';
import { useCarga, exigir } from '../../hooks/useCarga';
import { formatCurrency, formatDateShort } from '../../utils/format';
import { getFrequencyLabel } from '../../utils/labels';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type ScheduledStackParamList = {
  ScheduledList: undefined;
  ScheduledForm: { scheduled?: ScheduledMovement };
};

type Props = NativeStackScreenProps<ScheduledStackParamList, 'ScheduledList'>;

const ScheduledScreen: React.FC<Props> = ({ navigation }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();
  const [items, setItems] = useState<ScheduledMovement[]>([]);
  const [generating, setGenerating] = useState(false);
  const [propuestas, setPropuestas] = useState<ProposedOccurrence[]>([]);
  const [propuestasTotal, setPropuestasTotal] = useState(0);
  const [registrando, setRegistrando] = useState(false);

  const traerProgramados = useCallback(async () => {
    if (!user) return;
    const res = await scheduledService.getAll({
      userId: user.userId,
      page: 0,
      size: 50,
    });
    setItems(exigir(res)?.list || []);
  }, [user]);

  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traerProgramados);

  useFocusEffect(
    useCallback(() => {
      cargar();
    }, [cargar]),
  );

  const handleGenerate = async () => {
    if (!user) return;
    setGenerating(true);
    try {
      const res = await scheduledService.generatePending(user.userId, user.budgetStartDay || 1);
      if (res.correct) {
        const creados = res.object?.created?.length || 0;
        const porRevisar = res.object?.proposals || [];
        setPropuestas(porRevisar);
        setPropuestasTotal(res.object?.proposalsTotal || porRevisar.length);
        showToast('success', 'Listo', `Se generaron ${creados} movimientos pendientes`);
        // Generar cambia la lista: refrescarla aqui evita tener que salir y volver.
        await cargar();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo generar');
    } finally {
      setGenerating(false);
    }
  };

  // Aceptar las propuestas es lo que las convierte en movimientos. Sin esto, un
  // programado muy atrasado propondria lo mismo para siempre y no habria forma
  // de ponerse al dia desde la app.
  const handleRegistrarPropuestas = async () => {
    if (propuestas.length === 0) return;
    setRegistrando(true);
    try {
      const res = await scheduledService.materialize(
        propuestas.map((p) => ({
          scheduledMovementId: p.scheduledMovementId,
          periodStart: p.periodStart,
        })),
      );
      if (res.correct) {
        showToast('success', 'Listo', `Se registraron ${res.object?.length || 0} movimientos`);
        setPropuestas([]);
        setPropuestasTotal(0);
        await cargar();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo registrar');
    } finally {
      setRegistrando(false);
    }
  };

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
      <View style={styles.headerRow}>
        <AppText variant="subtitle">Programados</AppText>
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <Button
            title="Generar"
            accessibilityLabel="Generar los movimientos que ya tocaban"
            onPress={handleGenerate}
            loading={generating}
            size="small"
            variant="secondary"
          />
          <Button
            title="+ Nuevo"
            accessibilityLabel="Crear un movimiento programado"
            onPress={() => navigation.navigate('ScheduledForm', {})}
            size="small"
          />
        </View>
      </View>

      {propuestas.length > 0 && (
        <Card style={[styles.revisionCard, { borderColor: colors.warning || colors.expense }]}>
          <AppText variant="body" style={styles.revisionTitulo}>
            {`${propuestasTotal} ocurrencias atrasadas sin registrar`}
          </AppText>
          {/* El estado no depende solo del color: lo dice el texto. */}
          <AppText variant="caption">
            No se crearon solas porque son mas de cinco. Revisalas y registralas
            si de verdad ocurrieron.
          </AppText>
          {propuestas.slice(0, 5).map((p) => (
            <View
              key={`${p.scheduledMovementId}-${p.periodStart}`}
              style={styles.revisionFila}
              accessible
              accessibilityLabel={`${p.name}, del ${formatDateShort(p.date)}: ${formatCurrency(p.amount)}`}
            >
              <AppText variant="caption" numberOfLines={1} style={styles.revisionNombre}>
                {`${formatDateShort(p.date)} - ${p.name}`}
              </AppText>
              <AppText variant="caption">{formatCurrency(p.amount)}</AppText>
            </View>
          ))}
          {propuestasTotal > propuestas.length && (
            <AppText variant="caption">
              {`Mostrando ${propuestas.length}; las demas apareceran despues.`}
            </AppText>
          )}
          <Button
            title={`Registrar ${propuestas.length}`}
            onPress={handleRegistrarPropuestas}
            loading={registrando}
            size="small"
            style={styles.revisionBoton}
          />
        </Card>
      )}

      <FlatList
        data={items}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <Card
            style={styles.itemCard}
            accessibilityLabel={
              `${item.name}, ${getFrequencyLabel(item.frequency).toLowerCase()}, ` +
              `${item.categoryName || 'sin categoría'}: ${formatCurrency(item.amount)}`
            }
            onPress={() => navigation.navigate('ScheduledForm', { scheduled: item })}
          >
            <View style={styles.itemRow}>
              <View style={styles.itemInfo}>
                <AppText variant="body" numberOfLines={1}>{item.name}</AppText>
                <AppText variant="caption">
                  {getFrequencyLabel(item.frequency)} - {item.categoryName || 'Sin categoria'}
                </AppText>
              </View>
              <AppText
                variant="body"
                color={item.categoryType === 'INCOME' ? colors.income : colors.expense}
                style={styles.itemAmount}
              >
                {formatCurrency(item.amount)}
              </AppText>
            </View>
          </Card>
        )}
        contentContainerStyle={items.length === 0 ? styles.emptyContainer : styles.listContent}
        ListEmptyComponent={
          <EmptyState
            title="Sin programados"
            message="Configura movimientos recurrentes"
            icon="📅"
          />
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
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.md,
  },
  listContent: { padding: spacing.md, paddingTop: 0 },
  emptyContainer: { flex: 1 },
  itemCard: { marginBottom: spacing.sm },
  itemRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  itemInfo: { flex: 1, marginRight: spacing.sm },
  itemAmount: { fontWeight: '600' },
  revisionCard: {
    marginHorizontal: spacing.md,
    marginBottom: spacing.sm,
    borderWidth: 1.5,
  },
  revisionTitulo: { fontWeight: '600' },
  revisionFila: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing.xs,
  },
  revisionNombre: { flex: 1, marginRight: spacing.sm },
  revisionBoton: { marginTop: spacing.sm },
  fab: {
    position: 'absolute',
    right: spacing.md,
    bottom: spacing.lg,
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
    elevation: 6,
  },
  fabText: { fontSize: 28, marginTop: -2 },
});

export default ScheduledScreen;
