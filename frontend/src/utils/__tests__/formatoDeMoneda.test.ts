import { formatCurrency, configurarFormatoDeMoneda, formatoDeMoneda } from '../format';

/**
 * El formateador de importes despues de la ola 4: la moneda ya no esta escrita
 * en el codigo, pero lo que habia escrito sigue siendo el punto de partida.
 */
describe('formato de moneda', () => {
  beforeEach(() => {
    configurarFormatoDeMoneda({ currency: 'COP', decimalPlaces: 0, locale: 'es-CO' });
  });

  it('sin configurar nada, se ve como se veia antes de la ola 4', () => {
    expect(formatCurrency(1234567)).toBe('$1.234.567');
  });

  it('conserva el signo del negativo', () => {
    /* Un deficit de -500.000 se mostraba como "$500.000" antes de la ola 2. */
    expect(formatCurrency(-500000)).toBe('-$500.000');
  });

  it('cambia de moneda y de decimales cuando el servidor lo dice', () => {
    configurarFormatoDeMoneda({ currency: 'EUR', decimalPlaces: 2 });
    expect(formatCurrency(1234.5)).toBe('€1.234,50');
  });

  it('una moneda que no esta en el mapa se escribe con su codigo, nunca ambigua', () => {
    configurarFormatoDeMoneda({ currency: 'JPY' });
    expect(formatCurrency(1000)).toBe('JPY 1.000');
  });

  it('UN NUMERO DE DECIMALES IMPOSIBLE SE IGNORA', () => {
    /* toLocaleString lanza con cualquier cosa fuera de 0..20, y eso dejaria la
       pantalla en blanco sin explicacion. */
    configurarFormatoDeMoneda({ decimalPlaces: 99 });
    expect(formatoDeMoneda().decimalPlaces).toBe(0);
    expect(formatCurrency(1000)).toBe('$1.000');
  });

  it('y un decimal negativo tampoco entra', () => {
    configurarFormatoDeMoneda({ decimalPlaces: -1 });
    expect(formatoDeMoneda().decimalPlaces).toBe(0);
  });

  it('los campos vacios no borran lo que ya habia', () => {
    configurarFormatoDeMoneda({ currency: 'EUR', decimalPlaces: 2 });
    configurarFormatoDeMoneda({ currency: '   ' });
    expect(formatoDeMoneda().currency).toBe('EUR');
    expect(formatoDeMoneda().decimalPlaces).toBe(2);
  });

  it('un importe que no es un numero se escribe como cero, no como NaN', () => {
    expect(formatCurrency(Number.NaN)).toBe('$0');
    expect(formatCurrency(Infinity)).toBe('$0');
  });

  it('un locale que este movil no conozca no deja a nadie sin cifra', () => {
    configurarFormatoDeMoneda({ locale: 'xx-XX-inventado', decimalPlaces: 2 });
    expect(formatCurrency(1000)).toContain('1000.00');
  });
});
