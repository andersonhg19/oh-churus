import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import IconPicker from '../IconPicker';
import { TestWrapper } from '../../../test-utils';

describe('IconPicker', () => {
  it('renders the label and placeholder when no value', () => {
    const { getByText } = render(
      <TestWrapper><IconPicker value="" onChange={jest.fn()} /></TestWrapper>
    );
    expect(getByText('Icono')).toBeTruthy();
    expect(getByText('Seleccionar icono (opcional)')).toBeTruthy();
  });

  it('expands, shows categories and selects an icon', () => {
    const onChange = jest.fn();
    const { getByText, getByPlaceholderText } = render(
      <TestWrapper><IconPicker value="" onChange={onChange} /></TestWrapper>
    );
    fireEvent.press(getByText('Seleccionar icono (opcional)'));
    expect(getByPlaceholderText('Buscar icono...')).toBeTruthy();
    expect(getByText('Hogar')).toBeTruthy(); // categoria (unica)
    fireEvent.press(getByText('Casa'));
    expect(onChange).toHaveBeenCalledWith('home', '🏠');
  });

  it('filters icons via search', () => {
    const { getByText, getByPlaceholderText, queryByText } = render(
      <TestWrapper><IconPicker value="" onChange={jest.fn()} /></TestWrapper>
    );
    fireEvent.press(getByText('Seleccionar icono (opcional)'));
    fireEvent.changeText(getByPlaceholderText('Buscar icono...'), 'pizza');
    expect(getByText('Resultados')).toBeTruthy();
    expect(getByText('Pizza')).toBeTruthy();
    expect(queryByText('Dinero')).toBeNull();
  });

  it('shows the selected icon and clears it', () => {
    const onChange = jest.fn();
    const { getByText } = render(
      <TestWrapper><IconPicker value="home" onChange={onChange} /></TestWrapper>
    );
    // selected label visible in selector
    expect(getByText('Casa')).toBeTruthy();
    fireEvent.press(getByText('Casa'));
    fireEvent.press(getByText('Quitar icono'));
    expect(onChange).toHaveBeenCalledWith('', '');
  });
});
