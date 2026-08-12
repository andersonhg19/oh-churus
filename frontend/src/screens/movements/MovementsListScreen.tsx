import React, { useState, useCallback, useEffect, useRef } from 'react';
import { View, StyleSheet, FlatList, RefreshControl } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import MovementItem from '../../components/molecules/MovementItem';
import EmptyState from '../../components/molecules/EmptyState';
import EstadoError from '../../components/molecules/EstadoError';
import CapsuleToggle from '../../components/molecules/CapsuleToggle';
import PeriodNavigator from '../../components/molecules/PeriodNavigator';
import Spinner from '../../components/atoms/Spinner';
import { spacing } from '../../theme';
import { movementService } from '../../services/movementService';
import { Movement } from '../../types';
import { useCarga, exigir } from '../../hooks/useCarga';
import { getStartOfPeriod, getEndOfPeriod, navigatePeriod } from '../../utils/periodUtils';
import { useFocusEffect, useNavigation } from '@react-navigation/native';

type FilterMode = 'ALL' | 'CONFIRMED' | 'PENDING';

const MovementsListScreen: React.FC = () => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const rootNav = useNavigation<any>();

  const [movements, setMovements] = useState<Movement[]>([]);
  const [search, setSearch] = useState('');
  const [filterMode, setFilterMode] = useState<FilterMode>('ALL');

  /*
   * El navegador de periodo venia de MovementsScreen, una pantalla completa
   * —con sus pruebas— que nunca llego a registrarse en ninguna ruta: nadie
   * podia abrirla. Sin el, esta lista mostraba los ultimos 100 movimientos
   * sueltos, sin forma de mirar el mes pasado, que es justo lo que se quiere
   * hacer con un presupuesto mensual. Se trae aqui y la huerfana se retira.
   */
  const diaDeCorte = user?.budgetStartDay ?? 1;
  const [inicioPeriodo, setInicioPeriodo] = useState(() => getStartOfPeriod(diaDeCorte, new Date()));
  const [finPeriodo, setFinPeriodo] = useState(() =>
    getEndOfPeriod(diaDeCorte, getStartOfPeriod(diaDeCorte, new Date())));

  const cambiarPeriodo = (direccion: 'prev' | 'next') => {
    const { start, end } = navigatePeriod(inicioPeriodo, diaDeCorte, direccion);
    setInicioPeriodo(start);
    setFinPeriodo(end);
  };

  /* No se puede navegar al futuro: un periodo que no ha empezado esta vacio
     por definicion y solo confunde. */
  const puedeAvanzar = inicioPeriodo < getStartOfPeriod(diaDeCorte, new Date());

  const traerMovimientos = useCallback(async () => {
    if (!user) return;
    const filter: any = {
      userId: user.userId,
      page: 0,
      size: 100,
      startDate: inicioPeriodo,
      endDate: finPeriodo,
    };
    if (filterMode === 'CONFIRMED') filter.confirmed = true;
    if (filterMode === 'PENDING') filter.confirmed = false;

    const res = await movementService.getAll(filter);
    setMovements(exigir(res)?.list || []);
  }, [user, filterMode, inicioPeriodo, finPeriodo]);

  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traerMovimientos);

  /*
   * Antes esta lista solo se pedia en el montaje: creabas o editabas un
   * movimiento en el modal, volvias, y seguia la lista vieja. Al enfocar se
   * vuelve a pedir.
   */
  useFocusEffect(
    useCallback(() => {
      cargar();
    }, [cargar]),
  );

  /*
   * Recargar cuando cambian el periodo o el filtro, explicitamente.
   *
   * Funcionaba de rebote: useFocusEffect vuelve a ejecutarse cuando cambia la
   * identidad de su callback, y esta cambia porque `cargar` depende de los
   * filtros. Es cierto, pero es un efecto lateral que se rompe en cuanto
   * alguien memoiza algo, y no se ve leyendo el codigo. Mejor decirlo.
   */
  const esElPrimerRender = useRef(true);
  useEffect(() => {
    if (esElPrimerRender.current) {
      esElPrimerRender.current = false;
      return;
    }
    cargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inicioPeriodo, finPeriodo, filterMode]);

  const filtered = search.trim()
    ? movements.filter(m =>
        (m.description || '').toLowerCase().includes(search.toLowerCase()) ||
        (m.categoryName || '').toLowerCase().includes(search.toLowerCase()))
    : movements;

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
      <PeriodNavigator
        periodStart={inicioPeriodo}
        periodEnd={finPeriodo}
        onPrevious={() => cambiarPeriodo('prev')}
        onNext={() => cambiarPeriodo('next')}
        canGoNext={puedeAvanzar}
      />

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
              await movementService.confirm(String(item.id)).catch(() => null);
              cargar();
            }}
          />
        )}
        contentContainerStyle={filtered.length === 0 ? styles.emptyContainer : styles.listContent}
        ListEmptyComponent={
          <EmptyState title="Sin resultados" message={search ? 'Intenta otra busqueda' : 'No hay movimientos'} icon="🔍" />
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
  header: { padding: spacing.md, gap: spacing.sm },
  count: { paddingHorizontal: spacing.md, marginBottom: spacing.xs },
  listContent: { padding: spacing.md, paddingTop: 0 },
  emptyContainer: { flex: 1 },
});

export default MovementsListScreen;
