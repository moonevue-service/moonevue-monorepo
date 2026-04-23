'use client';

import React, { createContext, useContext, useEffect, useState } from 'react';
import { AuthApi, User } from '@/lib/api';

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  error: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  register: (data: {
    tenantName: string;
    tenantDocument: string;
    email: string;
    password: string;
    confirmPassword: string;
  }) => Promise<void>;
  refreshSession: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Check authentication on mount
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const userData = await AuthApi.introspect();
        setUser(userData);
      } catch {
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    };

    checkAuth();
  }, []);

  // Refresh session periodically
  useEffect(() => {
    if (!user) return;

    const interval = setInterval(async () => {
      try {
        await AuthApi.touch();
      } catch (err) {
        console.error('Failed to refresh session:', err);
      }
    }, 5 * 60 * 1000); // Every 5 minutes

    return () => clearInterval(interval);
  }, [user]);

  const login = async (email: string, password: string) => {
    try {
      setError(null);
      await AuthApi.login({ email, password });
      const userData = await AuthApi.introspect();
      setUser(userData);
    } catch (err: any) {
      const message = err?.message || 'Login failed';
      setError(message);
      throw err;
    }
  };

  const logout = async () => {
    try {
      await AuthApi.logout();
      setUser(null);
      setError(null);
    } catch (err: any) {
      const message = err?.message || 'Logout failed';
      setError(message);
      throw err;
    }
  };

  const register = async (data: {
    tenantName: string;
    tenantDocument: string;
    email: string;
    password: string;
    confirmPassword: string;
  }) => {
    try {
      setError(null);
      await AuthApi.register(data);
      const userData = await AuthApi.introspect();
      setUser(userData);
    } catch (err: any) {
      const message = err?.message || 'Registration failed';
      setError(message);
      throw err;
    }
  };

  const refreshSession = async () => {
    try {
      await AuthApi.touch();
      const userData = await AuthApi.introspect();
      setUser(userData);
    } catch {
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated: !!user,
        error,
        login,
        logout,
        register,
        refreshSession,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
