'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/app/providers';
import { useRouter } from 'next/navigation';
import { Loader2, Copy, Check } from 'lucide-react';

export default function SettingsPage() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [copied, setCopied] = useState(false);
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

  const handleCopyTenantId = async () => {
    if (user?.tenantId) {
      await navigator.clipboard.writeText(user.tenantId.toString());
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="space-y-6 max-w-2xl">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold">Configurações</h1>
        <p className="text-muted-foreground">Gerencie sua conta e preferências</p>
      </div>

      {/* Account Information */}
      <Card>
        <CardHeader>
          <CardTitle>Informações da Conta</CardTitle>
          <CardDescription>
            Seus dados de conta e identificadores
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>Email</Label>
            <div className="p-3 bg-muted rounded-md font-medium">{user?.email}</div>
          </div>

          <div className="space-y-2">
            <Label>Tenant ID</Label>
            <div className="flex gap-2">
              <div className="flex-1 p-3 bg-muted rounded-md font-mono text-sm">
                {user?.tenantId}
              </div>
              <Button
                variant="outline"
                size="icon"
                onClick={handleCopyTenantId}
              >
                {copied ? (
                  <Check className="w-4 h-4" />
                ) : (
                  <Copy className="w-4 h-4" />
                )}
              </Button>
            </div>
          </div>

          <div className="space-y-2">
            <Label>Papéis</Label>
            <div className="flex flex-wrap gap-2">
              {user?.roles && user.roles.length > 0 ? (
                user.roles.map((role) => (
                  <span
                    key={role}
                    className="px-3 py-1 bg-primary/10 text-primary rounded-full text-sm font-medium"
                  >
                    {role}
                  </span>
                ))
              ) : (
                <span className="text-muted-foreground">Nenhum papel atribuído</span>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Security */}
      <Card>
        <CardHeader>
          <CardTitle>Segurança</CardTitle>
          <CardDescription>
            Gerenciar segurança da sua conta
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="p-4 bg-blue-50 dark:bg-blue-950 rounded-lg border border-blue-200 dark:border-blue-800">
            <p className="text-sm text-blue-900 dark:text-blue-100">
              💡 Para trocar sua senha, saia da conta e use a opção de recuperação de senha na página de login.
            </p>
          </div>

          <div>
            <h4 className="font-medium mb-2">Sessão Ativa</h4>
            <p className="text-sm text-muted-foreground mb-3">
              Sua sessão será automaticamente renovada a cada interação
            </p>
          </div>
        </CardContent>
      </Card>

      {/* API Integration */}
      <Card>
        <CardHeader>
          <CardTitle>Integração com API</CardTitle>
          <CardDescription>
            Use seus identificadores para integrar com a API
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="p-3 bg-muted rounded-lg text-sm font-mono overflow-auto">
            <div>Authorization: Cookie sid=&lt;session_id&gt;</div>
          </div>
          <p className="text-sm text-muted-foreground">
            Sua session é gerenciada automaticamente via cookies. Use os endpoints da API com suas credenciais.
          </p>
        </CardContent>
      </Card>

      {/* Danger Zone */}
      <Card className="border-destructive/20">
        <CardHeader>
          <CardTitle className="text-destructive">Zona de Perigo</CardTitle>
          <CardDescription>
            Ações que não podem ser desfeitas
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div>
            <h4 className="font-medium mb-2">Sair da Conta</h4>
            <p className="text-sm text-muted-foreground mb-4">
              Você será desconectado e precisará fazer login novamente
            </p>
            <Button
              variant="destructive"
              onClick={handleLogout}
              disabled={isLoggingOut}
            >
              {isLoggingOut && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {isLoggingOut ? 'Saindo...' : 'Sair da Conta'}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Help */}
      <Card>
        <CardHeader>
          <CardTitle>Precisa de Ajuda?</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div>
            <h4 className="font-medium mb-1">Documentação</h4>
            <p className="text-sm text-muted-foreground">
              Consulte nossa documentação completa por mais informações
            </p>
          </div>
          <div>
            <h4 className="font-medium mb-1">Suporte</h4>
            <p className="text-sm text-muted-foreground">
              Entre em contato com nosso time de suporte para dúvidas
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
