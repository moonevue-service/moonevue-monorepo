import { ApiClient } from './client';
import { PageResponse, TransactionSummary } from './payments';

export interface ClientSummary {
  id: number;
  name: string;
  cpfCnpj: string;
  email: string;
  phone?: string;
  status: string;
  createdAt: string;
}

export interface ClientUpsertRequest {
  name: string;
  cpfCnpj: string;
  email: string;
  phone?: string;
}

export const ClientsApi = {
  list: (params?: { page?: number; size?: number }) =>
    ApiClient.get<PageResponse<ClientSummary>>(
      `/clients?page=${params?.page ?? 0}&size=${params?.size ?? 50}`
    ),

  get: (clientId: number) => ApiClient.get<ClientSummary>(`/clients/${clientId}`),

  create: (data: ClientUpsertRequest) => ApiClient.post<ClientSummary>('/clients', data),

  update: (clientId: number, data: ClientUpsertRequest) =>
    ApiClient.put<ClientSummary>(`/clients/${clientId}`, data),

  listTransactions: (clientId: number, params?: { page?: number; size?: number }) =>
    ApiClient.get<PageResponse<TransactionSummary>>(
      `/clients/${clientId}/transactions?page=${params?.page ?? 0}&size=${params?.size ?? 50}`
    ),
};
