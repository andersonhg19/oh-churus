import React from 'react';
import { View, StyleSheet } from 'react-native';
import AppText from '../atoms/Text';
import Button from '../atoms/Button';
import { useTheme } from '../../contexts/ThemeContext';
import { spacing } from '../../theme';

interface EstadoErrorProps {
  mensaje: string;
  onReintentar: () => void;
  titulo?: string;
}

/*
 * El gemelo malvado de EmptyState. Existe para que un fallo NO se vea igual que
 * "aun no tienes datos": aqui siempre hay motivo (el message del backend) y
 * siempre hay salida (Reintentar), sin obligar a salir y volver a entrar.
 */
const EstadoError: React.FC<EstadoErrorProps> = ({
  mensaje,
  onReintentar,
  titulo = 'No se pudo cargar',
}) => {
  const { colors } = useTheme();

  return (
    <View style={styles.container} testID="estado-error">
      <AppText variant="title" style={styles.icon}>⚠️</AppText>
      <AppText variant="subtitle" align="center" style={styles.titulo}>
        {titulo}
      </AppText>
      <AppText variant="body" color={colors.danger} align="center" style={styles.mensaje}>
        {mensaje}
      </AppText>
      <Button title="Reintentar" onPress={onReintentar} variant="outline" />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
  },
  icon: { fontSize: 56, marginBottom: spacing.md },
  titulo: { marginBottom: spacing.sm },
  mensaje: { marginBottom: spacing.lg },
});

export default EstadoError;
