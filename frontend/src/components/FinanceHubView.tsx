import React, { useEffect, useState } from 'react';
import { 
  Wallet, 
  PieChart as PieIcon, 
  BarChart3, 
  Search, 
  Trash2, 
  CheckCircle, 
  Clock, 
  RefreshCw,
  AlertCircle
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
  CartesianGrid, 
  Legend 
} from 'recharts';
import { api } from '../api/client';
import type { FinanceAnalyticsResponse, Transaction } from '../types';

const CATEGORY_COLORS: { [key: string]: string } = {
  'Housing': '#3b82f6',
  'Food & Dining': '#f97316',
  'Groceries': '#10b981',
  'Transportation': '#a855f7',
  'Utilities': '#06b6d4',
  'Healthcare': '#ec4899',
  'Entertainment': '#eab308',
  'Shopping': '#6366f1',
  'Education': '#14b8a6',
  'Other': '#64748b'
};

const DEFAULT_PALETTE = ['#3b82f6', '#f97316', '#10b981', '#a855f7', '#06b6d4', '#ec4899', '#eab308', '#6366f1'];

export const FinanceHubView: React.FC = () => {
  const currentMonthStr = new Date().toISOString().slice(0, 7);
  const [selectedMonth, setSelectedMonth] = useState<string>(currentMonthStr);
  const [analytics, setAnalytics] = useState<FinanceAnalyticsResponse | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState<string>('');
  const [filterType, setFilterType] = useState<string>('ALL');

  useEffect(() => {
    loadData(selectedMonth);
  }, [selectedMonth]);

  const loadData = async (month: string) => {
    try {
      setLoading(true);
      setError(null);
      const [an, txs] = await Promise.all([
        api.getFinanceAnalytics(month),
        api.getRecentTransactions(100)
      ]);
      setAnalytics(an);
      setTransactions(txs);
    } catch (err: any) {
      setError(err.message || 'Failed to load financial data');
    } finally {
      setLoading(false);
    }
  };

  const handleConfirm = async (id: string) => {
    try {
      await api.confirmTransaction(id);
      loadData(selectedMonth);
    } catch (err: any) {
      alert(`Error confirming transaction: ${err.message}`);
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('Delete this transaction?')) return;
    try {
      await api.deleteTransaction(id);
      loadData(selectedMonth);
    } catch (err: any) {
      alert(`Error deleting transaction: ${err.message}`);
    }
  };

  const formatVnd = (amount?: number) => {
    if (amount == null) return '-- VND';
    return new Intl.NumberFormat('vi-VN').format(amount) + ' ₫';
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
      {/* Month Picker & Summary Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between bg-slate-900/60 border border-slate-800 rounded-2xl p-4 gap-3">
        <div className="flex items-center space-x-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center">
            <Wallet className="w-5 h-5 text-emerald-400" />
          </div>
          <div>
            <h2 className="font-bold text-white text-base">Financial Ledger & Analytics</h2>
            <p className="text-xs text-slate-400">Deterministic burn rate & cash-flow engine</p>
          </div>
        </div>

        <div className="flex items-center space-x-2">
          <input
            type="month"
            value={selectedMonth}
            onChange={(e) => setSelectedMonth(e.target.value)}
            className="bg-slate-950 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-cyan-500"
          />
          <button
            onClick={() => loadData(selectedMonth)}
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

      {/* KPI Stats Grid */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4">
          <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Monthly Budget</span>
          <div className="text-base font-bold text-slate-100 mt-1">{formatVnd(analytics?.monthlyBudgetVnd)}</div>
          <span className="text-[10px] text-slate-500">Target baseline</span>
        </div>

        <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4">
          <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Total Spent</span>
          <div className="text-base font-bold text-rose-400 mt-1">{formatVnd(analytics?.totalSpendVnd)}</div>
          <span className="text-[10px] text-slate-400">
            {analytics?.totalSpendVnd && analytics?.monthlyBudgetVnd ? `${Math.round((analytics.totalSpendVnd / analytics.monthlyBudgetVnd) * 100)}% of budget` : '--'}
          </span>
        </div>

        <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4">
          <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Remaining</span>
          <div className="text-base font-bold text-emerald-400 mt-1">{formatVnd(analytics?.remainingBudgetVnd)}</div>
          <span className="text-[10px] text-slate-500">Day {analytics?.elapsedDays ?? '--'} of {analytics?.daysInMonth ?? '--'}</span>
        </div>

        <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4">
          <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Daily Burn Rate</span>
          <div className="text-base font-bold text-amber-400 mt-1">{formatVnd(analytics?.dailyBurnRateVnd)}</div>
          <span className="text-[10px] text-slate-500">Average / day</span>
        </div>

        <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4">
          <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Safe Daily Allowance</span>
          <div className="text-base font-bold text-cyan-300 mt-1">{formatVnd(analytics?.safeDailyAllowanceVnd)}</div>
          <span className="text-[10px] text-slate-500">Remaining pace</span>
        </div>

        <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-4">
          <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Projected Spend</span>
          <div className="text-base font-bold text-indigo-300 mt-1">{formatVnd(analytics?.projectedMonthEndSpendVnd)}</div>
          <span className="text-[10px] text-slate-500">Month-end estimate</span>
        </div>
      </div>

      {/* Charts Grid: Donut Category Breakdown & Daily Spend Bar */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Category Donut Chart */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
          <div className="flex items-center justify-between pb-4 border-b border-slate-800">
            <div>
              <h3 className="font-semibold text-white text-sm flex items-center space-x-2">
                <PieIcon className="w-4 h-4 text-purple-400" />
                <span>Spend Distribution by Category</span>
              </h3>
              <p className="text-xs text-slate-400 mt-0.5">Aggregated confirmed expenses</p>
            </div>
          </div>
          <div className="h-64 mt-4">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={analytics?.categories || []}
                  dataKey="totalVnd"
                  nameKey="category"
                  cx="50%"
                  cy="50%"
                  innerRadius={55}
                  outerRadius={85}
                  paddingAngle={3}
                >
                  {(analytics?.categories || []).map((entry, index) => {
                    const color = CATEGORY_COLORS[entry.category] || DEFAULT_PALETTE[index % DEFAULT_PALETTE.length];
                    return <Cell key={`cell-${index}`} fill={color} />;
                  })}
                </Pie>
                <Tooltip 
                  contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '0.75rem', fontSize: '12px' }}
                  formatter={(val: any) => [formatVnd(Number(val)), 'Amount']}
                />
                <Legend wrapperStyle={{ fontSize: '11px' }} />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Daily Spending Stacked Bar Chart */}
        <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
          <div className="flex items-center justify-between pb-4 border-b border-slate-800">
            <div>
              <h3 className="font-semibold text-white text-sm flex items-center space-x-2">
                <BarChart3 className="w-4 h-4 text-blue-400" />
                <span>Daily Spend (Fixed vs. Discretionary)</span>
              </h3>
              <p className="text-xs text-slate-400 mt-0.5">Chronological daily outflow</p>
            </div>
          </div>
          <div className="h-64 mt-4">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={analytics?.dailySpending || []}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                <XAxis dataKey="date" tickFormatter={(d) => d.split('-')[2]} stroke="#64748b" fontSize={11} />
                <YAxis stroke="#64748b" fontSize={11} tickFormatter={(v) => `${Math.round(v / 1000)}k`} />
                <Tooltip 
                  contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '0.75rem', fontSize: '12px' }}
                  formatter={(val: any) => [formatVnd(Number(val)), 'Amount']}
                />
                <Legend wrapperStyle={{ fontSize: '11px', paddingTop: '8px' }} />
                <Bar dataKey="fixedVnd" name="Fixed" stackId="s" fill="#3b82f6" />
                <Bar dataKey="discretionaryVnd" name="Discretionary" stackId="s" fill="#f97316" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Interactive Transaction Ledger Table */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-4 border-b border-slate-800 gap-3">
          <div>
            <h3 className="font-semibold text-white text-sm">Transaction Ledger</h3>
            <p className="text-xs text-slate-400">All scanned & logged transactions</p>
          </div>

          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
            <div className="relative">
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-2.5" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search merchant or category..."
                className="bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-cyan-500 w-full sm:w-60"
              />
            </div>

            <div className="bg-slate-950 p-1 rounded-xl border border-slate-800 flex items-center space-x-1 text-xs">
              {['ALL', 'DISCRETIONARY', 'FIXED', 'PENDING'].map((f) => (
                <button
                  key={f}
                  onClick={() => setFilterType(f)}
                  className={`px-2.5 py-1 rounded-lg font-medium transition-all ${
                    filterType === f ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-white'
                  }`}
                >
                  {f}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-left text-xs text-slate-300">
            <thead className="bg-slate-950/60 text-slate-400 uppercase text-[10px] tracking-wider border-b border-slate-800">
              <tr>
                <th className="py-3 px-4">Date</th>
                <th className="py-3 px-4">Merchant</th>
                <th className="py-3 px-4">Category</th>
                <th className="py-3 px-4">Type</th>
                <th className="py-3 px-4 text-right">Amount</th>
                <th className="py-3 px-4 text-center">Status</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {filteredTransactions.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-slate-500">
                    No transactions matching your filter.
                  </td>
                </tr>
              ) : (
                filteredTransactions.map((tx) => (
                  <tr key={tx.id} className="hover:bg-slate-800/40 transition-colors">
                    <td className="py-3 px-4 text-slate-400 whitespace-nowrap">{tx.txDate}</td>
                    <td className="py-3 px-4 font-medium text-slate-200">
                      {tx.merchant}
                      {tx.invoiceNumber && (
                        <span className="block text-[10px] text-slate-500 font-mono">Ref: {tx.invoiceNumber}</span>
                      )}
                    </td>
                    <td className="py-3 px-4">
                      <span className="px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 text-[10px] border border-slate-700">
                        {tx.category}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <span className={`text-[10px] font-semibold ${
                        tx.spendType === 'FIXED' ? 'text-blue-400' : 'text-orange-400'
                      }`}>
                        {tx.spendType}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right font-semibold text-slate-100 whitespace-nowrap">
                      {formatVnd(tx.amount)}
                    </td>
                    <td className="py-3 px-4 text-center">
                      {tx.confirmed ? (
                        <span className="inline-flex items-center text-emerald-400 text-[10px]">
                          <CheckCircle className="w-3.5 h-3.5 mr-1" /> Confirmed
                        </span>
                      ) : (
                        <span className="inline-flex items-center text-amber-400 text-[10px]">
                          <Clock className="w-3.5 h-3.5 mr-1" /> Pending
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-right space-x-2 whitespace-nowrap">
                      {!tx.confirmed && (
                        <button
                          onClick={() => handleConfirm(tx.id)}
                          className="px-2 py-1 rounded bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-400 border border-emerald-500/30 text-[10px] transition-all font-medium"
                        >
                          Confirm
                        </button>
                      )}
                      <button
                        onClick={() => handleDelete(tx.id)}
                        className="p-1 rounded text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
                        title="Delete transaction"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
