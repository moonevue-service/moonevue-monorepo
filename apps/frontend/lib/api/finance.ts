import { ApiClient } from './client';

export enum BankType {
  BRADESCO = 'BRADESCO',
  ITAU = 'ITAU',
  SANTANDER = 'SANTANDER',
  CEF = 'CEF',
  BB = 'BB',
}

export enum AccountType {
  CHECKING = 'CHECKING',
  SAVINGS = 'SAVINGS',
}

export enum Environment {
  SANDBOX = 'SANDBOX',
  PRODUCTION = 'PRODUCTION',
}

export interface BankAccountRequest {
  name: string;
  bank: BankType;
  cdAgency: string;
  cdAccount: string;
  cdAccountDigit: string;
  accountType: AccountType;
  active?: boolean;
}

export interface BankAccountResponse {
  id: number;
  name: string;
  bank: BankType;
  cdAgency: string;
  cdAccount: string;
  cdAccountDigit: string;
  accountType: AccountType;
  active: boolean;
}

export interface BankConfigurationRequest {
  environment: Environment;
  webhookUrl: string;
  isActive?: boolean;
  extraConfig?: Record<string, any>;
}

export interface BankConfigurationResponse {
  id: number;
  environment: Environment;
  webhookUrl: string;
  isActive: boolean;
  extraConfig: Record<string, any>;
}

export interface CertificateUploadResponse {
  certificatePath: string;
  expiresAt?: string;
}

export const FinanceApi = {
  // Bank Accounts
  createBankAccount: (tenantId: number, data: BankAccountRequest) =>
    ApiClient.post<BankAccountResponse>(
      `/api/tenant/${tenantId}/bank-account`,
      data
    ),

  updateBankAccount: (
    tenantId: number,
    bankAccountId: number,
    data: BankAccountRequest
  ) =>
    ApiClient.put<BankAccountResponse>(
      `/api/tenant/${tenantId}/bank-account/${bankAccountId}`,
      data
    ),

  deleteBankAccount: (tenantId: number, bankAccountId: number) =>
    ApiClient.delete<void>(
      `/api/tenant/${tenantId}/bank-account/${bankAccountId}`
    ),

  // Bank Configurations
  createBankConfiguration: (
    tenantId: number,
    bankAccountId: number,
    data: BankConfigurationRequest
  ) =>
    ApiClient.post<BankConfigurationResponse>(
      `/api/tenant/${tenantId}/bank-account/${bankAccountId}/configuration`,
      data
    ),

  updateBankConfiguration: (
    tenantId: number,
    bankAccountId: number,
    configId: number,
    data: Partial<BankConfigurationRequest>
  ) =>
    ApiClient.put<BankConfigurationResponse>(
      `/api/tenant/${tenantId}/bank-account/${bankAccountId}/configuration/${configId}`,
      data
    ),

  uploadCertificate: async (
    tenantId: number,
    bankAccountId: number,
    configId: number,
    file: File,
    password?: string
  ) => {
    const formData = new FormData();
    formData.append('file', file);
    if (password) {
      formData.append('password', password);
    }

    const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || '';
    const url = `${baseUrl}/api/tenant/${tenantId}/bank-account/${bankAccountId}/configuration/${configId}/certificate`;

    const response = await fetch(url, {
      method: 'POST',
      body: formData,
      credentials: 'include',
    });

    if (!response.ok) {
      throw new Error('Failed to upload certificate');
    }

    return response.json() as Promise<CertificateUploadResponse>;
  },
};
