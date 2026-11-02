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
import { categoryService } from '../../services/categoryService';
import { formatCurrency, formatCompacto } from '../../utils/format';
import { Category } from '../../types';

/*
 * INICIO. Una sola pregunta por zona, como en Monefy:
 *   · la dona: en que se fue la plata, contra lo presupuestado;
 *   · tres recuadros: cuanto entro, cuanto falta por pagar, cuanto hay;
 *   · un aviso: lo que me toca confirmar a mi;
 *   · dos botones: anotar un gasto o un ingreso.
 * Todo sale de /v1/estado/periodo, la misma fuente que Presupuesto y
 * Responsabilidades: las cifras no pueden decir cosas distintas.
 */
const InicioScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const { alcance, corte, periodo } = useAlcance();
  const [estado, setEstado] = useState<EstadoDelPeriodo | null>(null);
  const [misPendientes, setMisPendientes] = useState({ n: 0, monto: 0 });
  /* Lo por pagar del recuadro sale de la MISMA lista que se abre al tocarlo:
     antes el recuadro decia "20 pagos" y la lista ensenaba otros 9. */
  const [porPagar, setPorPagar] = useState({ n: 0, monto: 0 });
  const [otras, setOtras] = useState<Category[]>([]);

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
      const c = await categoryService.getAll({ userId: user.userId, page: 0, size: 200 });
      setOtras(((c.correct && c.object?.list) || []).filter((x: Category) => x.type === 'EXPENSE' && x.active !== false));
    } catch { setOtras([]); }
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
  const gastos = (estado?.categories || []).filter((c) => c.kind === 'EXPENSE');
  const presupuestoDeGasto = (t?.budgeted || 0) - (t?.savingsGoal || 0);

  /* En la dona van primero las categorias con plan o con gasto, y despues las
     demas, hasta llenar el anillo. */
  const enDona: CategoriaEnDona[] = gastos.map((c, i) => ({
    id: c.categoryId, nombre: c.categoryName, icono: c.categoryIcon,
    color: c.categoryColor || PALETA[i % PALETA.length], valor: c.actual,
  }));
  (estado?.categories || []).filter((c) => c.kind === 'SAVING').forEach((c, i) => enDona.push({
    id: c.categoryId, nombre: c.categoryName, icono: c.categoryIcon,
    color: c.categoryColor || PALETA[(gastos.length + i) % PALETA.length], valor: 0,
  }));
  /* TODAS las categorias de gasto alrededor, como en Monefy: antes solo salian
     las que tenian plan o gasto y se completaba hasta 8, asi que Regalo, Obra
     social y las demas no aparecian para anotar en ellas. */
  otras.filter((o) => !enDona.some((d) => String(d.id) === String(o.id)))
    .forEach((o, i) => enDona.push({ id: Number(o.id), nombre: o.name, icono: o.icon,
      color: o.color || PALETA[(enDona.length + i) % PALETA.length], valor: 0 }));

  const recuadro = (titulo: string, valor: number, detalle: string, destino: () => void, etiqueta: string) => (
    <TouchableOpacity style={[styles.kpi, { backgroundColor: colors.surfaceLight }]} onPress={destino}
      accessibilityRole="button" accessibilityLabel={etiqueta}>
      <AppText variant="caption" color={colors.textSecondary}>{titulo}</AppText>
      <AppText variant="body" style={{ fontWeight: '800' }} numberOfLines={1}>{formatCompacto(valor)}</AppText>
      <AppText variant="caption" color={colors.textMuted} numberOfLines={1}>{detalle}</AppText>
    </TouchableOpacity>
  );

  return (
    <View style={[styles.pantalla, { backgroundColor: colors.background }]}>
      <Encabezado />
      <ScrollView contentContainerStyle={styles.contenido}
        refreshControl={<RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />}>
        <DonaDeCategorias
          categorias={enDona}
          onCategoria={(c) => navigation.navigate('Anotar', { tipo: 'EXPENSE', categoriaId: c.id })}
          onCentro={() => navigation.navigate('Presupuesto')}
          onMas={() => navigation.navigate('Anotar', { tipo: 'EXPENSE' })}
          etiquetaCentro={`Gastado ${formatCurrency(t?.spent || 0)} de ${formatCurrency(presupuestoDeGasto)}. Ver el presupuesto`}
          centro={
            <>
              <AppText variant="caption" color={colors.textSecondary}>Gastado</AppText>
              <AppText variant="subtitle" style={{ fontWeight: '800', color: colors.expense }} numberOfLines={1} adjustsFontSizeToFit>
                {formatCurrency(t?.spent || 0)}
              </AppText>
              <AppText variant="caption" color={colors.textSecondary} numberOfLines={1} adjustsFontSizeToFit>
                de {formatCurrency(presupuestoDeGasto)}
              </AppText>
            </>
          }
        />

        <View style={styles.kpis}>
          {recuadro('Ingresos', t?.incomeReceived || 0, `de ${formatCompacto(t?.incomeExpected || 0)}`,
            () => navigation.navigate('Ingresos'),
            `Ingresos recibidos ${formatCurrency(t?.incomeReceived || 0)} de ${formatCurrency(t?.incomeExpected || 0)}. Ver el detalle`)}
          {recuadro('Por pagar', porPagar.monto, `${porPagar.n} ${porPagar.n === 1 ? 'pago' : 'pagos'}`,
            () => navigation.navigate('PorConfirmar', { quien: alcance.tipo === 'HOGAR' ? 'DOS' : alcance.userId }),
            `Por pagar ${formatCurrency(porPagar.monto)} en ${porPagar.n} pagos. Ver cuáles son`)}
          {recuadro('Fondos', estado?.funds.total || 0, 'en cuentas',
            () => navigation.navigate('AccountsList'), `Fondos ${formatCurrency(estado?.funds.total || 0)}. Ver las cuentas`)}
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

        <TouchableOpacity style={[styles.libre, { borderColor: colors.border }]} onPress={() => navigation.navigate('Responsabilidades')}
          accessibilityRole="button" accessibilityLabel={`Libre para gastar ${formatCurrency(t?.freeToSpend || 0)}. Ver de dónde sale`}>
          <AppText variant="body" color={colors.textSecondary}>Libre para gastar</AppText>
          <AppText variant="body" style={{ fontWeight: '800', color: (t?.freeToSpend || 0) >= 0 ? colors.income : colors.expense }}>
            {formatCurrency(t?.freeToSpend || 0)}
          </AppText>
        </TouchableOpacity>
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
  kpi: { flex: 1, minWidth: 0, borderRadius: 14, padding: 10, gap: 2 },
  aviso: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 14, borderRadius: 14, minHeight: 52 },
  punto: { width: 10, height: 10, borderRadius: 5 },
  libre: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 14, borderRadius: 14, borderWidth: 1, minHeight: 52 },
  botones: { flexDirection: 'row', gap: 12, padding: 16, paddingTop: 8 },
  grande: { flex: 1, height: 60, borderRadius: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  textoGrande: { color: '#FFFFFF', fontWeight: '800' },
});

export default InicioScreen;
