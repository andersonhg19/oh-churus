/**
 * La calculadora del teclado de la app.
 *
 * El teclado es propio para que el del celular no aparezca, y por eso las
 * operaciones tambien: 50.000 + 1.500 da 51.500 sin abrir otra app. Sin
 * eval(): la expresion se guarda como texto de digitos y operadores y se
 * resuelve con la precedencia de siempre (multiplicar y dividir antes de sumar
 * y restar). Los montos son pesos enteros, asi que el resultado se redondea.
 */

export type Tecla =
  | '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' | '000'
  | '+' | '-' | '×' | '÷' | '⌫' | 'C' | '=';

const OPERADORES = ['+', '-', '×', '÷'];
const MAXIMO_DIGITOS = 12;

const esOperador = (c: string) => OPERADORES.includes(c);

/** Aplica una tecla a la expresion y devuelve la nueva expresion. */
export function pulsar(expresion: string, tecla: Tecla): string {
  if (tecla === 'C') return '';
  if (tecla === '⌫') return expresion.slice(0, -1);
  if (tecla === '=') {
    const r = evaluar(expresion);
    return r > 0 ? String(r) : '';
  }
  const ultimo = expresion.slice(-1);
  if (esOperador(tecla)) {
    if (expresion === '') return '';
    // Cambiar de operador en vez de apilar dos seguidos.
    return esOperador(ultimo) ? expresion.slice(0, -1) + tecla : expresion + tecla;
  }
  // Digitos: el numero en curso no pasa del maximo ni empieza en ceros.
  const enCurso = numeroEnCurso(expresion);
  if (enCurso === '' && (tecla === '0' || tecla === '000')) return expresion;
  if ((enCurso + tecla).length > MAXIMO_DIGITOS) return expresion;
  return expresion + tecla;
}

function numeroEnCurso(expresion: string): string {
  const m = /(\d*)$/.exec(expresion);
  return m ? m[1] : '';
}

/** Resuelve la expresion; lo incompleto al final (un operador suelto) se ignora. */
export function evaluar(expresion: string): number {
  let e = expresion;
  while (e && esOperador(e.slice(-1))) e = e.slice(0, -1);
  if (!e) return 0;
  const partes = e.split(/([+\-×÷])/).filter((p) => p !== '');
  // Primero × y ÷.
  const pila: (number | string)[] = [];
  for (let i = 0; i < partes.length; i++) {
    const p = partes[i];
    if (p === '×' || p === '÷') {
      const izq = Number(pila.pop());
      const der = Number(partes[++i]);
      pila.push(p === '×' ? izq * der : der === 0 ? 0 : izq / der);
    } else if (esOperador(p)) {
      pila.push(p);
    } else {
      pila.push(Number(p));
    }
  }
  let total = Number(pila[0]);
  for (let i = 1; i < pila.length; i += 2) {
    const v = Number(pila[i + 1]);
    total = pila[i] === '+' ? total + v : total - v;
  }
  return Math.max(0, Math.round(total));
}

/** Hay una operacion por resolver (para ensenar "= 51.500" debajo). */
export const tieneOperacion = (expresion: string) => /\d[+\-×÷]\d/.test(expresion);

/** La expresion con separador de miles en cada numero: "50.000 + 1.500". */
export function mostrar(expresion: string, separador = '.'): string {
  if (!expresion) return '0';
  return expresion
    .split(/([+\-×÷])/)
    .filter((p) => p !== '')
    .map((p) => (esOperador(p) ? ` ${p === '-' ? '−' : p} ` : p.replace(/\B(?=(\d{3})+(?!\d))/g, separador)))
    .join('');
}

/** El monto inicial de un valor ya existente. */
export const desdeMonto = (monto?: number | null) => (monto && monto > 0 ? String(Math.round(monto)) : '');
