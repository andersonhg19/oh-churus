import React, { useState } from 'react';
import { View, StyleSheet, TouchableOpacity, Modal } from 'react-native';
import { useNavigation, DrawerActions } from '@react-navigation/native';
import AppText from '../atoms/Text';
import Icono from '../atoms/Icono';
import { useTheme } from '../../contexts/ThemeContext';
import { useAlcance } from '../../contexts/AlcanceContext';
import { formatDateShort } from '../../utils/format';
import { volverOInicio } from '../../navigation/linking';

interface Props {
  titulo?: string;
  /** Con flecha atras en vez de menu. */
  atras?: boolean;
  /** Ensena y deja cambiar el alcance (Hogar / cada persona). */
  conAlcance?: boolean;
  /** Ensena y deja cambiar el periodo. */
  conPeriodo?: boolean;
  derecha?: React.ReactNode;
}

/*
 * La barra de arriba de todas las pantallas nuevas, como la de Monefy: el
 * menu (o atras), el titulo con el alcance que se toca para cambiarlo, y el
 * periodo con sus flechas. Cambiar el alcance o el periodo aqui los cambia en
 * toda la app.
 */
const Encabezado: React.FC<Props> = ({ titulo, atras, conAlcance = true, conPeriodo = true, derecha }) => {
  const { colors } = useTheme();
  const navigation = useNavigation<any>();
  const { alcance, setAlcance, miembros, etiquetaAlcance, periodo, anterior, siguiente, puedeSeguir } = useAlcance();
  const [eligiendo, setEligiendo] = useState(false);

  const opciones = [{ clave: 'HOGAR', nombre: 'Hogar', elegir: () => setAlcance({ tipo: 'HOGAR' }) },
    ...miembros.map((m) => ({ clave: String(m.userId), nombre: m.nombre,
      elegir: () => setAlcance({ tipo: 'PERSONA', userId: m.userId }) }))];
  const actual = alcance.tipo === 'HOGAR' ? 'HOGAR' : String(alcance.userId);

  return (
    <View style={[styles.barra, { backgroundColor: colors.surface }]}>
      <TouchableOpacity
        style={styles.boton}
        onPress={() => (atras ? volverOInicio(navigation) : navigation.dispatch(DrawerActions.openDrawer()))}
        accessibilityRole="button"
        accessibilityLabel={atras ? 'Volver' : 'Abrir el menú'}
      >
        <Icono nombre={atras ? 'back' : 'menu'} color={colors.text} tamano={24} />
      </TouchableOpacity>

      <View style={styles.centro}>
        {conAlcance ? (
          <TouchableOpacity
            onPress={() => setEligiendo(true)}
            style={styles.titulo}
            accessibilityRole="button"
            accessibilityLabel={`Viendo ${etiquetaAlcance}. Cambiar de quién se ven las cifras`}
          >
            <AppText variant="body" style={{ fontWeight: '700' }}>
              {titulo ? `${titulo} · ` : ''}{etiquetaAlcance}
            </AppText>
            <Icono nombre="down" color={colors.textSecondary} tamano={16} />
          </TouchableOpacity>
        ) : (
          <AppText variant="body" style={{ fontWeight: '700' }}>{titulo}</AppText>
        )}
        {conPeriodo && (
          <View style={styles.periodo}>
            <TouchableOpacity onPress={anterior} style={styles.flecha} accessibilityRole="button" accessibilityLabel="Periodo anterior">
              <Icono nombre="left" color={colors.textSecondary} tamano={18} />
            </TouchableOpacity>
            <AppText variant="caption" color={colors.textSecondary}>
              {formatDateShort(periodo.inicio)} – {formatDateShort(periodo.fin)}
            </AppText>
            <TouchableOpacity
              onPress={siguiente}
              disabled={!puedeSeguir}
              style={[styles.flecha, !puedeSeguir && { opacity: 0.3 }]}
              accessibilityRole="button"
              accessibilityLabel="Periodo siguiente"
              accessibilityState={{ disabled: !puedeSeguir }}
            >
              <Icono nombre="right" color={colors.textSecondary} tamano={18} />
            </TouchableOpacity>
          </View>
        )}
      </View>

      <View style={styles.boton}>{derecha}</View>

      <Modal visible={eligiendo} transparent animationType="fade" onRequestClose={() => setEligiendo(false)}>
        <TouchableOpacity style={styles.fondo} activeOpacity={1} onPress={() => setEligiendo(false)}
          accessibilityRole="button" accessibilityLabel="Cerrar sin cambiar">
          <View style={[styles.hoja, { backgroundColor: colors.surface }]}>
            <AppText variant="subtitle" style={{ marginBottom: 8 }}>Ver las cifras de</AppText>
            {opciones.map((o) => (
              <TouchableOpacity
                key={o.clave}
                style={[styles.opcion, { borderColor: colors.border }, actual === o.clave && { backgroundColor: colors.primary }]}
                onPress={() => { o.elegir(); setEligiendo(false); }}
                accessibilityRole="radio"
                accessibilityState={{ selected: actual === o.clave }}
                accessibilityLabel={`Ver ${o.nombre}`}
              >
                <AppText variant="body" style={{ fontWeight: '700', color: actual === o.clave ? '#1A1405' : colors.text }}>
                  {o.nombre}
                </AppText>
                <AppText variant="caption" style={{ color: actual === o.clave ? '#1A1405' : colors.textSecondary }}>
                  {o.clave === 'HOGAR' ? 'Lo de los dos' : 'Lo que paga y le toca'}
                </AppText>
              </TouchableOpacity>
            ))}
          </View>
        </TouchableOpacity>
      </Modal>
    </View>
  );
};

const styles = StyleSheet.create({
  barra: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 8, paddingTop: 12, paddingBottom: 8 },
  boton: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  centro: { flex: 1, alignItems: 'center' },
  titulo: { flexDirection: 'row', alignItems: 'center', gap: 4, minHeight: 32 },
  periodo: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  flecha: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center' },
  fondo: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' },
  hoja: { padding: 20, borderTopLeftRadius: 24, borderTopRightRadius: 24, gap: 8 },
  opcion: { padding: 14, borderRadius: 14, borderWidth: 1, gap: 2 },
});

export default Encabezado;
