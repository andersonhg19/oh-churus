import { parseMonto } from '../format';

/**
 * Lo que una persona escribe en Colombia. El caso que motivo esto:
 * parseFloat("1.500.000") es 1.5, y el arriendo se guardaba de peso y medio.
 */
describe('parseMonto', () => {
  it.each([
    ['1.500.000', 1500000],
    ['$ 2.300.000', 2300000],
    ['1500000', 1500000],
    ['150.000', 150000],
    ['1.500', 1500],
    ['50.000,50', 50000.5],
    ['1,500,000.25', 1500000.25],
    ['1.5', 1.5],
    ['0,5', 0.5],
    ['-400.000', -400000],
  ])('"%s" es %d', (texto, esperado) => {
    expect(parseMonto(texto)).toBeCloseTo(esperado as number, 5);
  });

  it('sin numeros es NaN, para que el formulario avise', () => {
    expect(parseMonto('')).toBeNaN();
    expect(parseMonto('abc')).toBeNaN();
  });

  it('un numero se devuelve tal cual', () => {
    expect(parseMonto(210000)).toBe(210000);
  });
});
