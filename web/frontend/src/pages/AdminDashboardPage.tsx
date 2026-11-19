import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api, getErrorMessage } from '../services/api';
import {
  AnalyticsSummary,
  HourlyStat,
  ServiceStat,
  Service,
  Counter,
  AuthUser,
  CustomerRecord,
  AuditLogItem
} from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { LoadingState, EmptyState, ErrorState } from '../components/common/StateFeedback';
import {
  ShieldCheck,
  TrendingUp,
  Clock,
  UserX,
  FileSpreadsheet,
  Plus,
  Edit2,
  Trash2,
  Users,
  Briefcase,
  Layers,
  History,
  Search,
  CheckCircle,
  Download,
  BarChart3,
  Calendar
} from 'lucide-react';

export const AdminDashboardPage: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  // Redirect if not admin
  useEffect(() => {
    if (!user || user.role !== 'ADMIN') {
      navigate('/admin/login');
    }
  }, [user, navigate]);

  const [activeTab, setActiveTab] = useState<'analytics' | 'services' | 'counters' | 'staff' | 'customers' | 'audit'>('analytics');

  // Analytics Data
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [hourlyStats, setHourlyStats] = useState<HourlyStat[]>([]);
  const [serviceStats, setServiceStats] = useState<ServiceStat[]>([]);

  // Entities Data
  const [services, setServices] = useState<Service[]>([]);
  const [counters, setCounters] = useState<Counter[]>([]);
  const [staffList, setStaffList] = useState<AuthUser[]>([]);
  const [customers, setCustomers] = useState<CustomerRecord[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogItem[]>([]);

  // Loading & Error States
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [panelErrors, setPanelErrors] = useState<Record<string, string | null>>({});
  const [feedbackMsg, setFeedbackMsg] = useState<string | null>(null);

  // Modals State
  const [showServiceModal, setShowServiceModal] = useState<boolean>(false);
  const [editingService, setEditingService] = useState<Partial<Service> | null>(null);

  const [showCounterModal, setShowCounterModal] = useState<boolean>(false);
  const [editingCounter, setEditingCounter] = useState<Partial<Counter> | null>(null);

  const [showStaffModal, setShowStaffModal] = useState<boolean>(false);
  const [newStaff, setNewStaff] = useState({ username: '', name: '', password: '' });

  // Audit Search
  const [auditSearch, setAuditSearch] = useState<string>('');

  // Fetch all admin data
  const fetchAllData = async () => {
    setLoading(true);
    setError(null);
    const newErrors: Record<string, string | null> = {};

    try {
      const [
        sumRes,
        hourRes,
        srvStatRes,
        srvRes,
        cntRes,
        stfRes,
        custRes,
        audRes
      ] = await Promise.allSettled([
        api.getAnalyticsSummary(),
        api.getHourlyStats(),
        api.getServiceStats(),
        api.getAllServices(),
        api.getAllCounters(),
        api.getAllStaff(),
        api.getAllCustomers(),
        api.getAuditLogs()
      ]);

      if (sumRes.status === 'fulfilled') setSummary(sumRes.value);
      else newErrors.analytics = getErrorMessage(sumRes.reason);

      if (hourRes.status === 'fulfilled') setHourlyStats(hourRes.value);
      else newErrors.hourly = getErrorMessage(hourRes.reason);

      if (srvStatRes.status === 'fulfilled') setServiceStats(srvStatRes.value);
      else newErrors.servicesStat = getErrorMessage(srvStatRes.reason);

      if (srvRes.status === 'fulfilled') setServices(srvRes.value);
      else newErrors.services = getErrorMessage(srvRes.reason);

      if (cntRes.status === 'fulfilled') setCounters(cntRes.value);
      else newErrors.counters = getErrorMessage(cntRes.reason);

      if (stfRes.status === 'fulfilled') setStaffList(stfRes.value);
      else newErrors.staff = getErrorMessage(stfRes.reason);

      if (custRes.status === 'fulfilled') setCustomers(custRes.value);
      else newErrors.customers = getErrorMessage(custRes.reason);

      if (audRes.status === 'fulfilled') setAuditLogs(audRes.value);
      else newErrors.audit = getErrorMessage(audRes.reason);

      setPanelErrors(newErrors);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAllData();
  }, []);

  // Save Service
  const handleSaveService = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingService?.name || !editingService?.prefix) return;

    try {
      if (editingService.id) {
        await api.updateService(editingService.id, editingService);
        setFeedbackMsg('Service updated successfully.');
      } else {
        await api.createService(editingService);
        setFeedbackMsg('New service created successfully.');
      }
      setShowServiceModal(false);
      setEditingService(null);
      await fetchAllData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  // Save Counter
  const handleSaveCounter = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingCounter?.name) return;

    try {
      if (editingCounter.id) {
        await api.updateCounter(editingCounter.id, editingCounter);
        setFeedbackMsg('Counter updated successfully.');
      } else {
        await api.createCounter(editingCounter);
        setFeedbackMsg('New counter created successfully.');
      }
      setShowCounterModal(false);
      setEditingCounter(null);
      await fetchAllData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  // Save Staff
  const handleSaveStaff = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newStaff.username || !newStaff.name || !newStaff.password) return;

    try {
      await api.createStaff(newStaff);
      setFeedbackMsg(`Staff account '${newStaff.username}' created successfully.`);
      setShowStaffModal(false);
      setNewStaff({ username: '', name: '', password: '' });
      await fetchAllData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  // CSV Export Download
  const handleExportCsv = () => {
    window.open(api.exportCsvUrl, '_blank');
  };

  const filteredLogs = auditLogs.filter(
    (l) =>
      (l.action || '').toLowerCase().includes(auditSearch.toLowerCase()) ||
      (l.username || '').toLowerCase().includes(auditSearch.toLowerCase()) ||
      (l.details || '').toLowerCase().includes(auditSearch.toLowerCase())
  );

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Top Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <span className="text-xs font-bold uppercase tracking-wider text-purple-600 dark:text-purple-400 block">
            Administrative Control Panel
          </span>
          <h2 className="text-2xl sm:text-3xl font-black text-slate-900 dark:text-white tracking-tight flex items-center gap-2">
            <ShieldCheck className="w-7 h-7 text-purple-600" />
            Executive Administration
          </h2>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleExportCsv}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs shadow-md shadow-emerald-500/20 transition-all"
            data-testid="btn-export-csv"
          >
            <Download className="w-4 h-4" />
            Export CSV Report
          </button>
        </div>
      </div>

      {feedbackMsg && (
        <div className="p-3.5 rounded-2xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 text-emerald-800 dark:text-emerald-300 text-xs font-bold flex items-center justify-between">
          <span>{feedbackMsg}</span>
          <button onClick={() => setFeedbackMsg(null)}>&times;</button>
        </div>
      )}

      {error && <ErrorState message={error} onRetry={fetchAllData} />}

      {/* Navigation Tabs */}
      <div className="flex flex-wrap gap-2 border-b border-slate-200 dark:border-slate-800 pb-2">
        {[
          { id: 'analytics', label: 'KPI Analytics & Charts', icon: BarChart3 },
          { id: 'services', label: 'Services', icon: Layers },
          { id: 'counters', label: 'Counters', icon: Briefcase },
          { id: 'staff', label: 'Staff Accounts', icon: Users },
          { id: 'customers', label: 'Customer Directory', icon: UserX },
          { id: 'audit', label: 'Audit Trail', icon: History }
        ].map((tab) => {
          const Icon = tab.icon;
          const active = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={`inline-flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                active
                  ? 'bg-purple-600 text-white shadow-xs'
                  : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800'
              }`}
              data-testid={`admin-tab-${tab.id}`}
            >
              <Icon className="w-4 h-4" />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* 1. ANALYTICS & CHARTS TAB */}
      {activeTab === 'analytics' && (
        <div className="space-y-8">
          {(panelErrors.analytics || panelErrors.hourly || panelErrors.servicesStat) && (
            <div className="p-3.5 rounded-2xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 text-amber-800 dark:text-amber-300 text-xs font-semibold">
              {panelErrors.analytics || panelErrors.hourly || panelErrors.servicesStat}
            </div>
          )}
          {/* KPI Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
            <div className="bg-white dark:bg-slate-800 p-6 rounded-3xl border border-slate-200 dark:border-slate-700/80 shadow-xs">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">Total Tokens Today</span>
              <div className="text-3xl font-black text-slate-900 dark:text-white font-mono mt-1" data-testid="kpi-total-today">
                {summary?.totalTokensToday ?? 0}
              </div>
              <span className="text-xs text-slate-500 mt-2 block">
                {summary?.completedCount ?? 0} completed &bull; {summary?.waitingCount ?? 0} waiting
              </span>
            </div>

            <div className="bg-white dark:bg-slate-800 p-6 rounded-3xl border border-slate-200 dark:border-slate-700/80 shadow-xs">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">Average Wait Time</span>
              <div className="text-3xl font-black text-blue-600 dark:text-blue-400 font-mono mt-1" data-testid="kpi-avg-wait">
                ~{summary?.avgWaitMinutes ?? 0}m
              </div>
              <span className="text-xs text-slate-500 mt-2 block">Across all active departments</span>
            </div>

            <div className="bg-white dark:bg-slate-800 p-6 rounded-3xl border border-slate-200 dark:border-slate-700/80 shadow-xs">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">Average Service Duration</span>
              <div className="text-3xl font-black text-emerald-600 dark:text-emerald-400 font-mono mt-1" data-testid="kpi-avg-service">
                ~{summary?.avgServiceMinutes ?? 0}m
              </div>
              <span className="text-xs text-slate-500 mt-2 block">Active counter session time</span>
            </div>

            <div className="bg-white dark:bg-slate-800 p-6 rounded-3xl border border-slate-200 dark:border-slate-700/80 shadow-xs">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">No-Show / Skip Rate</span>
              <div className="text-3xl font-black text-rose-600 dark:text-rose-400 font-mono mt-1" data-testid="kpi-no-show-rate">
                {summary?.noShowRate ?? 0}%
              </div>
              <span className="text-xs text-slate-500 mt-2 block">{summary?.skippedCount ?? 0} total skipped tickets</span>
            </div>
          </div>

          {/* Charts Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
            {/* Hourly Traffic Bar Chart */}
            <div className="lg:col-span-7 bg-white dark:bg-slate-800 rounded-3xl p-6 border border-slate-200 dark:border-slate-700/80 shadow-md space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="font-bold text-sm text-slate-900 dark:text-white flex items-center gap-2">
                    <BarChart3 className="w-4 h-4 text-purple-600" />
                    Hourly Token Traffic Distribution
                  </h3>
                  <span className="text-xs text-slate-500">Volume breakdown by operating hour</span>
                </div>
              </div>

              {/* Responsive SVG Bar Chart */}
              <div className="h-64 flex items-end gap-2 pt-6 pb-2 px-2 border-b border-slate-100 dark:border-slate-700">
                {hourlyStats.length === 0 ? (
                  <EmptyState title="No Hourly Data" description="No token activity recorded for today yet." />
                ) : (
                  hourlyStats.map((h) => {
                    const maxVal = Math.max(...hourlyStats.map((x) => x.count), 5);
                    const pct = Math.round((h.count / maxVal) * 100);
                    return (
                      <div key={h.hour} className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end group">
                        <span className="text-[10px] font-bold text-slate-600 dark:text-slate-300 opacity-0 group-hover:opacity-100 transition-opacity">
                          {h.count}
                        </span>
                        <div
                          className="w-full max-w-[28px] bg-purple-600 dark:bg-purple-500 rounded-t-lg transition-all hover:bg-purple-700"
                          style={{ height: `${Math.max(pct, 6)}%` }}
                        />
                        <span className="text-[9px] font-mono text-slate-400">{h.label}</span>
                      </div>
                    );
                  })
                )}
              </div>
            </div>

            {/* Service Volume Breakdown */}
            <div className="lg:col-span-5 bg-white dark:bg-slate-800 rounded-3xl p-6 border border-slate-200 dark:border-slate-700/80 shadow-md space-y-4">
              <h3 className="font-bold text-sm text-slate-900 dark:text-white flex items-center gap-2">
                <Layers className="w-4 h-4 text-blue-600" />
                Service Department Breakdown
              </h3>
              <p className="text-xs text-slate-500">Today&rsquo;s volume and wait metrics per service</p>

              <div className="space-y-3 pt-2">
                {serviceStats.map((srv) => (
                  <div key={srv.serviceId} className="p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-900/60 border border-slate-100 dark:border-slate-700/60 flex items-center justify-between">
                    <div>
                      <span className="font-bold text-xs text-slate-900 dark:text-white block">{srv.serviceName}</span>
                      <span className="text-[10px] text-slate-400">Avg Wait: ~{srv.avgWaitMinutes}m</span>
                    </div>
                    <div className="text-right">
                      <span className="text-sm font-black font-mono text-purple-600 dark:text-purple-400 block">{srv.totalTokens} Tokens</span>
                      <span className="text-[10px] text-emerald-600 font-semibold">{srv.completedTokens} Served</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 2. SERVICES TAB */}
      {activeTab === 'services' && (
        <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 border border-slate-200 dark:border-slate-700/80 shadow-md space-y-4">
          {panelErrors.services && (
            <div className="p-3.5 rounded-2xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 text-amber-800 dark:text-amber-300 text-xs font-semibold">
              {panelErrors.services}
            </div>
          )}
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-base text-slate-900 dark:text-white">Department Services</h3>
            <button
              onClick={() => {
                setEditingService({ name: '', prefix: '', avgServiceTime: 5, active: true });
                setShowServiceModal(true);
              }}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-xs font-bold shadow-xs"
            >
              <Plus className="w-3.5 h-3.5" /> Add Service
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-700 text-slate-400 font-bold uppercase tracking-wider text-[10px]">
                  <th className="py-2.5 px-3">ID</th>
                  <th className="py-2.5 px-3">Service Name</th>
                  <th className="py-2.5 px-3">Prefix</th>
                  <th className="py-2.5 px-3">Avg Service Time</th>
                  <th className="py-2.5 px-3">Status</th>
                  <th className="py-2.5 px-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-medium">
                {services.map((srv) => (
                  <tr key={srv.id} className="hover:bg-slate-50 dark:hover:bg-slate-750">
                    <td className="py-3 px-3 font-mono text-slate-400">{srv.id}</td>
                    <td className="py-3 px-3 font-bold text-slate-900 dark:text-white">{srv.name}</td>
                    <td className="py-3 px-3 font-mono font-bold text-purple-600">{srv.prefix}</td>
                    <td className="py-3 px-3 text-slate-600 dark:text-slate-300 font-mono">~{srv.avgServiceTime} mins</td>
                    <td className="py-3 px-3">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                        srv.active ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-600'
                      }`}>
                        {srv.active ? 'ACTIVE' : 'INACTIVE'}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-right">
                      <button
                        onClick={() => {
                          setEditingService(srv);
                          setShowServiceModal(true);
                        }}
                        className="p-1 rounded-md text-slate-500 hover:text-purple-600"
                        title="Edit Service"
                      >
                        <Edit2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 3. COUNTERS TAB */}
      {activeTab === 'counters' && (
        <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 border border-slate-200 dark:border-slate-700/80 shadow-md space-y-4">
          {panelErrors.counters && (
            <div className="p-3.5 rounded-2xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 text-amber-800 dark:text-amber-300 text-xs font-semibold">
              {panelErrors.counters}
            </div>
          )}
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-base text-slate-900 dark:text-white">Counter Desks</h3>
            <button
              onClick={() => {
                setEditingCounter({ name: '', serviceId: services[0]?.id, status: 'OPEN' });
                setShowCounterModal(true);
              }}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-xs font-bold shadow-xs"
            >
              <Plus className="w-3.5 h-3.5" /> Add Counter
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-700 text-slate-400 font-bold uppercase tracking-wider text-[10px]">
                  <th className="py-2.5 px-3">ID</th>
                  <th className="py-2.5 px-3">Counter Name</th>
                  <th className="py-2.5 px-3">Assigned Service</th>
                  <th className="py-2.5 px-3">Desk Status</th>
                  <th className="py-2.5 px-3">Active Token</th>
                  <th className="py-2.5 px-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-medium">
                {counters.map((c) => (
                  <tr key={c.id} className="hover:bg-slate-50 dark:hover:bg-slate-750">
                    <td className="py-3 px-3 font-mono text-slate-400">{c.id}</td>
                    <td className="py-3 px-3 font-bold text-slate-900 dark:text-white">{c.name}</td>
                    <td className="py-3 px-3 text-slate-600 dark:text-slate-300">
                      {services.find((s) => s.id === c.serviceId)?.name || 'General Desk'}
                    </td>
                    <td className="py-3 px-3">
                      <StatusBadge status={c.status} size="sm" />
                    </td>
                    <td className="py-3 px-3 font-mono font-bold text-blue-600">
                      {c.currentTokenNo || '—'}
                    </td>
                    <td className="py-3 px-3 text-right">
                      <button
                        onClick={() => {
                          setEditingCounter(c);
                          setShowCounterModal(true);
                        }}
                        className="p-1 rounded-md text-slate-500 hover:text-purple-600"
                        title="Edit Counter"
                      >
                        <Edit2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 4. STAFF ACCOUNTS TAB */}
      {activeTab === 'staff' && (
        <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 border border-slate-200 dark:border-slate-700/80 shadow-md space-y-4">
          {panelErrors.staff && (
            <div className="p-3.5 rounded-2xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 text-amber-800 dark:text-amber-300 text-xs font-semibold">
              {panelErrors.staff}
            </div>
          )}
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-base text-slate-900 dark:text-white">Staff User Accounts</h3>
            <button
              onClick={() => setShowStaffModal(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-xs font-bold shadow-xs"
            >
              <Plus className="w-3.5 h-3.5" /> Add Staff Account
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-700 text-slate-400 font-bold uppercase tracking-wider text-[10px]">
                  <th className="py-2.5 px-3">Username</th>
                  <th className="py-2.5 px-3">Full Name</th>
                  <th className="py-2.5 px-3">Role</th>
                  <th className="py-2.5 px-3">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-medium">
                {staffList.map((st) => (
                  <tr key={st.username} className="hover:bg-slate-50 dark:hover:bg-slate-750">
                    <td className="py-3 px-3 font-mono font-bold text-slate-800 dark:text-slate-200">{st.username}</td>
                    <td className="py-3 px-3 text-slate-700 dark:text-slate-300">{st.name}</td>
                    <td className="py-3 px-3">
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-blue-100 text-blue-800 dark:bg-blue-950/60 dark:text-blue-300">
                        {st.role}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-emerald-600 font-bold text-xs">Active</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 5. CUSTOMERS DIRECTORY TAB */}
      {activeTab === 'customers' && (
        <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 border border-slate-200 dark:border-slate-700/80 shadow-md space-y-4">
          {panelErrors.customers && (
            <div className="p-3.5 rounded-2xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 text-amber-800 dark:text-amber-300 text-xs font-semibold">
              {panelErrors.customers}
            </div>
          )}
          <h3 className="font-bold text-base text-slate-900 dark:text-white">Registered Customer Directory</h3>
          <p className="text-xs text-slate-500">Historical directory of customer profiles and total tickets booked</p>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-700 text-slate-400 font-bold uppercase tracking-wider text-[10px]">
                  <th className="py-2.5 px-3">Customer Name</th>
                  <th className="py-2.5 px-3">Mobile Phone</th>
                  <th className="py-2.5 px-3">Registered Since</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-medium">
                {customers.map((c) => (
                  <tr key={c.id} className="hover:bg-slate-50 dark:hover:bg-slate-750">
                    <td className="py-3 px-3 font-bold text-slate-900 dark:text-white">{c.name}</td>
                    <td className="py-3 px-3 font-mono text-slate-600 dark:text-slate-300">{c.phone}</td>
                    <td className="py-3 px-3 text-slate-400 font-mono text-[11px]">{c.createdAt ? new Date(c.createdAt).toLocaleDateString() : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 6. AUDIT TRAIL TAB */}
      {activeTab === 'audit' && (
        <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 border border-slate-200 dark:border-slate-700/80 shadow-md space-y-4">
          {panelErrors.audit && (
            <div className="p-3.5 rounded-2xl bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 text-amber-800 dark:text-amber-300 text-xs font-semibold">
              {panelErrors.audit}
            </div>
          )}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <h3 className="font-bold text-base text-slate-900 dark:text-white">Security &amp; Operational Audit Trail</h3>
              <p className="text-xs text-slate-500">Immutable chronological event logs from MySQL database</p>
            </div>

            <div className="relative">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
              <input
                type="text"
                placeholder="Search audit events..."
                value={auditSearch}
                onChange={(e) => setAuditSearch(e.target.value)}
                className="pl-9 pr-3 py-1.5 rounded-xl border border-slate-200 dark:border-slate-700 text-xs bg-slate-50 dark:bg-slate-900"
              />
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-700 text-slate-400 font-bold uppercase tracking-wider text-[10px]">
                  <th className="py-2.5 px-3">Timestamp</th>
                  <th className="py-2.5 px-3">User</th>
                  <th className="py-2.5 px-3">Action</th>
                  <th className="py-2.5 px-3">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-mono text-[11px]">
                {filteredLogs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-50 dark:hover:bg-slate-750">
                    <td className="py-2.5 px-3 text-slate-400 truncate max-w-[140px]">{log.createdAt}</td>
                    <td className="py-2.5 px-3 font-bold text-purple-600">{log.username || 'SYSTEM'}</td>
                    <td className="py-2.5 px-3 text-slate-800 dark:text-slate-200 font-sans font-bold">{log.action}</td>
                    <td className="py-2.5 px-3 text-slate-500 font-sans truncate max-w-xs">{log.details}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* SERVICE MODAL */}
      {showServiceModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <form onSubmit={handleSaveService} className="bg-white dark:bg-slate-800 rounded-3xl p-6 max-w-md w-full border border-slate-200 dark:border-slate-700 shadow-2xl space-y-4">
            <h3 className="text-base font-bold text-slate-900 dark:text-white">
              {editingService?.id ? 'Edit Department Service' : 'Add Department Service'}
            </h3>
            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold block mb-1">Service Name *</label>
                <input
                  type="text"
                  required
                  value={editingService?.name || ''}
                  onChange={(e) => setEditingService({ ...editingService, name: e.target.value })}
                  className="w-full p-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900"
                />
              </div>
              <div>
                <label className="font-bold block mb-1">Prefix (Max 2 chars) *</label>
                <input
                  type="text"
                  required
                  maxLength={2}
                  value={editingService?.prefix || ''}
                  onChange={(e) => setEditingService({ ...editingService, prefix: e.target.value.toUpperCase() })}
                  className="w-full p-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 font-mono"
                />
              </div>
              <div>
                <label className="font-bold block mb-1">Avg Service Time (mins) *</label>
                <input
                  type="number"
                  required
                  min={1}
                  value={editingService?.avgServiceTime || 5}
                  onChange={(e) => setEditingService({ ...editingService, avgServiceTime: Number(e.target.value) })}
                  className="w-full p-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 font-mono"
                />
              </div>
            </div>
            <div className="flex gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowServiceModal(false)}
                className="flex-1 py-2 rounded-xl border border-slate-200 text-xs font-semibold"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="flex-1 py-2 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-xs font-bold"
              >
                Save Service
              </button>
            </div>
          </form>
        </div>
      )}

      {/* COUNTER MODAL */}
      {showCounterModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <form onSubmit={handleSaveCounter} className="bg-white dark:bg-slate-800 rounded-3xl p-6 max-w-md w-full border border-slate-200 dark:border-slate-700 shadow-2xl space-y-4">
            <h3 className="text-base font-bold text-slate-900 dark:text-white">
              {editingCounter?.id ? 'Edit Desk Counter' : 'Add Desk Counter'}
            </h3>
            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold block mb-1">Counter Name *</label>
                <input
                  type="text"
                  required
                  value={editingCounter?.name || ''}
                  onChange={(e) => setEditingCounter({ ...editingCounter, name: e.target.value })}
                  className="w-full p-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900"
                />
              </div>
              <div>
                <label className="font-bold block mb-1">Assigned Service</label>
                <select
                  value={editingCounter?.serviceId || ''}
                  onChange={(e) => setEditingCounter({ ...editingCounter, serviceId: Number(e.target.value) })}
                  className="w-full p-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900"
                >
                  {services.map((s) => (
                    <option key={s.id} value={s.id}>{s.name} ({s.prefix})</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="flex gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowCounterModal(false)}
                className="flex-1 py-2 rounded-xl border border-slate-200 text-xs font-semibold"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="flex-1 py-2 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-xs font-bold"
              >
                Save Counter
              </button>
            </div>
          </form>
        </div>
      )}

      {/* STAFF MODAL */}
      {showStaffModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <form onSubmit={handleSaveStaff} className="bg-white dark:bg-slate-800 rounded-3xl p-6 max-w-md w-full border border-slate-200 dark:border-slate-700 shadow-2xl space-y-4">
            <h3 className="text-base font-bold text-slate-900 dark:text-white">Create Staff Account</h3>
            <div className="space-y-3 text-xs">
              <div>
                <label className="font-bold block mb-1">Username *</label>
                <input
                  type="text"
                  required
                  value={newStaff.username}
                  onChange={(e) => setNewStaff({ ...newStaff, username: e.target.value })}
                  placeholder="e.g. staff3"
                  className="w-full p-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 font-mono"
                />
              </div>
              <div>
                <label className="font-bold block mb-1">Full Name *</label>
                <input
                  type="text"
                  required
                  value={newStaff.name}
                  onChange={(e) => setNewStaff({ ...newStaff, name: e.target.value })}
                  placeholder="e.g. Alice Smith"
                  className="w-full p-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900"
                />
              </div>
              <div>
                <label className="font-bold block mb-1">Initial Password *</label>
                <input
                  type="password"
                  required
                  value={newStaff.password}
                  onChange={(e) => setNewStaff({ ...newStaff, password: e.target.value })}
                  placeholder="••••••••"
                  className="w-full p-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900"
                />
              </div>
            </div>
            <div className="flex gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowStaffModal(false)}
                className="flex-1 py-2 rounded-xl border border-slate-200 text-xs font-semibold"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="flex-1 py-2 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-xs font-bold"
              >
                Create Account
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
