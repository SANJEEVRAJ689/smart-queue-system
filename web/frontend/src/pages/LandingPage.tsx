import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, getErrorMessage } from '../services/api';
import { PublicStats } from '../types';
import { wsService } from '../services/websocket';
import {
  Users,
  Briefcase,
  ShieldCheck,
  Tv,
  ArrowRight,
  TrendingUp,
  Clock,
  UserCheck,
  Sparkles,
  Edit2,
  Check,
  Radio,
  Printer
} from 'lucide-react';
import { LoadingState, ErrorState } from '../components/common/StateFeedback';

export const LandingPage: React.FC = () => {
  const [stats, setStats] = useState<PublicStats | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Editable headline feature
  const [headline, setHeadline] = useState<string>(() => {
    return localStorage.getItem('sq_headline') || 'Smart Queue and Token Management System';
  });
  const [isEditingHeadline, setIsEditingHeadline] = useState<boolean>(false);
  const [tempHeadline, setTempHeadline] = useState<string>(headline);

  // Printed ticket animation simulation on load
  const [printedTokenNo, setPrintedTokenNo] = useState<string>('TKT-042');
  const [ticketPrinted, setTicketPrinted] = useState<boolean>(false);

  useEffect(() => {
    // Generate a random preview token on load
    const randNum = Math.floor(100 + Math.random() * 900);
    setPrintedTokenNo(`A-${randNum}`);

    // Trigger print animation after 300ms
    const timer = setTimeout(() => {
      setTicketPrinted(true);
    }, 300);

    return () => clearTimeout(timer);
  }, []);

  const fetchStats = async () => {
    try {
      const data = await api.getPublicStats();
      setStats(data);
      setError(null);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
    // Poll stats every 10s as fallback
    const interval = setInterval(fetchStats, 10000);

    // Subscribe to STOMP display topic for instant updates
    const unsubscribe = wsService.subscribe('/topic/display', () => {
      fetchStats();
    });

    return () => {
      clearInterval(interval);
      unsubscribe();
    };
  }, []);

  const saveHeadline = () => {
    setHeadline(tempHeadline);
    localStorage.setItem('sq_headline', tempHeadline);
    setIsEditingHeadline(false);
  };

  return (
    <div className="min-h-screen flex flex-col space-y-12 pb-16">
      {/* Live Ticker Ribbon */}
      <div className="bg-slate-900 text-white py-2.5 px-4 shadow-sm border-b border-slate-800 overflow-hidden">
        <div className="max-w-7xl mx-auto flex items-center justify-between text-xs">
          <div className="flex items-center gap-2">
            <span className="flex h-2 w-2 relative">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
            </span>
            <span className="font-bold uppercase tracking-wider text-emerald-400 text-[11px] flex items-center gap-1">
              <Radio className="w-3.5 h-3.5" /> LIVE QUEUE FEED:
            </span>
            <span className="font-mono text-slate-200 font-medium truncate max-w-xs sm:max-w-md md:max-w-none" data-testid="live-ticker-text">
              {stats?.nowServingLatest || 'Connecting to live token engine...'}
            </span>
          </div>

          <Link
            to="/display"
            className="hidden sm:inline-flex items-center gap-1 text-[11px] font-semibold text-blue-400 hover:text-blue-300 transition-colors"
          >
            <Tv className="w-3.5 h-3.5" /> Open TV Display &rarr;
          </Link>
        </div>
      </div>

      {/* Hero Section */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-4">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-center">
          {/* Left Column: Headlines & Concept */}
          <div className="lg:col-span-7 space-y-6">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-bold bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 border border-blue-200 dark:border-blue-800">
              <Sparkles className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />
              <span>Persistent &bull; Real-Time &bull; Dual-Surface</span>
            </div>

            {/* Editable Headline */}
            <div className="space-y-2">
              {isEditingHeadline ? (
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    value={tempHeadline}
                    onChange={(e) => setTempHeadline(e.target.value)}
                    className="text-2xl sm:text-4xl font-black text-slate-900 dark:text-white bg-slate-100 dark:bg-slate-800 border border-blue-500 rounded-xl px-3 py-1 w-full"
                    autoFocus
                  />
                  <button
                    onClick={saveHeadline}
                    className="p-2 rounded-xl bg-blue-600 text-white hover:bg-blue-700"
                    title="Save title"
                  >
                    <Check className="w-5 h-5" />
                  </button>
                </div>
              ) : (
                <div className="group flex items-start gap-3">
                  <h1 className="text-3xl sm:text-5xl font-black text-slate-900 dark:text-white tracking-tight leading-none" data-testid="landing-headline">
                    {headline}
                  </h1>
                  <button
                    onClick={() => {
                      setTempHeadline(headline);
                      setIsEditingHeadline(true);
                    }}
                    title="Edit Title"
                    className="opacity-0 group-hover:opacity-100 p-1.5 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-all shrink-0 mt-1"
                  >
                    <Edit2 className="w-4 h-4" />
                  </button>
                </div>
              )}

              <p className="text-base sm:text-lg text-slate-600 dark:text-slate-300 leading-relaxed max-w-2xl">
                Experience seamless queueing with zero crowd friction. Take a digital ticket, track your place in real time on your mobile phone, and receive instant alerts when your counter is ready.
              </p>
            </div>

            {/* Quick Actions */}
            <div className="flex flex-wrap gap-4 pt-2">
              <Link
                to="/customer"
                className="inline-flex items-center justify-center gap-2.5 px-6 py-3.5 rounded-2xl bg-blue-600 hover:bg-blue-700 text-white font-bold text-sm shadow-lg shadow-blue-500/25 hover:shadow-blue-500/35 hover:-translate-y-0.5 transition-all"
                data-testid="cta-get-token"
              >
                <Users className="w-4 h-4" />
                Take a Digital Token
                <ArrowRight className="w-4 h-4" />
              </Link>

              <Link
                to="/display"
                className="inline-flex items-center justify-center gap-2 px-5 py-3.5 rounded-2xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 font-semibold text-sm hover:bg-slate-50 dark:hover:bg-slate-700/60 shadow-xs transition-colors"
              >
                <Tv className="w-4 h-4" />
                Live Waiting Hall Board
              </Link>
            </div>
          </div>

          {/* Right Column: Physical "Take a Number" Ticket Dispenser Mockup */}
          <div className="lg:col-span-5 flex justify-center">
            <div className="w-full max-w-xs relative flex flex-col items-center">
              {/* Dispenser Machine Box */}
              <div className="w-full bg-linear-to-b from-slate-800 to-slate-900 border-2 border-slate-700 rounded-3xl p-5 shadow-2xl relative z-20 text-center text-white">
                <div className="w-12 h-1.5 bg-slate-600 rounded-full mx-auto mb-3"></div>
                <div className="text-[10px] font-black uppercase tracking-widest text-blue-400">Automatic Dispenser</div>
                <div className="text-sm font-black mt-0.5">PRESS OR SCAN FOR TOKEN</div>
                
                {/* Dispenser Slot with glowing slit */}
                <div className="w-48 h-3.5 bg-black rounded-full mx-auto mt-4 border border-slate-700 shadow-inner flex items-center justify-center overflow-hidden">
                  <div className="w-24 h-0.5 bg-blue-500/80 blur-xs"></div>
                </div>
              </div>

              {/* Animated Ticket sliding out from slot */}
              <div 
                className={`w-64 bg-white text-slate-900 rounded-b-2xl shadow-xl border-x-2 border-b-2 border-slate-200 overflow-hidden -mt-2 transition-all duration-1000 ease-out z-10 ${
                  ticketPrinted ? 'translate-y-0 opacity-100' : '-translate-y-12 opacity-0'
                }`}
              >
                <div className="border-b-2 border-dashed border-slate-300 py-1 bg-slate-50 text-[10px] uppercase font-bold text-slate-400 tracking-wider text-center">
                  Official Admission Ticket
                </div>
                <div className="p-4 text-center space-y-2">
                  <span className="text-[10px] uppercase font-bold text-slate-400">Current Next Ticket</span>
                  <div className="text-4xl font-mono font-black text-blue-600 tracking-tight" data-testid="dispenser-token-number">
                    {printedTokenNo}
                  </div>
                  <div className="text-xs font-semibold text-slate-600">Admissions &amp; Registration</div>
                  <div className="border-t border-slate-100 pt-2 flex items-center justify-between text-[10px] text-slate-400 font-mono">
                    <span>{new Date().toLocaleDateString()}</span>
                    <span>READY</span>
                  </div>
                </div>
                <div className="bg-blue-600 text-white text-[11px] font-bold py-1.5 text-center flex items-center justify-center gap-1">
                  <Printer className="w-3 h-3" /> Auto-Generated on Load
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Real-time DB Metrics Grid */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 w-full">
        {loading ? (
          <LoadingState message="Connecting to queue database..." rows={2} />
        ) : error ? (
          <ErrorState message={error} onRetry={fetchStats} />
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
            <div className="bg-white dark:bg-slate-800 p-6 rounded-2xl border border-slate-200 dark:border-slate-700/80 shadow-xs flex items-center gap-4">
              <div className="w-12 h-12 rounded-2xl bg-blue-50 dark:bg-blue-950/60 text-blue-600 dark:text-blue-400 flex items-center justify-center shrink-0">
                <TrendingUp className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs font-semibold text-slate-500 dark:text-slate-400 block uppercase tracking-wider">
                  Total Tokens Today
                </span>
                <span className="text-3xl font-black text-slate-900 dark:text-white font-mono" data-testid="stat-tokens-today">
                  {stats?.tokensToday ?? 0}
                </span>
              </div>
            </div>

            <div className="bg-white dark:bg-slate-800 p-6 rounded-2xl border border-slate-200 dark:border-slate-700/80 shadow-xs flex items-center gap-4">
              <div className="w-12 h-12 rounded-2xl bg-amber-50 dark:bg-amber-950/60 text-amber-600 dark:text-amber-400 flex items-center justify-center shrink-0">
                <UserCheck className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs font-semibold text-slate-500 dark:text-slate-400 block uppercase tracking-wider">
                  People In Queue
                </span>
                <span className="text-3xl font-black text-amber-600 dark:text-amber-400 font-mono" data-testid="stat-people-waiting">
                  {stats?.peopleWaiting ?? 0}
                </span>
              </div>
            </div>

            <div className="bg-white dark:bg-slate-800 p-6 rounded-2xl border border-slate-200 dark:border-slate-700/80 shadow-xs flex items-center gap-4">
              <div className="w-12 h-12 rounded-2xl bg-emerald-50 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400 flex items-center justify-center shrink-0">
                <Clock className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs font-semibold text-slate-500 dark:text-slate-400 block uppercase tracking-wider">
                  Average Wait Time
                </span>
                <span className="text-3xl font-black text-emerald-600 dark:text-emerald-400 font-mono" data-testid="stat-avg-wait">
                  ~{stats?.avgWaitMinutes ?? 0}m
                </span>
              </div>
            </div>
          </div>
        )}
      </section>

      {/* Three Primary Role Entry Cards */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 w-full">
        <div className="text-center max-w-2xl mx-auto mb-8">
          <h2 className="text-2xl sm:text-3xl font-black text-slate-900 dark:text-white tracking-tight">
            Role-Based Portal Access
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Choose your interface below for public guest queueing, staff desk management, or administration.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {/* 1. Customer Card (Blue) */}
          <Link
            to="/customer"
            className="group relative bg-white dark:bg-slate-800 rounded-3xl p-7 border-2 border-blue-200 dark:border-blue-900/60 hover:border-blue-500 dark:hover:border-blue-400 shadow-md hover:shadow-xl hover:-translate-y-1 transition-all flex flex-col justify-between"
            data-testid="card-customer"
          >
            <div className="space-y-4">
              <div className="w-14 h-14 rounded-2xl bg-blue-600 text-white flex items-center justify-center shadow-lg shadow-blue-500/30 group-hover:scale-105 transition-transform">
                <Users className="w-7 h-7" />
              </div>
              <div>
                <span className="text-xs font-bold uppercase tracking-wider text-blue-600 dark:text-blue-400">
                  Public Guest Portal
                </span>
                <h3 className="text-xl font-black text-slate-900 dark:text-white mt-1">
                  Customer &amp; Kiosk
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-2 leading-relaxed">
                  Book a token instantly for any department, check queue positions, view estimated wait times, and track your ticket live on your device.
                </p>
              </div>
            </div>

            <div className="pt-6 mt-6 border-t border-slate-100 dark:border-slate-700/60 flex items-center justify-between text-xs font-bold text-blue-600 dark:text-blue-400">
              <span>Book / Track Ticket</span>
              <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
            </div>
          </Link>

          {/* 2. Staff Card (Green) */}
          <Link
            to="/staff/login"
            className="group relative bg-white dark:bg-slate-800 rounded-3xl p-7 border-2 border-emerald-200 dark:border-emerald-900/60 hover:border-emerald-500 dark:hover:border-emerald-400 shadow-md hover:shadow-xl hover:-translate-y-1 transition-all flex flex-col justify-between"
            data-testid="card-staff"
          >
            <div className="space-y-4">
              <div className="w-14 h-14 rounded-2xl bg-emerald-600 text-white flex items-center justify-center shadow-lg shadow-emerald-500/30 group-hover:scale-105 transition-transform">
                <Briefcase className="w-7 h-7" />
              </div>
              <div>
                <span className="text-xs font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-400">
                  Desk Station
                </span>
                <h3 className="text-xl font-black text-slate-900 dark:text-white mt-1">
                  Staff Counter Desk
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-2 leading-relaxed">
                  Select your counter, call next tokens in strict priority order, start serving with real-time session duration timers, complete, skip, or recall customers.
                </p>
              </div>
            </div>

            <div className="pt-6 mt-6 border-t border-slate-100 dark:border-slate-700/60 flex items-center justify-between text-xs font-bold text-emerald-600 dark:text-emerald-400">
              <span>Staff Login (staff1)</span>
              <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
            </div>
          </Link>

          {/* 3. Admin Card (Purple) */}
          <Link
            to="/admin/login"
            className="group relative bg-white dark:bg-slate-800 rounded-3xl p-7 border-2 border-purple-200 dark:border-purple-900/60 hover:border-purple-500 dark:hover:border-purple-400 shadow-md hover:shadow-xl hover:-translate-y-1 transition-all flex flex-col justify-between"
            data-testid="card-admin"
          >
            <div className="space-y-4">
              <div className="w-14 h-14 rounded-2xl bg-purple-600 text-white flex items-center justify-center shadow-lg shadow-purple-500/30 group-hover:scale-105 transition-transform">
                <ShieldCheck className="w-7 h-7" />
              </div>
              <div>
                <span className="text-xs font-bold uppercase tracking-wider text-purple-600 dark:text-purple-400">
                  Management &amp; Analytics
                </span>
                <h3 className="text-xl font-black text-slate-900 dark:text-white mt-1">
                  Executive Admin Portal
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-2 leading-relaxed">
                  Analyze real-time service KPIs and hourly charts, configure departments and counters, manage staff user accounts, inspect audit logs, and export CSV reports.
                </p>
              </div>
            </div>

            <div className="pt-6 mt-6 border-t border-slate-100 dark:border-slate-700/60 flex items-center justify-between text-xs font-bold text-purple-600 dark:text-purple-400">
              <span>Admin Login (admin)</span>
              <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
            </div>
          </Link>
        </div>
      </section>
    </div>
  );
};
