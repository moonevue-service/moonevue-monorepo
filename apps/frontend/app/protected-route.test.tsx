import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { ProtectedRoute } from './protected-route';

const replaceMock = vi.fn();
const useAuthMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

vi.mock('@/app/providers', () => ({
  useAuth: () => useAuthMock(),
}));

describe('ProtectedRoute', () => {
  beforeEach(() => {
    replaceMock.mockReset();
    useAuthMock.mockReset();
  });

  it('renderiza os filhos quando a sessão está válida', () => {
    useAuthMock.mockReturnValue({ isAuthenticated: true, isLoading: false });

    render(
      <ProtectedRoute>
        <div>conteudo protegido</div>
      </ProtectedRoute>
    );

    expect(screen.getByText('conteudo protegido')).toBeInTheDocument();
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it('redireciona para login quando não autenticado', async () => {
    useAuthMock.mockReturnValue({ isAuthenticated: false, isLoading: false });

    render(
      <ProtectedRoute>
        <div>conteudo protegido</div>
      </ProtectedRoute>
    );

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith('/login'));
  });
});