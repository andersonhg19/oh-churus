import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Spinner from '../../components/atoms/Spinner';
import EstadoError from '../../components/molecules/EstadoError';
import PeriodNavigator from '../../components/molecules/PeriodNavigator';
import { spacing } from '../../theme';
import { fastingService } from '../../services/fastingService';
import { useCarga, exigir } from '../../hooks/useCarga';
import { getStartOfPeriod, getEndOfPeriod, navigatePeriod } from '../../utils/periodUtils';
import { useFocusEffect } from '@react-navigation/native';

const STATUS_CONFIG: Record<string, { label: string; color: string }> = {
  COMPLETED: { label: 'Cumplido', color: '#00B894' },
  INCOMPLETE: { label: 'Incompleto', color: '#FF6B6B' },
  IN_PROGRESS: { label: 'En progreso', color: '#E8A838' },
  CANCELLED: { label: 'Cancelado', color: '#6C6C80' },
  NOT_REGISTERED: { label: 'No registrado', color: '#6C6C80' },
};

const FastingHistoryScreen: React.FC = () => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const budgetDay = user?.budgetStartDay || 1;

  const [periodStart, setPeriodStart] = useState(() => getStartOfPeriod(budgetDay, new Date()));
  const periodEnd = getEndOfPeriod(budgetDay, periodStart);
  const [history, setHistory] = useState<any>(null);
  const [summary, setSummary] = useState<any>(null);

  const traerHistorial = useCallback(async () => {
    if (!user) return;
    const [hRes, sRes] = await Promise.all([
      fastingService.getHistory(user.userId, budgetDay, periodStart),
      fastingService.getSummary(user.userId, budgetDay, periodStart),
    ]);
    setHistory(exigir(hRes) || null);
    setSummary(exigir(sRes) || null);
  }, [user, budgetDay, periodStart]);

  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traerHistorial);

  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  const currentPeriodStart = getStartOfPeriod(budgetDay, new Date());
  const canGoNext = periodStart < currentPeriodStart;

  if (cargando) return <Spinner fullScreen />;

  if (error) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background }]}>
        <EstadoError mensaje={error} onReintentar={cargar} />
      </View>
    );
  }

  const days = history?.days || [];

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />}
    >
      <AppText variant="subtitle" style={styles.title}>Historial</AppText>

      <PeriodNavigator
        periodStart={periodStart}
        periodEnd={periodEnd}
        onPrevious={() => setPeriodStart(navigatePeriod(periodStart, budgetDay, 'prev').start)}
        onNext={() => setPeriodStart(navigatePeriod(periodStart, budgetDay, 'next').start)}
        canGoNext={canGoNext}
      />

      {/* Summary */}
      {summary && (
        <Card style={styles.summaryCard}>
          <View style={styles.summaryRow}>
            <View style={styles.summaryItem}>
              <AppText variant="subtitle" color={colors.income}>{summary.completed || 0}</AppText>
              <AppText variant="caption">Cumplidos</AppText>
            </View>
            <View style={styles.summaryItem}>
              <AppText variant="subtitle" color={colors.expense}>{summary.incomplete || 0}</AppText>
              <AppText variant="caption">Incompletos</AppText>
            </View>
            <View style={styles.summaryItem}>
              <AppText variant="subtitle" color={colors.primary}>{summary.complianceRate || 0}%</AppText>
              <AppText variant="caption">Cumplimiento</AppText>
            </View>
          </View>
          <View style={styles.summaryRow}>
            <View style={styles.summaryItem}>
              <AppText variant="subtitle" color={colors.primary}>{summary.currentStreak || 0}</AppText>
              <AppText variant="caption">Racha actual</AppText>
            </View>
            <View style={styles.summaryItem}>
              <AppText variant="subtitle" color={colors.warning}>{summary.bestStreak || 0}</AppText>
              <AppText variant="caption">Mejor racha</AppText>
            </View>
          </View>
        </Card>
      )}

      {/* Day by day */}
      {days.map((day: any) => {
        const cfg = STATUS_CONFIG[day.status] || STATUS_CONFIG.NOT_REGISTERED;
        return (
          <View key={day.date} style={[styles.dayRow, { borderColor: colors.border }]}>
            <View style={styles.dayLeft}>
              <AppText variant="body">{day.date}</AppText>
              {day.startTime && (
                <AppText variant="caption" color={colors.textSecondary}>
                  {new Date(day.startTime).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })}
                  {day.durationFormatted ? ` · ${day.durationFormatted}` : ''}
                </AppText>
              )}
            </View>
            <View style={[styles.statusBadge, { backgroundColor: cfg.color + '20' }]}>
              <View style={[styles.statusDot, { backgroundColor: cfg.color }]} />
              <AppText variant="caption" style={{ color: cfg.color, fontWeight: '600' }}>{cfg.label}</AppText>
            </View>
          </View>
        );
      })}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.md, paddingBottom: spacing.xxl },
  title: { marginBottom: spacing.sm },
  summaryCard: { marginVertical: spacing.sm },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-around', marginVertical: spacing.xs },
  summaryItem: { alignItems: 'center' },
  dayRow: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingVertical: spacing.sm, borderBottomWidth: 1,
  },
  dayLeft: { flex: 1 },
  statusBadge: {
    flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10, paddingVertical: 4,
    borderRadius: 12, gap: 6,
  },
  statusDot: { width: 8, height: 8, borderRadius: 4 },
});

export default FastingHistoryScreen;
