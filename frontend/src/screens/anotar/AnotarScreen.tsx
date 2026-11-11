import React, { useEffect, useMemo, useRef, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, TextInput, Modal } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import AsyncStorage from '@react-native-async-storage/async-storage';
import AppText from '../../components/atoms/Text';
import Icono from '../../components/atoms/Icono';
import { tintaSobre } from '../../utils/tinta';
import { textoLegibleSobre } from '../../theme/contraste';
import Teclado, { useTecladoFisico } from '../../components/molecules/Teclado';
import Calendario, { moverDias } from '../../components/molecules/Calendario';
import ConfirmarDescarte from '../../components/molecules/ConfirmarDescarte';
import { nombreTarjeta } from '../../utils/medioDePago';
import { PALETA } from '../../components/molecules/DonaDeCategorias';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import { useAhorros, useMisTarjetas } from '../../hooks/useAhorros';
import { categoryService } from '../../services/categoryService';
import { movementService } from '../../services/movementService';
import { evaluar, mostrar, pulsar, tieneOperacion } from '../../utils/calculadora';
import { fechaLocalISO, formatCurrency, formatDateShort } from '../../utils/format';
import { Category, CategoryType, Movement } from '../../types';

interface Props {
  navigation: any;
  route: {
    params?: {
      tipo?: CategoryType;
      categoriaId?: string | number;
      /** Con que se paga de entrada: 'ahorro:<id>' o 'tarjeta:<id>'. */
      pagadoCon?: string;
    };
  };
}

/** Verde del ahorro: distinto del verde de los ingresos, siempre con la alcancia. */
export const VERDE_AHORRO = '#1FA67A';
const CUOTAS_RAPIDAS = [1, 3, 6, 12, 24, 36];

const sinTildes = (t: string) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

const claveRecientes = (tipo: CategoryType) => `oc.recientes.${tipo}`;

async function leerRecientes(tipo: CategoryType): Promise<string[]> {
  try {
    const v = await AsyncStorage.getItem(claveRecientes(tipo));
    const l = v ? JSON.parse(v) : [];
    return Array.isArray(l) ? l.map(String) : [];
  } catch {
    return [];
  }
}

async function recordarReciente(tipo: CategoryType, id: string) {
  try {
    const antes = await leerRecientes(tipo);
    const nuevos = [id, ...antes.filter((x) => x !== id)].slice(0, 6);
    await AsyncStorage.setItem(claveRecientes(tipo), JSON.stringify(nuevos));
  } catch {
    /* sin recientes no pasa nada */
  }
}

/*
 * ANOTAR. La categoria puede venir elegida (desde la dona): entonces son el
 * monto y un toque. Si no, se elige de una LISTA (con buscador y las
 * recientes a mano), no de una fila de botones con todas.
 *
 * "Pagado con" dice de donde salio la plata: de los fondos del mes (lo de
 * siempre), de un ahorro (no baja lo libre del mes, baja el ahorro) o de una
 * tarjeta (y a cuantas cuotas: cada cuota cuenta en su mes). Meter plata a un
 * ahorro es anotar en esa categoria, en verde.
 */
const AnotarScreen: React.FC<Props> = ({ navigation, route }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();
  const insets = useSafeAreaInsets();
  const tipo: CategoryType = route.params?.tipo || 'EXPENSE';

  const [categorias, setCategorias] = useState<Category[]>([]);
  const [categoria, setCategoria] = useState<Category | null>(null);
  const [expresion, setExpresion] = useState('');
  const [fecha, setFecha] = useState(fechaLocalISO());
  const [pagado, setPagado] = useState(true);
  const [nota, setNota] = useState('');
  const [eligiendoFecha, setEligiendoFecha] = useState(false);
  const [eligiendoCategoria, setEligiendoCategoria] = useState(false);
  const [eligiendoFuente, setEligiendoFuente] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const [recientes, setRecientes] = useState<string[]>([]);
  const [guardando, setGuardando] = useState(false);
  /* De donde sale la plata: null = fondos del mes; 'ahorro:<id>' o 'tarjeta:<id>'. */
  const [fuente, setFuente] = useState<string | null>(route.params?.pagadoCon || null);
  const [cuotas, setCuotas] = useState(1);
  const [descartando, setDescartando] = useState(false);
  /* Salir sin preguntar: al anotar o al pasar al formulario completo. */
  const salirSinPreguntar = useRef(false);
  /* Lo que se iba a hacer cuando se pidio salir (atras del navegador, gesto). */
  const salidaPendiente = useRef<any>(null);
  const ahorros = useAhorros();
  const tarjetas = useMisTarjetas();

  useEffect(() => {
    if (!user) return;
    (async () => {
      try {
        const r = await categoryService.getAll({ userId: user.userId, page: 0, size: 200 });
        const lista: Category[] = (r.correct && r.object?.list) || [];
        const deEsteTipo = lista.filter((c) => c.type === tipo && c.active !== false)
          .sort((a, b) => a.name.localeCompare(b.name));
        setCategorias(deEsteTipo);
        /* Sin categoria pedida no se elige ninguna: con la primera por orden
           alfabetico ("Ahorro") un gasto podia quedar anotado como ahorro. */
        setCategoria(deEsteTipo.find((c) => String(c.id) === String(route.params?.categoriaId)) || null);
      } catch {
        setCategorias([]);
      }
    })();
    leerRecientes(tipo).then(setRecientes);
  }, [user, tipo, route.params?.categoriaId]);

  const monto = evaluar(expresion);
  const idsAhorro = useMemo(() => new Set(ahorros.map((a) => a.id)), [ahorros]);
  const esDeposito = tipo === 'EXPENSE' && !!categoria && idsAhorro.has(String(categoria.id));
  const color = tipo === 'INCOME' ? colors.income : esDeposito ? VERDE_AHORRO : colors.expense;
  const colorDe = useMemo(() => {
    const mapa: Record<string, string> = {};
    categorias.forEach((c, i) => { mapa[c.id] = c.color || PALETA[i % PALETA.length]; });
    return mapa;
  }, [categorias]);

  const [claseFuente, idFuente] = (fuente || '').split(':');
  const ahorroElegido = claseFuente === 'ahorro' ? ahorros.find((a) => a.id === idFuente) : undefined;
  const tarjetaElegida = claseFuente === 'tarjeta' ? tarjetas.find((t) => String(t.id) === idFuente) : undefined;
  /* Un deposito o un ingreso siempre sale de lo del mes; y el ahorro no se paga con el mismo ahorro. */
  const puedeElegirFuente = tipo === 'EXPENSE' && !esDeposito;
  const fuenteValida = puedeElegirFuente && (
    (ahorroElegido && String(ahorroElegido.id) !== String(categoria?.id)) || tarjetaElegida);

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
        confirmed: tarjetaElegida && fuenteValida ? true : pagado,
        ...(fuenteValida && ahorroElegido && { fundedFromCategoryId: Number(ahorroElegido.id) }),
        ...(fuenteValida && tarjetaElegida && { cardId: Number(tarjetaElegida.id), installments: cuotas }),
      };
      const r = await movementService.save(datos);
      if (!r.correct) {
        showToast('error', 'No se pudo anotar', r.message);
        return;
      }
      recordarReciente(tipo, String(categoria.id));
      const titulo = tipo === 'INCOME' ? 'Ingreso anotado' : esDeposito ? 'Depositado en el ahorro' : 'Gasto anotado';
      const detalle = tarjetaElegida && fuenteValida && cuotas > 1
        ? `${categoria.name} · ${cuotas} cuotas de ${formatCurrency(Math.floor(monto / cuotas))}`
        : `${categoria.name} · ${formatCurrency(monto)}`;
      showToast('success', titulo, detalle);
      salirSinPreguntar.current = true;
      navigation.goBack();
    } catch (e: any) {
      showToast('error', 'Error', e?.message || 'No se pudo anotar');
    } finally {
      setGuardando(false);
    }
  };

  /* El texto sobre el ambar se calcula: casi negro en el tema oscuro, blanco en el claro. */
  const tintaPrimaria = textoLegibleSobre(colors.primary);
  /* Y el de la cabecera, sobre el rojo o el verde: el blanco sobre #FF6B6B era 2,78:1. */
  const tinta = textoLegibleSobre(color);
  const fondoPildora = tinta === '#FFFFFF' ? 'rgba(0,0,0,0.22)' : 'rgba(255,255,255,0.35)';

  const chip = (texto: string, activo: boolean, onPress: () => void, etiqueta: string, icono?: string) => (
    <TouchableOpacity
      key={texto}
      style={[styles.chip, { backgroundColor: activo ? colors.primary : colors.surfaceLight }]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={etiqueta}
    >
      {!!icono && <Icono nombre={icono} color={activo ? tintaPrimaria : colors.textSecondary} tamano={16} />}
      <AppText variant="caption" style={{ fontWeight: '700', color: activo ? tintaPrimaria : colors.textSecondary }}>{texto}</AppText>
    </TouchableOpacity>
  );

  const circulo = (c: Category, tam = 32) => {
    const fondo = idsAhorro.has(String(c.id)) && tipo === 'EXPENSE' ? VERDE_AHORRO : colorDe[c.id];
    return (
      <View style={[styles.punto, { width: tam, height: tam, borderRadius: tam / 2, backgroundColor: fondo }]}>
        <Icono nombre={c.icon} color={tintaSobre(fondo)} tamano={Math.round(tam * 0.55)} />
      </View>
    );
  };

  const elegir = (c: Category) => {
    setCategoria(c);
    setEligiendoCategoria(false);
    setBusqueda('');
  };

  const hoy = fechaLocalISO();
  const textoFecha = fecha === hoy ? 'Hoy' : fecha === moverDias(hoy, -1) ? 'Ayer' : formatDateShort(fecha);

  const filtradas = categorias.filter((c) => !busqueda.trim() || sinTildes(c.name).includes(sinTildes(busqueda.trim())));
  const gastos = filtradas.filter((c) => !(tipo === 'EXPENSE' && idsAhorro.has(String(c.id))));
  const deAhorro = filtradas.filter((c) => tipo === 'EXPENSE' && idsAhorro.has(String(c.id)));
  const recientesCat = recientes.map((id) => categorias.find((c) => String(c.id) === id)).filter(Boolean) as Category[];
  const vinoElegida = !!route.params?.categoriaId;

  const textoFuente = ahorroElegido && fuenteValida ? `Del ${ahorroElegido.nombre}`
    : tarjetaElegida && fuenteValida ? nombreTarjeta(tarjetaElegida.name) : 'Fondos del mes';
  const saldoTras = ahorroElegido ? ahorroElegido.saldo - monto : 0;
  const cuotaMes = cuotas > 0 ? Math.floor(monto / cuotas) : monto;

  /* Hay algo escrito que se perderia al cerrar. La categoria o el medio que
     llegaron ya elegidos (desde la dona, desde Ahorros) no cuentan. */
  const hayAlgoEscrito = expresion !== '' || nota.trim() !== '' || fecha !== hoy
    || (!!categoria && String(categoria.id) !== String(route.params?.categoriaId ?? ''))
    || (fuente || null) !== (route.params?.pagadoCon || null);
  const hayAlgoRef = useRef(hayAlgoEscrito);
  hayAlgoRef.current = hayAlgoEscrito;

  const salir = () => {
    salirSinPreguntar.current = true;
    setDescartando(false);
    const accion = salidaPendiente.current;
    salidaPendiente.current = null;
    if (accion && navigation.dispatch) navigation.dispatch(accion);
    else navigation.goBack();
  };
  const cerrar = () => {
    if (hayAlgoEscrito && !guardando) setDescartando(true);
    else salir();
  };

  /* El atras del navegador o del celular tambien pregunta. */
  useEffect(() => {
    if (!navigation.addListener) return undefined;
    return navigation.addListener('beforeRemove', (e: any) => {
      if (salirSinPreguntar.current || !hayAlgoRef.current) return;
      e.preventDefault();
      salidaPendiente.current = e.data?.action || null;
      setDescartando(true);
    });
  }, [navigation]);

  /* "Mas": el formulario completo con TODO lo elegido, tambien el medio de
     pago y las cuotas (antes volvia a "Fondos del mes" y sin cuotas). */
  const abrirFormularioCompleto = () => {
    salirSinPreguntar.current = true;
    navigation.replace('MovementFormModal', {
      prefill: {
        amount: monto || undefined, categoryId: categoria?.id, type: tipo, date: fecha,
        description: nota.trim() || undefined,
        confirmed: tarjetaElegida && fuenteValida ? true : pagado,
        ...(fuenteValida && ahorroElegido && { fundedFromCategoryId: ahorroElegido.id }),
        ...(fuenteValida && tarjetaElegida && { cardId: String(tarjetaElegida.id), installments: cuotas }),
      },
    });
  };

  const hojaAbierta = eligiendoCategoria || eligiendoFuente || eligiendoFecha || descartando;
  useTecladoFisico({
    activo: true,
    onTecla: (t) => { if (!hojaAbierta) setExpresion((e) => pulsar(e, t)); },
    onListo: () => { if (!hojaAbierta) guardar(); },
    onCerrar: () => {
      if (descartando) setDescartando(false);
      else if (eligiendoCategoria) setEligiendoCategoria(false);
      else if (eligiendoFuente) setEligiendoFuente(false);
      else if (eligiendoFecha) setEligiendoFecha(false);
      else cerrar();
    },
  });

  const fila = (c: Category) => {
    const saldo = ahorros.find((a) => a.id === String(c.id))?.saldo;
    return (
      <TouchableOpacity key={c.id} style={[styles.fila, { borderBottomColor: colors.border }]} onPress={() => elegir(c)}
        accessibilityRole="button" accessibilityLabel={`Categoría ${c.name}${saldo !== undefined ? `, hay ${formatCurrency(saldo)}` : ''}`}>
        {circulo(c)}
        <AppText variant="body" style={{ flex: 1, fontWeight: categoria?.id === c.id ? '800' : '500' }}>{c.name}</AppText>
        {saldo !== undefined && (
          <AppText variant="caption" style={{ color: VERDE_AHORRO, fontWeight: '700' }}>{`hay ${formatCurrency(saldo)}`}</AppText>
        )}
        {categoria?.id === c.id && <Icono nombre="check" color={colors.primary} />}
      </TouchableOpacity>
    );
  };

  const opcionFuente = (clave: string | null, titulo: string, detalle: string, icono: string, tono: string) => {
    const activa = (fuente || null) === clave || (clave === null && !fuenteValida);
    return (
      <TouchableOpacity key={clave || 'mes'} style={[styles.fila, { borderBottomColor: colors.border }]}
        onPress={() => { setFuente(clave); if (!clave || !clave.startsWith('tarjeta')) setCuotas(1); setEligiendoFuente(false); }}
        accessibilityRole="radio" accessibilityState={{ selected: activa }} accessibilityLabel={`${titulo}. ${detalle}`}>
        <View style={[styles.punto, { width: 32, height: 32, borderRadius: 16, backgroundColor: tono }]}>
          <Icono nombre={icono} color={tintaSobre(tono)} tamano={18} />
        </View>
        <View style={{ flex: 1 }}>
          <AppText variant="body" style={{ fontWeight: activa ? '800' : '600' }}>{titulo}</AppText>
          <AppText variant="caption" color={colors.textSecondary}>{detalle}</AppText>
        </View>
        {activa && <Icono nombre="check" color={colors.primary} />}
      </TouchableOpacity>
    );
  };

  return (
    <View style={[styles.pantalla, { backgroundColor: colors.background, paddingBottom: insets.bottom }]}>
      <View style={[styles.monto, { backgroundColor: color, paddingTop: insets.top + 8 }]}>
        <View style={styles.filaMonto}>
          <TouchableOpacity onPress={cerrar} style={styles.cerrar} accessibilityRole="button" accessibilityLabel="Cerrar sin anotar">
            <Icono nombre="close" color={tinta} />
          </TouchableOpacity>
          <AppText variant="caption" style={{ color: tinta, fontWeight: '700', flex: 1 }}>
            {tipo === 'INCOME' ? 'Ingreso' : esDeposito ? `Depositar en ${categoria?.name}` : 'Gasto'}
          </AppText>
          <TouchableOpacity onPress={() => setEligiendoFecha(true)} style={[styles.fechaCabecera, { backgroundColor: fondoPildora }]}
            accessibilityRole="button" accessibilityLabel={`Fecha ${textoFecha}. Cambiar la fecha`}>
            <Icono nombre="calendar" color={tinta} tamano={16} />
            <AppText variant="caption" style={{ color: tinta, fontWeight: '800' }}>{textoFecha} ▾</AppText>
          </TouchableOpacity>
        </View>
        <AppText variant="title" style={{ color: tinta, textAlign: 'right' }} accessibilityLabel={`Monto ${formatCurrency(monto)}`}>
          {mostrar(expresion)}
        </AppText>
        {tieneOperacion(expresion) && (
          <AppText variant="body" style={{ color: tinta, textAlign: 'right' }}>= {formatCurrency(monto)}</AppText>
        )}
      </View>

      <ScrollView style={{ flexGrow: 0, flexShrink: 1 }} contentContainerStyle={styles.campos} keyboardShouldPersistTaps="handled">
        <AppText variant="caption" color={colors.textSecondary}>Categoría</AppText>
        <TouchableOpacity style={[styles.campo, { backgroundColor: colors.surfaceLight, borderColor: categoria ? colors.border : colors.primary }]}
          onPress={() => setEligiendoCategoria(true)} accessibilityRole="button"
          accessibilityLabel={categoria ? `Categoría ${categoria.name}. Cambiar la categoría` : 'Elegir la categoría'}>
          {categoria ? circulo(categoria, 28) : <Icono nombre="list" color={colors.textMuted} />}
          <AppText variant="body" style={{ flex: 1, fontWeight: '700', color: categoria ? colors.text : colors.textMuted }}>
            {categoria ? categoria.name : 'Elige la categoría'}
          </AppText>
          <AppText variant="body" color={colors.textMuted}>▾</AppText>
        </TouchableOpacity>
        {!vinoElegida && recientesCat.length > 0 && (
          <View style={styles.recientes}>
            {recientesCat.slice(0, 3).map((c) => (
              <TouchableOpacity key={c.id} style={[styles.chip, { backgroundColor: categoria?.id === c.id ? colors.primary : colors.surfaceLight }]}
                onPress={() => setCategoria(c)} accessibilityRole="button" accessibilityLabel={`Reciente ${c.name}`}>
                {circulo(c, 22)}
                <AppText variant="caption" style={{ fontWeight: '700', color: categoria?.id === c.id ? tintaPrimaria : colors.textSecondary }}>{c.name}</AppText>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {puedeElegirFuente && (ahorros.length > 0 || tarjetas.length > 0) && (
          <>
            <AppText variant="caption" color={colors.textSecondary}>Pagado con</AppText>
            <TouchableOpacity style={[styles.campo, { backgroundColor: colors.surfaceLight,
              borderColor: ahorroElegido && fuenteValida ? VERDE_AHORRO : colors.border }]}
              onPress={() => setEligiendoFuente(true)} accessibilityRole="button" accessibilityLabel={`Pagado con ${textoFuente}. Cambiar`}>
              <Icono nombre={ahorroElegido && fuenteValida ? 'piggy-bank' : tarjetaElegida && fuenteValida ? 'credit-card' : 'wallet'}
                color={ahorroElegido && fuenteValida ? VERDE_AHORRO : colors.textSecondary} />
              <AppText variant="body" style={{ flex: 1, fontWeight: '700' }}>{textoFuente}</AppText>
              <AppText variant="body" color={colors.textMuted}>▾</AppText>
            </TouchableOpacity>
            {ahorroElegido && fuenteValida && (
              <AppText variant="caption" style={{ color: saldoTras < 0 ? colors.warning : VERDE_AHORRO }}>
                {saldoTras < 0
                  ? `Ojo: en ${ahorroElegido.nombre} hay ${formatCurrency(ahorroElegido.saldo)}; quedaría en ${formatCurrency(saldoTras)}.`
                  : `No baja lo libre del mes. ${ahorroElegido.nombre} queda en ${formatCurrency(saldoTras)}.`}
              </AppText>
            )}
            {tarjetaElegida && fuenteValida && (
              <View style={{ gap: 6 }}>
                <View style={styles.recientes}>
                  <AppText variant="caption" color={colors.textSecondary}>Cuotas</AppText>
                  {CUOTAS_RAPIDAS.map((n) => (
                    <TouchableOpacity key={n} style={[styles.cuota, { backgroundColor: cuotas === n ? colors.primary : colors.surfaceLight }]}
                      onPress={() => setCuotas(n)} accessibilityRole="radio" accessibilityState={{ selected: cuotas === n }}
                      accessibilityLabel={`${n} ${n === 1 ? 'cuota' : 'cuotas'}`}>
                      <AppText variant="caption" style={{ fontWeight: '800', color: cuotas === n ? tintaPrimaria : colors.textSecondary }}>{n}</AppText>
                    </TouchableOpacity>
                  ))}
                </View>
                <AppText variant="caption" color={colors.textSecondary}>
                  {cuotas === 1
                    ? 'A 1 cuota: cuenta completo este mes y queda en la tarjeta hasta que la pagues.'
                    : `${cuotas} cuotas de ${formatCurrency(cuotaMes)} al mes; la primera cuenta este mes.`}
                </AppText>
              </View>
            )}
          </>
        )}

        {/* La nota siempre a la vista: es lo que dice en que se gasto. */}
        <TextInput
          value={nota}
          onChangeText={setNota}
          placeholder="Nota: ¿en qué fue? (ej: almuerzo con Samy)"
          placeholderTextColor={colors.textMuted}
          style={[styles.nota, { backgroundColor: colors.surfaceLight, color: colors.text }]}
          accessibilityLabel="Nota del movimiento"
          maxLength={255}
        />

        <View style={styles.recientes}>
          {!(tarjetaElegida && fuenteValida) && (
            <View style={[styles.segmento, { backgroundColor: colors.surfaceLight }]}>
              {[true, false].map((v) => (
                <TouchableOpacity key={String(v)} style={[styles.opcion, pagado === v && { backgroundColor: colors.primary }]}
                  onPress={() => setPagado(v)} accessibilityRole="radio" accessibilityState={{ selected: pagado === v }}
                  accessibilityLabel={v ? 'Ya pagado' : 'Por pagar'}>
                  <AppText variant="caption" style={{ fontWeight: '700', color: pagado === v ? tintaPrimaria : colors.textSecondary }}>
                    {v ? 'Ya pagado' : 'Por pagar'}
                  </AppText>
                </TouchableOpacity>
              ))}
            </View>
          )}
          {chip('Más', false, abrirFormularioCompleto, 'Más opciones: abrir el formulario completo con lo escrito', 'more')}
        </View>
      </ScrollView>

      <View style={{ flex: 1 }} />
      <Teclado
        expresion={expresion}
        onCambio={setExpresion}
        onListo={guardar}
        textoListo={guardando ? 'Anotando…' : monto > 0 && !categoria ? 'Anotar · elige la categoría'
          : `${esDeposito ? 'Depositar' : 'Anotar'}${monto > 0 ? ` ${formatCurrency(monto)}` : ''}`}
        listoDeshabilitado={monto <= 0 || !categoria || guardando}
      />

      <Modal visible={eligiendoCategoria} transparent animationType="slide" onRequestClose={() => setEligiendoCategoria(false)}>
        <View style={styles.fondo}>
          <TouchableOpacity style={{ flex: 1 }} onPress={() => setEligiendoCategoria(false)} accessibilityRole="button" accessibilityLabel="Cerrar la lista" />
          <View style={[styles.hojaAlta, { backgroundColor: colors.surface, paddingBottom: insets.bottom + 12 }]}>
            <AppText variant="subtitle">¿En qué?</AppText>
            <TextInput
              value={busqueda}
              onChangeText={setBusqueda}
              placeholder="Buscar categoría"
              placeholderTextColor={colors.textMuted}
              style={[styles.nota, { marginHorizontal: 0, backgroundColor: colors.surfaceLight, color: colors.text }]}
              accessibilityLabel="Buscar categoría"
            />
            <ScrollView keyboardShouldPersistTaps="handled">
              {!busqueda && recientesCat.length > 0 && (
                <>
                  <AppText variant="caption" color={colors.textSecondary} style={styles.seccion}>Recientes</AppText>
                  {recientesCat.slice(0, 5).map((c) => fila(c))}
                </>
              )}
              {gastos.length > 0 && (
                <AppText variant="caption" color={colors.textSecondary} style={styles.seccion}>
                  {tipo === 'INCOME' ? 'Ingresos' : 'Gastos'}
                </AppText>
              )}
              {gastos.map((c) => fila(c))}
              {deAhorro.length > 0 && (
                <AppText variant="caption" style={[styles.seccion, { color: VERDE_AHORRO }]}>Ahorros (meter plata)</AppText>
              )}
              {deAhorro.map((c) => fila(c))}
              {filtradas.length === 0 && (
                <AppText variant="body" color={colors.textMuted} style={{ padding: 16, textAlign: 'center' }}>
                  No hay categorías con ese nombre
                </AppText>
              )}
            </ScrollView>
          </View>
        </View>
      </Modal>

      <Modal visible={eligiendoFuente} transparent animationType="slide" onRequestClose={() => setEligiendoFuente(false)}>
        <View style={styles.fondo}>
          <TouchableOpacity style={{ flex: 1 }} onPress={() => setEligiendoFuente(false)} accessibilityRole="button" accessibilityLabel="Cerrar" />
          <View style={[styles.hojaAlta, { backgroundColor: colors.surface, paddingBottom: insets.bottom + 12 }]}>
            <AppText variant="subtitle">¿Con qué se pagó?</AppText>
            <ScrollView>
              {opcionFuente(null, 'Fondos del mes', 'Lo del salario: baja lo libre para gastar', 'wallet', colors.primary)}
              {ahorros.filter((a) => a.id !== String(categoria?.id)).length > 0 && (
                <AppText variant="caption" style={[styles.seccion, { color: VERDE_AHORRO }]}>Ahorros</AppText>
              )}
              {ahorros.filter((a) => a.id !== String(categoria?.id)).map((a) =>
                opcionFuente(`ahorro:${a.id}`, a.nombre, `Hay ${formatCurrency(a.saldo)} · no baja lo del mes`, 'piggy-bank', VERDE_AHORRO))}
              {tarjetas.length > 0 && (
                <AppText variant="caption" color={colors.textSecondary} style={styles.seccion}>Tarjetas de crédito</AppText>
              )}
              {tarjetas.map((t) =>
                opcionFuente(`tarjeta:${t.id}`, t.name, t.owed > 0 ? `Debes ${formatCurrency(t.owed)}` : 'Al día',
                  'credit-card', t.color || colors.textSecondary))}
            </ScrollView>
          </View>
        </View>
      </Modal>

      <Calendario visible={eligiendoFecha} fecha={fecha} onElegir={setFecha} onCerrar={() => setEligiendoFecha(false)} />

      <ConfirmarDescarte visible={descartando} onSeguir={() => { salidaPendiente.current = null; setDescartando(false); }} onDescartar={salir} />
    </View>
  );
};

const styles = StyleSheet.create({
  pantalla: { flex: 1 },
  monto: { paddingHorizontal: 16, paddingBottom: 14, gap: 2 },
  filaMonto: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  cerrar: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', marginLeft: -10 },
  fechaCabecera: { minHeight: 44, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', gap: 6, borderRadius: 22 },
  campos: { paddingHorizontal: 16, paddingTop: 12, gap: 8 },
  campo: { flexDirection: 'row', alignItems: 'center', gap: 10, minHeight: 52, paddingHorizontal: 12, borderRadius: 14, borderWidth: 1 },
  recientes: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 8 },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 6, minHeight: 44, paddingHorizontal: 12, borderRadius: 22 },
  cuota: { minWidth: 44, minHeight: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 10 },
  segmento: { flexDirection: 'row', borderRadius: 24, padding: 2 },
  opcion: { minHeight: 44, paddingHorizontal: 12, borderRadius: 22, justifyContent: 'center' },
  punto: { alignItems: 'center', justifyContent: 'center' },
  nota: { marginHorizontal: 0, padding: 12, borderRadius: 12, fontSize: 16 },
  fondo: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)' },
  hojaAlta: { maxHeight: '80%', padding: 16, gap: 10, borderTopLeftRadius: 24, borderTopRightRadius: 24 },
  fila: { flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 52, paddingVertical: 8, borderBottomWidth: StyleSheet.hairlineWidth },
  seccion: { marginTop: 12, marginBottom: 4, fontWeight: '800', textTransform: 'uppercase', letterSpacing: 0.5 },
});

export default AnotarScreen;
