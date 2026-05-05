import { ApiClient } from './client';
import { BankType } from './finance';

export enum TransactionStatus {
  PENDING = 'PENDING',
  CONFIRMED = 'CONFIRMED',
  FAILED = 'FAILED',
}

export interface ChargeRequestDTO {
  bank: BankType;
  bankConfigurationId: number;
  payment: {
    instrument: string;
    amount: number;
    description?: string;
  };
}

export interface ChargeResponseDTO {
  id: string;
  status: TransactionStatus;
  amount: number;
  locId?: string;
  location?: string;
}

export const PaymentApi = {
  createPayment: (data: ChargeRequestDTO) =>
    ApiClient.post<ChargeResponseDTO>('/payments', data),
};
