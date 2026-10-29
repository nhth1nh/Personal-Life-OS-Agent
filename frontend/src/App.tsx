import { useState } from 'react';
import { Header } from './components/Header';
import { DashboardView } from './components/DashboardView';
import { HealthHubView } from './components/HealthHubView';
import { FinanceHubView } from './components/FinanceHubView';
import { ReceiptScannerView } from './components/ReceiptScannerView';
import { AssistantChatView } from './components/AssistantChatView';

export function App() {
  const [currentTab, setCurrentTab] = useState<string>('dashboard');
  const [refreshKey, setRefreshKey] = useState<number>(0);

  const handleRefreshAll = () => {
    setRefreshKey((prev) => prev + 1);
  };

  return (
    <div className="min-h-screen bg-[#0b0f19] text-slate-100 flex flex-col selection:bg-cyan-500 selection:text-white">
      <Header 
        currentTab={currentTab} 
        onTabChange={setCurrentTab} 
        onRefreshAll={handleRefreshAll} 
      />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 lg:px-8 py-6">
        {currentTab === 'dashboard' && <DashboardView key={refreshKey} onNavigate={setCurrentTab} />}
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
