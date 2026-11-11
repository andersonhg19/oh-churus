import React, { useCallback, useMemo, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, RefreshControl, TextInput } from 'react-native';
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
import { estadoService, ElementoDelPlan } from '../../services/estadoService';
import { formatCurrency, formatDateShort } from '../../utils/format';
import { comoSePago } from '../../utils/medioDePago';
import { useAhorros, useMisTarjetas } from '../../hooks/useAhorros';
import { Category, Movement } from '../../types';

type Tipo = 'EXPENSE' | 'INCOME' | 'TODOS';
type Vista = 'CATEGORIA' | 'FECHA';

const DIAS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
/** "lun 10 nov" */
export const diaLargo = (iso?: string) => {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || '');
  if (!m) return '';
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  return `${DIAS[d.getDay()]} ${d.getDate()} ${MESES[d.getMonth()]}`;
};

/** Para buscar sin que importen mayusculas ni tildes: "credito" encuentra "Crédito". */
const normalizar = (texto?: string) =>
  (texto || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();

interface Grupo {
  id: string;
  nombre: string;
  icono?: string;
  color: string;
  /** Lo pagado (o recibido): lo que suma. */
  total: number;
  /** Lo que aun esta por pagar: se cuenta y se dice aparte, no suma. */
  porPagar: number;
  movimientos: Movement[];
  /** Pagos del plan liberados del todo: no son movimientos, pero no deben desaparecer. */
  liberados: ElementoDelPlan[];
}

/*
 * LOS MOVIMIENTOS DEL PERIODO, como la lista de Monefy: agrupados por
 * categoria, de la que mas movio a la que menos, con su porcentaje y su total.
 * Cada categoria se despliega para ver sus movimientos, y "Expandir todo" las
 * abre todas: con mirar la parte de arriba ya se ve todo lo que tuvo
 * movimiento, y las categorias sin movimientos quedan al final.
 *
 * La caja de busqueda reemplaza a la antigua pantalla "Buscar movimientos":
 * filtra lo del periodo por la nota o por el nombre de la categoria, y deja a
 * la vista solo las categorias con algo que coincida, ya desplegadas.
 */
const MovimientosScreen: React.FC<{ navigation: any; route?: any }> = ({ navigation, route }) => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const { alcance, periodo, corte, nombreDe } = useAlcance();
  const [movs, setMovs] = useState<Movement[]>([]);
  const [liberados, setLiberados] = useState<(ElementoDelPlan & { categoryId: number })[]>([]);
  const [categorias, setCategorias] = useState<Category[]>([]);
  const [tipo, setTipo] = useState<Tipo>(route?.params?.tipo || 'EXPENSE');
  /* Por categoria (como Monefy) o por fecha: la lista de todo, dia por dia. */
  const [vista, setVista] = useState<Vista>(route?.params?.vista || 'CATEGORIA');
  const porFecha = vista === 'FECHA' || tipo === 'TODOS';
  const pedida = route?.params?.categoriaId ? String(route.params.categoriaId) : null;
  const [abiertas, setAbiertas] = useState<Record<string, boolean>>(pedida ? { [pedida]: true } : {});
  const [busqueda, setBusqueda] = useState('');
  const buscado = normalizar(busqueda);
  /* Para decir con que se pago cada movimiento (solo lectura). */
  const ahorros = useAhorros();
  const tarjetas = useMisTarjetas();

  const traer = useCallback(async () => {
    if (!user) return;
    const [m, c, e] = await Promise.all([
      movementService.getAll({ userId: user.userId, startDate: periodo.inicio, endDate: periodo.fin, page: 0, size: 500 }),
      categoryService.getAll({ userId: user.userId, page: 0, size: 200 }),
      estadoService.periodo(corte, periodo.inicio, alcance).catch(() => null),
    ]);
    setMovs(exigir(m)?.list || []);
    setCategorias(exigir(c)?.list || []);
    /* Lo liberado del todo ya no es un movimiento activo, pero tiene que
       seguir viendose: si no, parece que el pago se perdio. */
    const est = e && e.correct ? e.object : null;
    setLiberados((est?.categories || []).flatMap((cat) => (cat.items || [])
      .filter((it) => it.status === 'LIBERADO' && (alcance.tipo === 'HOGAR' || String(it.payerUserId) === String(alcance.userId)))
      .map((it) => ({ ...it, categoryId: cat.categoryId }))));
  }, [user, periodo.inicio, periodo.fin, corte, alcance]);
  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traer);
  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  const grupos = useMemo(() => {
    /* El detalle de un sub-gasto no va en esta cuenta: ya lo cuenta su padre. */
    const validos = movs.filter((m: any) => m.active !== false && !m.parentMovementId);
    const delAlcance = validos.filter((m) => alcance.tipo === 'HOGAR'
      ? m.shared !== false
      : String(m.userId) === String(alcance.userId));
    const deTipo = (t?: string) => tipo === 'TODOS' || t === tipo;
    const visibles = categorias.filter((c) => deTipo(c.type) && c.active !== false
      && (alcance.tipo === 'HOGAR' ? !!c.householdId : true));
    const mapa: Record<string, Grupo> = {};
    visibles.forEach((c, i) => {
      mapa[String(c.id)] = { id: String(c.id), nombre: c.name, icono: c.icon, color: c.color || PALETA[i % PALETA.length], total: 0, porPagar: 0, movimientos: [], liberados: [] };
    });
    const coincide = (m: Movement) => !buscado
      || normalizar(m.description).includes(buscado)
      || normalizar(m.categoryName || mapa[String(m.categoryId)]?.nombre).includes(buscado);
    delAlcance.filter((m) => deTipo(m.categoryType) || (!m.categoryType && mapa[String(m.categoryId)])).filter(coincide).forEach((m) => {
      const k = String(m.categoryId);
      if (!mapa[k]) mapa[k] = { id: k, nombre: m.categoryName || 'Sin categoría', icono: m.categoryIcon, color: m.categoryColor || PALETA[0], total: 0, porPagar: 0, movimientos: [], liberados: [] };
      mapa[k].movimientos.push(m);
      if (m.confirmed) mapa[k].total += m.amount || 0;
      else mapa[k].porPagar += m.amount || 0;
    });
    liberados.forEach((it) => {
      const g = mapa[String(it.categoryId)];
      if (g && (!buscado || normalizar(it.name).includes(buscado) || normalizar(g.nombre).includes(buscado))) g.liberados.push(it);
    });
    Object.values(mapa).forEach((g) => g.movimientos.sort((a, b) => (b.date || '').localeCompare(a.date || '')));
    const cuantos = (g: Grupo) => g.movimientos.length + g.liberados.length;
    /* De la que mas movio a la que menos; las que no movieron (o empatan), por nombre. */
    return Object.values(mapa).filter((g) => !buscado || cuantos(g) > 0)
      .sort((a, b) => b.total - a.total || a.nombre.localeCompare(b.nombre, 'es'));
  }, [movs, categorias, alcance, tipo, liberados, buscado]);

  const total = grupos.reduce((t, g) => t + g.total, 0);

  /* POR FECHA: todos los movimientos del periodo, del mas nuevo al mas viejo,
     agrupados por dia, con la nota adelante (es lo que dice en que se gasto). */
  const colorDe = useMemo(() => Object.fromEntries(grupos.map((g) => [g.id, g])), [grupos]);
  const dias = useMemo(() => {
    const todos = grupos.flatMap((g) => g.movimientos)
      .sort((a, b) => (b.date || '').localeCompare(a.date || '') || Number(b.id) - Number(a.id));
    const out: { dia: string; movs: Movement[]; neto: number }[] = [];
    todos.forEach((m) => {
      let d = out[out.length - 1];
      if (!d || d.dia !== m.date) { d = { dia: m.date, movs: [], neto: 0 }; out.push(d); }
      d.movs.push(m);
      if (m.confirmed) d.neto += (m.categoryType === 'INCOME' ? 1 : -1) * (m.amount || 0);
    });
    return out;
  }, [grupos]);
  const entradas = grupos.flatMap((g) => g.movimientos).filter((m) => m.confirmed && m.categoryType === 'INCOME')
    .reduce((t, m) => t + (m.amount || 0), 0);
  /* Lo pagado con el ahorro no salio de lo del periodo: se dice aparte. */
  const gastados = grupos.flatMap((g) => g.movimientos).filter((m) => m.confirmed && m.categoryType !== 'INCOME');
  const salidas = gastados.filter((m: any) => !m.fundedFromCategoryId).reduce((t, m) => t + (m.amount || 0), 0);
  const conAhorros = gastados.filter((m: any) => !!m.fundedFromCategoryId).reduce((t, m) => t + (m.amount || 0), 0);

  const filaMovimiento = (m: Movement, conCategoria: boolean) => {
    const g = colorDe[String(m.categoryId)];
    const esIngreso = (m.categoryType || tipo) === 'INCOME';
    /* Como se pago: "Tarjeta Nu · cuota 1/6", "Con el Ahorro", "Fondos del mes". */
    const medio = comoSePago({ ...m, categoryType: m.categoryType || (tipo === 'TODOS' ? 'EXPENSE' : tipo) }, ahorros, tarjetas);
    const quien = alcance.tipo === 'HOGAR' ? (m.payerName || nombreDe(m.userId)) : '';
    const pendiente = !m.confirmed ? (esIngreso ? 'por recibir' : 'por pagar') : '';
    const categoria = g?.nombre || m.categoryName || '';
    const nota = (m.description || '').trim();
    const detalle = [conCategoria && nota ? categoria : '', conCategoria ? '' : formatDateShort(m.date), pendiente, medio, quien]
      .filter(Boolean).join(' · ');
    return (
      <TouchableOpacity key={m.id} style={[conCategoria ? styles.movFecha : styles.movimiento, { borderTopColor: colors.border }]}
        onPress={() => navigation.navigate('MovementFormModal', { movement: m })}
        accessibilityRole="button"
        accessibilityLabel={`${nota || categoria}, ${conCategoria && nota ? `${categoria}, ` : ''}${detalle}, ${formatCurrency(m.amount)}. Editar`}>
        {conCategoria && <CirculoCategoria icono={g?.icono || m.categoryIcon} color={g?.color || m.categoryColor || PALETA[0]} tamano={34} />}
        <View style={{ flex: 1, minWidth: 0 }}>
          <AppText variant="body" style={{ fontWeight: nota ? '700' : '400' }} color={nota ? colors.text : colors.textSecondary} numberOfLines={2}>
            {nota || (conCategoria ? categoria : 'Sin nota')}
          </AppText>
          {!!detalle && (
            <AppText variant="caption" color={m.confirmed ? colors.textSecondary : colors.pending} numberOfLines={2}>{detalle}</AppText>
          )}
        </View>
        <AppText variant="body" style={{ fontWeight: '700', color: !m.confirmed ? colors.textMuted : esIngreso && conCategoria ? colors.income : colors.text }}>
          {`${conCategoria ? (esIngreso ? '+' : '−') : ''}${formatCurrency(m.amount)}`}
        </AppText>
      </TouchableOpacity>
    );
  };
  const conMovimientos = grupos.filter((g) => g.movimientos.length + g.liberados.length > 0);
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
          {(['EXPENSE', 'INCOME', 'TODOS'] as Tipo[]).map((t) => (
            <TouchableOpacity key={t} style={[styles.pestana, tipo === t && { backgroundColor: t === 'EXPENSE' ? colors.expense : t === 'INCOME' ? colors.income : colors.primary }]}
              onPress={() => setTipo(t)} accessibilityRole="tab" accessibilityState={{ selected: tipo === t }}
              accessibilityLabel={t === 'EXPENSE' ? 'Ver los gastos' : t === 'INCOME' ? 'Ver los ingresos' : 'Ver todos los movimientos'}>
              <AppText variant="caption" style={{ fontWeight: '700', color: tipo === t ? (t === 'TODOS' ? '#1A1405' : '#FFFFFF') : colors.textSecondary }}>
                {t === 'EXPENSE' ? 'Gastos' : t === 'INCOME' ? 'Ingresos' : 'Todos'}
              </AppText>
            </TouchableOpacity>
          ))}
        </View>
        {tipo !== 'TODOS' && (
          <View style={styles.vistas}>
            {(['CATEGORIA', 'FECHA'] as Vista[]).map((v) => (
              <TouchableOpacity key={v} onPress={() => setVista(v)}
                style={[styles.vista, { borderColor: vista === v ? colors.primary : colors.border, backgroundColor: vista === v ? colors.surfaceLight : 'transparent' }]}
                accessibilityRole="tab" accessibilityState={{ selected: vista === v }}
                accessibilityLabel={v === 'CATEGORIA' ? 'Agrupar por categoría' : 'Ver la lista por fecha'}>
                <Icono nombre={v === 'CATEGORIA' ? 'chart' : 'list'} color={vista === v ? colors.primary : colors.textSecondary} tamano={16} />
                <AppText variant="caption" style={{ fontWeight: '700', color: vista === v ? colors.primary : colors.textSecondary }}>
                  {v === 'CATEGORIA' ? 'Por categoría' : 'Por fecha'}
                </AppText>
              </TouchableOpacity>
            ))}
          </View>
        )}

        <View style={[styles.buscador, { backgroundColor: colors.surfaceLight }]}>
          <Icono nombre="search" color={colors.textSecondary} tamano={18} />
          <TextInput
            value={busqueda}
            onChangeText={setBusqueda}
            placeholder="Buscar por nota o categoría"
            placeholderTextColor={colors.textMuted}
            style={[styles.campoBusqueda, { color: colors.text }]}
            accessibilityLabel="Buscar movimientos por nota o categoría"
            autoCorrect={false}
            returnKeyType="search"
          />
          {!!busqueda && (
            <TouchableOpacity onPress={() => setBusqueda('')} style={styles.limpiar}
              accessibilityRole="button" accessibilityLabel="Borrar la búsqueda">
              <Icono nombre="close" color={colors.textSecondary} tamano={18} />
            </TouchableOpacity>
          )}
        </View>

        <View style={styles.cabecera}>
          <View style={{ flex: 1 }}>
            <AppText variant="caption" color={colors.textSecondary}>
              {buscado ? 'Lo que coincide con la búsqueda' : tipo === 'EXPENSE' ? 'Pagado en el periodo (gastos y ahorros)'
                : tipo === 'INCOME' ? 'Recibido en el periodo' : 'Entró · salió en el periodo'}
            </AppText>
            {tipo === 'TODOS' ? (
              <AppText variant="subtitle" style={{ fontWeight: '800' }}>
                <AppText variant="subtitle" style={{ fontWeight: '800', color: colors.income }}>{`+${formatCurrency(entradas)}`}</AppText>
                {'  '}
                <AppText variant="subtitle" style={{ fontWeight: '800', color: colors.expense }}>{`−${formatCurrency(salidas)}`}</AppText>
              </AppText>
            ) : null}
            {tipo === 'TODOS' && conAhorros > 0 && (
              <AppText variant="caption" color={colors.textSecondary}>{`y ${formatCurrency(conAhorros)} pagados con ahorros`}</AppText>
            )}
            {tipo !== 'TODOS' && (
              <AppText variant="subtitle" style={{ fontWeight: '800', color: tipo === 'EXPENSE' ? colors.expense : colors.income }}>
                {formatCurrency(total)}
              </AppText>
            )}
          </View>
          {!porFecha && conMovimientos.length > 0 && !buscado && (
            <TouchableOpacity style={styles.expandir} onPress={() => setAbiertas(todasAbiertas ? {} : Object.fromEntries(conMovimientos.map((g) => [g.id, true])))}
              accessibilityRole="button" accessibilityLabel={todasAbiertas ? 'Contraer todo' : 'Expandir todo'}>
              <AppText variant="caption" style={{ color: colors.primary, fontWeight: '700' }}>
                {todasAbiertas ? 'Contraer todo' : 'Expandir todo'}
              </AppText>
            </TouchableOpacity>
          )}
        </View>

        {porFecha && dias.length === 0 && (
          <AppText variant="body" color={colors.textSecondary} style={{ textAlign: 'center', marginTop: 24 }}>
            {buscado ? `Nada en este periodo coincide con «${busqueda.trim()}».` : 'No hay movimientos en este periodo.'}
          </AppText>
        )}
        {porFecha && dias.map((d) => (
          <View key={d.dia} style={styles.dia}>
            <View style={[styles.cabezaDia, { borderBottomColor: colors.border }]}>
              <AppText variant="label" color={colors.textSecondary} style={{ fontWeight: '700' }}>{diaLargo(d.dia)}</AppText>
              {d.neto !== 0 && (
                <AppText variant="caption" color={colors.textSecondary}>{`${d.neto > 0 ? '+' : '−'}${formatCurrency(Math.abs(d.neto))}`}</AppText>
              )}
            </View>
            {d.movs.map((m) => filaMovimiento(m, true))}
          </View>
        ))}

        {!porFecha && buscado && grupos.length === 0 && (
          <AppText variant="body" color={colors.textSecondary} style={{ textAlign: 'center', marginTop: 24 }}>
            Nada en este periodo coincide con «{busqueda.trim()}».
          </AppText>
        )}
        {!porFecha && grupos.map((g) => {
          /* Buscando, todo lo que coincide se ve sin tener que desplegarlo. */
          const abierta = !!buscado || !!abiertas[g.id];
          const vacia = g.movimientos.length + g.liberados.length === 0;
          const pct = total > 0 ? Math.round((g.total / total) * 100) : 0;
          /* "Casa · 5 movimientos · $2.388.000" mezclaba: el monto era de 3
             pagados y los otros 2 estaban por pagar. Ahora se dice cada cosa. */
          const pagados = g.movimientos.filter((m) => m.confirmed).length;
          const pendientes = g.movimientos.length - pagados;
          const esGasto = tipo === 'EXPENSE';
          const txtPagados = `${pagados} ${esGasto ? (pagados === 1 ? 'pagado' : 'pagados') : (pagados === 1 ? 'recibido' : 'recibidos')}`;
          const txtPendientes = pendientes > 0 ? `${pendientes} ${esGasto ? 'por pagar' : 'por recibir'}` : '';
          const resumen = vacia ? 'Sin movimientos'
            : [txtPagados, txtPendientes, `${pct}%`, g.liberados.length ? `${g.liberados.length} liberado${g.liberados.length === 1 ? '' : 's'}` : '']
              .filter(Boolean).join(' · ');
          return (
            <View key={g.id} style={[styles.grupo, { borderBottomColor: colors.border }]}>
              <TouchableOpacity style={[styles.fila, vacia && { opacity: 0.5 }]} disabled={vacia}
                onPress={() => setAbiertas({ ...abiertas, [g.id]: !abierta })}
                accessibilityRole="button" accessibilityState={{ expanded: abierta, disabled: vacia }}
                accessibilityLabel={`${g.nombre}: ${formatCurrency(g.total)} ${esGasto ? 'pagado' : 'recibido'}${vacia ? ', sin movimientos'
                  : `, ${txtPagados}${txtPendientes ? `, ${txtPendientes} por ${formatCurrency(g.porPagar)}` : ''}`}. ${abierta ? 'Contraer' : 'Expandir'}`}>
                <CirculoCategoria icono={g.icono} color={g.color} tamano={38} />
                <View style={{ flex: 1, minWidth: 0 }}>
                  <AppText variant="body" style={{ fontWeight: '700' }} numberOfLines={1}>{g.nombre}</AppText>
                  <AppText variant="caption" color={colors.textSecondary}>{resumen}</AppText>
                </View>
                <View style={{ alignItems: 'flex-end' }}>
                  <AppText variant="body" style={{ fontWeight: '800' }}>{formatCurrency(g.total)}</AppText>
                  {g.porPagar > 0 && (
                    <AppText variant="caption" color={colors.pending}>{`+ ${formatCurrency(g.porPagar)} ${esGasto ? 'por pagar' : 'por recibir'}`}</AppText>
                  )}
                </View>
                {!vacia && <Icono nombre={abierta ? 'down' : 'right'} color={colors.textSecondary} tamano={18} />}
              </TouchableOpacity>
              {abierta && g.movimientos.map((m) => filaMovimiento(m, false))}
              {abierta && g.liberados.map((it) => (
                <TouchableOpacity key={`lib-${it.movementId}`} style={[styles.movimiento, { borderTopColor: colors.border }]}
                  onPress={() => navigation.navigate('Categoria', { categoryId: Number(g.id) })}
                  accessibilityRole="button"
                  accessibilityLabel={`${it.name}, ${formatDateShort(it.date)}, liberado ${formatCurrency(it.released || 0)}. Ver en su presupuesto para deshacerlo`}>
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <AppText variant="body" numberOfLines={1} color={colors.textSecondary}>{it.name}</AppText>
                    <AppText variant="caption" color={colors.pending} numberOfLines={1}>
                      {`${formatDateShort(it.date)} · liberado, no se pagó · toca para deshacer`}
                    </AppText>
                  </View>
                  <AppText variant="body" style={{ fontWeight: '700', color: colors.textMuted, textDecorationLine: 'line-through' }}>
                    {formatCurrency(it.released || 0)}
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
  vistas: { flexDirection: 'row', gap: 8, marginBottom: 12 },
  vista: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, minHeight: 40, borderRadius: 10, borderWidth: 1 },
  dia: { marginTop: 8 },
  cabezaDia: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 6, borderBottomWidth: 1 },
  movFecha: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10, minHeight: 56 },
  buscador: { flexDirection: 'row', alignItems: 'center', gap: 8, borderRadius: 12, paddingLeft: 12, marginBottom: 12, minHeight: 44 },
  campoBusqueda: { flex: 1, fontSize: 16, paddingVertical: 10 },
  limpiar: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  grupo: { borderBottomWidth: 1 },
  fila: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 12, minHeight: 60 },
  movimiento: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10, paddingLeft: 50, borderTopWidth: 1, minHeight: 52 },
});

export default MovimientosScreen;
