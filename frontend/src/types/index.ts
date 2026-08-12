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
export type AccountKind = 'OWN' | 'LIABILITY';

/**
 * Una cuenta: donde esta la plata.
 *
 * Ojo con `balance` y `projectedBalance`: NO son campos guardados. El backend
 * los calcula sumando los movimientos en cada peticion, asi que llegan siempre
 * frescos y no hay nada que refrescar ni invalidar aqui.
 *
 *   balance          lo confirmado. Es lo que deberia decir el banco, y con lo
 *                    que se concilia.
 *   projectedBalance incluye lo pendiente: en que quedaria la cuenta si todo
 *                    lo anotado llega a ocurrir.
 *
 * En una cuenta de clase LIABILITY (tarjeta, prestamo) el saldo es NEGATIVO
 * cuando debes. La clase no cambia la aritmetica, solo como se presenta:
 * "debes 400.000" en vez de "tienes -400.000".
 */
export interface Account {
  id: string;
  userId: string;
  name: string;
  kind: AccountKind;
  icon?: string;
  color?: string;
  householdId?: number;
  isDefault?: boolean;
  balance: number;
  projectedBalance: number;
}

export interface AccountList {
  list: Account[];
  /** Suma de TODOS los saldos con su signo. Un pasivo ya resta por si solo. */
  netWorth: number;
}

export interface Reconciliation {
  accountId: string;
  date: string;
  appBalance: number;
  realBalance: number;
  difference: number;
  adjusted: boolean;
  adjustmentId?: string;
  message: string;
}

export interface Movement {
  id: string;
  userId: string;
  categoryId: string;
  categoryName?: string;
  categoryType?: CategoryType;
  accountId?: string;
  accountName?: string;
  isOpening?: boolean;
  amount: number;
  description?: string;
  date: string;
  confirmed: boolean;
  scheduledMovementId?: string;
  active?: boolean;
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
}

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
export interface DashboardSummary {
  totalIncome: number;
  totalExpense: number;
  balance: number;
  budgetTotal: number;
  pendingCount: number;
  pendingAmount: number;
  periodStart: string;
  periodEnd: string;
}

export interface CategorySummary {
  categoryId: string;
  categoryName: string;
  categoryType: CategoryType;
  total: number;
  percentage: number;
  color?: string;
  icon?: string;
}

export interface TrendData {
  period: string;
  income: number;
  expense: number;
  balance: number;
}

export interface PendingMovement {
  id: string;
  description: string;
  amount: number;
  date: string;
  categoryName?: string;
  categoryType?: CategoryType;
}
