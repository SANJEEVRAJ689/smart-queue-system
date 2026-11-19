export type TokenStatus = 'WAITING' | 'CALLED' | 'SERVING' | 'COMPLETED' | 'SKIPPED' | 'CANCELLED';
export type Priority = 'NORMAL' | 'SENIOR' | 'EMERGENCY';
export type CounterStatus = 'OPEN' | 'BUSY' | 'CLOSED';
export type UserRole = 'ADMIN' | 'STAFF' | 'CUSTOMER';

export interface Service {
  id: number;
  name: string;
  prefix: string;
  avgServiceTime: number;
  active: boolean;
}

export interface Counter {
  id: number;
  name: string;
  serviceId?: number;
  serviceName?: string;
  status: CounterStatus;
  currentTokenId?: number;
  currentTokenNo?: string;
  currentTokenStatus?: TokenStatus;
  currentServiceName?: string;
  currentStaffId?: number;
  staffName?: string;
}

export interface BookTokenRequest {
  serviceId: number;
  customerName: string;
  phone: string;
  priority: Priority;
}

export interface BookTokenResponse {
  trackingCode: string;
  tokenNo: string;
  serviceName: string;
  positionInQueue?: number;
  queuePosition?: number;
  estimatedWaitMinutes: number;
  status?: TokenStatus;
  maskedPhone?: string;
  phoneMasked?: string;
  queueDate?: string;
}

export interface TokenStatusResponse {
  trackingCode: string;
  tokenNo: string;
  serviceName: string;
  status: TokenStatus;
  priority: Priority;
  positionInQueue: number;
  peopleAhead: number;
  estimatedWaitMinutes: number;
  counterId?: number;
  counterName?: string;
  startedAtUtc?: string;
  serverTimeUtc: string;
  completedAtUtc?: string;
  waitDurationSeconds?: number;
  serviceDurationSeconds?: number;
  maskedPhone?: string;
  canCancel: boolean;
}

export interface QueueToken {
  id: number;
  tokenNo: string;
  serviceId: number;
  serviceName: string;
  priority: Priority;
  status: TokenStatus;
  customerName: string;
  phone?: string;
  counterId?: number;
  counterName?: string;
  createdAt: string;
  calledAt?: string;
  calledAtUtc?: string;
  startedAt?: string;
  startedAtUtc?: string;
  completedAt?: string;
  completedAtUtc?: string;
  serverTimeUtc?: string;
  recallCount?: number;
  waitMinutes?: number;
  isStarving?: boolean;
}

export interface PublicStats {
  tokensToday: number;
  peopleWaiting: number;
  peopleServing: number;
  tokensCompleted: number;
  avgWaitMinutes: number;
  nowServingLatest: string;
  serverTimeUtc: string;
}

export interface DisplayBoardData {
  nowServing: Array<{
    counterId: number;
    counterName: string;
    status: CounterStatus;
    tokenNo?: string;
    tokenStatus?: TokenStatus;
    serviceName?: string;
  }>;
  nextWaiting: Array<{
    tokenNo: string;
    serviceName: string;
    priority: Priority;
  }>;
  serverTimeUtc: string;
}

export interface AnalyticsSummary {
  totalTokensToday: number;
  waitingCount: number;
  servingCount: number;
  completedCount: number;
  skippedCount: number;
  cancelledCount: number;
  avgWaitMinutes: number;
  avgServiceMinutes: number;
  noShowRate: number;
}

export interface HourlyStat {
  hour: number;
  label: string;
  count: number;
}

export interface ServiceStat {
  serviceId: number;
  serviceName: string;
  totalTokens: number;
  completedTokens: number;
  avgWaitMinutes: number;
}

export interface AuditLogItem {
  id: number;
  action: string;
  details: string;
  username: string;
  createdAt: string;
  ipAddress?: string;
}

export interface CustomerRecord {
  id: number;
  name: string;
  phone: string;
  email?: string;
  createdAt: string;
  totalTokensBooked?: number;
}

export interface AuthUser {
  token?: string;
  username: string;
  name: string;
  role: UserRole;
}

export interface PublicIssuedToken {
  tokenNo: string;
  position: number;
  estimatedWait: number;
  status: TokenStatus;
  trackingCode: string;
  serviceName: string;
  counterId?: number;
  counterName?: string;
}
