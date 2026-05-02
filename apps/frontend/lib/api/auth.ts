import { ApiClient } from './client';

export interface RegisterRequest {
  tenantName: string;
  tenantDocument: string;
  email: string;
  password: string;
  confirmPassword: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  tenantId: number;
  userId: number;
  email: string;
}

export interface User {
  userId: number;
  email: string;
  tenantId: number | null;
  roles: string[];
}

export interface EmployeeRegisterRequest {
  email: string;
  password: string;
}

export const AuthApi = {
  register: (data: RegisterRequest) =>
    ApiClient.post<AuthResponse>('/auth/register', data),

  // Register returns Set-Cookie from AuthController and this call validates
  // that the session cookie is active before continuing in the UI.
  registerWithSession: async (data: RegisterRequest) => {
    await ApiClient.post<AuthResponse>('/auth/register', data);
    return ApiClient.get<User>('/auth/introspect');
  },

  login: (data: LoginRequest) =>
    ApiClient.post<AuthResponse>('/auth/login', data),

  logout: () =>
    ApiClient.get<void>('/auth/logout'),

  introspect: () =>
    ApiClient.get<User>('/auth/introspect'),

  touch: () =>
    ApiClient.post<void>('/auth/touch'),

  createEmployee: (data: EmployeeRegisterRequest) =>
    ApiClient.post<AuthResponse>('/auth/employees', data),
};
