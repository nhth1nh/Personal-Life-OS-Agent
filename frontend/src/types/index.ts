export interface HealthDailyPoint {
  date: string;
  hrvRmssd: number | null;
  hrvBaselineMean: number | null;
  hrvUpperThreshold: number | null;
  hrvLowerThreshold: number | null;
  rhr: number | null;
  rhrBaselineMean: number | null;
  sleepScore: number | null;
  sleepDurationHours: number | null;
  sleepDebtHours: number | null;
  deepSleepHours: number | null;
  remSleepHours: number | null;
  lightSleepHours: number | null;
  awakeHours: number | null;
  bodyBatteryCharged: number | null;
  bodyBatteryDrained: number | null;
  bodyBatteryMax: number | null;
  bodyBatteryMin: number | null;
  stressAvg: number | null;
  steps: number | null;
  activeKcal: number | null;
  totalKcal: number | null;
  weightKg: number | null;
  weightEmaKg: number | null;
  recoveryState: 'PRIME' | 'NORMAL' | 'FATIGUED' | 'STRESSED' | null;
}

export interface HealthTimeseriesResponse {
  days: number;
  baselineHrvMean: number | null;
  baselineHrvStd: number | null;
  baselineRhrMean: number | null;
  baselineRhrStd: number | null;
  targetWeightKg: number;
  sleepTargetHours: number;
  points: HealthDailyPoint[];
}

export interface CategorySpend {
  category: string;
  totalVnd: number;
  percentage: number;
}

export interface DailySpend {
  date: string;
  totalVnd: number;
  fixedVnd: number;
  discretionaryVnd: number;
}

export interface FinanceAnalyticsResponse {
  month: string;
  monthlyBudgetVnd: number;
  totalSpendVnd: number;
  remainingBudgetVnd: number;
  fixedSpendVnd: number;
  discretionarySpendVnd: number;
  dailyBurnRateVnd: number;
  safeDailyAllowanceVnd: number;
  projectedMonthEndSpendVnd: number;
  elapsedDays: number;
  daysInMonth: number;
  categories: CategorySpend[];
  dailySpending: DailySpend[];
}

export interface Transaction {
  id: string;
  amount: number;
  merchant: string;
  category: string;
  txDate: string;
  spendType: 'DISCRETIONARY' | 'FIXED';
  invoiceNumber?: string;
  note?: string;
  confirmed: boolean;
  createdAt: string;
}

export interface OcrResult {
  merchant?: string;
  totalAmount?: number;
  category?: string;
  transactionDate?: string;
  invoiceNumber?: string;
  items?: Array<{ name: string; quantity?: number; price?: number }>;
  confidence?: number;
}

export interface ReadinessResult {
  targetDate: string;
  hrvRmssd?: number;
  hrv30dMean?: number;
  hrv30dStd?: number;
  hrvZScore?: number;
  rhr?: number;
  rhr30dMean?: number;
  rhr30dStd?: number;
  rhrZScore?: number;
  sleepDebtHours?: number;
  restorativeRatio?: number;
  recoveryLevel?: string;
  readinessSummary?: string;
}

export interface UserProfileData {
  targetWeightKg: number;
  sleepTargetHours: number;
  baselineWindowDays: number;
  monthlyBudgetVnd: number;
  userName?: string;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
  groundingPills?: string[];
}
