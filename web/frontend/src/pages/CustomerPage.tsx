import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, getErrorMessage } from '../services/api';
import { Service, Priority, BookTokenResponse, TokenStatusResponse } from '../types';
import { wsService } from '../services/websocket';
import { TicketCard } from '../components/common/TicketCard';
import { StatusBadge } from '../components/common/StatusBadge';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { LiveTimer } from '../components/common/LiveTimer';
import { LoadingState, EmptyState, ErrorState } from '../components/common/StateFeedback';
import {
  Ticket,
  Search,
  Users,
  Clock,
  MapPin,
  CheckCircle,
  XCircle,
  AlertTriangle,
  RotateCcw,
  Sparkles,
  Phone,
  User,
  ShieldAlert
} from 'lucide-react';

export const CustomerPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialCode = searchParams.get('code') || '';

  const [activeTab, setActiveTab] = useState<'book' | 'track'>(initialCode ? 'track' : 'book');

  // Booking Form State
  const [services, setServices] = useState<Service[]>([]);
  const [loadingServices, setLoadingServices] = useState<boolean>(true);
  const [selectedServiceId, setSelectedServiceId] = useState<number | null>(null);
  const [customerName, setCustomerName] = useState<string>('');
  const [phone, setPhone] = useState<string>('');
  const [priority, setPriority] = useState<Priority>('NORMAL');
  const [bookingLoading, setBookingLoading] = useState<boolean>(false);
  const [bookingError, setBookingError] = useState<string | null>(null);
  const [bookedTicket, setBookedTicket] = useState<BookTokenResponse | null>(null);

  // Tracking State
  const [trackingCode, setTrackingCode] = useState<string>(initialCode);
  const [lookupTokenNo, setLookupTokenNo] = useState<string>('');
  const [lookupPhone, setLookupPhone] = useState<string>('');
  const [lookupLoading, setLookupLoading] = useState<boolean>(false);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [tokenStatus, setTokenStatus] = useState<TokenStatusResponse | null>(null);
  const [statusLoading, setStatusLoading] = useState<boolean>(false);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState<boolean>(false);

  // Load Services
  useEffect(() => {
    api.getActiveServices()
      .then((data) => {
        setServices(data);
        if (data.length > 0) setSelectedServiceId(data[0].id);
      })
      .catch((err) => {
        setBookingError(getErrorMessage(err));
      })
      .finally(() => {
        setLoadingServices(false);
      });
  }, []);

  // Fetch token tracking details
  const fetchTracking = async (code: string) => {
    if (!code) return;
    setStatusLoading(true);
    setStatusError(null);
    try {
      const data = await api.trackToken(code);
      setTokenStatus(data);
    } catch (err) {
      setStatusError(getErrorMessage(err));
      setTokenStatus(null);
    } finally {
      setStatusLoading(false);
    }
  };

  // On mount or trackingCode change, fetch details & subscribe to WebSocket
  useEffect(() => {
    if (trackingCode) {
      fetchTracking(trackingCode);
      setSearchParams({ code: trackingCode });

      // Subscribe to real-time status updates via STOMP
      const unsubscribe = wsService.subscribe(`/topic/token/${trackingCode}`, (updatedData) => {
        if (typeof updatedData === 'object') {
          setTokenStatus((prev) => ({ ...prev, ...updatedData }));
        } else {
          fetchTracking(trackingCode);
        }
      });

      // Poll every 5s as fallback
      const interval = setInterval(() => fetchTracking(trackingCode), 5000);

      return () => {
        unsubscribe();
        clearInterval(interval);
      };
    }
  }, [trackingCode]);

  // Handle Token Booking Submit
  const handleBookSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedServiceId) {
      setBookingError('Please select a service department.');
      return;
    }
    if (!customerName.trim()) {
      setBookingError('Please enter your full name.');
      return;
    }
    if (!phone.trim() || phone.trim().length < 8) {
      setBookingError('Please enter a valid phone number (minimum 8 digits).');
      return;
    }

    setBookingLoading(true);
    setBookingError(null);

    try {
      const ticket = await api.bookToken({
        serviceId: selectedServiceId,
        customerName: customerName.trim(),
        phone: phone.trim(),
        priority
      });
      setBookedTicket(ticket);
    } catch (err) {
      setBookingError(getErrorMessage(err));
    } finally {
      setBookingLoading(false);
    }
  };

  // Handle Token Lookup by token number + phone
  const handleLookupSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!lookupTokenNo.trim() || !lookupPhone.trim()) {
      setLookupError('Please enter both your Token Number and registered Phone Number.');
      return;
    }

    setLookupLoading(true);
    setLookupError(null);

    try {
      const session = await api.trackSession(lookupTokenNo.trim(), lookupPhone.trim());
      setTrackingCode(session.trackingCode);
      setActiveTab('track');
    } catch (err) {
      setLookupError(getErrorMessage(err));
    } finally {
      setLookupLoading(false);
    }
  };

  // Handle Customer Cancel Token
  const handleCancelToken = async () => {
    if (!tokenStatus?.trackingCode) return;
    if (!window.confirm('Are you sure you want to cancel this ticket? This action cannot be undone.')) {
      return;
    }

    setCancelling(true);
    try {
      await api.cancelToken(tokenStatus.trackingCode);
      await fetchTracking(tokenStatus.trackingCode);
    } catch (err) {
      alert(getErrorMessage(err));
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Tab Switcher */}
      <div className="flex bg-slate-200/70 dark:bg-slate-800 p-1.5 rounded-2xl max-w-md mx-auto">
        <button
          onClick={() => {
            setActiveTab('book');
            setBookedTicket(null);
          }}
          className={`flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl text-xs font-bold transition-all ${
            activeTab === 'book'
              ? 'bg-white dark:bg-slate-900 text-blue-600 dark:text-blue-400 shadow-sm'
              : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
          }`}
          data-testid="tab-book"
        >
          <Ticket className="w-4 h-4" />
          Book Digital Ticket
        </button>

        <button
          onClick={() => setActiveTab('track')}
          className={`flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl text-xs font-bold transition-all ${
            activeTab === 'track'
              ? 'bg-white dark:bg-slate-900 text-blue-600 dark:text-blue-400 shadow-sm'
              : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
          }`}
          data-testid="tab-track"
        >
          <Search className="w-4 h-4" />
          Track My Token
        </button>
      </div>

      {/* TAB 1: BOOK DIGITAL TICKET */}
      {activeTab === 'book' && (
        <div className="space-y-6">
          {bookedTicket ? (
            /* Ticket Confirmation Screen */
            <div className="space-y-6">
              <div className="text-center space-y-1">
                <div className="w-12 h-12 rounded-full bg-emerald-100 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400 flex items-center justify-center mx-auto mb-2">
                  <CheckCircle className="w-6 h-6" />
                </div>
                <h2 className="text-2xl font-black text-slate-900 dark:text-white">Ticket Issued Successfully!</h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">Keep this screen open or track with your phone number.</p>
              </div>

              <TicketCard
                ticket={bookedTicket}
                onTrackNow={(code) => {
                  setTrackingCode(code);
                  setActiveTab('track');
                }}
              />

              <div className="text-center pt-2">
                <button
                  onClick={() => {
                    setBookedTicket(null);
                    setCustomerName('');
                    setPhone('');
                    setPriority('NORMAL');
                  }}
                  className="text-xs font-semibold text-slate-500 hover:text-slate-800 dark:hover:text-slate-200 underline"
                >
                  Book another ticket
                </button>
              </div>
            </div>
          ) : (
            /* Booking Form */
            <form onSubmit={handleBookSubmit} className="bg-white dark:bg-slate-800 rounded-3xl p-6 sm:p-8 border border-slate-200 dark:border-slate-700/80 shadow-md space-y-6">
              <div className="space-y-1">
                <h2 className="text-xl sm:text-2xl font-black text-slate-900 dark:text-white tracking-tight">
                  Take a Number &bull; Customer Kiosk
                </h2>
                <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400">
                  Select your department service below and enter your details to generate your queue token.
                </p>
              </div>

              {bookingError && <ErrorState message={bookingError} />}

              {/* Service Selection */}
              <div className="space-y-3">
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                  Select Department Service *
                </label>

                {loadingServices ? (
                  <LoadingState message="Fetching active services..." rows={2} />
                ) : (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {services.map((srv) => (
                      <button
                        key={srv.id}
                        type="button"
                        onClick={() => setSelectedServiceId(srv.id)}
                        className={`p-4 rounded-2xl border text-left flex flex-col justify-between transition-all ${
                          selectedServiceId === srv.id
                            ? 'border-blue-600 bg-blue-50/70 dark:bg-blue-950/40 dark:border-blue-500 shadow-xs ring-2 ring-blue-500/20'
                            : 'border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-750'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-sm text-slate-900 dark:text-white">{srv.name}</span>
                          <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-slate-200 dark:bg-slate-700 text-slate-800 dark:text-slate-200">
                            Prefix {srv.prefix}
                          </span>
                        </div>
                        <span className="text-xs text-slate-500 dark:text-slate-400 mt-2 flex items-center gap-1">
                          <Clock className="w-3.5 h-3.5" /> ~{srv.avgServiceTime}m per session
                        </span>
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {/* Name & Phone Inputs */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                    Your Full Name *
                  </label>
                  <div className="relative">
                    <User className="w-4 h-4 text-slate-400 absolute left-3.5 top-3.5" />
                    <input
                      type="text"
                      required
                      placeholder="e.g. John Doe"
                      value={customerName}
                      onChange={(e) => setCustomerName(e.target.value)}
                      className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 text-slate-900 dark:text-white text-sm focus:border-blue-600 focus:bg-white dark:focus:bg-slate-900 transition-colors"
                      data-testid="input-customer-name"
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                    Mobile Phone Number *
                  </label>
                  <div className="relative">
                    <Phone className="w-4 h-4 text-slate-400 absolute left-3.5 top-3.5" />
                    <input
                      type="tel"
                      required
                      placeholder="e.g. 9876543210"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value)}
                      className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 text-slate-900 dark:text-white text-sm focus:border-blue-600 focus:bg-white dark:focus:bg-slate-900 transition-colors"
                      data-testid="input-customer-phone"
                    />
                  </div>
                </div>
              </div>

              {/* Priority Selection */}
              <div className="space-y-2">
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                  Customer Category (Priority Rule)
                </label>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <label className={`p-3 rounded-xl border cursor-pointer flex items-center gap-2.5 transition-all ${
                    priority === 'NORMAL'
                      ? 'border-blue-500 bg-blue-50/50 dark:bg-blue-950/30'
                      : 'border-slate-200 dark:border-slate-700'
                  }`}>
                    <input
                      type="radio"
                      name="priority"
                      value="NORMAL"
                      checked={priority === 'NORMAL'}
                      onChange={() => setPriority('NORMAL')}
                      className="text-blue-600"
                    />
                    <div>
                      <span className="block text-xs font-bold text-slate-800 dark:text-slate-200">Standard / Normal</span>
                      <span className="text-[10px] text-slate-500">General admission</span>
                    </div>
                  </label>

                  <label className={`p-3 rounded-xl border cursor-pointer flex items-center gap-2.5 transition-all ${
                    priority === 'SENIOR'
                      ? 'border-amber-500 bg-amber-50/50 dark:bg-amber-950/30'
                      : 'border-slate-200 dark:border-slate-700'
                  }`}>
                    <input
                      type="radio"
                      name="priority"
                      value="SENIOR"
                      checked={priority === 'SENIOR'}
                      onChange={() => setPriority('SENIOR')}
                      className="text-amber-600"
                    />
                    <div>
                      <span className="block text-xs font-bold text-slate-800 dark:text-slate-200">Senior Citizen (60+)</span>
                      <span className="text-[10px] text-slate-500">Fast-track queue</span>
                    </div>
                  </label>

                  <label className={`p-3 rounded-xl border cursor-pointer flex items-center gap-2.5 transition-all ${
                    priority === 'EMERGENCY'
                      ? 'border-red-500 bg-red-50/50 dark:bg-red-950/30'
                      : 'border-slate-200 dark:border-slate-700'
                  }`}>
                    <input
                      type="radio"
                      name="priority"
                      value="EMERGENCY"
                      checked={priority === 'EMERGENCY'}
                      onChange={() => setPriority('EMERGENCY')}
                      className="text-red-600"
                    />
                    <div>
                      <span className="block text-xs font-bold text-slate-800 dark:text-slate-200">Emergency / Urgent</span>
                      <span className="text-[10px] text-slate-500">Highest priority call</span>
                    </div>
                  </label>
                </div>
              </div>

              {/* Submit Button */}
              <button
                type="submit"
                disabled={bookingLoading}
                className="w-full py-3.5 rounded-2xl bg-blue-600 hover:bg-blue-700 text-white font-bold text-sm shadow-lg shadow-blue-500/25 transition-all flex items-center justify-center gap-2 disabled:opacity-50"
                data-testid="btn-issue-token"
              >
                <Ticket className="w-5 h-5" />
                {bookingLoading ? 'Issuing Ticket...' : 'Issue My Digital Ticket'}
              </button>
            </form>
          )}
        </div>
      )}

      {/* TAB 2: TRACK MY TOKEN */}
      {activeTab === 'track' && (
        <div className="space-y-6">
          {/* Lookup Box (if not already tracking or wants to change) */}
          <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 border border-slate-200 dark:border-slate-700/80 shadow-sm space-y-4">
            <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
              <Search className="w-4 h-4 text-blue-600" />
              Find &amp; Track Token
            </h3>

            {lookupError && <ErrorState message={lookupError} />}

            <form onSubmit={handleLookupSubmit} className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <input
                type="text"
                placeholder="Token No (e.g. AD-001)"
                value={lookupTokenNo}
                onChange={(e) => setLookupTokenNo(e.target.value)}
                className="px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 text-sm font-mono font-bold"
                data-testid="input-lookup-token"
              />
              <input
                type="tel"
                placeholder="Phone (e.g. 9876543210)"
                value={lookupPhone}
                onChange={(e) => setLookupPhone(e.target.value)}
                className="px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 text-sm font-mono"
                data-testid="input-lookup-phone"
              />
              <button
                type="submit"
                disabled={lookupLoading}
                className="py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs shadow-xs transition-colors flex items-center justify-center gap-1.5 disabled:opacity-50"
                data-testid="btn-lookup-token"
              >
                <Search className="w-4 h-4" />
                {lookupLoading ? 'Searching...' : 'Search Ticket'}
              </button>
            </form>
          </div>

          {/* Active Ticket Status Card */}
          {statusLoading && !tokenStatus ? (
            <LoadingState message="Fetching live queue status..." rows={3} />
          ) : statusError ? (
            <ErrorState message={statusError} onRetry={() => fetchTracking(trackingCode)} />
          ) : tokenStatus ? (
            <div className="bg-white dark:bg-slate-800 rounded-3xl border border-slate-200 dark:border-slate-700/80 shadow-lg overflow-hidden">
              {/* Header Banner */}
              <div className={`p-6 text-white flex flex-col sm:flex-row sm:items-center justify-between gap-4 ${
                tokenStatus.status === 'CALLED'
                  ? 'bg-blue-600 animate-pulse'
                  : tokenStatus.status === 'SERVING'
                  ? 'bg-emerald-600'
                  : tokenStatus.status === 'COMPLETED'
                  ? 'bg-slate-800'
                  : 'bg-slate-900'
              }`}>
                <div>
                  <span className="text-[11px] uppercase font-bold text-slate-300 block">Current Status</span>
                  <div className="flex items-center gap-2 mt-1">
                    <StatusBadge status={tokenStatus.status} size="lg" />
                    <PriorityBadge priority={tokenStatus.priority} />
                  </div>
                </div>

                <div className="text-right">
                  <span className="text-[10px] text-slate-300 uppercase block">Token Number</span>
                  <span className="text-4xl font-black font-mono tracking-tight" data-testid="live-track-token-no">
                    {tokenStatus.tokenNo}
                  </span>
                </div>
              </div>

              {/* Status Message / Notification */}
              {tokenStatus.status === 'CALLED' && (
                <div className="bg-blue-50 dark:bg-blue-950/60 p-4 border-b border-blue-200 dark:border-blue-900 text-blue-900 dark:text-blue-200 flex items-center gap-3">
                  <AlertTriangle className="w-6 h-6 text-blue-600 animate-bounce" />
                  <div>
                    <h4 className="font-black text-sm">YOUR TOKEN IS BEING CALLED!</h4>
                    <p className="text-xs mt-0.5">Please proceed immediately to <strong className="font-bold underline">{tokenStatus.counterName || 'Assigned Counter'}</strong>.</p>
                  </div>
                </div>
              )}

              {/* Body Details */}
              <div className="p-6 sm:p-8 space-y-6">
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                  <div className="p-4 bg-slate-50 dark:bg-slate-900/60 rounded-2xl border border-slate-100 dark:border-slate-700/60">
                    <span className="text-[11px] font-semibold text-slate-400 uppercase block">Queue Position</span>
                    <span className="text-2xl font-black text-slate-900 dark:text-white font-mono mt-1 block" data-testid="track-position">
                      #{tokenStatus.positionInQueue}
                    </span>
                  </div>

                  <div className="p-4 bg-slate-50 dark:bg-slate-900/60 rounded-2xl border border-slate-100 dark:border-slate-700/60">
                    <span className="text-[11px] font-semibold text-slate-400 uppercase block">People Ahead</span>
                    <span className="text-2xl font-black text-slate-900 dark:text-white font-mono mt-1 block" data-testid="track-people-ahead">
                      {tokenStatus.peopleAhead}
                    </span>
                  </div>

                  <div className="p-4 bg-slate-50 dark:bg-slate-900/60 rounded-2xl border border-slate-100 dark:border-slate-700/60">
                    <span className="text-[11px] font-semibold text-slate-400 uppercase block">Est. Wait</span>
                    <span className="text-2xl font-black text-slate-900 dark:text-white font-mono mt-1 block">
                      ~{tokenStatus.estimatedWaitMinutes}m
                    </span>
                  </div>

                  <div className="p-4 bg-slate-50 dark:bg-slate-900/60 rounded-2xl border border-slate-100 dark:border-slate-700/60">
                    <span className="text-[11px] font-semibold text-slate-400 uppercase block">Assigned Desk</span>
                    <span className="text-sm font-bold text-slate-900 dark:text-white mt-2 block truncate">
                      {tokenStatus.counterName || 'Pending'}
                    </span>
                  </div>
                </div>

                {/* Live Session Timer (When SERVING) */}
                {tokenStatus.status === 'SERVING' && tokenStatus.startedAtUtc && (
                  <div className="p-5 rounded-2xl bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-800 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <div>
                      <span className="text-xs font-bold uppercase tracking-wider text-emerald-800 dark:text-emerald-300">
                        Session Active in Progress
                      </span>
                      <p className="text-xs text-emerald-700 dark:text-emerald-400 mt-0.5">
                        Your desk session is currently underway with staff.
                      </p>
                    </div>
                    <LiveTimer
                      startedAtUtc={tokenStatus.startedAtUtc}
                      serverTimeUtc={tokenStatus.serverTimeUtc}
                      label="Active Service Duration"
                      size="lg"
                    />
                  </div>
                )}

                {/* Completed Session Summary */}
                {tokenStatus.status === 'COMPLETED' && (
                  <div className="p-5 rounded-2xl bg-slate-50 dark:bg-slate-900/60 border border-slate-200 dark:border-slate-700/80 space-y-3">
                    <h4 className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                      <CheckCircle className="w-4 h-4 text-emerald-500" />
                      Session Completed Successfully
                    </h4>
                    <div className="grid grid-cols-2 gap-4 text-xs">
                      <div>
                        <span className="text-slate-400 block">Total Wait Duration:</span>
                        <span className="font-bold font-mono text-slate-700 dark:text-slate-300">
                          {Math.floor((tokenStatus.waitDurationSeconds || 0) / 60)}m {(tokenStatus.waitDurationSeconds || 0) % 60}s
                        </span>
                      </div>
                      <div>
                        <span className="text-slate-400 block">Total Service Duration:</span>
                        <span className="font-bold font-mono text-slate-700 dark:text-slate-300" data-testid="completed-service-duration">
                          {Math.floor((tokenStatus.serviceDurationSeconds || 0) / 60)}m {(tokenStatus.serviceDurationSeconds || 0) % 60}s
                        </span>
                      </div>
                    </div>
                  </div>
                )}

                {/* Actions (Cancel button if WAITING) */}
                <div className="pt-4 border-t border-slate-100 dark:border-slate-700/60 flex items-center justify-between">
                  <div className="text-xs text-slate-400">
                    Department: <span className="font-semibold text-slate-700 dark:text-slate-300">{tokenStatus.serviceName}</span>
                  </div>

                  {tokenStatus.canCancel && (
                    <button
                      onClick={handleCancelToken}
                      disabled={cancelling}
                      className="px-4 py-2 rounded-xl border border-rose-200 dark:border-rose-900 text-rose-600 dark:text-rose-400 text-xs font-semibold hover:bg-rose-50 dark:hover:bg-rose-950/40 transition-colors disabled:opacity-50"
                      data-testid="btn-cancel-ticket"
                    >
                      {cancelling ? 'Cancelling...' : 'Cancel My Ticket'}
                    </button>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <EmptyState
              title="No Active Ticket Selected"
              description="Enter your token number and registered mobile number above to track your queue position live."
              action={{
                label: 'Book a New Ticket',
                onClick: () => setActiveTab('book')
              }}
            />
          )}
        </div>
      )}
    </div>
  );
};
