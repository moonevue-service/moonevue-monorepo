import { ApiClient } from './client';

export enum PaymentBankType {
  EFI = 'EFI',
}

export enum TransactionStatus {
  PENDING = 'PENDING',
  CONFIRMED = 'CONFIRMED',
  FAILED = 'FAILED',
}

export interface ChargeRequestDTO {
  bank: PaymentBankType;
  bankConfigurationId: number;
  payment: PaymentPayload;
}

export type PaymentPayload =
  | {
      instrument: 'PIX_IMMEDIATE';
      pixImmediate: PixImmediatePayment;
      pixDue?: null;
      boleto?: null;
    }
  | {
      instrument: 'PIX_DUE';
      pixImmediate?: null;
      pixDue: PixDuePayment;
      boleto?: null;
    }
  | {
      instrument: 'BOLETO';
      pixImmediate?: null;
      pixDue?: null;
      boleto: BoletoPayment;
    };

export interface PixImmediatePayment {
  expiracaoSeconds?: number;
  cpf?: string;
  cnpj?: string;
  nome?: string;
  amount: number;
  solicitacaoPagador?: string;
  chave?: string;
}

export interface PixDuePayment {
  txid: string;
  dataDeVencimento: string;
  validadeAposVencimento?: number;
  cpf?: string;
  cnpj?: string;
  nome?: string;
  logradouro?: string;
  cidade?: string;
  uf?: string;
  cep?: string;
  amountOriginal: number;
  multaPerc?: string;
  jurosPerc?: string;
  descontoData?: string;
  descontoValorPerc?: string;
  solicitacaoPagador?: string;
  chave?: string;
}

export interface BoletoItem {
  name: string;
  valueInCents: number;
  amount: number;
}

export interface BoletoPayment {
  items: BoletoItem[];
  customer: {
    name?: string;
    cpf?: string;
    email?: string;
    phoneNumber?: string;
    juridicalPerson?: {
      corporateName?: string;
      cnpj?: string;
    };
    address?: {
      street?: string;
      number?: string;
      neighborhood?: string;
      zipcode?: string;
      city?: string;
      complement?: string;
      state?: string;
    };
  };
  expireAt: string;
  configurations?: {
    fineInCents?: number;
    interestInCents?: number;
    daysToWriteOff?: number;
    interestObject?: Record<string, unknown>;
  };
  message?: string;
}

export interface ChargeResponseDTO {
  id: string;
  status: string;
  amount: string;
  locId?: number;
  location?: string;
  kind?: string;
  dueDate?: string;
  expiracao?: number;
  pixCopiaECola?: string;
  chave?: string;
  barcode?: string;
  link?: string;
  billetLink?: string;
  pdfLink?: string;
}

export interface TransactionSummary {
  id: number;
  amount: string;
  status: string;
  type: string;
  description?: string;
  externalReference?: string;
  bank?: string;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const PaymentApi = {
  listTransactions: (params?: { page?: number; size?: number }) =>
    ApiClient.get<PageResponse<TransactionSummary>>(
      `/payments?page=${params?.page ?? 0}&size=${params?.size ?? 50}`
    ),

  createPayment: (data: ChargeRequestDTO) =>
    ApiClient.post<ChargeResponseDTO>('/payments', data),

  createPixImmediate: (data: {
    bank: PaymentBankType;
    bankConfigurationId: number;
    payment: PixImmediatePayment;
  }) => ApiClient.post<ChargeResponseDTO>('/payments/pix/immediate', data),

  createPixDue: (data: {
    bank: PaymentBankType;
    bankConfigurationId: number;
    payment: PixDuePayment;
  }) => ApiClient.post<ChargeResponseDTO>('/payments/pix/due', data),

  createBoleto: (data: {
    bank: PaymentBankType;
    bankConfigurationId: number;
    payment: BoletoPayment;
  }) => ApiClient.post<ChargeResponseDTO>('/payments/boleto', data),
};
