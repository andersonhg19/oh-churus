import React, { useCallback, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, RefreshControl, Modal } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import AppText from '../../components/atoms/Text';
import Icono from '../../components/atoms/Icono';
import Spinner from '../../components/atoms/Spinner';
import Encabezado from '../../components/molecules/Encabezado';
import EstadoError from '../../components/molecules/EstadoError';
import { PALETA } from '../../components/molecules/DonaDeCategorias';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAlcance } from '../../contexts/AlcanceContext';
import { useCarga, exigir } from '../../hooks/useCarga';
import { estadoService, EstadoDelPeriodo, CategoriaDelPeriodo } from '../../services/estadoService';
import { budgetAllocationService } from '../../services/budgetAllocationService';
import { formatCurrency, formatCompacto } from '../../utils/format';
import MontoModal from '../../components/molecules/MontoModal';
import { categoryService } from '../../services/categoryService';
import { useAuth } from '../../contexts/AuthContext';
import { Category } from '../../types';
import { ESTADOS, colorDeTono } from '../../utils/estadoTextos';

type Vista = 'PLAN' | 'REAL' | 'QUIEN';
export const COLORES_PERSONA = ['#E8A838', '#8B7CF6', '#00CEC9', '#FF7675'];

/*
 * PRESUPUESTO: las tres cosas de un presupuesto conjunto, cada una en su
 * pestana y sobre la misma lista de categorias.
 *   Planeado  lo que dijimos que ibamos a gastar (o a ahorrar).
 *   Real      lo que se gasto de verdad, contra lo planeado.
 *   Quien     quien lo gasto, persona por persona.
 * El total de arriba es la suma de la lista de abajo: salen del mismo calculo.
 */
const PresupuestoScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { alcance, corte, periodo, miembros } = useAlcance();
  const [estado, setEstado] = useState<EstadoDelPeriodo | null>(null);
  const [vista, setVista] = useState<Vista>('REAL');
  const { user } = useAuth();
  /* Presupuestar una categoria que aun no tiene nada (un regalo, la obra
     social): se elige y se escribe el monto con el teclado. */
  const [eligiendoNueva, setEligiendoNueva] = useState<Category[] | null>(null);
  const [nueva, setNueva] = useState<Category | null>(null);

  const traer = useCallback(async () => {
    setEstado(exigir(await estadoService.periodo(corte, periodo.inicio, alcance)) || null);
  }, [corte, periodo.inicio, alcance]);
  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traer);
  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  const copiar = async () => {
    try {
      const r = await budgetAllocationService.copyPrevious(corte, periodo.inicio);
      if (!r.correct) { showToast('error', 'No se pudo copiar', r.message); return; }
      showToast('success', 'Presupuesto copiado', 'Del periodo anterior, sin pisar lo que ya estaba');
      cargar();
    } catch { showToast('error', 'Error', 'No se pudo copiar el presupuesto'); }
  };

  const abrirNueva = async () => {
    if (!user) return;
    try {
      const r = await categoryService.getAll({ userId: user.userId, page: 0, size: 200 });
      const ya = new Set((estado?.categories || []).filter((c) => c.allocated > 0).map((c) => String(c.categoryId)));
      const lista = ((r.correct && r.object?.list) || []).filter((c: Category) => c.type === 'EXPENSE' && c.active !== false
        && !ya.has(String(c.id)) && (alcance.tipo === 'HOGAR' ? !!c.householdId : !c.householdId));
      setEligiendoNueva(lista);
    } catch { showToast('error', 'Error', 'No se pudieron cargar las categorías'); }
  };

  const presupuestar = async (monto: number) => {
    if (!user || !nueva) return;
    try {
      const r = await budgetAllocationService.save({ userId: user.userId, categoryId: Number(nueva.id), amount: monto,
        budgetStartDay: corte, referenceDate: periodo.inicio });
      if (!r.correct) { showToast('error', 'No se pudo guardar', r.message); return; }
      showToast('success', 'Presupuesto guardado', `${nueva.name}: ${formatCurrency(monto)}`);
      setNueva(null);
      cargar();
    } catch { showToast('error', 'Error', 'No se pudo guardar el presupuesto'); }
  };

  const colorPersona = (userId: number) => {
    const i = miembros.findIndex((m) => m.userId === userId);
    return COLORES_PERSONA[(i < 0 ? 0 : i) % COLORES_PERSONA.length];
  };

  const encabezado = (
    <Encabezado titulo="Presupuesto" atras={navigation.canGoBack()} derecha={
      <TouchableOpacity onPress={() => navigation.navigate('Cierre')} style={styles.icono}
        accessibilityRole="button" accessibilityLabel="Ver el cierre de cada mes">
        <Icono nombre="history" color={colors.text} />
      </TouchableOpacity>
    } />
  );

  if (cargando && !estado) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}>{encabezado}<Spinner fullScreen /></View>;
  if (error) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}>{encabezado}<EstadoError mensaje={error} onReintentar={cargar} /></View>;

  const t = estado!.totals;
  const cats = estado!.categories;

  const fila = (c: CategoriaDelPeriodo, i: number) => {
    const est = ESTADOS[c.status];
    const tono = colorDeTono(est.tono, colors);
    const color = c.categoryColor || PALETA[i % PALETA.length];
    const pct = c.allocated > 0 ? Math.min(100, (c.actual / c.allocated) * 100) : c.actual > 0 ? 100 : 0;
    const total = c.byPerson.reduce((s, p) => s + p.actual, 0);
    return (
      <TouchableOpacity key={c.categoryId} style={[styles.fila, { borderBottomColor: colors.border }]}
        onPress={() => navigation.navigate('Categoria', { categoryId: c.categoryId })}
        accessibilityRole="button"
        accessibilityLabel={`${c.categoryName}: ${formatCurrency(c.actual)} de ${formatCurrency(c.allocated)}, ${est.texto}. Ver el detalle`}>
        <View style={[styles.circulo, { backgroundColor: color }]}><Icono nombre={c.categoryIcon} color="#FFFFFF" tamano={20} /></View>
        <View style={{ flex: 1, minWidth: 0, gap: 4 }}>
          <View style={styles.entre}>
            <AppText variant="body" style={{ fontWeight: '700' }} numberOfLines={1}>{c.categoryName}</AppText>
            <AppText variant="body" style={{ fontWeight: '800', color: vista === 'REAL' && est.tono === 'mal' ? colors.expense : colors.text }}>
              {formatCurrency(vista === 'PLAN' ? c.allocated : c.actual)}
            </AppText>
          </View>
          {vista === 'PLAN' && (
            <AppText variant="caption" color={colors.textSecondary} numberOfLines={1}>
              {c.kind === 'SAVING' ? 'Meta de ahorro' : c.provision > 0 ? `Bolsa de ${formatCurrency(c.provision)}` : c.hasAllocation ? 'Asignado' : 'Lo planeado en sus elementos'}
              {c.committed > 0 ? ` · por pagar ${formatCurrency(c.committed)}` : ''}
            </AppText>
          )}
          {vista === 'REAL' && (
            <>
              <View style={[styles.barra, { backgroundColor: colors.border }]}>
                <View style={{ width: `${pct}%`, backgroundColor: tono, height: '100%', borderRadius: 4 }} />
              </View>
              <View style={styles.entre}>
                <AppText variant="caption" style={{ color: tono }}>{est.texto}</AppText>
                <AppText variant="caption" color={colors.textSecondary}>de {formatCurrency(c.allocated)}</AppText>
              </View>
            </>
          )}
          {vista === 'QUIEN' && (
            <>
              <View style={[styles.barra, { backgroundColor: colors.border, flexDirection: 'row' }]}>
                {total > 0 && c.byPerson.map((p) => (
                  <View key={p.userId} style={{ width: `${(p.actual / total) * 100}%`, backgroundColor: colorPersona(p.userId), height: '100%' }} />
                ))}
              </View>
              <AppText variant="caption" color={colors.textSecondary} numberOfLines={2}>
                {total > 0 ? c.byPerson.filter((p) => p.actual > 0).map((p) => `${p.name || 'Alguien'} ${formatCurrency(p.actual)}`).join(' · ') : 'Nadie ha gastado aquí todavía'}
              </AppText>
            </>
          )}
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <View style={[styles.pantalla, { backgroundColor: colors.background }]}>
      {encabezado}
      <ScrollView contentContainerStyle={styles.contenido}
        refreshControl={<RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />}>
        <View style={[styles.pestanas, { backgroundColor: colors.surfaceLight }]}>
          {(['PLAN', 'REAL', 'QUIEN'] as Vista[]).map((v) => (
            <TouchableOpacity key={v} style={[styles.pestana, vista === v && { backgroundColor: colors.primary }]}
              onPress={() => setVista(v)} accessibilityRole="tab" accessibilityState={{ selected: vista === v }}
              accessibilityLabel={v === 'PLAN' ? 'Lo planeado' : v === 'REAL' ? 'Lo real contra lo planeado' : 'Quién gastó'}>
              <AppText variant="caption" style={{ fontWeight: '700', color: vista === v ? '#1A1405' : colors.textSecondary }}>
                {v === 'PLAN' ? 'Planeado' : v === 'REAL' ? 'Real' : 'Quién'}
              </AppText>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.totales}>
          <View style={styles.total}><AppText variant="caption" color={colors.textSecondary}>Planeado</AppText>
            <AppText variant="body" style={{ fontWeight: '800' }}>{formatCompacto(t.budgeted - t.savingsGoal)}</AppText></View>
          <View style={styles.total}><AppText variant="caption" color={colors.textSecondary}>Gastado</AppText>
            <AppText variant="body" style={{ fontWeight: '800' }}>{formatCompacto(t.spent)}</AppText></View>
          <View style={styles.total}><AppText variant="caption" color={colors.textSecondary}>Ahorro</AppText>
            <AppText variant="body" style={{ fontWeight: '800' }}>{formatCompacto(t.saved)} de {formatCompacto(t.savingsGoal)}</AppText></View>
        </View>
        {(t.unbudgetedSpending > 0 || t.unassigned !== 0) && (
          <AppText variant="caption" color={colors.textSecondary}>
            {t.unbudgetedSpending > 0 ? `${formatCurrency(t.unbudgetedSpending)} gastados sin presupuesto. ` : ''}
            {t.unassigned > 0 ? `${formatCurrency(t.unassigned)} de los ingresos esperados sin asignar.` : t.unassigned < 0 ? `Lo presupuestado supera los ingresos esperados en ${formatCurrency(-t.unassigned)}.` : ''}
          </AppText>
        )}

        <View>{cats.map(fila)}</View>
        {cats.length === 0 && (
          <AppText variant="body" color={colors.textSecondary} style={{ textAlign: 'center', marginTop: 24 }}>
            Este periodo no tiene presupuesto ni gastos todavía.
          </AppText>
        )}

        <View style={styles.acciones}>
          <TouchableOpacity style={[styles.accion, { borderColor: colors.border }]} onPress={abrirNueva}
            accessibilityRole="button" accessibilityLabel="Presupuestar otra categoría">
            <AppText variant="caption" style={{ fontWeight: '700' }}>Presupuestar otra</AppText>
          </TouchableOpacity>
          <TouchableOpacity style={[styles.accion, { borderColor: colors.border }]} onPress={copiar}
            accessibilityRole="button" accessibilityLabel="Copiar el presupuesto del periodo anterior">
            <AppText variant="caption" style={{ fontWeight: '700' }}>Copiar del periodo anterior</AppText>
          </TouchableOpacity>
          <TouchableOpacity style={[styles.accion, { borderColor: colors.border }]} onPress={() => navigation.navigate('Envelopes')}
            accessibilityRole="button" accessibilityLabel="Ver los sobres con lo que sobró del mes anterior">
            <AppText variant="caption" style={{ fontWeight: '700' }}>Sobres y arrastre</AppText>
          </TouchableOpacity>
        </View>
      </ScrollView>

      <Modal visible={!!eligiendoNueva} transparent animationType="slide" onRequestClose={() => setEligiendoNueva(null)}>
        <TouchableOpacity style={styles.fondo} activeOpacity={1} onPress={() => setEligiendoNueva(null)} accessibilityRole="button" accessibilityLabel="Cerrar">
          <View style={[styles.hoja, { backgroundColor: colors.surface }]}>
            <AppText variant="subtitle">¿Qué categoría?</AppText>
            <ScrollView style={{ maxHeight: 360 }}>
              {(eligiendoNueva || []).map((c) => (
                <TouchableOpacity key={c.id} style={[styles.fila, { borderBottomColor: colors.border }]}
                  onPress={() => { setEligiendoNueva(null); setNueva(c); }} accessibilityRole="button" accessibilityLabel={`Presupuestar ${c.name}`}>
                  <Icono nombre={c.icon} color={colors.text} />
                  <AppText variant="body">{c.name}</AppText>
                </TouchableOpacity>
              ))}
              {eligiendoNueva?.length === 0 && <AppText variant="body" color={colors.textSecondary}>Todas las categorías ya tienen presupuesto.</AppText>}
            </ScrollView>
          </View>
        </TouchableOpacity>
      </Modal>
      <MontoModal visible={!!nueva} titulo={`Presupuesto de ${nueva?.name || ''}`} textoListo="Guardar"
        onListo={presupuestar} onCerrar={() => setNueva(null)} />
    </View>
  );
};

const styles = StyleSheet.create({
  pantalla: { flex: 1 },
  contenido: { padding: 16, gap: 12, paddingBottom: 32 },
  icono: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  pestanas: { flexDirection: 'row', borderRadius: 12, padding: 4 },
  pestana: { flex: 1, alignItems: 'center', paddingVertical: 10, borderRadius: 9, minHeight: 44, justifyContent: 'center' },
  totales: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  total: { flex: 1, gap: 2 },
  fila: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 12, borderBottomWidth: 1 },
  circulo: { width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
  entre: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  barra: { height: 8, borderRadius: 4, overflow: 'hidden' },
  acciones: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 8 },
  fondo: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' },
  hoja: { padding: 20, gap: 8, borderTopLeftRadius: 24, borderTopRightRadius: 24 },
  accion: { flexGrow: 1, flexBasis: '30%', borderWidth: 1, borderRadius: 12, padding: 12, alignItems: 'center', minHeight: 44, justifyContent: 'center' },
});

export default PresupuestoScreen;
