import { pulsar, evaluar, mostrar, tieneOperacion, Tecla } from '../calculadora';

const teclear = (teclas: Tecla[], desde = '') => teclas.reduce((e, t) => pulsar(e, t), desde);

describe('la calculadora del teclado', () => {
  it('suma lo que dice la maqueta: 50.000 + 1.500 = 51.500', () => {
    const e = teclear(['5', '0', '000', '+', '1', '5', '0', '0']);
    expect(mostrar(e)).toBe('50.000 + 1.500');
    expect(evaluar(e)).toBe(51500);
    expect(tieneOperacion(e)).toBe(true);
  });

  it('resta, multiplica y divide con la precedencia de siempre', () => {
    expect(evaluar('100000-25000')).toBe(75000);
    expect(evaluar('2×150000+10000')).toBe(310000);
    expect(evaluar('10000+2×150000')).toBe(310000);
    expect(evaluar('900000÷3')).toBe(300000);
    expect(evaluar('100000÷3')).toBe(33333);
  });

  it('dividir por cero no rompe nada', () => {
    expect(evaluar('5000÷0')).toBe(0);
  });

  it('un resultado negativo no es un monto', () => {
    expect(evaluar('1000-5000')).toBe(0);
  });

  it('dos operadores seguidos se reemplazan, no se apilan', () => {
    expect(teclear(['5', '+', '×', '2'])).toBe('5×2');
  });

  it('no empieza por operador ni por ceros', () => {
    expect(teclear(['+', '0', '000', '7'])).toBe('7');
  });

  it('el operador suelto al final no cuenta', () => {
    expect(evaluar('5000+')).toBe(5000);
  });

  it('= deja el resultado para seguir operando; C borra; ⌫ quita la ultima tecla', () => {
    expect(teclear(['5', '+', '5', '='])).toBe('10');
    expect(teclear(['5', '5', 'C'])).toBe('');
    expect(teclear(['5', '5', '⌫'])).toBe('5');
  });

  it('no deja escribir montos absurdos', () => {
    const e = teclear(Array(20).fill('9') as Tecla[]);
    expect(e.length).toBe(12);
  });
});
