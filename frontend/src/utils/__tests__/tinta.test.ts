import { tintaSobre } from '../tinta';

describe('el icono de una categoria se ve sobre su color', () => {
  it('blanco sobre los colores oscuros', () => {
    ['#E53935', '#1E88E5', '#8E24AA', '#D81B60', '#3949AB', '#00897B'].forEach((c) => expect(tintaSobre(c)).toBe('#FFFFFF'));
  });
  it('oscuro sobre los claros, donde el blanco no se ve', () => {
    ['#FDD835', '#C0CA33', '#FFAB91', '#B0BEC5'].forEach((c) => expect(tintaSobre(c)).toBe('#1A1405'));
  });
  it('sin color valido, blanco', () => {
    expect(tintaSobre(undefined)).toBe('#FFFFFF');
    expect(tintaSobre('rojo')).toBe('#FFFFFF');
  });
});
