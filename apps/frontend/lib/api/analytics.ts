import { ApiClient } from './client';

export type Granularity = 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR';

export type InsightSeverity = 'INFO' | 'POSITIVE' | 'WARNING' | 'CRITICAL';

export interface ExecutiveSummary {
  period: {
    from: string;
    to: string;
    granularity: Granularity;
  };
  grossRevenue: number;
  netRevenue: number;
  totalFees: number;
  totalRefunds: number;
  netProfit: number;
  averageTicket: number;
  contributionMarginPct: number;
  conversionRatePct: number;
  paidTransactions: number;
  totalTransactions: number;
  payingClients: number;
  growth: {
    grossRevenuePct: number;
    netRevenuePct: number;
    paidTransactionsPct: number;
  };
}

export interface RevenueTimeSeriesPoint {
  date: string;
  grossRevenue: number;
  netRevenue: number;
  paidCount: number;
}

export interface RevenueTimeSeries {
  granularity: Granularity;
  points: RevenueTimeSeriesPoint[];
}

export interface ClientRevenue {
  rank: number;
  clientId: number;
  clientName: string;
  revenue: number;
  txCount: number;
  sharePct: number;
}

export interface StatusBreakdown {
  status: string;
  txCount: number;
  totalAmount: number;
  sharePct: number;
}

export interface Receivables {
  totalReceivable: number;
  totalOverdue: number;
  totalToDue: number;
  overdueRatioPct: number;
}

export interface Insight {
  severity: InsightSeverity;
  category: string;
  title: string;
  message: string;
  metricValue: number | null;
}

export interface AnalyticsDashboard {
  summary: ExecutiveSummary;
  revenueTimeSeries: RevenueTimeSeries;
  topClients: ClientRevenue[];
  statusBreakdown: StatusBreakdown[];
  receivables: Receivables;
  insights: Insight[];
}

export interface AnalyticsQuery {
  from?: string;
  to?: string;
  granularity?: Granularity;
  topClients?: number;
}

function buildQuery(params: AnalyticsQuery): string {
  const search = new URLSearchParams();
  if (params.from) search.set('from', params.from);
  if (params.to) search.set('to', params.to);
  if (params.granularity) search.set('granularity', params.granularity);
  if (params.topClients != null) search.set('topClients', String(params.topClients));
  const qs = search.toString();
  return qs ? `?${qs}` : '';
}

export const AnalyticsApi = {
  getDashboard: (tenantId: number, params: AnalyticsQuery = {}) =>
    ApiClient.get<AnalyticsDashboard>(
      `/api/tenant/${tenantId}/analytics/dashboard${buildQuery(params)}`
    ),

  getSummary: (tenantId: number, params: AnalyticsQuery = {}) =>
    ApiClient.get<ExecutiveSummary>(
      `/api/tenant/${tenantId}/analytics/summary${buildQuery(params)}`
    ),

  getRevenueTimeSeries: (tenantId: number, params: AnalyticsQuery = {}) =>
    ApiClient.get<RevenueTimeSeries>(
      `/api/tenant/${tenantId}/analytics/revenue/timeseries${buildQuery(params)}`
    ),

  getClientRanking: (tenantId: number, params: AnalyticsQuery = {}) =>
    ApiClient.get<ClientRevenue[]>(
      `/api/tenant/${tenantId}/analytics/clients/ranking${buildQuery(params)}`
    ),

  getStatusBreakdown: (tenantId: number, params: AnalyticsQuery = {}) =>
    ApiClient.get<StatusBreakdown[]>(
      `/api/tenant/${tenantId}/analytics/status-breakdown${buildQuery(params)}`
    ),

  getReceivables: (tenantId: number) =>
    ApiClient.get<Receivables>(`/api/tenant/${tenantId}/analytics/receivables`),
};
