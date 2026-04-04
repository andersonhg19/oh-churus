import React from 'react';
import { ThemeProvider } from './contexts/ThemeContext';

export const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider>{children}</ThemeProvider>
);
