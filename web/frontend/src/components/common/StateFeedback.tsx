import React from 'react';
import { AlertCircle, Inbox, RefreshCw, Loader2 } from 'lucide-react';

interface LoadingProps {
  message?: string;
  rows?: number;
}

export const LoadingState: React.FC<LoadingProps> = ({ message = 'Loading details...', rows = 3 }) => {
  return (
    <div className="w-full py-10 px-4 flex flex-col items-center justify-center space-y-4">
      <Loader2 className="w-8 h-8 text-blue-600 dark:text-blue-400 animate-spin" />
      <span className="text-sm font-medium text-slate-500 dark:text-slate-400">{message}</span>
      <div className="w-full max-w-md space-y-2.5 pt-2">
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="h-4 bg-slate-200 dark:bg-slate-800 rounded-md animate-pulse" style={{ width: `${85 - i * 15}%` }} />
        ))}
      </div>
    </div>
  );
};

interface EmptyStateProps {
  title: string;
  description: string;
  icon?: React.ReactNode;
  action?: {
    label: string;
    onClick: () => void;
  };
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  title,
  description,
  icon = <Inbox className="w-12 h-12 text-slate-400" />,
  action
}) => {
  return (
    <div className="w-full py-12 px-6 flex flex-col items-center justify-center text-center bg-slate-50/50 dark:bg-slate-900/40 border border-dashed border-slate-200 dark:border-slate-800 rounded-2xl">
      <div className="p-3 bg-white dark:bg-slate-800 rounded-2xl shadow-xs border border-slate-100 dark:border-slate-700 mb-3 text-slate-400">
        {icon}
      </div>
      <h3 className="text-base font-bold text-slate-800 dark:text-slate-200">{title}</h3>
      <p className="text-sm text-slate-500 dark:text-slate-400 max-w-sm mt-1">{description}</p>
      {action && (
        <button
          onClick={action.onClick}
          className="mt-4 px-4 py-2 rounded-xl text-xs font-semibold bg-blue-600 hover:bg-blue-700 text-white shadow-xs transition-colors"
        >
          {action.label}
        </button>
      )}
    </div>
  );
};

interface ErrorStateProps {
  title?: string;
  message: string;
  onRetry?: () => void;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  title = 'Something went wrong',
  message,
  onRetry
}) => {
  return (
    <div className="w-full p-4 rounded-2xl bg-rose-50 dark:bg-rose-950/30 border border-rose-200 dark:border-rose-900/60 text-rose-900 dark:text-rose-200 flex items-start gap-3 shadow-xs">
      <AlertCircle className="w-5 h-5 text-rose-600 dark:text-rose-400 shrink-0 mt-0.5" />
      <div className="flex-1">
        <h4 className="text-sm font-bold">{title}</h4>
        <p className="text-xs text-rose-700 dark:text-rose-300 mt-0.5 leading-relaxed">{message}</p>
        {onRetry && (
          <button
            onClick={onRetry}
            className="mt-2.5 inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-semibold bg-rose-100 dark:bg-rose-900/60 text-rose-800 dark:text-rose-200 hover:bg-rose-200 dark:hover:bg-rose-900 transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            Try Again
          </button>
        )}
      </div>
    </div>
  );
};
