import React, { useCallback, useMemo, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, RefreshControl } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import AppText from '../../components/atoms/Text';
import Icono from '../../components/atoms/Icono';
import CirculoCategoria from '../../components/atoms/CirculoCategoria';
import Spinner from '../../components/atoms/Spinner';
import Encabezado from '../../components/molecules/Encabezado';
import EstadoError from '../../components/molecules/EstadoError';
import { PALETA } from '../../components/molecules/DonaDeCategorias';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import { useAlcance } from '../../contexts/AlcanceContext';
import { useCarga, exigir } from '../../hooks/useCarga';
import { movementService } from '../../services/movementService';
import { categoryService } from '../../services/categoryService';
import { formatCurrency, formatDateShort } from '../../utils/format';
import { Category, Movement } from '../../types';

type Tipo = 'EXPENSE' | 'INCOME';

interface Grupo {
  id: string;
  nombre: string;
  icono?: string;
  color: string;
  total: number;
  movimientos: Movement[];
}

/*
 * LOS MOVIMIENTOS DEL PERIODO, como la lista de Monefy: agrupados por
 * categoria, de la que mas movio a la que menos, con su porcentaje y su total.
 * Cada categoria se despliega para ver sus movimientos, y "Expandir todo" las
 * abre todas: con mirar la parte de arriba ya se ve todo lo que tuvo
 * movimiento, y las categorias sin movimientos quedan al final.
 */
const MovimientosScreen: React.FC<{ navigation: any; route?: any }> = ({ navigation, route }) => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const { alcance, periodo, nombreDe } = useAlcance();
  const [movs, setMovs] = useState<Movement[]>([]);
  const [categorias, setCategorias] = useState<Category[]>([]);
  const [tipo, setTipo] = useState<Tipo>(route?.params?.tipo || 'EXPENSE');
  const pedida = route?.params?.categoriaId ? String(route.params.categoriaId) : null;
  const [abiertas, setAbiertas] = useState<Record<string, boolean>>(pedida ? { [pedida]: true } : {});

  const traer = useCallback(async () => {
    if (!user) return;
    const [m, c] = await Promise.all([
      movementService.getAll({ userId: user.userId, startDate: periodo.inicio, endDate: periodo.fin, page: 0, size: 500 }),
      categoryService.getAll({ userId: user.userId, page: 0, size: 200 }),
    ]);
    setMovs(exigir(m)?.list || []);
    setCategorias(exigir(c)?.list || []);
  }, [user, periodo.inicio, periodo.fin]);
  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traer);
  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  const grupos = useMemo(() => {
    /* Lo que no es gasto ni ingreso (aperturas, liquidaciones, la otra pata de
       una transferencia, el detalle de un sub-gasto) no va en esta cuenta. */
    const validos = movs.filter((m: any) => m.active !== false && !m.isOpening && !m.isSettlement
      && !m.isTransfer && !m.parentMovementId);
    const delAlcance = validos.filter((m) => alcance.tipo === 'HOGAR'
      ? m.shared !== false
      : String(m.userId) === String(alcance.userId));
    const visibles = categorias.filter((c) => c.type === tipo && c.active !== false
      && (alcance.tipo === 'HOGAR' ? !!c.householdId : true));
    const mapa: Record<string, Grupo> = {};
    visibles.forEach((c, i) => {
      mapa[String(c.id)] = { id: String(c.id), nombre: c.name, icono: c.icon, color: c.color || PALETA[i % PALETA.length], total: 0, movimientos: [] };
    });
    delAlcance.filter((m) => m.categoryType === tipo || (!m.categoryType && mapa[String(m.categoryId)])).forEach((m) => {
      const k = String(m.categoryId);
      if (!mapa[k]) mapa[k] = { id: k, nombre: m.categoryName || 'Sin categoría', icono: m.categoryIcon, color: m.categoryColor || PALETA[0], total: 0, movimientos: [] };
      mapa[k].movimientos.push(m);
      if (m.confirmed) mapa[k].total += m.amount || 0;
    });
    Object.values(mapa).forEach((g) => g.movimientos.sort((a, b) => (b.date || '').localeCompare(a.date || '')));
    return Object.values(mapa).sort((a, b) => b.total - a.total || b.movimientos.length - a.movimientos.length
      || a.nombre.localeCompare(b.nombre));
  }, [movs, categorias, alcance, tipo]);

  const total = grupos.reduce((t, g) => t + g.total, 0);
  const conMovimientos = grupos.filter((g) => g.movimientos.length > 0);
  const todasAbiertas = conMovimientos.length > 0 && conMovimientos.every((g) => abiertas[g.id]);

  const cabeza = <Encabezado titulo="Movimientos" atras />;
  if (cargando && movs.length === 0) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}>{cabeza}<Spinner fullScreen /></View>;
  if (error) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}>{cabeza}<EstadoError mensaje={error} onReintentar={cargar} /></View>;

  return (
    <View style={[styles.pantalla, { backgroundColor: colors.background }]}>
      {cabeza}
      <ScrollView contentContainerStyle={styles.contenido}
        refreshControl={<RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />}>
        <View style={[styles.pestanas, { backgroundColor: colors.surfaceLight }]}>
          {(['EXPENSE', 'INCOME'] as Tipo[]).map((t) => (
            <TouchableOpacity key={t} style={[styles.pestana, tipo === t && { backgroundColor: t === 'EXPENSE' ? colors.expense : colors.income }]}
              onPress={() => setTipo(t)} accessibilityRole="tab" accessibilityState={{ selected: tipo === t }}
              accessibilityLabel={t === 'EXPENSE' ? 'Ver los gastos' : 'Ver los ingresos'}>
              <AppText variant="caption" style={{ fontWeight: '700', color: tipo === t ? '#FFFFFF' : colors.textSecondary }}>
                {t === 'EXPENSE' ? 'Gastos' : 'Ingresos'}
              </AppText>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.cabecera}>
          <View style={{ flex: 1 }}>
            <AppText variant="caption" color={colors.textSecondary}>
              {tipo === 'EXPENSE' ? 'Pagado en el periodo (gastos y ahorros)' : 'Recibido en el periodo'}
            </AppText>
            <AppText variant="subtitle" style={{ fontWeight: '800', color: tipo === 'EXPENSE' ? colors.expense : colors.income }}>
              {formatCurrency(total)}
            </AppText>
          </View>
          {conMovimientos.length > 0 && (
            <TouchableOpacity style={styles.expandir} onPress={() => setAbiertas(todasAbiertas ? {} : Object.fromEntries(conMovimientos.map((g) => [g.id, true])))}
              accessibilityRole="button" accessibilityLabel={todasAbiertas ? 'Contraer todo' : 'Expandir todo'}>
              <AppText variant="caption" style={{ color: colors.primary, fontWeight: '700' }}>
                {todasAbiertas ? 'Contraer todo' : 'Expandir todo'}
              </AppText>
            </TouchableOpacity>
          )}
        </View>

        {grupos.map((g) => {
          const abierta = !!abiertas[g.id];
          const vacia = g.movimientos.length === 0;
          const pct = total > 0 ? Math.round((g.total / total) * 100) : 0;
          return (
            <View key={g.id} style={[styles.grupo, { borderBottomColor: colors.border }]}>
              <TouchableOpacity style={[styles.fila, vacia && { opacity: 0.5 }]} disabled={vacia}
                onPress={() => setAbiertas({ ...abiertas, [g.id]: !abierta })}
                accessibilityRole="button" accessibilityState={{ expanded: abierta, disabled: vacia }}
                accessibilityLabel={`${g.nombre}: ${formatCurrency(g.total)}, ${g.movimientos.length} movimientos. ${abierta ? 'Contraer' : 'Expandir'}`}>
                <CirculoCategoria icono={g.icono} color={g.color} tamano={38} />
                <View style={{ flex: 1, minWidth: 0 }}>
                  <AppText variant="body" style={{ fontWeight: '700' }} numberOfLines={1}>{g.nombre}</AppText>
                  <AppText variant="caption" color={colors.textSecondary}>
                    {vacia ? 'Sin movimientos' : `${g.movimientos.length} ${g.movimientos.length === 1 ? 'movimiento' : 'movimientos'} · ${pct}%`}
                  </AppText>
                </View>
                <AppText variant="body" style={{ fontWeight: '800' }}>{formatCurrency(g.total)}</AppText>
                {!vacia && <Icono nombre={abierta ? 'down' : 'right'} color={colors.textSecondary} tamano={18} />}
              </TouchableOpacity>
              {abierta && g.movimientos.map((m) => (
                <TouchableOpacity key={m.id} style={[styles.movimiento, { borderTopColor: colors.border }]}
                  onPress={() => navigation.navigate('MovementFormModal', { movement: m })}
                  accessibilityRole="button"
                  accessibilityLabel={`${m.description || g.nombre}, ${formatDateShort(m.date)}, ${formatCurrency(m.amount)}${m.confirmed ? '' : ', por pagar'}. Editar`}>
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <AppText variant="body" numberOfLines={1}>{m.description || g.nombre}</AppText>
                    <AppText variant="caption" color={m.confirmed ? colors.textSecondary : colors.pending} numberOfLines={1}>
                      {formatDateShort(m.date)}{m.confirmed ? '' : ' · por pagar'}
                      {alcance.tipo === 'HOGAR' ? ` · ${m.payerName || nombreDe(m.userId)}` : ''}
                    </AppText>
                  </View>
                  <AppText variant="body" style={{ fontWeight: '700', color: m.confirmed ? colors.text : colors.textMuted }}>
                    {formatCurrency(m.amount)}
                  </AppText>
                </TouchableOpacity>
              ))}
            </View>
          );
        })}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  pantalla: { flex: 1 },
  contenido: { padding: 16, paddingBottom: 32 },
  pestanas: { flexDirection: 'row', borderRadius: 12, padding: 4, marginBottom: 12 },
  pestana: { flex: 1, alignItems: 'center', paddingVertical: 10, borderRadius: 9, minHeight: 44, justifyContent: 'center' },
  cabecera: { flexDirection: 'row', alignItems: 'center', marginBottom: 4 },
  expandir: { minHeight: 44, justifyContent: 'center', paddingHorizontal: 8 },
  grupo: { borderBottomWidth: 1 },
  fila: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 12, minHeight: 60 },
  movimiento: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10, paddingLeft: 50, borderTopWidth: 1, minHeight: 52 },
});

export default MovimientosScreen;
