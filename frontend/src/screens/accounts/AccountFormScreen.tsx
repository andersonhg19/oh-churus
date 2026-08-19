import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import { spacing } from '../../theme';
import { confirmarBorrado } from '../../utils/confirmar';
import { useAccionUnica } from '../../hooks/useAccionUnica';
import { validateRequired } from '../../utils/validators';
import { accountService } from '../../services/accountService';
import { Account, AccountKind } from '../../types';
import { fechaLocalISO } from '../../utils/format';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type AccountsStackParamList = {
  AccountsList: undefined;
  AccountForm: { account?: Account };
  Reconcile: { account: Account };
};

type Props = NativeStackScreenProps<AccountsStackParamList, 'AccountForm'>;

const AccountFormScreen: React.FC<Props> = ({ navigation, route }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const existente = route.params?.account;
  const esEdicion = !!existente;

  const [nombre, setNombre] = useState(existente?.name || '');
  const [clase, setClase] = useState<AccountKind>(existente?.kind || 'OWN');
  const [saldoInicial, setSaldoInicial] = useState('');

  const borrarCuenta = useCallback(async () => {
    if (!existente) return;
    confirmarBorrado('esta cuenta', async () => {
      try {
        const res = await accountService.delete(existente.id);
        if (res.correct) {
          navigation.goBack();
        } else {
          /* El backend se niega con un motivo escrito para humanos ("todavia
             tiene movimientos"), asi que se ensena tal cual en vez de un
             "no se pudo eliminar" que no explica nada. */
          showToast('error', 'No se puede borrar', res.message);
        }
      } catch (err: any) {
        showToast('error', 'Error', err.message || 'No se pudo eliminar');
      }
    });
  }, [existente, navigation, showToast]);

  const { ejecutando: borrando, ejecutar: borrar } = useAccionUnica(borrarCuenta);

  const guardarCuenta = useCallback(async () => {
    const error = validateRequired(nombre, 'Nombre de la cuenta');
    if (error) {
      showToast('warning', 'Validacion', error);
      return;
    }

    const inicial = saldoInicial.trim() ? Number(saldoInicial.replace(/[^0-9.-]/g, '')) : 0;
    if (saldoInicial.trim() && Number.isNaN(inicial)) {
      showToast('warning', 'Validacion', 'El saldo inicial tiene que ser un numero');
      return;
    }

    try {
      const datos = {
        ...(esEdicion && { id: existente.id }),
        name: nombre.trim(),
        kind: clase,
        /* Solo al CREAR. Al editar el backend los ignora a proposito: cambiar
           el saldo inicial de una cuenta con historia reescribiria el pasado
           sin que se vea. Por eso el campo ni siquiera se muestra al editar. */
        ...(!esEdicion && inicial !== 0 && {
          openingBalance: inicial,
          openingDate: fechaLocalISO(new Date()),
        }),
      };
      const res = await accountService.save(datos);
      if (res.correct) {
        navigation.goBack();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo guardar');
    }
  }, [nombre, clase, saldoInicial, esEdicion, existente, navigation, showToast]);

  const { ejecutando: guardando, ejecutar: guardar } = useAccionUnica(guardarCuenta);

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      <AppText variant="subtitle" style={styles.titulo}>
        {esEdicion ? 'Editar cuenta' : 'Nueva cuenta'} 🐿️
      </AppText>

      <Input
        label="Nombre"
        value={nombre}
        onChangeText={setNombre}
        placeholder="Ahorros, Efectivo, Tarjeta..."
      />

      <AppText variant="label" style={styles.etiquetaClase}>Clase</AppText>
      <View style={styles.filaClase}>
        <TouchableOpacity
          testID="clase-propia"
          style={[
            styles.botonClase,
            {
              backgroundColor: clase === 'OWN' ? colors.income : colors.surface,
              borderColor: clase === 'OWN' ? colors.income : colors.border,
            },
          ]}
          onPress={() => setClase('OWN')}
          accessibilityRole="radio"
          accessibilityState={{ selected: clase === 'OWN' }}
          /* "Propia" a secas no dice nada; el emoji del banco, menos. */
          accessibilityLabel="Cuenta propia: el saldo es plata que tienes"
        >
          <AppText variant="body" color={clase === 'OWN' ? '#FFFFFF' : colors.text}>
            🏦 Propia
          </AppText>
        </TouchableOpacity>
        <TouchableOpacity
          testID="clase-pasivo"
          style={[
            styles.botonClase,
            {
              backgroundColor: clase === 'LIABILITY' ? colors.expense : colors.surface,
              borderColor: clase === 'LIABILITY' ? colors.expense : colors.border,
            },
          ]}
          onPress={() => setClase('LIABILITY')}
          accessibilityRole="radio"
          accessibilityState={{ selected: clase === 'LIABILITY' }}
          accessibilityLabel="Deuda: el saldo es plata que debes, como una tarjeta o un préstamo"
        >
          <AppText variant="body" color={clase === 'LIABILITY' ? '#FFFFFF' : colors.text}>
            💳 Pasivo
          </AppText>
        </TouchableOpacity>
      </View>
      <AppText variant="caption" color={colors.textSecondary} style={styles.ayudaClase}>
        {clase === 'OWN'
          ? 'Efectivo, cuenta de ahorros, nomina: el saldo es plata que tienes.'
          : 'Tarjeta de credito o prestamo: el saldo es plata que debes.'}
      </AppText>

      {!esEdicion && (
        <>
          <Input
            label="¿Con cuanto empieza?"
            value={saldoInicial}
            onChangeText={setSaldoInicial}
            placeholder="0"
            keyboardType="numeric"
          />
          <AppText variant="caption" color={colors.textSecondary} style={styles.ayudaClase}>
            {clase === 'OWN'
              ? 'Lo que hay hoy en la cuenta segun tu banco. Se anota como un movimiento de apertura con la fecha de hoy, asi que lo puedes corregir despues.'
              : 'Lo que debes hoy, en negativo (por ejemplo -400000). Se anota como un movimiento de apertura con la fecha de hoy.'}
          </AppText>
        </>
      )}

      {esEdicion && (
        <AppText variant="caption" color={colors.textMuted} style={styles.ayudaClase}>
          El saldo inicial no se edita aqui: cambiarlo reescribiria el pasado sin que se
          vea. Corrigelo desde su movimiento de apertura, o concilia con tu banco.
        </AppText>
      )}

      <Button
        title={esEdicion ? 'Guardar cambios' : 'Crear cuenta'}
        onPress={guardar}
        loading={guardando}
      />

      {esEdicion && !existente?.isDefault && (
        <Button
          title="Eliminar"
          accessibilityLabel={`Eliminar la cuenta ${existente?.name || ''}`.trim()}
          onPress={borrar}
          loading={borrando}
          variant="danger"
        />
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.md, paddingBottom: spacing.xl * 2 },
  titulo: { marginBottom: spacing.md },
  etiquetaClase: { marginBottom: spacing.xs },
  filaClase: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.xs },
  botonClase: {
    flex: 1,
    paddingVertical: spacing.sm,
    borderRadius: 8,
    borderWidth: 1,
    alignItems: 'center',
  },
  ayudaClase: { marginBottom: spacing.md },
});

export default AccountFormScreen;
