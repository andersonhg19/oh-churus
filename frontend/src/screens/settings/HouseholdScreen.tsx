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
import EstadoError from '../../components/molecules/EstadoError';
import Spinner from '../../components/atoms/Spinner';
import { spacing } from '../../theme';
import { householdService, HouseholdInfo } from '../../services/householdService';
import { authService } from '../../services/authService';
import { confirmarAccion } from '../../utils/confirmar';
import { useCarga, exigir } from '../../hooks/useCarga';
import { useFocusEffect } from '@react-navigation/native';

const HouseholdScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();

  const [households, setHouseholds] = useState<HouseholdInfo[]>([]);
  const [memberNames, setMemberNames] = useState<{ [userId: number]: string }>({});
  const [memberEmails, setMemberEmails] = useState<{ [userId: number]: string }>({});
  const [newName, setNewName] = useState('');
  // Se invita por correo: el id de fila de la base de datos no lo conoce nadie.
  const [newMemberEmails, setNewMemberEmails] = useState<{ [key: number]: string }>({});
  const [creating, setCreating] = useState(false);

  const traerHogares = useCallback(async () => {
    if (!user) return;
    const hList = exigir(await householdService.getByUser(user.userId)) || [];
    setHouseholds(hList);

    const allUserIds = new Set<number>();
    hList.forEach(h => h.members.forEach(m => allUserIds.add(m.userId)));
    const names: { [id: number]: string } = {};
    const emails: { [id: number]: string } = {};
    for (const uid of allUserIds) {
      /* El nombre de un miembro es adorno: si su ficha no llega se pinta
         "Usuario #id" y el nucleo se sigue viendo. Tumbar la pantalla entera
         por un nombre que falta seria peor que mostrarlo sin nombre. */
      try {
        const userRes = await authService.getUser(String(uid));
        if (userRes.correct && userRes.object) {
          names[uid] = userRes.object.name || `Usuario #${uid}`;
          emails[uid] = userRes.object.email || '';
        } else {
          names[uid] = `Usuario #${uid}`;
        }
      } catch {
        names[uid] = `Usuario #${uid}`;
      }
    }
    setMemberNames(names);
    setMemberEmails(emails);
  }, [user]);

  /* Tras crear, invitar o sacar se recarga con refrescar y no con cargar: el
     spinner de pantalla completa taparia el toast que confirma la accion. */
  const { cargando, error, cargar, refrescar } = useCarga(traerHogares);

  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

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
        refrescar();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo crear');
    } finally {
      setCreating(false);
    }
  };

  const handleInvite = async (householdId: number) => {
    const email = (newMemberEmails[householdId] || '').trim();
    if (!email) {
      showToast('warning', 'Correo requerido');
      return;
    }
    try {
      const res = await householdService.inviteByEmail(householdId, email);
      if (res.correct) {
        showToast('success', 'Miembro agregado', email);
        setNewMemberEmails(prev => ({ ...prev, [householdId]: '' }));
        refrescar();
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo agregar');
    }
  };

  /* Expulsar existia en el backend y la pantalla no lo usaba: no habia forma
     de sacar a nadie del nucleo. Solo lo ve el propietario, y pregunta antes:
     al que sale se le dejan de compartir todas las categorias del hogar. */
  const handleRemove = (householdId: number, userId: number) => {
    const quien = memberNames[userId] || `Usuario #${userId}`;
    confirmarAccion({
      titulo: 'Sacar del nucleo',
      mensaje: `Seguro que quieres sacar a ${quien}? Dejara de ver las categorias y el presupuesto compartidos.`,
      textoConfirmar: 'Si, sacar',
      onConfirmar: async () => {
        try {
          const res = await householdService.removeMember(householdId, userId);
          if (res.correct) {
            showToast('success', 'Miembro retirado', quien);
            refrescar();
          } else {
            showToast('error', 'Error', res.message);
          }
        } catch (err: any) {
          showToast('error', 'Error', err.message || 'No se pudo sacar');
        }
      },
    });
  };

  if (cargando) return <Spinner fullScreen />;

  /* "Sin nucleo familiar" y "no pude preguntar si tienes nucleo" pintaban el
     mismo vacio, invitando a crear un hogar duplicado sobre uno que ya existe. */
  if (error) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background }]}>
        <EstadoError mensaje={error} onReintentar={cargar} />
      </View>
    );
  }

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
                  {memberEmails[m.userId] ? (
                    <AppText variant="caption" color={colors.textMuted}>{memberEmails[m.userId]}</AppText>
                  ) : null}
                </View>
                <AppText variant="caption" color={m.role === 'OWNER' ? colors.primary : colors.textSecondary}>
                  {m.role === 'OWNER' ? 'Propietario' : 'Miembro'}
                </AppText>
                {h.role === 'OWNER' && m.role !== 'OWNER' && String(m.userId) !== String(user?.userId) && (
                  <Button
                    title="Sacar"
                    variant="danger"
                    size="small"
                    onPress={() => handleRemove(h.householdId, m.userId)}
                    style={styles.removeBtn}
                  />
                )}
              </View>
            ))}

            {h.role === 'OWNER' && (
              <View style={styles.addMemberRow}>
                <Input
                  label="Invitar por correo"
                  value={newMemberEmails[h.householdId] || ''}
                  onChangeText={(v) => setNewMemberEmails(prev => ({ ...prev, [h.householdId]: v }))}
                  placeholder="Ej: pareja@correo.com"
                  keyboardType="email-address"
                />
                <Button title="Invitar" onPress={() => handleInvite(h.householdId)} size="small" style={styles.addBtn} />
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
  removeBtn: { marginLeft: spacing.sm },
  addMemberRow: { marginTop: spacing.sm },
  addBtn: { marginTop: spacing.xs },
  createBtn: { marginTop: spacing.sm },
});

export default HouseholdScreen;
