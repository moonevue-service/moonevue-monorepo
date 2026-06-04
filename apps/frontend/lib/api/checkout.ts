import { ApiClient } from './client';
import { ChargeResponseDTO } from './payments';

export interface CheckoutInfo {
  token: string;
  amount: string;
  description?: string;
  allowedInstruments: string[];
  status: 'CHECKOUT_OPEN' | 'PROCESSING' | 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELED' | 'FAILED';
  expiresAt: string;
  checkoutAccessMode?: 'PUBLIC' | 'CLIENT_DOCUMENT' | 'CLIENT_LOGIN' | string;
  clientId?: number;
  clientName?: string;
  clientDocumentMasked?: string;
  identityVerified?: boolean;
  bank: string;
  paymentResult?: ChargeResponseDTO;
}

export interface CheckoutIdentifyRequest {
  document: string;
}

export interface CheckoutPayRequest {
  instrument: string;
  payerName: string;
  payerDocument: string;
  payerEmail?: string;
  payerPhone?: string;
  pixKey?: string;
}

export interface CheckoutClientLookup {
  found: boolean;
  name?: string;
  email?: string;
  phone?: string;
}

export const CheckoutApi = {
  getInfo: (token: string) =>
    ApiClient.get<CheckoutInfo>(`/checkout/${token}`),

  getStatus: (token: string) =>
    ApiClient.get<CheckoutInfo>(`/checkout/${token}/status`),

  lookupClient: (token: string, document: string) =>
    ApiClient.get<CheckoutClientLookup>(
      `/checkout/${token}/client-lookup?document=${encodeURIComponent(document)}`
    ),

  identify: (token: string, data: CheckoutIdentifyRequest) =>
    ApiClient.post<CheckoutInfo>(`/checkout/${token}/identify`, data),

  pay: (token: string, data: CheckoutPayRequest) =>
    ApiClient.post<CheckoutInfo>(`/checkout/${token}/pay`, data),
};
