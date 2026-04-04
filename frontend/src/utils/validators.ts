/**
 * Validadores centralizados con mensajes claros en español.
 * Retorna null si es valido, o el mensaje de error si no.
 */

export const validateRequired = (value: string | null | undefined, fieldName: string): string | null => {
  if (!value || !value.trim()) {
    return `El campo "${fieldName}" es obligatorio`;
  }
  if (value.trim().length > 100) {
    return `El campo "${fieldName}" no puede superar 100 caracteres`;
  }
  return null;
};

export const validateEmail = (value: string | null | undefined): string | null => {
  if (!value || !value.trim()) {
    return 'El correo electronico es obligatorio';
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())) {
    return 'Ingresa un correo valido (ej: usuario@correo.com)';
  }
  return null;
};

export const validatePassword = (value: string | null | undefined, minLength: number = 6): string | null => {
  if (!value || !value.trim()) {
    return 'La contrasena es obligatoria';
  }
  if (value.length < minLength) {
    return `La contrasena debe tener al menos ${minLength} caracteres`;
  }
  return null;
};

export const validateAmount = (value: string): string | null => {
  if (!value || !value.trim()) {
    return 'El monto es obligatorio';
  }
  const parsed = parseFloat(value);
  if (isNaN(parsed)) {
    return 'El monto debe ser un numero valido (ej: 150000)';
  }
  if (parsed <= 0) {
    return 'El monto debe ser mayor a $0';
  }
  if (parsed > 999999999) {
    return 'El monto no puede superar $999.999.999';
  }
  return null;
};

export const validateDate = (value: string): string | null => {
  if (!value || !value.trim()) {
    return 'La fecha es obligatoria';
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return 'La fecha debe tener formato AAAA-MM-DD (ej: 2026-03-15)';
  }
  const [year, month, day] = value.split('-').map(Number);
  if (year < 2020 || year > 2099) {
    return 'El año debe estar entre 2020 y 2099';
  }
  if (month < 1 || month > 12) {
    return 'El mes debe estar entre 01 y 12';
  }
  const daysInMonth = new Date(year, month, 0).getDate();
  if (day < 1 || day > daysInMonth) {
    return `El dia debe estar entre 01 y ${daysInMonth} para el mes ${String(month).padStart(2, '0')}`;
  }
  return null;
};

export const validateDayOfMonth = (value: string): string | null => {
  if (!value || !value.trim()) return null; // opcional
  const parsed = parseInt(value, 10);
  if (isNaN(parsed) || parsed < 1 || parsed > 31) {
    return 'El dia del mes debe estar entre 1 y 31';
  }
  return null;
};

export const validateDuration = (value: string): string | null => {
  if (!value || !value.trim()) return null; // opcional
  const parsed = parseInt(value, 10);
  if (isNaN(parsed) || parsed < 1) {
    return 'La duracion debe ser al menos 1 mes';
  }
  if (parsed > 120) {
    return 'La duracion no puede superar 120 meses (10 años)';
  }
  return null;
};

export const validateCategory = (categoryId: string | null | undefined): string | null => {
  if (!categoryId) {
    return 'Debes seleccionar una categoria';
  }
  return null;
};

/**
 * Ejecuta multiples validaciones y retorna el primer error encontrado.
 */
export const validateAll = (...validations: (string | null)[]): string | null => {
  for (const error of validations) {
    if (error) return error;
  }
  return null;
};
