import React, { useEffect, useState, useMemo } from 'react';
import { Timer, Clock } from 'lucide-react';

interface LiveTimerProps {
  startedAtUtc?: string;
  serverTimeUtc?: string;
  staticDurationSeconds?: number;
  label?: string;
  size?: 'sm' | 'md' | 'lg';
}

export const LiveTimer: React.FC<LiveTimerProps> = ({
  startedAtUtc,
  serverTimeUtc,
  staticDurationSeconds,
  label = 'Session Duration',
  size = 'md'
}) => {
  // Compute clock skew between local browser time and server time
  const clockSkewMs = useMemo(() => {
    if (!serverTimeUtc) return 0;
    try {
      const serverMs = new Date(serverTimeUtc).getTime();
      return isNaN(serverMs) ? 0 : Date.now() - serverMs;
    } catch {
      return 0;
    }
  }, [serverTimeUtc]);

  const calculateElapsed = () => {
    if (staticDurationSeconds !== undefined) return staticDurationSeconds;
    if (!startedAtUtc) return 0;
    try {
      const startMs = new Date(startedAtUtc).getTime();
      if (isNaN(startMs)) return 0;
      const nowEstimatedServerMs = Date.now() - clockSkewMs;
      return Math.max(0, Math.floor((nowEstimatedServerMs - startMs) / 1000));
    } catch {
      return 0;
    }
  };

  const [elapsedSeconds, setElapsedSeconds] = useState<number>(calculateElapsed);

  useEffect(() => {
    if (staticDurationSeconds !== undefined || !startedAtUtc) {
      if (staticDurationSeconds !== undefined) setElapsedSeconds(staticDurationSeconds);
      return;
    }

    // Immediately calculate on mount/update
    setElapsedSeconds(calculateElapsed());

    const interval = setInterval(() => {
      setElapsedSeconds(calculateElapsed());
    }, 1000);

    return () => clearInterval(interval);
  }, [startedAtUtc, staticDurationSeconds, clockSkewMs]);

  const formatTime = (totalSec: number) => {
    const hours = Math.floor(totalSec / 3600);
    const mins = Math.floor((totalSec % 3600) / 60);
    const secs = totalSec % 60;

    const pad = (n: number) => n.toString().padStart(2, '0');

    if (hours > 0) {
      return `${pad(hours)}:${pad(mins)}:${pad(secs)}`;
    }
    return `${pad(mins)}:${pad(secs)}`;
  };

  const sizeClasses = {
    sm: 'text-sm font-semibold',
    md: 'text-lg font-bold',
    lg: 'text-2xl font-black'
  };

  return (
    <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg bg-emerald-50 text-emerald-800 border border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800/80 shadow-xs">
      {staticDurationSeconds !== undefined ? (
        <Clock className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
      ) : (
        <Timer className="w-4 h-4 text-emerald-600 dark:text-emerald-400 animate-pulse" />
      )}
      <div className="flex flex-col">
        {label && <span className="text-[10px] uppercase font-semibold text-emerald-600/80 dark:text-emerald-400/80 leading-none">{label}</span>}
        <span className={`font-mono tracking-wider tabular-nums ${sizeClasses[size]}`} data-testid="live-timer-value">
          {formatTime(elapsedSeconds)}
        </span>
      </div>
    </div>
  );
};
