'use client';

import { useEffect, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { CopyOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useAuth } from '@/app/providers';
import {
  BankAccountResponse,
  BankConfigurationResponse,
  ChargeResponseDTO,
  ClientsApi,
  ClientSummary,
  Environment,
  FinanceApi,
  PageResponse,
  PaymentApi,
  PaymentBankType,
  TransactionSummary,
} from '@/lib/api';

const { Title, Text } = Typography;

type PaymentInstrument = 'PIX_IMMEDIATE' | 'PIX_DUE' | 'BOLETO';
type PaymentAction = 'DIRECT' | 'CHECKOUT';

type PaymentResultModal =
  | { kind: 'charge'; data: ChargeResponseDTO }
  | { kind: 'checkout'; data: TransactionSummary };

interface Transaction {
  id: string;
  amount: number;
  status: string;
  description: string;
  createdAt: string;
  bank: string;
  instrument: PaymentInstrument;
  externalReference?: string;
  checkoutToken?: string;
  checkoutUrl?: string;
  checkoutExpiresAt?: string;
}

// FormValues tipada por forma de pagamento
type FormValues = {
  bankAccountId: number;
  bankConfigurationId: number;
  clientId?: number;
  instrument: PaymentInstrument;
  // PIX Imediato
  pixAmount?: number;
  pixDescription?: string;
  pixChave?: string;
  pixExpiracao?: number;
  pixCpf?: string;
  pixCnpj?: string;
  pixNome?: string;
  // PIX com Vencimento
  pixDueTxid?: string;
  pixDueDataVencimento?: string;
  pixDueAmount?: number;
  pixDueNome?: string;
  pixDueCpf?: string;
  pixDueCnpj?: string;
  pixDueChave?: string;
  pixDueSolicitacao?: string;
  // Boleto
  boletoNome?: string;
  boletoCpf?: string;
  boletoEmail?: string;
  boletoExpireAt?: string;
  boletoItemName?: string;
  boletoItemValue?: number;
  boletoMessage?: string;
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendente',
  CONFIRMED: 'Confirmado',
  FAILED: 'Falhou',
};

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'warning',
  CONFIRMED: 'success',
  FAILED: 'error',
};

const INSTRUMENT_LABELS: Record<PaymentInstrument, string> = {
  PIX_IMMEDIATE: 'PIX Imediato',
  PIX_DUE: 'PIX com Vencimento',
  BOLETO: 'Boleto',
};

const INSTRUMENT_COLORS: Record<PaymentInstrument, string> = {
  PIX_IMMEDIATE: 'green',
  PIX_DUE: 'cyan',
  BOLETO: 'purple',
};

function descriptionToInstrument(description?: string): PaymentInstrument {
  if (!description) return 'PIX_IMMEDIATE';
  if (description.includes('PIX_DUE')) return 'PIX_DUE';
  if (description.includes('BOLETO')) return 'BOLETO';
  return 'PIX_IMMEDIATE';
}

export default function TransactionsPage() {
  const { user } = useAuth();
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [instrument, setInstrument] = useState<PaymentInstrument>('PIX_IMMEDIATE');
  const [resultModal, setResultModal] = useState<PaymentResultModal | null>(null);
  const [loadingTx, setLoadingTx] = useState(false);
  const [totalTx, setTotalTx] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const PAGE_SIZE = 50;

  // Dados das contas e configs
  const [accounts, setAccounts] = useState<BankAccountResponse[]>([]);
  const [configs, setConfigs] = useState<BankConfigurationResponse[]>([]);
  const [loadingAccounts, setLoadingAccounts] = useState(false);
  const [loadingConfigs, setLoadingConfigs] = useState(false);
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [loadingClients, setLoadingClients] = useState(false);

  // Carrega transações do banco
  const loadTransactions = async (page = 0) => {
    setLoadingTx(true);
    try {
      const resp = await PaymentApi.listTransactions({ page, size: PAGE_SIZE });
      setTransactions(
        resp.content.map((t) => ({
          id: String(t.id),
          amount: Number(t.amount),
          status: t.status,
          description: t.description ?? '',
          createdAt: t.createdAt,
          bank: t.bank ?? '',
          instrument: descriptionToInstrument(t.description),
          externalReference: t.externalReference,
          checkoutToken: t.checkoutToken,
          checkoutUrl: t.checkoutUrl,
          checkoutExpiresAt: t.checkoutExpiresAt,
        }))
      );
      setTotalTx(resp.totalElements);
    } catch {
      message.error('Erro ao carregar transações');
    } finally {
      setLoadingTx(false);
    }
  };

  useEffect(() => {
    loadTransactions(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Carrega contas ao abrir o modal
  const handleOpenModal = async () => {
    setModalOpen(true);
    if (!user?.tenantId) return;

    if (accounts.length === 0) {
      setLoadingAccounts(true);
      try {
        const data = await FinanceApi.listBankAccounts(user.tenantId);
        setAccounts(data.filter((a) => a.active));
      } catch {
        message.error('Erro ao carregar contas bancárias');
      } finally {
        setLoadingAccounts(false);
      }
    }

    if (clients.length === 0) {
      setLoadingClients(true);
      try {
        const page = await ClientsApi.list({ page: 0, size: 100 });
        const activeClients = page.content.filter((c) => c.status === 'ACTIVE');
        setClients(activeClients);
      } catch {
        message.error('Erro ao carregar clientes');
      } finally {
        setLoadingClients(false);
      }
    }
  };

  const handleCloseModal = () => {
    setModalOpen(false);
    form.resetFields();
    setConfigs([]);
    setInstrument('PIX_IMMEDIATE');
  };

  // Carrega configs quando seleciona uma conta
  const handleAccountChange = async (bankAccountId: number) => {
    form.setFieldValue('bankConfigurationId', undefined);
    setConfigs([]);
    if (!user?.tenantId) return;
    setLoadingConfigs(true);
    try {
      const data = await FinanceApi.listBankConfigurations(user.tenantId, bankAccountId);
      setConfigs(data.filter((c) => c.isActive));
      if (data.filter((c) => c.isActive).length === 1) {
        form.setFieldValue('bankConfigurationId', data.find((c) => c.isActive)!.id);
      }
    } catch {
      message.error('Erro ao carregar configurações');
    } finally {
      setLoadingConfigs(false);
    }
  };

  const handleClientChange = (clientId?: number) => {
    if (!clientId) {
      return;
    }

    const selectedClient = clients.find((client) => client.id === clientId);
    if (!selectedClient) return;

    const normalizedDocument = selectedClient.cpfCnpj?.replaceAll(/[^0-9]/g, '') || '';
    const isCpf = normalizedDocument.length === 11;
    const isCnpj = normalizedDocument.length === 14;

    form.setFieldsValue({
      pixNome: selectedClient.name,
      pixCpf: isCpf ? normalizedDocument : undefined,
      pixCnpj: isCnpj ? normalizedDocument : undefined,
      pixDueNome: selectedClient.name,
      pixDueCpf: isCpf ? normalizedDocument : undefined,
      pixDueCnpj: isCnpj ? normalizedDocument : undefined,
      boletoNome: selectedClient.name,
      boletoCpf: isCpf ? normalizedDocument : undefined,
      boletoEmail: selectedClient.email || undefined,
    });
  };

  const handleSubmit = async (values: FormValues, action: PaymentAction) => {
    const selectedAccount = accounts.find((a) => a.id === values.bankAccountId);
    const bank = (selectedAccount?.bank ?? 'EFI') as PaymentBankType;
    const bankConfigurationId = values.bankConfigurationId;
    setSubmitting(true);
    try {
      if (action === 'DIRECT') {
        if (values.instrument === 'PIX_IMMEDIATE' && !values.pixAmount) {
          throw new Error('Informe o valor do PIX');
        }

        if (values.instrument === 'PIX_DUE') {
          if (!values.pixDueTxid) throw new Error('Informe o TXID');
          if (!values.pixDueDataVencimento) throw new Error('Informe a data de vencimento');
          if (!values.pixDueAmount) throw new Error('Informe o valor');
        }

        if (values.instrument === 'BOLETO') {
          if (!values.boletoNome) throw new Error('Informe o nome do cliente');
          if (!values.boletoCpf) throw new Error('Informe o CPF');
          if (!values.boletoExpireAt) throw new Error('Informe o vencimento');
          if (!values.boletoItemValue) throw new Error('Informe o valor');
        }
      }

      const buildCheckoutDescription = () => {
        if (values.instrument === 'PIX_IMMEDIATE') {
          return values.pixDescription?.trim() || `Checkout PIX Imediato R$ ${Number(values.pixAmount ?? 0).toFixed(2)}`;
        }

        if (values.instrument === 'PIX_DUE') {
          return values.pixDueSolicitacao?.trim() || (
            values.pixDueDataVencimento
              ? `Checkout PIX com vencimento ${values.pixDueDataVencimento}`
              : 'Checkout PIX com vencimento'
          );
        }

        return values.boletoMessage?.trim() || `Checkout boleto ${values.boletoItemName ?? 'Cobrança'}`;
      };

      const buildCheckoutAmount = () => {
        if (values.instrument === 'PIX_IMMEDIATE') {
          return values.pixAmount ?? 0;
        }

        if (values.instrument === 'PIX_DUE') {
          return values.pixDueAmount ?? 0;
        }

        return values.boletoItemValue ?? 0;
      };

      if (action === 'CHECKOUT') {
        const response = await PaymentApi.createCheckoutDraft({
          bankConfigurationId,
          amount: buildCheckoutAmount(),
          description: buildCheckoutDescription(),
          instrument: values.instrument,
          clientId: values.clientId,
          checkoutAccessMode: values.clientId ? 'CLIENT_LOGIN' : 'PUBLIC',
          pixKey:
            values.instrument === 'PIX_IMMEDIATE'
              ? values.pixChave || undefined
              : values.instrument === 'PIX_DUE'
                ? values.pixDueChave || undefined
                : undefined,
          expiresInHours: 24,
        });

        const newTx: Transaction = {
          id: String(response.id),
          amount: Number(response.amount ?? buildCheckoutAmount()),
          status: (response.status as Transaction['status']) || 'PENDING',
          description: response.description ?? buildCheckoutDescription(),
          createdAt: response.createdAt ?? new Date().toISOString(),
          bank: selectedAccount?.bank ?? 'EFI',
          instrument: values.instrument,
          externalReference: response.checkoutToken ? String(response.checkoutToken) : response.checkoutUrl,
          checkoutToken: response.checkoutToken,
          checkoutUrl: response.checkoutUrl,
          checkoutExpiresAt: response.checkoutExpiresAt,
        };

        setTransactions((prev) => [newTx, ...prev]);
        handleCloseModal();
        setResultModal({ kind: 'checkout', data: response });
        loadTransactions(0);
        setCurrentPage(1);
        return;
      }

      let response: ChargeResponseDTO;
      let description = '';

      if (values.instrument === 'PIX_IMMEDIATE') {
        description = values.pixDescription ?? '';
        response = await PaymentApi.createPixImmediate({
          bank,
          bankConfigurationId,
          clientId: values.clientId,
          payment: {
            amount: values.pixAmount!,
            solicitacaoPagador: values.pixDescription || undefined,
            chave: values.pixChave || undefined,
            expiracaoSeconds: values.pixExpiracao || undefined,
            cpf: values.pixCpf || undefined,
            cnpj: values.pixCnpj || undefined,
            nome: values.pixNome || undefined,
          },
        });
      } else if (values.instrument === 'PIX_DUE') {
        description = values.pixDueSolicitacao ?? `PIX Vencimento ${values.pixDueDataVencimento}`;
        response = await PaymentApi.createPixDue({
          bank,
          bankConfigurationId,
          clientId: values.clientId,
          payment: {
            txid: values.pixDueTxid!,
            dataDeVencimento: values.pixDueDataVencimento!,
            amountOriginal: values.pixDueAmount!,
            nome: values.pixDueNome || undefined,
            cpf: values.pixDueCpf || undefined,
            cnpj: values.pixDueCnpj || undefined,
            chave: values.pixDueChave || undefined,
            solicitacaoPagador: values.pixDueSolicitacao || undefined,
          },
        });
      } else {
        description = values.boletoMessage ?? `Boleto ${values.boletoNome}`;
        response = await PaymentApi.createBoleto({
          bank,
          bankConfigurationId,
          clientId: values.clientId,
          payment: {
            expireAt: values.boletoExpireAt!,
            message: values.boletoMessage || undefined,
            customer: {
              name: values.boletoNome || undefined,
              cpf: values.boletoCpf || undefined,
              email: values.boletoEmail || undefined,
            },
            items: [
              {
                name: values.boletoItemName ?? 'Cobrança',
                valueInCents: Math.round((values.boletoItemValue ?? 0) * 100),
                amount: 1,
              },
            ],
          },
        });
      }

      const newTx: Transaction = {
        id: response.id,
        amount: Number(response.amount ?? values.pixAmount ?? values.pixDueAmount ?? values.boletoItemValue ?? 0),
        status: (response.status as Transaction['status']) || 'PENDING',
        description,
        createdAt: new Date().toISOString(),
        bank: selectedAccount?.bank ?? 'EFI',
        instrument: values.instrument,
      };

      setTransactions((prev) => [newTx, ...prev]);
      handleCloseModal();
      setResultModal({ kind: 'charge', data: response });
      // Recarrega a lista para incluir a transação persistida com dados do BD
      loadTransactions(0);
      setCurrentPage(1);
    } catch (err: any) {
      message.error(err?.detail ?? err?.message ?? 'Erro ao processar pagamento');
    } finally {
      setSubmitting(false);
    }
  };

  const columns: ColumnsType<Transaction> = [
    {
      title: 'Data',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (date: string) =>
        new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(date)),
    },
    {
      title: 'Forma',
      dataIndex: 'instrument',
      key: 'instrument',
      width: 160,
      render: (inst: PaymentInstrument) => (
        <Tag color={INSTRUMENT_COLORS[inst]}>{INSTRUMENT_LABELS[inst] ?? inst}</Tag>
      ),
    },
    {
      title: 'Referência / Descrição',
      key: 'ref',
      render: (_: unknown, record: Transaction) => (
        <div>
          {record.externalReference && (
            <Text code copyable style={{ fontSize: 12 }}>{record.externalReference}</Text>
          )}
          {record.checkoutUrl && (
            <div style={{ marginTop: 4 }}>
              <a href={record.checkoutUrl} target="_blank" rel="noreferrer">Abrir checkout</a>
            </div>
          )}
          {record.description && (
            <div><Text type="secondary" style={{ fontSize: 12 }}>{record.description}</Text></div>
          )}
        </div>
      ),
    },
    {
      title: 'Banco',
      dataIndex: 'bank',
      key: 'bank',
      width: 80,
    },
    {
      title: 'Valor',
      dataIndex: 'amount',
      key: 'amount',
      width: 130,
      align: 'right',
      render: (value: number) =>
        new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: string) => (
        <Tag color={STATUS_COLORS[status] ?? 'default'}>{STATUS_LABELS[status] ?? status}</Tag>
      ),
    },
  ];

  // ─── CAMPOS DINÂMICOS ────────────────────────────────────────────────────────
  const pixImmediateFields = (
    <>
      <Form.Item
        label="Valor (R$)"
        name="pixAmount"
        rules={[{ required: true, message: 'Informe o valor' }, { type: 'number', min: 0.01, message: 'Mínimo R$ 0,01' }]}
      >
        <InputNumber style={{ width: '100%' }} placeholder="0,00" precision={2} min={0.01} decimalSeparator="," step={0.01} />
      </Form.Item>
      <Form.Item label="Descrição / Solicitação ao pagador" name="pixDescription">
        <Input placeholder="Ex: Pagamento de serviço (opcional)" />
      </Form.Item>
      <Form.Item label="Chave PIX" name="pixChave">
        <Input placeholder="CPF, CNPJ, e-mail, telefone ou chave aleatória (opcional)" />
      </Form.Item>
      <Form.Item label="Validade (segundos)" name="pixExpiracao" initialValue={3600}>
        <InputNumber style={{ width: '100%' }} min={60} />
      </Form.Item>
      <Divider plain style={{ fontSize: 12 }}>Devedor (opcional)</Divider>
      <Space.Compact style={{ width: '100%', gap: 8, display: 'flex' }}>
        <Form.Item name="pixNome" style={{ flex: 1 }}><Input placeholder="Nome" /></Form.Item>
        <Form.Item name="pixCpf" style={{ flex: 1 }}><Input placeholder="CPF" /></Form.Item>
        <Form.Item name="pixCnpj" style={{ flex: 1 }}><Input placeholder="CNPJ" /></Form.Item>
      </Space.Compact>
    </>
  );

  const pixDueFields = (
    <>
      <Form.Item
        label="TXID (identificador único)"
        name="pixDueTxid"
      >
        <Input placeholder="Ex: abc123 (32 chars alfanumérico)" />
      </Form.Item>
      <Space style={{ width: '100%', gap: 8, display: 'flex' }}>
        <Form.Item
          label="Data de Vencimento"
          name="pixDueDataVencimento"
          style={{ flex: 1 }}
        >
          <Input type="date" />
        </Form.Item>
        <Form.Item label="Valor (R$)" name="pixDueAmount" rules={[{ required: true, message: 'Informe o valor' }, { type: 'number', min: 0.01 }]} style={{ flex: 1 }}>
          <InputNumber style={{ width: '100%' }} placeholder="0,00" precision={2} min={0.01} decimalSeparator="," />
        </Form.Item>
      </Space>
      <Form.Item label="Solicitação ao pagador" name="pixDueSolicitacao">
        <Input placeholder="Opcional" />
      </Form.Item>
      <Form.Item label="Chave PIX do recebedor" name="pixDueChave">
        <Input placeholder="Opcional — usa chave padrão da configuração se omitida" />
      </Form.Item>
      <Divider plain style={{ fontSize: 12 }}>Devedor (opcional)</Divider>
      <Space.Compact style={{ width: '100%', gap: 8, display: 'flex' }}>
        <Form.Item name="pixDueNome" style={{ flex: 1 }}><Input placeholder="Nome" /></Form.Item>
        <Form.Item name="pixDueCpf" style={{ flex: 1 }}><Input placeholder="CPF" /></Form.Item>
        <Form.Item name="pixDueCnpj" style={{ flex: 1 }}><Input placeholder="CNPJ" /></Form.Item>
      </Space.Compact>
    </>
  );

  const boletoFields = (
    <>
      <Space style={{ width: '100%', gap: 8, display: 'flex' }}>
        <Form.Item
          label="Nome do cliente"
          name="boletoNome"
          style={{ flex: 2 }}
        >
          <Input />
        </Form.Item>
        <Form.Item
          label="CPF"
          name="boletoCpf"
          style={{ flex: 1 }}
        >
          <Input placeholder="000.000.000-00" />
        </Form.Item>
      </Space>
      <Space style={{ width: '100%', gap: 8, display: 'flex' }}>
        <Form.Item label="E-mail" name="boletoEmail" style={{ flex: 2 }}>
          <Input type="email" placeholder="Opcional" />
        </Form.Item>
        <Form.Item
          label="Vencimento"
          name="boletoExpireAt"
          style={{ flex: 1 }}
        >
          <Input type="date" />
        </Form.Item>
      </Space>
      <Divider plain style={{ fontSize: 12 }}>Item da cobrança</Divider>
      <Space style={{ width: '100%', gap: 8, display: 'flex' }}>
        <Form.Item
          label="Descrição do item"
          name="boletoItemName"
          initialValue="Cobrança"
          style={{ flex: 2 }}
        >
          <Input />
        </Form.Item>
        <Form.Item label="Valor (R$)" name="boletoItemValue" rules={[{ required: true, message: 'Informe o valor' }, { type: 'number', min: 0.01 }]} style={{ flex: 1 }}>
          <InputNumber style={{ width: '100%' }} placeholder="0,00" precision={2} min={0.01} decimalSeparator="," />
        </Form.Item>
      </Space>
      <Form.Item label="Mensagem no boleto" name="boletoMessage">
        <Input placeholder="Opcional" />
      </Form.Item>
    </>
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <Title level={3} style={{ marginBottom: 4 }}>
            Transações
          </Title>
          <Text type="secondary">Crie cobranças imediatas ou gere um checkout interno para completar depois.</Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleOpenModal}>
          Nova Transação
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={transactions}
        rowKey="id"
        loading={loadingTx}
        pagination={{
          current: currentPage,
          pageSize: PAGE_SIZE,
          total: totalTx,
          showTotal: (total) => `${total} transações`,
          showSizeChanger: false,
          hideOnSinglePage: true,
          onChange: (page) => {
            setCurrentPage(page);
            loadTransactions(page - 1);
          },
        }}
        locale={{
          emptyText: 'Nenhuma transação encontrada. Clique em "Nova Transação" para começar.',
        }}
        scroll={{ x: 650 }}
      />

      {/* ── Modal de criação ── */}
      <Modal
        title="Nova Transação"
        open={modalOpen}
        onCancel={handleCloseModal}
        cancelText="Cancelar"
        confirmLoading={submitting}
        width={600}
        destroyOnClose
        footer={[
          <Button key="cancel" onClick={handleCloseModal} disabled={submitting}>
            Cancelar
          </Button>,
          <Button
            key="checkout"
            onClick={async () => {
              try {
                const values = await form.validateFields();
                await handleSubmit(values, 'CHECKOUT');
              } catch {
                // Validação do formulário ou do submit
              }
            }}
            loading={submitting}
          >
            Salvar transação
          </Button>,
          <Button
            key="direct"
            type="primary"
            onClick={async () => {
              try {
                const values = await form.validateFields();
                await handleSubmit(values, 'DIRECT');
              } catch {
                // Validação do formulário ou do submit
              }
            }}
            loading={submitting}
          >
            Gerar no banco
          </Button>,
        ]}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ marginTop: 16 }}>
          {clients.length === 0 && !loadingClients && (
            <Alert
              showIcon
              type="warning"
              message="Nenhum cliente ativo cadastrado"
              description="Cadastre um cliente para habilitar a emissão direta no banco."
              style={{ marginBottom: 16 }}
            />
          )}

          <Alert
            showIcon
            type="info"
            message="Ações disponíveis"
            description="Use 'Salvar transação' para criar o checkout interno primeiro. Use 'Gerar no banco' para emitir a cobrança imediatamente na EFI."
            style={{ marginBottom: 16 }}
          />

          <Form.Item
            label="Cliente"
            name="clientId"
            extra="Opcional. Se informado, a transação fica vinculada ao cliente e com link de checkout."
          >
            <Select
              placeholder="Selecione um cliente"
              loading={loadingClients}
              allowClear
              onChange={handleClientChange}
              options={clients.map((c) => ({
                value: c.id,
                label: `${c.name} (${c.cpfCnpj})`,
              }))}
              notFoundContent={loadingClients ? 'Carregando...' : 'Nenhum cliente ativo cadastrado'}
            />
          </Form.Item>

          {/* Conta bancária */}
          <Form.Item
            label="Conta Bancária"
            name="bankAccountId"
            rules={[{ required: true, message: 'Selecione a conta bancária' }]}
          >
            <Select
              placeholder="Selecione a conta bancária"
              loading={loadingAccounts}
              onChange={handleAccountChange}
              options={accounts.map((a) => ({
                value: a.id,
                label: (
                  <Space>
                    <Tag style={{ margin: 0 }}>{a.bank}</Tag>
                    {a.name}
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      Ag. {a.cdAgency} / {a.cdAccount}-{a.cdAccountDigit}
                    </Text>
                  </Space>
                ),
              }))}
              notFoundContent={loadingAccounts ? 'Carregando...' : 'Nenhuma conta ativa cadastrada'}
            />
          </Form.Item>

          {/* Ambiente */}
          <Form.Item
            label="Ambiente"
            name="bankConfigurationId"
            rules={[{ required: true, message: 'Selecione o ambiente' }]}
          >
            <Select
              placeholder="Selecione o ambiente (Sandbox / Produção)"
              loading={loadingConfigs}
              disabled={configs.length === 0 && !loadingConfigs}
              options={configs.map((c) => ({
                value: c.id,
                label: (
                  <Space>
                    {c.environment === Environment.PRODUCTION ? (
                      <Tag color="blue" style={{ margin: 0 }}>Produção</Tag>
                    ) : (
                      <Tag color="orange" style={{ margin: 0 }}>Sandbox</Tag>
                    )}
                    {c.environment === Environment.PRODUCTION ? 'Produção' : 'Homologação (Sandbox)'}
                  </Space>
                ),
              }))}
              notFoundContent={
                loadingConfigs
                  ? 'Carregando...'
                  : form.getFieldValue('bankAccountId')
                  ? 'Nenhuma configuração ativa. Configure o banco primeiro.'
                  : 'Selecione uma conta primeiro'
              }
            />
          </Form.Item>

          {/* Forma de pagamento */}
          <Form.Item
            label="Forma de Pagamento"
            name="instrument"
            initialValue="PIX_IMMEDIATE"
            rules={[{ required: true }]}
          >
            <Select
              onChange={(val) => setInstrument(val as PaymentInstrument)}
              options={[
                { value: 'PIX_IMMEDIATE', label: '⚡ PIX Imediato' },
                { value: 'PIX_DUE', label: '📅 PIX com Vencimento' },
                { value: 'BOLETO', label: '🏦 Boleto Bancário' },
              ]}
            />
          </Form.Item>

          <Divider />

          {instrument === 'PIX_IMMEDIATE' && pixImmediateFields}
          {instrument === 'PIX_DUE' && pixDueFields}
          {instrument === 'BOLETO' && boletoFields}
        </Form>
      </Modal>

      {/* ── Modal de resultado ── */}
      <Modal
        title={resultModal?.kind === 'checkout' ? 'Checkout criado com sucesso' : 'Cobrança criada com sucesso'}
        open={!!resultModal}
        onCancel={() => setResultModal(null)}
        footer={[
          <Button key="close" type="primary" onClick={() => setResultModal(null)}>Fechar</Button>,
        ]}
        destroyOnClose
      >
        {resultModal && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 8 }}>
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {resultModal.kind === 'checkout' ? 'ID da transação' : 'ID da cobrança'}
              </Text>
              <br />
              <Text copyable>{resultModal.data.id}</Text>
            </div>

            {resultModal.kind === 'checkout' && resultModal.data.checkoutUrl && (
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>Link do checkout</Text>
                <Alert
                  message={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Text
                        style={{ flex: 1, wordBreak: 'break-all', fontSize: 12, fontFamily: 'monospace' }}
                      >
                        {resultModal.data.checkoutUrl}
                      </Text>
                      <Button
                        size="small"
                        icon={<CopyOutlined />}
                        onClick={() => {
                          navigator.clipboard.writeText(resultModal.data.checkoutUrl!);
                          message.success('Copiado!');
                        }}
                      />
                      <Button size="small" href={resultModal.data.checkoutUrl} target="_blank" rel="noreferrer">
                        Abrir
                      </Button>
                    </div>
                  }
                  type="info"
                  style={{ marginTop: 4 }}
                />
              </div>
            )}

            {resultModal.kind === 'charge' && resultModal.data.pixCopiaECola && (
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>PIX Copia e Cola</Text>
                <Alert
                  message={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Text
                        style={{ flex: 1, wordBreak: 'break-all', fontSize: 12, fontFamily: 'monospace' }}
                      >
                        {resultModal.data.pixCopiaECola}
                      </Text>
                      <Button
                        size="small"
                        icon={<CopyOutlined />}
                        onClick={() => {
                          navigator.clipboard.writeText(resultModal.data.pixCopiaECola!);
                          message.success('Copiado!');
                        }}
                      />
                    </div>
                  }
                  type="info"
                  style={{ marginTop: 4 }}
                />
              </div>
            )}

            {resultModal.kind === 'charge' && resultModal.data.barcode && (
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>Linha Digitável</Text>
                <Alert
                  message={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Text style={{ flex: 1, wordBreak: 'break-all', fontSize: 12, fontFamily: 'monospace' }}>
                        {resultModal.data.barcode}
                      </Text>
                      <Button
                        size="small"
                        icon={<CopyOutlined />}
                        onClick={() => {
                          navigator.clipboard.writeText(resultModal.data.barcode!);
                          message.success('Copiado!');
                        }}
                      />
                    </div>
                  }
                  type="info"
                  style={{ marginTop: 4 }}
                />
              </div>
            )}

            {resultModal.kind === 'charge' && (resultModal.data.billetLink || resultModal.data.pdfLink || resultModal.data.link) && (
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {resultModal.data.billetLink && (
                  <Button href={resultModal.data.billetLink} target="_blank" size="small">
                    Abrir Boleto
                  </Button>
                )}
                {resultModal.data.pdfLink && (
                  <Button href={resultModal.data.pdfLink} target="_blank" size="small">
                    PDF
                  </Button>
                )}
                {resultModal.data.link && (
                  <Button href={resultModal.data.link} target="_blank" size="small">
                    Link de Pagamento
                  </Button>
                )}
              </div>
            )}

            <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
              {resultModal.data.amount && (
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Valor</Text>
                  <br />
                  <Text strong>
                    {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(resultModal.data.amount))}
                  </Text>
                </div>
              )}
              {resultModal.data.status && (
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Status</Text>
                  <br />
                  <Tag color={STATUS_COLORS[resultModal.data.status] ?? 'default'}>
                    {STATUS_LABELS[resultModal.data.status] ?? resultModal.data.status}
                  </Tag>
                </div>
              )}
              {resultModal.kind === 'charge' && resultModal.data.dueDate && (
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Vencimento</Text>
                  <br />
                  <Text>{resultModal.data.dueDate}</Text>
                </div>
              )}
              {resultModal.kind === 'checkout' && resultModal.data.checkoutExpiresAt && (
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Expira em</Text>
                  <br />
                  <Text>{new Date(resultModal.data.checkoutExpiresAt).toLocaleString('pt-BR')}</Text>
                </div>
              )}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
