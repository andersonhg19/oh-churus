import React, { useCallback, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Button from '../../components/atoms/Button';
import Spinner from '../../components/atoms/Spinner';
import EstadoError from '../../components/molecules/EstadoError';
import { spacing } from '../../theme';
import { authService } from '../../services/authService';
import { preferenciasService } from '../../services/adminService';
import { useCarga, exigir, mensajeDeError } from '../../hooks/useCarga';
import { useToast } from '../../contexts/ToastContext';
import { configurarFormatoDeMoneda, formatCurrency } from '../../utils/format';

const MONEDAS = [
  { codigo: 'COP', nombre: 'Peso colombiano' },
  { codigo: 'USD', nombre: 'Dólar' },
  { codigo: 'EUR', nombre: 'Euro' },
  { codigo: 'MXN', nombre: 'Peso mexicano' },
  { codigo: 'ARS', nombre: 'Peso argentino' },
  { codigo: 'CLP', nombre: 'Peso chileno' },
  { codigo: 'PEN', nombre: 'Sol' },
  { codigo: 'BRL', nombre: 'Real' },
];

const DECIMALES = [0, 2];

/**
 * Con que moneda y cuantos decimales se escriben los importes.
 *
 * Hasta la ola 4 esto estaba escrito a mano en el formateador: "es-CO", el
 * simbolo "$" y cero decimales. Cambiarlo obligaba a tocar codigo y volver a
 * publicar la app.
 *
 * La vista previa de arriba no es decoracion: los decimales son la clase de
 * ajuste que solo se entiende viendolo, y verlo antes de guardar evita el
 * viaje de cambiar, salir, mirar y volver.
 */
const CurrencyConfigScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();

  const [moneda, setMoneda] = useState('COP');
  const [decimales, setDecimales] = useState(0);
  const [propia, setPropia] = useState(false);
  const [guardando, setGuardando] = useState(false);

  const traer = useCallback(async () => {
    const datos = exigir(await preferenciasService.mias());
    if (datos) {
      setMoneda(datos.currency);
      setDecimales(datos.decimalPlaces);
      setPropia(datos.propia);
    }
  }, []);

  const { cargando, error, cargar } = useCarga(traer);

  useFocusEffect(
    useCallback(() => {
      cargar();
    }, [cargar]),
  );

  const guardar = async () => {
    setGuardando(true);
    try {
      const res = await authService.updateUser({
        id: user?.userId,
        name: user?.name,
        email: user?.email,
        currency: moneda,
        decimalPlaces: decimales,
      } as any);
      if (!res.correct) {
        showToast('error', res.message);
        return;
      }
      /* Se aplica sin esperar a la proxima sesion: el formateador vive en el
         modulo y aqui ya sabemos lo que el servidor acaba de aceptar. */
      configurarFormatoDeMoneda({ currency: moneda, decimalPlaces: decimales });
      setPropia(true);
      showToast('success', 'Asi se veran tus importes');
    } catch (err) {
      showToast('error', mensajeDeError(err));
    } finally {
      setGuardando(false);
    }
  };

  if (cargando) return <Spinner fullScreen />;

  if (error) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background }]}>
        <EstadoError mensaje={error} onReintentar={cargar} />
      </View>
    );
  }

  /* Se calcula con lo elegido AHORA, sin tocar el formateador global: si se
     configurara aqui, cambiar de opcion y salirse sin guardar dejaria la app
     entera con una moneda que nadie confirmo. */
  const ejemplo = () => {
    const valor = 1234567.89;
    const simbolos: Record<string, string> = {
      COP: '$', USD: 'US$', EUR: '€', MXN: 'MX$', ARS: 'AR$',
      CLP: 'CL$', PEN: 'S/', BRL: 'R$',
    };
    try {
      return `${simbolos[moneda] ?? `${moneda} `}${valor.toLocaleString('es-CO', {
        minimumFractionDigits: decimales,
        maximumFractionDigits: decimales,
      })}`;
    } catch {
      return formatCurrency(valor);
    }
  };

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.contenido}
    >
      <Card style={styles.tarjeta}>
        <AppText variant="caption" color={colors.textSecondary}>
          Asi se veran tus importes
        </AppText>
        <AppText variant="title" testID="vista-previa">
          {ejemplo()}
        </AppText>
        {!propia && (
          <AppText variant="caption" color={colors.textMuted} style={styles.aviso}>
            Ahora mismo usas lo que esta configurado para toda la aplicacion. En
            cuanto guardes, sera tu eleccion.
          </AppText>
        )}
      </Card>

      <AppText variant="caption" color={colors.textSecondary} style={styles.seccion}>
        Moneda
      </AppText>
      <View style={styles.opciones}>
        {MONEDAS.map((m) => {
          const elegida = m.codigo === moneda;
          return (
            <TouchableOpacity
              key={m.codigo}
              onPress={() => setMoneda(m.codigo)}
              style={[
                styles.chip,
                {
                  backgroundColor: elegida ? colors.primary : 'transparent',
                  borderColor: elegida ? colors.primary : colors.border,
                },
              ]}
              accessibilityRole="radio"
              accessibilityState={{ selected: elegida }}
              accessibilityLabel={`Usar ${m.nombre}`}
              testID={`moneda-${m.codigo}`}
            >
              <AppText variant="caption" color={elegida ? colors.background : colors.textSecondary}>
                {m.codigo}
              </AppText>
            </TouchableOpacity>
          );
        })}
      </View>

      <AppText variant="caption" color={colors.textSecondary} style={styles.seccion}>
        Decimales
      </AppText>
      <View style={styles.opciones}>
        {DECIMALES.map((d) => {
          const elegido = d === decimales;
          return (
            <TouchableOpacity
              key={d}
              onPress={() => setDecimales(d)}
              style={[
                styles.chip,
                {
                  backgroundColor: elegido ? colors.primary : 'transparent',
                  borderColor: elegido ? colors.primary : colors.border,
                },
              ]}
              accessibilityRole="radio"
              accessibilityState={{ selected: elegido }}
              accessibilityLabel={
                d === 0 ? 'Sin decimales, como 1.234' : 'Con dos decimales, como 1.234,56'
              }
              testID={`decimales-${d}`}
            >
              <AppText variant="caption" color={elegido ? colors.background : colors.textSecondary}>
                {d === 0 ? 'Ninguno' : 'Dos'}
              </AppText>
            </TouchableOpacity>
          );
        })}
      </View>

      <Button
        title="Guardar"
        loading={guardando}
        onPress={guardar}
        accessibilityLabel="Guardar la moneda y los decimales elegidos"
        testID="guardar-moneda"
        style={styles.boton}
      />
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  contenido: { padding: spacing.md, paddingBottom: spacing.xl },
  tarjeta: { marginBottom: spacing.md },
  aviso: { marginTop: spacing.sm },
  seccion: { marginBottom: spacing.sm, textTransform: 'uppercase' },
  opciones: { flexDirection: 'row', flexWrap: 'wrap', marginBottom: spacing.md },
  chip: {
    borderWidth: 1,
    borderRadius: 16,
    paddingHorizontal: spacing.md,
    minHeight: 44,
    justifyContent: 'center',
    marginRight: spacing.sm,
    marginBottom: spacing.sm,
  },
  boton: { marginTop: spacing.md },
});

export default CurrencyConfigScreen;
