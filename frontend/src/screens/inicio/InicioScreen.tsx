import React, { useCallback, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, RefreshControl } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import AppText from '../../components/atoms/Text';
import Icono from '../../components/atoms/Icono';
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
import { formatCurrency, formatCompacto } from '../../utils/format';

/*
 * INICIO. Tres zonas, una pregunta cada una:
 *   · la dona: cuanto queda libre y en que se fue la plata;
 *   · cuatro recuadros: lo que entro, lo que falta pagar, lo ahorrado y lo
 *     que se debe (y el aviso de lo que me toca confirmar, si hay);
 *   · dos botones: anotar un gasto o un ingreso.
 * Todo sale de /v1/estado/periodo, la misma fuente que Presupuesto: las
 * cifras no pueden decir cosas distintas. Las deudas salen de /v1/debts.
 */
const InicioScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const { alcance, corte, periodo, anterior, siguiente, puedeSeguir } = useAlcance();
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
    const mios = pagos.filter((m) => String(m.userId) === String(user.userId));
    setMisPendientes({ n: mios.length, monto: mios.reduce((t, m) => t + (m.amount || 0), 0) });
    /* Hogar: lo comun de los dos. Una persona: lo que le toca a ella. */
    const delAlcance = alcance.tipo === 'HOGAR'
      ? pagos.filter((m) => m.shared !== false)
      : pagos.filter((m) => String(m.userId) === String(alcance.userId));
    setPorPagar({ n: delAlcance.length, monto: delAlcance.reduce((t, m) => t + (m.amount || 0), 0) });
    try {
      const d = await deudaService.todas();
      setDeudas(d?.correct && d.object ? { total: d.object.totalOwed || 0, mes: d.object.monthlyInstallments || 0 } : null);
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

  /* En la dona solo va lo GASTADO: los ahorros no se dibujan, y la dona misma
     deja fuera lo que no tiene gasto y junta lo chico en "Otros". */
  const enDona: CategoriaEnDona[] = (estado?.categories || [])
    .filter((c) => c.kind === 'EXPENSE')
    .map((c, i) => ({
      id: c.categoryId, nombre: c.categoryName, icono: c.categoryIcon,
      color: c.categoryColor || PALETA[i % PALETA.length], valor: c.actual || 0, presupuesto: c.allocated || 0,
    }));

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
      <AppText variant="caption" color={colors.textMuted} numberOfLines={1}>{o.detalle}</AppText>
    </TouchableOpacity>
  );

  const ahorros = t?.savingsBalance || 0;

  return (
    <View style={[styles.pantalla, { backgroundColor: colors.background }]}>
      <Encabezado />
      <ScrollView contentContainerStyle={styles.contenido}
        refreshControl={<RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />}>
        <DonaDeCategorias
          categorias={enDona}
          libre={t?.freeToSpend || 0}
          gastado={t?.spent || 0}
          presupuestado={presupuestoDeGasto}
          onCentro={() => navigation.navigate('Presupuesto')}
          onAnotar={(c) => navigation.navigate('Anotar', { tipo: 'EXPENSE', categoriaId: c.id })}
          onVerMovimientos={(c) => navigation.navigate('Movimientos', { categoriaId: c.id })}
          onVerCategoria={(c) => navigation.navigate('Categoria', { categoryId: c.id })}
          onAnterior={anterior}
          onSiguiente={puedeSeguir ? siguiente : undefined}
          claveAnimacion={`${periodo.inicio}|${alcance.tipo === 'HOGAR' ? 'HOGAR' : alcance.userId}`}
        />

        <View style={styles.kpis}>
          {recuadro({
            titulo: 'Ingresos', valor: formatCompacto(t?.incomeReceived || 0),
            detalle: `de ${formatCompacto(t?.incomeExpected || 0)}`,
            destino: () => navigation.navigate('Ingresos'),
            etiqueta: `Ingresos recibidos ${formatCurrency(t?.incomeReceived || 0)} de ${formatCurrency(t?.incomeExpected || 0)}. Ver el detalle`,
          })}
          {recuadro({
            titulo: 'Por pagar', valor: formatCompacto(porPagar.monto),
            detalle: `${porPagar.n} ${porPagar.n === 1 ? 'pago' : 'pagos'}`,
            destino: () => navigation.navigate('PorConfirmar', { quien: alcance.tipo === 'HOGAR' ? 'DOS' : alcance.userId }),
            etiqueta: `Por pagar ${formatCurrency(porPagar.monto)} en ${porPagar.n} pagos. Ver cuáles son`,
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
            onPress={() => navigation.navigate('PorConfirmar', { quien: 'MIOS' })} accessibilityRole="button"
            accessibilityLabel={`Tienes ${misPendientes.n} pagos por confirmar. Confirmarlos`}>
            <View style={[styles.punto, { backgroundColor: colors.primary }]} />
            <AppText variant="body" style={{ flex: 1, fontWeight: '700' }}>
              Tienes {misPendientes.n} {misPendientes.n === 1 ? 'pago' : 'pagos'} por confirmar
            </AppText>
            <AppText variant="caption" color={colors.textSecondary}>{formatCurrency(misPendientes.monto)}</AppText>
            <Icono nombre="right" color={colors.textSecondary} tamano={18} />
          </TouchableOpacity>
        )}
      </ScrollView>

      <View style={styles.botones}>
        <TouchableOpacity style={[styles.grande, { backgroundColor: colors.expense }]}
          onPress={() => navigation.navigate('Anotar', { tipo: 'EXPENSE' })} accessibilityRole="button" accessibilityLabel="Anotar un gasto">
          <Icono nombre="minus" color="#FFFFFF" />
          <AppText variant="subtitle" style={styles.textoGrande}>Gasto</AppText>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.grande, { backgroundColor: colors.income }]}
          onPress={() => navigation.navigate('Anotar', { tipo: 'INCOME' })} accessibilityRole="button" accessibilityLabel="Anotar un ingreso">
          <Icono nombre="plus" color="#FFFFFF" />
          <AppText variant="subtitle" style={styles.textoGrande}>Ingreso</AppText>
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
  botones: { flexDirection: 'row', gap: 12, padding: 16, paddingTop: 8 },
  grande: { flex: 1, height: 60, borderRadius: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  textoGrande: { color: '#FFFFFF', fontWeight: '800' },
});

export default InicioScreen;
