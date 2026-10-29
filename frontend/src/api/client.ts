import type {
  HealthTimeseriesResponse,
  FinanceAnalyticsResponse,
  Transaction,
  OcrResult,
  ReadinessResult
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

export const api = {
  // Health
  getHealthTimeseries: (days = 30) => request<HealthTimeseriesResponse>(`/health/timeseries?days=${days}`),
  getReadiness: (date = 'today') => request<ReadinessResult>(`/health/readiness?date=${date}`),
  getTodayBiometrics: () => request<any>('/health/today'),
  triggerGarminPull: () => request<{ status: string; message: string }>('/health/pull', { method: 'POST' }),

  // Finance
  getFinanceAnalytics: (month?: string) => request<FinanceAnalyticsResponse>(month ? `/finance/analytics?month=${month}` : '/finance/analytics'),
  getRecentTransactions: (limit = 30) => request<Transaction[]>(`/finance/transactions?limit=${limit}`),
  confirmTransaction: (id: string) => request<Transaction>(`/finance/transactions/${id}/confirm`, { method: 'POST' }),
  deleteTransaction: (id: string) => request<void>(`/finance/transactions/${id}`, { method: 'DELETE' }),

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
      const res = await fetch(`${API_BASE}/assistant/chat/multimodal`, {
        method: 'POST',
        headers: { 'X-API-Key': apiKey },
        body: formData,
      });
      if (!res.ok) throw new Error(`Chat failed: ${res.statusText}`);
      return res.json();
    } else {
      return request<{ reply: string }>('/assistant/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message }),
      });
    }
  },
};
