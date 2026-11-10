import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { ThemeProvider } from './src/contexts/ThemeContext';
import { AuthProvider } from './src/contexts/AuthContext';
import { ToastProvider } from './src/contexts/ToastContext';
import { ToastContainer } from './src/components/organisms/ToastContainer';
import AppNavigator from './src/navigation/AppNavigator';
import ErrorBoundary from './src/components/organisms/ErrorBoundary';

export default function App() {
  return (
    <ThemeProvider>
      {/* Ultima red: cada pantalla ya tiene su propio limite (ver AppNavigator);
          este atrapa lo que falle fuera de ellas, para que nunca quede en blanco. */}
      <ErrorBoundary nombre="App">
        <AuthProvider>
          <ToastProvider>
            <StatusBar style="light" />
            <AppNavigator />
            <ToastContainer />
          </ToastProvider>
        </AuthProvider>
      </ErrorBoundary>
    </ThemeProvider>
  );
}
