import { useState, useRef, useEffect } from 'react';
import { 
  Send, 
  Paperclip, 
  Bot, 
  User, 
  Trash2, 
  Sparkles, 
  ShieldCheck,
  X
} from 'lucide-react';
import { api } from '../api/client';
import type { ChatMessage } from '../types';

export const AssistantChatView: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 'init-1',
      role: 'assistant',
      content: 'Hello! I am your **Personal Life OS Assistant**. All my answers are strictly grounded in your deterministic Garmin biometrics and financial ledger. How can I help you today?',
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      groundingPills: ['Empirical Truth', 'Temporal UTC+7']
    }
  ]);
  const [input, setInput] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [attachment, setAttachment] = useState<File | null>(null);
  const [attachmentPreview, setAttachmentPreview] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, loading]);

  const handleSend = async (textToSend?: string) => {
    const text = textToSend || input;
    if ((!text.trim() && !attachment) || loading) return;

    const userMsg: ChatMessage = {
      id: Date.now().toString(),
      role: 'user',
      content: text,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    const curAttachment = attachment;
    setAttachment(null);
    setAttachmentPreview(null);
    setLoading(true);

    try {
      const res = await api.chat(text, curAttachment || undefined);
      const assistantMsg: ChatMessage = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: res.reply || 'No response generated.',
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        groundingPills: ['Java Engines Grounded']
      };
      setMessages((prev) => [...prev, assistantMsg]);
    } catch (err: any) {
      const errorMsg: ChatMessage = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: `⚠️ Error contacting Life OS assistant: ${err.message}`,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleFileAttach = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const f = e.target.files[0];
      setAttachment(f);
      setAttachmentPreview(URL.createObjectURL(f));
    }
  };

  const handleClearHistory = () => {
    setMessages([
      {
        id: 'init-reset',
        role: 'assistant',
        content: '🧹 Chat session cleared. Ask me anything about your physical recovery or personal finance ledger!',
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      }
    ]);
  };

  const quickPrompts = [
    'Should I train high intensity today?',
    'How much safe daily budget do I have left?',
    'How was my sleep debt this week?',
    'What was my spending breakdown this month?'
  ];

  return (
    <div className="max-w-4xl mx-auto h-[calc(100vh-140px)] flex flex-col bg-slate-900/80 border border-slate-800 rounded-3xl overflow-hidden backdrop-blur-md shadow-2xl">
      {/* Chat Header */}
      <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/60">
        <div className="flex items-center space-x-3">
          <div className="w-9 h-9 rounded-xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center">
            <Bot className="w-5 h-5 text-cyan-400" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h2 className="font-bold text-white text-sm">Life OS Conversational Partner</h2>
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-950 text-emerald-400 border border-emerald-800/60 flex items-center space-x-1">
                <ShieldCheck className="w-3 h-3" />
                <span>Deterministic Grounding</span>
              </span>
            </div>
            <p className="text-[11px] text-slate-400">Contextual advisor powered by real-time biometrics & finance</p>
          </div>
        </div>

        <button
          onClick={handleClearHistory}
          title="Clear conversation"
          className="p-2 rounded-xl text-slate-400 hover:text-rose-400 hover:bg-slate-800/80 transition-colors"
        >
          <Trash2 className="w-4 h-4" />
        </button>
      </div>

      {/* Messages Stream */}
      <div className="flex-1 overflow-y-auto p-6 space-y-4">
        {messages.map((m) => {
          const isUser = m.role === 'user';
          return (
            <div key={m.id} className={`flex space-x-3 ${isUser ? 'justify-end' : 'justify-start'}`}>
              {!isUser && (
                <div className="w-8 h-8 rounded-xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                  <Bot className="w-4 h-4 text-cyan-400" />
                </div>
              )}

              <div className={`max-w-[80%] rounded-2xl px-4 py-3 text-xs leading-relaxed ${
                isUser 
                  ? 'bg-blue-600 text-white rounded-tr-none' 
                  : 'bg-slate-950/70 border border-slate-800/80 text-slate-200 rounded-tl-none'
              }`}>
                <div className="whitespace-pre-wrap">{m.content}</div>

                {m.groundingPills && m.groundingPills.length > 0 && (
                  <div className="mt-2.5 pt-2 border-t border-slate-800/80 flex flex-wrap gap-1">
                    {m.groundingPills.map((pill, idx) => (
                      <span key={idx} className="text-[9px] px-2 py-0.5 rounded-md bg-slate-900 border border-slate-700/60 text-cyan-400 font-mono">
                        • {pill}
                      </span>
                    ))}
                  </div>
                )}

                <div className={`text-[10px] mt-1.5 ${isUser ? 'text-blue-200/80 text-right' : 'text-slate-500'}`}>
                  {m.timestamp}
                </div>
              </div>

              {isUser && (
                <div className="w-8 h-8 rounded-xl bg-blue-600/20 border border-blue-500/30 flex items-center justify-center flex-shrink-0 mt-0.5">
                  <User className="w-4 h-4 text-blue-400" />
                </div>
              )}
            </div>
          );
        })}

        {loading && (
          <div className="flex space-x-3 justify-start">
            <div className="w-8 h-8 rounded-xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center flex-shrink-0 mt-0.5">
              <Bot className="w-4 h-4 text-cyan-400" />
            </div>
            <div className="bg-slate-950/70 border border-slate-800/80 rounded-2xl rounded-tl-none px-4 py-3 text-xs text-slate-400 flex items-center space-x-2">
              <Sparkles className="w-3.5 h-3.5 text-cyan-400 animate-spin" />
              <span>Synthesizing with biometrics and financial context...</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Quick Prompts Chips */}
      <div className="px-6 py-2 bg-slate-950/40 border-t border-slate-800/60 flex items-center space-x-2 overflow-x-auto no-scrollbar">
        {quickPrompts.map((p, idx) => (
          <button
            key={idx}
            onClick={() => handleSend(p)}
            className="flex-shrink-0 px-3 py-1 rounded-full bg-slate-800/60 hover:bg-slate-800 text-[11px] text-slate-300 border border-slate-700/60 hover:border-cyan-500/40 transition-colors"
          >
            {p}
          </button>
        ))}
      </div>

      {/* Attachment Preview (if any) */}
      {attachmentPreview && (
        <div className="px-6 py-2 bg-slate-950/80 border-t border-slate-800 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <img src={attachmentPreview} alt="Attached" className="w-10 h-10 object-cover rounded-lg border border-slate-700" />
            <span className="text-xs text-slate-300">{attachment?.name}</span>
          </div>
          <button 
            onClick={() => { setAttachment(null); setAttachmentPreview(null); }}
            className="p-1 rounded text-slate-400 hover:text-white"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Chat Input Bar */}
      <div className="p-4 bg-slate-950/80 border-t border-slate-800 flex items-end space-x-2">
        <input 
          type="file" 
          ref={fileInputRef} 
          onChange={handleFileAttach} 
          accept="image/*" 
          className="hidden" 
        />
        <button
          onClick={() => fileInputRef.current?.click()}
          title="Attach image or receipt photo"
          className="p-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-slate-200 border border-slate-700 transition-colors"
        >
          <Paperclip className="w-4 h-4" />
        </button>

        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Ask a question about your recovery or budget, or log an expense..."
          rows={1}
          className="flex-1 bg-slate-900 border border-slate-800 rounded-xl px-4 py-2.5 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500 resize-none max-h-28"
        />

        <button
          onClick={() => handleSend()}
          disabled={loading || (!input.trim() && !attachment)}
          className="p-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-40 text-white shadow-md shadow-blue-600/30 transition-all"
        >
          <Send className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};
