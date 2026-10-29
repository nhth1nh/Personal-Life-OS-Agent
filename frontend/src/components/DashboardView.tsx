import React, { useEffect, useState } from 'react';
import { 
  Heart, 
  BatteryCharging, 
  Moon, 
  TrendingUp, 
  Sparkles, 
  SunMedium, 
  RefreshCw,
  ArrowRight,
  ShieldAlert,
  ShieldCheck,
  Zap
} from 'lucide-react';
import { api } from '../api/client';
import type { ReadinessResult, FinanceAnalyticsResponse, ActivitySummary } from '../types';

interface DashboardViewProps {
  onNavigate: (tab: string) => void;
}

export const DashboardView: React.FC<DashboardViewProps> = ({ onNavigate }) => {
  const [readiness, setReadiness] = useState<ReadinessResult | null>(null);
  const [todayBio, setTodayBio] = useState<any>(null);
  const [finance, setFinance] = useState<FinanceAnalyticsResponse | null>(null);
  const [recentWorkout, setRecentWorkout] = useState<ActivitySummary | null>(null);
  const [briefing, setBriefing] = useState<string>('');
  const [briefingType, setBriefingType] = useState<'morning' | 'evening'>('morning');
  const [loadingBriefing, setLoadingBriefing] = useState<boolean>(false);
  const [, setLoading] = useState<boolean>(true);

  useEffect(() => {
    loadDashboardData();
  }, []);

  const loadDashboardData = async () => {
    try {
      setLoading(true);
      const [r, bio, fin, acts] = await Promise.allSettled([
        api.getReadiness(),
        api.getTodayBiometrics(),
        api.getFinanceAnalytics(),
        api.getActivities({ limit: 1 }),
      ]);

      if (r.status === 'fulfilled') setReadiness(r.value);
      if (bio.status === 'fulfilled') setTodayBio(bio.value);
      if (fin.status === 'fulfilled') setFinance(fin.value);
      if (acts.status === 'fulfilled' && acts.value && acts.value.length > 0) {
        setRecentWorkout(acts.value[0]);
      }

      await loadBriefing('morning');
    } catch (err) {
      console.error('Failed to load dashboard data', err);
    } finally {
      setLoading(false);
    }
  };

  const loadBriefing = async (type: 'morning' | 'evening') => {
    try {
      setLoadingBriefing(true);
      setBriefingType(type);
      if (type === 'morning') {
        const res = await api.getMorningBriefing();
        setBriefing(res.briefing || 'No morning briefing generated yet.');
      } else {
        const res = await api.getEveningSummary();
        setBriefing(res.summary || 'No evening summary generated yet.');
      }
    } catch (err) {
      setBriefing('Unable to load briefing. Ensure backend and AI models are running.');
    } finally {
      setLoadingBriefing(false);
    }
  };

  const getRecoveryBadge = (level?: string) => {
    switch (level?.toUpperCase()) {
      case 'PRIME':
        return { label: 'PRIME RECOVERY', color: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40', icon: ShieldCheck };
      case 'NORMAL':
        return { label: 'NORMAL RECOVERY', color: 'bg-blue-500/20 text-blue-400 border-blue-500/40', icon: ShieldCheck };
      case 'FATIGUED':
        return { label: 'FATIGUED (REST RECOMMENDED)', color: 'bg-amber-500/20 text-amber-400 border-amber-500/40', icon: ShieldAlert };
      case 'STRESSED':
        return { label: 'HIGH STRESS / DELOAD', color: 'bg-rose-500/20 text-rose-400 border-rose-500/40', icon: ShieldAlert };
      default:
        return { label: 'CALCULATING BASELINE', color: 'bg-slate-700/50 text-slate-400 border-slate-600', icon: Sparkles };
    }
  };

  const formatVnd = (amount?: number) => {
    if (amount == null) return '-- VND';
    return new Intl.NumberFormat('vi-VN').format(amount) + ' VND';
  };

  const badge = getRecoveryBadge(readiness?.recoveryLevel);
  const BadgeIcon = badge.icon;

  return (
    <div className="space-y-6">
      {/* Top Banner: Recovery Status & Vital Signals */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {/* Recovery State Card */}
        <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 relative overflow-hidden backdrop-blur-md">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Physiological Readiness</span>
            <Heart className="w-4 h-4 text-rose-400" />
          </div>
          <div className="flex items-baseline space-x-2">
            <div className="text-2xl font-bold text-white tracking-tight">
              {todayBio?.hrvRmssd != null ? `${todayBio.hrvRmssd} ms` : readiness?.hrvRmssd != null ? `${readiness.hrvRmssd} ms` : '--'}
            </div>
            <span className="text-xs text-slate-400">rMSSD</span>
          </div>
          <div className="mt-3 flex items-center space-x-2">
            <div className={`px-2 py-0.5 rounded-full border text-[11px] font-medium flex items-center space-x-1.5 ${badge.color}`}>
              <BadgeIcon className="w-3 h-3" />
              <span>{badge.label}</span>
            </div>
          </div>
          <div className="mt-3 text-xs text-slate-400 flex justify-between border-t border-slate-800/80 pt-2.5">
            <span>RHR: <strong className="text-slate-200">{todayBio?.rhr ?? readiness?.rhr ?? '--'} bpm</strong></span>
            <span>Z-Score: <strong className="text-slate-200">{readiness?.hrvZScore != null ? (readiness.hrvZScore > 0 ? `+${readiness.hrvZScore}` : readiness.hrvZScore) : '--'}</strong></span>
          </div>
        </div>

        {/* Body Battery & Stress Card */}
        <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 relative overflow-hidden backdrop-blur-md">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Body Battery & Energy</span>
            <BatteryCharging className="w-4 h-4 text-cyan-400" />
          </div>
          <div className="flex items-baseline space-x-2">
            <div className="text-2xl font-bold text-cyan-300 tracking-tight">
              {todayBio?.bodyBatteryCurrent ?? '--'}
            </div>
            <span className="text-xs text-slate-400">/ 100</span>
          </div>
          <div className="mt-3 flex items-center space-x-2 text-xs">
            <span className="text-emerald-400">+{todayBio?.bodyBatteryCharged ?? '--'} chg</span>
            <span className="text-slate-500">|</span>
            <span className="text-rose-400">-{todayBio?.bodyBatteryDrained ?? '--'} drn</span>
          </div>
          <div className="mt-3 text-xs text-slate-400 flex justify-between border-t border-slate-800/80 pt-2.5">
            <span>Stress Avg: <strong className="text-slate-200">{todayBio?.stressAvg ?? '--'}</strong></span>
            <span>Max Stress: <strong className="text-slate-200">{todayBio?.stressMax ?? '--'}</strong></span>
          </div>
        </div>

        {/* Sleep Architecture Card */}
        <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 relative overflow-hidden backdrop-blur-md">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Sleep Recovery</span>
            <Moon className="w-4 h-4 text-indigo-400" />
          </div>
          <div className="flex items-baseline space-x-2">
            <div className="text-2xl font-bold text-indigo-200 tracking-tight">
              {todayBio?.sleepTotalMin ? `${Math.round((todayBio.sleepTotalMin / 60) * 10) / 10}h` : '--'}
            </div>
            <span className="text-xs text-slate-400">
              Score: <strong className="text-slate-100">{todayBio?.sleepScore ?? '--'}</strong>
            </span>
          </div>
          <div className="mt-3 text-xs text-slate-300">
            Sleep Debt: <span className={readiness?.sleepDebtHours && readiness.sleepDebtHours > 1 ? 'text-amber-400 font-semibold' : 'text-emerald-400'}>
              {readiness?.sleepDebtHours != null ? `${readiness.sleepDebtHours}h` : '0h'}
            </span>
          </div>
          <div className="mt-3 text-xs text-slate-400 flex justify-between border-t border-slate-800/80 pt-2.5">
            <span>Deep: <strong className="text-slate-200">{todayBio?.sleepDeepMin ? `${Math.round(todayBio.sleepDeepMin / 60 * 10) / 10}h` : '--'}</strong></span>
            <span>REM: <strong className="text-slate-200">{todayBio?.sleepRemMin ? `${Math.round(todayBio.sleepRemMin / 60 * 10) / 10}h` : '--'}</strong></span>
          </div>
        </div>

        {/* Safe Daily Allowance & Burn Rate Card */}
        <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 relative overflow-hidden backdrop-blur-md">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Safe Daily Allowance</span>
            <TrendingUp className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="flex items-baseline space-x-2">
            <div className="text-2xl font-bold text-emerald-300 tracking-tight">
              {formatVnd(finance?.safeDailyAllowanceVnd)}
            </div>
          </div>
          <div className="mt-3 text-xs text-slate-400">
            Remaining Budget: <strong className="text-slate-200">{formatVnd(finance?.remainingBudgetVnd)}</strong>
          </div>
          <div className="mt-3 text-xs text-slate-400 flex justify-between border-t border-slate-800/80 pt-2.5">
            <span>Daily Burn: <strong className="text-slate-200">{formatVnd(finance?.dailyBurnRateVnd)}</strong></span>
            <span>Spend: <strong className="text-slate-200">{finance?.totalSpendVnd != null ? `${Math.round((finance.totalSpendVnd / finance.monthlyBudgetVnd) * 100)}%` : '--'}</strong></span>
          </div>
        </div>
      </div>

      {/* Recent Workout Quick Highlight */}
      {recentWorkout && (
        <div 
          onClick={() => onNavigate('health')}
          className="bg-slate-900/60 hover:bg-slate-900/90 border border-slate-800 hover:border-slate-700 rounded-2xl p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 cursor-pointer transition-all backdrop-blur-md"
        >
          <div className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center flex-shrink-0">
              <Zap className="w-5 h-5 text-cyan-400" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <span className="text-xs font-semibold text-white">{recentWorkout.name}</span>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 capitalize font-medium">
                  {recentWorkout.activityType}
                </span>
                <span className="text-[11px] text-slate-400 font-mono">{recentWorkout.date}</span>
              </div>
              <div className="flex items-center space-x-4 mt-1 text-xs text-slate-400">
                {recentWorkout.durationMinutes && (
                  <span>⏱️ <strong>{Math.round(recentWorkout.durationMinutes)} min</strong></span>
                )}
                {recentWorkout.calories && (
                  <span>🔥 <strong>{recentWorkout.calories} kcal</strong></span>
                )}
                {recentWorkout.averageHr && (
                  <span>❤️ <strong>{recentWorkout.averageHr} bpm avg</strong></span>
                )}
                {recentWorkout.aerobicTrainingEffect && (
                  <span>⚡ <strong>{recentWorkout.aerobicTrainingEffect.toFixed(1)} Aero TE</strong></span>
                )}
              </div>
            </div>
          </div>

          <div className="flex items-center space-x-1 text-xs text-cyan-400 font-medium">
            <span>View Health Hub</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </div>
        </div>
      )}

      {/* Executive Daily Briefing (AI Synthesis) */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 lg:p-8 backdrop-blur-lg shadow-xl relative">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-5 border-b border-slate-800 gap-4">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-2xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center shadow-inner">
              <Sparkles className="w-5 h-5 text-cyan-400" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white tracking-tight flex items-center space-x-2">
                <span>Executive Daily Briefing</span>
                <span className="text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 font-normal">
                  Ground-Truth Synthesizer
                </span>
              </h2>
              <p className="text-xs text-slate-400">Contextual synthesis grounded in deterministic Java math</p>
            </div>
          </div>

          <div className="flex items-center space-x-2">
            <div className="bg-slate-950 p-1 rounded-xl border border-slate-800 flex items-center space-x-1 text-xs">
              <button
                onClick={() => loadBriefing('morning')}
                className={`px-3 py-1 rounded-lg font-medium transition-all flex items-center space-x-1.5 ${
                  briefingType === 'morning' ? 'bg-cyan-600 text-white' : 'text-slate-400 hover:text-white'
                }`}
              >
                <SunMedium className="w-3.5 h-3.5" />
                <span>Morning 07:30</span>
              </button>
              <button
                onClick={() => loadBriefing('evening')}
                className={`px-3 py-1 rounded-lg font-medium transition-all flex items-center space-x-1.5 ${
                  briefingType === 'evening' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white'
                }`}
              >
                <Moon className="w-3.5 h-3.5" />
                <span>Evening 21:30</span>
              </button>
            </div>

            <button
              onClick={() => loadBriefing(briefingType)}
              disabled={loadingBriefing}
              title="Refresh Briefing"
              className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition-all disabled:opacity-50"
            >
              <RefreshCw className={`w-4 h-4 ${loadingBriefing ? 'animate-spin text-cyan-400' : ''}`} />
            </button>
          </div>
        </div>

        <div className="mt-6 prose prose-invert max-w-none text-slate-300 text-sm leading-relaxed whitespace-pre-wrap font-sans bg-slate-950/40 p-6 rounded-2xl border border-slate-800/60">
          {loadingBriefing ? (
            <div className="flex items-center justify-center py-12 space-x-3 text-slate-400">
              <RefreshCw className="w-5 h-5 animate-spin text-cyan-400" />
              <span>Synthesizing live briefing with temporal context...</span>
            </div>
          ) : (
            briefing
          )}
        </div>
      </div>

      {/* Quick Access Action Hub */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <button
          onClick={() => onNavigate('health')}
          className="group bg-gradient-to-br from-slate-900 to-slate-900/60 border border-slate-800 hover:border-cyan-500/40 p-5 rounded-2xl text-left transition-all flex items-center justify-between"
        >
          <div>
            <h3 className="font-semibold text-slate-200 group-hover:text-cyan-400 transition-colors">Biometric Trajectory</h3>
            <p className="text-xs text-slate-400 mt-1">HRV corridors, Sleep staging & Weight EMA</p>
          </div>
          <ArrowRight className="w-5 h-5 text-slate-500 group-hover:text-cyan-400 group-hover:translate-x-1 transition-all" />
        </button>

        <button
          onClick={() => onNavigate('finance')}
          className="group bg-gradient-to-br from-slate-900 to-slate-900/60 border border-slate-800 hover:border-emerald-500/40 p-5 rounded-2xl text-left transition-all flex items-center justify-between"
        >
          <div>
            <h3 className="font-semibold text-slate-200 group-hover:text-emerald-400 transition-colors">Financial Ledger</h3>
            <p className="text-xs text-slate-400 mt-1">Burn rate, category donut & transaction table</p>
          </div>
          <ArrowRight className="w-5 h-5 text-slate-500 group-hover:text-emerald-400 group-hover:translate-x-1 transition-all" />
        </button>

        <button
          onClick={() => onNavigate('scan')}
          className="group bg-gradient-to-br from-slate-900 to-slate-900/60 border border-slate-800 hover:border-blue-500/40 p-5 rounded-2xl text-left transition-all flex items-center justify-between"
        >
          <div>
            <h3 className="font-semibold text-slate-200 group-hover:text-blue-400 transition-colors">Scan Receipt / Transfer</h3>
            <p className="text-xs text-slate-400 mt-1">Camera OCR with instant itemized parsing</p>
          </div>
          <ArrowRight className="w-5 h-5 text-slate-500 group-hover:text-blue-400 group-hover:translate-x-1 transition-all" />
        </button>
      </div>
    </div>
  );
};
