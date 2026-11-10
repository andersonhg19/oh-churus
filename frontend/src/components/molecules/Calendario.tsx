import React, { useEffect, useState } from 'react';
import { View, StyleSheet, TouchableOpacity, Modal } from 'react-native';
import AppText from '../atoms/Text';
import Icono from '../atoms/Icono';
import { useTheme } from '../../contexts/ThemeContext';
import { textoLegibleSobre } from '../../theme/contraste';
import { fechaLocalISO, getMonthName } from '../../utils/format';

const DIAS_SEMANA = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];
const NOMBRES_DIA = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];

const aISO = (y: number, m: number, d: number) => fechaLocalISO(new Date(y, m, d));
const partes = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return { y, m: m - 1, d };
};

/** Mueve una fecha AAAA-MM-DD unos dias (negativo = antes). */
export const moverDias = (iso: string, dias: number) => {
  const { y, m, d } = partes(iso);
  return aISO(y, m, d + dias);
};

/** Hasta donde se puede anotar: un año despues de hoy (cuotas, pagos ya programados). */
export const maximoPorDefecto = (hoy: string = fechaLocalISO()) => {
  const { y, m, d } = partes(hoy);
  return aISO(y + 1, m, d);
};

/**
 * Las casillas de un mes, empezando el lunes: null para los huecos antes del
 * dia 1 y luego 1..N. Siempre multiplo de 7 para que la cuadricula cierre.
 */
export function casillasDelMes(anio: number, mes: number): (number | null)[] {
  const primero = new Date(anio, mes, 1).getDay(); // 0 = domingo
  const huecos = (primero + 6) % 7;
  const dias = new Date(anio, mes + 1, 0).getDate();
  const c: (number | null)[] = [...Array(huecos).fill(null), ...Array.from({ length: dias }, (_, i) => i + 1)];
  while (c.length % 7 !== 0) c.push(null);
  return c;
}

/** "10 de noviembre de 2026" para el lector de pantalla y el titulo. */
export const fechaLarga = (iso: string) => {
  const { y, m, d } = partes(iso);
  return `${d} de ${getMonthName(m).toLowerCase()} de ${y}`;
};

interface Props {
  visible: boolean;
  fecha: string;
  onElegir: (fecha: string) => void;
  onCerrar: () => void;
  /** AAAA-MM-DD; por defecto un año despues de hoy. */
  maximo?: string;
  titulo?: string;
}

/*
 * EL CALENDARIO. Antes la fecha solo se movia de a un dia ("− 1 día"), y
 * anotar algo de hace dos semanas eran 14 toques. Ahora: Hoy y Ayer a mano, y
 * el mes en cuadricula, un toque por dia. Se puede ir a meses pasados sin
 * limite y al futuro hasta un año.
 */
const Calendario: React.FC<Props> = ({ visible, fecha, onElegir, onCerrar, maximo, titulo = '¿Cuándo fue?' }) => {
  const { colors } = useTheme();
  const hoy = fechaLocalISO();
  const ayer = moverDias(hoy, -1);
  const tope = maximo || maximoPorDefecto(hoy);
  const [mes, setMes] = useState(() => { const p = partes(fecha || hoy); return { y: p.y, m: p.m }; });

  /* Al abrir, el mes de la fecha elegida. */
  useEffect(() => {
    if (visible) { const p = partes(fecha || hoy); setMes({ y: p.y, m: p.m }); }
  }, [visible, fecha, hoy]);

  const topeP = partes(tope);
  const puedeSeguir = mes.y < topeP.y || (mes.y === topeP.y && mes.m < topeP.m);
  const mover = (n: number) => setMes(({ y, m }) => {
    const f = new Date(y, m + n, 1);
    return { y: f.getFullYear(), m: f.getMonth() };
  });
  const elegir = (iso: string) => { onElegir(iso); onCerrar(); };
  const tintaPrimaria = textoLegibleSobre(colors.primary);

  const chip = (texto: string, iso: string) => {
    const activo = fecha === iso;
    return (
      <TouchableOpacity key={texto} style={[styles.chip, { backgroundColor: activo ? colors.primary : colors.surfaceLight }]}
        onPress={() => elegir(iso)} accessibilityRole="button" accessibilityState={{ selected: activo }} accessibilityLabel={texto}>
        <AppText variant="body" style={{ fontWeight: '700', color: activo ? tintaPrimaria : colors.text }}>{texto}</AppText>
      </TouchableOpacity>
    );
  };

  const casillas = casillasDelMes(mes.y, mes.m);
  const filas: (number | null)[][] = [];
  for (let i = 0; i < casillas.length; i += 7) filas.push(casillas.slice(i, i + 7));

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onCerrar}>
      <View style={styles.fondo}>
        <TouchableOpacity style={{ flex: 1 }} onPress={onCerrar} accessibilityRole="button" accessibilityLabel="Cerrar el calendario" />
        <View style={[styles.hoja, { backgroundColor: colors.surface }]}>
          <View style={styles.cabeza}>
            <AppText variant="subtitle" style={{ flex: 1 }}>{titulo}</AppText>
            {chip('Hoy', hoy)}
            {chip('Ayer', ayer)}
          </View>

          <View style={styles.cabeza}>
            <TouchableOpacity style={styles.flecha} onPress={() => mover(-1)} accessibilityRole="button" accessibilityLabel="Mes anterior">
              <Icono nombre="left" color={colors.text} />
            </TouchableOpacity>
            <AppText variant="body" style={{ flex: 1, textAlign: 'center', fontWeight: '800' }}>
              {`${getMonthName(mes.m)} ${mes.y}`}
            </AppText>
            <TouchableOpacity style={[styles.flecha, !puedeSeguir && { opacity: 0.3 }]} onPress={() => mover(1)} disabled={!puedeSeguir}
              accessibilityRole="button" accessibilityLabel="Mes siguiente" accessibilityState={{ disabled: !puedeSeguir }}>
              <Icono nombre="right" color={colors.text} />
            </TouchableOpacity>
          </View>

          <View style={styles.fila}>
            {DIAS_SEMANA.map((d, i) => (
              <View key={i} style={styles.celda} accessibilityLabel={NOMBRES_DIA[i]}>
                <AppText variant="caption" color={colors.textSecondary} style={{ fontWeight: '700' }}>{d}</AppText>
              </View>
            ))}
          </View>
          {filas.map((f, i) => (
            <View key={i} style={styles.fila}>
              {f.map((d, j) => {
                if (d === null) return <View key={j} style={styles.celda} />;
                const iso = aISO(mes.y, mes.m, d);
                const elegido = iso === fecha;
                const esHoy = iso === hoy;
                const fuera = iso > tope;
                return (
                  <TouchableOpacity key={j} style={styles.celda} disabled={fuera} onPress={() => elegir(iso)}
                    accessibilityRole="button" accessibilityState={{ selected: elegido, disabled: fuera }}
                    accessibilityLabel={`${fechaLarga(iso)}${esHoy ? ', hoy' : ''}`}>
                    <View style={[styles.dia,
                      elegido && { backgroundColor: colors.primary },
                      esHoy && !elegido && { borderWidth: 2, borderColor: colors.primary }]}>
                      <AppText variant="body" style={{
                        fontWeight: elegido || esHoy ? '800' : '500',
                        color: elegido ? tintaPrimaria : fuera ? colors.textMuted : colors.text,
                        opacity: fuera ? 0.5 : 1,
                      }}>{d}</AppText>
                    </View>
                  </TouchableOpacity>
                );
              })}
            </View>
          ))}
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  fondo: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)' },
  hoja: { padding: 16, paddingBottom: 24, gap: 6, borderTopLeftRadius: 24, borderTopRightRadius: 24, width: '100%', maxWidth: 480, alignSelf: 'center' },
  cabeza: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  chip: { minHeight: 44, minWidth: 64, paddingHorizontal: 14, borderRadius: 22, alignItems: 'center', justifyContent: 'center' },
  flecha: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  fila: { flexDirection: 'row' },
  celda: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  dia: { width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
});

export default Calendario;
