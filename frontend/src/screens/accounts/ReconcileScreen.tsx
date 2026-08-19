import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import { spacing } from '../../theme';
import { useAccionUnica } from '../../hooks/useAccionUnica';
import { accountService } from '../../services/accountService';
import { Account, Reconciliation } from '../../types';
import { formatCurrency } from '../../utils/format';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type AccountsStackParamList = {
  AccountsList: undefined;
  AccountForm: { account?: Account };
  Reconcile: { account: Account };
};

type Props = NativeStackScreenProps<AccountsStackParamList, 'Reconcile'>;

/**
 * Conciliar: una sola pregunta, "¿cuanto dice tu banco?".
 *
 * La pantalla esta partida en dos pasos a proposito, y el orden importa:
 * primero se ENSENA la diferencia, y solo despues se ofrece ajustar. Ajustar
 * de una es la salida facil y casi siempre la equivocada: si te faltan 45.000,
 * lo que hay que hacer es acordarse del gasto que no anotaste, no taparlo con
 * un movimiento de ajuste que no explica nada.
 *
 * Por eso el boton de ajustar es secundario y va acompanado de la frase que
 * dice de que lado esta la diferencia.
 */
const ReconcileScreen: React.FC<Props> = ({ navigation, route }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const cuenta = route.params.account;

  const [loQueDiceElBanco, setLoQueDiceElBanco] = useState('');
  const [comparacion, setComparacion] = useState<Reconciliation | null>(null);

  const importeValido = (): number | null => {
    const limpio = loQueDiceElBanco.trim().replace(/[^0-9.-]/g, '');
    if (!limpio) return null;
    const numero = Number(limpio);
    return Number.isNaN(numero) ? null : numero;
  };

  const comparar = useCallback(async () => {
    const importe = importeValido();
    if (importe === null) {
      showToast('warning', 'Validacion', 'Escribe el saldo que ves en tu banco');
      return;
    }
    try {
      const res = await accountService.reconcile(cuenta.id, importe, false);
      if (res.correct && res.object) {
        setComparacion(res.object);
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo comparar');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cuenta, loQueDiceElBanco, showToast]);

  const { ejecutando: comparando, ejecutar: ejecutarComparar } = useAccionUnica(comparar);

  const ajustar = useCallback(async () => {
    const importe = importeValido();
    if (importe === null) return;
    try {
      const res = await accountService.reconcile(cuenta.id, importe, true);
      if (res.correct) {
        showToast('success', 'Cuenta conciliada', res.object?.message);
        navigation.goBack();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo ajustar');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cuenta, loQueDiceElBanco, navigation, showToast]);

  const { ejecutando: ajustando, ejecutar: ejecutarAjustar } = useAccionUnica(ajustar);

  const cuadra = comparacion !== null && comparacion.difference === 0;

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      <AppText variant="subtitle" style={styles.titulo}>Conciliar {cuenta.name}</AppText>

      <Card
        style={styles.tarjeta}
        accessible
        accessibilityLabel={`Según la app tienes ${formatCurrency(cuenta.balance)}`}
      >
        <AppText variant="caption" color={colors.textSecondary}>Segun la app tienes</AppText>
        <AppText variant="title" testID="saldo-app">{formatCurrency(cuenta.balance)}</AppText>
      </Card>

      <Input
        label="¿Cuanto dice tu banco?"
        value={loQueDiceElBanco}
        onChangeText={(texto) => {
          setLoQueDiceElBanco(texto);
          /* Al cambiar la cifra, la comparacion anterior deja de ser cierta.
             Dejarla en pantalla invitaria a ajustar por un numero viejo. */
          setComparacion(null);
        }}
        placeholder="0"
        keyboardType="numeric"
      />

      <Button
        title="Comparar"
        accessibilityLabel="Comparar con lo que dice tu banco"
        onPress={ejecutarComparar}
        loading={comparando}
      />

      {comparacion && (
        <Card style={styles.tarjeta}>
          {cuadra ? (
            <>
              <AppText variant="body" color={colors.income} testID="veredicto">
                ✅ La cuenta cuadra
              </AppText>
              <AppText variant="caption" color={colors.textSecondary}>
                Lo que tienes anotado coincide con tu banco. No hay nada que hacer.
              </AppText>
            </>
          ) : (
            <>
              <View
                accessible
                accessibilityLabel={`Hay una diferencia de ${formatCurrency(comparacion.difference)}`}
              >
                <AppText variant="caption" color={colors.textSecondary}>Diferencia</AppText>
                <AppText
                  variant="title"
                  color={colors.expense}
                  testID="veredicto"
                >
                  {formatCurrency(comparacion.difference)}
                </AppText>
              </View>
              <AppText variant="caption" color={colors.textSecondary} style={styles.explicacion}>
                {comparacion.message}
              </AppText>
              <AppText variant="caption" color={colors.textMuted} style={styles.explicacion}>
                Busca primero el movimiento que falta. Si no aparece, anota la diferencia
                como ajuste para que la cuenta vuelva a cuadrar.
              </AppText>
              <Button
                title="Anotar la diferencia como ajuste"
                onPress={ejecutarAjustar}
                loading={ajustando}
                variant="secondary"
              />
            </>
          )}
        </Card>
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.md, paddingBottom: spacing.xl * 2 },
  titulo: { marginBottom: spacing.md },
  tarjeta: { marginBottom: spacing.md },
  explicacion: { marginTop: spacing.xs, marginBottom: spacing.sm },
});

export default ReconcileScreen;
