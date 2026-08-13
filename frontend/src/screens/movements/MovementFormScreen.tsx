import React, { useState, useEffect, useCallback } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import CapsuleToggle from '../../components/molecules/CapsuleToggle';
import { spacing } from '../../theme';
import { movementService } from '../../services/movementService';
import { accountService } from '../../services/accountService';
import { householdService } from '../../services/householdService';
import { categoryService } from '../../services/categoryService';
import { Movement, Category, CategoryType, Account, SplitMode, SplitInput } from '../../types';
import { formatCurrency, fechaLocalISO } from '../../utils/format';
import { confirmarBorrado } from '../../utils/confirmar';
import { useAccionUnica } from '../../hooks/useAccionUnica';
import { getIconEmoji } from '../../utils/iconMap';
import { validateAmount, validateDate, validateCategory, validateAll } from '../../utils/validators';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type RootStackParamList = {
  MainTabs: undefined;
  MovementFormModal: { movement?: Movement; parentMovement?: Movement };
};

type Props = NativeStackScreenProps<RootStackParamList, 'MovementFormModal'>;

const MovementFormScreen: React.FC<Props> = ({ navigation, route }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();
  const existing = route.params?.movement;
  const parentMovement = route.params?.parentMovement;
  const isEdit = !!existing;

  const [allCategories, setAllCategories] = useState<Category[]>([]);
  const [movementType, setMovementType] = useState<CategoryType>(existing?.categoryType || 'EXPENSE');
  const [selectedCategory, setSelectedCategory] = useState<Category | null>(null);
  const [amount, setAmount] = useState(existing ? String(existing.amount) : '');
  const [description, setDescription] = useState(existing?.description || '');
  const [date, setDate] = useState(existing?.date || fechaLocalISO());
  const [confirmed, setConfirmed] = useState(existing?.confirmed ?? true);

  /* La cuenta donde ocurre. Si no se toca, el backend lo manda a la cuenta
     por defecto, asi que anotar rapido sigue siendo un gesto: no se obliga a
     elegir cuenta para guardar un gasto. */
  const [cuentas, setCuentas] = useState<Account[]>([]);
  const [cuentaId, setCuentaId] = useState<string | null>(existing?.accountId || null);

  /*
   * El reparto entre personas.
   *
   * Empieza APAGADO y solo aparece si hay con quien repartir. La inmensa
   * mayoria de los gastos son de una sola persona, y meterle el selector
   * delante a todo el mundo convertiria el gesto de anotar un cafe en un
   * formulario. Quien no tenga nucleo familiar no ve nada de esto.
   */
  const [reparte, setReparte] = useState(false);
  const [modoReparto, setModoReparto] = useState<SplitMode>('EQUAL');
  const [conQuien, setConQuien] = useState<number[]>([]);
  const [valores, setValores] = useState<Record<number, string>>({});
  const [companeros, setCompaneros] = useState<number[]>([]);

  // Transfer: disponibilizar a cuenta personal
  const [isTransfer, setIsTransfer] = useState(false);
  const [personalCategoryId, setPersonalCategoryId] = useState<number | null>(null);

  useEffect(() => {
    /* Si las cuentas no cargan, el selector no aparece y el movimiento se
       guarda en la de por defecto. Se degrada en silencio a proposito: no
       poder anotar un gasto porque falla una lista secundaria seria peor que
       anotarlo en la cuenta equivocada, que ademas se puede corregir. */
    const cargarCuentas = async () => {
      try {
        const res = await accountService.getAll();
        if (res.correct && res.object) setCuentas(res.object.list || []);
      } catch {
        setCuentas([]);
      }
    };
    cargarCuentas();
  }, []);

  /* Los companeros de hogar. Si esto falla, el reparto simplemente no aparece
     y el gasto se guarda como propio: degradarse en silencio es mejor que no
     poder anotar nada por culpa de una lista secundaria. */
  useEffect(() => {
    const cargarCompaneros = async () => {
      if (!user) return;
      try {
        const res = await householdService.getByUser(user.userId);
        if (!res.correct || !res.object) return;
        const otros = new Set<number>();
        res.object.forEach((h) =>
          h.members?.forEach((m) => {
            if (String(m.userId) !== String(user.userId)) otros.add(m.userId);
          }),
        );
        setCompaneros(Array.from(otros));
      } catch {
        setCompaneros([]);
      }
    };
    cargarCompaneros();
  }, [user]);

  useEffect(() => {
    const loadCategories = async () => {
      if (!user) return;
      try {
        const res = await categoryService.getAll({ userId: user.userId, page: 0, size: 100 });
        if (res.correct && res.object) {
          const list = res.object.list || [];
          setAllCategories(list);
          if (existing?.categoryId) {
            const found = list.find((c: Category) => String(c.id) === String(existing.categoryId));
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
  }, [user, existing]);

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
        navigation.goBack();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo eliminar');
    }
  }, [existing, navigation, showToast]);

  const { ejecutando: borrando, ejecutar: borrar } = useAccionUnica(borrarMovimiento);

  /* En EQUAL no hay valor que mandar: todos ponen lo mismo. En los demas es
     lo que la persona escribio, y un vacio vale cero. */
  const valorDe = (quien: number): number | undefined => {
    if (modoReparto === 'EQUAL') return undefined;
    const escrito = valores[quien];
    const numero = Number((escrito || '').replace(/[^0-9.]/g, ''));
    return Number.isNaN(numero) ? 0 : numero;
  };

  const alternarPersona = (quien: number) => {
    setConQuien((antes) =>
      antes.includes(quien) ? antes.filter((x) => x !== quien) : [...antes, quien],
    );
  };

  const guardarMovimiento = useCallback(async () => {
    const error = validateAll(
      validateCategory(selectedCategory?.id ? String(selectedCategory.id) : null),
      validateAmount(amount),
      validateDate(date),
    );
    if (error) {
      showToast('warning', 'Validacion', error);
      return;
    }
    try {
      // If transfer mode, use transfer endpoint
      if (isTransfer && personalCategoryId && !isEdit) {
        const res = await movementService.transfer({
          userId: user!.userId,
          fromCategoryId: Number(selectedCategory!.id),
          toCategoryId: personalCategoryId,
          amount: parseFloat(amount),
          description: description.trim() || 'Disponibilizar',
        });
        if (res.correct) {
          showToast('success', 'Transferencia registrada');
          navigation.goBack();
        } else {
          showToast('error', 'Error', res.message);
        }
      } else {
        // Normal save
        const data: Partial<Movement> = {
          ...(isEdit && { id: existing.id }),
          userId: user?.userId,
          categoryId: selectedCategory!.id,
          amount: parseFloat(amount),
          description: description.trim() || undefined,
          date,
          confirmed,
          ...(cuentaId && { accountId: cuentaId }),
          /* Yo SIEMPRE voy en el reparto: el gasto es mio y una parte me
             toca. Sin esto, repartir con una persona significaria que el
             gasto entero es de la otra. */
          ...(reparte && conQuien.length > 0 && {
            splitMode: modoReparto,
            splits: [
              { participantId: Number(user?.userId), value: valorDe(Number(user?.userId)) },
              ...conQuien.map((quien) => ({ participantId: quien, value: valorDe(quien) })),
            ] as SplitInput[],
          }),
          ...(parentMovement && { parentMovementId: parentMovement.id }),
        };
        const res = await movementService.save(data);
        if (res.correct) {
          showToast('success', isEdit ? 'Actualizado' : parentMovement ? 'Sub-gasto registrado' : 'Guardado');
          navigation.goBack();
        } else {
          showToast('error', 'Error', res.message);
        }
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo guardar');
    }
  }, [selectedCategory, amount, date, description, confirmed, isTransfer, personalCategoryId, cuentaId, reparte, modoReparto, conQuien, valores, isEdit, existing, parentMovement, user, navigation, showToast]);

  const { ejecutando: guardando, ejecutar: handleSave } = useAccionUnica(guardarMovimiento);

  const typeColor = movementType === 'INCOME' ? colors.income : colors.expense;

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      {/* Info de sub-gasto */}
      {parentMovement && (
        <View style={[styles.parentInfo, { borderColor: colors.primary + '40' }]}>
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
          <Input label="" value={amount} onChangeText={setAmount} placeholder="0" keyboardType="numeric" />
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
        const renderCat = (cat: any) => {
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
            >
              <AppText style={styles.catIcon}>{getIconEmoji(cat.icon)}</AppText>
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
                <View style={styles.catGrid}>{shared.map(renderCat)}</View>
              </>
            )}
            {personal.length > 0 && (
              <>
                <AppText variant="label" style={styles.sectionLabel}>Personales</AppText>
                <View style={styles.catGrid}>{personal.map(renderCat)}</View>
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

      {/* Transfer: solo si es gasto, categoría compartida, no es edición, y no es sub-gasto */}
      {movementType === 'EXPENSE' && selectedCategory && (selectedCategory as any).shared && !isEdit && !parentMovement && (
        <View style={styles.transferSection}>
          <TouchableOpacity
            style={[styles.transferToggle, {
              backgroundColor: isTransfer ? colors.primary + '20' : 'transparent',
              borderColor: isTransfer ? colors.primary : colors.border,
            }]}
            onPress={() => { setIsTransfer(!isTransfer); if (isTransfer) setPersonalCategoryId(null); }}
          >
            <AppText variant="body" style={{ color: isTransfer ? colors.primary : colors.textMuted, fontWeight: isTransfer ? '700' : '400' }}>
              💰 Disponibilizar a cuenta personal
            </AppText>
          </TouchableOpacity>
          {isTransfer && (
            <View style={styles.transferPicker}>
              <AppText variant="caption" color={colors.textSecondary} style={{ marginBottom: spacing.xs }}>Destino personal:</AppText>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.xs }}>
                {allCategories.filter((c: any) => c.type === 'EXPENSE' && !c.shared && !c.householdId).map((c: any) => (
                  <TouchableOpacity
                    key={c.id}
                    style={[styles.catChip, {
                      backgroundColor: personalCategoryId === c.id ? (c.color || colors.secondary) : colors.surface,
                      borderColor: personalCategoryId === c.id ? (c.color || colors.secondary) : colors.border,
                    }]}
                    onPress={() => setPersonalCategoryId(c.id)}
                  >
                    <AppText variant="caption" style={{ color: personalCategoryId === c.id ? '#FFF' : colors.text }}>
                      {c.name}
                    </AppText>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          )}
        </View>
      )}

      {/* placeholder - parent info moved above */}

      {/* Campos */}
      {cuentas.length > 1 && (
        <>
          <AppText variant="label" style={styles.etiquetaCuenta}>¿De que cuenta salio?</AppText>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filaCuentas}>
            {cuentas.map((cuenta) => {
              const elegida = String(cuentaId) === String(cuenta.id);
              return (
                <TouchableOpacity
                  key={cuenta.id}
                  testID={`elegir-cuenta-${cuenta.id}`}
                  onPress={() => setCuentaId(elegida ? null : cuenta.id)}
                  style={[
                    styles.chipCuenta,
                    {
                      backgroundColor: elegida ? colors.primary : colors.surface,
                      borderColor: elegida ? colors.primary : colors.border,
                    },
                  ]}
                >
                  <AppText variant="caption" color={elegida ? '#FFFFFF' : colors.text}>
                    {cuenta.name}
                  </AppText>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        </>
      )}

      {companeros.length > 0 && movementType === 'EXPENSE' && !parentMovement && (
        <View style={styles.bloqueReparto}>
          <TouchableOpacity
            testID="alternar-reparto"
            style={[styles.cabeceraReparto, { borderColor: colors.border }]}
            onPress={() => setReparte((antes) => !antes)}
          >
            <AppText variant="label">
              {reparte ? '👥 Repartir este gasto' : '👥 ¿Lo compartes con alguien?'}
            </AppText>
            <AppText variant="body" color={colors.textMuted}>{reparte ? '−' : '+'}</AppText>
          </TouchableOpacity>

          {reparte && (
            <>
              <AppText variant="caption" color={colors.textSecondary} style={styles.ayudaReparto}>
                El gasto sale entero de tu cuenta, pero en tu presupuesto solo entra tu parte.
                Lo demas queda como plata que te deben.
              </AppText>

              <View style={styles.filaModos}>
                {([
                  ['EQUAL', 'A medias'],
                  ['SHARES', 'Partes'],
                  ['PERCENT', '%'],
                  ['AMOUNT', 'Importe'],
                ] as [SplitMode, string][]).map(([modo, etiqueta]) => (
                  <TouchableOpacity
                    key={modo}
                    testID={`modo-${modo}`}
                    onPress={() => setModoReparto(modo)}
                    style={[
                      styles.chipModo,
                      {
                        backgroundColor: modoReparto === modo ? colors.primary : colors.surface,
                        borderColor: modoReparto === modo ? colors.primary : colors.border,
                      },
                    ]}
                  >
                    <AppText variant="caption" color={modoReparto === modo ? '#FFFFFF' : colors.text}>
                      {etiqueta}
                    </AppText>
                  </TouchableOpacity>
                ))}
              </View>

              <AppText variant="caption" color={colors.textSecondary} style={styles.ayudaReparto}>
                ¿Con quien?
              </AppText>
              {companeros.map((quien) => {
                const elegido = conQuien.includes(quien);
                return (
                  <View key={quien} style={styles.filaPersona}>
                    <TouchableOpacity
                      testID={`persona-${quien}`}
                      onPress={() => alternarPersona(quien)}
                      style={[
                        styles.chipPersona,
                        {
                          backgroundColor: elegido ? colors.secondary : colors.surface,
                          borderColor: elegido ? colors.secondary : colors.border,
                        },
                      ]}
                    >
                      <AppText variant="caption" color={elegido ? '#FFFFFF' : colors.text}>
                        {elegido ? '✓ ' : ''}Persona {quien}
                      </AppText>
                    </TouchableOpacity>

                    {elegido && modoReparto !== 'EQUAL' && (
                      <View style={styles.campoValor}>
                        <Input
                          label=""
                          value={valores[quien] || ''}
                          onChangeText={(t) => setValores((v) => ({ ...v, [quien]: t }))}
                          placeholder={modoReparto === 'PERCENT' ? '%' : '0'}
                          keyboardType="numeric"
                        />
                      </View>
                    )}
                  </View>
                );
              })}

              {reparte && conQuien.length === 0 && (
                <AppText variant="caption" color={colors.warning} style={styles.ayudaReparto}>
                  Elige al menos una persona, o apaga el reparto.
                </AppText>
              )}
            </>
          )}
        </View>
      )}

      <Input label="Fecha" value={date} onChangeText={setDate} placeholder="YYYY-MM-DD" />
      <Input label="Nota" value={description} onChangeText={setDescription} placeholder="Descripcion (opcional)" multiline />

      {/* Acciones */}
      <Button title={isTransfer ? 'Disponibilizar' : isEdit ? 'Actualizar' : parentMovement ? 'Registrar sub-gasto' : 'Guardar'} onPress={handleSave} loading={guardando} disabled={borrando} size="large" style={styles.saveBtn} />
      {isEdit && (
        <Button title="Eliminar" onPress={handleDelete} loading={borrando} disabled={guardando} variant="danger" style={styles.deleteBtn} />
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  etiquetaCuenta: { marginBottom: spacing.xs },
  bloqueReparto: { marginBottom: spacing.md },
  cabeceraReparto: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.sm,
    borderBottomWidth: 1,
  },
  ayudaReparto: { marginTop: spacing.sm, marginBottom: spacing.sm },
  filaModos: { flexDirection: 'row', gap: spacing.xs, marginBottom: spacing.sm },
  chipModo: {
    flex: 1,
    paddingVertical: spacing.sm,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: 'center',
  },
  filaPersona: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, marginBottom: spacing.xs },
  chipPersona: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: 20,
    borderWidth: 1,
  },
  campoValor: { flex: 1 },
  filaCuentas: { marginBottom: spacing.md },
  chipCuenta: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: 20,
    borderWidth: 1,
    marginRight: spacing.sm,
  },
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
    fontSize: 11,
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
    paddingVertical: 10,
    borderRadius: 12,
    borderWidth: 1.5,
    gap: 8,
  },
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
  transferSection: { marginTop: spacing.sm, marginBottom: spacing.sm },
  transferToggle: { paddingVertical: 12, paddingHorizontal: 16, borderRadius: 12, borderWidth: 1.5 },
  transferPicker: { marginTop: spacing.sm, paddingLeft: spacing.sm },
  parentInfo: { borderWidth: 1, borderRadius: 10, padding: spacing.sm, marginBottom: spacing.sm },
  saveBtn: { marginTop: spacing.lg },
  deleteBtn: { marginTop: spacing.sm },
});

export default MovementFormScreen;
