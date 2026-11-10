// ---- Generic API Response ----
export interface ResultDTO<T> {
  correct: boolean;
  message: string;
  errorCode: number;
  object: T;
}

export interface PageDTO<T> {
  page: number;
  size: number;
  totalPage: number;
  list: T[];
}

// ---- Auth ----
export interface AuthResponse {
  token: string;
  userId: string;
  name: string;
  email: string;
  /**
   * Los mismos roles que van firmados dentro del token. Vienen aparte para no
   * tener que descodificar el JWT solo para saber si pintar el menu de
   * administracion. Lo que manda es el token; esto es comodidad de pintado.
   * Opcional porque un backend anterior a la ola 4 no lo envia.
   */
  roles?: string[];
}

// ---- Administracion (ola 4) ----
export interface AdminUser {
  id: number;
  name: string;
  email: string;
  budgetStartDay?: number;
  active: boolean;
  roles: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface Rol {
  id: number;
  name: string;
  description?: string;
  active: boolean;
  usuarios: number;
}

export interface PaginaDeModulo {
  id: number;
  code: string;
  name: string;
  route: string;
  icon?: string;
  inMenu: boolean;
  sortOrder: number;
  active: boolean;
}

export interface ModuloAdmin {
  id: number;
  code: string;
  name: string;
  icon?: string;
  sortOrder: number;
  active: boolean;
  pages: PaginaDeModulo[];
}

export interface PermisoDeRol {
  pageId: number;
  moduleCode: string;
  pageCode: string;
  pageName: string;
  canRead: boolean;
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
}

export interface PaginaDelMenu {
  code: string;
  name: string;
  route: string;
  icon?: string;
  inMenu: boolean;
  sortOrder: number;
  canRead: boolean;
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
}

export interface ModuloDelMenu {
  code: string;
  name: string;
  icon?: string;
  sortOrder: number;
  pages: PaginaDelMenu[];
}

export interface MenuPropio {
  modules: ModuloDelMenu[];
}

export interface Preferencias {
  currency: string;
  decimalPlaces: number;
  locale: string;
  /** Si viene de una eleccion propia o de la configuracion de la instalacion. */
  propia: boolean;
}

export interface AjusteGlobal {
  id: number;
  configKey: string;
  value: string;
  description?: string;
  active: boolean;
}

export interface LineaDeAuditoria {
  id: number;
  actorUserId?: number;
  actorEmail?: string;
  entityType: string;
  entityId?: number;
  action: string;
  details?: string;
  occurredAt: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

// ---- User ----
export interface User {
  id: string;
  name: string;
  email: string;
  budgetStartDay?: number;
  active?: boolean;
}

export interface ResultUserDTO {
  id: string;
  name: string;
  email: string;
  budgetStartDay?: number;
  active?: boolean;
}

export interface UserFilter {
  name?: string;
  email?: string;
  page?: number;
  size?: number;
}

// ---- Category ----
export type CategoryType = 'INCOME' | 'EXPENSE';

export interface Category {
  id: string;
  userId: string;
  name: string;
  description?: string;
  type: CategoryType;
  /**
   * "Es dinero que me van a devolver."
   *
   * Una categoria marcada asi no descuenta su sobregiro del total a repartir
   * del mes siguiente. Se llama por su caso de uso y no "excluida del
   * arrastre" porque nadie sabe si quiere lo segundo; en cambio todo el mundo
   * sabe si le van a devolver la plata.
   */
  reimbursable?: boolean;
  parentId?: string;
  icon?: string;
  color?: string;
  active?: boolean;
  householdId?: number;
  shared?: boolean;
}

export interface CategoryTree extends Category {
  children: CategoryTree[];
}

export interface CategoryFilter {
  userId?: string;
  type?: CategoryType;
  parentId?: string;
  name?: string;
  page?: number;
  size?: number;
}

// ---- Movement ----
export interface Movement {
  id: string;
  userId: string;
  categoryId: string;
  categoryName?: string;
  categoryType?: CategoryType;
  categoryIcon?: string;
  categoryColor?: string;
  /** Quien lo paga (ola 5). Nulo en los movimientos de antes: los paga su dueno. */
  payerUserId?: number;
  payerName?: string;
  /** La categoria es del hogar (true) o privada de su dueno (false). */
  shared?: boolean;
  amount: number;
  description?: string;
  date: string;
  confirmed: boolean;
  scheduledMovementId?: string;
  active?: boolean;
  /** Lo liberado de este pendiente del plan. */
  releasedAmount?: number;
  /** De que categoria de ahorro salio la plata (0 al guardar = ya no). */
  fundedFromCategoryId?: string | number | null;
  /** Con que tarjeta se pago (0 al guardar = ya no). */
  cardId?: string | number | null;
  /** La tarjeta ya se pago por esta compra o cuota. */
  cardPaid?: boolean;
  /** Que cuota es y de cuantas, si fue con tarjeta. */
  installmentNumber?: number | null;
  installmentTotal?: number | null;
  /** Las cuotas de una compra comparten este id. */
  purchaseId?: string | number | null;
  /** Solo al crear: a cuantas cuotas (1 = una). */
  installments?: number;
}

export interface MovementFilter {
  userId?: string;
  categoryId?: string;
  confirmed?: boolean;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}

// ---- Scheduled Movement ----
export type Frequency = 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'BIMONTHLY' | 'QUARTERLY' | 'SEMIANNUAL' | 'ANNUAL';

// Que hacer si la ocurrencia cae sabado o domingo. KEEP no la mueve, y es el
// valor por defecto: mover una fecha le cambia el mes al que pertenece el gasto.
export type WeekendPolicy = 'KEEP' | 'PREVIOUS_BUSINESS_DAY' | 'NEXT_BUSINESS_DAY';

export interface ScheduledMovement {
  id: string;
  userId: string;
  categoryId: string;
  categoryName?: string;
  categoryType?: CategoryType;
  name: string;
  amount: number;
  frequency: Frequency;
  startDate: string;
  endDate?: string;
  durationMonths?: number;
  dayOfMonth?: number;
  // "El tercer viernes": weekOfMonth 3 + dayOfWeek 5 (1 lunes .. 7 domingo).
  // El 5 en weekOfMonth significa "la ultima". Van los dos o ninguno.
  weekOfMonth?: number;
  dayOfWeek?: number;
  weekendPolicy?: WeekendPolicy;
  active?: boolean;
  /** Ola 5: quien lo paga (nulo = quien lo creo) y que clase de salida es. */
  payerUserId?: number;
  kind?: ScheduledKind;
  notes?: string;
  effectivePayerUserId?: number;
  payerName?: string;
  remainingOccurrences?: number;
  nextDate?: string;
  remainingTotal?: number;
  categoryIcon?: string;
  categoryHouseholdId?: number;
}

/**
 * PAYMENT: pago con fecha. DEDUCTION: descuento de nomina (resta del ingreso).
 * SAVING: ahorro. PROVISION: plata que se separa; no genera pendiente.
 */
export type ScheduledKind = 'PAYMENT' | 'DEDUCTION' | 'SAVING' | 'PROVISION';

// Una ocurrencia que tocaba y NO se creo: hay demasiadas atrasadas y el backend
// no las materializa en silencio. Se aceptan con scheduledService.materialize.
export interface ProposedOccurrence {
  scheduledMovementId: string;
  name: string;
  categoryId: string;
  categoryName?: string;
  categoryType?: CategoryType;
  amount: number;
  date: string;
  periodStart: string;
  overdue: boolean;
}

export interface GeneratePendingResult {
  created: Movement[];
  proposals: ProposedOccurrence[];
  proposalsTotal: number;
  needsReview: boolean;
}

export interface OccurrenceRef {
  scheduledMovementId: string;
  periodStart: string;
}

export interface ScheduledFilter {
  userId?: string;
  categoryId?: string;
  frequency?: Frequency;
  page?: number;
  size?: number;
}

// ---- Dashboard ----
export interface PendingMovement {
  id: string;
  description: string;
  amount: number;
  date: string;
  categoryName?: string;
  categoryType?: CategoryType;
}
