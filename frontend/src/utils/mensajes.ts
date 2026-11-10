/*
 * LOS ERRORES SE DICEN EN ESPANOL.
 *
 * Parte del backend responde en ingles ("Invalid email or password",
 * "Category name already exists at this level", "must be less than or equal
 * to 500"), y esos textos llegaban tal cual al aviso de la pantalla. Aqui se
 * traducen en un solo sitio (lo usa el interceptor de api.ts), para que ninguna
 * pantalla tenga que acordarse. Lo que no se reconoce se deja como vino: un
 * mensaje raro es mejor que ninguno.
 */

const EXACTOS: Record<string, string> = {
  'invalid email or password': 'Correo o contraseña incorrectos.',
  'email already in use': 'Ese correo ya está registrado.',
  'email already in use by another user': 'Ese correo ya lo usa otra cuenta.',
  'password is required for new users': 'La contraseña es obligatoria.',
  'category name already exists at this level': 'Ya existe una categoría con ese nombre.',
  'category cannot be its own parent': 'Una categoría no puede estar dentro de sí misma.',
  'cannot delete category with active children': 'No se puede borrar: la categoría tiene subcategorías.',
  'cannot remove the household owner': 'No se puede sacar del hogar a quien lo creó.',
  'user is already a member': 'Esa persona ya es parte del hogar.',
  'card deleted': 'Tarjeta borrada.',
  'allocation deleted': 'Presupuesto quitado.',
  'access denied': 'No tienes permiso para hacer esto.',
  forbidden: 'No tienes permiso para hacer esto.',
  unauthorized: 'Tu sesión expiró. Vuelve a iniciar sesión.',
};

const ENTIDADES: Record<string, string> = {
  category: 'la categoría',
  'parent category': 'la categoría principal',
  'savings category': 'el ahorro',
  user: 'la cuenta',
  movement: 'el movimiento',
  'parent movement': 'el movimiento principal',
  household: 'el hogar',
  'scheduled movement': 'el programado',
  card: 'la tarjeta',
  debt: 'la deuda',
  member: 'esa persona en el hogar',
  allocation: 'el presupuesto',
};

export const enEspanol = (mensaje?: string | null): string => {
  if (!mensaje) return mensaje ?? '';
  const limpio = mensaje.trim();
  const clave = limpio.toLowerCase().replace(/[.!]+$/, '');
  if (EXACTOS[clave]) return EXACTOS[clave];

  const noEncontrado = /^(.+?) not found(?::\s*(.*))?$/i.exec(limpio);
  if (noEncontrado) {
    const entidad = ENTIDADES[noEncontrado[1].toLowerCase()];
    if (entidad) return `No se encontró ${entidad}.`;
  }

  const borrado = /^(.+?) deleted successfully$/i.exec(limpio);
  if (borrado && ENTIDADES[borrado[1].toLowerCase()]) return 'Borrado.';

  if (/^error\s+\w+ing\b/i.test(limpio)) return 'Algo falló en el servidor. Inténtalo otra vez.';

  /* Validaciones de Spring: "amount: must be less than or equal to 500". */
  const maximo = /must be less than or equal to ([\d.,]+)/i.exec(limpio);
  if (maximo) return `El valor debe ser como máximo ${maximo[1]}.`;
  const minimo = /must be greater than or equal to ([\d.,]+)/i.exec(limpio);
  if (minimo) return `El valor debe ser como mínimo ${minimo[1]}.`;
  if (/must be greater than 0/i.test(limpio)) return 'El valor debe ser mayor que cero.';
  if (/must not be (blank|null|empty)/i.test(limpio)) return 'Falta un dato obligatorio.';

  return limpio;
};

/**
 * El texto que conviene mostrar cuando una peticion lanza (HTTP 4xx/5xx): el
 * mensaje del servidor si lo trae (p. ej. el 429 del login, que ya viene en
 * espanol), luego el del error si no es el generico de axios, y si no, el de
 * respaldo.
 */
export const mensajeDelServidor = (err: any, respaldo: string): string => {
  const delServidor = err?.response?.data?.message;
  if (typeof delServidor === 'string' && delServidor.trim()) return enEspanol(delServidor);
  const propio = typeof err?.message === 'string' ? err.message : '';
  if (propio && !/^(Request failed with status code|Network Error|timeout of)/i.test(propio)) return enEspanol(propio);
  return respaldo;
};
