import React, { useState, useRef } from 'react';
import { 
  Camera, 
  UploadCloud, 
  CheckCircle2, 
  FileText, 
  RotateCcw, 
  Sparkles,
  AlertCircle
} from 'lucide-react';
import { api } from '../api/client';
import type { OcrResult } from '../types';

export const ReceiptScannerView: React.FC = () => {
  const [, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [ocrResult, setOcrResult] = useState<OcrResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Editable fields
  const [merchant, setMerchant] = useState<string>('');
  const [amount, setAmount] = useState<number | ''>('');
  const [category, setCategory] = useState<string>('Other');
  const [date, setDate] = useState<string>(new Date().toISOString().slice(0, 10));
  const [invoiceNumber, setInvoiceNumber] = useState<string>('');

  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      processSelectedFile(e.target.files[0]);
    }
  };

  const processSelectedFile = (selectedFile: File) => {
    setFile(selectedFile);
    setPreviewUrl(URL.createObjectURL(selectedFile));
    setOcrResult(null);
    setError(null);
    setSuccessMsg(null);
    triggerOcr(selectedFile);
  };

  const triggerOcr = async (selectedFile: File) => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.uploadReceipt(selectedFile);
      setOcrResult(res);

      if (res.merchant) setMerchant(res.merchant);
      if (res.totalAmount) setAmount(res.totalAmount);
      if (res.category) setCategory(res.category);
      if (res.transactionDate) setDate(res.transactionDate);
      if (res.invoiceNumber) setInvoiceNumber(res.invoiceNumber);
    } catch (err: any) {
      setError(err.message || 'Failed to extract text from receipt image');
    } finally {
      setLoading(false);
    }
  };

  const handleSaveToLedger = async () => {
    if (!amount || amount <= 0) {
      alert('Please specify a valid amount.');
      return;
    }
    try {
      setLoading(true);
      // We can trigger assistant or direct expense recording
      const message = `Log expense: ${amount} VND at ${merchant || 'Unknown'} for ${category} on ${date}`;
      await api.chat(message);
      setSuccessMsg(`Successfully logged ${new Intl.NumberFormat('vi-VN').format(Number(amount))} VND at ${merchant}!`);
      setTimeout(() => {
        resetScanner();
      }, 3000);
    } catch (err: any) {
      setError(`Failed to save transaction: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  const resetScanner = () => {
    setFile(null);
    setPreviewUrl(null);
    setOcrResult(null);
    setError(null);
    setSuccessMsg(null);
    setMerchant('');
    setAmount('');
    setCategory('Other');
    setDate(new Date().toISOString().slice(0, 10));
    setInvoiceNumber('');
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header Banner */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-5 flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl bg-blue-500/10 border border-blue-500/20 flex items-center justify-center">
            <Camera className="w-5 h-5 text-blue-400" />
          </div>
          <div>
            <h2 className="font-bold text-white text-base">Receipt & Transfer Scanner</h2>
            <p className="text-xs text-slate-400">Multimodal Gemini Vision OCR with instant itemized parsing</p>
          </div>
        </div>

        {previewUrl && (
          <button
            onClick={resetScanner}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs text-slate-300 transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Scan Another</span>
          </button>
        )}
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-rose-950/40 border border-rose-800 text-rose-300 text-xs flex items-center space-x-2">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {successMsg && (
        <div className="p-4 rounded-xl bg-emerald-950/40 border border-emerald-800 text-emerald-300 text-xs flex items-center space-x-2">
          <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      {/* Main Upload Zone */}
      {!previewUrl ? (
        <div 
          onClick={() => fileInputRef.current?.click()}
          className="border-2 border-dashed border-slate-700 hover:border-cyan-500/60 rounded-3xl p-12 text-center cursor-pointer bg-slate-900/40 hover:bg-slate-900/80 transition-all group"
        >
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileChange}
            accept="image/*"
            className="hidden"
          />
          <div className="w-16 h-16 rounded-2xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center mx-auto mb-4 group-hover:scale-105 transition-transform">
            <UploadCloud className="w-8 h-8 text-cyan-400" />
          </div>
          <h3 className="text-base font-semibold text-white">Click or drag receipt photo here</h3>
          <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
            Bank transfer confirmation screenshot, paper receipt, or supermarket checkout ticket
          </p>
          <div className="mt-4 flex items-center justify-center space-x-2">
            <span className="text-[11px] px-3 py-1 rounded-full bg-slate-800 text-slate-300 border border-slate-700">
              Camera direct support
            </span>
            <span className="text-[11px] px-3 py-1 rounded-full bg-slate-800 text-slate-300 border border-slate-700">
              Auto-downscaled &lt;1280px
            </span>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Left Column: Image Preview */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-5 backdrop-blur-md flex flex-col items-center">
            <span className="text-xs font-semibold text-slate-400 mb-3 self-start flex items-center space-x-1.5">
              <FileText className="w-3.5 h-3.5 text-cyan-400" />
              <span>Scanned Image Preview</span>
            </span>
            <div className="relative w-full max-h-[460px] rounded-2xl overflow-hidden border border-slate-800 bg-black/50 flex items-center justify-center">
              <img 
                src={previewUrl} 
                alt="Receipt preview" 
                className="max-h-[460px] w-auto object-contain"
              />
              {loading && (
                <div className="absolute inset-0 bg-black/70 backdrop-blur-sm flex flex-col items-center justify-center space-y-3">
                  <Sparkles className="w-8 h-8 text-cyan-400 animate-spin" />
                  <span className="text-xs font-medium text-slate-200">Gemini Vision is analyzing receipt...</span>
                </div>
              )}
            </div>
            {ocrResult?.confidence && (
              <div className="mt-3 text-xs text-slate-400">
                OCR Confidence: <strong className="text-emerald-400">{Math.round(ocrResult.confidence * 100)}%</strong>
              </div>
            )}
          </div>

          {/* Right Column: Extracted & Editable Fields */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 backdrop-blur-md flex flex-col justify-between">
            <div className="space-y-4">
              <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                <h3 className="font-semibold text-white text-sm">Extracted Receipt Metadata</h3>
                <span className="text-[10px] text-cyan-400 uppercase tracking-wider font-semibold">Ready for ledger</span>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Merchant / Payee</label>
                <input
                  type="text"
                  value={merchant}
                  onChange={(e) => setMerchant(e.target.value)}
                  placeholder="Merchant name (e.g. Highlands Coffee)"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-slate-100 focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Total Amount (VND)</label>
                <input
                  type="number"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value ? Number(e.target.value) : '')}
                  placeholder="Total in VND (e.g. 55000)"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-emerald-300 font-bold focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-400 mb-1">Category</label>
                  <select
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
                  >
                    {['Food & Dining', 'Groceries', 'Housing', 'Transportation', 'Utilities', 'Healthcare', 'Entertainment', 'Shopping', 'Other'].map((c) => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-400 mb-1">Date</label>
                  <input
                    type="date"
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-cyan-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Invoice / Ref No (Optional)</label>
                <input
                  type="text"
                  value={invoiceNumber}
                  onChange={(e) => setInvoiceNumber(e.target.value)}
                  placeholder="Reference number for deduplication"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-300 font-mono focus:outline-none focus:border-cyan-500"
                />
              </div>

              {ocrResult?.items && ocrResult.items.length > 0 && (
                <div className="mt-3">
                  <label className="block text-xs font-medium text-slate-400 mb-1">Itemized Lines</label>
                  <div className="bg-slate-950/60 rounded-xl p-3 border border-slate-800/80 max-h-32 overflow-y-auto space-y-1.5 text-xs text-slate-300">
                    {ocrResult.items.map((it, idx) => (
                      <div key={idx} className="flex justify-between border-b border-slate-800/40 pb-1">
                        <span>{it.name} {it.quantity ? `(x${it.quantity})` : ''}</span>
                        <span className="font-semibold">{it.price ? `${new Intl.NumberFormat('vi-VN').format(it.price)} ₫` : ''}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            <div className="pt-6 border-t border-slate-800 mt-6 flex space-x-3">
              <button
                onClick={resetScanner}
                className="w-1/3 py-2.5 rounded-xl border border-slate-700 hover:bg-slate-800 text-xs text-slate-300 font-medium transition-colors"
              >
                Discard
              </button>
              <button
                onClick={handleSaveToLedger}
                disabled={loading || !amount}
                className="w-2/3 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white text-xs font-semibold shadow-lg shadow-blue-600/30 flex items-center justify-center space-x-2 transition-all"
              >
                <CheckCircle2 className="w-4 h-4" />
                <span>Confirm & Log to Ledger</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
