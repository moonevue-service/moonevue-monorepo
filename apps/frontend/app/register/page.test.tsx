import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import RegisterPage from './page';

const pushMock = vi.fn();
const refreshSessionMock = vi.fn();
const registerWithSessionMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock }),
}));

vi.mock('@/app/providers', () => ({
  useAuth: () => ({ isLoading: false, refreshSession: refreshSessionMock }),
}));

vi.mock('@/lib/api', () => ({
  AuthApi: {
    registerWithSession: (...args: unknown[]) => registerWithSessionMock(...args),
  },
}));

describe('RegisterPage', () => {
  beforeEach(() => {
    pushMock.mockReset();
    refreshSessionMock.mockReset();
    registerWithSessionMock.mockReset();
    registerWithSessionMock.mockResolvedValue(undefined);
    refreshSessionMock.mockResolvedValue(undefined);
  });

  it('cria a conta e redireciona para o dashboard', async () => {
    render(<RegisterPage />);

    fireEvent.change(screen.getByPlaceholderText('Sua Empresa Ltda'), {
      target: { value: 'Moonevue Ltda' },
    });
    fireEvent.change(screen.getByPlaceholderText('00.000.000/0000-00'), {
      target: { value: '12345678000199' },
    });
    fireEvent.change(screen.getByPlaceholderText('seu@email.com'), {
      target: { value: 'admin@moonevue.test' },
    });

    const passwordInputs = screen.getAllByPlaceholderText('••••••••');
    fireEvent.change(passwordInputs[0], { target: { value: 'senha123' } });
    fireEvent.change(passwordInputs[1], { target: { value: 'senha123' } });

    fireEvent.click(screen.getByRole('button', { name: 'Criar conta' }));

    await waitFor(() => {
      expect(registerWithSessionMock).toHaveBeenCalledWith({
        tenantName: 'Moonevue Ltda',
        tenantDocument: '12345678000199',
        email: 'admin@moonevue.test',
        password: 'senha123',
        confirmPassword: 'senha123',
      });
    });

    expect(refreshSessionMock).toHaveBeenCalledTimes(1);
    expect(pushMock).toHaveBeenCalledWith('/dashboard');
  });
});