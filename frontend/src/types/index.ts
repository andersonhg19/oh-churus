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
/**
 * Como se reparte un gasto entre varias personas.
 *
 *   EQUAL   "a medias". El 80 % de los casos.
 *   SHARES  "yo pago por dos porque vinieron los ninos".
 *   PERCENT "yo el 70 %, tu el 30 %", como el arriendo cuando uno gana mas.
 *   AMOUNT  "de estos 120.000, 45.000 son tuyos".
 */
/**
 * Un sobre: una categoria con lo que tiene disponible este periodo.
 *
 *   allocated  lo que le asignaste este mes
 *   carryover  lo que sobro del mes pasado. NUNCA es negativo: si te pasaste,
 *              el sobregiro no baja a la categoria, sale del total a repartir.
 *   spent      lo gastado, contando solo TU parte de los gastos repartidos
 *   available  allocated + carryover - spent
 *   label      "Disponible" o "Te pasaste", escrito. Un estado nunca puede
 *              depender solo del signo o del color.
 */
export interface Envelope {
  categoryId: string;
  categoryName: string;
  reimbursable: boolean;
  allocated: number;
  carryover: number;
  spent: number;
  available: number;
  label: string;
}

export interface EnvelopeState {
  periodStart: string;
  periodEnd: string;
  envelopes: Envelope[];
  totalAllocated: number;
  totalSpent: number;
  /** Lo que te pasaste el mes pasado y este mes tienes de menos. */
  carriedDebt: number;
  /** Lo que queda por repartir. Negativo = arrancas el mes con deuda. */
  toBudget: number;
}

export interface EnvelopeMove {
  fromCategoryId: string;
  toCategoryId: string;
  amount: number;
  message: string;
}

/**
 * Una fila del extracto tal y como la devuelve la vista previa.
 *
 * `suggestedCategoryId` sale del diccionario que la app aprendio de tus
 * importaciones anteriores; `matchedMovementId` viene relleno cuando la fila
 * casa con algo que ya tenias.
 */
export interface ImportRow {
  row: number;
  date: string;
  amount: number;
  description?: string;
  externalId?: string;
  suggestedType: CategoryType;
  suggestedCategoryId?: string;
  matchedMovementId?: string;
  reason: string;
}

/**
 * TRES listas, no dos.
 *
 *   newRows        no estaban: se crean
 *   duplicates     ya estaban: no se tocan
 *   confirmPending casan con un pendiente que genero una recurrencia, asi que
 *                  lo CONFIRMAN en vez de crear otro. Sin esta tercera lista,
 *                  importar el arriendo dejaria el pendiente colgando para
 *                  siempre y el gasto contado dos veces.
 */
export interface ImportPreview {
  newRows: ImportRow[];
  duplicates: ImportRow[];
  confirmPending: ImportRow[];
  total: number;
}

export interface ImportRowChoice {
  row: number;
  categoryId?: string;
  /** Cuando la fila confirma un pendiente en vez de crear un movimiento. */
  confirmsMovementId?: string;
}

export interface ImportResult {
  created: number;
  confirmed: number;
  skipped: string[];
  message: string;
}

/** El mapeo de columnas recordado por banco. */
export interface ImportProfile {
  id: string;
  bankName: string;
  dateColumn: number;
  amountColumn: number;
  descriptionColumn?: number;
  externalIdColumn?: number;
  datePattern?: string;
  decimalSeparator?: string;
  hasHeader: boolean;
  invertSign: boolean;
}

export type SplitMode = 'EQUAL' | 'SHARES' | 'PERCENT' | 'AMOUNT';

/**
 * Una linea del reparto. Se llama participantId y NO userId a proposito: no
 * dice quien hace la peticion —eso lo dice el token— sino a quien se le
 * reparte. El backend tiene una prueba que rompe el build si ese nombre vuelve
 * a ser userId.
 */
export interface SplitInput {
  participantId: number;
  /** Participaciones, porcentaje o importe, segun el modo. En EQUAL se ignora. */
  value?: number;
}

/** El balance NETO con una persona. Positivo = te debe; negativo = le debes. */
export interface Balance {
  userId: number;
  net: number;
  /** "Te debe" o "Le debes", escrito. Un estado nunca depende solo del signo. */
  label: string;
  amount: number;
}

export interface BalanceList {
  list: Balance[];
  totalOwedToMe: number;
  totalIOwe: number;
  net: number;
}

export interface Settlement {
  settlementId: string;
  amount: number;
  /** Lo decide el SIGNO DEL BALANCE, no quien pulsa el boton. */
  iPaid: boolean;
  message: string;
}

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
