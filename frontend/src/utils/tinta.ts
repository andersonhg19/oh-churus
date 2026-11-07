/**
 * El color del icono sobre el circulo de una categoria: blanco sobre los
 * colores oscuros y casi negro sobre los claros (amarillo, lima, durazno),
 * donde un icono blanco no se ve.
 */
export function tintaSobre(fondo?: string): string {
  const hex = (fondo || '').replace('#', '');
  if (!/^[0-9a-fA-F]{6}$/.test(hex)) return '#FFFFFF';
  const canal = (i: number) => {
    const v = parseInt(hex.slice(i, i + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  const luz = 0.2126 * canal(0) + 0.7152 * canal(2) + 0.0722 * canal(4);
  return luz > 0.45 ? '#1A1405' : '#FFFFFF';
}
