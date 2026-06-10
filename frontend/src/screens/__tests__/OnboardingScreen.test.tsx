import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import OnboardingScreen from '../onboarding/OnboardingScreen';
import { ThemeProvider } from '../../contexts/ThemeContext';

const Wrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ThemeProvider>{children}</ThemeProvider>
);

describe('OnboardingScreen', () => {
  it('renders the first step and advances with Siguiente', () => {
    const onComplete = jest.fn();
    const { getByText } = render(<OnboardingScreen onComplete={onComplete} />, { wrapper: Wrapper });
    expect(getByText('Bienvenido a Oh Churus!')).toBeTruthy();
    fireEvent.press(getByText('Siguiente'));
    expect(getByText('Organiza por categorias')).toBeTruthy();
  });

  it('skips and calls onComplete', async () => {
    const onComplete = jest.fn();
    const { getByText } = render(<OnboardingScreen onComplete={onComplete} />, { wrapper: Wrapper });
    fireEvent.press(getByText('Saltar'));
    await waitFor(() => expect(onComplete).toHaveBeenCalled());
  });

  it('completes on the last step', async () => {
    const onComplete = jest.fn();
    const { getByText } = render(<OnboardingScreen onComplete={onComplete} />, { wrapper: Wrapper });
    // advance through all intermediate steps
    fireEvent.press(getByText('Siguiente'));
    fireEvent.press(getByText('Siguiente'));
    fireEvent.press(getByText('Siguiente'));
    fireEvent.press(getByText('Siguiente'));
    fireEvent.press(getByText('Comenzar'));
    await waitFor(() => expect(onComplete).toHaveBeenCalled());
  });
});
