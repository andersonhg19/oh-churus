import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import EmptyState from '../../components/molecules/EmptyState';
import EstadoError from '../../components/molecules/EstadoError';
import Spinner from '../../components/atoms/Spinner';
import { spacing } from '../../theme';
import { accountService } from '../../services/accountService';
import { Account } from '../../types';
import { formatCurrency } from '../../utils/format';
import { useCarga, exigir } from '../../hooks/useCarga';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type AccountsStackParamList = {
  AccountsList: undefined;
  AccountForm: { account?: Account };
  Reconcile: { account: Account };
};

type Props = NativeStackScreenProps<AccountsStackParamList, 'AccountsList'>;

/**
 * Lo que esta pantalla tiene que conseguir en un vistazo: que Anderson pueda
 * comparar lo que dice la app con lo que dice su banco. Todo lo demas es
 * secundario.
 *
 * Por eso el saldo confirmado va grande y el proyectado pequeno debajo. Son
 * dos numeros distintos y confundirlos arruina la comparacion: el banco no
 * sabe nada de los movimientos que aun no han ocurrido.
 */
const AccountsScreen: React.FC<Props> = ({ navigation }) => {
  const { colors } = useTheme();
  const [cuentas, setCuentas] = useState<Account[]>([]);
  const [patrimonio, setPatrimonio] = useState<number>(0);

  const traerCuentas = useCallback(async () => {
    const res = await accountService.getAll();
    const datos = exigir(res);
    setCuentas(datos?.list || []);
    setPatrimonio(datos?.netWorth ?? 0);
  }, []);

  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traerCuentas);

  useFocusEffect(
    useCallback(() => {
      cargar();
    }, [cargar]),
  );

  /**
   * En un pasivo el saldo es negativo cuando debes, asi que se ensena en
   * positivo con la palabra "Debes" delante. El signo menos junto a "Tarjeta"
   * se lee mal: parece un error, no una deuda.
   *
   * Y nunca solo el color: rojo y verde son la misma casilla gris para quien
   * no distingue esos dos, y en el movil se mira a contraluz.
   */
  const comoSeLee = (cuenta: Account) => {
    const esDeuda = cuenta.kind === 'LIABILITY';
    const importe = esDeuda ? Math.abs(cuenta.balance) : cuenta.balance;
    return {
      etiqueta: esDeuda ? 'Debes' : 'Tienes',
      importe: formatCurrency(importe),
      color: esDeuda || cuenta.balance < 0 ? colors.expense : colors.income,
    };
  };

  if (cargando) return <Spinner fullScreen />;

  if (error) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background }]}>
          <EstadoError mensaje={error} onReintentar={cargar} />
      </View>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>

      <ScrollView
        contentContainerStyle={cuentas.length === 0 ? styles.emptyContainer : styles.listContent}
        refreshControl={
          <RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />
        }
      >
        {cuentas.length === 0 ? (
          <EmptyState
            title="Sin cuentas"
            message="Crea tu primera cuenta y dile con cuanto empieza. A partir de ahi la app puede decirte cuanta plata deberia haber."
            icon="🏦"
          />
        ) : (
          <>
            <Card
              style={styles.patrimonio}
              accessible
              accessibilityLabel={`Patrimonio, lo que tienes menos lo que debes: ${formatCurrency(patrimonio)}`}
            >
              <AppText variant="caption" color={colors.textSecondary}>
                Patrimonio (lo que tienes menos lo que debes)
              </AppText>
              <AppText
                variant="title"
                color={patrimonio < 0 ? colors.expense : colors.text}
                testID="patrimonio"
              >
                {formatCurrency(patrimonio)}
              </AppText>
            </Card>

            {cuentas.map((cuenta) => {
              const lectura = comoSeLee(cuenta);
              const hayPendiente = cuenta.projectedBalance !== cuenta.balance;
              return (
                <TouchableOpacity
                  key={cuenta.id}
                  testID={`cuenta-${cuenta.id}`}
                  activeOpacity={0.7}
                  onPress={() => navigation.navigate('AccountForm', { account: cuenta })}
                  onLongPress={() => navigation.navigate('Reconcile', { account: cuenta })}
                  accessibilityRole="button"
                  accessibilityLabel={
                    `${cuenta.name}, ${cuenta.kind === 'LIABILITY' ? 'una deuda' : 'cuenta propia'}. ` +
                    `${lectura.etiqueta} ${lectura.importe}` +
                    (hayPendiente ? `. Contando lo pendiente: ${formatCurrency(cuenta.projectedBalance)}` : '')
                  }
                  /* Lo de mantener pulsado para conciliar solo se cuenta en una
                     linea de texto al final de la lista, que quien va elemento
                     por elemento oye media pantalla despues. */
                  accessibilityHint="Ábrela para editarla. Debajo tienes el botón de conciliar"
                >
                  <Card style={styles.cuenta}>
                    <View style={styles.filaCuenta}>
                      <View style={styles.datosCuenta}>
                        <AppText variant="body">{cuenta.name}</AppText>
                        <AppText variant="caption" color={colors.textSecondary}>
                          {cuenta.kind === 'LIABILITY' ? 'Pasivo (deuda)' : 'Cuenta propia'}
                        </AppText>
                        {/* La cuenta por defecto la crea la app sola la primera vez
                            que hace falta (al generar los pendientes), y nadie sabia
                            de donde salia ni para que era. */}
                        {cuenta.isDefault && (
                          <AppText variant="caption" color={colors.textMuted}>
                            La creó la app: aquí caen los movimientos que no dicen de qué cuenta salen. Renómbrala con el nombre de tu banco.
                          </AppText>
                        )}
                      </View>
                      <View style={styles.saldos}>
                        <AppText variant="caption" color={colors.textSecondary}>
                          {lectura.etiqueta}
                        </AppText>
                        <AppText variant="body" color={lectura.color} testID={`saldo-${cuenta.id}`}>
                          {lectura.importe}
                        </AppText>
                        {hayPendiente && (
                          <AppText variant="caption" color={colors.pending}>
                            Si se paga y se cobra todo lo programado: {formatCurrency(cuenta.projectedBalance)}
                          </AppText>
                        )}
                      </View>
                    </View>

                    {/*
                      * CONCILIAR DEJA DE SER UN SECRETO.
                      *
                      * Estaba solo detras de un gesto largo, y una accion que
                      * hay que adivinar es una accion que no existe: nadie
                      * mantiene pulsada una tarjeta a ver que pasa. El gesto
                      * se conserva —quien ya lo conocia lo sigue teniendo—
                      * pero ahora hay un boton que se ve.
                      */}
                    <TouchableOpacity
                      onPress={() => navigation.navigate('Reconcile', { account: cuenta })}
                      style={[styles.conciliar, { borderColor: colors.border }]}
                      accessibilityRole="button"
                      accessibilityLabel={`Conciliar ${cuenta.name} con tu banco`}
                      testID={`conciliar-${cuenta.id}`}
                    >
                      <AppText variant="caption" color={colors.primary}>
                        Conciliar con el banco
                      </AppText>
                    </TouchableOpacity>
                  </Card>
                </TouchableOpacity>
              );
            })}
          </>
        )}
      </ScrollView>

      <TouchableOpacity
        style={[styles.fab, { backgroundColor: colors.primary }]}
        onPress={() => navigation.navigate('AccountForm', {})}
        activeOpacity={0.8}
        testID="nueva-cuenta"
        accessibilityRole="button"
        accessibilityLabel="Crear una cuenta"
      >
        <AppText variant="title" color="#FFFFFF" style={styles.fabText}>+</AppText>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { padding: spacing.md, paddingBottom: spacing.sm },
  listContent: { padding: spacing.md, paddingBottom: spacing.xl * 2 },
  emptyContainer: { flex: 1 },
  patrimonio: { marginBottom: spacing.md },
  cuenta: { marginBottom: spacing.sm },
  filaCuenta: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  datosCuenta: { flex: 1, minWidth: 0, marginRight: spacing.sm },
  saldos: { alignItems: 'flex-end', maxWidth: '48%', flexShrink: 1 },
  conciliar: {
    marginTop: spacing.sm,
    paddingTop: spacing.sm,
    borderTopWidth: 1,
    minHeight: 44,
    justifyContent: 'center',
  },
  pista: { marginTop: spacing.md, textAlign: 'center' },
  fab: {
    position: 'absolute',
    right: spacing.md,
    bottom: spacing.lg,
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
    elevation: 6,
  },
  fabText: { lineHeight: 32 },
});

export default AccountsScreen;
