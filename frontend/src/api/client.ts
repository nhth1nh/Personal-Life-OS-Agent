import type {
  HealthTimeseriesResponse,
  FinanceAnalyticsResponse,
  Transaction,
  OcrResult,
  ReadinessResult,
  ActivitySummary,
  CashFlowSummary,
  Debt,
  DebtSchedule,
  Income,
  PayoffSimulationComparison
} from '../types';

const API_BASE = '/api/v1';

export function getApiKey(): string {
  return localStorage.getItem('ploa_api_key') || 'ploa';
}

export function setApiKey(key: string): void {
  localStorage.setItem('ploa_api_key', key);
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const apiKey = getApiKey();
  const headers = new Headers(options.headers || {});
  
  if (apiKey) {
    headers.set('X-API-Key', apiKey);
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  if (!res.ok) {
    const errorText = await res.text().catch(() => '');
    throw new Error(`API Error ${res.status}: ${errorText || res.statusText}`);
  }

  if (res.status === 204 || res.headers.get('content-length') === '0') {
    return {} as T;
  }

  return res.json() as Promise<T>;
}

export interface SyncGarminOptions {
  startDate?: string;
  endDate?: string;
  days?: number;
}

export interface SyncGarminResponse {
  status: string;
  message: string;
  startDate?: string;
  endDate?: string;
  syncedDays?: number;
  failedDays?: number;
  dates?: string[];
}

export interface BatchSyncResponse {
  status: string;
  syncedCount: number;
  startDate: string;
  endDate: string;
  message: string;
}

export const api = {
  // Health
  getHealthTimeseries: (days = 30, startDate?: string, endDate?: string) => {
    let url = `/health/timeseries?days=${days}`;
    if (startDate && endDate) {
      url = `/health/timeseries?startDate=${startDate}&endDate=${endDate}`;
    }
    return request<HealthTimeseriesResponse>(url);
  },
  getReadiness: (date = 'today') => request<ReadinessResult>(`/health/readiness?date=${date}`),
  getTodayBiometrics: () => request<any>('/health/today'),
  triggerGarminPull: (options?: SyncGarminOptions) => {
    let url = '/health/pull';
    const params = new URLSearchParams();
    if (options?.startDate) params.set('startDate', options.startDate);
    if (options?.endDate) params.set('endDate', options.endDate);
    if (options?.days) params.set('days', options.days.toString());
    const qs = params.toString();
    if (qs) url += `?${qs}`;
    return request<SyncGarminResponse>(url, { method: 'POST' });
  },
  syncBatchHealthMetrics: (metrics: any[]) => request<BatchSyncResponse>('/health/sync/batch', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(metrics),
  }),
  getActivities: (params?: { date?: string; startDate?: string; endDate?: string; limit?: number }) => {
    let url = '/health/activities';
    const qs = new URLSearchParams();
    if (params?.date) qs.set('date', params.date);
    if (params?.startDate) qs.set('startDate', params.startDate);
    if (params?.endDate) qs.set('endDate', params.endDate);
    if (params?.limit) qs.set('limit', params.limit.toString());
    const query = qs.toString();
    if (query) url += `?${query}`;
    return request<ActivitySummary[]>(url);
  },

  // Finance Analytics & Cash Flow
  getFinanceAnalytics: (month?: string) => request<FinanceAnalyticsResponse>(month ? `/finance/analytics?month=${month}` : '/finance/analytics'),
  getCashFlow: () => request<CashFlowSummary>('/finance/cashflow'),
  getRecentTransactions: (limit = 30) => request<Transaction[]>(`/finance/transactions?limit=${limit}`),
  createDirectTransaction: (data: {
    amount: number;
    category?: string;
    spendType?: 'DISCRETIONARY' | 'FIXED';
    transactionType?: string;
    merchant?: string;
    txDate?: string;
    note?: string;
  }) => request<Transaction>('/finance/transactions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  }),
  confirmTransaction: (id: string) => request<Transaction>(`/finance/transactions/${id}/confirm`, { method: 'POST' }),
  deleteTransaction: (id: string) => request<void>(`/finance/transactions/${id}`, { method: 'DELETE' }),

  // Incomes
  getIncomes: (params?: { from?: string; to?: string; status?: string }) => {
    const qs = new URLSearchParams();
    if (params?.from) qs.set('from', params.from);
    if (params?.to) qs.set('to', params.to);
    if (params?.status) qs.set('status', params.status);
    const query = qs.toString();
    return request<Income[]>(`/finance/incomes${query ? `?${query}` : ''}`);
  },
  createIncome: (data: {
    source: string;
    expectedAmount: number;
    actualAmount?: number;
    expectedDate?: string;
    incomeType?: string;
    confidenceFactor?: number;
    isRecurring?: boolean;
    frequency?: string;
    notes?: string;
  }) => request<Income>('/finance/incomes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  }),
  markIncomeReceived: (id: string, data: { actualAmount?: number; receivedDate?: string; accountId?: string }) =>
    request<Income>(`/finance/incomes/${id}/receive`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    }),
  deleteIncome: (id: string) => request<void>(`/finance/incomes/${id}`, { method: 'DELETE' }),

  // Debts & Simulations
  getDebts: () => request<Debt[]>('/finance/debts'),
  getDebtById: (id: string) => request<Debt>(`/finance/debts/${id}`),
  createDebt: (data: {
    name: string;
    creditor: string;
    debtType?: string;
    originalPrincipal: number;
    currentPrincipal?: number;
    interestRateApr?: number;
    interestAccrualConvention?: string;
    monthlyMinimumDue?: number;
    dueDayOfMonth?: number;
    originationDate?: string;
    maturityDate?: string;
    notes?: string;
  }) => request<Debt>('/finance/debts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  }),
  getDebtSchedule: (debtId: string) => request<DebtSchedule[]>(`/finance/debts/${debtId}/schedule`),
  recordDebtPayment: (debtId: string, data: {
    totalPaid: number;
    principalPortion?: number;
    interestPortion?: number;
    feePortion?: number;
    paymentDate?: string;
    note?: string;
  }) => request<any>(`/finance/debts/${debtId}/payments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  }),
  simulateDebtPayoff: (surplus = 0) =>
    request<PayoffSimulationComparison>(`/finance/debts/simulate?surplus=${surplus}`),

  // OCR
  uploadReceipt: async (file: File): Promise<OcrResult> => {
    const formData = new FormData();
    formData.append('file', file);
    const apiKey = getApiKey();
    const res = await fetch(`${API_BASE}/finance/ocr`, {
      method: 'POST',
      headers: {
        'X-API-Key': apiKey,
      },
      body: formData,
    });
    if (!res.ok) throw new Error(`OCR upload failed: ${res.statusText}`);
    return res.json();
  },

  // Briefings
  getMorningBriefing: () => request<{ briefing: string }>('/briefing/morning/preview'),
  getEveningSummary: () => request<{ summary: string }>('/briefing/evening/preview'),

  // Assistant Chat
  chat: async (message: string, file?: File): Promise<{ reply: string }> => {
    const apiKey = getApiKey();
    if (file) {
      const formData = new FormData();
      formData.append('message', message);
      formData.append('file', file);
      const res = await fetch(`${API_BASE}/chat/multimodal`, {
        method: 'POST',
        headers: { 'X-API-Key': apiKey },
        body: formData,
      });
      if (!res.ok) throw new Error(`Chat failed: ${res.statusText}`);
      return res.json();
    } else {
      return request<{ reply: string }>('/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message }),
      });
    }
  },
};
