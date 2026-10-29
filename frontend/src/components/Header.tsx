import React, { useState } from 'react';
import { 
  Activity, 
  Wallet, 
  LayoutDashboard, 
  Camera, 
  MessageSquareCode, 
  RefreshCw, 
  KeyRound, 
  CheckCircle2,
  XCircle,
  Calendar,
  ChevronDown
} from 'lucide-react';
import { api, getApiKey, setApiKey } from '../api/client';

interface HeaderProps {
  currentTab: string;
  onTabChange: (tab: string) => void;
  onRefreshAll: () => void;
}

export const Header: React.FC<HeaderProps> = ({ currentTab, onTabChange, onRefreshAll }) => {
  const [syncing, setSyncing] = useState(false);
  const [syncMsg, setSyncMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const [showKeyModal, setShowKeyModal] = useState(false);
  const [showSyncModal, setShowSyncModal] = useState(false);
  const [keyInput, setKeyInput] = useState(getApiKey());

  // Dynamic range sync states
  const [syncMode, setSyncMode] = useState<'today' | '7days' | '30days' | 'custom'>('today');
  const [customStart, setCustomStart] = useState<string>(() => {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().split('T')[0];
  });
  const [customEnd, setCustomEnd] = useState<string>(() => new Date().toISOString().split('T')[0]);

  const handleExecuteSync = async () => {
    setShowSyncModal(false);
    try {
      setSyncing(true);
      setSyncMsg(null);
      let options: { startDate?: string; endDate?: string; days?: number } = {};
      if (syncMode === 'today') {
        options = { days: 1 };
      } else if (syncMode === '7days') {
        options = { days: 7 };
      } else if (syncMode === '30days') {
        options = { days: 30 };
      } else if (syncMode === 'custom') {
        options = { startDate: customStart, endDate: customEnd };
      }

      const res = await api.triggerGarminPull(options);
      setSyncMsg({ 
        text: res.message || `Garmin sync completed (${res.syncedDays || 1} day(s))`, 
        ok: res.status === 'success' 
      });
      onRefreshAll();
    } catch (err: any) {
      setSyncMsg({ text: err.message || 'Garmin sync failed', ok: false });
    } finally {
      setSyncing(false);
      setTimeout(() => setSyncMsg(null), 7000);
    }
  };

  const handleSaveKey = () => {
    setApiKey(keyInput.trim());
    setShowKeyModal(false);
    onRefreshAll();
  };

  const tabs = [
    { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { id: 'health', label: 'Biometrics', icon: Activity },
    { id: 'finance', label: 'Finance & Ledger', icon: Wallet },
    { id: 'scan', label: 'Receipt Scanner', icon: Camera },
    { id: 'chat', label: 'AI Partner', icon: MessageSquareCode },
  ];

  return (
    <>
      <header className="sticky top-0 z-40 bg-[#0f172a]/80 backdrop-blur-md border-b border-slate-800 px-4 lg:px-8 py-3">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-cyan-500 to-blue-600 flex items-center justify-center shadow-lg shadow-cyan-500/20">
              <span className="font-black text-lg text-white tracking-wider">P</span>
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h1 className="font-bold text-slate-100 tracking-tight text-base lg:text-lg">Personal Life OS</h1>
                <span className="text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full bg-cyan-950/80 text-cyan-400 border border-cyan-800/60">
                  Java 25 Core
                </span>
              </div>
              <p className="text-xs text-slate-400 hidden sm:block">Deterministic Bio & Finance Intelligence</p>
            </div>
          </div>

          <nav className="flex items-center bg-slate-900/90 p-1 rounded-xl border border-slate-800">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              const active = currentTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => onTabChange(tab.id)}
                  className={`flex items-center space-x-2 px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                    active
                      ? 'bg-blue-600 text-white shadow-md shadow-blue-600/30 font-semibold'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
                  }`}
                >
                  <Icon className="w-3.5 h-3.5" />
                  <span className="hidden md:inline">{tab.label}</span>
                </button>
              );
            })}
          </nav>

          <div className="flex items-center space-x-2">
            <button
              onClick={() => setShowSyncModal(true)}
              disabled={syncing}
              title="Sync Garmin Watch Biometrics (Dynamic Range)"
              className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700 text-xs text-slate-200 transition-all font-medium disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 text-cyan-400 ${syncing ? 'animate-spin' : ''}`} />
              <span className="hidden sm:inline">{syncing ? 'Syncing...' : 'Sync Garmin'}</span>
              <ChevronDown className="w-3 h-3 text-slate-400" />
            </button>

            <button
              onClick={() => setShowKeyModal(true)}
              title="Configure API Secret Key"
              className="p-1.5 rounded-lg bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700 text-slate-300 transition-all"
            >
              <KeyRound className="w-4 h-4 text-amber-400" />
            </button>
          </div>
        </div>

        {syncMsg && (
          <div className="max-w-7xl mx-auto mt-2">
            <div className={`text-xs px-3 py-1.5 rounded-lg flex items-center space-x-2 ${
              syncMsg.ok ? 'bg-emerald-950/80 border border-emerald-800 text-emerald-300' : 'bg-rose-950/80 border border-rose-800 text-rose-300'
            }`}>
              {syncMsg.ok ? <CheckCircle2 className="w-3.5 h-3.5" /> : <XCircle className="w-3.5 h-3.5" />}
              <span>{syncMsg.text}</span>
            </div>
          </div>
        )}
      </header>

      {/* Sync Range Modal */}
      {showSyncModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 max-w-md w-full shadow-2xl">
            <div className="flex items-center space-x-3 mb-4">
              <div className="w-10 h-10 rounded-xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center">
                <RefreshCw className="w-5 h-5 text-cyan-400" />
              </div>
              <div>
                <h3 className="font-semibold text-slate-100">Sync Garmin Biometrics</h3>
                <p className="text-xs text-slate-400">Trigger on-demand sync for a dynamic date window</p>
              </div>
            </div>

            <div className="space-y-2 mb-4">
              {[
                { id: 'today', label: 'Today Only (Live Biometrics)', desc: 'Real-time Body Battery, stress, steps, kcal' },
                { id: '7days', label: 'Last 7 Days', desc: 'Refresh recent sleep debt & readiness baseline' },
                { id: '30days', label: 'Last 30 Days (Backfill)', desc: 'Comprehensive rolling recovery baselines' },
                { id: 'custom', label: 'Custom Date Range', desc: 'Specify arbitrary start and end dates' },
              ].map((opt) => (
                <label
                  key={opt.id}
                  onClick={() => setSyncMode(opt.id as any)}
                  className={`flex items-start space-x-3 p-3 rounded-xl border cursor-pointer transition-all ${
                    syncMode === opt.id
                      ? 'bg-cyan-950/30 border-cyan-500/60 text-cyan-200'
                      : 'bg-slate-950/50 border-slate-800/80 text-slate-300 hover:border-slate-700'
                  }`}
                >
                  <input
                    type="radio"
                    name="syncMode"
                    checked={syncMode === opt.id}
                    onChange={() => setSyncMode(opt.id as any)}
                    className="mt-0.5 text-cyan-500 focus:ring-cyan-500"
                  />
                  <div>
                    <div className="text-xs font-semibold text-slate-100">{opt.label}</div>
                    <div className="text-[11px] text-slate-400">{opt.desc}</div>
                  </div>
                </label>
              ))}
            </div>

            {syncMode === 'custom' && (
              <div className="grid grid-cols-2 gap-3 mb-4 p-3 bg-slate-950 rounded-xl border border-slate-800">
                <div>
                  <label className="block text-[11px] font-medium text-slate-400 mb-1 flex items-center space-x-1">
                    <Calendar className="w-3 h-3 text-cyan-400" />
                    <span>Start Date</span>
                  </label>
                  <input
                    type="date"
                    value={customStart}
                    onChange={(e) => setCustomStart(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-medium text-slate-400 mb-1 flex items-center space-x-1">
                    <Calendar className="w-3 h-3 text-cyan-400" />
                    <span>End Date</span>
                  </label>
                  <input
                    type="date"
                    value={customEnd}
                    onChange={(e) => setCustomEnd(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
                  />
                </div>
              </div>
            )}

            <div className="flex justify-end space-x-2">
              <button
                onClick={() => setShowSyncModal(false)}
                className="px-4 py-1.5 rounded-lg text-xs font-medium text-slate-400 hover:text-slate-200"
              >
                Cancel
              </button>
              <button
                onClick={handleExecuteSync}
                className="px-4 py-1.5 rounded-lg text-xs font-medium bg-cyan-600 hover:bg-cyan-500 text-white shadow-md shadow-cyan-600/30 font-semibold"
              >
                Start Sync
              </button>
            </div>
          </div>
        </div>
      )}

      {/* API Key Modal */}
      {showKeyModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 max-w-md w-full shadow-2xl">
            <div className="flex items-center space-x-3 mb-4">
              <div className="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center">
                <KeyRound className="w-5 h-5 text-amber-400" />
              </div>
              <div>
                <h3 className="font-semibold text-slate-100">API Secret Key</h3>
                <p className="text-xs text-slate-400">Sent via X-API-Key to authorize backend requests</p>
              </div>
            </div>

            <input
              type="password"
              value={keyInput}
              onChange={(e) => setKeyInput(e.target.value)}
              placeholder="Enter API Secret Key"
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-cyan-500 mb-4"
            />

            <div className="flex justify-end space-x-2">
              <button
                onClick={() => setShowKeyModal(false)}
                className="px-4 py-1.5 rounded-lg text-xs font-medium text-slate-400 hover:text-slate-200"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveKey}
                className="px-4 py-1.5 rounded-lg text-xs font-medium bg-blue-600 hover:bg-blue-500 text-white shadow-md shadow-blue-600/30"
              >
                Save Key
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
