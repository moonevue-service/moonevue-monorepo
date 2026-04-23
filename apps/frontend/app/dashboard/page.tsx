'use client';

import Link from 'next/link';
import { useAuth } from '@/app/providers';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Activity, Banknote, CreditCard } from 'lucide-react';

export default function DashboardPage() {
  const { user } = useAuth();

  const stats = [
    {
      title: 'Contas Bancárias',
      value: '0',
      description: 'Configure suas contas',
      icon: Banknote,
      href: '/dashboard/bank-accounts',
    },
    {
      title: 'Transações',
      value: '0',
      description: 'Atividade recente',
      icon: CreditCard,
      href: '/dashboard/transactions',
    },
    {
      title: 'Status',
      value: 'Ativo',
      description: 'Sistema operacional',
      icon: Activity,
      href: '#',
    },
  ];

  return (
    <div className="space-y-8">
      <div className="space-y-2">
        <h1 className="text-3xl font-bold">Bem-vindo!</h1>
        <p className="text-muted-foreground">
          Gerencie suas finanças e integrações bancárias em um único lugar
        </p>
      </div>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
        {stats.map((stat) => {
          const Icon = stat.icon;

          return (
            <Card key={stat.title} className="transition-shadow hover:shadow-lg">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between">
                  <div className="space-y-1">
                    <CardTitle className="text-sm font-medium">{stat.title}</CardTitle>
                    <CardDescription>{stat.description}</CardDescription>
                  </div>
                  <Icon className="h-5 w-5 text-muted-foreground" />
                </div>
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stat.value}</div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Primeiros Passos</CardTitle>
          <CardDescription>
            Use este roteiro para sair do cadastro e chegar ao primeiro pagamento com menos tentativa e erro.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-5">
            <div className="flex gap-4 rounded-xl border bg-muted/30 p-4">
              <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-primary text-sm font-bold text-primary-foreground">
                1
              </div>
              <div className="space-y-3">
                <h3 className="font-medium">Configure suas contas bancárias</h3>
                <p className="text-sm text-muted-foreground">
                  Cadastre a conta que vai operar os recebimentos. Tenha em mãos banco, agência,
                  número da conta, dígito e o tipo da conta.
                </p>
                <div className="rounded-lg bg-background p-3 text-sm text-muted-foreground">
                  Resultado esperado: pelo menos uma conta ativa visível na tela de contas bancárias.
                </div>
                <Button asChild size="sm">
                  <Link href="/dashboard/bank-accounts">Abrir contas bancárias</Link>
                </Button>
              </div>
            </div>

            <div className="flex gap-4 rounded-xl border bg-muted/30 p-4">
              <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-primary text-sm font-bold text-primary-foreground">
                2
              </div>
              <div className="space-y-3">
                <h3 className="font-medium">Revise os dados técnicos da integração</h3>
                <p className="text-sm text-muted-foreground">
                  Guarde seu Tenant ID e confirme quais papéis sua conta possui. Essas informações
                  serão usadas ao conectar o frontend, configurar certificados e validar permissões.
                </p>
                <div className="rounded-lg bg-background p-3 text-sm text-muted-foreground">
                  Dica: o Tenant ID abaixo identifica sua empresa nas rotas do backend e nas futuras configurações bancárias.
                </div>
                <Button asChild size="sm" variant="outline">
                  <Link href="/dashboard/settings">Abrir configurações</Link>
                </Button>
              </div>
            </div>

            <div className="flex gap-4 rounded-xl border bg-muted/30 p-4">
              <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-primary text-sm font-bold text-primary-foreground">
                3
              </div>
              <div className="space-y-3">
                <h3 className="font-medium">Execute seu primeiro teste de pagamento</h3>
                <p className="text-sm text-muted-foreground">
                  Depois de preparar a conta e a configuração bancária, crie uma transação de teste
                  informando banco, ID da configuração bancária, valor e descrição.
                </p>
                <div className="rounded-lg bg-background p-3 text-sm text-muted-foreground">
                  Se ainda não tiver a configuração bancária completa no painel, use esta etapa como checklist do que falta integrar no backend antes de operar em produção.
                </div>
                <Button asChild size="sm" variant="secondary">
                  <Link href="/dashboard/transactions">Abrir transações</Link>
                </Button>
              </div>
            </div>

            <div className="rounded-xl border border-dashed p-4 text-sm text-muted-foreground">
              Ordem recomendada: contas bancárias, validação dos dados da conta, configuração bancária e certificados, teste de transação e só depois integração completa com fluxo real.
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Informações da Conta</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Email:</span>
              <span className="font-medium">{user?.email}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Tenant ID:</span>
              <span className="font-medium">{user?.tenantId}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Papéis:</span>
              <span className="font-medium">{user?.roles?.join(', ') || 'Nenhum'}</span>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
