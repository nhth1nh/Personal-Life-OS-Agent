import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface MarkdownContentProps {
  content: string;
  className?: string;
  variant?: 'assistant' | 'user' | 'briefing';
}

export const MarkdownContent: React.FC<MarkdownContentProps> = ({
  content,
  className = '',
  variant = 'assistant'
}) => {
  const isUser = variant === 'user';
  const isBriefing = variant === 'briefing';

  return (
    <div className={`markdown-content ${isBriefing ? 'text-sm leading-relaxed text-slate-300' : 'text-xs leading-relaxed'} ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          p: ({ children }) => (
            <p className={`${isBriefing ? 'mb-3' : 'mb-2'} last:mb-0 leading-relaxed ${isUser ? 'text-white' : 'text-slate-200'}`}>
              {children}
            </p>
          ),
          strong: ({ children }) => (
            <strong className={`font-semibold ${isUser ? 'text-white underline decoration-blue-300/40' : 'text-white font-bold'}`}>
              {children}
            </strong>
          ),
          em: ({ children }) => (
            <em className={`italic ${isUser ? 'text-blue-100' : 'text-slate-300'}`}>
              {children}
            </em>
          ),
          h1: ({ children }) => (
            <h1 className={`text-base font-bold mt-3 mb-1.5 pb-1 border-b ${isUser ? 'text-white border-blue-400/40' : 'text-white border-slate-800'}`}>
              {children}
            </h1>
          ),
          h2: ({ children }) => (
            <h2 className={`text-sm font-bold mt-2.5 mb-1 ${isUser ? 'text-white' : 'text-slate-100'}`}>
              {children}
            </h2>
          ),
          h3: ({ children }) => (
            <h3 className={`text-xs font-semibold mt-2 mb-1 ${isUser ? 'text-blue-100' : 'text-cyan-300'}`}>
              {children}
            </h3>
          ),
          ul: ({ children }) => (
            <ul className={`list-disc pl-4 space-y-1 my-1.5 ${isUser ? 'text-white' : 'text-slate-200'}`}>
              {children}
            </ul>
          ),
          ol: ({ children }) => (
            <ol className={`list-decimal pl-4 space-y-1 my-1.5 ${isUser ? 'text-white' : 'text-slate-200'}`}>
              {children}
            </ol>
          ),
          li: ({ children }) => (
            <li className="leading-relaxed pl-0.5">
              {children}
            </li>
          ),
          code: ({ className: codeClassName, children, ...props }) => {
            const isInline = !codeClassName && typeof children === 'string' && !children.includes('\n');
            if (isInline) {
              return (
                <code
                  className={`px-1.5 py-0.5 rounded-md text-[11px] font-mono ${
                    isUser
                      ? 'bg-blue-700/60 text-blue-100 border border-blue-400/30'
                      : 'bg-slate-900 text-cyan-300 border border-slate-800'
                  }`}
                  {...props}
                >
                  {children}
                </code>
              );
            }
            return (
              <code className={codeClassName} {...props}>
                {children}
              </code>
            );
          },
          pre: ({ children }) => (
            <pre className="p-3 my-2 rounded-xl bg-slate-950 border border-slate-800 overflow-x-auto text-[11px] font-mono text-slate-300">
              {children}
            </pre>
          ),
          blockquote: ({ children }) => (
            <blockquote className={`border-l-2 pl-3 my-2 italic ${
              isUser
                ? 'border-blue-300/60 text-blue-100'
                : 'border-cyan-500/60 text-slate-400'
            }`}>
              {children}
            </blockquote>
          ),
          table: ({ children }) => (
            <div className="overflow-x-auto my-2 rounded-lg border border-slate-800">
              <table className="min-w-full divide-y divide-slate-800 text-[11px] text-left">
                {children}
              </table>
            </div>
          ),
          thead: ({ children }) => (
            <thead className="bg-slate-900/80 text-slate-300 font-semibold">
              {children}
            </thead>
          ),
          th: ({ children }) => (
            <th className="px-3 py-1.5 text-slate-300 font-medium">
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td className="px-3 py-1.5 text-slate-400 border-t border-slate-800/50">
              {children}
            </td>
          ),
          a: ({ href, children }) => (
            <a
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className={`underline font-medium hover:opacity-80 transition-opacity ${
                isUser ? 'text-white' : 'text-cyan-400'
              }`}
            >
              {children}
            </a>
          ),
          hr: () => (
            <hr className={`my-2.5 border-t ${isUser ? 'border-blue-400/40' : 'border-slate-800'}`} />
          )
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
};
