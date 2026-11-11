import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, RefreshControl, Modal, PanResponder, useWindowDimensions, Platform } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import AppText from '../../components/atoms/Text';
import Icono from '../../components/atoms/Icono';
import CirculoCategoria from '../../components/atoms/CirculoCategoria';
import Spinner from '../../components/atoms/Spinner';
import Encabezado from '../../components/molecules/Encabezado';
import EstadoError from '../../components/molecules/EstadoError';
import DonaDeCategorias, { CategoriaEnDona, PALETA } from '../../components/molecules/DonaDeCategorias';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import { useAlcance } from '../../contexts/AlcanceContext';
import { useCarga, exigir } from '../../hooks/useCarga';
import { estadoService, EstadoDelPeriodo } from '../../services/estadoService';
import { dashboardService } from '../../services/dashboardService';
import { deudaService } from '../../services/deudaService';
import { categoryService } from '../../services/categoryService';
import { Category } from '../../types';
import { deudasDelAlcance } from '../../utils/deudas';
import { formatCurrency, formatCompacto } from '../../utils/format';
import { textoLegibleSobre } from '../../theme/contraste';

/*
 * INICIO, como Monefy:
 *   · la dona: en que se fue la plata, con TODAS las categorias alrededor
 *     (tocar una: anotar, ver sus movimientos o su presupuesto) y adentro lo
 *     que entro y lo que salio;
 *   · cuatro recuadros: lo que entro, lo que falta pagar, lo ahorrado y lo
 *     que se debe (y el aviso de lo que me toca confirmar, si hay);
 *   · abajo, lo libre para gastar: tocarlo abre la lista de movimientos;
 *   · dos botones: anotar un gasto o un ingreso.
 * Y con gestos, en cualquier parte: deslizar a la derecha anota un gasto, a
 * la izquierda un ingreso, y desde abajo hacia arriba abre los movimientos. El periodo se
 * cambia con las flechas de arriba.
 * Todo sale de /v1/estado/periodo, la misma fuente que Presupuesto: las
 * cifras no pueden decir cosas distintas. Las deudas salen de /v1/debts.
 */
const InicioScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const { alcance, corte, periodo, nombreDe } = useAlcance();
  const [otras, setOtras] = useState<Category[]>([]);
  const [elegida, setElegida] = useState<CategoriaEnDona | null>(null);

  /* Los gestos. Hacia arriba solo cuenta cuando la lista ya no puede subir
     mas (esta abajo del todo o no tiene scroll): si no, se roba el scroll. */
  const abajoDelTodo = useRef(true);
  const altoVisible = useRef(0);
  /* Desde la zona de abajo (lo libre y los botones) el gesto hacia arriba
     siempre vale: ahi no hay nada que desplazar. */
  const { height: altoVentana } = useWindowDimensions();
  const altoPantalla = useRef(altoVentana);
  altoPantalla.current = altoVentana;
  const acciones = useRef({ gasto: () => {}, ingreso: () => {}, arriba: () => {} });
  acciones.current = {
    gasto: () => navigation.navigate('Anotar', { tipo: 'EXPENSE' }),
    ingreso: () => navigation.navigate('Anotar', { tipo: 'INCOME' }),
    arriba: () => navigation.navigate('Movimientos'),
  };
  const gestos = useMemo(() => PanResponder.create({
    onMoveShouldSetPanResponderCapture: (_e, g) =>
      (Math.abs(g.dx) > 30 && Math.abs(g.dx) > Math.abs(g.dy) * 1.8)
      || (g.dy < -40 && Math.abs(g.dy) > Math.abs(g.dx) * 2 && (abajoDelTodo.current || g.moveY - g.dy > altoPantalla.current - 200)),
    onPanResponderTerminationRequest: () => false,
    onPanResponderRelease: (_e, g) => {
      if (Math.abs(g.dx) > Math.abs(g.dy)) {
        if (g.dx > 80) acciones.current.gasto();
        else if (g.dx < -80) acciones.current.ingreso();
      } else if (g.dy < -90) acciones.current.arriba();
    },
  }), []);
  /* En el navegador del celular, deslizar a los lados es "volver atras" y
     arrastrar hacia arriba es mover la pagina: se le dice al navegador que
     esos gestos son de la app (en la pantalla, los lados; abajo, todo). */
  const raiz = useRef<any>(null);
  const zonaAbajo = useRef<any>(null);
  const zonaSaldo = useRef<any>(null);
  useEffect(() => {
    if (Platform.OS !== 'web') return;
    if (raiz.current?.style) raiz.current.style.touchAction = 'pan-y';
    /* Sin esto Chrome toma el deslizar a la derecha como "pagina anterior". */
    [document.documentElement, document.body].forEach((n: any) => { if (n?.style) n.style.overscrollBehaviorX = 'none'; });
    [zonaAbajo.current, zonaSaldo.current].forEach((n) => { if (n?.style) n.style.touchAction = 'none'; });
  });
  const medir = (e: any) => {
    const { layoutMeasurement: l, contentOffset: o, contentSize: c } = e.nativeEvent;
    abajoDelTodo.current = l.height + o.y >= c.height - 8;
  };
  const [estado, setEstado] = useState<EstadoDelPeriodo | null>(null);
  const [misPendientes, setMisPendientes] = useState({ n: 0, monto: 0 });
  /* Lo por pagar del recuadro sale de la MISMA lista que se abre al tocarlo:
     antes el recuadro decia "20 pagos" y la lista ensenaba otros 9. */
  const [porPagar, setPorPagar] = useState({ n: 0, monto: 0 });
  /* null: no se pudieron traer. Un fallo aqui no tumba el Inicio. */
  const [deudas, setDeudas] = useState<{ total: number; mes: number } | null>(null);

  const traer = useCallback(async () => {
    if (!user) return;
    const [e, p] = await Promise.all([
      estadoService.periodo(corte, periodo.inicio, alcance),
      dashboardService.getPending(user.userId, corte, periodo.inicio),
    ]);
    setEstado(exigir(e) || null);
    const pendientes = exigir(p) || [];
    const pagos = pendientes.filter((m) => m.categoryType !== 'INCOME');
    /* El aviso sigue al selector: en Hogar, lo mio (soy quien confirma); en
       una persona, lo de ESA persona. Antes, viendo a Samy, el aviso decia
       los pagos de Andy. */
    const deQuien = alcance.tipo === 'HOGAR' ? String(user.userId) : String(alcance.userId);
    const mios = pagos.filter((m) => String(m.userId) === deQuien);
    setMisPendientes({ n: mios.length, monto: mios.reduce((t, m) => t + (m.amount || 0), 0) });
    /* Hogar: lo comun de los dos. Una persona: lo que le toca a ella. */
    const delAlcance = alcance.tipo === 'HOGAR'
      ? pagos.filter((m) => m.shared !== false)
      : pagos.filter((m) => String(m.userId) === String(alcance.userId));
    setPorPagar({ n: delAlcance.length, monto: delAlcance.reduce((t, m) => t + (m.amount || 0), 0) });
    try {
      const c = await categoryService.getAll({ userId: user.userId, page: 0, size: 200 });
      setOtras(((c.correct && c.object?.list) || []).filter((x: Category) => x.type === 'EXPENSE' && x.active !== false
        && (alcance.tipo === 'HOGAR' ? !!x.householdId : true)));
    } catch { setOtras([]); }
    try {
      const d = await deudaService.todas();
      /* Sigue el selector, igual que la pantalla de Deudas (QA 7.1). */
      const r = d?.correct && d.object ? deudasDelAlcance(d.object, alcance as any) : null;
      setDeudas(r ? { total: r.totalOwed, mes: r.monthlyInstallments } : null);
    } catch (err) {
      console.warn('Inicio: no se pudieron traer las deudas', err);
      setDeudas(null);
    }
  }, [user, corte, periodo.inicio, alcance]);

  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traer);
  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  if (cargando && !estado) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}><Encabezado /><Spinner fullScreen /></View>;
  if (error) {
    return (
      <View style={[styles.pantalla, { backgroundColor: colors.background }]}>
        <Encabezado />
        <EstadoError mensaje={error} onReintentar={cargar} />
      </View>
    );
  }
  const t = estado?.totals;
  const presupuestoDeGasto = (t?.budgeted || 0) - (t?.savingsGoal || 0);
  /* Lo pagado con el ahorro (la reparacion del carro) es gasto del carro y
     sale en su porcion, pero no salio del sueldo: no va en el rojo del centro. */
  const gastosDelMes = Math.max(0, (t?.spent || 0) - (t?.fromSavings || 0));
  /* "Por pagar" es la MISMA cifra que "Libre para gastar" descuenta (toPay):
     antes salia de la lista de pendientes y no cuadraba con lo libre, porque
     toPay tambien cuenta lo programado que aun no se ha generado. */
  const aPagar = t?.toPay ?? porPagar.monto;
  const previstos = Math.max(0, aPagar - porPagar.monto);
  const quienAviso = alcance.tipo === 'HOGAR' || String(alcance.userId) === String(user?.userId)
    ? null : (nombreDe(alcance.userId) || 'Esta persona');
  const tintaGasto = textoLegibleSobre(colors.expense);
  const tintaIngreso = textoLegibleSobre(colors.income);

  /* La porcion es lo GASTADO de cada categoria. Alrededor van todas las de
     gasto y las de ahorro (para depositar con un toque), aunque no tengan
     movimiento: asi se anota en cualquiera sin buscarla. */
  const enDona: CategoriaEnDona[] = (estado?.categories || [])
    .filter((c) => c.kind === 'EXPENSE' || c.kind === 'SAVING')
    .map((c, i) => ({
      id: c.categoryId, nombre: c.categoryName, icono: c.categoryIcon,
      color: c.categoryColor || PALETA[i % PALETA.length], valor: c.kind === 'EXPENSE' ? (c.actual || 0) : 0,
    }));
  otras.filter((o) => !enDona.some((d) => String(d.id) === String(o.id)))
    .forEach((o, i) => enDona.push({ id: Number(o.id), nombre: o.name, icono: o.icon,
      color: o.color || PALETA[(enDona.length + i) % PALETA.length], valor: 0 }));

  const recuadro = (o: {
    titulo: string; icono?: string; valor: string; colorValor?: string; detalle: string;
    destino: () => void; etiqueta: string;
  }) => (
    <TouchableOpacity style={[styles.kpi, { backgroundColor: colors.surfaceLight }]} onPress={o.destino}
      accessibilityRole="button" accessibilityLabel={o.etiqueta}>
      <View style={styles.tituloKpi}>
        {o.icono && <Icono nombre={o.icono} color={colors.textSecondary} tamano={16} />}
        <AppText variant="caption" color={colors.textSecondary} numberOfLines={1}>{o.titulo}</AppText>
      </View>
      <AppText variant="body" style={{ fontWeight: '800', color: o.colorValor || colors.text }} numberOfLines={1} adjustsFontSizeToFit>
        {o.valor}
      </AppText>
      <AppText variant="caption" color={colors.textSecondary} numberOfLines={1}>{o.detalle}</AppText>
    </TouchableOpacity>
  );

  const ahorros = t?.savingsBalance || 0;

  return (
    <View style={[styles.pantalla, { backgroundColor: colors.background }]} {...gestos.panHandlers} testID="inicio-gestos" ref={raiz}>
      <Encabezado />
      <ScrollView contentContainerStyle={styles.contenido} onScroll={medir} scrollEventThrottle={64}
        onContentSizeChange={(_w, h) => { abajoDelTodo.current = h <= altoVisible.current + 8; }}
        onLayout={(e) => { altoVisible.current = e.nativeEvent.layout.height; }}
        refreshControl={<RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />}>
        <DonaDeCategorias
          categorias={enDona}
          ingresos={t?.incomeReceived || 0}
          gastos={gastosDelMes}
          conAhorros={t?.fromSavings || 0}
          onCategoria={setElegida}
          onCentro={() => navigation.navigate('Presupuesto')}
          etiquetaCentro={`Ingresos ${formatCurrency(t?.incomeReceived || 0)}, gastos ${formatCurrency(gastosDelMes)}`
            + ` de ${formatCurrency(presupuestoDeGasto)} presupuestado`
            + `${(t?.fromSavings || 0) > 0 ? ` y ${formatCurrency(t?.fromSavings || 0)} pagados con ahorros` : ''}. Ver el presupuesto`}
          onMas={() => navigation.navigate('Anotar', { tipo: 'EXPENSE' })}
        />

        <View style={styles.kpis}>
          {recuadro({
            titulo: 'Ingresos', valor: formatCompacto(t?.incomeReceived || 0),
            detalle: `de ${formatCompacto(t?.incomeExpected || 0)}`,
            destino: () => navigation.navigate('Ingresos'),
            etiqueta: `Ingresos recibidos ${formatCurrency(t?.incomeReceived || 0)} de ${formatCurrency(t?.incomeExpected || 0)}. Ver el detalle`,
          })}
          {recuadro({
            titulo: 'Por pagar', valor: formatCompacto(aPagar),
            detalle: `${porPagar.n} ${porPagar.n === 1 ? 'pago' : 'pagos'}${previstos > 0 ? ' + previstos' : ''}`,
            destino: () => navigation.navigate('PorConfirmar', { quien: alcance.tipo === 'HOGAR' ? 'DOS' : alcance.userId }),
            etiqueta: `Por pagar ${formatCurrency(aPagar)}: ${porPagar.n} ${porPagar.n === 1 ? 'pago' : 'pagos'} por ${formatCurrency(porPagar.monto)}`
              + `${previstos > 0 ? ` y ${formatCurrency(previstos)} programados que aún no se generan` : ''}. Ver cuáles son`,
          })}
        </View>
        <View style={styles.kpis}>
          {recuadro({
            titulo: 'Ahorros', icono: 'piggy-bank', valor: formatCompacto(ahorros), colorValor: colors.income,
            detalle: 'en todos los ahorros',
            destino: () => navigation.navigate('Ahorros'),
            etiqueta: `Ahorros: hay ${formatCurrency(ahorros)}. Ver los ahorros`,
          })}
          {recuadro({
            titulo: 'Deudas', icono: 'credit-card', valor: deudas ? formatCompacto(deudas.total) : '—',
            detalle: deudas ? `este mes ${formatCompacto(deudas.mes)}` : 'No se pudieron cargar',
            destino: () => navigation.navigate('Deudas'),
            etiqueta: deudas
              ? `Deudas: se deben ${formatCurrency(deudas.total)}, este mes ${formatCurrency(deudas.mes)}. Ver las deudas`
              : 'Deudas: no se pudieron cargar. Ver las deudas',
          })}
        </View>

        {misPendientes.n > 0 && (
          <TouchableOpacity style={[styles.aviso, { backgroundColor: colors.surfaceLight }]}
            onPress={() => navigation.navigate('PorConfirmar', { quien: quienAviso && alcance.tipo !== 'HOGAR' ? alcance.userId : 'MIOS' })} accessibilityRole="button"
            accessibilityLabel={`${quienAviso ? `${quienAviso} tiene` : 'Tienes'} ${misPendientes.n} ${misPendientes.n === 1 ? 'pago' : 'pagos'} por confirmar. Ver cuáles son`}>
            <View style={[styles.punto, { backgroundColor: colors.primary }]} />
            <AppText variant="body" style={{ flex: 1, fontWeight: '700' }}>
              {quienAviso ? `${quienAviso} tiene` : 'Tienes'} {misPendientes.n} {misPendientes.n === 1 ? 'pago' : 'pagos'} por confirmar
            </AppText>
            <AppText variant="caption" color={colors.textSecondary}>{formatCurrency(misPendientes.monto)}</AppText>
            <Icono nombre="right" color={colors.textSecondary} tamano={18} />
          </TouchableOpacity>
        )}
      </ScrollView>

      <TouchableOpacity ref={zonaSaldo} style={[styles.saldo, { backgroundColor: colors.surfaceLight }]} onPress={() => navigation.navigate('Movimientos')}
        accessibilityRole="button"
        accessibilityLabel={`Libre para gastar ${formatCurrency(t?.freeToSpend || 0)}. Ver todos los movimientos`}>
        <View style={{ flex: 1 }}>
          <AppText variant="caption" color={colors.textSecondary}>Libre para gastar · ver movimientos</AppText>
          <AppText variant="subtitle" style={{ fontWeight: '800', color: (t?.freeToSpend || 0) >= 0 ? colors.income : colors.expense }}>
            {formatCurrency(t?.freeToSpend || 0)}
          </AppText>
        </View>
        <Icono nombre="list" color={colors.textSecondary} />
        <Icono nombre="right" color={colors.textSecondary} tamano={18} />
      </TouchableOpacity>

      <Modal visible={!!elegida} transparent animationType="slide" onRequestClose={() => setElegida(null)}>
        <TouchableOpacity style={styles.fondo} activeOpacity={1} onPress={() => setElegida(null)}
          accessibilityRole="button" accessibilityLabel="Cerrar">
          {elegida && (
            <View style={[styles.hoja, { backgroundColor: colors.surface }]}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 4 }}>
                <CirculoCategoria icono={elegida.icono} color={elegida.color} tamano={40} />
                <View style={{ flex: 1 }}>
                  <AppText variant="subtitle">{elegida.nombre}</AppText>
                  <AppText variant="caption" color={colors.textSecondary}>
                    {elegida.valor > 0 ? `Van ${formatCurrency(elegida.valor)} este periodo` : 'Sin gastos este periodo'}
                  </AppText>
                </View>
              </View>
              <TouchableOpacity style={[styles.opcion, { backgroundColor: colors.expense }]}
                onPress={() => { const c = elegida; setElegida(null); navigation.navigate('Anotar', { tipo: 'EXPENSE', categoriaId: c.id }); }}
                accessibilityRole="button" accessibilityLabel={`Anotar en ${elegida.nombre}`}>
                <Icono nombre="minus" color={tintaGasto} />
                <AppText variant="body" style={{ color: tintaGasto, fontWeight: '800' }}>Anotar aquí</AppText>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.opcion, { backgroundColor: colors.surfaceLight }]}
                onPress={() => { const c = elegida; setElegida(null); navigation.navigate('Movimientos', { categoriaId: c.id }); }}
                accessibilityRole="button" accessibilityLabel={`Ver los movimientos de ${elegida.nombre}`}>
                <Icono nombre="list" color={colors.text} />
                <AppText variant="body" style={{ fontWeight: '700' }}>Ver sus movimientos</AppText>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.opcion, { backgroundColor: colors.surfaceLight }]}
                onPress={() => { const c = elegida; setElegida(null); navigation.navigate('Categoria', { categoryId: c.id }); }}
                accessibilityRole="button" accessibilityLabel={`Ver el presupuesto de ${elegida.nombre}`}>
                <Icono nombre="chart" color={colors.text} />
                <AppText variant="body" style={{ fontWeight: '700' }}>Ver su presupuesto</AppText>
              </TouchableOpacity>
            </View>
          )}
        </TouchableOpacity>
      </Modal>

      <View style={styles.botones} ref={zonaAbajo}>
        <TouchableOpacity style={[styles.grande, { backgroundColor: colors.expense }]}
          onPress={() => navigation.navigate('Anotar', { tipo: 'EXPENSE' })} accessibilityRole="button" accessibilityLabel="Anotar un gasto">
          <Icono nombre="minus" color={tintaGasto} />
          <AppText variant="subtitle" style={[styles.textoGrande, { color: tintaGasto }]}>Gasto</AppText>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.grande, { backgroundColor: colors.income }]}
          onPress={() => navigation.navigate('Anotar', { tipo: 'INCOME' })} accessibilityRole="button" accessibilityLabel="Anotar un ingreso">
          <Icono nombre="plus" color={tintaIngreso} />
          <AppText variant="subtitle" style={[styles.textoGrande, { color: tintaIngreso }]}>Ingreso</AppText>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  pantalla: { flex: 1 },
  contenido: { padding: 16, gap: 12, paddingBottom: 24 },
  kpis: { flexDirection: 'row', gap: 8 },
  kpi: { flex: 1, minWidth: 0, borderRadius: 14, padding: 10, gap: 2, minHeight: 64 },
  tituloKpi: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  aviso: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 14, borderRadius: 14, minHeight: 52 },
  punto: { width: 10, height: 10, borderRadius: 5 },
  botones: { flexDirection: 'row', gap: 12, padding: 16, paddingTop: 8, width: '100%', maxWidth: 560, alignSelf: 'center' },
  saldo: { flexDirection: 'row', alignItems: 'center', gap: 8, marginHorizontal: 16, marginTop: 4, paddingHorizontal: 14,
    paddingVertical: 8, borderRadius: 14, minHeight: 52, width: '100%', maxWidth: 528, alignSelf: 'center' },
  fondo: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' },
  hoja: { padding: 20, gap: 10, borderTopLeftRadius: 24, borderTopRightRadius: 24, width: '100%', maxWidth: 560, alignSelf: 'center' },
  opcion: { flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 54, borderRadius: 14, paddingHorizontal: 16 },
  grande: { flex: 1, height: 60, borderRadius: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  textoGrande: { fontWeight: '800' },
});

export default InicioScreen;
