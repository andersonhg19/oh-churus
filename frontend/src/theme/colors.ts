/*
 * LOS COLORES TIENEN QUE PODER LEERSE.
 *
 * El tema claro llevaba SIETE colores por debajo de 3:1 sobre su propio fondo
 * —primary, primaryLight, accent, warning, success, income y pending, mas
 * textMuted—, y con ellos se escriben cifras: los ingresos en verde y los
 * gastos en rojo son el contenido de media aplicacion, no decoracion. En un
 * movil a contraluz, o para quien no distingue esos dos, sencillamente no se
 * leian.
 *
 * La correccion NO cambia la identidad: cada color conserva su tono y baja de
 * luminosidad lo justo. El ambar de la ardilla sigue siendo ambar.
 *
 * Los numeros estan medidos, no estimados, y hay una prueba que los vuelve a
 * medir en cada construccion —ElContrasteSeMide—, para que el proximo color
 * que entre no pueda bajar del minimo sin que nadie se entere.
 */

export const darkColors = {
  background: '#1A1A2E',
  surface: '#16213E',
  surfaceLight: '#1F3460',
  primary: '#E8A838',
  primaryLight: '#F2C94C',
  /* Subido desde #6C5CE7: sobre la superficie oscura se quedaba en 3.27, y con
     el se escribe texto. */
  secondary: '#8B7CF6',
  accent: '#00B894',
  danger: '#FF6B6B',
  warning: '#FDCB6E',
  success: '#00B894',
  text: '#FFFFFF',
  textSecondary: '#A0A0B8',
  /* Subido desde #6C6C80 (3.10): es el color de las leyendas, o sea de texto
     pequeno, y ahi 3:1 no basta. */
  textMuted: '#8C8CA6',
  border: '#2D2D44',
  card: '#1E1E36',
  income: '#00B894',
  expense: '#FF6B6B',
  pending: '#FDCB6E',
};

export const lightColors = {
  background: '#F5F0E8',
  surface: '#FFFFFF',
  surfaceLight: '#FFF8F0',
  /* Antes #D4922A (2.33). Mismo ambar, mas oscuro: ahora tambien sostiene
     texto blanco encima en los botones. */
  primary: '#96650F',
  /* Antes #E8A838 (1.83). Sigue siendo el claro de la pareja, pero ya se ve. */
  primaryLight: '#B87A16',
  secondary: '#5A4BD1',
  /* Antes #00B894 (2.23). */
  accent: '#00795F',
  /* Antes #E74C3C (3.37): pasaba el minimo de elementos grandes pero no el de
     texto, y con el se escriben los gastos. */
  danger: '#C0392B',
  /* Antes #F39C12 (1.93), el peor de todos. */
  warning: '#9A5F00',
  /* Antes #27AE60 (2.53). */
  success: '#1A7340',
  text: '#2D2D2D',
  textSecondary: '#6B6B6B',
  /* Antes #9B9B9B (2.45). */
  textMuted: '#6C6C6C',
  border: '#E0D5C5',
  card: '#FFFFFF',
  income: '#1A7340',
  expense: '#C0392B',
  pending: '#9A5F00',
};

export type ThemeColors = typeof darkColors;
