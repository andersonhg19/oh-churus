import { fechaLocalISO } from '../../utils/format';
import React, { useState, useCallback, useEffect, useRef } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl, Modal, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Button from '../../components/atoms/Button';
import Input from '../../components/atoms/Input';
import Card from '../../components/atoms/Card';
import Spinner from '../../components/atoms/Spinner';
import { spacing } from '../../theme';
import { fastingService } from '../../services/fastingService';
import { useFocusEffect } from '@react-navigation/native';
import Svg, { Circle } from 'react-native-svg';

const FastingDashboardScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();

  const [plan, setPlan] = useState<any>(null);
  const [session, setSession] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const timerRef = useRef<any>(null);

  const [water, setWater] = useState<any>(null);
  const [waterBusy, setWaterBusy] = useState(false);
  const [achievements, setAchievements] = useState<any[]>([]);

  // Modal for editing time
  const [showTimeModal, setShowTimeModal] = useState<'start' | 'stop' | null>(null);
  const [editDate, setEditDate] = useState('');
  const [editTime, setEditTime] = useState('');

  const fetchData = useCallback(async () => {
    if (!user) return;
    try {
      const [planRes, sessionRes, waterRes, achRes] = await Promise.all([
        fastingService.getPlan(user.userId),
        fastingService.getActiveSession(user.userId),
        fastingService.getWaterToday(user.userId),
        fastingService.getAchievements(user.userId),
      ]);
      if (planRes.correct) setPlan(planRes.object);
      if (sessionRes.correct) setSession(sessionRes.object);
      if (waterRes.correct) setWater(waterRes.object);
      if (achRes.correct) setAchievements(achRes.object || []);
    } catch { /* silent */ }
    finally { setLoading(false); }
  }, [user]);

  useFocusEffect(useCallback(() => { setLoading(true); fetchData(); }, [fetchData]));

  // Timer
  useEffect(() => {
    if (session?.status === 'IN_PROGRESS' && session.startTime) {
      const updateElapsed = () => {
        const start = new Date(session.startTime).getTime();
        setElapsed(Math.floor((Date.now() - start) / 1000));
      };
      updateElapsed();
      timerRef.current = setInterval(updateElapsed, 1000);
      return () => clearInterval(timerRef.current);
    } else {
      setElapsed(0);
    }
  }, [session]);

  const openStartModal = () => {
    const now = new Date();
    setEditDate(fechaLocalISO(now));
    setEditTime(now.toTimeString().substring(0, 5));
    setShowTimeModal('start');
  };

  const openStopModal = () => {
    const now = new Date();
    setEditDate(fechaLocalISO(now));
    setEditTime(now.toTimeString().substring(0, 5));
    setShowTimeModal('stop');
  };

  const handleConfirmAction = async () => {
    setActing(true);
    const dateTime = `${editDate}T${editTime}:00`;
    try {
      if (showTimeModal === 'start') {
        const res = await fastingService.startFasting(user!.userId, dateTime);
        if (res.correct) { setSession(res.object); showToast('success', 'Ayuno iniciado'); }
        else showToast('error', 'Error', res.message);
      } else {
        const res = await fastingService.stopFasting(user!.userId, dateTime);
        if (res.correct) {
          setSession(res.object);
          showToast('success', res.object?.status === 'COMPLETED' ? 'Ayuno completado!' : 'Ayuno finalizado');
          // Refresh achievements after completing
          const achRes = await fastingService.getAchievements(user!.userId);
          if (achRes.correct) setAchievements(achRes.object || []);
        } else showToast('error', 'Error', res.message);
      }
    } catch { showToast('error', 'Error'); }
    finally { setActing(false); setShowTimeModal(null); }
  };

  const handleCancel = async () => {
    setActing(true);
    try {
      const res = await fastingService.cancelFasting(user!.userId);
      if (res.correct) { setSession(null); showToast('info', 'Ayuno cancelado'); }
    } catch { showToast('error', 'Error'); }
    finally { setActing(false); }
  };

  const formatTimer = (secs: number) => {
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    const s = secs % 60;
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  const formatDT = (dt: string) => {
    if (!dt) return '--:--';
    const d = new Date(dt);
    return d.toLocaleString('es-CO', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  };

  if (loading) return <Spinner fullScreen />;

  const isActive = session?.status === 'IN_PROGRESS';
  const totalSeconds = (plan?.fastingHours || 16) * 3600;
  const progress = isActive ? Math.min(elapsed / totalSeconds, 1) : 0;
  const elapsedHours = elapsed / 3600;

  // Metabolic zones
  const ZONES = [
    { minH: 0, maxH: 4, name: 'Digestión', icon: '🍽️', color: '#FF9800', desc: 'Tu cuerpo procesa los alimentos' },
    { minH: 4, maxH: 8, name: 'Quema de grasa', icon: '🔥', color: '#FF5722', desc: 'Insulina baja, inicia la quema' },
    { minH: 8, maxH: 12, name: 'Quema acelerada', icon: '⚡', color: '#E91E63', desc: 'Tu cuerpo usa grasa como energia' },
    { minH: 12, maxH: 16, name: 'Autofagia', icon: '🧬', color: '#9C27B0', desc: 'Reparacion celular activada' },
    { minH: 16, maxH: 24, name: 'Cetosis profunda', icon: '🧠', color: '#2196F3', desc: 'Maximo rendimiento metabolico' },
    { minH: 24, maxH: 999, name: 'Ayuno extendido', icon: '🏆', color: '#00BCD4', desc: 'Nivel avanzado de cetosis' },
  ];
  const currentZone = isActive ? ZONES.find(z => elapsedHours >= z.minH && elapsedHours < z.maxH) || ZONES[0] : null;
  const nextZone = isActive && currentZone ? ZONES.find(z => z.minH > elapsedHours) : null;

  const size = 240;
  const strokeWidth = 14;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const progressOffset = circumference - progress * circumference;
  const goalReached = isActive && progress >= 1;
  const progressColor = goalReached ? colors.income : (currentZone ? currentZone.color : colors.primary);

  const statusLabel = isActive ? (currentZone?.name || 'En ayuno') : session?.status === 'COMPLETED' ? 'Completado' : session?.status === 'INCOMPLETE' ? 'Incompleto' : 'Sin iniciar';
  const statusColor = isActive ? progressColor : session?.status === 'COMPLETED' ? colors.income : session?.status === 'INCOMPLETE' ? colors.expense : colors.textMuted;

  return (
    <>
      <ScrollView
        style={[styles.container, { backgroundColor: colors.background }]}
        contentContainerStyle={styles.content}
        refreshControl={<RefreshControl refreshing={false} onRefresh={fetchData} tintColor={colors.primary} />}
      >
        <AppText variant="subtitle" style={styles.title}>Ayuno Intermitente 🕐</AppText>

        {!plan ? (
          <Card style={styles.noPlan}>
            <AppText variant="body" align="center">No tienes un plan configurado</AppText>
            <AppText variant="caption" color={colors.textSecondary} align="center" style={{ marginTop: spacing.xs }}>
              Ve a Configuracion para elegir tu plan
            </AppText>
          </Card>
        ) : (
          <>
            <Card style={styles.planCard}>
              <AppText variant="caption" color={colors.textSecondary}>Plan activo</AppText>
              <AppText variant="subtitle" color={colors.primary}>{plan.fastingHours}:{plan.eatingHours}</AppText>
              <AppText variant="caption" color={statusColor}>{statusLabel}</AppText>
            </Card>

            <View style={styles.timerContainer}>
              <Svg width={size} height={size}>
                <Circle cx={size / 2} cy={size / 2} r={radius} stroke={colors.border} strokeWidth={strokeWidth} fill="none" opacity={0.3} />
                {isActive && (
                  <Circle cx={size / 2} cy={size / 2} r={radius} stroke={progressColor} strokeWidth={strokeWidth} fill="none"
                    strokeDasharray={`${circumference}`} strokeDashoffset={progressOffset} strokeLinecap="round"
                    rotation="-90" origin={`${size / 2}, ${size / 2}`} />
                )}
              </Svg>
              <View style={styles.timerText}>
                {isActive && currentZone && (
                  <AppText style={{ fontSize: 28, marginBottom: 2 }}>{currentZone.icon}</AppText>
                )}
                <AppText style={[styles.timerValue, { color: isActive ? progressColor : colors.textMuted }]}>
                  {isActive ? formatTimer(elapsed) : formatTimer(0)}
                </AppText>
                <AppText variant="caption" color={statusColor} style={{ fontWeight: '700' }}>
                  {statusLabel}
                </AppText>
              </View>
            </View>

            {/* Zona actual + descripción */}
            {isActive && currentZone && (
              <Card style={[styles.zoneCard, { borderLeftColor: currentZone.color, borderLeftWidth: 4 }]}>
                <AppText variant="body" style={{ color: currentZone.color, fontWeight: '700' }}>
                  {currentZone.icon} {currentZone.name}
                </AppText>
                <AppText variant="caption" color={colors.textSecondary}>{currentZone.desc}</AppText>
                {nextZone && (
                  <AppText variant="caption" color={colors.textMuted} style={{ marginTop: 4 }}>
                    Siguiente: {nextZone.icon} {nextZone.name} en {Math.ceil(nextZone.minH - elapsedHours)}h
                  </AppText>
                )}
              </Card>
            )}

            {/* Zonas timeline */}
            {isActive && (
              <View style={styles.zonesTimeline}>
                {ZONES.map((z, i) => {
                  const reached = elapsedHours >= z.minH;
                  const current = elapsedHours >= z.minH && elapsedHours < z.maxH;
                  return (
                    <View key={i} style={styles.zoneStep}>
                      <View style={[styles.zoneDot, {
                        backgroundColor: reached ? z.color : colors.border,
                        width: current ? 14 : 10,
                        height: current ? 14 : 10,
                        borderRadius: current ? 7 : 5,
                      }]} />
                      <AppText variant="caption" style={{
                        color: reached ? z.color : colors.textMuted,
                        fontSize: 10,
                        fontWeight: current ? '700' : '400',
                      }}>{z.minH}h</AppText>
                    </View>
                  );
                })}
              </View>
            )}

            {/* Horas inicio / objetivo */}
            {isActive && (
              <View style={styles.timesRow}>
                <View style={styles.timeCol}>
                  <AppText variant="caption" color={colors.textSecondary}>Inicio</AppText>
                  <AppText variant="body">{formatDT(session.startTime)}</AppText>
                </View>
                <View style={styles.timeCol}>
                  <AppText variant="caption" color={colors.textSecondary}>Objetivo</AppText>
                  <AppText variant="body" color={colors.primary}>{formatDT(session.targetEndTime)}</AppText>
                </View>
              </View>
            )}

            <View style={styles.actions}>
              {!isActive ? (
                <Button title="Iniciar Ayuno" onPress={openStartModal} size="large" />
              ) : (
                <>
                  <Button title="Finalizar Ayuno" onPress={openStopModal} size="large" />
                  <Button title="Cancelar" onPress={handleCancel} variant="outline" style={{ marginTop: spacing.sm }} />
                </>
              )}
            </View>

            {/* Water Tracker */}
            <Card style={styles.waterCard}>
              <View style={styles.waterHeader}>
                <AppText variant="label">💧 Agua</AppText>
                <AppText variant="caption" color={colors.textSecondary}>
                  {water?.glasses || 0} / {water?.goalGlasses || 8} vasos
                </AppText>
              </View>
              <View style={styles.waterGlasses}>
                {Array.from({ length: Math.max(water?.goalGlasses || 8, 1) }).map((_, i) => (
                  <View key={i} style={[styles.waterGlass, {
                    backgroundColor: i < (water?.glasses || 0) ? '#4FC3F7' : colors.border,
                  }]} />
                ))}
              </View>
              <View style={styles.waterActions}>
                <TouchableOpacity
                  style={[styles.waterBtn, { backgroundColor: '#4FC3F7', opacity: waterBusy ? 0.5 : 1 }]}
                  disabled={waterBusy}
                  onPress={async () => {
                    if (!user || waterBusy) return;
                    setWaterBusy(true);
                    // Optimistic update
                    setWater((prev: any) => prev ? { ...prev, glasses: (prev.glasses || 0) + 1 } : prev);
                    try {
                      const res = await fastingService.addWater(user.userId, 1);
                      if (res.correct) setWater(res.object);
                    } catch { showToast('error', 'Error al registrar agua'); }
                    finally { setWaterBusy(false); }
                  }}
                >
                  <AppText variant="body" color="#FFF">+ 1 vaso</AppText>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.waterBtn, { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, opacity: waterBusy || (water?.glasses || 0) <= 0 ? 0.5 : 1 }]}
                  disabled={waterBusy || (water?.glasses || 0) <= 0}
                  onPress={async () => {
                    if (!user || waterBusy || (water?.glasses || 0) <= 0) return;
                    setWaterBusy(true);
                    setWater((prev: any) => prev ? { ...prev, glasses: Math.max(0, (prev.glasses || 0) - 1) } : prev);
                    try {
                      const res = await fastingService.addWater(user.userId, -1);
                      if (res.correct) setWater(res.object);
                    } catch { showToast('error', 'Error'); }
                    finally { setWaterBusy(false); }
                  }}
                >
                  <AppText variant="body">- 1</AppText>
                </TouchableOpacity>
              </View>
            </Card>

            {/* Achievements */}
            {achievements.length > 0 && (
              <Card style={styles.achieveCard}>
                <AppText variant="label" style={{ marginBottom: spacing.sm }}>🏆 Logros</AppText>
                <View style={styles.achieveGrid}>
                  {achievements.map((a: any) => (
                    <View key={a.code} style={[styles.achieveItem, { opacity: a.unlocked ? 1 : 0.3 }]}>
                      <AppText style={{ fontSize: 24 }}>{a.icon}</AppText>
                      <AppText variant="caption" align="center" numberOfLines={1}>{a.name}</AppText>
                    </View>
                  ))}
                </View>
              </Card>
            )}
          </>
        )}
      </ScrollView>

      {/* Modal para editar fecha/hora */}
      <Modal visible={!!showTimeModal} transparent animationType="fade" onRequestClose={() => setShowTimeModal(null)}>
        <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setShowTimeModal(null)}>
          <TouchableOpacity activeOpacity={1} style={[styles.modalContent, { backgroundColor: colors.surface }]}>
            <AppText variant="subtitle" style={styles.modalTitle}>
              {showTimeModal === 'start' ? 'Iniciar Ayuno' : 'Finalizar Ayuno'}
            </AppText>
            <AppText variant="caption" color={colors.textSecondary} style={{ marginBottom: spacing.md }}>
              Ajusta la fecha y hora si es diferente a ahora
            </AppText>
            <Input label="Fecha" value={editDate} onChangeText={setEditDate} placeholder="YYYY-MM-DD" />
            <Input label="Hora" value={editTime} onChangeText={setEditTime} placeholder="HH:MM" />
            <View style={styles.modalActions}>
              <Button title="Cancelar" onPress={() => setShowTimeModal(null)} variant="outline" style={styles.modalBtn} />
              <Button title="Confirmar" onPress={handleConfirmAction} loading={acting} style={styles.modalBtn} />
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.md, paddingBottom: spacing.xxl },
  title: { marginBottom: spacing.md },
  noPlan: { padding: spacing.xl, alignItems: 'center' },
  planCard: { alignItems: 'center', marginBottom: spacing.md },
  timerContainer: { alignItems: 'center', justifyContent: 'center', marginVertical: spacing.md },
  timerText: { position: 'absolute', alignItems: 'center' },
  timerValue: { fontSize: 36, fontWeight: '700' },
  timesRow: { flexDirection: 'row', justifyContent: 'space-around', marginBottom: spacing.md },
  timeCol: { alignItems: 'center' },
  zoneCard: { marginBottom: spacing.md, borderRadius: 12 },
  zonesTimeline: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingHorizontal: spacing.sm, marginBottom: spacing.md,
  },
  zoneStep: { alignItems: 'center', gap: 2 },
  zoneDot: { borderRadius: 7 },
  actions: { marginTop: spacing.md },
  waterCard: { marginTop: spacing.lg },
  waterHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.sm },
  waterGlasses: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginBottom: spacing.sm },
  waterGlass: { width: 28, height: 28, borderRadius: 6 },
  waterActions: { flexDirection: 'row', gap: spacing.sm },
  waterBtn: { flex: 1, paddingVertical: 10, borderRadius: 12, alignItems: 'center' },
  achieveCard: { marginTop: spacing.md },
  achieveGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  achieveItem: { alignItems: 'center', width: 70 },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'center', alignItems: 'center', padding: spacing.xl },
  modalContent: { width: '100%', maxWidth: 400, borderRadius: 20, padding: spacing.xl },
  modalTitle: { marginBottom: spacing.xs },
  modalActions: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.md },
  modalBtn: { flex: 1 },
});

export default FastingDashboardScreen;
