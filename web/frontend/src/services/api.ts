import axios, { AxiosError } from 'axios';
import {
  BookTokenRequest,
  BookTokenResponse,
  TokenStatusResponse,
  PublicStats,
  DisplayBoardData,
  Service,
  Counter,
  QueueToken,
  AnalyticsSummary,
  HourlyStat,
  ServiceStat,
  AuditLogItem,
  CustomerRecord,
  AuthUser
} from '../types';

export const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Friendly error extractor
export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const err = error as AxiosError<{ message?: string; error?: string }>;
    if (err.response?.data?.message) return err.response.data.message;
    if (err.response?.data?.error) return err.response.data.error;
    if (err.response?.status === 401) return 'Session expired or unauthenticated. Please log in.';
    if (err.response?.status === 403) return 'Access denied. You do not have permission for this action.';
    if (err.response?.status === 404) return 'Requested resource was not found.';
    if (err.message) return err.message;
  }
  return 'An unexpected error occurred. Please try again.';
}

export const api = {
  // Public Display & Landing
  getPublicStats: async (): Promise<PublicStats> => {
    const res = await apiClient.get<PublicStats>('/display/stats');
    return res.data;
  },
  getDisplayBoard: async (): Promise<DisplayBoardData> => {
    const res = await apiClient.get<DisplayBoardData>('/display/board');
    return res.data;
  },

  // Customer APIs
  getActiveServices: async (): Promise<Service[]> => {
    const res = await apiClient.get<Service[]>('/customer/services');
    return res.data;
  },
  bookToken: async (req: BookTokenRequest): Promise<BookTokenResponse> => {
    const res = await apiClient.post<BookTokenResponse>('/customer/book', req);
    return res.data;
  },
  trackSession: async (tokenNo: string, phone: string): Promise<{ trackingCode: string }> => {
    const res = await apiClient.post<{ trackingCode: string }>('/customer/track-session', { tokenNo, phone });
    return res.data;
  },
  trackToken: async (trackingCode: string): Promise<TokenStatusResponse> => {
    const res = await apiClient.get<TokenStatusResponse>(`/customer/track/${trackingCode}`);
    return res.data;
  },
  cancelToken: async (trackingCode: string): Promise<{ success: boolean; message: string }> => {
    const res = await apiClient.post<{ success: boolean; message: string }>(`/customer/cancel/${trackingCode}`);
    return res.data;
  },

  // Auth APIs
  staffLogin: async (username: string, password: string): Promise<AuthUser> => {
    const res = await apiClient.post<AuthUser>('/auth/staff/login', { username, password });
    return res.data;
  },
  adminLogin: async (username: string, password: string): Promise<AuthUser> => {
    const res = await apiClient.post<AuthUser>('/auth/admin/login', { username, password });
    return res.data;
  },
  logout: async (): Promise<void> => {
    await apiClient.post('/auth/logout');
  },
  getCurrentUser: async (): Promise<AuthUser> => {
    const res = await apiClient.get<AuthUser>('/auth/me');
    return res.data;
  },

  // Staff APIs
  getStaffCounters: async (): Promise<Counter[]> => {
    const res = await apiClient.get<Counter[]>('/staff/counters');
    return res.data;
  },
  updateCounterStatus: async (counterId: number, status: 'OPEN' | 'BUSY' | 'CLOSED'): Promise<any> => {
    const res = await apiClient.post(`/staff/counters/${counterId}/status`, { status });
    return res.data;
  },
  getStaffQueue: async (serviceId?: number): Promise<QueueToken[]> => {
    const url = serviceId ? `/staff/queue?serviceId=${serviceId}` : '/staff/queue';
    const res = await apiClient.get<QueueToken[]>(url);
    return res.data;
  },
  callNext: async (counterId: number): Promise<QueueToken> => {
    const res = await apiClient.post<QueueToken>(`/staff/counters/${counterId}/call-next`);
    return res.data;
  },
  startServing: async (tokenId: number, counterId?: number): Promise<QueueToken> => {
    const url = counterId ? `/staff/tokens/${tokenId}/start?counterId=${counterId}` : `/staff/tokens/${tokenId}/start`;
    const res = await apiClient.post<QueueToken>(url);
    return res.data;
  },
  completeToken: async (tokenId: number, counterId?: number): Promise<QueueToken> => {
    const url = counterId ? `/staff/tokens/${tokenId}/complete?counterId=${counterId}` : `/staff/tokens/${tokenId}/complete`;
    const res = await apiClient.post<QueueToken>(url);
    return res.data;
  },
  skipToken: async (tokenId: number, counterId?: number): Promise<QueueToken> => {
    const url = counterId ? `/staff/tokens/${tokenId}/skip?counterId=${counterId}` : `/staff/tokens/${tokenId}/skip`;
    const res = await apiClient.post<QueueToken>(url);
    return res.data;
  },
  recallToken: async (tokenId: number, counterId?: number): Promise<QueueToken> => {
    const url = counterId ? `/staff/tokens/${tokenId}/recall?counterId=${counterId}` : `/staff/tokens/${tokenId}/recall`;
    const res = await apiClient.post<QueueToken>(url);
    return res.data;
  },
  transferToken: async (tokenId: number, targetServiceId: number): Promise<QueueToken> => {
    const res = await apiClient.post<QueueToken>(`/staff/tokens/${tokenId}/transfer?targetServiceId=${targetServiceId}`);
    return res.data;
  },
  toggleAutoCall: async (counterId: number, enabled: boolean): Promise<{ success: boolean; autoCallEnabled: boolean }> => {
    const res = await apiClient.post<{ success: boolean; autoCallEnabled: boolean }>(`/staff/counters/${counterId}/auto-call?enabled=${enabled}`);
    return res.data;
  },

  // Admin APIs
  getAnalyticsSummary: async (): Promise<AnalyticsSummary> => {
    const res = await apiClient.get<AnalyticsSummary>('/admin/analytics/summary');
    return res.data;
  },
  getHourlyStats: async (): Promise<HourlyStat[]> => {
    const res = await apiClient.get<HourlyStat[]>('/admin/analytics/hourly');
    return res.data;
  },
  getServiceStats: async (): Promise<ServiceStat[]> => {
    const res = await apiClient.get<ServiceStat[]>('/admin/analytics/services');
    return res.data;
  },
  getAllServices: async (): Promise<Service[]> => {
    const res = await apiClient.get<Service[]>('/admin/services');
    return res.data;
  },
  createService: async (service: Partial<Service>): Promise<Service> => {
    const res = await apiClient.post<Service>('/admin/services', service);
    return res.data;
  },
  updateService: async (id: number, service: Partial<Service>): Promise<Service> => {
    const res = await apiClient.put<Service>(`/admin/services/${id}`, service);
    return res.data;
  },
  getAllCounters: async (): Promise<Counter[]> => {
    const res = await apiClient.get<Counter[]>('/admin/counters');
    return res.data;
  },
  createCounter: async (counter: Partial<Counter>): Promise<Counter> => {
    const res = await apiClient.post<Counter>('/admin/counters', counter);
    return res.data;
  },
  updateCounter: async (id: number, counter: Partial<Counter>): Promise<Counter> => {
    const res = await apiClient.put<Counter>(`/admin/counters/${id}`, counter);
    return res.data;
  },
  getAllStaff: async (): Promise<AuthUser[]> => {
    const res = await apiClient.get<AuthUser[]>('/admin/staff');
    return res.data;
  },
  createStaff: async (staff: { username: string; name: string; password?: string }): Promise<AuthUser> => {
    const res = await apiClient.post<AuthUser>('/admin/staff', staff);
    return res.data;
  },
  getAllCustomers: async (): Promise<CustomerRecord[]> => {
    const res = await apiClient.get<CustomerRecord[]>('/admin/customers');
    return res.data;
  },
  getAuditLogs: async (): Promise<AuditLogItem[]> => {
    const res = await apiClient.get<AuditLogItem[]>('/admin/audit-logs');
    return res.data;
  },
  exportCsvUrl: '/api/admin/export/csv'
};
