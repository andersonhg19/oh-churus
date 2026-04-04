import React, { useState } from 'react';
import { View, StyleSheet, ScrollView, Platform } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Button from '../../components/atoms/Button';
import PeriodNavigator from '../../components/molecules/PeriodNavigator';
import { spacing } from '../../theme';
import { getStartOfPeriod, getEndOfPeriod, navigatePeriod } from '../../utils/periodUtils';
import api from '../../services/api';

const ExportImportScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();
  const budgetDay = user?.budgetStartDay || 1;

  const [exportPeriodStart, setExportPeriodStart] = useState(() => getStartOfPeriod(budgetDay, new Date()));
  const exportPeriodEnd = getEndOfPeriod(budgetDay, exportPeriodStart);
  const currentPeriodStart = getStartOfPeriod(budgetDay, new Date());
  const canGoNext = exportPeriodStart < currentPeriodStart;
  const [exporting, setExporting] = useState(false);

  const handleExportExcel = async () => {
    setExporting(true);
    try {
      const response = await api.post(
        '/BUDGET-SERVICE/oh-churus/v1/export/excel',
        { userId: user?.userId, budgetStartDay: budgetDay, referenceDate: exportPeriodStart },
        { responseType: 'blob' },
      );
      if (Platform.OS === 'web') {
        const blob = new Blob([response.data], {
          type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `Presupuesto_${exportPeriodStart}_${exportPeriodEnd}.xlsx`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
        showToast('success', 'Exportado', 'Excel descargado');
      } else {
        showToast('info', 'Info', 'Descarga disponible solo en version web');
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo exportar');
    } finally {
      setExporting(false);
    }
  };

  return (
    <ScrollView style={[styles.container, { backgroundColor: colors.background }]} contentContainerStyle={styles.content}>
      <Card style={styles.section}>
        <AppText variant="label" style={styles.sectionTitle}>Exportar Reporte Excel</AppText>
        <AppText variant="caption" color={colors.textSecondary} style={styles.hint}>
          Selecciona el periodo y descarga tu presupuesto en Excel con formulas y estilos
        </AppText>
        <PeriodNavigator
          periodStart={exportPeriodStart}
          periodEnd={exportPeriodEnd}
          onPrevious={() => setExportPeriodStart(navigatePeriod(exportPeriodStart, budgetDay, 'prev').start)}
          onNext={() => setExportPeriodStart(navigatePeriod(exportPeriodStart, budgetDay, 'next').start)}
          canGoNext={canGoNext}
        />
        <Button title="Descargar Excel" onPress={handleExportExcel} loading={exporting} variant="secondary" style={styles.actionBtn} />
      </Card>

      <Card style={styles.section}>
        <AppText variant="label" style={styles.sectionTitle}>Importar desde Excel</AppText>
        <AppText variant="caption" color={colors.textSecondary} style={styles.hint}>
          Proximamente: carga un archivo Excel con el mismo formato para importar movimientos
        </AppText>
        <Button title="Importar Excel" onPress={() => showToast('info', 'Proximamente', 'Esta funcionalidad estara disponible pronto')} variant="outline" style={styles.actionBtn} />
      </Card>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.xl },
  section: { marginBottom: spacing.md },
  sectionTitle: { marginBottom: spacing.xs },
  hint: { marginBottom: spacing.md },
  actionBtn: { marginTop: spacing.sm },
});

export default ExportImportScreen;
