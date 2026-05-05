'use client';

import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/app/providers';
import { PaymentApi, BankType, FinanceApi } from '@/lib/api';
import { Loader2, Plus, Eye, EyeOff } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';

interface Transaction {
  id: string;
  amount: number;
  status: 'PENDING' | 'CONFIRMED' | 'FAILED';
  description: string;
  createdAt: string;
  bank: string;
}

export default function TransactionsPage() {
  const { user } = useAuth();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [bankAccounts, setBankAccounts] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [formData, setFormData] = useState({
    bank: 'BRADESCO' as BankType,
    bankConfigurationId: '',
    amount: '',
    description: '',
  });

  useEffect(() => {
    // Load bank accounts for the dropdown
    if (user?.tenantId) {
      // In a real app, you'd fetch this from an API
      // For now, we'll leave it empty as we don't have a GET endpoint for bank accounts
    }
  }, [user?.tenantId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.bankConfigurationId) {
      setError('Selecione uma configuração bancária');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const response = await PaymentApi.createPayment({
        bank: formData.bank,
        bankConfigurationId: parseInt(formData.bankConfigurationId),
        payment: {
          instrument: 'PIX',
          amount: parseFloat(formData.amount),
          description: formData.description,
        },
      });

      const newTransaction: Transaction = {
        id: response.id,
        amount: response.amount,
        status: response.status,
        description: formData.description,
        createdAt: new Date().toISOString(),
        bank: formData.bank,
      };

      setTransactions([newTransaction, ...transactions]);
      setFormData({
        bank: 'BRADESCO',
        bankConfigurationId: '',
        amount: '',
        description: '',
      });
      setIsDialogOpen(false);
    } catch (err: any) {
      setError(err?.message || 'Erro ao processar pagamento');
    } finally {
      setIsLoading(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value,
    });
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'CONFIRMED':
        return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200';
      case 'PENDING':
        return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200';
      case 'FAILED':
        return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200';
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200';
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'CONFIRMED':
        return 'Confirmado';
      case 'PENDING':
        return 'Pendente';
      case 'FAILED':
        return 'Falhou';
      default:
        return status;
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Transações</h1>
          <p className="text-muted-foreground">Histórico e processamento de pagamentos</p>
        </div>
        <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
          <DialogTrigger asChild>
            <Button className="gap-2">
              <Plus className="w-4 h-4" />
              Novo Pagamento
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[425px]">
            <DialogHeader>
              <DialogTitle>Processar Pagamento</DialogTitle>
              <DialogDescription>
                Crie uma nova transação de pagamento
              </DialogDescription>
            </DialogHeader>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="bank">Banco</Label>
                <select
                  id="bank"
                  name="bank"
                  value={formData.bank}
                  onChange={handleChange}
                  className="w-full px-3 py-2 border rounded-md bg-background"
                  required
                >
                  <option value="BRADESCO">Bradesco</option>
                  <option value="ITAU">Itaú</option>
                  <option value="SANTANDER">Santander</option>
                  <option value="CEF">Caixa</option>
                  <option value="BB">Banco do Brasil</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="bankConfigurationId">Configuração Bancária</Label>
                <Input
                  id="bankConfigurationId"
                  name="bankConfigurationId"
                  type="number"
                  placeholder="ID da configuração"
                  value={formData.bankConfigurationId}
                  onChange={handleChange}
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="amount">Valor (R$)</Label>
                <Input
                  id="amount"
                  name="amount"
                  type="number"
                  step="0.01"
                  placeholder="0.00"
                  value={formData.amount}
                  onChange={handleChange}
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="description">Descrição</Label>
                <Input
                  id="description"
                  name="description"
                  placeholder="Descrição do pagamento"
                  value={formData.description}
                  onChange={handleChange}
                />
              </div>

              {error && (
                <div className="p-3 bg-destructive/10 text-destructive rounded-md text-sm">
                  {error}
                </div>
              )}

              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                {isLoading ? 'Processando...' : 'Processar Pagamento'}
              </Button>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      {/* Error Message */}
      {error && (
        <div className="p-4 bg-destructive/10 text-destructive rounded-lg">
          {error}
        </div>
      )}

      {/* Transactions List */}
      {transactions.length === 0 ? (
        <Card>
          <CardContent className="pt-6 text-center">
            <p className="text-muted-foreground mb-4">Nenhuma transação registrada</p>
            <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
              <DialogTrigger asChild>
                <Button variant="outline">Criar Primeiro Pagamento</Button>
              </DialogTrigger>
            </Dialog>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {transactions.map((transaction) => (
            <Card key={transaction.id} className="hover:shadow-md transition-shadow">
              <CardContent className="pt-6">
                <div className="flex items-center justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-4">
                      <div className="flex-1">
                        <h3 className="font-medium">{transaction.description}</h3>
                        <p className="text-sm text-muted-foreground">
                          {transaction.bank} • {new Date(transaction.createdAt).toLocaleDateString('pt-BR')}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="font-semibold text-lg">
                          R$ {transaction.amount.toFixed(2)}
                        </p>
                        <span
                          className={`inline-block px-2 py-1 rounded text-xs font-medium ${getStatusColor(
                            transaction.status
                          )}`}
                        >
                          {getStatusLabel(transaction.status)}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
