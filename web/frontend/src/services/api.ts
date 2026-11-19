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
  AuthUser,
  PublicIssuedToken
} from '../types';

export const TOKEN_KEY = 'sq_token';

export const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Attach Authorization Bearer token from localStorage
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Refresh on 401 retry queue
let isRefreshing = false;
let failedQueue: Array<{ resolve: (token: string) => void; reject: (err: any) => void }> = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else if (token) {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry && !originalRequest.url?.includes('/auth/')) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return apiClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshRes = await axios.post('/api/auth/refresh', {}, { withCredentials: true });
        const newToken = refreshRes.data?.token;
        if (newToken) {
          localStorage.setItem(TOKEN_KEY, newToken);
          originalRequest.headers.Authorization = `Bearer ${newToken}`;
          processQueue(null, newToken);
          return apiClient(originalRequest);
        }
      } catch (refreshErr) {
        processQueue(refreshErr, null);
        localStorage.removeItem(TOKEN_KEY);
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }
    return Promise.reject(error);
  }
);

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
  // Public Display & Landing & Digital Dispenser
  getPublicStats: async (): Promise<PublicStats> => {
    const res = await apiClient.get<PublicStats>('/display/stats');
    return res.data;
  },
  getDisplayBoard: async (): Promise<DisplayBoardData> => {
    const res = await apiClient.get<DisplayBoardData>('/display/board');
    return res.data;
  },
  getPublicDepartments: async (): Promise<Service[]> => {
    const res = await apiClient.get<Service[]>('/public/departments');
    return res.data;
  },
  issuePublicToken: async (departmentId: number, customerName?: string): Promise<PublicIssuedToken> => {
    const res = await apiClient.post<PublicIssuedToken>('/public/tokens', { departmentId, customerName });
    return res.data;
  },
  getPublicTokenStatus: async (trackingCode: string): Promise<PublicIssuedToken> => {
    const res = await apiClient.get<PublicIssuedToken>(`/public/tokens/${trackingCode}`);
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
    const res = await apiClient.post<AuthUser & { token?: string }>('/auth/staff/login', { username, password });
    if (res.data?.token) {
      localStorage.setItem(TOKEN_KEY, res.data.token);
    }
    return res.data;
  },
  adminLogin: async (username: string, password: string): Promise<AuthUser> => {
    const res = await apiClient.post<AuthUser & { token?: string }>('/auth/admin/login', { username, password });
    if (res.data?.token) {
      localStorage.setItem(TOKEN_KEY, res.data.token);
    }
    return res.data;
  },
  logout: async (): Promise<void> => {
    try {
      await apiClient.post('/auth/logout');
    } finally {
      localStorage.removeItem(TOKEN_KEY);
    }
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
  getStaffQueue: async (serviceId?: number, counterId?: number): Promise<QueueToken[]> => {
    let url = '/staff/queue';
    const params = new URLSearchParams();
    if (serviceId) params.append('serviceId', serviceId.toString());
    if (counterId) params.append('counterId', counterId.toString());
    if (params.toString()) url += `?${params.toString()}`;
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
  transferToken: async (
    tokenId: number,
    req: { targetServiceId?: number; targetCounterId?: number; sourceCounterId?: number } | number
  ): Promise<QueueToken> => {
    const payload = typeof req === 'number' ? { targetServiceId: req } : req;
    const res = await apiClient.post<QueueToken>(`/staff/tokens/${tokenId}/transfer`, payload);
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
