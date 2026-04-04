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
export interface Movement {
  id: string;
  userId: string;
  categoryId: string;
  categoryName?: string;
  categoryType?: CategoryType;
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
  active?: boolean;
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
