'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter, usePathname } from 'next/navigation';
import {
  Menu,
  X,
  LogOut,
  LayoutDashboard,
  Banknote,
  CreditCard,
  Settings,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/app/providers';
import { ProtectedRoute } from '@/app/protected-route';

interface DashboardLayoutProps {
  children: React.ReactNode;
}

const menuItems = [
  { href: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/dashboard/bank-accounts', label: 'Contas Bancárias', icon: Banknote },
  { href: '/dashboard/transactions', label: 'Transações', icon: CreditCard },
  { href: '/dashboard/settings', label: 'Configurações', icon: Settings },
];

export default function DashboardLayout({ children }: DashboardLayoutProps) {
  const router = useRouter();
  const pathname = usePathname();
  const { logout, user } = useAuth();
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const handleLogout = async () => {
    setIsLoggingOut(true);
    try {
      await logout();
      router.push('/login');
    } catch (err) {
      console.error('Logout failed:', err);
      setIsLoggingOut(false);
    }
  };

  return (
    <ProtectedRoute>
      <div className="flex h-screen bg-background">
        {/* Sidebar */}
        <aside
          className={`${
            isSidebarOpen ? 'w-64' : 'w-0'
          } border-r transition-all duration-300 overflow-hidden bg-muted/50`}
        >
          <div className="p-6 border-b h-16 flex items-center">
            <h1 className="text-xl font-bold text-primary">Moonevue</h1>
          </div>

          <nav className="p-4 space-y-2">
            {menuItems.map((item) => {
              const Icon = item.icon;
              const isActive = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex items-center gap-3 px-4 py-2 rounded-lg transition-colors ${
                    isActive
                      ? 'bg-primary text-primary-foreground'
                      : 'text-foreground hover:bg-muted'
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  <span>{item.label}</span>
                </Link>
              );
            })}
          </nav>

          <div className="absolute bottom-0 left-0 right-0 p-4 border-t bg-muted/30">
            <div className="flex items-center gap-2 mb-3 px-2">
              <div className="w-8 h-8 rounded-full bg-primary/20 flex items-center justify-center">
                <span className="text-xs font-bold text-primary">
                  {user?.email?.[0].toUpperCase() || '?'}
                </span>
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate">{user?.email}</p>
              </div>
            </div>
            <Button
              variant="ghost"
              size="sm"
              className="w-full justify-start"
              onClick={handleLogout}
              disabled={isLoggingOut}
            >
              <LogOut className="w-4 h-4 mr-2" />
              Sair
            </Button>
          </div>
        </aside>

        {/* Main Content */}
        <div className="flex-1 flex flex-col">
          {/* Top Bar */}
          <header className="border-b bg-card h-16 flex items-center px-6 gap-4">
            <button
              onClick={() => setIsSidebarOpen(!isSidebarOpen)}
              className="p-2 hover:bg-muted rounded-lg transition-colors"
            >
              {isSidebarOpen ? (
                <X className="w-5 h-5" />
              ) : (
                <Menu className="w-5 h-5" />
              )}
            </button>
            <div className="flex-1" />
            <div className="text-sm text-muted-foreground">
              Tenant ID: {user?.tenantId}
            </div>
          </header>

          {/* Content */}
          <main className="flex-1 overflow-auto">
            <div className="p-6">{children}</div>
          </main>
        </div>
      </div>
    </ProtectedRoute>
  );
}
