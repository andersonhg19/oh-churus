import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Button from '../../components/atoms/Button';
import Input from '../../components/atoms/Input';
import EmptyState from '../../components/molecules/EmptyState';
import { spacing } from '../../theme';
import { householdService, HouseholdInfo } from '../../services/householdService';
import { authService } from '../../services/authService';
import { useFocusEffect } from '@react-navigation/native';

const HouseholdScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();

  const [households, setHouseholds] = useState<HouseholdInfo[]>([]);
  const [memberNames, setMemberNames] = useState<{ [userId: number]: string }>({});
  const [loading, setLoading] = useState(true);
  const [newName, setNewName] = useState('');
  const [newMemberIds, setNewMemberIds] = useState<{ [key: number]: string }>({});
  const [creating, setCreating] = useState(false);

  const fetchData = useCallback(async () => {
    if (!user) return;
    try {
      const res = await householdService.getByUser(user.userId);
      if (res.correct && res.object) {
        const hList = res.object || [];
        setHouseholds(hList);

        // Fetch names for all member userIds
        const allUserIds = new Set<number>();
        hList.forEach(h => h.members.forEach(m => allUserIds.add(m.userId)));
        const names: { [id: number]: string } = {};
        for (const uid of allUserIds) {
          try {
            const userRes = await authService.getUser(String(uid));
            if (userRes.correct && userRes.object) {
              names[uid] = userRes.object.name || `Usuario #${uid}`;
            } else {
              names[uid] = `Usuario #${uid}`;
            }
          } catch {
            names[uid] = `Usuario #${uid}`;
          }
        }
        setMemberNames(names);
      }
    } catch {
      // silent
    } finally {
      setLoading(false);
    }
  }, [user]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      fetchData();
    }, [fetchData]),
  );

  const handleCreate = async () => {
    if (!newName.trim()) {
      showToast('warning', 'Nombre requerido');
      return;
    }
    setCreating(true);
    try {
      const res = await householdService.create(newName.trim(), user!.userId);
      if (res.correct) {
        showToast('success', 'Nucleo creado', newName);
        setNewName('');
        fetchData();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo crear');
    } finally {
      setCreating(false);
    }
  };

  const handleAddMember = async (householdId: number) => {
    const memberId = parseInt(newMemberIds[householdId] || '');
    if (!memberId) {
      showToast('warning', 'ID de usuario requerido');
      return;
    }
    try {
      const res = await householdService.addMember(householdId, memberId);
      if (res.correct) {
        showToast('success', 'Miembro agregado');
        setNewMemberIds(prev => ({ ...prev, [householdId]: '' }));
        fetchData();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo agregar');
    }
  };

  return (
    <ScrollView style={[styles.container, { backgroundColor: colors.background }]} contentContainerStyle={styles.content}>
      {households.length > 0 ? (
        households.map(h => (
          <Card key={h.householdId} style={styles.section}>
            <View style={styles.headerRow}>
              <AppText variant="subtitle">🏠 {h.name}</AppText>
              <AppText variant="caption" color={colors.textSecondary}>
                {h.role === 'OWNER' ? 'Propietario' : 'Miembro'}
              </AppText>
            </View>
            <AppText variant="caption" color={colors.textMuted} style={styles.memberCount}>
              {h.memberCount} miembro{h.memberCount !== 1 ? 's' : ''}
            </AppText>

            {h.members.map((m, i) => (
              <View key={i} style={[styles.memberRow, { borderColor: colors.border }]}>
                <View style={styles.memberInfo}>
                  <AppText variant="body">👤 {memberNames[m.userId] || `Usuario #${m.userId}`}</AppText>
                </View>
                <AppText variant="caption" color={m.role === 'OWNER' ? colors.primary : colors.textSecondary}>
                  {m.role === 'OWNER' ? 'Propietario' : 'Miembro'}
                </AppText>
              </View>
            ))}

            {h.role === 'OWNER' && (
              <View style={styles.addMemberRow}>
                <Input
                  label="Agregar miembro (ID usuario)"
                  value={newMemberIds[h.householdId] || ''}
                  onChangeText={(v) => setNewMemberIds(prev => ({ ...prev, [h.householdId]: v }))}
                  placeholder="Ej: 4"
                  keyboardType="numeric"
                />
                <Button title="Agregar" onPress={() => handleAddMember(h.householdId)} size="small" style={styles.addBtn} />
              </View>
            )}
          </Card>
        ))
      ) : (
        <EmptyState title="Sin nucleo familiar" message="Crea uno para compartir categorias y presupuesto" icon="🏠" />
      )}

      <Card style={styles.section}>
        <AppText variant="label" style={styles.sectionTitle}>Crear nucleo familiar</AppText>
        <Input
          label="Nombre del nucleo"
          value={newName}
          onChangeText={setNewName}
          placeholder="Ej: Familia"
        />
        <Button title="Crear" onPress={handleCreate} loading={creating} style={styles.createBtn} />
      </Card>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.xl },
  section: { marginBottom: spacing.md },
  sectionTitle: { marginBottom: spacing.md },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.xs },
  memberCount: { marginBottom: spacing.sm },
  memberRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.sm,
    borderBottomWidth: 1,
  },
  memberInfo: { flex: 1 },
  addMemberRow: { marginTop: spacing.sm },
  addBtn: { marginTop: spacing.xs },
  createBtn: { marginTop: spacing.sm },
});

export default HouseholdScreen;
