import React, { useCallback, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import AppText from '../../components/atoms/Text';
import Icono from '../../components/atoms/Icono';
import Spinner from '../../components/atoms/Spinner';
import Encabezado from '../../components/molecules/Encabezado';
import EstadoError from '../../components/molecules/EstadoError';
import MontoModal from '../../components/molecules/MontoModal';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import { useAlcance } from '../../contexts/AlcanceContext';
import { useCarga, exigir } from '../../hooks/useCarga';
import { estadoService, EstadoDelPeriodo } from '../../services/estadoService';
import { budgetAllocationService } from '../../services/budgetAllocationService';
import { movementService } from '../../services/movementService';
import { formatCurrency, formatDateShort } from '../../utils/format';
import { ESTADOS, ESTADOS_ELEMENTO, colorDeTono, fraseDelCierre } from '../../utils/estadoTextos';

/*
 * UNA CATEGORIA por dentro: lo planeado, lo gastado, lo que falta pagar y lo
 * disponible; sus elementos (cada factura, cuota, ahorro o bolsa con quien la
 * paga y si ya se pago); y quien gasto cuanto. Aqui tambien se fija o se
 * cambia lo presupuestado, con el teclado de la app.
 */
const CategoriaScreen: React.FC<{ navigation: any; route: any }> = ({ navigation, route }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();
  const { alcance, corte, periodo } = useAlcance();
  const id = Number(route.params?.categoryId);
  const [estado, setEstado] = useState<EstadoDelPeriodo | null>(null);
  const [fijando, setFijando] = useState(false);
  const [abierto, setAbierto] = useState<number | null>(null);

  const traer = useCallback(async () => {
    setEstado(exigir(await estadoService.periodo(corte, periodo.inicio, alcance)) || null);
  }, [corte, periodo.inicio, alcance]);
  const { cargando, error, cargar } = useCarga(traer);
  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  const c = estado?.categories.find((x) => x.categoryId === id);
  const titulo = c?.categoryName || 'Categoría';

  if (cargando && !estado) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}><Encabezado titulo={titulo} atras /><Spinner fullScreen /></View>;
  if (error) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}><Encabezado titulo={titulo} atras /><EstadoError mensaje={error} onReintentar={cargar} /></View>;

  /* Lo presupuestado se fija en el hogar (o en lo personal propio): en la vista
     de una persona, lo de una categoria comun es lo que le toca, no se edita. */
  const editable = !!c && (alcance.tipo === 'HOGAR' ? c.shared : !c.shared && alcance.userId === Number(user?.userId));

  /* Liberar se puede deshacer: si se libero por error, o al final si se pudo
     pagar, lo liberado vuelve a quedar por pagar. */
  const deshacer = async (movementId: number, nombre: string) => {
    try {
      const r = await movementService.unrelease(movementId);
      if (!r.correct) { showToast('error', 'No se pudo', r.message); return; }
      showToast('success', 'Listo', `${nombre} vuelve a estar por pagar`);
      setAbierto(null);
      cargar();
    } catch { showToast('error', 'Error', 'No se pudo deshacer la liberación'); }
  };

  const fijar = async (monto: number) => {
    if (!user) return;
    try {
      const r = await budgetAllocationService.save({ userId: user.userId, categoryId: id, amount: monto,
        budgetStartDay: corte, referenceDate: periodo.inicio });
      if (!r.correct) { showToast('error', 'No se pudo guardar', r.message); return; }
      showToast('success', 'Presupuesto guardado', `${titulo}: ${formatCurrency(monto)}`);
      setFijando(false);
      cargar();
    } catch { showToast('error', 'Error', 'No se pudo guardar el presupuesto'); }
  };

  const quitar = async () => {
    if (!user) return;
    try {
      const l = await budgetAllocationService.list(user.userId, corte, periodo.inicio);
      const obj: any = l.object;
      const lista: any[] = (l.correct && (Array.isArray(obj) ? obj : obj?.list)) || [];
      const a = lista.find((x) => Number(x.categoryId) === id);
      if (!a) { showToast('error', 'No se encontró el presupuesto'); return; }
      const r = await budgetAllocationService.delete(a.id);
      if (!r.correct) { showToast('error', 'No se pudo quitar', r.message); return; }
      showToast('success', 'Presupuesto quitado', `${titulo} queda con lo planeado en sus elementos`);
      cargar();
    } catch { showToast('error', 'Error', 'No se pudo quitar el presupuesto'); }
  };

  if (!c) {
    return (
      <View style={[styles.pantalla, { backgroundColor: colors.background }]}>
        <Encabezado titulo={titulo} atras />
        <AppText variant="body" color={colors.textSecondary} style={{ padding: 24, textAlign: 'center' }}>
          En este periodo y para esta vista no hay nada en esta categoría.
        </AppText>
      </View>
    );
  }

  const est = ESTADOS[c.status];
  const caja = (titulo2: string, valor: number, color?: string) => (
    <View style={[styles.caja, { backgroundColor: colors.surfaceLight }]}>
      <AppText variant="caption" color={colors.textSecondary}>{titulo2}</AppText>
      <AppText variant="body" style={{ fontWeight: '800', color: color || colors.text }}>{formatCurrency(valor)}</AppText>
    </View>
  );

  return (
    <View style={[styles.pantalla, { backgroundColor: colors.background }]}>
      <Encabezado titulo={titulo} atras />
      <ScrollView contentContainerStyle={styles.contenido}>
        <View style={styles.cajas}>
          {caja(c.kind === 'SAVING' ? 'Meta' : 'Planeado', c.allocated)}
          {caja(c.kind === 'SAVING' ? 'Ahorrado' : 'Gastado', c.actual, est.tono === 'mal' ? colors.expense : undefined)}
          {caja('Por pagar', c.committed)}
          {caja('Disponible', c.available, c.available < 0 ? colors.expense : colors.income)}
        </View>
        <View style={[styles.frase, { borderColor: colorDeTono(est.tono, colors) }]}>
          <AppText variant="caption" style={{ color: colorDeTono(est.tono, colors), fontWeight: '700' }}>{est.texto}</AppText>
          <AppText variant="body">{fraseDelCierre(c, estado!.closed)}</AppText>
          {c.provision > 0 && (
            <AppText variant="caption" color={colors.textSecondary}>
              Bolsa de {formatCurrency(c.provision)}: quedan {formatCurrency(c.provisionLeft)}.
            </AppText>
          )}
        </View>

        {editable && (
          <TouchableOpacity style={[styles.boton, { backgroundColor: colors.primary }]} onPress={() => setFijando(true)}
            accessibilityRole="button" accessibilityLabel={`Fijar lo presupuestado de ${titulo}`}>
            <AppText variant="body" style={{ color: '#1A1405', fontWeight: '800' }}>
              {c.hasAllocation ? 'Cambiar lo presupuestado' : 'Fijar lo presupuestado'}
            </AppText>
          </TouchableOpacity>
        )}

        {editable && c.hasAllocation && (
          <TouchableOpacity style={[styles.enlace, { borderColor: colors.border, justifyContent: 'center' }]} onPress={quitar}
            accessibilityRole="button" accessibilityLabel={`Quitar lo presupuestado de ${titulo}`}>
            <AppText variant="caption" color={colors.expense} style={{ fontWeight: '700' }}>Quitar lo presupuestado</AppText>
          </TouchableOpacity>
        )}

        {!!c.items?.length && (
          <>
            <AppText variant="subtitle">Elementos</AppText>
            {c.items.map((it, i) => {
              const e = ESTADOS_ELEMENTO[it.status];
              const liberado = (it.released || 0) > 0 && !!it.movementId && it.status !== 'PAGADO';
              const fila = (
                <>
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <AppText variant="body" style={{ fontWeight: '700' }} numberOfLines={1}>{it.name}</AppText>
                    <AppText variant="caption" color={colors.textSecondary}>
                      {it.payerName ? `Paga ${it.payerName} · ` : ''}{formatDateShort(it.date)} · <AppText variant="caption" style={{ color: colorDeTono(e.tono, colors) }}>{e.texto}</AppText>
                      {liberado ? ` · liberado ${formatCurrency(it.released || 0)}` : ''}
                    </AppText>
                  </View>
                  <AppText variant="body" style={{ fontWeight: '800' }}>{formatCurrency(it.amount)}</AppText>
                </>
              );
              const clave = `${it.scheduledMovementId}-${it.date}-${i}`;
              if (!liberado) return <View key={clave} style={[styles.fila, { borderBottomColor: colors.border }]}>{fila}</View>;
              return (
                <View key={clave} style={{ borderBottomWidth: 1, borderBottomColor: colors.border }}>
                  <TouchableOpacity style={[styles.fila, { borderBottomWidth: 0 }]} onPress={() => setAbierto(abierto === it.movementId ? null : it.movementId!)}
                    accessibilityRole="button" accessibilityLabel={`${it.name}: liberado ${formatCurrency(it.released || 0)}. Ver opciones`}>
                    {fila}
                  </TouchableOpacity>
                  {abierto === it.movementId && (
                    <TouchableOpacity style={[styles.enlace, { borderColor: colors.primary, justifyContent: 'center', marginBottom: 10 }]}
                      onPress={() => deshacer(it.movementId!, it.name)}
                      accessibilityRole="button" accessibilityLabel={`Deshacer la liberación de ${it.name}: vuelve a estar por pagar`}>
                      <AppText variant="caption" style={{ color: colors.primary, fontWeight: '700' }}>
                        {`Deshacer: vuelve a estar por pagar (${formatCurrency((it.released || 0) + (it.status === 'LIBERADO' ? 0 : it.amount))})`}
                      </AppText>
                    </TouchableOpacity>
                  )}
                </View>
              );
            })}
            {c.otherSpending > 0 && (
              <View style={[styles.fila, { borderBottomColor: colors.border }]}>
                <AppText variant="body" style={{ flex: 1 }} color={colors.textSecondary}>Otros gastos de la categoría</AppText>
                <AppText variant="body" style={{ fontWeight: '800' }}>{formatCurrency(c.otherSpending)}</AppText>
              </View>
            )}
          </>
        )}

        {(c.fromSavings || 0) > 0 && (
          <View style={[styles.fila, { borderBottomColor: colors.border }]}>
            <Icono nombre="piggy-bank" color={colors.textSecondary} />
            <AppText variant="body" style={{ flex: 1 }}>Pagado con el ahorro</AppText>
            <AppText variant="body" style={{ fontWeight: '800' }}>{formatCurrency(c.fromSavings || 0)}</AppText>
          </View>
        )}

        {!!c.withdrawals?.length && (
          <>
            <AppText variant="subtitle">Sacado del ahorro</AppText>
            {c.withdrawals.map((w) => (
              <View key={w.movementId} style={[styles.fila, { borderBottomColor: colors.border }]}>
                <View style={{ flex: 1, minWidth: 0 }}>
                  <AppText variant="body" style={{ fontWeight: '700' }} numberOfLines={1}>{w.description || w.toCategoryName}</AppText>
                  <AppText variant="caption" color={colors.textSecondary}>{`Para ${w.toCategoryName} · ${formatDateShort(w.date)}`}</AppText>
                </View>
                <AppText variant="body" style={{ fontWeight: '800', color: colors.expense }}>{`−${formatCurrency(w.amount)}`}</AppText>
              </View>
            ))}
          </>
        )}

        {c.byPerson.length > 0 && (
          <>
            <AppText variant="subtitle">Quién</AppText>
            {c.byPerson.map((p) => (
              <View key={p.userId} style={[styles.fila, { borderBottomColor: colors.border }]}>
                <Icono nombre="user" color={colors.textSecondary} />
                <AppText variant="body" style={{ flex: 1 }}>{p.name || 'Alguien del hogar'}</AppText>
                <View style={{ alignItems: 'flex-end' }}>
                  <AppText variant="body" style={{ fontWeight: '800' }}>{formatCurrency(p.actual)}</AppText>
                  {p.committed > 0 && <AppText variant="caption" color={colors.textSecondary}>por pagar {formatCurrency(p.committed)}</AppText>}
                </View>
              </View>
            ))}
          </>
        )}

        <TouchableOpacity style={[styles.enlace, { borderColor: colors.border }]}
          onPress={() => navigation.navigate('CategoryDrillDown', { categoryId: String(id), categoryName: titulo,
            color: c.categoryColor || colors.primary, startDate: periodo.inicio, endDate: periodo.fin })}
          accessibilityRole="button" accessibilityLabel={`Ver los movimientos de ${titulo}`}>
          <AppText variant="body" style={{ fontWeight: '700' }}>Ver los movimientos</AppText>
          <Icono nombre="right" color={colors.textSecondary} />
        </TouchableOpacity>
      </ScrollView>

      <MontoModal visible={fijando} titulo={`Presupuesto de ${titulo}`}
        detalle={`${formatDateShort(periodo.inicio)} – ${formatDateShort(periodo.fin)}`}
        inicial={c.hasAllocation ? c.allocated : c.plannedInItems} textoListo="Guardar"
        onListo={fijar} onCerrar={() => setFijando(false)} />
    </View>
  );
};

const styles = StyleSheet.create({
  pantalla: { flex: 1 },
  contenido: { padding: 16, gap: 12, paddingBottom: 32 },
  cajas: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  caja: { flexBasis: '48%', flexGrow: 1, borderRadius: 12, padding: 12, gap: 2 },
  frase: { borderLeftWidth: 4, paddingLeft: 12, gap: 4 },
  boton: { borderRadius: 14, minHeight: 52, alignItems: 'center', justifyContent: 'center' },
  fila: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 12, borderBottomWidth: 1 },
  enlace: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderWidth: 1, borderRadius: 14, padding: 14, minHeight: 52 },
});

export default CategoriaScreen;
