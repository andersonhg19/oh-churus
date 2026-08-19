import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import EmptyState from '../../components/molecules/EmptyState';
import EstadoError from '../../components/molecules/EstadoError';
import Spinner from '../../components/atoms/Spinner';
import { spacing } from '../../theme';
import { splitService } from '../../services/splitService';
import { householdService } from '../../services/householdService';
import { Balance } from '../../types';
import { formatCurrency } from '../../utils/format';
import { useCarga, exigir } from '../../hooks/useCarga';
import { confirmarAccion } from '../../utils/confirmar';
import { useFocusEffect } from '@react-navigation/native';

/**
 * Quien te debe y a quien le debes.
 *
 * UNA CIFRA POR PERSONA, NO UNA LISTA DE DEUDAS. "A le debe 40.000 a B por el
 * mercado, B le debe 25.000 a A por la gasolina y A le debe 12.000 a B por el
 * taxi" no lo resuelve nadie de cabeza, y encima invita a hacer tres pagos
 * donde sobra uno. Lo util es "te debo 27.000". El detalle sigue estando en
 * los movimientos, que es donde tiene sentido mirarlo.
 *
 * Y NO HAY SELECTOR DE PERIODO, a proposito: una deuda no caduca a fin de mes.
 */
const BalancesScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();

  const [balances, setBalances] = useState<Balance[]>([]);
  const [meDeben, setMeDeben] = useState(0);
  const [debo, setDebo] = useState(0);
  const [nombres, setNombres] = useState<Record<number, string>>({});

  const traerBalances = useCallback(async () => {
    const res = await splitService.balances();
    const datos = exigir(res);
    setBalances(datos?.list || []);
    setMeDeben(datos?.totalOwedToMe ?? 0);
    setDebo(datos?.totalIOwe ?? 0);
  }, []);

  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traerBalances);

  /*
   * Los nombres se piden aparte y si fallan NO se rompe la pantalla: el
   * balance es la informacion, el nombre es el adorno. Sin esto, un fallo al
   * cargar el hogar dejaria a Anderson sin poder ver cuanto debe.
   */
  const traerNombres = useCallback(async () => {
    if (!user) return;
    try {
      const res = await householdService.getByUser(user.userId);
      if (!res.correct || !res.object) return;
      const mapa: Record<number, string> = {};
      res.object.forEach((h) =>
        h.members?.forEach((m) => {
          mapa[m.userId] = mapa[m.userId] || `Miembro ${m.userId}`;
        }),
      );
      setNombres(mapa);
    } catch {
      setNombres({});
    }
  }, [user]);

  useFocusEffect(
    useCallback(() => {
      cargar();
      traerNombres();
    }, [cargar, traerNombres]),
  );

  const comoSeLlama = (userId: number) => nombres[userId] || `Persona ${userId}`;

  const liquidarCon = useCallback(
    async (balance: Balance) => {
      const leDebo = balance.net < 0;
      confirmarAccion({
        titulo: leDebo ? 'Anotar que pagaste' : 'Anotar que te pagaron',
        mensaje: leDebo
          ? `¿Le pagaste ${formatCurrency(balance.amount)} a ${comoSeLlama(balance.userId)}?`
          : `¿${comoSeLlama(balance.userId)} te pago ${formatCurrency(balance.amount)}?`,
        textoConfirmar: 'Si, anotarlo',
        onConfirmar: async () => {
          try {
            const res = await splitService.settle(balance.userId);
            if (res.correct) {
              showToast('success', 'Anotado', res.object?.message);
              cargar();
            } else {
              showToast('error', 'Error', res.message);
            }
          } catch (err: any) {
            showToast('error', 'Error', err.message || 'No se pudo anotar');
          }
        },
      });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [nombres, cargar, showToast],
  );

  if (cargando) return <Spinner fullScreen />;

  if (error) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background }]}>
        <AppText variant="subtitle" style={styles.header}>Cuentas entre nosotros</AppText>
        <EstadoError mensaje={error} onReintentar={cargar} />
      </View>
    );
  }

  const neto = meDeben - debo;

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <AppText variant="subtitle" style={styles.header}>Cuentas entre nosotros</AppText>

      <ScrollView
        contentContainerStyle={balances.length === 0 ? styles.emptyContainer : styles.listContent}
        refreshControl={
          <RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />
        }
      >
        {balances.length === 0 ? (
          <EmptyState
            title="Estan en paz"
            message="Cuando repartas un gasto entre varias personas, aqui vas a ver quien le debe que a quien."
            icon="🤝"
          />
        ) : (
          <>
            <Card
              style={styles.resumen}
              accessible
              accessibilityLabel={`${neto >= 0 ? 'En total te deben' : 'En total debes'} ${formatCurrency(Math.abs(neto))}`}
            >
              <AppText variant="caption" color={colors.textSecondary}>
                {neto >= 0 ? 'En total te deben' : 'En total debes'}
              </AppText>
              <AppText
                variant="title"
                color={neto >= 0 ? colors.income : colors.expense}
                testID="neto-total"
              >
                {formatCurrency(Math.abs(neto))}
              </AppText>
            </Card>

            {balances.map((b) => {
              const leDebo = b.net < 0;
              return (
                <TouchableOpacity
                  key={b.userId}
                  testID={`balance-${b.userId}`}
                  activeOpacity={0.7}
                  onPress={() => liquidarCon(b)}
                  accessibilityRole="button"
                  accessibilityLabel={`${comoSeLlama(b.userId)}: ${b.label} ${formatCurrency(b.amount)}`}
                  /* Que tocar la fila sirva para saldar la deuda solo se cuenta
                     en una linea al final de la lista. */
                  accessibilityHint={leDebo ? 'Anota que ya le pagaste' : 'Anota que ya te pagó'}
                >
                  <Card style={styles.fila}>
                    <View style={styles.filaInterna}>
                      <View style={styles.persona}>
                        <AppText variant="body">{comoSeLlama(b.userId)}</AppText>
                        {/* El texto va SIEMPRE junto al numero: un estado no
                            puede depender solo del color o del signo. */}
                        <AppText variant="caption" color={colors.textSecondary}>
                          {b.label}
                        </AppText>
                      </View>
                      <AppText
                        variant="body"
                        color={leDebo ? colors.expense : colors.income}
                        testID={`importe-${b.userId}`}
                      >
                        {formatCurrency(b.amount)}
                      </AppText>
                    </View>
                  </Card>
                </TouchableOpacity>
              );
            })}

            <AppText variant="caption" color={colors.textMuted} style={styles.pista}>
              Toca una persona para anotar que la deuda ya se pago.
            </AppText>
          </>
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { padding: spacing.md, paddingBottom: spacing.sm },
  listContent: { padding: spacing.md, paddingTop: 0, paddingBottom: spacing.xl },
  emptyContainer: { flex: 1 },
  resumen: { marginBottom: spacing.md },
  fila: { marginBottom: spacing.sm },
  filaInterna: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  persona: { flex: 1, marginRight: spacing.sm },
  pista: { marginTop: spacing.md, textAlign: 'center' },
});

export default BalancesScreen;
