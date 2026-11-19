import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api, getErrorMessage } from '../services/api';
import { Counter, QueueToken, Service, CounterStatus } from '../types';
import { wsService } from '../services/websocket';
import { StatusBadge } from '../components/common/StatusBadge';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { LiveTimer } from '../components/common/LiveTimer';
import { LoadingState, EmptyState, ErrorState } from '../components/common/StateFeedback';
import {
  Briefcase,
  PhoneCall,
  Play,
  CheckCircle2,
  FastForward,
  RotateCcw,
  ArrowRightLeft,
  Power,
  Search,
  Filter,
  RefreshCw,
  BellRing,
  Clock,
  User,
  Users,
  ShieldCheck,
  AlertCircle
} from 'lucide-react';

export const StaffDashboardPage: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  // Redirect if not authenticated as staff
  useEffect(() => {
    if (!user || (user.role !== 'STAFF' && user.role !== 'ADMIN')) {
      navigate('/staff/login');
    }
  }, [user, navigate]);

  // Counters & Selected Desk
  const [counters, setCounters] = useState<Counter[]>([]);
  const [selectedCounterId, setSelectedCounterId] = useState<number>(1);
  const [loadingCounters, setLoadingCounters] = useState<boolean>(true);
  const [counterStatus, setCounterStatus] = useState<CounterStatus>('OPEN');
  const [autoCall, setAutoCall] = useState<boolean>(false);

  // Active Token at this desk
  const [activeToken, setActiveToken] = useState<QueueToken | null>(null);

  // Waiting Queue
  const [queue, setQueue] = useState<QueueToken[]>([]);
  const [services, setServices] = useState<Service[]>([]);
  const [loadingQueue, setLoadingQueue] = useState<boolean>(true);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [priorityFilter, setPriorityFilter] = useState<string>('ALL');

  // Operation In-Progress State
  const [actionLoading, setActionLoading] = useState<boolean>(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Transfer Modal State
  const [showTransferModal, setShowTransferModal] = useState<boolean>(false);
  const [transferTargetCounterId, setTransferTargetCounterId] = useState<number | null>(null);
  const [transferBtnStatus, setTransferBtnStatus] = useState<{
    state: 'idle' | 'loading' | 'success' | 'error';
    message?: string;
  }>({ state: 'idle' });

  // Load Counters & Services
  const loadInitialData = async () => {
    setLoadingCounters(true);
    try {
      const [cntList, srvList] = await Promise.all([
        api.getStaffCounters(),
        api.getActiveServices()
      ]);
      setCounters(cntList);
      setServices(srvList);

      if (cntList.length > 0) {
        const first = cntList[0];
        setSelectedCounterId(first.id);
        setCounterStatus(first.status);
      }
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setLoadingCounters(false);
    }
  };

  useEffect(() => {
    loadInitialData();
  }, []);

  // Fetch queue and active token for current selected counter
  const refreshQueueAndDesk = async () => {
    if (!selectedCounterId) return;
    try {
      const currentCounter = counters.find((c) => c.id === selectedCounterId);
      const srvId = currentCounter?.serviceId;

      const [qList, cntList] = await Promise.all([
        api.getStaffQueue(srvId, selectedCounterId),
        api.getStaffCounters()
      ]);

      setQueue(qList);
      setCounters(cntList);

      const updatedCounter = cntList.find((c) => c.id === selectedCounterId);
      if (updatedCounter) {
        setCounterStatus(updatedCounter.status);
        if (updatedCounter.currentTokenId) {
          // If activeToken already has this id, keep it
          setActiveToken((prev) => {
            if (prev && prev.id === updatedCounter.currentTokenId) {
              return prev;
            }
            const foundInQueue = qList.find((t) => t.id === updatedCounter.currentTokenId);
            if (foundInQueue) return foundInQueue;
            return prev;
          });
        } else {
          setActiveToken(null);
        }
      }
    } catch (err) {
      console.warn('Queue refresh error:', err);
    } finally {
      setLoadingQueue(false);
    }
  };

  useEffect(() => {
    refreshQueueAndDesk();
    const interval = setInterval(refreshQueueAndDesk, 4000);

    // STOMP WebSocket subscriber
    const unsubscribeDisplay = wsService.subscribe('/topic/display', refreshQueueAndDesk);

    return () => {
      clearInterval(interval);
      unsubscribeDisplay();
    };
  }, [selectedCounterId, counters.length]);

  // Handle Counter Status Change
  const handleStatusChange = async (newStatus: CounterStatus) => {
    setActionLoading(true);
    setActionError(null);
    try {
      await api.updateCounterStatus(selectedCounterId, newStatus);
      setCounterStatus(newStatus);
      await refreshQueueAndDesk();
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setActionLoading(false);
    }
  };

  // Handle Auto-Call Toggle
  const handleAutoCallToggle = async () => {
    const nextVal = !autoCall;
    setAutoCall(nextVal);
    try {
      await api.toggleAutoCall(selectedCounterId, nextVal);
    } catch (err) {
      console.warn('Auto call toggle sync issue:', err);
    }
  };

  // Action: Call Next Token (Single call path, double-click safe)
  const handleCallNext = async () => {
    setActionLoading(true);
    setActionError(null);
    setActionMessage(null);
    try {
      const token = await api.callNext(selectedCounterId);
      setActiveToken(token);
      setActionMessage(`Called token ${token.tokenNo} successfully!`);
      await refreshQueueAndDesk();
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setActionLoading(false);
    }
  };

  // Action: Start Serving
  const handleStartServing = async () => {
    if (!activeToken) return;
    setActionLoading(true);
    setActionError(null);
    try {
      const token = await api.startServing(activeToken.id, selectedCounterId);
      setActiveToken(token);
      setActionMessage(`Service session started for token ${token.tokenNo}`);
      await refreshQueueAndDesk();
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setActionLoading(false);
    }
  };

  // Action: Complete Token
  const handleCompleteToken = async () => {
    if (!activeToken) return;
    setActionLoading(true);
    setActionError(null);
    try {
      await api.completeToken(activeToken.id, selectedCounterId);
      setActionMessage(`Token ${activeToken.tokenNo} completed! Desk is now OPEN.`);
      setActiveToken(null);
      await refreshQueueAndDesk();
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setActionLoading(false);
    }
  };

  // Action: Skip Token
  const handleSkipToken = async () => {
    if (!activeToken) return;
    setActionLoading(true);
    setActionError(null);
    try {
      await api.skipToken(activeToken.id, selectedCounterId);
      setActionMessage(`Token ${activeToken.tokenNo} marked as skipped.`);
      setActiveToken(null);
      await refreshQueueAndDesk();
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setActionLoading(false);
    }
  };

  // Action: Recall Token (allowed once)
  const handleRecallToken = async (tokenId: number) => {
    setActionLoading(true);
    setActionError(null);
    try {
      const token = await api.recallToken(tokenId, selectedCounterId);
      setActiveToken(token);
      setActionMessage(`Token ${token.tokenNo} recalled to Desk.`);
      await refreshQueueAndDesk();
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setActionLoading(false);
    }
  };

  // Action: Transfer Token to another counter / department
  const handleTransferToken = async () => {
    if (!activeToken || !transferTargetCounterId) return;

    if (transferTargetCounterId === selectedCounterId) {
      const msg = 'Cannot transfer to the same counter';
      setTransferBtnStatus({ state: 'error', message: msg });
      setActionError(msg);
      return;
    }

    const targetCounter = counters.find((c) => c.id === transferTargetCounterId);
    if (targetCounter?.status === 'CLOSED') {
      const msg = 'Cannot transfer to a closed counter';
      setTransferBtnStatus({ state: 'error', message: msg });
      setActionError(msg);
      return;
    }

    setTransferBtnStatus({ state: 'loading' });
    setActionLoading(true);
    setActionError(null);

    try {
      await api.transferToken(activeToken.id, {
        targetCounterId: transferTargetCounterId,
        targetServiceId: targetCounter?.serviceId,
        sourceCounterId: selectedCounterId
      });

      const successMsg = `Transferred to ${targetCounter?.name || 'desk'}`;
      setTransferBtnStatus({ state: 'success', message: successMsg });
      setActionMessage(`Token ${activeToken.tokenNo} transferred to ${targetCounter?.name || 'desk'}.`);
      setActiveToken(null);

      await refreshQueueAndDesk();

      setTimeout(() => {
        setShowTransferModal(false);
      }, 700);

      setTimeout(() => {
        setTransferBtnStatus({ state: 'idle' });
      }, 3000);
    } catch (err) {
      const errorMsg = getErrorMessage(err);
      setTransferBtnStatus({ state: 'error', message: errorMsg });
      setActionError(errorMsg);
    } finally {
      setActionLoading(false);
    }
  };

  // Filtered Queue
  const filteredQueue = queue.filter((t) => {
    const matchesSearch =
      t.tokenNo.toLowerCase().includes(searchQuery.toLowerCase()) ||
      t.customerName.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesPriority =
      priorityFilter === 'ALL' || t.priority === priorityFilter;
    return matchesSearch && matchesPriority;
  });

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Top Bar: Counter Selector, Status Toggles, and Auto-Call */}
      <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 border border-slate-200 dark:border-slate-700/80 shadow-sm flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
        <div className="flex flex-wrap items-center gap-4">
          <div className="w-12 h-12 rounded-2xl bg-emerald-600 text-white flex items-center justify-center shadow-md shadow-emerald-500/20">
            <Briefcase className="w-6 h-6" />
          </div>
          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">
              Staff Desk Station &bull; Active Officer: {user?.name || user?.username}
            </span>
            <div className="flex items-center gap-2 mt-0.5">
              <label htmlFor="counter-select" className="text-sm font-bold text-slate-700 dark:text-slate-300">
                Desk:
              </label>
              <select
                id="counter-select"
                value={selectedCounterId}
                onChange={(e) => setSelectedCounterId(Number(e.target.value))}
                className="font-black text-base text-slate-900 dark:text-white bg-slate-100 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded-xl px-3 py-1.5 focus:border-emerald-600"
                data-testid="select-counter"
              >
                {counters.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} ({c.status})
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {/* Status Pills & Auto-call */}
        <div className="flex flex-wrap items-center gap-4">
          {/* Counter Status Controls */}
          <div className="flex items-center bg-slate-100 dark:bg-slate-900 p-1 rounded-2xl border border-slate-200 dark:border-slate-700">
            <button
              onClick={() => handleStatusChange('OPEN')}
              disabled={actionLoading}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
                counterStatus === 'OPEN'
                  ? 'bg-emerald-600 text-white shadow-xs'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
              }`}
              data-testid="status-btn-open"
            >
              OPEN
            </button>
            <button
              onClick={() => handleStatusChange('BUSY')}
              disabled={actionLoading}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
                counterStatus === 'BUSY'
                  ? 'bg-purple-600 text-white shadow-xs'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
              }`}
              data-testid="status-btn-busy"
            >
              BUSY
            </button>
            <button
              onClick={() => handleStatusChange('CLOSED')}
              disabled={actionLoading}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
                counterStatus === 'CLOSED'
                  ? 'bg-rose-600 text-white shadow-xs'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
              }`}
              data-testid="status-btn-closed"
            >
              CLOSED
            </button>
          </div>

          {/* Auto-Call Switch */}
          <div className="flex items-center gap-2 pl-2 border-l border-slate-200 dark:border-slate-700">
            <button
              onClick={handleAutoCallToggle}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 border transition-all ${
                autoCall
                  ? 'bg-blue-600 text-white border-blue-600 shadow-xs'
                  : 'bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-300 border-slate-300 dark:border-slate-700'
              }`}
              title="Automatically calls next token once current session finishes"
              data-testid="toggle-auto-call"
            >
              <Power className="w-3.5 h-3.5" />
              Auto-Call: {autoCall ? 'ON' : 'OFF'}
            </button>
          </div>
        </div>
      </div>

      {/* Action Messages & Alerts */}
      {actionMessage && (
        <div className="p-3.5 rounded-2xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-300 text-xs font-bold flex items-center justify-between">
          <span>{actionMessage}</span>
          <button onClick={() => setActionMessage(null)} className="text-emerald-600 hover:text-emerald-900">&times;</button>
        </div>
      )}

      {actionError && <ErrorState message={actionError} onRetry={refreshQueueAndDesk} />}

      {/* Grid: Left Column (Active Serving Card & Actions) | Right Column (Live Queue) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Left Column: Active Desk Workstation (5 cols) */}
        <div className="lg:col-span-5 space-y-6">
          <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 sm:p-7 border-2 border-slate-200 dark:border-slate-700 shadow-lg space-y-6">
            <div className="flex items-center justify-between">
              <h3 className="font-black text-lg text-slate-900 dark:text-white tracking-tight flex items-center gap-2">
                <BellRing className="w-5 h-5 text-emerald-600" />
                Current Serving Desk
              </h3>
              <StatusBadge status={counterStatus} />
            </div>

            {/* Active Token Display */}
            {activeToken ? (
              <div className="bg-slate-50 dark:bg-slate-900/60 rounded-2xl p-6 border border-slate-200 dark:border-slate-700/80 space-y-5 text-center">
                <div>
                  <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">
                    Now Serving Token
                  </span>
                  <div className="text-5xl font-black text-emerald-600 dark:text-emerald-400 font-mono tracking-tight mt-1" data-testid="staff-active-token-no">
                    {activeToken.tokenNo}
                  </div>
                </div>

                <div className="flex items-center justify-center gap-2">
                  <StatusBadge status={activeToken.status} size="md" />
                  <PriorityBadge priority={activeToken.priority} isStarving={activeToken.isStarving} />
                </div>

                <div className="text-left bg-white dark:bg-slate-800 p-4 rounded-xl border border-slate-200 dark:border-slate-700 text-xs space-y-2">
                  <div className="flex justify-between">
                    <span className="text-slate-400">Customer Name:</span>
                    <span className="font-bold text-slate-800 dark:text-slate-200">{activeToken.customerName}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">Service:</span>
                    <span className="font-bold text-slate-800 dark:text-slate-200">{activeToken.serviceName}</span>
                  </div>
                  {activeToken.recallCount !== undefined && activeToken.recallCount > 0 && (
                    <div className="flex justify-between text-orange-600 dark:text-orange-400 font-semibold">
                      <span>Recall Status:</span>
                      <span>Recalled ({activeToken.recallCount}/1)</span>
                    </div>
                  )}
                </div>

                {/* Session Live Timer */}
                {activeToken.status === 'SERVING' ? (
                  <div className="pt-2">
                    <LiveTimer
                      startedAtUtc={activeToken.startedAtUtc || activeToken.startedAt || activeToken.calledAtUtc || activeToken.calledAt}
                      serverTimeUtc={activeToken.serverTimeUtc}
                      label="Service Duration"
                      size="lg"
                    />
                  </div>
                ) : (
                  <p className="text-xs text-blue-600 dark:text-blue-400 font-semibold animate-pulse">
                    Token is CALLED. Customer is walking to desk. Click &ldquo;Start Session&rdquo; once customer arrives.
                  </p>
                )}

                {/* Desk Operations Buttons */}
                <div className="grid grid-cols-2 gap-2.5 pt-2">
                  {activeToken.status === 'CALLED' ? (
                    <button
                      onClick={handleStartServing}
                      disabled={actionLoading}
                      className="col-span-2 py-3 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-sm shadow-md shadow-emerald-500/25 flex items-center justify-center gap-2"
                      data-testid="btn-start-session"
                    >
                      <Play className="w-4 h-4" /> Start Session
                    </button>
                  ) : (
                    <button
                      onClick={handleCompleteToken}
                      disabled={actionLoading}
                      className="col-span-2 py-3 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-sm shadow-md shadow-emerald-500/25 flex items-center justify-center gap-2"
                      data-testid="btn-complete-token"
                    >
                      <CheckCircle2 className="w-4 h-4" /> Complete Session
                    </button>
                  )}

                  <button
                    onClick={handleSkipToken}
                    disabled={actionLoading}
                    className="py-2.5 rounded-xl border border-orange-200 dark:border-orange-900 bg-orange-50 dark:bg-orange-950/40 text-orange-700 dark:text-orange-300 font-bold text-xs hover:bg-orange-100 flex items-center justify-center gap-1.5"
                    data-testid="btn-skip-token"
                  >
                    <FastForward className="w-3.5 h-3.5" /> Skip
                  </button>

                  <button
                    onClick={() => handleRecallToken(activeToken.id)}
                    disabled={actionLoading || (activeToken.recallCount !== undefined && activeToken.recallCount >= 1)}
                    className="py-2.5 rounded-xl border border-blue-200 dark:border-blue-900 bg-blue-50 dark:bg-blue-950/40 text-blue-700 dark:text-blue-300 font-bold text-xs hover:bg-blue-100 flex items-center justify-center gap-1.5 disabled:opacity-40"
                    title={activeToken.recallCount ? 'Can only recall once' : 'Recall customer'}
                    data-testid="btn-recall-token"
                  >
                    <RotateCcw className="w-3.5 h-3.5" /> Recall (1x)
                  </button>

                  <button
                    onClick={() => {
                      setTransferBtnStatus({ state: 'idle' });
                      setShowTransferModal(true);
                    }}
                    disabled={actionLoading}
                    className={`col-span-2 py-2 rounded-xl border text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors ${
                      transferBtnStatus.state === 'success'
                        ? 'border-emerald-500 bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300'
                        : transferBtnStatus.state === 'error'
                        ? 'border-rose-500 bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300'
                        : 'border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-750'
                    }`}
                    data-testid="btn-transfer-token"
                  >
                    <ArrowRightLeft className="w-3.5 h-3.5" />
                    {transferBtnStatus.state === 'success'
                      ? `✓ ${transferBtnStatus.message}`
                      : transferBtnStatus.state === 'error'
                      ? `✕ ${transferBtnStatus.message}`
                      : 'Transfer to Another Counter'}
                  </button>
                </div>
              </div>
            ) : (
              /* No Active Token -> Big Call Next Button */
              <div className="py-12 px-4 text-center space-y-4 bg-slate-50/60 dark:bg-slate-900/40 rounded-2xl border border-dashed border-slate-200 dark:border-slate-700">
                <div className="w-16 h-16 rounded-full bg-emerald-100 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400 flex items-center justify-center mx-auto">
                  <PhoneCall className="w-8 h-8" />
                </div>
                <div>
                  <h4 className="text-base font-bold text-slate-800 dark:text-slate-200">Desk is Idle &amp; Ready</h4>
                  <p className="text-xs text-slate-500 max-w-xs mx-auto mt-1">
                    Press Call Next to claim the next waiting ticket according to Emergency &gt; Senior &gt; Normal priority rules.
                  </p>
                </div>
                <button
                  onClick={handleCallNext}
                  disabled={actionLoading}
                  className="px-8 py-4 rounded-2xl bg-emerald-600 hover:bg-emerald-700 text-white font-black text-base shadow-xl shadow-emerald-500/30 hover:scale-102 active:scale-98 transition-all inline-flex items-center gap-2.5"
                  data-testid="btn-call-next"
                >
                  <PhoneCall className="w-5 h-5" />
                  {actionLoading ? 'Claiming Ticket...' : 'CALL NEXT TOKEN'}
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Live Queue Table & Filters (7 cols) */}
        <div className="lg:col-span-7 space-y-4">
          <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 border border-slate-200 dark:border-slate-700/80 shadow-md space-y-5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h3 className="text-lg font-black text-slate-900 dark:text-white tracking-tight flex items-center gap-2">
                  <Users className="w-5 h-5 text-blue-600" />
                  Waiting Priority Queue ({queue.filter(t => t.status === 'WAITING').length})
                </h3>
                <span className="text-xs text-slate-500 dark:text-slate-400">
                  Prioritized: Emergency &gt; Senior &gt; Normal (&gt;30m starvation auto-promoted)
                </span>
              </div>

              <button
                onClick={refreshQueueAndDesk}
                className="p-2 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-750 self-start sm:self-auto"
                title="Refresh Queue"
              >
                <RefreshCw className="w-4 h-4" />
              </button>
            </div>

            {/* Search and Priority Filter */}
            <div className="grid grid-cols-1 sm:grid-cols-12 gap-3">
              <div className="sm:col-span-8 relative">
                <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                <input
                  type="text"
                  placeholder="Filter by token no or customer name..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full pl-10 pr-4 py-2 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 text-xs focus:border-blue-600"
                />
              </div>

              <div className="sm:col-span-4">
                <select
                  value={priorityFilter}
                  onChange={(e) => setPriorityFilter(e.target.value)}
                  className="w-full py-2 px-3 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 text-xs font-semibold"
                >
                  <option value="ALL">All Categories</option>
                  <option value="EMERGENCY">Emergency Only</option>
                  <option value="SENIOR">Senior Citizen</option>
                  <option value="NORMAL">Normal</option>
                </select>
              </div>
            </div>

            {/* Queue Table */}
            {loadingQueue ? (
              <LoadingState message="Loading live queue..." rows={4} />
            ) : filteredQueue.length === 0 ? (
              <EmptyState
                title="Queue is Empty"
                description="There are currently no waiting tickets for this service department."
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-700 text-slate-400 font-bold uppercase tracking-wider text-[10px]">
                      <th className="py-2.5 px-3">Token</th>
                      <th className="py-2.5 px-3">Customer</th>
                      <th className="py-2.5 px-3">Category</th>
                      <th className="py-2.5 px-3">Status</th>
                      <th className="py-2.5 px-3">Wait Time</th>
                      <th className="py-2.5 px-3 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-medium">
                    {filteredQueue.map((t) => (
                      <tr
                        key={t.id}
                        className={`hover:bg-slate-50/80 dark:hover:bg-slate-750 transition-colors ${
                          t.priority === 'EMERGENCY'
                            ? 'bg-red-50/30 dark:bg-red-950/20'
                            : t.priority === 'SENIOR'
                            ? 'bg-amber-50/20 dark:bg-amber-950/10'
                            : ''
                        }`}
                      >
                        <td className="py-3 px-3 font-mono font-black text-sm text-blue-600 dark:text-blue-400">
                          {t.tokenNo}
                        </td>
                        <td className="py-3 px-3 text-slate-800 dark:text-slate-200 font-semibold truncate max-w-[120px]">
                          {t.customerName}
                        </td>
                        <td className="py-3 px-3">
                          <PriorityBadge priority={t.priority} isStarving={t.isStarving} />
                        </td>
                        <td className="py-3 px-3">
                          <StatusBadge status={t.status} size="sm" />
                        </td>
                        <td className="py-3 px-3 text-slate-500 font-mono text-[11px]">
                          {t.waitMinutes ? `${t.waitMinutes}m` : '<1m'}
                        </td>
                        <td className="py-3 px-3 text-right">
                          {t.status === 'SKIPPED' && (
                            <button
                              onClick={() => handleRecallToken(t.id)}
                              disabled={actionLoading || (t.recallCount !== undefined && t.recallCount >= 1)}
                              className="px-2.5 py-1 rounded-lg text-[10px] font-bold bg-blue-50 text-blue-700 hover:bg-blue-100 dark:bg-blue-950/60 dark:text-blue-300 disabled:opacity-40"
                              title={t.recallCount ? 'Already recalled' : 'Recall to desk'}
                            >
                              Recall
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Transfer Modal */}
      {showTransferModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4">
          <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 max-w-md w-full border border-slate-200 dark:border-slate-700 shadow-2xl space-y-4">
            <h3 className="text-lg font-black text-slate-900 dark:text-white flex items-center gap-2">
              <ArrowRightLeft className="w-5 h-5 text-blue-600" />
              Transfer Token {activeToken?.tokenNo}
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Select the destination desk to transfer this customer&rsquo;s ticket to. The token keeps its number and enters the target desk&rsquo;s queue as WAITING.
            </p>

            <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
              {counters.map((c) => {
                const isCurrent = c.id === selectedCounterId;
                const isClosed = c.status === 'CLOSED';
                const isBlocked = isCurrent || isClosed;
                const isSelected = transferTargetCounterId === c.id;
                const svc = services.find((s) => s.id === c.serviceId);

                return (
                  <label
                    key={c.id}
                    className={`p-3 rounded-xl border flex items-center justify-between text-xs font-bold transition-colors ${
                      isBlocked
                        ? 'opacity-50 cursor-not-allowed bg-slate-50 dark:bg-slate-900 border-slate-200 dark:border-slate-700'
                        : isSelected
                        ? 'border-blue-600 bg-blue-50 dark:bg-blue-950/40 text-blue-600 cursor-pointer'
                        : 'border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:border-slate-400 cursor-pointer'
                    }`}
                  >
                    <div>
                      <div className="font-bold">{c.name}</div>
                      <div className="text-[10px] text-slate-400 font-normal">
                        {svc ? `${svc.name} (${svc.prefix})` : 'General Service'} &bull; Status: {c.status}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      {isCurrent && (
                        <span className="text-[10px] font-bold text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-950/60 px-2 py-0.5 rounded-md">
                          Same Desk
                        </span>
                      )}
                      {isClosed && (
                        <span className="text-[10px] font-bold text-rose-600 dark:text-rose-400 bg-rose-50 dark:bg-rose-950/60 px-2 py-0.5 rounded-md">
                          Closed
                        </span>
                      )}
                      <input
                        type="radio"
                        name="targetCounter"
                        disabled={isBlocked}
                        checked={isSelected}
                        onChange={() => {
                          if (!isBlocked) {
                            setTransferTargetCounterId(c.id);
                            setTransferBtnStatus({ state: 'idle' });
                          }
                        }}
                      />
                    </div>
                  </label>
                );
              })}
            </div>

            <div className="flex gap-3 pt-2">
              <button
                onClick={() => {
                  setShowTransferModal(false);
                  setTransferBtnStatus({ state: 'idle' });
                }}
                className="flex-1 py-2.5 rounded-xl border border-slate-200 text-xs font-semibold text-slate-600 dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                onClick={handleTransferToken}
                disabled={!transferTargetCounterId || transferBtnStatus.state === 'loading'}
                className={`flex-1 py-2.5 rounded-xl text-white text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
                  transferBtnStatus.state === 'success'
                    ? 'bg-emerald-600'
                    : transferBtnStatus.state === 'error'
                    ? 'bg-rose-600'
                    : 'bg-blue-600 hover:bg-blue-700 disabled:opacity-50'
                }`}
                data-testid="btn-confirm-transfer"
              >
                {transferBtnStatus.state === 'loading' ? (
                  <>Transferring...</>
                ) : transferBtnStatus.state === 'success' ? (
                  <>✓ {transferBtnStatus.message || 'Transferred!'}</>
                ) : transferBtnStatus.state === 'error' ? (
                  <>✕ {transferBtnStatus.message || 'Transfer Failed'}</>
                ) : (
                  <>Confirm Transfer</>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
