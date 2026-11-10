import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView, Modal } from 'react-native';
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
import { useAlcanceOpcional } from '../../contexts/AlcanceContext';
import { mensajeDelServidor } from '../../utils/mensajes';

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
  /* Salir del hogar: se confirma dentro de la app (no con el dialogo del
     navegador), porque al salir se deja de ver todo lo compartido. */
  const [saliendoDe, setSaliendoDe] = useState<HouseholdInfo | null>(null);
  const [saliendo, setSaliendo] = useState(false);
  const alcance = useAlcanceOpcional();

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
        showToast('success', 'Hogar creado', newName);
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
        showToast('error', 'No se pudo invitar', res.message);
      }
    } catch (err: any) {
      /* El servidor responde un 400 con un mensaje que ya explica que hacer
         ("Revisa el correo; si ya esta en un nucleo familiar..."): se muestra
         tal cual en vez del generico de axios. */
      showToast('error', 'No se pudo invitar', mensajeDelServidor(err, 'No se pudo agregar a esa persona'));
    }
  };

  const salirDelHogar = async () => {
    if (!saliendoDe || !user || saliendo) return;
    setSaliendo(true);
    try {
      const res = await householdService.removeMember(saliendoDe.householdId, Number(user.userId));
      if (res.correct) {
        showToast('success', 'Saliste del hogar', saliendoDe.name);
        setSaliendoDe(null);
        alcance?.setAlcance({ tipo: 'PERSONA', userId: Number(user.userId) });
        alcance?.recargarMiembros();
        refrescar();
      } else {
        showToast('error', 'No se pudo salir', res.message);
      }
    } catch (err: any) {
      showToast('error', 'No se pudo salir', mensajeDelServidor(err, 'No se pudo salir del hogar'));
    } finally {
      setSaliendo(false);
    }
  };

  /* Expulsar existia en el backend y la pantalla no lo usaba: no habia forma
     de sacar a nadie del nucleo. Solo lo ve el propietario, y pregunta antes:
     al que sale se le dejan de compartir todas las categorias del hogar. */
  const handleRemove = (householdId: number, userId: number) => {
    const quien = memberNames[userId] || `Usuario #${userId}`;
    confirmarAccion({
      titulo: 'Sacar del hogar',
      mensaje: `¿Seguro que quieres sacar a ${quien}? Dejará de ver las categorías y el presupuesto compartidos.`,
      textoConfirmar: 'Sí, sacar',
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
            <View
              style={styles.headerRow}
              accessible
              accessibilityLabel={`Núcleo familiar ${h.name}. Tú eres ${h.role === 'OWNER' ? 'el propietario' : 'miembro'}`}
            >
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
                <View
                  style={styles.memberInfo}
                  accessible
                  accessibilityLabel={
                    `${memberNames[m.userId] || `Usuario número ${m.userId}`}` +
                    `${memberEmails[m.userId] ? `, ${memberEmails[m.userId]}` : ''}` +
                    `. ${m.role === 'OWNER' ? 'Propietario' : 'Miembro'}`
                  }
                >
                  <AppText variant="body">👤 {memberNames[m.userId] || `Usuario #${m.userId}`}</AppText>
                  {memberEmails[m.userId] ? (
                    <AppText variant="caption" color={colors.textMuted}>{memberEmails[m.userId]}</AppText>
                  ) : null}
                </View>
                <AppText variant="caption" color={m.role === 'OWNER' ? colors.primary : colors.textSecondary}>
                  {m.role === 'OWNER' ? 'Propietario' : 'Miembro'}
                </AppText>
                {h.role === 'OWNER' && m.role !== 'OWNER' && String(m.userId) !== String(user?.userId) && (
                  /* Hay un "Sacar" por miembro y todos dicen lo mismo. */
                  <Button
                    title="Sacar"
                    accessibilityLabel={`Sacar a ${memberNames[m.userId] || `Usuario número ${m.userId}`} del núcleo familiar`}
                    variant="danger"
                    size="small"
                    onPress={() => handleRemove(h.householdId, m.userId)}
                    style={styles.removeBtn}
                  />
                )}
              </View>
            ))}

            {h.role !== 'OWNER' && (
              <Button
                title="Salir del hogar"
                accessibilityLabel={`Salir del hogar ${h.name}`}
                variant="outline"
                size="small"
                onPress={() => setSaliendoDe(h)}
                style={styles.addBtn}
              />
            )}

            {h.role === 'OWNER' && (
              <View style={styles.addMemberRow}>
                <Input
                  label="Invitar por correo"
                  value={newMemberEmails[h.householdId] || ''}
                  onChangeText={(v) => setNewMemberEmails(prev => ({ ...prev, [h.householdId]: v }))}
                  placeholder="Ej: pareja@correo.com"
                  keyboardType="email-address"
                />
                <Button
                  title="Invitar"
                  accessibilityLabel={`Invitar a alguien a ${h.name}`}
                  onPress={() => handleInvite(h.householdId)}
                  size="small"
                  style={styles.addBtn}
                />
              </View>
            )}
          </Card>
        ))
      ) : (
        <EmptyState title="Sin hogar" message="Crea uno para compartir categorías y presupuesto" icon="🏠" />
      )}

      {/* Crear otro hogar solo tiene sentido si no se esta en ninguno: antes
          se ofrecia siempre y parecia que el hogar actual no existia. */}
      {households.length === 0 && (
      <Card style={styles.section}>
        <AppText variant="label" style={styles.sectionTitle}>Crear hogar</AppText>
        <Input
          label="Nombre del hogar"
          value={newName}
          onChangeText={setNewName}
          placeholder="Ej: Familia"
        />
        <Button title="Crear" onPress={handleCreate} loading={creating} style={styles.createBtn} />
      </Card>
      )}

      <Modal visible={!!saliendoDe} transparent animationType="fade" onRequestClose={() => setSaliendoDe(null)}>
        <View style={styles.fondo}>
          <View style={[styles.hoja, { backgroundColor: colors.surface }]} accessibilityViewIsModal>
            <AppText variant="subtitle">¿Salir de {saliendoDe?.name}?</AppText>
            <AppText variant="body" color={colors.textSecondary}>
              Dejarás de ver las categorías, el presupuesto y los movimientos compartidos del hogar. Lo tuyo personal se queda contigo. Para volver, alguien del hogar tendrá que invitarte otra vez.
            </AppText>
            <Button title="Sí, salir del hogar" variant="danger" onPress={salirDelHogar} loading={saliendo} />
            <Button title="Cancelar" variant="outline" onPress={() => setSaliendoDe(null)} />
          </View>
        </View>
      </Modal>
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
  fondo: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'center', padding: spacing.lg },
  hoja: { borderRadius: 16, padding: spacing.lg, gap: spacing.sm },
});

export default HouseholdScreen;
