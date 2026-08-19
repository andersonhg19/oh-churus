import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import EmptyState from '../../components/molecules/EmptyState';
import EstadoError from '../../components/molecules/EstadoError';
import Spinner from '../../components/atoms/Spinner';
import { spacing } from '../../theme';
import { envelopeService } from '../../services/envelopeService';
import { Envelope } from '../../types';
import { formatCurrency } from '../../utils/format';
import { useCarga, exigir } from '../../hooks/useCarga';
import { useAccionUnica } from '../../hooks/useAccionUnica';
import { useFocusEffect } from '@react-navigation/native';

/**
 * Los sobres del mes.
 *
 * LA FRASE que la pantalla tiene que dejar clara, y que se muestra dentro de
 * la app y no solo en el codigo:
 *
 *   Lo que sobra en una categoria se queda. Lo que te pasaste se descuenta de
 *   lo que tienes para repartir el mes que viene.
 *
 * Por eso "Para repartir" va arriba y grande: es la unica cifra sobre la que
 * se decide algo. Y por eso tocar un sobre en rojo abre directamente el
 * movimiento entre sobres — cuando te pasaste, la accion util no es sentirse
 * mal, es elegir de que otro sobre sale esa plata.
 */
const EnvelopesScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();
  const diaDeCorte = user?.budgetStartDay ?? 1;

  const [sobres, setSobres] = useState<Envelope[]>([]);
  const [paraRepartir, setParaRepartir] = useState(0);
  const [deudaArrastrada, setDeudaArrastrada] = useState(0);

  const [moviendoDesde, setMoviendoDesde] = useState<Envelope | null>(null);
  const [haciaCategoria, setHaciaCategoria] = useState<string | null>(null);
  const [cuanto, setCuanto] = useState('');

  const traerSobres = useCallback(async () => {
    const res = await envelopeService.state(diaDeCorte);
    const datos = exigir(res);
    setSobres(datos?.envelopes || []);
    setParaRepartir(datos?.toBudget ?? 0);
    setDeudaArrastrada(datos?.carriedDebt ?? 0);
  }, [diaDeCorte]);

  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traerSobres);

  useFocusEffect(
    useCallback(() => {
      cargar();
    }, [cargar]),
  );

  const mover = useCallback(async () => {
    if (!moviendoDesde || !haciaCategoria) return;
    const importe = Number((cuanto || '').replace(/[^0-9.]/g, ''));
    if (!importe || Number.isNaN(importe)) {
      showToast('warning', 'Validacion', 'Escribe cuanto quieres mover');
      return;
    }
    try {
      const res = await envelopeService.move(
        moviendoDesde.categoryId, haciaCategoria, importe, diaDeCorte,
      );
      if (res.correct) {
        showToast('success', 'Movido');
        setMoviendoDesde(null);
        setHaciaCategoria(null);
        setCuanto('');
        cargar();
      } else {
        /* El backend explica por que no se puede ("en ese sobre no hay tanto
           para mover"), y ese texto es mas util que un "error" generico. */
        showToast('error', 'No se pudo mover', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo mover');
    }
  }, [moviendoDesde, haciaCategoria, cuanto, diaDeCorte, cargar, showToast]);

  const { ejecutando: moviendo, ejecutar: ejecutarMover } = useAccionUnica(mover);

  if (cargando) return <Spinner fullScreen />;

  if (error) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background }]}>
        <AppText variant="subtitle" style={styles.header}>Sobres</AppText>
        <EstadoError mensaje={error} onReintentar={cargar} />
      </View>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <AppText variant="subtitle" style={styles.header}>Sobres</AppText>

      <ScrollView
        contentContainerStyle={sobres.length === 0 ? styles.emptyContainer : styles.listContent}
        refreshControl={
          <RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />
        }
      >
        {sobres.length === 0 ? (
          <EmptyState
            title="Sin sobres todavia"
            message="Asigna un presupuesto a una categoria y aqui vas a ver cuanto te queda, contando lo que sobro del mes pasado."
            icon="✉️"
          />
        ) : (
          <>
            <Card style={styles.resumen}>
              {/* El titulo y la cifra son lo mismo; separados se oye "Para
                  repartir" y, tras una pausa, un numero sin dueño. */}
              <View accessible accessibilityLabel={`Para repartir: ${formatCurrency(paraRepartir)}`}>
                <AppText variant="caption" color={colors.textSecondary}>Para repartir</AppText>
                <AppText
                  variant="title"
                  color={paraRepartir < 0 ? colors.expense : colors.text}
                  testID="para-repartir"
                >
                  {formatCurrency(paraRepartir)}
                </AppText>
              </View>
              {deudaArrastrada > 0 && (
                <AppText variant="caption" color={colors.expense} testID="deuda-arrastrada">
                  Arrancas el mes con {formatCurrency(deudaArrastrada)} de menos porque el mes
                  pasado te pasaste.
                </AppText>
              )}
              <AppText variant="caption" color={colors.textMuted} style={styles.regla}>
                Lo que sobra en un sobre se queda. Lo que te pasaste se descuenta de lo que
                tienes para repartir.
              </AppText>
            </Card>

            {sobres.map((sobre) => {
              const tePasaste = sobre.available < 0;
              return (
                <TouchableOpacity
                  key={sobre.categoryId}
                  testID={`sobre-${sobre.categoryId}`}
                  activeOpacity={0.7}
                  onPress={() => {
                    setMoviendoDesde(sobre);
                    setHaciaCategoria(null);
                    setCuanto('');
                  }}
                  /* La fila entera es un boton, asi que sus cuatro lineas se
                     leen de una: nombre, cuanto queda y de donde sale. */
                  accessibilityRole="button"
                  accessibilityLabel={
                    `${sobre.categoryName}. ${sobre.label} ${formatCurrency(Math.abs(sobre.available))}. ` +
                    `${formatCurrency(sobre.allocated)} asignado` +
                    (sobre.carryover > 0 ? ` más ${formatCurrency(sobre.carryover)} que sobró del mes pasado` : '') +
                    (sobre.reimbursable ? '. Es dinero que te van a devolver' : '')
                  }
                  accessibilityHint={
                    tePasaste
                      ? 'Elige de qué otro sobre sale esta plata'
                      : 'Deja pasar plata de este sobre a otro'
                  }
                >
                  <Card style={styles.sobre}>
                    <View style={styles.filaSobre}>
                      <View style={styles.datos}>
                        <AppText variant="body">{sobre.categoryName}</AppText>
                        <AppText variant="caption" color={colors.textSecondary}>
                          {formatCurrency(sobre.allocated)} asignado
                          {sobre.carryover > 0
                            ? ` + ${formatCurrency(sobre.carryover)} del mes pasado`
                            : ''}
                        </AppText>
                        {sobre.reimbursable && (
                          <AppText variant="caption" color={colors.textMuted}>
                            Es dinero que te van a devolver
                          </AppText>
                        )}
                      </View>
                      <View style={styles.cifras}>
                        {/* El texto va SIEMPRE con la cifra: rojo y verde son
                            la misma casilla gris para mucha gente. */}
                        <AppText variant="caption" color={colors.textSecondary}>
                          {sobre.label}
                        </AppText>
                        <AppText
                          variant="body"
                          color={tePasaste ? colors.expense : colors.income}
                          testID={`disponible-${sobre.categoryId}`}
                        >
                          {formatCurrency(Math.abs(sobre.available))}
                        </AppText>
                      </View>
                    </View>
                  </Card>
                </TouchableOpacity>
              );
            })}

            {moviendoDesde && (
              <Card style={styles.mover}>
                <AppText variant="label">
                  Sacar de "{moviendoDesde.categoryName}" y poner en...
                </AppText>
                <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filaDestinos}>
                  {sobres
                    .filter((s) => s.categoryId !== moviendoDesde.categoryId)
                    .map((s) => {
                      const elegido = haciaCategoria === s.categoryId;
                      return (
                        <TouchableOpacity
                          key={s.categoryId}
                          testID={`destino-${s.categoryId}`}
                          onPress={() => setHaciaCategoria(s.categoryId)}
                          accessibilityRole="radio"
                          accessibilityState={{ selected: elegido }}
                          accessibilityLabel={`Poner en ${s.categoryName}`}
                          style={[
                            styles.chipDestino,
                            {
                              backgroundColor: elegido ? colors.primary : colors.surface,
                              borderColor: elegido ? colors.primary : colors.border,
                            },
                          ]}
                        >
                          <AppText variant="caption" color={elegido ? '#FFFFFF' : colors.text}>
                            {s.categoryName}
                          </AppText>
                        </TouchableOpacity>
                      );
                    })}
                </ScrollView>
                <Input
                  label="¿Cuanto?"
                  value={cuanto}
                  onChangeText={setCuanto}
                  placeholder="0"
                  keyboardType="numeric"
                />
                <Button
                  title="Mover"
                  accessibilityLabel={`Mover la plata desde ${moviendoDesde.categoryName}`}
                  onPress={ejecutarMover}
                  loading={moviendo}
                />
                <Button
                  title="Cancelar"
                  accessibilityLabel="Dejar la plata donde está"
                  onPress={() => setMoviendoDesde(null)}
                  variant="secondary"
                />
              </Card>
            )}
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
  regla: { marginTop: spacing.sm },
  sobre: { marginBottom: spacing.sm },
  filaSobre: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  datos: { flex: 1, marginRight: spacing.sm },
  cifras: { alignItems: 'flex-end' },
  mover: { marginTop: spacing.md },
  filaDestinos: { marginVertical: spacing.sm },
  chipDestino: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: 20,
    borderWidth: 1,
    marginRight: spacing.sm,
  },
});

export default EnvelopesScreen;
