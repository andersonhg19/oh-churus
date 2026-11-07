import React, { useEffect, useMemo, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, TextInput, Modal } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import AppText from '../../components/atoms/Text';
import Icono from '../../components/atoms/Icono';
import { tintaSobre } from '../../utils/tinta';
import Teclado from '../../components/molecules/Teclado';
import { PALETA } from '../../components/molecules/DonaDeCategorias';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import { useAlcance } from '../../contexts/AlcanceContext';
import { categoryService } from '../../services/categoryService';
import { accountService } from '../../services/accountService';
import { movementService } from '../../services/movementService';
import { evaluar, mostrar, tieneOperacion } from '../../utils/calculadora';
import { fechaLocalISO, formatCurrency, formatDateShort } from '../../utils/format';
import { Account, Category, CategoryType, Movement } from '../../types';

interface Props {
  navigation: any;
  route: { params?: { tipo?: CategoryType; categoriaId?: string | number } };
}

const desplazar = (iso: string, dias: number) => {
  const [y, m, d] = iso.split('-').map(Number);
  const f = new Date(y, m - 1, d + dias);
  return fechaLocalISO(f);
};

/*
 * ANOTAR, como en Monefy: la categoria ya viene elegida, se escribe el monto
 * con el teclado de la app y listo. Son dos toques mas los numeros. Lo demas
 * (fecha, cuenta, reparto, pagado o por pagar) son botones chicos que ya traen
 * lo mas comun; y "Mas opciones" abre el formulario completo con lo escrito,
 * para los repartos por porcentaje o por montos.
 */
const AnotarScreen: React.FC<Props> = ({ navigation, route }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();
  const { miembros } = useAlcance();
  const insets = useSafeAreaInsets();
  const tipo: CategoryType = route.params?.tipo || 'EXPENSE';

  const [categorias, setCategorias] = useState<Category[]>([]);
  const [categoria, setCategoria] = useState<Category | null>(null);
  const [cuentas, setCuentas] = useState<Account[]>([]);
  const [cuenta, setCuenta] = useState<Account | null>(null);
  const [expresion, setExpresion] = useState('');
  const [fecha, setFecha] = useState(fechaLocalISO());
  const [pagado, setPagado] = useState(true);
  const [aMedias, setAMedias] = useState(false);
  const [nota, setNota] = useState('');
  const [eligiendoFecha, setEligiendoFecha] = useState(false);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    if (!user) return;
    (async () => {
      try {
        const r = await categoryService.getAll({ userId: user.userId, page: 0, size: 200 });
        const lista: Category[] = (r.correct && r.object?.list) || [];
        const deEsteTipo = lista.filter((c) => c.type === tipo && c.active !== false)
          .sort((a, b) => a.name.localeCompare(b.name));
        const pedida = deEsteTipo.find((c) => String(c.id) === String(route.params?.categoriaId));
        setCategorias(pedida ? [pedida, ...deEsteTipo.filter((c) => c !== pedida)] : deEsteTipo);
        /* Sin categoria pedida no se elige ninguna: con la primera por orden
           alfabetico ("Ahorro") un gasto podia quedar anotado como ahorro. */
        setCategoria(pedida || null);
      } catch {
        setCategorias([]);
      }
      try {
        const r = await accountService.getAll();
        const lista: Account[] = (r.correct && r.object?.list) || [];
        setCuentas(lista);
        setCuenta(lista.find((a: any) => a.isDefault) || lista[0] || null);
      } catch {
        setCuentas([]);
      }
    })();
  }, [user, tipo, route.params?.categoriaId]);

  const monto = evaluar(expresion);
  const color = tipo === 'INCOME' ? colors.income : colors.expense;
  const companeros = miembros.filter((m) => String(m.userId) !== String(user?.userId));
  const puedeRepartir = tipo === 'EXPENSE' && !!categoria?.householdId && companeros.length > 0;
  const colorDe = useMemo(() => {
    const mapa: Record<string, string> = {};
    categorias.forEach((c, i) => { mapa[c.id] = c.color || PALETA[i % PALETA.length]; });
    return mapa;
  }, [categorias]);

  const guardar = async () => {
    if (!categoria || monto <= 0 || !user || guardando) return;
    setGuardando(true);
    try {
      const datos: Partial<Movement> & Record<string, any> = {
        userId: user.userId,
        categoryId: categoria.id,
        amount: monto,
        date: fecha,
        description: nota.trim() || undefined,
        confirmed: pagado,
        ...(cuenta && { accountId: cuenta.id }),
        ...(puedeRepartir && aMedias && {
          splitMode: 'EQUAL',
          splits: [Number(user.userId), ...companeros.map((c) => c.userId)].map((id) => ({ participantId: id })),
        }),
      };
      const r = await movementService.save(datos);
      if (!r.correct) {
        showToast('error', 'No se pudo anotar', r.message);
        return;
      }
      showToast('success', tipo === 'INCOME' ? 'Ingreso anotado' : 'Gasto anotado',
        `${categoria.name} · ${formatCurrency(monto)}`);
      navigation.goBack();
    } catch (e: any) {
      showToast('error', 'Error', e?.message || 'No se pudo anotar');
    } finally {
      setGuardando(false);
    }
  };

  const chip = (texto: string, activo: boolean, onPress: () => void, etiqueta: string) => (
    <TouchableOpacity
      key={texto}
      style={[styles.chip, { backgroundColor: activo ? colors.primary : colors.surfaceLight }]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={etiqueta}
    >
      <AppText variant="caption" style={{ fontWeight: '700', color: activo ? '#1A1405' : colors.textSecondary }}>{texto}</AppText>
    </TouchableOpacity>
  );

  const hoy = fechaLocalISO();
  const textoFecha = fecha === hoy ? 'Hoy' : fecha === desplazar(hoy, -1) ? 'Ayer' : formatDateShort(fecha);

  return (
    <View style={[styles.pantalla, { backgroundColor: colors.background, paddingBottom: insets.bottom }]}>
      <View style={[styles.monto, { backgroundColor: color, paddingTop: insets.top + 8 }]}>
        <View style={styles.filaMonto}>
          <TouchableOpacity onPress={() => navigation.goBack()} style={styles.cerrar} accessibilityRole="button" accessibilityLabel="Cerrar sin anotar">
            <Icono nombre="close" color="#FFFFFF" />
          </TouchableOpacity>
          <AppText variant="caption" style={{ color: '#FFFFFF', fontWeight: '700', flex: 1 }}>
            {tipo === 'INCOME' ? 'Ingreso' : 'Gasto'}{categoria ? ` · ${categoria.name}` : ' · elige la categoría'}
          </AppText>
        </View>
        <AppText variant="title" style={{ color: '#FFFFFF', textAlign: 'right' }} accessibilityLabel={`Monto ${formatCurrency(monto)}`}>
          {mostrar(expresion)}
        </AppText>
        {tieneOperacion(expresion) && (
          <AppText variant="body" style={{ color: '#FFFFFF', textAlign: 'right', opacity: 0.9 }}>= {formatCurrency(monto)}</AppText>
        )}
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filaChips} contentContainerStyle={styles.chips}>
        {categorias.map((c) => (
          <TouchableOpacity
            key={c.id}
            style={[styles.chip, { backgroundColor: categoria?.id === c.id ? colors.primary : colors.surfaceLight }]}
            onPress={() => setCategoria(c)}
            accessibilityRole="radio"
            accessibilityState={{ selected: categoria?.id === c.id }}
            accessibilityLabel={`Categoría ${c.name}`}
          >
            <View style={[styles.punto, { backgroundColor: colorDe[c.id] }]}>
              <Icono nombre={c.icon} color={tintaSobre(colorDe[c.id])} tamano={14} />
            </View>
            <AppText variant="caption" style={{ fontWeight: '700', color: categoria?.id === c.id ? '#1A1405' : colors.textSecondary }}>{c.name}</AppText>
          </TouchableOpacity>
        ))}
      </ScrollView>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filaChips} contentContainerStyle={[styles.chips, { paddingTop: 0 }]}>
        {chip(textoFecha, false, () => setEligiendoFecha(true), `Fecha ${textoFecha}. Cambiar la fecha`)}
        {chip(pagado ? 'Ya pagado' : 'Por pagar', !pagado, () => setPagado(!pagado),
          pagado ? 'Marcado como ya pagado. Tocar para dejarlo por pagar' : 'Marcado por pagar. Tocar para marcarlo pagado')}
        {puedeRepartir && chip(aMedias ? 'A medias' : 'Sin repartir', aMedias, () => setAMedias(!aMedias),
          aMedias ? 'Repartido a medias. Tocar para no repartir' : 'Sin repartir. Tocar para repartir a medias')}
        {cuentas.length > 1 && cuenta && chip(cuenta.name, false, () => {
          const i = cuentas.findIndex((a) => a.id === cuenta.id);
          setCuenta(cuentas[(i + 1) % cuentas.length]);
        }, `Cuenta ${cuenta.name}. Tocar para cambiar de cuenta`)}
        {chip('Más opciones', false, () => navigation.replace('MovementFormModal', {
          prefill: { amount: monto || undefined, categoryId: categoria?.id, type: tipo, date: fecha, description: nota, confirmed: pagado },
        }), 'Abrir el formulario completo con lo escrito')}
      </ScrollView>

      <TextInput
        value={nota}
        onChangeText={setNota}
        placeholder="Nota (opcional)"
        placeholderTextColor={colors.textMuted}
        style={[styles.nota, { backgroundColor: colors.surfaceLight, color: colors.text }]}
        accessibilityLabel="Nota del movimiento"
      />

      <View style={{ flex: 1 }} />
      <Teclado
        expresion={expresion}
        onCambio={setExpresion}
        onListo={guardar}
        textoListo={guardando ? 'Anotando…' : 'Anotar'}
        listoDeshabilitado={monto <= 0 || !categoria || guardando}
      />

      <Modal visible={eligiendoFecha} transparent animationType="fade" onRequestClose={() => setEligiendoFecha(false)}>
        <TouchableOpacity style={styles.fondo} activeOpacity={1} onPress={() => setEligiendoFecha(false)}
          accessibilityRole="button" accessibilityLabel="Cerrar">
          <View style={[styles.hoja, { backgroundColor: colors.surface }]}>
            <AppText variant="subtitle">¿Cuándo fue?</AppText>
            <AppText variant="title" style={{ textAlign: 'center' }}>{formatDateShort(fecha)}</AppText>
            <View style={styles.filaFecha}>
              {chip('− 1 día', false, () => setFecha(desplazar(fecha, -1)), 'Un día antes')}
              {chip('Hoy', fecha === hoy, () => setFecha(hoy), 'Hoy')}
              {chip('+ 1 día', false, () => setFecha(desplazar(fecha, 1)), 'Un día después')}
            </View>
            {chip('Listo', true, () => setEligiendoFecha(false), 'Usar esta fecha')}
          </View>
        </TouchableOpacity>
      </Modal>
    </View>
  );
};

const styles = StyleSheet.create({
  pantalla: { flex: 1 },
  monto: { paddingHorizontal: 16, paddingBottom: 14, gap: 2 },
  filaMonto: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  cerrar: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', marginLeft: -10 },
  filaChips: { flexGrow: 0, flexShrink: 0 },
  chips: { gap: 8, paddingHorizontal: 16, paddingVertical: 10, alignItems: 'center' },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 6, minHeight: 40, paddingHorizontal: 12, borderRadius: 20 },
  punto: { width: 22, height: 22, borderRadius: 11, alignItems: 'center', justifyContent: 'center' },
  nota: { marginHorizontal: 16, padding: 12, borderRadius: 12, fontSize: 16 },
  fondo: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' },
  hoja: { padding: 20, gap: 14, borderTopLeftRadius: 24, borderTopRightRadius: 24 },
  filaFecha: { flexDirection: 'row', justifyContent: 'center', gap: 8 },
});

export default AnotarScreen;
