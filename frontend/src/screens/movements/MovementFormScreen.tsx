import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useAhorros, useMisTarjetas } from '../../hooks/useAhorros';
import { View, StyleSheet, ScrollView, TouchableOpacity, Modal } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import CapsuleToggle from '../../components/molecules/CapsuleToggle';
import Calendario, { fechaLarga, moverDias } from '../../components/molecules/Calendario';
import ConfirmarDescarte from '../../components/molecules/ConfirmarDescarte';
import { useTecladoFisico } from '../../components/molecules/Teclado';
import Icono from '../../components/atoms/Icono';
import { tintaSobre } from '../../utils/tinta';
import { nombreTarjeta, conElAhorro } from '../../utils/medioDePago';
import { spacing } from '../../theme';
import { movementService } from '../../services/movementService';
import { categoryService } from '../../services/categoryService';
import { Movement, Category, CategoryType } from '../../types';
import { formatCurrency, fechaLocalISO, formatDateShort, parseMonto } from '../../utils/format';
import { confirmarBorrado } from '../../utils/confirmar';
import { useAccionUnica } from '../../hooks/useAccionUnica';
import CirculoCategoria from '../../components/atoms/CirculoCategoria';
import { validateAmount, validateDate, validateCategory, validateAll } from '../../utils/validators';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type RootStackParamList = {
  MainTabs: undefined;
  MovementFormModal: {
    movement?: Movement;
    parentMovement?: Movement;
    /* Lo que ya se escribio en el teclado rapido ("Mas opciones"): no se
       vuelve a pedir. */
    prefill?: {
      amount?: number; categoryId?: string; type?: CategoryType; date?: string; description?: string; confirmed?: boolean;
      /* El medio de pago elegido en el teclado rapido: no se pierde al pasar aqui. */
      fundedFromCategoryId?: string | number; cardId?: string | number; installments?: number;
    };
  };
};

type Props = NativeStackScreenProps<RootStackParamList, 'MovementFormModal'>;

const MovementFormScreen: React.FC<Props> = ({ navigation, route }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();
  const existing = route.params?.movement;
  const parentMovement = route.params?.parentMovement;
  const prefill = route.params?.prefill;
  const isEdit = !!existing;

  const [allCategories, setAllCategories] = useState<Category[]>([]);
  const [movementType, setMovementType] = useState<CategoryType>(existing?.categoryType || prefill?.type || 'EXPENSE');
  const [selectedCategory, setSelectedCategory] = useState<Category | null>(null);
  const [amount, setAmount] = useState(existing ? String(existing.amount) : prefill?.amount ? String(prefill.amount) : '');
  const [description, setDescription] = useState(existing?.description || prefill?.description || '');
  const [date, setDate] = useState(existing?.date || prefill?.date || fechaLocalISO());
  const [confirmed, setConfirmed] = useState(existing?.confirmed ?? prefill?.confirmed ?? true);
  /* De donde salio la plata: del mes (null) o de un ahorro. */
  /* De donde salio la plata: null = fondos del mes; 'ahorro:<id>' o 'tarjeta:<id>'. */
  const fuenteInicial = existing
    ? (existing.fundedFromCategoryId ? `ahorro:${existing.fundedFromCategoryId}` : existing.cardId ? `tarjeta:${existing.cardId}` : null)
    : prefill?.fundedFromCategoryId ? `ahorro:${prefill.fundedFromCategoryId}`
      : prefill?.cardId ? `tarjeta:${prefill.cardId}` : null;
  const [fuente, setFuente] = useState<string | null>(fuenteInicial);
  const [cuotas, setCuotas] = useState(!existing && prefill?.installments && prefill.installments > 0 ? prefill.installments : 1);
  const [eligiendoFuente, setEligiendoFuente] = useState(false);
  const [eligiendoFecha, setEligiendoFecha] = useState(false);
  const [descartando, setDescartando] = useState(false);
  const salirSinPreguntar = useRef(false);
  const salidaPendiente = useRef<any>(null);
  const ahorros = useAhorros();
  const tarjetas = useMisTarjetas();
  const [claseFuente, idFuente] = (fuente || '').split(':');
  const delAhorro = claseFuente === 'ahorro' ? idFuente : null;
  const conTarjeta = claseFuente === 'tarjeta' ? idFuente : null;

  /* Lo que habia al abrir: si algo cambio, cerrar pregunta antes de perderlo.
     Lo que llego escrito desde el teclado rapido ("Mas") tambien cuenta. */
  const inicial = useRef({
    amount, description, date, confirmed, fuente: fuenteInicial, cuotas,
    categoryId: String(existing?.categoryId || parentMovement?.categoryId || prefill?.categoryId || ''),
  }).current;
  const trajoAlgo = !existing && !!prefill && (!!prefill.amount || !!prefill.categoryId || !!prefill.description);
  const hayCambios = trajoAlgo || amount !== inicial.amount || description !== inicial.description
    || date !== inicial.date || confirmed !== inicial.confirmed || (fuente || null) !== (inicial.fuente || null)
    || cuotas !== inicial.cuotas
    || (!!selectedCategory && String(selectedCategory.id) !== inicial.categoryId);
  const hayCambiosRef = useRef(hayCambios);
  hayCambiosRef.current = hayCambios;

  useEffect(() => {
    if (!navigation.addListener) return undefined;
    return navigation.addListener('beforeRemove', (e: any) => {
      if (salirSinPreguntar.current || !hayCambiosRef.current) return;
      e.preventDefault();
      salidaPendiente.current = e.data?.action || null;
      setDescartando(true);
    });
  }, [navigation]);

  const salir = () => {
    salirSinPreguntar.current = true;
    setDescartando(false);
    const accion = salidaPendiente.current;
    salidaPendiente.current = null;
    if (accion && navigation.dispatch) navigation.dispatch(accion);
    else navigation.goBack();
  };

  /* Escape en el computador: cerrar, con la misma pregunta. */
  useTecladoFisico({
    activo: true,
    onCerrar: () => {
      if (descartando) setDescartando(false);
      else if (eligiendoFuente) setEligiendoFuente(false);
      else if (eligiendoFecha) setEligiendoFecha(false);
      else if (hayCambios) setDescartando(true);
      else salir();
    },
  });

  useEffect(() => {
    const loadCategories = async () => {
      if (!user) return;
      try {
        const res = await categoryService.getAll({ userId: user.userId, page: 0, size: 100 });
        if (res.correct && res.object) {
          const list = res.object.list || [];
          setAllCategories(list);
          /* El sub-gasto va en la categoria de su padre: el formulario la
             oculta, pero la validacion la exigia y nunca se tomaba, asi que
             guardar un sub-gasto siempre fallaba. */
          const categoriaInicial = existing?.categoryId || parentMovement?.categoryId || prefill?.categoryId;
          if (categoriaInicial) {
            const found = list.find((c: Category) => String(c.id) === String(categoriaInicial));
            if (found) {
              setSelectedCategory(found);
              setMovementType(found.type);
            }
          }
        }
      } catch {
        // silent
      }
    };
    loadCategories();
  }, [user, existing, parentMovement, prefill]);

  const filteredCategories = allCategories.filter(c => c.type === movementType);

  const handleTypeChange = (newType: string) => {
    const t = newType as CategoryType;
    setMovementType(t);
    if (selectedCategory && selectedCategory.type !== t) {
      setSelectedCategory(null);
    }
  };

  // Borrar un movimiento se ejecutaba al primer toque, sin vuelta atras.
  const handleDelete = () =>
    confirmarBorrado(
      existing?.description || 'este movimiento',
      () => { borrar(); },
    );

  const borrarMovimiento = useCallback(async () => {
    if (!existing) return;
    try {
      const res = await movementService.delete(String(existing.id));
      if (res.correct) {
        showToast('success', 'Eliminado');
        salirSinPreguntar.current = true;
        navigation.goBack();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo eliminar');
    }
  }, [existing, navigation, showToast]);

  const { ejecutando: borrando, ejecutar: borrar } = useAccionUnica(borrarMovimiento);

  const guardarMovimiento = useCallback(async () => {
    const error = validateAll(
      validateCategory(selectedCategory?.id ? String(selectedCategory.id) : null),
      validateAmount(amount),
      validateDate(date),
    );
    if (error) {
      showToast('warning', 'Revisa los datos', error);
      return;
    }
    try {
      const data: Partial<Movement> = {
        ...(isEdit && { id: existing.id }),
        userId: user?.userId,
        categoryId: selectedCategory!.id,
        amount: parseMonto(amount),
        description: description.trim() || undefined,
        date,
        confirmed,
        ...(movementType === 'EXPENSE' && !parentMovement && {
          fundedFromCategoryId: delAhorro && delAhorro !== String(selectedCategory!.id) ? Number(delAhorro) : (isEdit ? 0 : undefined),
          cardId: conTarjeta ? Number(conTarjeta) : (isEdit ? 0 : undefined),
          ...(!isEdit && conTarjeta && cuotas > 1 && { installments: cuotas }),
        }),
        ...(parentMovement && { parentMovementId: parentMovement.id }),
      };
      const res = await movementService.save(data);
      if (res.correct) {
        showToast('success', isEdit ? 'Actualizado' : parentMovement ? 'Sub-gasto registrado' : 'Guardado');
        salirSinPreguntar.current = true;
        navigation.goBack();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo guardar');
    }
  }, [selectedCategory, amount, date, description, confirmed, delAhorro, conTarjeta, cuotas, movementType, isEdit, existing, parentMovement, user, navigation, showToast]);

  const { ejecutando: guardando, ejecutar: handleSave } = useAccionUnica(guardarMovimiento);

  const typeColor = movementType === 'INCOME' ? colors.income : colors.expense;

  const hoy = fechaLocalISO();
  const textoFecha = /^\d{4}-\d{2}-\d{2}$/.test(date)
    ? `${date === hoy ? 'Hoy · ' : date === moverDias(hoy, -1) ? 'Ayer · ' : ''}${fechaLarga(date)}`
    : date || 'Elige la fecha';
  const ahorroElegido = delAhorro ? ahorros.find((a) => a.id === delAhorro) : undefined;
  const tarjetaElegida = conTarjeta ? tarjetas.find((t) => String(t.id) === conTarjeta) : undefined;
  /* Una tarjeta que no es mia (la de la pareja) no esta en la lista: se dice "Con tarjeta". */
  const textoFuente = delAhorro ? (() => { const t = conElAhorro(ahorroElegido?.nombre); return t.charAt(0).toUpperCase() + t.slice(1); })()
    : conTarjeta ? (tarjetaElegida ? nombreTarjeta(tarjetaElegida.name) : 'Con tarjeta') : 'Fondos del mes';

  const opcionFuente = (clave: string | null, titulo: string, detalle: string, icono: string, tono: string) => {
    const activa = (fuente || null) === clave;
    return (
      <TouchableOpacity key={clave || 'mes'} style={[styles.opcionFuente, { borderBottomColor: colors.border }]}
        onPress={() => { setFuente(clave); if (!clave || !clave.startsWith('tarjeta')) setCuotas(1); setEligiendoFuente(false); }}
        accessibilityRole="radio" accessibilityState={{ selected: activa }} accessibilityLabel={`${titulo}. ${detalle}`}>
        <View style={[styles.puntoFuente, { backgroundColor: tono }]}>
          <Icono nombre={icono} color={tintaSobre(tono)} tamano={18} />
        </View>
        <View style={{ flex: 1, minWidth: 0 }}>
          <AppText variant="body" style={{ fontWeight: activa ? '800' : '600' }}>{titulo}</AppText>
          <AppText variant="caption" color={colors.textSecondary}>{detalle}</AppText>
        </View>
        {activa && <Icono nombre="check" color={colors.primary} />}
      </TouchableOpacity>
    );
  };

  return (
    <>
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      {/* Info de sub-gasto. Las dos lineas son una sola idea: de que gasto
          cuelga esto y cuanto habia presupuestado. Sueltas, el lector las lee
          como dos datos sin relacion y no se sabe de que movimiento habla. */}
      {parentMovement && (
        <View
          style={[styles.parentInfo, { borderColor: colors.primary + '40' }]}
          accessible
          accessibilityLabel={`Sub-gasto de ${parentMovement.description || parentMovement.categoryName}. Presupuesto ${formatCurrency(parentMovement.amount)}, categoría ${parentMovement.categoryName}`}
        >
          <AppText variant="caption" color={colors.primary} style={{ fontWeight: '700' }}>
            Sub-gasto de: {parentMovement.description || parentMovement.categoryName}
          </AppText>
          <AppText variant="caption" color={colors.textSecondary}>
            Presupuesto: {formatCurrency(parentMovement.amount)} | Categoria: {parentMovement.categoryName}
          </AppText>
        </View>
      )}

      {/* Monto prominente arriba */}
      <View style={[styles.amountCard, { backgroundColor: colors.surface, borderColor: typeColor + '40' }]}>
        <AppText variant="caption" color={colors.textSecondary} style={styles.amountLabel}>
          MONTO
        </AppText>
        <View style={styles.amountRow}>
          <AppText style={[styles.currencySign, { color: typeColor }]}>
            {movementType === 'INCOME' ? '+' : '-'} $
          </AppText>
          <Input
            label=""
            accessibilityLabel="Monto del movimiento"
            value={amount}
            onChangeText={setAmount}
            placeholder="0"
            keyboardType="numeric"
          />
        </View>
      </View>

      {/* Toggle Ingreso / Gasto - oculto en sub-gastos */}
      {!parentMovement && (
        <CapsuleToggle
          options={[
            { label: 'Gasto', value: 'EXPENSE', color: colors.expense },
            { label: 'Ingreso', value: 'INCOME', color: colors.income },
          ]}
          selected={movementType}
          onChange={handleTypeChange}
        />
      )}

      {/* De donde salio la plata: la reparacion del carro pagada con el ahorro
          es gasto del carro, pero no de lo del mes. */}
      {movementType === 'EXPENSE' && !parentMovement && (ahorros.length > 0 || tarjetas.length > 0) && (
        <View style={styles.statusSection}>
          <AppText variant="label" style={styles.sectionLabel}>¿Con qué se pagó?</AppText>
          <TouchableOpacity style={[styles.campo, { backgroundColor: colors.surface, borderColor: delAhorro ? VERDE_AHORRO : colors.border }]}
            onPress={() => setEligiendoFuente(true)} accessibilityRole="button" accessibilityLabel={`Pagado con ${textoFuente}. Cambiar`}>
            <Icono nombre={delAhorro ? 'piggy-bank' : conTarjeta ? 'credit-card' : 'wallet'} color={delAhorro ? VERDE_AHORRO : colors.textSecondary} />
            <AppText variant="body" style={{ flex: 1, fontWeight: '700' }} numberOfLines={2}>{textoFuente}</AppText>
            <AppText variant="body" color={colors.textMuted}>▾</AppText>
          </TouchableOpacity>
          {!!conTarjeta && !isEdit && (
            <View style={[styles.statusRow, { flexWrap: 'wrap', marginTop: 8 }]}>
              {[1, 3, 6, 12, 24, 36].map((n) => (
                <TouchableOpacity key={n}
                  style={[styles.statusChip, styles.cuota, { backgroundColor: cuotas === n ? colors.primary + '20' : 'transparent', borderColor: cuotas === n ? colors.primary : colors.border }]}
                  onPress={() => setCuotas(n)} accessibilityRole="radio" accessibilityState={{ selected: cuotas === n }}
                  accessibilityLabel={`${n} ${n === 1 ? 'cuota' : 'cuotas'}`}>
                  <AppText variant="caption" style={{ color: cuotas === n ? colors.primary : colors.textMuted, fontWeight: '700' }}>{n}</AppText>
                </TouchableOpacity>
              ))}
            </View>
          )}
          {!!conTarjeta && isEdit && (existing?.installmentTotal || 1) > 1 && (
            <AppText variant="caption" color={colors.textMuted}>
              {`Cuota ${existing?.installmentNumber} de ${existing?.installmentTotal}. Para cambiar las cuotas, borra la compra y anótala de nuevo.`}
            </AppText>
          )}
        </View>
      )}

      {/* Estado: Pagado / Por pagar - oculto en sub-gastos (siempre confirmado) */}
      {!parentMovement && <View style={styles.statusSection}>
        <AppText variant="label" style={styles.sectionLabel}>Estado</AppText>
        <View style={styles.statusRow}>
          <TouchableOpacity
            style={[styles.statusChip, {
              backgroundColor: confirmed ? colors.income + '20' : 'transparent',
              borderColor: confirmed ? colors.income : colors.border,
            }]}
            onPress={() => setConfirmed(true)}
            accessibilityRole="radio"
            accessibilityState={{ selected: confirmed }}
            accessibilityLabel="Ya está pagado"
          >
            <AppText style={[styles.statusDot, { backgroundColor: colors.income }]}>{' '}</AppText>
            <AppText variant="caption" style={{ color: confirmed ? colors.income : colors.textMuted, fontWeight: confirmed ? '700' : '400' }}>
              Pagado
            </AppText>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.statusChip, {
              backgroundColor: !confirmed ? colors.warning + '20' : 'transparent',
              borderColor: !confirmed ? colors.warning : colors.border,
            }]}
            onPress={() => setConfirmed(false)}
            accessibilityRole="radio"
            accessibilityState={{ selected: !confirmed }}
            accessibilityLabel="Todavía está por pagar"
          >
            <AppText style={[styles.statusDot, { backgroundColor: colors.warning }]}>{' '}</AppText>
            <AppText variant="caption" style={{ color: !confirmed ? colors.warning : colors.textMuted, fontWeight: !confirmed ? '700' : '400' }}>
              Por pagar
            </AppText>
          </TouchableOpacity>
        </View>
      </View>}

      {/* Categorías - oculto en sub-gastos (hereda del padre) */}
      {!parentMovement && (() => {
        const shared = filteredCategories.filter((c: any) => c.shared || c.householdId);
        const personal = filteredCategories.filter((c: any) => !c.shared && !c.householdId);
        /* El grupo va en la etiqueta porque una categoria conjunta y una
           personal pueden llamarse igual: sin el, se oyen dos "Mercado"
           iguales en la misma pantalla y no hay forma de distinguirlas. */
        const renderCat = (cat: any, grupo: string) => {
          const isSelected = String(cat.id) === String(selectedCategory?.id);
          return (
            <TouchableOpacity
              key={String(cat.id)}
              style={[styles.catChip, {
                backgroundColor: isSelected ? (cat.color || colors.primary) : colors.surface,
                borderColor: isSelected ? (cat.color || colors.primary) : colors.border,
              }]}
              onPress={() => setSelectedCategory(cat)}
              activeOpacity={0.7}
              accessibilityRole="radio"
              accessibilityState={{ selected: isSelected }}
              accessibilityLabel={`Categoría ${grupo} ${cat.name}`}
            >
              <CirculoCategoria icono={cat.icon} color={cat.color} tamano={28} />
              <AppText variant="caption" style={{
                color: isSelected ? '#FFF' : colors.text,
                fontWeight: isSelected ? '700' : '400',
              }}>
                {cat.name}
              </AppText>
            </TouchableOpacity>
          );
        };
        return (
          <>
            {shared.length > 0 && (
              <>
                <AppText variant="label" style={styles.sectionLabel}>Conjuntas</AppText>
                <View style={styles.catGrid}>{shared.map((c: any) => renderCat(c, 'conjunta'))}</View>
              </>
            )}
            {personal.length > 0 && (
              <>
                <AppText variant="label" style={styles.sectionLabel}>Personales</AppText>
                <View style={styles.catGrid}>{personal.map((c: any) => renderCat(c, 'personal'))}</View>
              </>
            )}
            {shared.length === 0 && personal.length === 0 && null}
          </>
        );
      })()}
      {!parentMovement && filteredCategories.length === 0 && (
        <AppText variant="caption" color={colors.textMuted} style={{ padding: spacing.sm }}>
          Sin categorias de {movementType === 'INCOME' ? 'ingreso' : 'gasto'}
        </AppText>
      )}

      <AppText variant="label" style={styles.sectionLabel}>Fecha</AppText>
      <TouchableOpacity style={[styles.campo, { backgroundColor: colors.surface, borderColor: colors.border }]}
        onPress={() => setEligiendoFecha(true)} accessibilityRole="button"
        accessibilityLabel={`Fecha ${textoFecha}. Cambiar la fecha`}>
        <Icono nombre="calendar" color={colors.textSecondary} />
        <AppText variant="body" style={{ flex: 1, fontWeight: '700' }}>{textoFecha}</AppText>
        <AppText variant="body" color={colors.textMuted}>▾</AppText>
      </TouchableOpacity>
      <Input label="Nota" value={description} onChangeText={setDescription} placeholder="Descripcion (opcional)" multiline />

      {/* Acciones */}
      <Button title={isEdit ? 'Actualizar' : parentMovement ? 'Registrar sub-gasto' : 'Guardar'} onPress={handleSave} loading={guardando} disabled={borrando} size="large" style={styles.saveBtn} />
      {isEdit && (
        <Button
          title="Eliminar"
          accessibilityLabel={`Eliminar el movimiento ${existing?.description || existing?.categoryName || ''}`.trim()}
          onPress={handleDelete}
          loading={borrando}
          disabled={guardando}
          variant="danger"
          style={styles.deleteBtn}
        />
      )}
    </ScrollView>

    <Modal visible={eligiendoFuente} transparent animationType="slide" onRequestClose={() => setEligiendoFuente(false)}>
      <View style={styles.fondo}>
        <TouchableOpacity style={{ flex: 1 }} onPress={() => setEligiendoFuente(false)} accessibilityRole="button" accessibilityLabel="Cerrar" />
        <View style={[styles.hoja, { backgroundColor: colors.surface, paddingBottom: 28 }]}>
          <AppText variant="subtitle">¿Con qué se pagó?</AppText>
          <ScrollView>
            {opcionFuente(null, 'Fondos del mes', 'Lo del salario: baja lo libre para gastar', 'wallet', colors.primary)}
            {ahorros.filter((a) => a.id !== String(selectedCategory?.id)).length > 0 && (
              <AppText variant="caption" style={[styles.seccion, { color: VERDE_AHORRO }]}>Ahorros</AppText>
            )}
            {ahorros.filter((a) => a.id !== String(selectedCategory?.id)).map((a) =>
              opcionFuente(`ahorro:${a.id}`, a.nombre, `Hay ${formatCurrency(a.saldo)} · no baja lo del mes`, 'piggy-bank', VERDE_AHORRO))}
            {tarjetas.length > 0 && (
              <AppText variant="caption" color={colors.textSecondary} style={styles.seccion}>Tarjetas de crédito</AppText>
            )}
            {tarjetas.map((t) =>
              opcionFuente(`tarjeta:${t.id}`, nombreTarjeta(t.name), t.owed > 0 ? `Debes ${formatCurrency(t.owed)}` : 'Al día',
                'credit-card', t.color || colors.textSecondary))}
            {!!conTarjeta && !tarjetaElegida && opcionFuente(`tarjeta:${conTarjeta}`, 'Con tarjeta', 'La tarjeta con la que se anotó', 'credit-card', colors.textSecondary)}
          </ScrollView>
        </View>
      </View>
    </Modal>

    <Calendario visible={eligiendoFecha} fecha={/^\d{4}-\d{2}-\d{2}$/.test(date) ? date : hoy} onElegir={setDate}
      onCerrar={() => setEligiendoFecha(false)} />

    <ConfirmarDescarte visible={descartando} onSeguir={() => { salidaPendiente.current = null; setDescartando(false); }} onDescartar={salir}
      mensaje={isEdit ? 'Los cambios no se han guardado. Si sales ahora, se pierden.' : undefined} />
    </>
  );
};

/** Verde del ahorro (el mismo de Anotar). */
const VERDE_AHORRO = '#1FA67A';

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.lg, paddingBottom: spacing.xxl * 2 },
  amountCard: {
    alignItems: 'center',
    paddingVertical: spacing.lg,
    paddingHorizontal: spacing.md,
    borderRadius: 16,
    borderWidth: 1,
    marginBottom: spacing.md,
  },
  amountLabel: {
    marginBottom: spacing.xs,
    letterSpacing: 2,
    /* Era 11: el rotulo del campo mas usado de la app, en el tamano mas
       pequeno que habia. */
    fontSize: 14,
  },
  amountRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  currencySign: {
    fontSize: 26,
    fontWeight: '700',
    marginRight: 4,
  },
  sectionLabel: {
    marginTop: spacing.md,
    marginBottom: spacing.sm,
    marginLeft: spacing.xs,
  },
  statusSection: {},
  statusRow: {
    flexDirection: 'row',
    gap: spacing.sm,
  },
  statusChip: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 44,
    paddingVertical: 10,
    borderRadius: 12,
    borderWidth: 1.5,
    gap: 8,
  },
  /* NO ES TEXTO: es un punto de color de 8x8 pintado con un componente de
     texto vacio. El fontSize: 1 esta para que la altura de linea no lo
     deforme. Queda escrito porque a simple vista parece un texto diminuto. */
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
    fontSize: 1,
  },
  catGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginBottom: spacing.md,
  },
  catChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 20,
    borderWidth: 1.5,
    gap: 6,
  },
  catIcon: {
    fontSize: 16,
  },
  cuota: { flex: 0, minWidth: 48 },
  campo: { flexDirection: 'row', alignItems: 'center', gap: 10, minHeight: 52, paddingHorizontal: 12, borderRadius: 14, borderWidth: 1 },
  fondo: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)' },
  hoja: { maxHeight: '80%', padding: 16, gap: 10, borderTopLeftRadius: 24, borderTopRightRadius: 24, width: '100%', maxWidth: 560, alignSelf: 'center' },
  opcionFuente: { flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 56, paddingVertical: 8, borderBottomWidth: StyleSheet.hairlineWidth },
  puntoFuente: { width: 32, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  seccion: { marginTop: 12, marginBottom: 4, fontWeight: '800', textTransform: 'uppercase', letterSpacing: 0.5 },
  parentInfo: { borderWidth: 1, borderRadius: 10, padding: spacing.sm, marginBottom: spacing.sm },
  saveBtn: { marginTop: spacing.lg },
  deleteBtn: { marginTop: spacing.sm },
});

export default MovementFormScreen;
