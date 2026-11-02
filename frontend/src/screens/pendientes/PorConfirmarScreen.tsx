import React, { useCallback, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Modal } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import AppText from '../../components/atoms/Text';
import Icono from '../../components/atoms/Icono';
import Spinner from '../../components/atoms/Spinner';
import Encabezado from '../../components/molecules/Encabezado';
import EstadoError from '../../components/molecules/EstadoError';
import MontoModal from '../../components/molecules/MontoModal';
import { PALETA } from '../../components/molecules/DonaDeCategorias';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import { useAlcance } from '../../contexts/AlcanceContext';
import { useCarga, exigir } from '../../hooks/useCarga';
import { dashboardService } from '../../services/dashboardService';
import { movementService } from '../../services/movementService';
import { fechaLocalISO, formatCurrency, formatDateShort } from '../../utils/format';
import { Movement } from '../../types';

/*
 * POR CONFIRMAR: los pagos del periodo que todavia no se marcaron, para
 * confirmarlos de a varios (el ahorro, el arriendo, las cuotas de la tarjeta)
 * en un solo toque. Por defecto, los mios; "De los dos" ensena tambien los del
 * otro, que es donde se le pasa un pago cuando hay una eventualidad.
 * Tocar una fila abre lo que se puede hacer con ese pago.
 */
const PorConfirmarScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();
  const { corte, periodo, miembros, nombreDe } = useAlcance();
  const [todos, setTodos] = useState<Movement[]>([]);
  const [soloMios, setSoloMios] = useState(true);
  const [marcados, setMarcados] = useState<Record<string, boolean>>({});
  const [valores, setValores] = useState<Record<string, number>>({});
  const [abierto, setAbierto] = useState<Movement | null>(null);
  const [cambiandoValor, setCambiandoValor] = useState<Movement | null>(null);
  const [confirmando, setConfirmando] = useState(false);

  const traer = useCallback(async () => {
    if (!user) return;
    setTodos(exigir(await dashboardService.getPending(user.userId, corte, periodo.inicio)) || []);
    setMarcados({});
    setValores({});
  }, [user, corte, periodo.inicio]);
  const { cargando, error, cargar } = useCarga(traer);
  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  const lista = todos
    .filter((m) => !soloMios || String(m.userId) === String(user?.userId))
    .sort((a, b) => (a.date || '').localeCompare(b.date || ''));
  const valorDe = (m: Movement) => valores[m.id] ?? m.amount ?? 0;
  const elegidos = lista.filter((m) => marcados[m.id]);
  const total = elegidos.reduce((t, m) => t + (m.categoryType === 'INCOME' ? 0 : valorDe(m)), 0);
  /* La fecha local, no la de UTC: a las 7 de la noche UTC ya es manana. */
  const hoy = fechaLocalISO();

  const confirmar = async (items: Movement[]) => {
    if (items.length === 0 || confirmando) return;
    setConfirmando(true);
    try {
      const r = await movementService.confirmBatch(items.map((m) => ({
        id: m.id, amount: valores[m.id] !== undefined && valores[m.id] !== m.amount ? valores[m.id] : undefined })));
      if (!r.correct) { showToast('error', 'No se confirmó ninguno', r.message); return; }
      showToast('success', `${r.object?.confirmed ?? items.length} confirmados`, formatCurrency(r.object?.total ?? total));
      setAbierto(null);
      cargar();
    } catch { showToast('error', 'Error', 'No se pudieron confirmar'); }
    finally { setConfirmando(false); }
  };

  const reasignar = async (m: Movement, quien: number) => {
    try {
      const r = await movementService.reassign(String(m.id), quien);
      if (!r.correct) { showToast('error', 'No se pudo cambiar', r.message); return; }
      showToast('success', 'Responsable cambiado', `${m.description || 'El pago'} ahora lo paga ${nombreDe(quien) || 'la otra persona'}`);
      setAbierto(null);
      cargar();
    } catch { showToast('error', 'Error', 'No se pudo cambiar quién paga'); }
  };

  const cabeza = <Encabezado titulo="Por confirmar" atras conAlcance={false} />;
  if (cargando && todos.length === 0) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}>{cabeza}<Spinner fullScreen /></View>;
  if (error) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}>{cabeza}<EstadoError mensaje={error} onReintentar={cargar} /></View>;

  const accion = (texto: string, icono: string, onPress: () => void, etiqueta: string) => (
    <TouchableOpacity style={[styles.accion, { borderBottomColor: colors.border }]} onPress={onPress}
      accessibilityRole="button" accessibilityLabel={etiqueta}>
      <Icono nombre={icono} color={colors.text} />
      <AppText variant="body">{texto}</AppText>
    </TouchableOpacity>
  );

  return (
    <View style={[styles.pantalla, { backgroundColor: colors.background }]}>
      {cabeza}
      <View style={styles.cabecera}>
        <View style={[styles.pestanas, { backgroundColor: colors.surfaceLight }]}>
          {[true, false].map((v) => (
            <TouchableOpacity key={String(v)} style={[styles.pestana, soloMios === v && { backgroundColor: colors.primary }]}
              onPress={() => { setSoloMios(v); setMarcados({}); }} accessibilityRole="tab"
              accessibilityState={{ selected: soloMios === v }} accessibilityLabel={v ? 'Solo los míos' : 'Los de los dos'}>
              <AppText variant="caption" style={{ fontWeight: '700', color: soloMios === v ? '#1A1405' : colors.textSecondary }}>
                {v ? 'Los míos' : 'De los dos'}
              </AppText>
            </TouchableOpacity>
          ))}
        </View>
        {lista.length > 0 && (
          <TouchableOpacity onPress={() => setMarcados(elegidos.length === lista.length ? {} : Object.fromEntries(lista.map((m) => [m.id, true])))}
            style={styles.todo} accessibilityRole="button"
            accessibilityLabel={elegidos.length === lista.length ? 'Desmarcar todos' : 'Marcar todos'}>
            <AppText variant="caption" style={{ color: colors.primary, fontWeight: '700' }}>
              {elegidos.length === lista.length ? 'Ninguno' : 'Todos'}
            </AppText>
          </TouchableOpacity>
        )}
      </View>

      <ScrollView contentContainerStyle={styles.contenido}>
        {lista.length === 0 && (
          <AppText variant="body" color={colors.textSecondary} style={{ textAlign: 'center', marginTop: 32 }}>
            {soloMios ? 'No tienes pagos por confirmar en este periodo.' : 'No hay pagos por confirmar en este periodo.'}
          </AppText>
        )}
        {lista.map((m, i) => {
          const marcado = !!marcados[m.id];
          const vencido = (m.date || '') < hoy;
          const ingreso = m.categoryType === 'INCOME';
          return (
            <View key={m.id} style={[styles.fila, { borderBottomColor: colors.border }]}>
              <TouchableOpacity onPress={() => setMarcados({ ...marcados, [m.id]: !marcado })} style={styles.casilla}
                accessibilityRole="checkbox" accessibilityState={{ checked: marcado }}
                accessibilityLabel={`${marcado ? 'Desmarcar' : 'Marcar'} ${m.description || m.categoryName}`}>
                <View style={[styles.check, { borderColor: marcado ? colors.income : colors.textMuted }, marcado && { backgroundColor: colors.income }]}>
                  {marcado && <Icono nombre="check" color="#FFFFFF" tamano={16} />}
                </View>
              </TouchableOpacity>
              <TouchableOpacity style={styles.cuerpo} onPress={() => setAbierto(m)} accessibilityRole="button"
                accessibilityLabel={`${m.description || m.categoryName}, ${formatCurrency(valorDe(m))}. Ver opciones`}>
                <View style={[styles.circulo, { backgroundColor: m.categoryColor || PALETA[i % PALETA.length] }]}>
                  <Icono nombre={m.categoryIcon} color="#FFFFFF" tamano={18} />
                </View>
                <View style={{ flex: 1, minWidth: 0 }}>
                  <AppText variant="body" style={{ fontWeight: '700' }} numberOfLines={1}>{m.description || m.categoryName}</AppText>
                  <AppText variant="caption" color={vencido ? colors.expense : colors.textSecondary} numberOfLines={1}>
                    {formatDateShort(m.date)}{vencido ? ' · vencido' : ''}{!soloMios ? ` · ${m.payerName || nombreDe(m.userId) || 'paga otro'}` : ''}{ingreso ? ' · por recibir' : ''}
                  </AppText>
                </View>
                <AppText variant="body" style={{ fontWeight: '800', color: ingreso ? colors.income : colors.text }}>{formatCurrency(valorDe(m))}</AppText>
              </TouchableOpacity>
            </View>
          );
        })}
      </ScrollView>

      {elegidos.length > 0 && (
        <View style={[styles.pie, { backgroundColor: colors.surface }]}>
          <TouchableOpacity style={[styles.boton, { backgroundColor: colors.income }]} onPress={() => confirmar(elegidos)}
            disabled={confirmando} accessibilityRole="button"
            accessibilityLabel={`Confirmar ${elegidos.length} pagos por ${formatCurrency(total)}`}>
            <AppText variant="body" style={{ color: '#FFFFFF', fontWeight: '800' }}>
              {confirmando ? 'Confirmando…' : `Confirmar ${elegidos.length} · ${formatCurrency(total)}`}
            </AppText>
          </TouchableOpacity>
        </View>
      )}

      <Modal visible={!!abierto} transparent animationType="slide" onRequestClose={() => setAbierto(null)}>
        <TouchableOpacity style={styles.fondo} activeOpacity={1} onPress={() => setAbierto(null)} accessibilityRole="button" accessibilityLabel="Cerrar">
          <View style={[styles.hoja, { backgroundColor: colors.surface }]}>
            {abierto && (
              <>
                <AppText variant="subtitle">{abierto.description || abierto.categoryName}</AppText>
                <AppText variant="caption" color={colors.textSecondary}>
                  {formatDateShort(abierto.date)} · {formatCurrency(valorDe(abierto))} · paga {abierto.payerName || nombreDe(abierto.userId)}
                </AppText>
                {accion('Confirmar ahora', 'check', () => confirmar([abierto]), 'Confirmar este pago ahora')}
                {accion('Cambiar el valor', 'edit', () => { setCambiandoValor(abierto); setAbierto(null); }, 'Cambiar el valor de este pago')}
                {abierto.shared && miembros.length > 1 && miembros.filter((x) => String(x.userId) !== String(abierto.userId)).map((x) =>
                  accion(`Que lo pague ${x.nombre} esta vez`, 'people', () => reasignar(abierto, x.userId), `Pasarle este pago a ${x.nombre}`))}
                {abierto.categoryType !== 'INCOME' && accion('Anotar un sub-gasto', 'plus', () => { const p = abierto; setAbierto(null); navigation.navigate('MovementFormModal', { parentMovement: p }); },
                  'Anotar un gasto dentro de este pago')}
                {accion('Editar', 'list', () => { const p = abierto; setAbierto(null); navigation.navigate('MovementFormModal', { movement: p }); }, 'Editar el pago completo')}
              </>
            )}
          </View>
        </TouchableOpacity>
      </Modal>

      <MontoModal visible={!!cambiandoValor} titulo={cambiandoValor?.description || 'Valor real'}
        detalle="Si el valor real fue distinto al previsto" inicial={cambiandoValor ? valorDe(cambiandoValor) : 0}
        onListo={(v) => { if (cambiandoValor) { setValores({ ...valores, [cambiandoValor.id]: v }); setMarcados({ ...marcados, [cambiandoValor.id]: true }); } setCambiandoValor(null); }}
        onCerrar={() => setCambiandoValor(null)} />
    </View>
  );
};

const styles = StyleSheet.create({
  pantalla: { flex: 1 },
  cabecera: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 16, paddingTop: 8 },
  pestanas: { flex: 1, flexDirection: 'row', borderRadius: 12, padding: 4 },
  pestana: { flex: 1, alignItems: 'center', paddingVertical: 10, borderRadius: 9, minHeight: 44, justifyContent: 'center' },
  todo: { minHeight: 44, paddingHorizontal: 8, justifyContent: 'center' },
  contenido: { padding: 16, paddingBottom: 32 },
  fila: { flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1 },
  casilla: { width: 48, height: 56, alignItems: 'center', justifyContent: 'center', marginLeft: -8 },
  check: { width: 26, height: 26, borderRadius: 13, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
  cuerpo: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 10, minHeight: 56 },
  circulo: { width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  pie: { padding: 16 },
  boton: { minHeight: 54, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  fondo: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' },
  hoja: { padding: 20, gap: 4, borderTopLeftRadius: 24, borderTopRightRadius: 24 },
  accion: { flexDirection: 'row', alignItems: 'center', gap: 14, minHeight: 52, borderBottomWidth: 1 },
});

export default PorConfirmarScreen;
