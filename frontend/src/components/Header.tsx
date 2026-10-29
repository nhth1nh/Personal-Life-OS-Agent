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
  XCircle
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
  const [keyInput, setKeyInput] = useState(getApiKey());

  const handleGarminSync = async () => {
    try {
      setSyncing(true);
      setSyncMsg(null);
      const res = await api.triggerGarminPull();
      setSyncMsg({ text: res.message || 'Garmin sync completed', ok: true });
      onRefreshAll();
    } catch (err: any) {
      setSyncMsg({ text: err.message || 'Garmin sync failed', ok: false });
    } finally {
      setSyncing(false);
      setTimeout(() => setSyncMsg(null), 5000);
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
              onClick={handleGarminSync}
              disabled={syncing}
              title="Trigger on-demand Garmin Watch Sync"
              className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700 text-xs text-slate-200 transition-all font-medium disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 text-cyan-400 ${syncing ? 'animate-spin' : ''}`} />
              <span className="hidden sm:inline">{syncing ? 'Syncing...' : 'Garmin Sync'}</span>
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
