import React, { useEffect, useState } from 'react';
import { 
  Heart, 
  Moon, 
  Scale, 
  Flame, 
  RefreshCw,
  AlertCircle
} from 'lucide-react';
import { 
  ResponsiveContainer, 
  ComposedChart, 
  Line, 
  BarChart, 
  Bar, 
  XAxis, 
  YAxis, 
  Tooltip, 
  CartesianGrid, 
  Legend, 
  ReferenceLine 
} from 'recharts';
import { api } from '../api/client';
import type { HealthTimeseriesResponse } from '../types';

export const HealthHubView: React.FC = () => {
  const [data, setData] = useState<HealthTimeseriesResponse | null>(null);
  const [days, setDays] = useState<number>(30);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadTimeseries(days);
  }, [days]);

  const loadTimeseries = async (windowDays: number) => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.getHealthTimeseries(windowDays);
      setData(res);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch biometric timeseries');
    } finally {
      setLoading(false);
    }
  };

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length === 3) return `${parts[1]}/${parts[2]}`;
    return dateStr;
  };

  return (
    <div className="space-y-6">
      {/* Top Controls: Window Selector & Summary */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between bg-slate-900/60 border border-slate-800 rounded-2xl p-4 gap-3">
        <div className="flex items-center space-x-3">
          <div className="w-9 h-9 rounded-xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center">
            <Heart className="w-5 h-5 text-rose-400" />
          </div>
          <div>
            <h2 className="font-bold text-white text-base">Biometric Trajectory & Recovery</h2>
            <p className="text-xs text-slate-400">Deterministic rolling baseline math ($\mu \pm 1\sigma$)</p>
          </div>
        </div>

        <div className="flex items-center space-x-2">
          <div className="bg-slate-950 p-1 rounded-xl border border-slate-800 flex items-center space-x-1 text-xs">
            {[7, 14, 30, 60, 90].map((d) => (
              <button
                key={d}
                onClick={() => setDays(d)}
                className={`px-3 py-1 rounded-lg font-medium transition-all ${
                  days === d ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-white'
                }`}
              >
                {d}d
              </button>
            ))}
          </div>

          <button
            onClick={() => loadTimeseries(days)}
            disabled={loading}
            className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition-all disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-cyan-400' : ''}`} />
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-rose-950/40 border border-rose-800 text-rose-300 text-xs flex items-center space-x-2">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Chart 1: HRV Recovery Corridor */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-4 border-b border-slate-800 gap-2">
          <div>
            <h3 className="font-semibold text-white text-sm flex items-center space-x-2">
              <span className="w-2.5 h-2.5 rounded-full bg-rose-400"></span>
              <span>HRV (rMSSD) Recovery Corridor</span>
            </h3>
            <p className="text-xs text-slate-400 mt-0.5">
              Overnight HRV vs. 30-day baseline mean ({data?.baselineHrvMean ?? '--'} ms) and $\pm 1\sigma$ corridor
            </p>
          </div>
          <div className="flex items-center space-x-3 text-xs text-slate-400">
            <span className="flex items-center space-x-1"><span className="w-2 h-2 rounded-full bg-rose-400"></span><span>Daily HRV</span></span>
            <span className="flex items-center space-x-1"><span className="w-2 h-0.5 bg-cyan-400"></span><span>Baseline Mean</span></span>
            <span className="flex items-center space-x-1"><span className="w-2 h-0.5 bg-emerald-400 border border-dashed"></span><span>$\pm 1\sigma$ Corridor</span></span>
          </div>
        </div>

        <div className="h-72 mt-4">
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={data?.points || []}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
              <XAxis dataKey="date" tickFormatter={formatDate} stroke="#64748b" fontSize={11} />
              <YAxis domain={['auto', 'auto']} stroke="#64748b" fontSize={11} unit="ms" />
              <Tooltip 
                contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '0.75rem', fontSize: '12px' }}
                formatter={(val: any) => [val != null ? `${val} ms` : '--']}
                labelFormatter={(label) => `Date: ${label}`}
              />
              {data?.baselineHrvMean && (
                <ReferenceLine y={data.baselineHrvMean} stroke="#22d3ee" strokeWidth={1.5} label={{ value: 'Mean', fill: '#22d3ee', fontSize: 10, position: 'right' }} />
              )}
              {data?.points?.[0]?.hrvUpperThreshold && (
                <ReferenceLine y={data.points[0].hrvUpperThreshold} stroke="#34d399" strokeDasharray="4 4" strokeWidth={1} />
              )}
              {data?.points?.[0]?.hrvLowerThreshold && (
                <ReferenceLine y={data.points[0].hrvLowerThreshold} stroke="#fb7185" strokeDasharray="4 4" strokeWidth={1} />
              )}
              <Line type="monotone" dataKey="hrvRmssd" name="HRV (rMSSD)" stroke="#f43f5e" strokeWidth={2.5} dot={{ r: 3, fill: '#f43f5e' }} activeDot={{ r: 6 }} />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Grid: 2 Charts - Resting Heart Rate & Sleep Architecture */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Chart 2: Resting Heart Rate Trend */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
          <div className="flex items-center justify-between pb-4 border-b border-slate-800">
            <div>
              <h3 className="font-semibold text-white text-sm flex items-center space-x-2">
                <span className="w-2.5 h-2.5 rounded-full bg-cyan-400"></span>
                <span>Resting Heart Rate (RHR) Trend</span>
              </h3>
              <p className="text-xs text-slate-400 mt-0.5">Baseline: {data?.baselineRhrMean ?? '--'} bpm (lower indicates parasympathetic recovery)</p>
            </div>
          </div>
          <div className="h-64 mt-4">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={data?.points || []}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                <XAxis dataKey="date" tickFormatter={formatDate} stroke="#64748b" fontSize={11} />
                <YAxis domain={['dataMin - 3', 'dataMax + 3']} stroke="#64748b" fontSize={11} unit="bpm" />
                <Tooltip 
                  contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '0.75rem', fontSize: '12px' }}
                />
                {data?.baselineRhrMean && (
                  <ReferenceLine y={data.baselineRhrMean} stroke="#94a3b8" strokeDasharray="3 3" label={{ value: 'RHR Baseline', fill: '#94a3b8', fontSize: 10 }} />
                )}
                <Line type="monotone" dataKey="rhr" name="Resting HR" stroke="#06b6d4" strokeWidth={2} dot={{ r: 2.5, fill: '#06b6d4' }} />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Chart 3: Sleep Architecture (Stacked Stages) */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
          <div className="flex items-center justify-between pb-4 border-b border-slate-800">
            <div>
              <h3 className="font-semibold text-white text-sm flex items-center space-x-2">
                <Moon className="w-4 h-4 text-indigo-400" />
                <span>Sleep Architecture (Stages & Debt)</span>
              </h3>
              <p className="text-xs text-slate-400 mt-0.5">Target: {data?.sleepTargetHours ?? 7.5} hours/night</p>
            </div>
          </div>
          <div className="h-64 mt-4">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data?.points || []}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                <XAxis dataKey="date" tickFormatter={formatDate} stroke="#64748b" fontSize={11} />
                <YAxis stroke="#64748b" fontSize={11} unit="h" />
                <Tooltip 
                  contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '0.75rem', fontSize: '12px' }}
                  formatter={(val: any) => [val != null ? `${val}h` : '--']}
                />
                <Legend wrapperStyle={{ fontSize: '11px', paddingTop: '8px' }} />
                {data?.sleepTargetHours && (
                  <ReferenceLine y={data.sleepTargetHours} stroke="#38bdf8" strokeDasharray="3 3" />
                )}
                <Bar dataKey="deepSleepHours" name="Deep Sleep" stackId="a" fill="#4f46e5" />
                <Bar dataKey="remSleepHours" name="REM Sleep" stackId="a" fill="#818cf8" />
                <Bar dataKey="lightSleepHours" name="Light Sleep" stackId="a" fill="#38bdf8" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Grid: Chart 4 (Weight EMA) & Chart 5 (Body Battery / Activity) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Chart 4: Weight 7-day EMA Trajectory */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
          <div className="flex items-center justify-between pb-4 border-b border-slate-800">
            <div>
              <h3 className="font-semibold text-white text-sm flex items-center space-x-2">
                <Scale className="w-4 h-4 text-amber-400" />
                <span>Weight 7-day EMA Trajectory</span>
              </h3>
              <p className="text-xs text-slate-400 mt-0.5">Filters water/glycogen noise towards goal ({data?.targetWeightKg ?? '--'} kg)</p>
            </div>
          </div>
          <div className="h-64 mt-4">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={data?.points || []}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                <XAxis dataKey="date" tickFormatter={formatDate} stroke="#64748b" fontSize={11} />
                <YAxis domain={['dataMin - 1', 'dataMax + 1']} stroke="#64748b" fontSize={11} unit="kg" />
                <Tooltip 
                  contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '0.75rem', fontSize: '12px' }}
                />
                {data?.targetWeightKg && (
                  <ReferenceLine y={data.targetWeightKg} stroke="#10b981" strokeDasharray="3 3" label={{ value: 'Target', fill: '#10b981', fontSize: 10 }} />
                )}
                <Line type="monotone" dataKey="weightKg" name="Raw Weight" stroke="#64748b" strokeWidth={1} dot={{ r: 2 }} />
                <Line type="monotone" dataKey="weightEmaKg" name="7d EMA" stroke="#f59e0b" strokeWidth={2.5} dot={false} />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Chart 5: Daily Activity & Energy Burn */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
          <div className="flex items-center justify-between pb-4 border-b border-slate-800">
            <div>
              <h3 className="font-semibold text-white text-sm flex items-center space-x-2">
                <Flame className="w-4 h-4 text-orange-400" />
                <span>Daily Activity & Energy</span>
              </h3>
              <p className="text-xs text-slate-400 mt-0.5">Steps & Active Caloric Burn</p>
            </div>
          </div>
          <div className="h-64 mt-4">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data?.points || []}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                <XAxis dataKey="date" tickFormatter={formatDate} stroke="#64748b" fontSize={11} />
                <YAxis yAxisId="steps" stroke="#64748b" fontSize={11} />
                <YAxis yAxisId="cal" orientation="right" stroke="#64748b" fontSize={11} unit="kcal" />
                <Tooltip 
                  contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '0.75rem', fontSize: '12px' }}
                />
                <Legend wrapperStyle={{ fontSize: '11px', paddingTop: '8px' }} />
                <Bar yAxisId="steps" dataKey="steps" name="Steps" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                <Bar yAxisId="cal" dataKey="activeKcal" name="Active Kcal" fill="#f97316" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
    </div>
  );
};
