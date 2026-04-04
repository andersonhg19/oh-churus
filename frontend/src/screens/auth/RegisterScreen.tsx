import React, { useState } from 'react';
import { View, StyleSheet, KeyboardAvoidingView, Platform, ScrollView, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import { spacing } from '../../theme';
import { validateRequired, validateEmail, validatePassword } from '../../utils/validators';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type AuthStackParamList = {
  Login: undefined;
  Register: undefined;
};

type Props = NativeStackScreenProps<AuthStackParamList, 'Register'>;

const RegisterScreen: React.FC<Props> = ({ navigation }) => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { register } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<{ name?: string; email?: string; password?: string }>({});

  const validate = (): boolean => {
    const newErrors: { name?: string; email?: string; password?: string } = {};
    const nameErr = validateRequired(name, 'Nombre');
    const emailErr = validateEmail(email);
    const passErr = validatePassword(password, 6);
    if (nameErr) newErrors.name = nameErr;
    if (emailErr) newErrors.email = emailErr;
    if (passErr) newErrors.password = passErr;
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleRegister = async () => {
    if (!validate()) return;
    setLoading(true);
    try {
      await register(name.trim(), email.trim(), password);
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo crear la cuenta');
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
        <View style={styles.logoContainer}>
          <AppText variant="title" style={styles.mascot}>🐿️</AppText>
          <AppText variant="title" color={colors.primary}>Oh Churus!</AppText>
          <AppText variant="caption" color={colors.textSecondary} style={styles.tagline}>
            Crea tu cuenta y empieza a organizar tu vida
          </AppText>
        </View>

        <View style={styles.form}>
          <Input
            label="Nombre"
            value={name}
            onChangeText={setName}
            placeholder="Tu nombre"
            error={errors.name}
          />
          <Input
            label="Correo electronico"
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
            placeholder="Minimo 6 caracteres"
            secureTextEntry
            error={errors.password}
          />
          <Button
            title="Crear Cuenta"
            onPress={handleRegister}
            loading={loading}
            size="large"
            style={styles.registerBtn}
          />
        </View>

        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.linkContainer}>
          <AppText variant="body" color={colors.textSecondary}>
            Ya tienes cuenta?{' '}
          </AppText>
          <AppText variant="body" color={colors.primary}>
            Inicia sesion
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
  registerBtn: {
    marginTop: spacing.sm,
  },
  linkContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
  },
});

export default RegisterScreen;
