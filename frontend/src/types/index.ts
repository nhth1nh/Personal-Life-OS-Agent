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

export interface ActivitySummary {
  id: string;
  name: string;
  activityType: string;
  startTime: string;
  date: string;
  durationMinutes: number | null;
  distanceKm: number | null;
  calories: number | null;
  averageHr: number | null;
  maxHr: number | null;
  aerobicTrainingEffect: number | null;
  anaerobicTrainingEffect: number | null;
  trainingEffectLabel: string | null;
  trainingLoad: number | null;
  avgPaceMinPerKm: number | null;
  avgSpeedKmh: number | null;
  elevationGainMeters: number | null;
  steps: number | null;
  moderateMinutes: number | null;
  vigorousMinutes: number | null;
  differenceBodyBattery: number | null;
  hrZonesJsonb?: Record<string, number> | null;
  splitsJsonb?: any;
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
  transactionType?: string;
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

// ── Cash Flow & Debt Redesign Types ──────────────────────────────────────

export type LiquidityStatus = 'SOLVENT' | 'PROJECTED_SHORTFALL' | 'LIQUIDITY_DEFICIT';

export interface CashFlowSummary {
  asOfDate: string;
  liquidCashNow: number;
  expectedInflowsBeforeMonthEnd: number;
  unpaidDebtObligationsBeforeMonthEnd: number;
  unpaidFixedBillsBeforeMonthEnd: number;
  emergencyReserveFloor: number;
  spendableCash: number;
  daysRemainingInMonth: number;
  safeDailyAllowance: number;
  liquidityStatus: LiquidityStatus;
  mtdIncomeReceived: number;
  mtdOperatingSpend: number;
  mtdDebtPrincipalPaid: number;
  netCashFlowMtd: number;
  netSavingsMtd: number;
  recommendation: string;
  solvent: boolean;
  deficit: boolean;
}

export interface Debt {
  id: string;
  name: string;
  creditor: string;
  debtType: 'AMORTIZING_LOAN' | 'REVOLVING_CREDIT' | 'INSTALLMENT_BNPL' | 'PEER_DEBT';
  currency: string;
  originalPrincipal: number;
  currentPrincipal: number;
  interestRateApr: number;
  interestAccrualConvention: 'ACTUAL_365' | 'THIRTY_360' | 'MONTHLY_FLAT';
  monthlyMinimumDue: number;
  dueDayOfMonth: number;
  originationDate: string;
  maturityDate?: string;
  status: 'ACTIVE' | 'PAID_OFF' | 'DEFAULTED' | 'SETTLED';
  notes?: string;
}

export interface DebtSchedule {
  id: string;
  installmentNumber: number;
  dueDate: string;
  expectedPayment: number;
  expectedPrincipal: number;
  expectedInterest: number;
  remainingPrincipalAfter: number;
  status: 'UPCOMING' | 'PAID' | 'OVERDUE';
}

export interface Income {
  id: string;
  source: string;
  expectedAmount: number;
  actualAmount?: number;
  currency: string;
  expectedDate: string;
  receivedDate?: string;
  incomeType: 'SALARY' | 'BONUS' | 'FREELANCE' | 'INVESTMENT' | 'OTHER';
  confidenceFactor: number;
  isRecurring: boolean;
  frequency?: 'MONTHLY' | 'BIWEEKLY' | 'AD_HOC';
  status: 'EXPECTED' | 'RECEIVED' | 'CANCELLED';
  note?: string;
}

export interface DebtPayoffDetail {
  debtId: string;
  debtName: string;
  startingPrincipal: number;
  totalInterestPaid: number;
  totalPrincipalPaid: number;
  monthsToPayoff: number;
  payoffDate: string;
}

export interface PayoffStrategyResult {
  strategy: 'BASELINE' | 'AVALANCHE' | 'SNOWBALL';
  debtFreeDate: string;
  totalMonths: number;
  totalInterestPaid: number;
  totalPrincipalPaid: number;
  totalPayments: number;
  debtDetails: DebtPayoffDetail[];
}

export interface PayoffSimulationComparison {
  monthlySurplusApplied: number;
  baseline: PayoffStrategyResult;
  avalanche: PayoffStrategyResult;
  snowball: PayoffStrategyResult;
  avalancheInterestSaved: number;
  avalancheMonthsSaved: number;
  snowballInterestSaved: number;
  snowballMonthsSaved: number;
}
