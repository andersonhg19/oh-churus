import React, { useCallback, useState } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import AppText from '../../components/atoms/Text';
import Spinner from '../../components/atoms/Spinner';
import Encabezado from '../../components/molecules/Encabezado';
import EstadoError from '../../components/molecules/EstadoError';
import { COLORES_PERSONA } from '../presupuesto/PresupuestoScreen';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import { useAlcance } from '../../contexts/AlcanceContext';
import { useCarga, exigir } from '../../hooks/useCarga';
import { estadoService, EstadoDelPeriodo } from '../../services/estadoService';
import { formatCurrency } from '../../utils/format';

/*
 * RESPONSABILIDADES. "Las mias": cuanto me toca cubrir (facturas, ahorro y
 * bolsas), cuanto ya pague, cuanto falta y cuanto me queda libre, con la
 * cuenta a la vista para que se pueda comprobar. "De los dos": como se reparte
 * lo planeado del hogar entre las dos personas.
 */
const ResponsabilidadesScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const { corte, periodo, miembros } = useAlcance();
  const [vista, setVista] = useState<'MIAS' | 'DOS'>('MIAS');
  const [mio, setMio] = useState<EstadoDelPeriodo | null>(null);
  const [hogar, setHogar] = useState<EstadoDelPeriodo | null>(null);

  const traer = useCallback(async () => {
    if (!user) return;
    const [a, b] = await Promise.all([
      estadoService.periodo(corte, periodo.inicio, { tipo: 'PERSONA', userId: Number(user.userId) }),
      estadoService.periodo(corte, periodo.inicio, { tipo: 'HOGAR' }),
    ]);
    setMio(exigir(a) || null);
    setHogar(exigir(b) || null);
  }, [user, corte, periodo.inicio]);
  const { cargando, error, cargar } = useCarga(traer);
  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  const cabeza = <Encabezado titulo="Responsabilidades" atras conAlcance={false} />;
  if (cargando && !mio) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}>{cabeza}<Spinner fullScreen /></View>;
  if (error) return <View style={[styles.pantalla, { backgroundColor: colors.background }]}>{cabeza}<EstadoError mensaje={error} onReintentar={cargar} /></View>;

  const yo = hogar?.responsibilities.find((r) => String(r.userId) === String(user?.userId));
  const t = mio?.totals;
  const linea = (texto: string, valor: number, opciones: { signo?: string; color?: string; fuerte?: boolean } = {}) => (
    <View style={styles.linea}>
      <AppText variant="body" color={opciones.fuerte ? colors.text : colors.textSecondary} style={opciones.fuerte ? { fontWeight: '800' } : undefined}>{texto}</AppText>
      <AppText variant="body" style={{ fontWeight: '800', color: opciones.color || colors.text }}>
        {opciones.signo || ''}{formatCurrency(valor)}
      </AppText>
    </View>
  );
  const total = (hogar?.responsibilities || []).reduce((s, r) => s + r.total, 0);

  return (
    <View style={[styles.pantalla, { backgroundColor: colors.background }]}>
      {cabeza}
      <ScrollView contentContainerStyle={styles.contenido}>
        <View style={[styles.pestanas, { backgroundColor: colors.surfaceLight }]}>
          {(['MIAS', 'DOS'] as const).map((v) => (
            <TouchableOpacity key={v} style={[styles.pestana, vista === v && { backgroundColor: colors.primary }]}
              onPress={() => setVista(v)} accessibilityRole="tab" accessibilityState={{ selected: vista === v }}
              accessibilityLabel={v === 'MIAS' ? 'Mis responsabilidades' : 'Las de los dos'}>
              <AppText variant="caption" style={{ fontWeight: '700', color: vista === v ? '#1A1405' : colors.textSecondary }}>
                {v === 'MIAS' ? 'Las mías' : 'De los dos'}
              </AppText>
            </TouchableOpacity>
          ))}
        </View>

        {vista === 'MIAS' && yo && t && (
          <>
            <View style={styles.cajas}>
              {[['Me toca cubrir', yo.total], ['Ya pagué', yo.paid], ['Me falta pagar', yo.pending], ['Bolsas a mi cargo', yo.provisions]].map(([k, v]) => (
                <View key={k as string} style={[styles.caja, { backgroundColor: colors.surfaceLight }]}>
                  <AppText variant="caption" color={colors.textSecondary}>{k as string}</AppText>
                  <AppText variant="body" style={{ fontWeight: '800' }}>{formatCurrency(v as number)}</AppText>
                </View>
              ))}
            </View>
            <View style={[styles.cuenta, { backgroundColor: colors.surfaceLight }]}>
              <AppText variant="caption" color={colors.textSecondary}>De dónde sale lo libre</AppText>
              {linea('Mis ingresos del periodo', t.incomeExpected)}
              {linea('Gasté', t.spent, { signo: '− ' })}
              {linea('Ahorré', t.saved, { signo: '− ' })}
              {linea('Me falta pagar del plan', t.toPay, { signo: '− ' })}
              {linea('Aparte en bolsas', t.provisionsLeft, { signo: '− ' })}
              <View style={[styles.separador, { backgroundColor: colors.border }]} />
              {linea('Me queda libre', t.freeToSpend, { fuerte: true, color: t.freeToSpend >= 0 ? colors.income : colors.expense })}
              {t.incomePending > 0 && (
                <AppText variant="caption" color={colors.textSecondary}>
                  De los ingresos faltan {formatCurrency(t.incomePending)} por llegar: con lo recibido hoy, quedan {formatCurrency(t.freeNow)}.
                </AppText>
              )}
            </View>
            <TouchableOpacity style={[styles.enlace, { borderColor: colors.border }]} onPress={() => navigation.navigate('PorConfirmar')}
              accessibilityRole="button" accessibilityLabel="Ver y confirmar mis pagos pendientes">
              <AppText variant="body" style={{ fontWeight: '700' }}>Ver mis pagos pendientes</AppText>
            </TouchableOpacity>
          </>
        )}

        {vista === 'DOS' && hogar && (
          <>
            <View style={[styles.barra, { backgroundColor: colors.border }]}>
              {total > 0 && hogar.responsibilities.map((r) => {
                const i = miembros.findIndex((m) => m.userId === r.userId);
                return <View key={r.userId} style={{ width: `${(r.total / total) * 100}%`, backgroundColor: COLORES_PERSONA[(i < 0 ? 0 : i) % COLORES_PERSONA.length] }} />;
              })}
            </View>
            {hogar.responsibilities.map((r) => (
              <View key={r.userId} style={[styles.persona, { backgroundColor: colors.surfaceLight }]}>
                <View style={styles.linea}>
                  <AppText variant="subtitle">{r.name || 'Alguien'}</AppText>
                  <AppText variant="body" color={colors.textSecondary}>{r.sharePct}%</AppText>
                </View>
                {linea('Facturas y cuotas', r.bills)}
                {linea('Ahorro', r.savings)}
                {linea('Bolsas', r.provisions)}
                {linea('Total a cargo', r.total, { fuerte: true })}
                {linea('Pagado', r.paid, { color: colors.income })}
                {linea('Le falta', r.pending, { color: r.pending > 0 ? colors.pending : colors.income })}
              </View>
            ))}
          </>
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  pantalla: { flex: 1 },
  contenido: { padding: 16, gap: 12, paddingBottom: 32 },
  pestanas: { flexDirection: 'row', borderRadius: 12, padding: 4 },
  pestana: { flex: 1, alignItems: 'center', paddingVertical: 10, borderRadius: 9, minHeight: 44, justifyContent: 'center' },
  cajas: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  caja: { flexBasis: '48%', flexGrow: 1, borderRadius: 12, padding: 12, gap: 2 },
  cuenta: { borderRadius: 14, padding: 14, gap: 6 },
  linea: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  separador: { height: 1, marginVertical: 4 },
  enlace: { borderWidth: 1, borderRadius: 14, padding: 14, alignItems: 'center', minHeight: 52, justifyContent: 'center' },
  barra: { height: 12, borderRadius: 6, overflow: 'hidden', flexDirection: 'row' },
  persona: { borderRadius: 14, padding: 14, gap: 6 },
});

export default ResponsabilidadesScreen;
