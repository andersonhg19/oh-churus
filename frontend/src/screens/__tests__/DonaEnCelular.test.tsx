import React from 'react';
import { StyleSheet } from 'react-native';
import { render, fireEvent } from '@testing-library/react-native';
import { ThemeProvider } from '../../contexts/ThemeContext';
import DonaDeCategorias, { CategoriaEnDona } from '../../components/molecules/DonaDeCategorias';

/* Un celular de 390px: ahi las etiquetas no tienen sitio para el nombre. */
jest.mock('react-native/Libraries/Utilities/useWindowDimensions', () => ({
  __esModule: true,
  default: () => ({ width: 390, height: 844, scale: 2, fontScale: 1 }),
}));

const cats: CategoriaEnDona[] = [
  { id: 1, nombre: 'Casa', icono: 'home', color: '#0984E3', valor: 600000, presupuesto: 700000 },
  { id: 2, nombre: 'Mercado', icono: 'cart', color: '#00CEC9', valor: 300000 },
  { id: 3, nombre: 'Regalos', icono: 'gift', color: '#E84393', valor: 100000 },
];

const pintar = (over: object = {}) => render(
  <ThemeProvider>
    <DonaDeCategorias categorias={cats} libre={-2000000} gastado={1000000} presupuestado={4960000}
      onAnotar={jest.fn()} onVerMovimientos={jest.fn()} onVerCategoria={jest.fn()} {...over} />
  </ThemeProvider>,
);

describe('La dona en el celular', () => {
  it('sin sitio para los nombres, salen en la leyenda; tocar uno lo elige y el centro dice su nombre', () => {
    const { getByTestId, getByLabelText, getAllByText } = pintar();
    expect(getByTestId('leyenda-1', { includeHiddenElements: true })).toBeTruthy();
    fireEvent.press(getByTestId('leyenda-2', { includeHiddenElements: true }));
    expect(getByLabelText('Anotar un gasto en Mercado')).toBeTruthy();
    /* El centro dice el nombre de la elegida (la leyenda va oculta al lector). */
    expect(getAllByText('Mercado')).toHaveLength(1);
  });

  it('cada etiqueta es una zona táctil de 44px de alto', () => {
    const { getByLabelText } = pintar();
    const casa = getByLabelText(/^Casa: /);
    expect(StyleSheet.flatten(casa.props.style).height).toBeGreaterThanOrEqual(44);
  });

  it('la cifra del centro sale completa, sin "…"', () => {
    const { getByText } = pintar();
    const cifra = getByText('-$2.000.000');
    expect(StyleSheet.flatten(cifra.props.style).fontSize).toBeGreaterThanOrEqual(16);
    expect(getByText('Gastado $1 M')).toBeTruthy();
    expect(getByText('de $4,96 M')).toBeTruthy();
  });
});
