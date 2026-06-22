import { ApiClient } from "./client";

export type ApiKeyEnvironment = "LIVE" | "TEST";
export type ApiKeyStatus = "ACTIVE" | "REVOKED";

export const API_KEY_SCOPES: {
  value: string;
  label: string;
  description: string;
}[] = [
  {
    value: "charges:write",
    label: "Criar cobranças",
    description: "Emitir novas cobranças (PIX e boleto)",
  },
  {
    value: "charges:read",
    label: "Consultar cobranças",
    description: "Consultar status e detalhes de cobranças",
  },
];

export interface ApiKey {
  id: number;
  name: string;
  keyId: string;
  keyPrefix: string;
  environment: ApiKeyEnvironment;
  scopes: string[];
  status: ApiKeyStatus;
  lastUsedAt?: string | null;
  expiresAt?: string | null;
  createdAt: string;
  /** Preenchido apenas na criação/rotação — exibido uma única vez. */
  plaintextKey?: string | null;
}

export interface CreateApiKeyRequest {
  name: string;
  environment: ApiKeyEnvironment;
  scopes: string[];
  expiresAt?: string | null;
}

export interface IntegrationAnalytics {
  rangeDays: number;
  keys: {
    total: number;
    active: number;
    revoked: number;
    live: number;
    test: number;
    usedLast7d: number;
    neverUsed: number;
  };
  usage: {
    totalCharges: number;
    totalAmount: number;
    paidCharges: number;
    paidAmount: number;
    chargesLast7d: number;
    successRate: number;
  };
  timeseries: { date: string; count: number; amount: number }[];
  byStatus: { status: string; count: number }[];
  byEnvironment: { environment: string; count: number; amount: number }[];
  perKey: {
    apiKeyId: number;
    name: string;
    environment: ApiKeyEnvironment;
    status: ApiKeyStatus;
    lastUsedAt?: string | null;
    charges: number;
    amount: number;
    paidCharges: number;
  }[];
}

export const IntegrationsApi = {
  listKeys: () => ApiClient.get<ApiKey[]>("/integrations/api-keys"),

  createKey: (data: CreateApiKeyRequest) =>
    ApiClient.post<ApiKey>("/integrations/api-keys", data),

  revokeKey: (id: number) =>
    ApiClient.delete<void>(`/integrations/api-keys/${id}`),

  rotateKey: (id: number) =>
    ApiClient.post<ApiKey>(`/integrations/api-keys/${id}/rotate`),

  analytics: (days = 30) =>
    ApiClient.get<IntegrationAnalytics>(`/integrations/api-keys/analytics?days=${days}`),
};
