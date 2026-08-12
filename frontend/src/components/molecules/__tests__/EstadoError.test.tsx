import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import EstadoError from '../EstadoError';
import { TestWrapper } from '../../../test-utils';

describe('EstadoError', () => {
  it('muestra el mensaje del backend, no un vacio mudo', () => {
    const { getByText } = render(
      <EstadoError mensaje="No tienes permiso sobre este nucleo" onReintentar={jest.fn()} />,
      { wrapper: TestWrapper },
    );
    expect(getByText('No tienes permiso sobre este nucleo')).toBeTruthy();
    expect(getByText('No se pudo cargar')).toBeTruthy();
  });

  it('ofrece reintentar sin salir de la pantalla', () => {
    const onReintentar = jest.fn();
    const { getByText } = render(
      <EstadoError mensaje="Backend caido" onReintentar={onReintentar} />,
      { wrapper: TestWrapper },
    );
    fireEvent.press(getByText('Reintentar'));
    expect(onReintentar).toHaveBeenCalled();
  });
});
