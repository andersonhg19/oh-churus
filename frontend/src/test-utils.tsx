import React from 'react';
import { Alert } from 'react-native';
import { ThemeProvider } from './contexts/ThemeContext';

export const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

/**
 * Pulsa el boton destructivo del ultimo dialogo de confirmacion.
 * La suite debe haber hecho jest.spyOn(Alert, 'alert') antes.
 */
export const confirmarUltimaAlerta = (): void => {
  const alerta = Alert.alert as unknown as jest.Mock;
  const llamada = alerta.mock.calls[alerta.mock.calls.length - 1];
  const botones = (llamada?.[2] || []) as Array<{ text: string; style?: string; onPress?: () => void }>;
  const destructivo = botones.find((b) => b.style === 'destructive') || botones[botones.length - 1];
  destructivo?.onPress?.();
};
