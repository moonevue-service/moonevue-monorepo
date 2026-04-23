'use client';

import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/app/providers';
import { FinanceApi, BankType, AccountType, BankAccountResponse } from '@/lib/api';
import { Loader2, Plus, Trash2, Edit2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';

export default function BankAccountsPage() {
  const { user } = useAuth();
  const [accounts, setAccounts] = useState<BankAccountResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    bank: 'BRADESCO' as BankType,
    cdAgency: '',
    cdAccount: '',
    cdAccountDigit: '',
    accountType: 'CHECKING' as AccountType,
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user?.tenantId) {
      setError('Tenant ID não encontrado');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const response = await FinanceApi.createBankAccount(user.tenantId, {
        ...formData,
        active: true,
      });
      setAccounts([...accounts, response]);
      setFormData({
        name: '',
        bank: 'BRADESCO',
        cdAgency: '',
        cdAccount: '',
        cdAccountDigit: '',
        accountType: 'CHECKING',
      });
      setIsDialogOpen(false);
    } catch (err: any) {
      setError(err?.message || 'Erro ao criar conta bancária');
    } finally {
      setIsLoading(false);
    }
  };

  const handleDelete = async (accountId: number) => {
    if (!user?.tenantId) return;
    if (!confirm('Tem certeza que deseja deletar esta conta?')) return;

    try {
      await FinanceApi.deleteBankAccount(user.tenantId, accountId);
      setAccounts(accounts.filter((a) => a.id !== accountId));
    } catch (err: any) {
      setError(err?.message || 'Erro ao deletar conta');
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value,
    });
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Contas Bancárias</h1>
          <p className="text-muted-foreground">Gerencie suas contas bancárias integradas</p>
        </div>
        <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
          <DialogTrigger asChild>
            <Button className="gap-2">
              <Plus className="w-4 h-4" />
              Nova Conta
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[425px]">
            <DialogHeader>
              <DialogTitle>Adicionar Conta Bancária</DialogTitle>
              <DialogDescription>
                Preencha os dados de sua conta bancária
              </DialogDescription>
            </DialogHeader>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="name">Nome da Conta</Label>
                <Input
                  id="name"
                  name="name"
                  placeholder="Ex: Conta Principal"
                  value={formData.name}
                  onChange={handleChange}
                  required
                />
              </div>

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

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="cdAgency">Agência</Label>
                  <Input
                    id="cdAgency"
                    name="cdAgency"
                    placeholder="1234"
                    value={formData.cdAgency}
                    onChange={handleChange}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="cdAccount">Conta</Label>
                  <Input
                    id="cdAccount"
                    name="cdAccount"
                    placeholder="123456"
                    value={formData.cdAccount}
                    onChange={handleChange}
                    required
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="cdAccountDigit">Dígito</Label>
                <Input
                  id="cdAccountDigit"
                  name="cdAccountDigit"
                  placeholder="1"
                  value={formData.cdAccountDigit}
                  onChange={handleChange}
                  required
                  maxLength="1"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="accountType">Tipo de Conta</Label>
                <select
                  id="accountType"
                  name="accountType"
                  value={formData.accountType}
                  onChange={handleChange}
                  className="w-full px-3 py-2 border rounded-md bg-background"
                  required
                >
                  <option value="CHECKING">Corrente</option>
                  <option value="SAVINGS">Poupança</option>
                </select>
              </div>

              {error && (
                <div className="p-3 bg-destructive/10 text-destructive rounded-md text-sm">
                  {error}
                </div>
              )}

              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                {isLoading ? 'Criando...' : 'Adicionar Conta'}
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

      {/* Accounts List */}
      {accounts.length === 0 ? (
        <Card>
          <CardContent className="pt-6 text-center">
            <p className="text-muted-foreground mb-4">Nenhuma conta bancária configurada</p>
            <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
              <DialogTrigger asChild>
                <Button variant="outline">Adicionar Primeira Conta</Button>
              </DialogTrigger>
            </Dialog>
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {accounts.map((account) => (
            <Card key={account.id} className="hover:shadow-md transition-shadow">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between">
                  <div>
                    <CardTitle className="text-lg">{account.name}</CardTitle>
                    <CardDescription>{account.bank}</CardDescription>
                  </div>
                  <div className="flex gap-2">
                    <Button variant="ghost" size="icon">
                      <Edit2 className="w-4 h-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => handleDelete(account.id)}
                    >
                      <Trash2 className="w-4 h-4 text-destructive" />
                    </Button>
                  </div>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <p className="text-muted-foreground">Agência</p>
                    <p className="font-medium">{account.cdAgency}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground">Conta</p>
                    <p className="font-medium">
                      {account.cdAccount}-{account.cdAccountDigit}
                    </p>
                  </div>
                  <div>
                    <p className="text-muted-foreground">Tipo</p>
                    <p className="font-medium">
                      {account.accountType === 'CHECKING' ? 'Corrente' : 'Poupança'}
                    </p>
                  </div>
                  <div>
                    <p className="text-muted-foreground">Status</p>
                    <p className="font-medium">
                      <span className={`px-2 py-1 rounded text-xs ${
                        account.active
                          ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                          : 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200'
                      }`}>
                        {account.active ? 'Ativa' : 'Inativa'}
                      </span>
                    </p>
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
