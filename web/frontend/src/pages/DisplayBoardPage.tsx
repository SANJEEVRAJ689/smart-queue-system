import React, { useEffect, useState, useRef } from 'react';
import { api, getErrorMessage } from '../services/api';
import { DisplayBoardData, Priority } from '../types';
import { wsService } from '../services/websocket';
import { StatusBadge } from '../components/common/StatusBadge';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { LoadingState, EmptyState, ErrorState } from '../components/common/StateFeedback';
import {
  Tv,
  Volume2,
  VolumeX,
  Radio,
  Clock,
  Layers,
  Sparkles,
  ArrowRight
} from 'lucide-react';

export const DisplayBoardPage: React.FC = () => {
  const [boardData, setBoardData] = useState<DisplayBoardData | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [isMuted, setIsMuted] = useState<boolean>(false);
  const [lastAnnouncedToken, setLastAnnouncedToken] = useState<string | null>(null);
  const [recentlyUpdatedCounterId, setRecentlyUpdatedCounterId] = useState<number | null>(null);

  // Synthesize soft pleasant chime sound using Web Audio API
  const playChime = () => {
    if (isMuted) return;
    try {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();

      // Play 2-tone melodic chime (E5 -> B5)
      const playTone = (freq: number, startTime: number, duration: number) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(freq, startTime);

        gain.gain.setValueAtTime(0.01, startTime);
        gain.gain.exponentialRampToValueAtTime(0.3, startTime + 0.05);
        gain.gain.exponentialRampToValueAtTime(0.001, startTime + duration);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(startTime);
        osc.stop(startTime + duration);
      };

      const now = ctx.currentTime;
      playTone(659.25, now, 0.4);       // E5
      playTone(987.77, now + 0.25, 0.6); // B5
    } catch (e) {
      console.warn('Audio chime playback error:', e);
    }
  };

  // Optional voice announcement using SpeechSynthesis
  const speakToken = (tokenNo: string, counterName: string) => {
    if (isMuted) return;
    if ('speechSynthesis' in window) {
      try {
        const utterance = new SpeechSynthesisUtterance(
          `Token number ${tokenNo.split('').join(' ')}, please proceed to ${counterName}`
        );
        utterance.rate = 0.95;
        utterance.pitch = 1.0;
        window.speechSynthesis.speak(utterance);
      } catch {}
    }
  };

  const fetchDisplay = async () => {
    try {
      const data = await api.getDisplayBoard();
      setBoardData(data);
      setError(null);

      // Check if any active counter has a new token
      data.nowServing.forEach((c) => {
        if (c.tokenNo && (c.tokenStatus === 'CALLED' || c.tokenStatus === 'SERVING')) {
          if (c.tokenNo !== lastAnnouncedToken) {
            setLastAnnouncedToken(c.tokenNo);
            setRecentlyUpdatedCounterId(c.counterId);
            playChime();
            speakToken(c.tokenNo, c.counterName);

            setTimeout(() => {
              setRecentlyUpdatedCounterId(null);
            }, 5000);
          }
        }
      });
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDisplay();
    const interval = setInterval(fetchDisplay, 5000);

    // STOMP WebSocket listener
    const unsubscribe = wsService.subscribe('/topic/display', (msg) => {
      fetchDisplay();
    });

    return () => {
      clearInterval(interval);
      unsubscribe();
    };
  }, [lastAnnouncedToken, isMuted]);

  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col justify-between p-4 sm:p-6 lg:p-8">
      {/* Top TV Header Bar */}
      <div className="flex items-center justify-between pb-6 border-b border-slate-800">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-2xl bg-blue-600 flex items-center justify-center text-white shadow-lg shadow-blue-500/30">
            <Tv className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-2xl sm:text-3xl font-black tracking-tight" data-testid="display-title">
              LIVE QUEUE DISPLAY BOARD
            </h1>
            <div className="flex items-center gap-2 text-xs text-slate-400 mt-0.5">
              <span className="flex h-2 w-2 relative">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
              </span>
              <span className="font-mono text-emerald-400 font-bold">REAL-TIME SYNC</span>
              <span>&bull; Waiting Hall TV View</span>
            </div>
          </div>
        </div>

        {/* Audio Mute/Unmute Toggle & Clock */}
        <div className="flex items-center gap-4">
          <button
            onClick={() => setIsMuted(!isMuted)}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all border ${
              isMuted
                ? 'bg-rose-950/40 text-rose-300 border-rose-800'
                : 'bg-emerald-950/40 text-emerald-300 border-emerald-800 hover:bg-emerald-900/40'
            }`}
            data-testid="btn-mute-toggle"
          >
            {isMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
            {isMuted ? 'Audio Muted' : 'Sound Alerts ON'}
          </button>
        </div>
      </div>

      {/* Main Grid: Now Serving Cards (Left) & Next in Line (Right) */}
      <div className="my-8 flex-1 grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Column: Now Serving Per Counter Desks (8 cols) */}
        <div className="lg:col-span-8 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-xs font-bold uppercase tracking-widest text-slate-400">
              NOW SERVING AT COUNTERS
            </h2>
            <span className="text-xs font-mono text-blue-400">
              {boardData?.nowServing.length || 0} Total Desks
            </span>
          </div>

          {loading ? (
            <LoadingState message="Connecting to live television display feed..." rows={3} />
          ) : error ? (
            <ErrorState message={error} onRetry={fetchDisplay} />
          ) : !boardData || boardData.nowServing.length === 0 ? (
            <EmptyState title="No Active Counters" description="All desks are currently closed." />
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              {boardData.nowServing.map((counter) => {
                const isFlash = recentlyUpdatedCounterId === counter.counterId;
                const hasToken = counter.tokenNo;

                return (
                  <div
                    key={counter.counterId}
                    className={`rounded-3xl p-6 border-2 transition-all relative overflow-hidden flex flex-col justify-between min-h-[220px] ${
                      isFlash
                        ? 'bg-blue-900/60 border-blue-400 shadow-2xl shadow-blue-500/40 scale-102 animate-pulse'
                        : hasToken
                        ? 'bg-slate-900 border-slate-700 shadow-xl'
                        : 'bg-slate-900/40 border-slate-800 opacity-60'
                    }`}
                    data-testid={`display-counter-${counter.counterId}`}
                  >
                    {/* Top Row: Counter Name & Status */}
                    <div className="flex items-center justify-between">
                      <span className="font-black text-lg text-slate-200">
                        {counter.counterName}
                      </span>
                      <StatusBadge status={counter.status} size="sm" />
                    </div>

                    {/* Middle: Token Number */}
                    <div className="py-4 text-center">
                      {hasToken ? (
                        <>
                          <div className="text-6xl sm:text-7xl font-black font-mono tracking-tight text-emerald-400 drop-shadow-md">
                            {counter.tokenNo}
                          </div>
                          <span className="text-xs font-semibold text-slate-400 mt-1 block">
                            {counter.serviceName || 'General Service'}
                          </span>
                        </>
                      ) : (
                        <div className="text-3xl font-bold font-mono text-slate-600 tracking-wider">
                          READY
                        </div>
                      )}
                    </div>

                    {/* Bottom Status text */}
                    <div className="border-t border-slate-800 pt-3 flex items-center justify-between text-xs text-slate-400">
                      <span>Status: <strong className="text-white uppercase">{counter.tokenStatus || counter.status}</strong></span>
                      {hasToken && (
                        <span className="text-emerald-400 font-bold flex items-center gap-1">
                          PROCEED NOW <ArrowRight className="w-3.5 h-3.5" />
                        </span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Right Column: Next in Line List (4 cols) */}
        <div className="lg:col-span-4 bg-slate-900/90 rounded-3xl p-6 border border-slate-800 shadow-xl space-y-4">
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <h3 className="font-bold text-sm text-slate-200 flex items-center gap-2">
              <Layers className="w-4 h-4 text-blue-400" />
              NEXT IN LINE (TOP 5)
            </h3>
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
              UPCOMING
            </span>
          </div>

          {!boardData || boardData.nextWaiting.length === 0 ? (
            <div className="py-12 text-center text-xs text-slate-500">
              No customers currently waiting in queue.
            </div>
          ) : (
            <div className="space-y-3">
              {boardData.nextWaiting.map((token, index) => (
                <div
                  key={token.tokenNo}
                  className="p-3.5 rounded-2xl bg-slate-800/80 border border-slate-700/60 flex items-center justify-between"
                >
                  <div className="flex items-center gap-3">
                    <span className="w-6 h-6 rounded-full bg-slate-700 text-slate-300 font-bold text-xs flex items-center justify-center font-mono">
                      {index + 1}
                    </span>
                    <div>
                      <span className="text-xl font-mono font-black text-blue-400 block leading-none">
                        {token.tokenNo}
                      </span>
                      <span className="text-[11px] text-slate-400 block mt-1">
                        {token.serviceName}
                      </span>
                    </div>
                  </div>

                  <PriorityBadge priority={token.priority} />
                </div>
              ))}
            </div>
          )}

          <div className="p-3 bg-slate-800/40 rounded-xl text-center text-[11px] text-slate-500">
            Please listen for audio announcements and watch for your token number.
          </div>
        </div>
      </div>

      {/* Footer Info */}
      <div className="border-t border-slate-800 pt-4 flex flex-col sm:flex-row items-center justify-between text-xs text-slate-500">
        <span>Smart Queue and Token Management System &bull; TV Station Display</span>
        <span className="font-mono mt-1 sm:mt-0">Server Time: {new Date().toLocaleTimeString()}</span>
      </div>
    </div>
  );
};
