import React from 'react';
import { render, fireEvent, screen, act } from '@testing-library/react-native';
import { Text, TouchableOpacity } from 'react-native';
import { ThemeProvider, useTheme } from '../ThemeContext';
import { darkColors, lightColors } from '../../theme/colors';

const ThemeConsumer = () => {
  const { colors, isDark, toggleTheme } = useTheme();
  return (
    <>
      <Text testID="isDark">{String(isDark)}</Text>
      <Text testID="bgColor">{colors.background}</Text>
      <TouchableOpacity testID="toggleBtn" onPress={toggleTheme}>
        <Text>Toggle</Text>
      </TouchableOpacity>
    </>
  );
};

describe('ThemeContext', () => {
  it('provides dark theme by default', () => {
    render(
      <ThemeProvider>
        <ThemeConsumer />
      </ThemeProvider>,
    );
    expect(screen.getByTestId('isDark').props.children).toBe('true');
    expect(screen.getByTestId('bgColor').props.children).toBe(darkColors.background);
  });

  it('toggleTheme switches to light colors', async () => {
    render(
      <ThemeProvider>
        <ThemeConsumer />
      </ThemeProvider>,
    );

    await act(async () => {
      fireEvent.press(screen.getByTestId('toggleBtn'));
    });

    expect(screen.getByTestId('isDark').props.children).toBe('false');
    expect(screen.getByTestId('bgColor').props.children).toBe(lightColors.background);
  });

  it('toggleTheme switches back to dark colors on second toggle', async () => {
    render(
      <ThemeProvider>
        <ThemeConsumer />
      </ThemeProvider>,
    );

    await act(async () => {
      fireEvent.press(screen.getByTestId('toggleBtn'));
    });
    await act(async () => {
      fireEvent.press(screen.getByTestId('toggleBtn'));
    });

    expect(screen.getByTestId('isDark').props.children).toBe('true');
    expect(screen.getByTestId('bgColor').props.children).toBe(darkColors.background);
  });
});
