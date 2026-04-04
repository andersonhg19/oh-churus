import {
  validateRequired,
  validateEmail,
  validatePassword,
  validateAmount,
  validateDate,
  validateDayOfMonth,
  validateDuration,
  validateCategory,
  validateAll,
} from '../validators';

describe('validateRequired', () => {
  it('returns null for valid value', () => {
    expect(validateRequired('test', 'campo')).toBeNull();
  });
  it('returns error for empty string', () => {
    expect(validateRequired('', 'Nombre')).toBe('El campo "Nombre" es obligatorio');
  });
  it('returns error for null', () => {
    expect(validateRequired(null, 'Nombre')).toBe('El campo "Nombre" es obligatorio');
  });
  it('returns error for whitespace only', () => {
    expect(validateRequired('   ', 'Nombre')).toBe('El campo "Nombre" es obligatorio');
  });
  it('returns error for exceeding 100 chars', () => {
    const long = 'a'.repeat(101);
    expect(validateRequired(long, 'Nombre')).toBe('El campo "Nombre" no puede superar 100 caracteres');
  });
  it('accepts 100 chars', () => {
    expect(validateRequired('a'.repeat(100), 'Nombre')).toBeNull();
  });
});

describe('validateEmail', () => {
  it('returns null for valid email', () => {
    expect(validateEmail('user@correo.com')).toBeNull();
  });
  it('returns error for empty', () => {
    expect(validateEmail('')).toBe('El correo electronico es obligatorio');
  });
  it('returns error for null', () => {
    expect(validateEmail(null)).toBe('El correo electronico es obligatorio');
  });
  it('returns error for invalid format', () => {
    expect(validateEmail('noesuncorreo')).toBe('Ingresa un correo valido (ej: usuario@correo.com)');
  });
  it('returns error for missing @', () => {
    expect(validateEmail('usuario.correo.com')).toBe('Ingresa un correo valido (ej: usuario@correo.com)');
  });
  it('returns error for missing domain', () => {
    expect(validateEmail('usuario@')).toBe('Ingresa un correo valido (ej: usuario@correo.com)');
  });
  it('accepts email with dots', () => {
    expect(validateEmail('user.name@mail.co')).toBeNull();
  });
});

describe('validatePassword', () => {
  it('returns null for valid password', () => {
    expect(validatePassword('password123')).toBeNull();
  });
  it('returns error for empty', () => {
    expect(validatePassword('')).toBe('La contrasena es obligatoria');
  });
  it('returns error for null', () => {
    expect(validatePassword(null)).toBe('La contrasena es obligatoria');
  });
  it('returns error for too short', () => {
    expect(validatePassword('abc')).toBe('La contrasena debe tener al menos 6 caracteres');
  });
  it('accepts exactly min length', () => {
    expect(validatePassword('123456')).toBeNull();
  });
  it('respects custom min length', () => {
    expect(validatePassword('1234', 8)).toBe('La contrasena debe tener al menos 8 caracteres');
  });
  it('returns error for whitespace only', () => {
    expect(validatePassword('   ')).toBe('La contrasena es obligatoria');
  });
});

describe('validateAmount', () => {
  it('returns null for valid amount', () => {
    expect(validateAmount('150000')).toBeNull();
  });
  it('returns error for empty', () => {
    expect(validateAmount('')).toBe('El monto es obligatorio');
  });
  it('returns error for non-numeric', () => {
    expect(validateAmount('abc')).toBe('El monto debe ser un numero valido (ej: 150000)');
  });
  it('returns error for zero', () => {
    expect(validateAmount('0')).toBe('El monto debe ser mayor a $0');
  });
  it('returns error for negative', () => {
    expect(validateAmount('-5000')).toBe('El monto debe ser mayor a $0');
  });
  it('returns error for exceeding max', () => {
    expect(validateAmount('9999999999')).toBe('El monto no puede superar $999.999.999');
  });
  it('accepts decimal amounts', () => {
    expect(validateAmount('1500.50')).toBeNull();
  });
});

describe('validateDate', () => {
  it('returns null for valid date', () => {
    expect(validateDate('2026-03-15')).toBeNull();
  });
  it('returns error for empty', () => {
    expect(validateDate('')).toBe('La fecha es obligatoria');
  });
  it('returns error for wrong format', () => {
    expect(validateDate('15/03/2026')).toBe('La fecha debe tener formato AAAA-MM-DD (ej: 2026-03-15)');
  });
  it('returns error for invalid year', () => {
    expect(validateDate('1990-03-15')).toBe('El año debe estar entre 2020 y 2099');
  });
  it('returns error for invalid month', () => {
    expect(validateDate('2026-13-15')).toBe('El mes debe estar entre 01 y 12');
  });
  it('returns error for month 0', () => {
    expect(validateDate('2026-00-15')).toBe('El mes debe estar entre 01 y 12');
  });
  it('returns error for day 31 in February', () => {
    expect(validateDate('2026-02-31')).toBe('El dia debe estar entre 01 y 28 para el mes 02');
  });
  it('returns error for day 0', () => {
    expect(validateDate('2026-03-00')).toContain('El dia debe estar entre 01 y');
  });
  it('accepts Feb 29 in leap year', () => {
    expect(validateDate('2024-02-29')).toBeNull();
  });
  it('rejects Feb 29 in non-leap year', () => {
    expect(validateDate('2026-02-29')).toContain('El dia debe estar entre 01 y 28');
  });
});

describe('validateDayOfMonth', () => {
  it('returns null for empty (optional)', () => {
    expect(validateDayOfMonth('')).toBeNull();
  });
  it('returns null for valid day', () => {
    expect(validateDayOfMonth('15')).toBeNull();
  });
  it('returns error for 0', () => {
    expect(validateDayOfMonth('0')).toBe('El dia del mes debe estar entre 1 y 31');
  });
  it('returns error for 32', () => {
    expect(validateDayOfMonth('32')).toBe('El dia del mes debe estar entre 1 y 31');
  });
  it('returns error for text', () => {
    expect(validateDayOfMonth('abc')).toBe('El dia del mes debe estar entre 1 y 31');
  });
});

describe('validateDuration', () => {
  it('returns null for empty (optional)', () => {
    expect(validateDuration('')).toBeNull();
  });
  it('returns null for valid duration', () => {
    expect(validateDuration('12')).toBeNull();
  });
  it('returns error for 0', () => {
    expect(validateDuration('0')).toBe('La duracion debe ser al menos 1 mes');
  });
  it('returns error for negative', () => {
    expect(validateDuration('-5')).toBe('La duracion debe ser al menos 1 mes');
  });
  it('returns error for exceeding max', () => {
    expect(validateDuration('200')).toBe('La duracion no puede superar 120 meses (10 años)');
  });
});

describe('validateCategory', () => {
  it('returns null for valid id', () => {
    expect(validateCategory('5')).toBeNull();
  });
  it('returns error for null', () => {
    expect(validateCategory(null)).toBe('Debes seleccionar una categoria');
  });
  it('returns error for empty', () => {
    expect(validateCategory('')).toBe('Debes seleccionar una categoria');
  });
});

describe('validateAll', () => {
  it('returns null when all pass', () => {
    expect(validateAll(null, null, null)).toBeNull();
  });
  it('returns first error', () => {
    expect(validateAll(null, 'error1', 'error2')).toBe('error1');
  });
  it('returns single error', () => {
    expect(validateAll('solo error')).toBe('solo error');
  });
});
