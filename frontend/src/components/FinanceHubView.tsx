import React, { useEffect, useState } from 'react';
import { 
  Wallet, 
  PieChart as PieIcon, 
  BarChart3, 
  Search, 
  Trash2, 
  CheckCircle, 
  RefreshCw,
  AlertCircle,
  TrendingUp,
  ShieldAlert,
  ArrowDownRight,
  ArrowUpRight,
  Plus,
  CreditCard,
  DollarSign,
  Sparkles,
  Zap,
  Layers,
  X
} from 'lucide-react';
import { 
  ResponsiveContainer, 
  PieChart, 
  Pie, 
  Cell, 
  BarChart, 
  Bar, 
  XAxis, 
  YAxis, 
  Tooltip, 
  CartesianGrid 
} from 'recharts';
import { api } from '../api/client';
import type { 
  FinanceAnalyticsResponse, 
  Transaction, 
  CashFlowSummary, 
  Debt, 
  DebtSchedule, 
  Income, 
  PayoffSimulationComparison 
} from '../types';

const CATEGORY_COLORS: { [key: string]: string } = {
  'Housing': '#3b82f6',
  'Food & Dining': '#f97316',
  'Groceries': '#10b981',
  'Transportation': '#a855f7',
  'Utilities': '#06b6d4',
  'Healthcare': '#ec4899',
  'Entertainment': '#eab308',
  'Shopping': '#6366f1',
  'Debt Principal': '#64748b',
  'Interest & Bank Fees': '#ef4444',
  'Other': '#94a3b8'
};

const DEFAULT_PALETTE = ['#3b82f6', '#f97316', '#10b981', '#a855f7', '#06b6d4', '#ec4899', '#eab308', '#6366f1'];

export const FinanceHubView: React.FC = () => {
  const currentMonthStr = new Date().toISOString().slice(0, 7);
  const [selectedMonth, setSelectedMonth] = useState<string>(currentMonthStr);
  const [activeTab, setActiveTab] = useState<'waterfall' | 'debts' | 'incomes' | 'analytics'>('waterfall');
  
  // Data states
  const [cashFlow, setCashFlow] = useState<CashFlowSummary | null>(null);
  const [debts, setDebts] = useState<Debt[]>([]);
  const [incomes, setIncomes] = useState<Income[]>([]);
  const [analytics, setAnalytics] = useState<FinanceAnalyticsResponse | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [simulation, setSimulation] = useState<PayoffSimulationComparison | null>(null);
  const [surplusVnd, setSurplusVnd] = useState<number>(2000000);
  
  // Schedule viewer modal
  const [selectedDebtForSchedule, setSelectedDebtForSchedule] = useState<Debt | null>(null);
  const [scheduleItems, setScheduleItems] = useState<DebtSchedule[]>([]);
  const [loadingSchedule, setLoadingSchedule] = useState<boolean>(false);

  // Modals
  const [showExpenseModal, setShowExpenseModal] = useState<boolean>(false);
  const [showIncomeModal, setShowIncomeModal] = useState<boolean>(false);
  const [showDebtModal, setShowDebtModal] = useState<boolean>(false);
  const [showPaymentModal, setShowPaymentModal] = useState<boolean>(false);
  const [paymentDebt, setPaymentDebt] = useState<Debt | null>(null);

  // Form states
  const [expenseForm, setExpenseForm] = useState({ amount: '', merchant: '', category: 'Food & Dining', spendType: 'DISCRETIONARY', note: '' });
  const [incomeForm, setIncomeForm] = useState({ source: '', expectedAmount: '', expectedDate: new Date().toISOString().slice(0, 10), incomeType: 'SALARY', confidenceFactor: 1.0 });
  const [debtForm, setDebtForm] = useState({ name: '', creditor: '', debtType: 'AMORTIZING_LOAN', originalPrincipal: '', currentPrincipal: '', interestRateApr: '', monthlyMinimumDue: '', dueDayOfMonth: '10' });
  const [paymentForm, setPaymentForm] = useState({ totalPaid: '', principalPortion: '', interestPortion: '', note: '' });

  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState<string>('');
  const [filterType, setFilterType] = useState<string>('ALL');

  useEffect(() => {
    loadAllData();
  }, [selectedMonth]);

  const loadAllData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [cf, dList, inList, an, txs] = await Promise.all([
        api.getCashFlow().catch(() => null),
        api.getDebts().catch(() => []),
        api.getIncomes().catch(() => []),
        api.getFinanceAnalytics(selectedMonth).catch(() => null),
        api.getRecentTransactions(100).catch(() => [])
      ]);
      setCashFlow(cf);
      setDebts(dList);
      setIncomes(inList);
      setAnalytics(an);
      setTransactions(txs);

      if (dList.length > 0) {
        api.simulateDebtPayoff(surplusVnd).then(setSimulation).catch(() => null);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load financial data');
    } finally {
      setLoading(false);
    }
  };

  const handleSimulate = async (surplus: number) => {
    setSurplusVnd(surplus);
    try {
      const res = await api.simulateDebtPayoff(surplus);
      setSimulation(res);
    } catch (err) {
      console.error(err);
    }
  };

  const handleViewSchedule = async (debt: Debt) => {
    setSelectedDebtForSchedule(debt);
    setLoadingSchedule(true);
    try {
      const items = await api.getDebtSchedule(debt.id);
      setScheduleItems(items);
    } catch (err: any) {
      alert(`Failed to load schedule: ${err.message}`);
    } finally {
      setLoadingSchedule(false);
    }
  };

  const handleCreateExpense = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.createDirectTransaction({
        amount: Number(expenseForm.amount),
        merchant: expenseForm.merchant,
        category: expenseForm.category,
        spendType: expenseForm.spendType as any,
        note: expenseForm.note
      });
      setShowExpenseModal(false);
      setExpenseForm({ amount: '', merchant: '', category: 'Food & Dining', spendType: 'DISCRETIONARY', note: '' });
      loadAllData();
    } catch (err: any) {
      alert(`Failed to save expense: ${err.message}`);
    }
  };

  const handleCreateIncome = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.createIncome({
        source: incomeForm.source,
        expectedAmount: Number(incomeForm.expectedAmount),
        expectedDate: incomeForm.expectedDate,
        incomeType: incomeForm.incomeType,
        confidenceFactor: Number(incomeForm.confidenceFactor)
      });
      setShowIncomeModal(false);
      setIncomeForm({ source: '', expectedAmount: '', expectedDate: new Date().toISOString().slice(0, 10), incomeType: 'SALARY', confidenceFactor: 1.0 });
      loadAllData();
    } catch (err: any) {
      alert(`Failed to save income: ${err.message}`);
    }
  };

  const handleCreateDebt = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.createDebt({
        name: debtForm.name,
        creditor: debtForm.creditor,
        debtType: debtForm.debtType,
        originalPrincipal: Number(debtForm.originalPrincipal),
        currentPrincipal: debtForm.currentPrincipal ? Number(debtForm.currentPrincipal) : Number(debtForm.originalPrincipal),
        interestRateApr: Number(debtForm.interestRateApr),
        monthlyMinimumDue: debtForm.monthlyMinimumDue ? Number(debtForm.monthlyMinimumDue) : undefined,
        dueDayOfMonth: Number(debtForm.dueDayOfMonth)
      });
      setShowDebtModal(false);
      setDebtForm({ name: '', creditor: '', debtType: 'AMORTIZING_LOAN', originalPrincipal: '', currentPrincipal: '', interestRateApr: '', monthlyMinimumDue: '', dueDayOfMonth: '10' });
      loadAllData();
    } catch (err: any) {
      alert(`Failed to create debt: ${err.message}`);
    }
  };

  const handleRecordPayment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!paymentDebt) return;
    try {
      await api.recordDebtPayment(paymentDebt.id, {
        totalPaid: Number(paymentForm.totalPaid),
        principalPortion: paymentForm.principalPortion ? Number(paymentForm.principalPortion) : undefined,
        interestPortion: paymentForm.interestPortion ? Number(paymentForm.interestPortion) : undefined,
        note: paymentForm.note
      });
      setShowPaymentModal(false);
      setPaymentForm({ totalPaid: '', principalPortion: '', interestPortion: '', note: '' });
      setPaymentDebt(null);
      loadAllData();
    } catch (err: any) {
      alert(`Failed to record payment: ${err.message}`);
    }
  };

  const handleMarkIncomeReceived = async (id: string, amount: number) => {
    try {
      await api.markIncomeReceived(id, { actualAmount: amount });
      loadAllData();
    } catch (err: any) {
      alert(`Failed to mark received: ${err.message}`);
    }
  };

  const handleDeleteTransaction = async (id: string) => {
    if (!window.confirm('Delete this transaction?')) return;
    try {
      await api.deleteTransaction(id);
      loadAllData();
    } catch (err: any) {
      alert(`Failed to delete transaction: ${err.message}`);
    }
  };

  const formatVnd = (amount?: number) => {
    if (amount == null) return '-- VND';
    return new Intl.NumberFormat('vi-VN').format(Math.round(amount)) + ' ₫';
  };

  const filteredTransactions = transactions.filter((t) => {
    const matchesSearch = 
      t.merchant?.toLowerCase().includes(search.toLowerCase()) ||
      t.category?.toLowerCase().includes(search.toLowerCase()) ||
      t.invoiceNumber?.toLowerCase().includes(search.toLowerCase());
    
    if (filterType === 'FIXED') return matchesSearch && t.spendType === 'FIXED';
    if (filterType === 'DISCRETIONARY') return matchesSearch && t.spendType === 'DISCRETIONARY';
    if (filterType === 'PENDING') return matchesSearch && !t.confirmed;
    return matchesSearch;
  });

  return (
    <div className="space-y-6">
      {/* Top Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between bg-slate-900/60 border border-slate-800 rounded-2xl p-4 gap-4">
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center">
            <Wallet className="w-5 h-5 text-emerald-400" />
          </div>
          <div>
            <h2 className="font-bold text-white text-base">Personal Finance Decision Hub</h2>
            <p className="text-xs text-slate-400">Deterministic cash flow, debt acceleration & real-world liquidity</p>
          </div>
        </div>

        {/* Action Buttons & Month Picker */}
        <div className="flex items-center flex-wrap gap-2">
          <input
            type="month"
            value={selectedMonth}
            onChange={(e) => setSelectedMonth(e.target.value)}
            className="bg-slate-950 border border-slate-800 rounded-xl px-2.5 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-cyan-500"
          />

          <button
            onClick={() => setShowExpenseModal(true)}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 text-rose-300 text-xs font-semibold transition-all"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Expense</span>
          </button>

          <button
            onClick={() => setShowIncomeModal(true)}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-emerald-500/10 hover:bg-emerald-500/20 border border-emerald-500/30 text-emerald-300 text-xs font-semibold transition-all"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Income</span>
          </button>

          <button
            onClick={() => setShowDebtModal(true)}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 text-amber-300 text-xs font-semibold transition-all"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Debt</span>
          </button>

          <button
            onClick={loadAllData}
            disabled={loading}
            className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition-all disabled:opacity-50"
            title="Refresh"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-cyan-400' : ''}`} />
          </button>
        </div>
      </div>

      {/* Navigation Sub-Tabs */}
      <div className="flex items-center space-x-1 border-b border-slate-800 pb-2">
        <button
          onClick={() => setActiveTab('waterfall')}
          className={`flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-medium transition-all ${
            activeTab === 'waterfall'
              ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/30 shadow-sm'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
          }`}
        >
          <Layers className="w-3.5 h-3.5" />
          <span>Cash-Flow Waterfall</span>
        </button>

        <button
          onClick={() => setActiveTab('debts')}
          className={`flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-medium transition-all ${
            activeTab === 'debts'
              ? 'bg-amber-500/10 text-amber-400 border border-amber-500/30 shadow-sm'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
          }`}
        >
          <CreditCard className="w-3.5 h-3.5" />
          <span>Debt Portfolio & Payoff ({debts.length})</span>
        </button>

        <button
          onClick={() => setActiveTab('incomes')}
          className={`flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-medium transition-all ${
            activeTab === 'incomes'
              ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 shadow-sm'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
          }`}
        >
          <DollarSign className="w-3.5 h-3.5" />
          <span>Incomes & Inflows ({incomes.length})</span>
        </button>

        <button
          onClick={() => setActiveTab('analytics')}
          className={`flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-medium transition-all ${
            activeTab === 'analytics'
              ? 'bg-purple-500/10 text-purple-400 border border-purple-500/30 shadow-sm'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
          }`}
        >
          <BarChart3 className="w-3.5 h-3.5" />
          <span>Spend Ledger & Breakdown</span>
        </button>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-rose-950/40 border border-rose-800 text-rose-300 text-xs flex items-center space-x-2">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* ── TAB 1: CASH-FLOW WATERFALL ─────────────────────────────────── */}
      {activeTab === 'waterfall' && (
        <div className="space-y-6">
          {/* Liquidity Alert Banner */}
          {cashFlow && (
            <div className={`p-4 rounded-2xl border flex items-start space-x-3 ${
              cashFlow.liquidityStatus === 'LIQUIDITY_DEFICIT'
                ? 'bg-rose-950/40 border-rose-800 text-rose-200'
                : cashFlow.liquidityStatus === 'PROJECTED_SHORTFALL'
                ? 'bg-amber-950/40 border-amber-800 text-amber-200'
                : 'bg-emerald-950/30 border-emerald-800/60 text-emerald-200'
            }`}>
              {cashFlow.liquidityStatus === 'LIQUIDITY_DEFICIT' && <ShieldAlert className="w-5 h-5 text-rose-400 flex-shrink-0 mt-0.5" />}
              {cashFlow.liquidityStatus === 'PROJECTED_SHORTFALL' && <AlertCircle className="w-5 h-5 text-amber-400 flex-shrink-0 mt-0.5" />}
              {cashFlow.liquidityStatus === 'SOLVENT' && <CheckCircle className="w-5 h-5 text-emerald-400 flex-shrink-0 mt-0.5" />}
              <div>
                <div className="font-semibold text-sm flex items-center space-x-2">
                  <span>{cashFlow.liquidityStatus.replace('_', ' ')}</span>
                  <span className="text-xs px-2 py-0.5 rounded-full bg-slate-900/60 border border-slate-700 text-slate-300 font-normal">
                    {cashFlow.daysRemainingInMonth} days remaining in month
                  </span>
                </div>
                <p className="text-xs mt-1 text-slate-300">{cashFlow.recommendation}</p>
              </div>
            </div>
          )}

          {/* Cash Flow Waterfall Grid */}
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
            <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4">
              <span className="text-[11px] font-semibold text-cyan-400 uppercase tracking-wider flex items-center space-x-1">
                <span>Liquid Cash Now</span>
              </span>
              <div className="text-base font-bold text-slate-100 mt-1">{formatVnd(cashFlow?.liquidCashNow)}</div>
              <span className="text-[10px] text-slate-500">Bank accounts + cash</span>
            </div>

            <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4">
              <span className="text-[11px] font-semibold text-emerald-400 uppercase tracking-wider flex items-center space-x-1">
                <ArrowUpRight className="w-3.5 h-3.5" />
                <span>Expected Inflows</span>
              </span>
              <div className="text-base font-bold text-emerald-400 mt-1">+{formatVnd(cashFlow?.expectedInflowsBeforeMonthEnd)}</div>
              <span className="text-[10px] text-slate-500">Risk-weighted before month-end</span>
            </div>

            <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4">
              <span className="text-[11px] font-semibold text-rose-400 uppercase tracking-wider flex items-center space-x-1">
                <ArrowDownRight className="w-3.5 h-3.5" />
                <span>Unpaid Debt Due</span>
              </span>
              <div className="text-base font-bold text-rose-400 mt-1">-{formatVnd(cashFlow?.unpaidDebtObligationsBeforeMonthEnd)}</div>
              <span className="text-[10px] text-slate-500">Committed scheduled payments</span>
            </div>

            <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4">
              <span className="text-[11px] font-semibold text-amber-400 uppercase tracking-wider flex items-center space-x-1">
                <ArrowDownRight className="w-3.5 h-3.5" />
                <span>Unpaid Fixed Bills</span>
              </span>
              <div className="text-base font-bold text-amber-400 mt-1">-{formatVnd(cashFlow?.unpaidFixedBillsBeforeMonthEnd)}</div>
              <span className="text-[10px] text-slate-500">Reserved monthly costs</span>
            </div>

            <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4">
              <span className="text-[11px] font-semibold text-indigo-400 uppercase tracking-wider">Emergency Reserve</span>
              <div className="text-base font-bold text-indigo-300 mt-1">-{formatVnd(cashFlow?.emergencyReserveFloor)}</div>
              <span className="text-[10px] text-slate-500">Protected capital floor</span>
            </div>

            <div className="bg-gradient-to-br from-cyan-950/40 to-slate-900 border border-cyan-500/40 rounded-2xl p-4 shadow-lg shadow-cyan-950/20">
              <span className="text-[11px] font-bold text-cyan-300 uppercase tracking-wider">Spendable Cash</span>
              <div className={`text-base font-black mt-1 ${
                (cashFlow?.spendableCash ?? 0) > 0 ? 'text-cyan-300' : 'text-rose-400'
              }`}>
                {formatVnd(cashFlow?.spendableCash)}
              </div>
              <span className="text-[10px] text-cyan-400/80">Available right now</span>
            </div>
          </div>

          {/* Safe Daily Allowance & Net Invariants */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 flex items-center space-x-4">
              <div className="w-12 h-12 rounded-2xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center flex-shrink-0">
                <Zap className="w-6 h-6 text-cyan-400" />
              </div>
              <div>
                <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Safe Daily Allowance</span>
                <div className="text-2xl font-black text-cyan-300 mt-0.5">{formatVnd(cashFlow?.safeDailyAllowance)} <span className="text-xs font-normal text-slate-400">/ day</span></div>
                <p className="text-[11px] text-slate-500 mt-1">Derived from spendable cash ÷ {cashFlow?.daysRemainingInMonth ?? '--'} days</p>
              </div>
            </div>

            <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 flex items-center space-x-4">
              <div className="w-12 h-12 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center flex-shrink-0">
                <TrendingUp className="w-6 h-6 text-emerald-400" />
              </div>
              <div>
                <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Net Cash Flow (MTD)</span>
                <div className={`text-2xl font-black mt-0.5 ${
                  (cashFlow?.netCashFlowMtd ?? 0) >= 0 ? 'text-emerald-400' : 'text-rose-400'
                }`}>
                  {formatVnd(cashFlow?.netCashFlowMtd)}
                </div>
                <p className="text-[11px] text-slate-500 mt-1">Cash Inflows - Operating Spend - Principal</p>
              </div>
            </div>

            <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 flex items-center space-x-4">
              <div className="w-12 h-12 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center flex-shrink-0">
                <Sparkles className="w-6 h-6 text-indigo-400" />
              </div>
              <div>
                <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Net Savings (Wealth Created)</span>
                <div className={`text-2xl font-black mt-0.5 ${
                  (cashFlow?.netSavingsMtd ?? 0) >= 0 ? 'text-indigo-300' : 'text-rose-400'
                }`}>
                  {formatVnd(cashFlow?.netSavingsMtd)}
                </div>
                <p className="text-[11px] text-slate-500 mt-1">Income - Operating Expenses (Principal preserved)</p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── TAB 2: DEBT COMMAND CENTER & SIMULATOR ────────────────────────── */}
      {activeTab === 'debts' && (
        <div className="space-y-6">
          {/* Active Debt Cards Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {debts.map((d) => {
              const progressPct = d.originalPrincipal > 0
                ? Math.min(100, Math.round(((d.originalPrincipal - d.currentPrincipal) / d.originalPrincipal) * 100))
                : 0;

              return (
                <div key={d.id} className="bg-slate-900/80 border border-slate-800 rounded-3xl p-5 hover:border-slate-700 transition-all">
                  <div className="flex items-start justify-between">
                    <div>
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-slate-800 text-amber-400 border border-amber-500/30 uppercase">
                        {d.debtType.replace('_', ' ')}
                      </span>
                      <h3 className="font-bold text-white text-base mt-2">{d.name}</h3>
                      <p className="text-xs text-slate-400">{d.creditor}</p>
                    </div>

                    <div className="text-right">
                      <span className="text-xs font-semibold text-slate-400">APR</span>
                      <div className="text-sm font-bold text-amber-400">
                        {d.interestRateApr > 1 ? d.interestRateApr : Math.round(d.interestRateApr * 1000) / 10}%
                      </div>
                    </div>
                  </div>

                  {/* Progress Bar */}
                  <div className="mt-4">
                    <div className="flex items-center justify-between text-xs text-slate-400 mb-1">
                      <span>Principal Paid: {progressPct}%</span>
                      <span className="font-semibold text-slate-200">{formatVnd(d.currentPrincipal)} left</span>
                    </div>
                    <div className="w-full bg-slate-800 rounded-full h-2 overflow-hidden">
                      <div 
                        className="bg-emerald-500 h-2 rounded-full transition-all duration-500" 
                        style={{ width: `${progressPct}%` }}
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-2 mt-4 pt-3 border-t border-slate-800/80 text-xs">
                    <div>
                      <span className="text-[10px] text-slate-500 uppercase">Min Monthly Due</span>
                      <div className="font-semibold text-slate-200">{formatVnd(d.monthlyMinimumDue)}</div>
                    </div>
                    <div>
                      <span className="text-[10px] text-slate-500 uppercase">Due Day</span>
                      <div className="font-semibold text-slate-200">Day {d.dueDayOfMonth} of month</div>
                    </div>
                  </div>

                  {/* Action Buttons */}
                  <div className="flex items-center space-x-2 mt-4 pt-3 border-t border-slate-800">
                    <button
                      onClick={() => {
                        setPaymentDebt(d);
                        setPaymentForm({ totalPaid: String(d.monthlyMinimumDue), principalPortion: '', interestPortion: '', note: '' });
                        setShowPaymentModal(true);
                      }}
                      className="flex-1 py-1.5 rounded-xl bg-amber-500/10 hover:bg-amber-500/20 text-amber-300 border border-amber-500/30 text-xs font-semibold transition-all"
                    >
                      Record Payment
                    </button>
                    <button
                      onClick={() => handleViewSchedule(d)}
                      className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 text-xs transition-all"
                    >
                      Schedule
                    </button>
                  </div>
                </div>
              );
            })}

            {debts.length === 0 && (
              <div className="col-span-full p-8 text-center bg-slate-900/40 border border-dashed border-slate-800 rounded-3xl text-slate-400">
                <CreditCard className="w-8 h-8 text-slate-600 mx-auto mb-2" />
                <p className="font-medium text-slate-300">No active debts registered</p>
                <p className="text-xs text-slate-500 mt-1">Add your loans, BNPL, or credit card balances to simulate payoff</p>
                <button
                  onClick={() => setShowDebtModal(true)}
                  className="mt-4 px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-semibold transition-all"
                >
                  + Add First Debt
                </button>
              </div>
            )}
          </div>

          {/* Payoff Acceleration Simulator Section */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-4 border-b border-slate-800 gap-3">
              <div>
                <h3 className="font-semibold text-white text-sm flex items-center space-x-2">
                  <Sparkles className="w-4 h-4 text-cyan-400" />
                  <span>Debt-Free Payoff Simulator (Avalanche vs. Snowball)</span>
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">Deterministic pure Java mathematical simulation</p>
              </div>

              {/* Surplus Slider */}
              <div className="flex items-center space-x-3">
                <span className="text-xs text-slate-400">Monthly Surplus:</span>
                <span className="text-xs font-bold text-cyan-400 min-w-24 text-right">{formatVnd(surplusVnd)}</span>
                <input
                  type="range"
                  min="0"
                  max="10000000"
                  step="500000"
                  value={surplusVnd}
                  onChange={(e) => handleSimulate(Number(e.target.value))}
                  className="w-32 accent-cyan-500 cursor-pointer"
                />
              </div>
            </div>

            {simulation && (
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-6">
                {/* Baseline Strategy */}
                <div className="bg-slate-950/60 border border-slate-800 rounded-2xl p-4">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-400 uppercase">Baseline (Minimums Only)</span>
                    <span className="text-[10px] text-slate-500">No surplus</span>
                  </div>
                  <div className="text-xl font-bold text-slate-100 mt-2">
                    {simulation.baseline.debtFreeDate || 'N/A'}
                  </div>
                  <div className="text-xs text-slate-500 mt-1">{simulation.baseline.totalMonths} months to debt-free</div>
                  <div className="mt-4 pt-3 border-t border-slate-800 text-xs flex justify-between">
                    <span className="text-slate-400">Total Interest:</span>
                    <span className="font-semibold text-rose-400">{formatVnd(simulation.baseline.totalInterestPaid)}</span>
                  </div>
                </div>

                {/* Avalanche Strategy */}
                <div className="bg-gradient-to-br from-cyan-950/30 to-slate-950 border border-cyan-500/30 rounded-2xl p-4 shadow-lg shadow-cyan-950/20">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-cyan-300 uppercase flex items-center space-x-1">
                      <Zap className="w-3 h-3 text-cyan-400" />
                      <span>Avalanche (Highest APR)</span>
                    </span>
                    <span className="text-[10px] text-cyan-400 font-semibold">Optimal</span>
                  </div>
                  <div className="text-xl font-bold text-cyan-300 mt-2">
                    {simulation.avalanche.debtFreeDate || 'N/A'}
                  </div>
                  <div className="text-xs text-cyan-400/80 mt-1">
                    {simulation.avalanche.totalMonths} months ({simulation.avalancheMonthsSaved} months faster!)
                  </div>
                  <div className="mt-4 pt-3 border-t border-cyan-800/40 text-xs flex justify-between">
                    <span className="text-slate-400">Interest Saved:</span>
                    <span className="font-bold text-emerald-400">+{formatVnd(simulation.avalancheInterestSaved)}</span>
                  </div>
                </div>

                {/* Snowball Strategy */}
                <div className="bg-slate-950/60 border border-slate-800 rounded-2xl p-4">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-amber-300 uppercase">Snowball (Smallest Balance)</span>
                    <span className="text-[10px] text-amber-500">Momentum</span>
                  </div>
                  <div className="text-xl font-bold text-amber-300 mt-2">
                    {simulation.snowball.debtFreeDate || 'N/A'}
                  </div>
                  <div className="text-xs text-slate-400 mt-1">
                    {simulation.snowball.totalMonths} months ({simulation.snowballMonthsSaved} months faster)
                  </div>
                  <div className="mt-4 pt-3 border-t border-slate-800 text-xs flex justify-between">
                    <span className="text-slate-400">Interest Saved:</span>
                    <span className="font-bold text-emerald-400">+{formatVnd(simulation.snowballInterestSaved)}</span>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── TAB 3: INCOMES & INFLOWS ────────────────────────────────────── */}
      {activeTab === 'incomes' && (
        <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
          <div className="flex items-center justify-between pb-4 border-b border-slate-800">
            <div>
              <h3 className="font-semibold text-white text-sm">Income Streams & Expected Inflows</h3>
              <p className="text-xs text-slate-400 mt-0.5">Track salary, freelance, bonuses with probabilistic confidence</p>
            </div>
            <button
              onClick={() => setShowIncomeModal(true)}
              className="px-3 py-1.5 rounded-xl bg-emerald-500/10 hover:bg-emerald-500/20 border border-emerald-500/30 text-emerald-300 text-xs font-semibold transition-all flex items-center space-x-1"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add Income</span>
            </button>
          </div>

          <div className="overflow-x-auto mt-4">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-800 text-slate-500 font-semibold uppercase tracking-wider">
                  <th className="pb-3 px-3">Source</th>
                  <th className="pb-3 px-3">Type</th>
                  <th className="pb-3 px-3">Expected Date</th>
                  <th className="pb-3 px-3">Expected Amount</th>
                  <th className="pb-3 px-3">Actual Amount</th>
                  <th className="pb-3 px-3">Confidence</th>
                  <th className="pb-3 px-3">Status</th>
                  <th className="pb-3 px-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {incomes.map((inc) => (
                  <tr key={inc.id} className="hover:bg-slate-800/20 transition-colors">
                    <td className="py-3 px-3 font-semibold text-slate-100">{inc.source}</td>
                    <td className="py-3 px-3 text-slate-400">
                      <span className="px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 border border-slate-700 text-[10px]">
                        {inc.incomeType}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-slate-400">{inc.expectedDate}</td>
                    <td className="py-3 px-3 font-bold text-emerald-400">{formatVnd(inc.expectedAmount)}</td>
                    <td className="py-3 px-3 text-slate-300">{inc.actualAmount ? formatVnd(inc.actualAmount) : '--'}</td>
                    <td className="py-3 px-3 text-slate-400">{Math.round(inc.confidenceFactor * 100)}%</td>
                    <td className="py-3 px-3">
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                        inc.status === 'RECEIVED'
                          ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                          : 'bg-amber-500/10 text-amber-400 border border-amber-500/30'
                      }`}>
                        {inc.status}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-right">
                      {inc.status === 'EXPECTED' && (
                        <button
                          onClick={() => handleMarkIncomeReceived(inc.id, inc.expectedAmount)}
                          className="px-2.5 py-1 rounded-lg bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 border border-emerald-500/40 text-[11px] font-semibold transition-all"
                        >
                          Mark Received
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {incomes.length === 0 && (
                  <tr>
                    <td colSpan={8} className="py-8 text-center text-slate-500">
                      No income streams recorded yet. Click "Add Income" to record expected salary or cash receipts.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ── TAB 4: SPEND LEDGER & BREAKDOWN ────────────────────────────── */}
      {activeTab === 'analytics' && (
        <div className="space-y-6">
          {/* Charts Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
              <h3 className="font-semibold text-white text-sm flex items-center space-x-2 pb-4 border-b border-slate-800">
                <PieIcon className="w-4 h-4 text-purple-400" />
                <span>Spend Distribution by Category</span>
              </h3>
              <div className="h-64 mt-4">
                {analytics && analytics.categories && analytics.categories.length > 0 ? (
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={analytics.categories}
                        dataKey="totalVnd"
                        nameKey="category"
                        cx="50%"
                        cy="50%"
                        innerRadius={55}
                        outerRadius={85}
                        paddingAngle={3}
                      >
                        {analytics.categories.map((entry, idx) => (
                          <Cell 
                            key={`cell-${idx}`} 
                            fill={CATEGORY_COLORS[entry.category] || DEFAULT_PALETTE[idx % DEFAULT_PALETTE.length]} 
                          />
                        ))}
                      </Pie>
                      <Tooltip 
                        formatter={(val: any) => [formatVnd(Number(val)), 'Amount']}
                        contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '1rem', color: '#fff', fontSize: '12px' }}
                      />
                    </PieChart>
                  </ResponsiveContainer>
                ) : (
                  <div className="h-full flex items-center justify-center text-xs text-slate-500">No expense records for chart</div>
                )}
              </div>
            </div>

            <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
              <h3 className="font-semibold text-white text-sm flex items-center space-x-2 pb-4 border-b border-slate-800">
                <BarChart3 className="w-4 h-4 text-cyan-400" />
                <span>Daily Spend Breakdown (Fixed vs Discretionary)</span>
              </h3>
              <div className="h-64 mt-4">
                {analytics && analytics.dailySpending && analytics.dailySpending.length > 0 ? (
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={analytics.dailySpending}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                      <XAxis dataKey="date" tick={{ fill: '#64748b', fontSize: 10 }} />
                      <YAxis tick={{ fill: '#64748b', fontSize: 10 }} tickFormatter={(val) => `${val / 1000}k`} />
                      <Tooltip 
                        formatter={(val: any) => [formatVnd(Number(val)), 'Amount']}
                        contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '1rem', color: '#fff', fontSize: '12px' }}
                      />
                      <Bar dataKey="fixedVnd" name="Fixed" stackId="a" fill="#3b82f6" />
                      <Bar dataKey="discretionaryVnd" name="Discretionary" stackId="a" fill="#10b981" />
                    </BarChart>
                  </ResponsiveContainer>
                ) : (
                  <div className="h-full flex items-center justify-center text-xs text-slate-500">No daily records for chart</div>
                )}
              </div>
            </div>
          </div>

          {/* Transaction Table */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-4 border-b border-slate-800 gap-3">
              <div>
                <h3 className="font-semibold text-white text-sm">Confirmed Financial Transactions</h3>
                <p className="text-xs text-slate-400 mt-0.5">Immutable audit ledger</p>
              </div>

              <div className="flex items-center flex-wrap gap-2">
                <div className="flex items-center space-x-1 bg-slate-950 p-1 rounded-xl border border-slate-800 text-[11px]">
                  {['ALL', 'DISCRETIONARY', 'FIXED', 'PENDING'].map((type) => (
                    <button
                      key={type}
                      onClick={() => setFilterType(type)}
                      className={`px-2.5 py-1 rounded-lg transition-all ${
                        filterType === type ? 'bg-slate-800 text-cyan-400 font-bold' : 'text-slate-400 hover:text-white'
                      }`}
                    >
                      {type}
                    </button>
                  ))}
                </div>

                <div className="relative">
                  <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-2.5" />
                  <input
                    type="text"
                    placeholder="Search merchant, category..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    className="bg-slate-950 border border-slate-800 rounded-xl pl-8 pr-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-cyan-500 w-44"
                  />
                </div>
              </div>
            </div>

            <div className="overflow-x-auto mt-4">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-800 text-slate-500 font-semibold uppercase tracking-wider">
                    <th className="pb-3 px-3">Date</th>
                    <th className="pb-3 px-3">Merchant</th>
                    <th className="pb-3 px-3">Category</th>
                    <th className="pb-3 px-3">Spend Type</th>
                    <th className="pb-3 px-3 text-right">Amount</th>
                    <th className="pb-3 px-3 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {filteredTransactions.map((t) => (
                    <tr key={t.id} className="hover:bg-slate-800/20 transition-colors">
                      <td className="py-3 px-3 text-slate-400">{t.txDate}</td>
                      <td className="py-3 px-3 font-semibold text-slate-100">{t.merchant || 'Unknown'}</td>
                      <td className="py-3 px-3">
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-800 text-slate-300">
                          {t.category}
                        </span>
                      </td>
                      <td className="py-3 px-3">
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                          t.spendType === 'FIXED' ? 'bg-blue-500/10 text-blue-400 border border-blue-500/30' : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                        }`}>
                          {t.spendType || 'NON-EXPENSE'}
                        </span>
                      </td>
                      <td className="py-3 px-3 text-right font-bold text-slate-200">{formatVnd(t.amount)}</td>
                      <td className="py-3 px-3 text-right">
                        <button
                          onClick={() => handleDeleteTransaction(t.id)}
                          className="p-1 rounded-lg text-slate-500 hover:text-rose-400 transition-colors"
                          title="Delete transaction"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </td>
                    </tr>
                  ))}
                  {filteredTransactions.length === 0 && (
                    <tr>
                      <td colSpan={6} className="py-8 text-center text-slate-500">No transactions found</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ── MODAL: SCHEDULE VIEWER ─────────────────────────────────────── */}
      {selectedDebtForSchedule && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 max-w-2xl w-full max-h-[80vh] flex flex-col">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div>
                <h3 className="font-bold text-white text-base">Contractual Amortization Schedule</h3>
                <p className="text-xs text-slate-400">{selectedDebtForSchedule.name} • {selectedDebtForSchedule.creditor}</p>
              </div>
              <button onClick={() => setSelectedDebtForSchedule(null)} className="p-1 rounded-lg hover:bg-slate-800 text-slate-400">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="overflow-y-auto flex-1 mt-4">
              {loadingSchedule ? (
                <div className="py-12 text-center text-slate-400 text-xs">Loading amortization schedule...</div>
              ) : (
                <table className="w-full text-left text-xs">
                  <thead className="sticky top-0 bg-slate-900 border-b border-slate-800 text-slate-500 uppercase">
                    <tr>
                      <th className="py-2 px-2">#</th>
                      <th className="py-2 px-2">Due Date</th>
                      <th className="py-2 px-2">Payment</th>
                      <th className="py-2 px-2">Principal</th>
                      <th className="py-2 px-2">Interest</th>
                      <th className="py-2 px-2">Remaining</th>
                      <th className="py-2 px-2">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60">
                    {scheduleItems.map((item) => (
                      <tr key={item.id} className="hover:bg-slate-800/30">
                        <td className="py-2.5 px-2 text-slate-400">{item.installmentNumber}</td>
                        <td className="py-2.5 px-2 font-medium text-slate-200">{item.dueDate}</td>
                        <td className="py-2.5 px-2 font-bold text-slate-100">{formatVnd(item.expectedPayment)}</td>
                        <td className="py-2.5 px-2 text-emerald-400">{formatVnd(item.expectedPrincipal)}</td>
                        <td className="py-2.5 px-2 text-rose-400">{formatVnd(item.expectedInterest)}</td>
                        <td className="py-2.5 px-2 text-slate-300">{formatVnd(item.remainingPrincipalAfter)}</td>
                        <td className="py-2.5 px-2">
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                            item.status === 'PAID'
                              ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                              : 'bg-amber-500/10 text-amber-400 border border-amber-500/30'
                          }`}>
                            {item.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ── MODAL: ADD EXPENSE ─────────────────────────────────────────── */}
      {showExpenseModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <form onSubmit={handleCreateExpense} className="bg-slate-900 border border-slate-800 rounded-3xl p-6 max-w-md w-full space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h3 className="font-bold text-white text-base">Record Direct Expense</h3>
              <button type="button" onClick={() => setShowExpenseModal(false)} className="text-slate-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Amount (VND)</label>
              <input
                type="number"
                required
                placeholder="e.g. 50000"
                value={expenseForm.amount}
                onChange={(e) => setExpenseForm({ ...expenseForm, amount: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-cyan-500"
              />
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Merchant / Vendor</label>
              <input
                type="text"
                placeholder="e.g. Grab, Starbucks, Circle K"
                value={expenseForm.merchant}
                onChange={(e) => setExpenseForm({ ...expenseForm, merchant: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-cyan-500"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 block mb-1">Category</label>
                <select
                  value={expenseForm.category}
                  onChange={(e) => setExpenseForm({ ...expenseForm, category: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500"
                >
                  <option value="Food & Dining">Food & Dining</option>
                  <option value="Groceries">Groceries</option>
                  <option value="Transportation">Transportation</option>
                  <option value="Utilities">Utilities</option>
                  <option value="Shopping">Shopping</option>
                  <option value="Entertainment">Entertainment</option>
                  <option value="Other">Other</option>
                </select>
              </div>

              <div>
                <label className="text-xs text-slate-400 block mb-1">Spend Type</label>
                <select
                  value={expenseForm.spendType}
                  onChange={(e) => setExpenseForm({ ...expenseForm, spendType: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500"
                >
                  <option value="DISCRETIONARY">Discretionary</option>
                  <option value="FIXED">Fixed (Bills/Rent)</option>
                </select>
              </div>
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Note (Optional)</label>
              <input
                type="text"
                placeholder="e.g. Lunch with team"
                value={expenseForm.note}
                onChange={(e) => setExpenseForm({ ...expenseForm, note: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
              />
            </div>

            <button
              type="submit"
              className="w-full py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white font-semibold text-xs transition-all mt-4"
            >
              Save Expense
            </button>
          </form>
        </div>
      )}

      {/* ── MODAL: ADD INCOME ──────────────────────────────────────────── */}
      {showIncomeModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <form onSubmit={handleCreateIncome} className="bg-slate-900 border border-slate-800 rounded-3xl p-6 max-w-md w-full space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h3 className="font-bold text-white text-base">Record Expected Income</h3>
              <button type="button" onClick={() => setShowIncomeModal(false)} className="text-slate-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Income Source</label>
              <input
                type="text"
                required
                placeholder="e.g. Acme Corp Salary, Freelance Client"
                value={incomeForm.source}
                onChange={(e) => setIncomeForm({ ...incomeForm, source: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-cyan-500"
              />
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Expected Amount (VND)</label>
              <input
                type="number"
                required
                placeholder="e.g. 43000000"
                value={incomeForm.expectedAmount}
                onChange={(e) => setIncomeForm({ ...incomeForm, expectedAmount: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-cyan-500"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 block mb-1">Expected Date</label>
                <input
                  type="date"
                  required
                  value={incomeForm.expectedDate}
                  onChange={(e) => setIncomeForm({ ...incomeForm, expectedDate: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div>
                <label className="text-xs text-slate-400 block mb-1">Income Type</label>
                <select
                  value={incomeForm.incomeType}
                  onChange={(e) => setIncomeForm({ ...incomeForm, incomeType: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500"
                >
                  <option value="SALARY">Salary</option>
                  <option value="FREELANCE">Freelance</option>
                  <option value="BONUS">Bonus</option>
                  <option value="INVESTMENT">Investment</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
            </div>

            <button
              type="submit"
              className="w-full py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-semibold text-xs transition-all mt-4"
            >
              Save Expected Income
            </button>
          </form>
        </div>
      )}

      {/* ── MODAL: ADD DEBT ────────────────────────────────────────────── */}
      {showDebtModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <form onSubmit={handleCreateDebt} className="bg-slate-900 border border-slate-800 rounded-3xl p-6 max-w-md w-full space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h3 className="font-bold text-white text-base">Add Debt / Loan Contract</h3>
              <button type="button" onClick={() => setShowDebtModal(false)} className="text-slate-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Debt Name</label>
              <input
                type="text"
                required
                placeholder="e.g. Mac Mini M4 BNPL, Home Loan"
                value={debtForm.name}
                onChange={(e) => setDebtForm({ ...debtForm, name: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-cyan-500"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 block mb-1">Creditor / Bank</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Home Credit, VCB"
                  value={debtForm.creditor}
                  onChange={(e) => setDebtForm({ ...debtForm, creditor: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div>
                <label className="text-xs text-slate-400 block mb-1">Debt Type</label>
                <select
                  value={debtForm.debtType}
                  onChange={(e) => setDebtForm({ ...debtForm, debtType: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500"
                >
                  <option value="AMORTIZING_LOAN">Amortizing Loan</option>
                  <option value="INSTALLMENT_BNPL">Installment BNPL</option>
                  <option value="REVOLVING_CREDIT">Revolving Credit / Card</option>
                  <option value="PEER_DEBT">Peer / Friend Debt</option>
                </select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 block mb-1">Principal Balance (VND)</label>
                <input
                  type="number"
                  required
                  placeholder="e.g. 24000000"
                  value={debtForm.originalPrincipal}
                  onChange={(e) => setDebtForm({ ...debtForm, originalPrincipal: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div>
                <label className="text-xs text-slate-400 block mb-1">Interest APR (%)</label>
                <input
                  type="number"
                  step="0.01"
                  required
                  placeholder="e.g. 8.5 for 8.5%"
                  value={debtForm.interestRateApr}
                  onChange={(e) => setDebtForm({ ...debtForm, interestRateApr: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 block mb-1">Monthly Payment (VND)</label>
                <input
                  type="number"
                  placeholder="Leave empty to auto-calc"
                  value={debtForm.monthlyMinimumDue}
                  onChange={(e) => setDebtForm({ ...debtForm, monthlyMinimumDue: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div>
                <label className="text-xs text-slate-400 block mb-1">Due Day of Month</label>
                <input
                  type="number"
                  min="1"
                  max="31"
                  value={debtForm.dueDayOfMonth}
                  onChange={(e) => setDebtForm({ ...debtForm, dueDayOfMonth: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
                />
              </div>
            </div>

            <button
              type="submit"
              className="w-full py-2.5 rounded-xl bg-amber-600 hover:bg-amber-500 text-white font-semibold text-xs transition-all mt-4"
            >
              Create Debt & Generate Schedule
            </button>
          </form>
        </div>
      )}

      {/* ── MODAL: RECORD PAYMENT ──────────────────────────────────────── */}
      {showPaymentModal && paymentDebt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <form onSubmit={handleRecordPayment} className="bg-slate-900 border border-slate-800 rounded-3xl p-6 max-w-md w-full space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div>
                <h3 className="font-bold text-white text-base">Record Debt Payment</h3>
                <p className="text-xs text-slate-400">{paymentDebt.name} ({paymentDebt.creditor})</p>
              </div>
              <button type="button" onClick={() => setShowPaymentModal(false)} className="text-slate-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Total Paid (VND)</label>
              <input
                type="number"
                required
                value={paymentForm.totalPaid}
                onChange={(e) => setPaymentForm({ ...paymentForm, totalPaid: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-cyan-500"
              />
              <p className="text-[10px] text-slate-500 mt-1">
                Auto-splits principal & interest via Java engine if portions are left blank.
              </p>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 block mb-1">Principal (Optional)</label>
                <input
                  type="number"
                  placeholder="Auto-calculated"
                  value={paymentForm.principalPortion}
                  onChange={(e) => setPaymentForm({ ...paymentForm, principalPortion: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div>
                <label className="text-xs text-slate-400 block mb-1">Interest / Fee (Optional)</label>
                <input
                  type="number"
                  placeholder="Auto-calculated"
                  value={paymentForm.interestPortion}
                  onChange={(e) => setPaymentForm({ ...paymentForm, interestPortion: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
                />
              </div>
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Note (Optional)</label>
              <input
                type="text"
                placeholder="e.g. Month 2 payment"
                value={paymentForm.note}
                onChange={(e) => setPaymentForm({ ...paymentForm, note: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
              />
            </div>

            <button
              type="submit"
              className="w-full py-2.5 rounded-xl bg-amber-600 hover:bg-amber-500 text-white font-semibold text-xs transition-all mt-4"
            >
              Confirm Payment & Update Balance
            </button>
          </form>
        </div>
      )}
    </div>
  );
};
