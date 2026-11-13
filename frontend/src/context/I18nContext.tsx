import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';

export type Locale = 'vi' | 'en';

export interface SupportedLocale {
  code: string;
  name: string;
  flag: string;
}

interface I18nContextType {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: string, fallback?: string) => string;
  supportedLocales: SupportedLocale[];
  loading: boolean;
}

const DEFAULT_SUPPORTED_LOCALES: SupportedLocale[] = [
  { code: 'vi', name: 'Tiếng Việt', flag: '🇻🇳' },
  { code: 'en', name: 'English', flag: '🇬🇧' }
];

// Baseline instant fallback dictionary to prevent flash of untranslated text
const BASELINE_FALLBACKS: Record<Locale, Record<string, string>> = {
  vi: {
    'nav.dashboard': 'Bảng điều khiển',
    'nav.health': 'Chỉ số sinh học',
    'nav.finance': 'Tài chính & Sổ cái',
    'nav.scan': 'Quét hóa đơn',
    'nav.chat': 'Trợ lý AI',
    'header.sync_garmin': 'Đồng bộ Garmin',
    'header.syncing': 'Đang đồng bộ...',
    'header.api_key': 'Mã API',
    'dashboard.overview_title': 'Trung tâm Điều hành Trực quan',
    'dashboard.morning_briefing': 'Điểm tin Buổi sáng',
    'dashboard.evening_briefing': 'Tổng kết Buổi tối',
    'dashboard.refresh_briefing': 'Làm mới điểm tin',
    'dashboard.readiness_title': 'Độ sẵn sàng Phục hồi',
    'dashboard.body_battery_title': 'Pin Cơ thể (Body Battery)',
    'dashboard.sleep_debt_title': 'Nợ Giấc ngủ (7 ngày)',
    'dashboard.weight_trend_title': 'Cân nặng EMA 7 ngày',
    'dashboard.safe_daily_title': 'Hạn mức Chi tiêu An toàn',
    'dashboard.burn_rate_title': 'Tốc độ Chi tiêu Thực tế',
    'chat.title': 'Cộng sự Đàm thoại Life OS',
    'chat.subtitle': 'Cố vấn ngữ cảnh vận hành trên sinh học & tài chính thực tế',
    'chat.placeholder': 'Hỏi về mức độ phục hồi, cường độ tập, ngân sách, hoặc gửi ảnh hóa đơn...',
    'scanner.title': 'Máy quét Chứng từ & Hóa đơn Đa phương thức',
    'finance.title': 'Trung tâm Tài chính Cá nhân & Quản lý Nợ',
    'health.title': 'Hệ thống Sinh học & Phục hồi Thể chất'
  },
  en: {
    'nav.dashboard': 'Dashboard',
    'nav.health': 'Biometrics',
    'nav.finance': 'Finance & Ledger',
    'nav.scan': 'Receipt Scanner',
    'nav.chat': 'AI Partner',
    'header.sync_garmin': 'Sync Garmin',
    'header.syncing': 'Syncing...',
    'header.api_key': 'API Key',
    'dashboard.overview_title': 'Executive Control Center',
    'dashboard.morning_briefing': 'Morning Briefing',
    'dashboard.evening_briefing': 'Evening Summary',
    'dashboard.refresh_briefing': 'Refresh Briefing',
    'dashboard.readiness_title': 'Recovery Readiness',
    'dashboard.body_battery_title': 'Body Battery',
    'dashboard.sleep_debt_title': 'Sleep Debt (7D)',
    'dashboard.weight_trend_title': 'Weight 7-Day EMA',
    'dashboard.safe_daily_title': 'Safe Daily Allowance',
    'dashboard.burn_rate_title': 'Daily Burn Rate',
    'chat.title': 'Life OS Conversational Partner',
    'chat.subtitle': 'Contextual advisor powered by real-time biometrics & finance',
    'chat.placeholder': 'Ask about recovery, training intensity, budget, or upload a receipt...',
    'scanner.title': 'Multimodal Document & Receipt Scanner',
    'finance.title': 'Personal Finance & Debt Ledger',
    'health.title': 'Physical Health & Recovery Engine'
  }
};

const I18nContext = createContext<I18nContextType | undefined>(undefined);

function getInitialLocale(): Locale {
  if (typeof window !== 'undefined') {
    const saved = localStorage.getItem('ploa_ui_locale');
    if (saved === 'vi' || saved === 'en') {
      return saved;
    }
  }
  return 'vi';
}

export const I18nProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [locale, setLocaleState] = useState<Locale>(getInitialLocale);
  const [translations, setTranslations] = useState<Record<string, string>>(() => BASELINE_FALLBACKS[getInitialLocale()]);
  const [supportedLocales, setSupportedLocales] = useState<SupportedLocale[]>(DEFAULT_SUPPORTED_LOCALES);
  const [loading, setLoading] = useState<boolean>(false);

  // Fetch translations from CMS backend
  const loadCmsTranslations = useCallback(async (targetLocale: Locale) => {
    try {
      setLoading(true);
      const res = await api.getCmsTranslations(targetLocale);
      if (res && res.translations) {
        setTranslations({
          ...BASELINE_FALLBACKS[targetLocale],
          ...res.translations
        });
      }
    } catch (err) {
      console.warn(`[CMS-I18N] Failed to fetch CMS translations for ${targetLocale}, keeping baseline fallback:`, err);
    } finally {
      setLoading(false);
    }
  }, []);

  // Fetch supported locales once
  useEffect(() => {
    api.getSupportedLocales()
      .then((locales) => {
        if (Array.isArray(locales) && locales.length > 0) {
          setSupportedLocales(locales);
        }
      })
      .catch(() => {
        // Fallback already populated
      });
  }, []);

  // Sync translations whenever locale changes
  useEffect(() => {
    loadCmsTranslations(locale);
  }, [locale, loadCmsTranslations]);

  const setLocale = (newLocale: Locale) => {
    setLocaleState(newLocale);
    if (typeof window !== 'undefined') {
      localStorage.setItem('ploa_ui_locale', newLocale);
    }
  };

  const t = useCallback((key: string, fallback?: string): string => {
    if (translations && translations[key] !== undefined) {
      return translations[key];
    }
    const baseline = BASELINE_FALLBACKS[locale]?.[key];
    if (baseline !== undefined) {
      return baseline;
    }
    return fallback !== undefined ? fallback : key;
  }, [translations, locale]);

  return (
    <I18nContext.Provider value={{ locale, setLocale, t, supportedLocales, loading }}>
      {children}
    </I18nContext.Provider>
  );
};

export const useI18n = (): I18nContextType => {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error('useI18n must be used within an I18nProvider');
  }
  return context;
};
