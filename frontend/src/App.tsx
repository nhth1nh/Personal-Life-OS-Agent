import { useState, useEffect } from 'react';
import { Header } from './components/Header';
import { DashboardView } from './components/DashboardView';
import { HealthHubView } from './components/HealthHubView';
import { FinanceHubView } from './components/FinanceHubView';
import { ReceiptScannerView } from './components/ReceiptScannerView';
import { AssistantChatView } from './components/AssistantChatView';

const VALID_TABS = ['dashboard', 'health', 'finance', 'scan', 'chat'] as const;
type TabType = typeof VALID_TABS[number];

function getInitialTab(): string {
  // 1. Check URL hash (e.g. #health, #finance, #chat, #scan)
  if (typeof window !== 'undefined') {
    const hash = window.location.hash.replace('#', '').toLowerCase();
    if (VALID_TABS.includes(hash as TabType)) {
      return hash;
    }
    // 2. Check localStorage
    const saved = localStorage.getItem('ploa_active_tab')?.toLowerCase();
    if (saved && VALID_TABS.includes(saved as TabType)) {
      return saved;
    }
  }
  return 'dashboard';
}

export function App() {
  const [currentTab, setCurrentTab] = useState<string>(getInitialTab);
  const [refreshKey, setRefreshKey] = useState<number>(0);

  const handleTabChange = (tab: string) => {
    const target = tab.toLowerCase();
    if (VALID_TABS.includes(target as TabType)) {
      setCurrentTab(target);
      localStorage.setItem('ploa_active_tab', target);
      if (window.location.hash.replace('#', '') !== target) {
        window.history.pushState(null, '', `#${target}`);
      }
    }
  };

  // Sync initial tab into URL hash if not present, and listen to browser back/forward buttons
  useEffect(() => {
    const currentHash = window.location.hash.replace('#', '').toLowerCase();
    if (!currentHash || !VALID_TABS.includes(currentHash as TabType)) {
      window.history.replaceState(null, '', `#${currentTab}`);
    }

    const onHashChange = () => {
      const hash = window.location.hash.replace('#', '').toLowerCase();
      if (VALID_TABS.includes(hash as TabType)) {
        setCurrentTab(hash);
        localStorage.setItem('ploa_active_tab', hash);
      }
    };

    window.addEventListener('hashchange', onHashChange);
    window.addEventListener('popstate', onHashChange);

    return () => {
      window.removeEventListener('hashchange', onHashChange);
      window.removeEventListener('popstate', onHashChange);
    };
  }, [currentTab]);

  const handleRefreshAll = () => {
    setRefreshKey((prev) => prev + 1);
  };

  return (
    <div className="min-h-screen bg-[#0b0f19] text-slate-100 flex flex-col selection:bg-cyan-500 selection:text-white">
      <Header 
        currentTab={currentTab} 
        onTabChange={handleTabChange} 
        onRefreshAll={handleRefreshAll} 
      />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 lg:px-8 py-6">
        {currentTab === 'dashboard' && <DashboardView key={refreshKey} onNavigate={handleTabChange} />}
        {currentTab === 'health' && <HealthHubView key={refreshKey} />}
        {currentTab === 'finance' && <FinanceHubView key={refreshKey} />}
        {currentTab === 'scan' && <ReceiptScannerView key={refreshKey} />}
        {currentTab === 'chat' && <AssistantChatView key={refreshKey} />}
      </main>

      <footer className="border-t border-slate-800/80 py-4 px-4 text-center text-xs text-slate-500 bg-[#0f172a]/50">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2">
          <span>Personal Life OS • Private, Self-Hosted Recovery & Financial Intelligence</span>
          <span className="font-mono text-[10px] text-slate-600">Deterministic Math Core (Java 25) • Multimodal Gemini Perception</span>
        </div>
      </footer>
    </div>
  );
}

export default App;
