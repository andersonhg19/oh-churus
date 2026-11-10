import React, { useState } from 'react';
import { View, StyleSheet, KeyboardAvoidingView, Platform, ScrollView, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import { spacing } from '../../theme';
import { validateEmail, validatePassword } from '../../utils/validators';
import { mensajeDelServidor } from '../../utils/mensajes';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type AuthStackParamList = {
  Login: undefined;
  Register: undefined;
};

type Props = NativeStackScreenProps<AuthStackParamList, 'Login'>;

const LoginScreen: React.FC<Props> = ({ navigation }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { login, avisoDeSesion, limpiarAvisoDeSesion } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});

  const validate = (): boolean => {
    const newErrors: { email?: string; password?: string } = {};
    const emailErr = validateEmail(email);
    const passErr = validatePassword(password);
    if (emailErr) newErrors.email = emailErr;
    if (passErr) newErrors.password = passErr;
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleLogin = async () => {
    if (!validate()) return;
    setLoading(true);
    try {
      await login(email.trim(), password);
    } catch (err: any) {
      /* Un 429 ("Demasiados intentos...") llega como error HTTP: su mensaje
         viene en el cuerpo y ya esta en espanol. */
      showToast('error', 'No se pudo entrar', mensajeDelServidor(err, 'No se pudo iniciar sesión'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={[styles.flex, { backgroundColor: colors.background }]}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
        <View
          style={styles.logoContainer}
          accessible
          accessibilityLabel="Oh Churus, tu asistente para la vida cotidiana"
        >
          <AppText variant="title" style={styles.mascot}>🐿️</AppText>
          <AppText variant="title" color={colors.primary}>Oh Churus!</AppText>
          <AppText variant="caption" color={colors.textSecondary} style={styles.tagline}>
            Tu asistente para la vida cotidiana
          </AppText>
        </View>

        {/* Si la sesion se cerro sola (token caducado), se dice por que: sin
            esto, aparecer de golpe en la entrada parece un fallo de la app. */}
        {!!avisoDeSesion && (
          <View style={[styles.aviso, { borderColor: colors.warning, backgroundColor: colors.surface }]}
            accessibilityRole="alert" accessibilityLiveRegion="polite">
            <AppText variant="body" style={{ flex: 1 }}>{avisoDeSesion}</AppText>
            {!!limpiarAvisoDeSesion && (
              <TouchableOpacity onPress={limpiarAvisoDeSesion} style={styles.cerrarAviso}
                accessibilityRole="button" accessibilityLabel="Cerrar el aviso">
                <AppText variant="body" color={colors.textSecondary}>✕</AppText>
              </TouchableOpacity>
            )}
          </View>
        )}

        <View style={styles.form}>
          <Input
            label="Correo electrónico"
            value={email}
            onChangeText={setEmail}
            placeholder="tu@correo.com"
            keyboardType="email-address"
            error={errors.email}
          />
          <Input
            label="Contraseña"
            value={password}
            onChangeText={setPassword}
            placeholder="Tu contraseña"
            secureTextEntry
            error={errors.password}
          />
          <Button
            title="Iniciar sesión"
            onPress={handleLogin}
            loading={loading}
            size="large"
            style={styles.loginBtn}
          />
        </View>

        {/* El enlace son dos textos: "No tienes cuenta?" y "Registrate".
            Sueltos se oyen como una pregunta sin respuesta. */}
        <TouchableOpacity
          onPress={() => navigation.navigate('Register')}
          style={styles.linkContainer}
          accessibilityRole="link"
          accessibilityLabel="¿No tienes cuenta? Regístrate"
        >
          <AppText variant="body" color={colors.textSecondary}>
            ¿No tienes cuenta?{' '}
          </AppText>
          <AppText variant="body" color={colors.primary}>
            Regístrate
          </AppText>
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  flex: { flex: 1 },
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: spacing.xl,
  },
  logoContainer: {
    alignItems: 'center',
    marginBottom: spacing.xxl,
  },
  mascot: {
    fontSize: 72,
    marginBottom: spacing.sm,
  },
  tagline: {
    marginTop: spacing.sm,
  },
  form: {
    marginBottom: spacing.lg,
  },
  aviso: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 12,
    paddingLeft: spacing.md,
    marginBottom: spacing.lg,
    minHeight: 48,
  },
  cerrarAviso: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  loginBtn: {
    marginTop: spacing.sm,
  },
  linkContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
  },
});

export default LoginScreen;
