import React, { useCallback, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, RefreshControl } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import AppText from '../../components/atoms/Text';
import Spinner from '../../components/atoms/Spinner';
import Icono from '../../components/atoms/Icono';
import CirculoCategoria from '../../components/atoms/CirculoCategoria';
import Encabezado from '../../components/molecules/Encabezado';
import EstadoError from '../../components/molecules/EstadoError';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useCarga, exigir, mensajeDeError } from '../../hooks/useCarga';
import { deudaService, Deudas, Tarjeta, DeudaEnCuotas } from '../../services/deudaService';
import { formatCurrency } from '../../utils/format';
import { tintaSobre } from '../../utils/tinta';
import { diaMes, mesAnio } from './fechas';
import Hoja from './Hoja';
import FormTarjeta, { DatosTarjeta } from './FormTarjeta';

type Accion =
  | { tipo: 'pagar'; tarjeta: Tarjeta }
  | { tipo: 'borrar'; tarjeta: Tarjeta }
  | { tipo: 'adelantar'; deuda: DeudaEnCuotas };

/*
 * DEUDAS: lo que se debe en tarjetas y en cuotas. Pagar la tarjeta NO es un
 * gasto nuevo (las compras ya contaron cuando se hicieron, o cuota a cuota):
 * solo las deja saldadas. Un credito es un programado con N cuotas, y se
 * puede adelantar pagando hoy la ultima.
 */
const DeudasScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const [deudas, setDeudas] = useState<Deudas | null>(null);
  const [abierta, setAbierta] = useState<number | null>(null);
  const [accion, setAccion] = useState<Accion | null>(null);
  const [agregando, setAgregando] = useState(false);
  const [ocupado, setOcupado] = useState(false);

  const traer = useCallback(async () => {
    setDeudas(exigir(await deudaService.todas()) || null);
  }, []);
  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traer);
  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  /* Corre una accion contra el servidor, avisa y recarga. Un fallo se dice,
     no se traga: la hoja se queda abierta para reintentar o cancelar. */
  const correr = async (hacer: () => Promise<{ correct: boolean; message?: string } | undefined>,
    exito: (r: any) => [string, string?], cerrar: () => void) => {
    if (ocupado) return;
    setOcupado(true);
    try {
      const r: any = await hacer();
      if (!r?.correct) {
        showToast('error', 'No se pudo', r?.message || 'Intenta de nuevo.');
        return;
      }
      const [titulo, detalle] = exito(r);
      showToast('success', titulo, detalle);
      cerrar();
      await refrescar();
    } catch (e) {
      showToast('error', 'No se pudo', mensajeDeError(e));
    } finally {
      setOcupado(false);
    }
  };

  const confirmar = () => {
    if (!accion) return;
    if (accion.tipo === 'pagar') {
      correr(() => deudaService.pagarTarjeta(accion.tarjeta.id),
        (r) => ['Tarjeta pagada', `Quedaron saldados ${formatCurrency(r.object?.paidTotal ?? accion.tarjeta.dueNow)}`],
        () => setAccion(null));
    } else if (accion.tipo === 'borrar') {
      correr(() => deudaService.borrarTarjeta(accion.tarjeta.id), () => ['Tarjeta borrada'], () => setAccion(null));
    } else {
      correr(() => deudaService.adelantarCuota(accion.deuda.scheduledMovementId),
        (r) => ['Cuota adelantada', r.object?.remainingInstallments != null
          ? `Faltan ${r.object.remainingInstallments} cuotas` : undefined],
        () => setAccion(null));
    }
  };

  const guardarTarjeta = (t: DatosTarjeta) =>
    correr(() => deudaService.guardarTarjeta(t), () => ['Tarjeta agregada', t.name], () => setAgregando(false));

  const cabeza = <Encabezado titulo="Deudas" conAlcance={false} conPeriodo={false} />;
  const marco = (contenido: React.ReactNode) => (
    <View style={[styles.pantalla, { backgroundColor: colors.background }]}>{cabeza}{contenido}</View>
  );
  if (cargando && !deudas) return marco(<Spinner fullScreen />);
  if (error) return marco(<EstadoError mensaje={error} onReintentar={cargar} />);
  const d = deudas!;
  const tarjetas = d.cards || [];
  const cuotas = d.loans || [];

  const tarjetaUI = (t: Tarjeta) => {
    const expandida = abierta === t.id;
    const color = t.color || colors.secondary;
    return (
      <View key={t.id} style={[styles.tarjeta, { backgroundColor: colors.card, borderColor: colors.border }]}>
        <TouchableOpacity style={styles.fila} onPress={() => setAbierta(expandida ? null : t.id)}
          accessibilityRole="button" accessibilityState={{ expanded: expandida }}
          accessibilityLabel={`Tarjeta ${t.name}${t.mine ? '' : ` de ${t.ownerName || 'otra persona'}`}: se debe ${formatCurrency(t.owed)}. ${expandida ? 'Ocultar' : 'Ver'} las compras`}>
          <View style={[styles.plastico, { backgroundColor: color }]}>
            <Icono nombre="credit-card" color={tintaSobre(color)} tamano={22} />
          </View>
          <View style={{ flex: 1, gap: 2 }}>
            <AppText variant="body" style={{ fontWeight: '700' }} numberOfLines={1}>
              {t.name}{!t.mine ? ` de ${t.ownerName || 'otra persona'}` : ''}
            </AppText>
            <AppText variant="subtitle" color={t.owed > 0 ? colors.expense : colors.textSecondary} style={{ fontWeight: '800' }}>
              {formatCurrency(t.owed)}
            </AppText>
            {t.owed > 0 && (
              <AppText variant="caption" color={colors.textSecondary}>
                {t.dueNow > 0 ? `Ya toca pagar ${formatCurrency(t.dueNow)}` : 'Nada por pagar aún'}
                {t.upcoming > 0 ? ` · Después ${formatCurrency(t.upcoming)}` : ''}
              </AppText>
            )}
            {!!t.paymentDay && <AppText variant="caption" color={colors.textMuted}>Se paga el {t.paymentDay} de cada mes</AppText>}
          </View>
          <Icono nombre={expandida ? 'down' : 'right'} color={colors.textSecondary} tamano={20} />
        </TouchableOpacity>

        {expandida && (
          <View style={[styles.detalle, { borderTopColor: colors.border }]}>
            {t.purchases.length === 0 && <AppText variant="caption" color={colors.textMuted}>No hay compras por pagar.</AppText>}
            {t.purchases.map((p) => (
              <View key={p.purchaseId} style={styles.compra}>
                <CirculoCategoria icono={p.categoryIcon || undefined} color={p.categoryColor || colors.secondary} tamano={28} />
                <View style={{ flex: 1 }}>
                  <AppText variant="body" numberOfLines={1}>
                    {p.description || p.categoryName || 'Compra'}
                    {p.installments > 1 ? ` · ${p.paidInstallments}/${p.installments}` : ''}
                  </AppText>
                  <AppText variant="caption" color={colors.textSecondary}>
                    {diaMes(p.date)} · faltan {formatCurrency(p.owed)}
                    {p.nextDate ? ` · próxima ${diaMes(p.nextDate)}` : ''}
                  </AppText>
                </View>
                <AppText variant="label" style={{ fontWeight: '700' }}>{formatCurrency(p.total)}</AppText>
              </View>
            ))}
          </View>
        )}

        {t.mine && (t.dueNow > 0 || t.owed === 0) && (
          <View style={styles.acciones}>
            {t.dueNow > 0 && (
              <TouchableOpacity style={[styles.boton, { backgroundColor: colors.primary }]} onPress={() => setAccion({ tipo: 'pagar', tarjeta: t })}
                accessibilityRole="button" accessibilityLabel={`Pagar tarjeta ${t.name}`}>
                <AppText variant="label" color={tintaSobre(colors.primary)} style={{ fontWeight: '700' }}>Pagar tarjeta</AppText>
              </TouchableOpacity>
            )}
            {t.owed === 0 && (
              <TouchableOpacity style={[styles.boton, { borderColor: colors.danger, borderWidth: 2 }]} onPress={() => setAccion({ tipo: 'borrar', tarjeta: t })}
                accessibilityRole="button" accessibilityLabel={`Borrar tarjeta ${t.name}`}>
                <AppText variant="label" color={colors.danger} style={{ fontWeight: '700' }}>Borrar</AppText>
              </TouchableOpacity>
            )}
          </View>
        )}
      </View>
    );
  };

  const cuotaUI = (l: DeudaEnCuotas) => {
    const pct = l.totalInstallments > 0 ? Math.round((l.paidInstallments / l.totalInstallments) * 100) : 0;
    const color = l.categoryColor || colors.secondary;
    return (
      <View key={l.scheduledMovementId} style={[styles.tarjeta, { backgroundColor: colors.card, borderColor: colors.border }]}
        accessibilityLabel={`${l.name}: pagadas ${l.paidInstallments} de ${l.totalInstallments} cuotas`}>
        <View style={styles.fila}>
          <CirculoCategoria icono={l.categoryIcon || 'bank'} color={color} tamano={40} />
          <View style={{ flex: 1, gap: 2 }}>
            <AppText variant="body" style={{ fontWeight: '700' }} numberOfLines={1}>{l.name}</AppText>
            <AppText variant="caption" color={colors.textSecondary}>
              Cuota {formatCurrency(l.installmentAmount)} · faltan {l.remainingInstallments} ({formatCurrency(l.remainingTotal)}) · termina {mesAnio(l.endDate)}
            </AppText>
            {!!l.payerName && <AppText variant="caption" color={colors.textMuted}>La paga {l.payerName}</AppText>}
          </View>
        </View>
        <View style={{ gap: 4 }}>
          <View style={[styles.barra, { backgroundColor: colors.border }]}>
            <View style={[styles.relleno, { backgroundColor: color, width: `${Math.min(100, pct)}%` }]} />
          </View>
          <AppText variant="caption" color={colors.textSecondary}>
            {l.paidInstallments} de {l.totalInstallments} cuotas pagadas{l.nextDate ? ` · próxima ${diaMes(l.nextDate)}` : ''}
          </AppText>
        </View>
        {l.remainingInstallments > 0 && (
          <TouchableOpacity style={[styles.boton, styles.solo, { borderColor: colors.primary, borderWidth: 2 }]}
            onPress={() => setAccion({ tipo: 'adelantar', deuda: l })}
            accessibilityRole="button" accessibilityLabel={`Adelantar una cuota de ${l.name}`}>
            <AppText variant="label" color={colors.primary} style={{ fontWeight: '700' }}>Adelantar una cuota</AppText>
          </TouchableOpacity>
        )}
      </View>
    );
  };

  const hoja = (() => {
    if (!accion) return null;
    if (accion.tipo === 'pagar') {
      return {
        titulo: `Pagar ${accion.tarjeta.name}`, boton: 'Pagar',
        texto: `Quedan pagadas las compras hasta hoy: ${formatCurrency(accion.tarjeta.dueNow)}. No es un gasto nuevo: ya contaron cuando compraste.`,
      };
    }
    if (accion.tipo === 'borrar') {
      return { titulo: `Borrar ${accion.tarjeta.name}`, boton: 'Borrar', texto: 'La tarjeta no debe nada. Deja de aparecer en "Pagado con".' };
    }
    return {
      titulo: `Adelantar ${accion.deuda.name}`, boton: 'Adelantar',
      texto: `Pagas hoy ${formatCurrency(accion.deuda.installmentAmount)} y la deuda termina un mes antes.`,
    };
  })();

  return marco(
    <>
      <ScrollView contentContainerStyle={styles.contenido}
        refreshControl={<RefreshControl refreshing={refrescando} onRefresh={refrescar} />}>
        <View style={[styles.resumen, { backgroundColor: colors.surfaceLight }]} accessible
          accessibilityLabel={`Deben ${formatCurrency(d.totalOwed)}. Este mes en cuotas: ${formatCurrency(d.monthlyInstallments)}`}>
          <AppText variant="caption" color={colors.textSecondary}>Deben</AppText>
          <AppText variant="title" color={d.totalOwed > 0 ? colors.expense : colors.text} adjustsFontSizeToFit numberOfLines={1}>
            {formatCurrency(d.totalOwed)}
          </AppText>
          <AppText variant="body" color={colors.textSecondary}>Este mes en cuotas: {formatCurrency(d.monthlyInstallments)}</AppText>
        </View>

        <View style={styles.seccion}>
          <AppText variant="subtitle">Tarjetas</AppText>
          {d.owedOnCards > 0 && <AppText variant="label">{formatCurrency(d.owedOnCards)}</AppText>}
        </View>
        {tarjetas.length === 0 && (
          <AppText variant="body" color={colors.textSecondary}>
            No hay tarjetas. Agrégala para elegirla en "Pagado con" al anotar una compra.
          </AppText>
        )}
        {tarjetas.map(tarjetaUI)}
        <TouchableOpacity style={[styles.boton, styles.agregar, { borderColor: colors.border }]} onPress={() => setAgregando(true)}
          accessibilityRole="button" accessibilityLabel="Agregar tarjeta">
          <Icono nombre="plus" color={colors.primary} tamano={18} />
          <AppText variant="label" color={colors.primary} style={{ fontWeight: '700' }}>Agregar tarjeta</AppText>
        </TouchableOpacity>

        <View style={styles.seccion}>
          <AppText variant="subtitle">En cuotas</AppText>
          {d.owedInInstallments > 0 && <AppText variant="label">{formatCurrency(d.owedInInstallments)}</AppText>}
        </View>
        {cuotas.length === 0 && (
          <AppText variant="body" color={colors.textSecondary}>No hay créditos en cuotas.</AppText>
        )}
        {cuotas.map(cuotaUI)}
        <View style={[styles.pista, { backgroundColor: colors.surfaceLight }]}>
          <AppText variant="caption" color={colors.textSecondary}>
            Para anotar un crédito, créalo en Programados con su número de cuotas.
          </AppText>
          <TouchableOpacity style={styles.enlace} onPress={() => navigation.navigate('ScheduledList')}
            accessibilityRole="button" accessibilityLabel="Ir a Programados">
            <AppText variant="label" color={colors.primary} style={{ fontWeight: '700' }}>Ir a Programados</AppText>
            <Icono nombre="right" color={colors.primary} tamano={16} />
          </TouchableOpacity>
        </View>
      </ScrollView>

      <Hoja visible={!!accion} titulo={hoja?.titulo || ''} onCerrar={() => !ocupado && setAccion(null)}
        textoConfirmar={hoja?.boton} onConfirmar={confirmar} ocupado={ocupado}>
        <AppText variant="body" color={colors.textSecondary}>{hoja?.texto}</AppText>
      </Hoja>
      <FormTarjeta visible={agregando} ocupado={ocupado} onCerrar={() => !ocupado && setAgregando(false)} onGuardar={guardarTarjeta} />
    </>,
  );
};

const styles = StyleSheet.create({
  pantalla: { flex: 1 },
  contenido: { padding: 16, gap: 12, paddingBottom: 32, width: '100%', maxWidth: 720, alignSelf: 'center' },
  resumen: { borderRadius: 16, padding: 16, gap: 2 },
  seccion: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', marginTop: 8 },
  tarjeta: { borderRadius: 16, padding: 14, gap: 10, borderWidth: 1 },
  fila: { flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 44 },
  plastico: { width: 44, height: 32, borderRadius: 6, alignItems: 'center', justifyContent: 'center' },
  detalle: { borderTopWidth: 1, paddingTop: 10, gap: 10 },
  compra: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  acciones: { flexDirection: 'row', gap: 10 },
  boton: { flex: 1, minHeight: 44, borderRadius: 12, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12 },
  solo: { flex: 0 },
  agregar: { flex: 0, flexDirection: 'row', gap: 6, borderWidth: 1, borderStyle: 'dashed' },
  barra: { height: 8, borderRadius: 4, overflow: 'hidden' },
  relleno: { height: 8, borderRadius: 4 },
  pista: { borderRadius: 14, padding: 12, gap: 6 },
  enlace: { flexDirection: 'row', alignItems: 'center', gap: 4, minHeight: 44, alignSelf: 'flex-start' },
});

export default DeudasScreen;
