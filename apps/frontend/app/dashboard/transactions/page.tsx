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
  Environment,
  FinanceApi,
  PageResponse,
  PaymentApi,
  PaymentBankType,
  TransactionSummary,
} from '@/lib/api';

const { Title, Text } = Typography;

type PaymentInstrument = 'PIX_IMMEDIATE' | 'PIX_DUE' | 'BOLETO';

interface Transaction {
  id: string;
  amount: number;
  status: string;
  description: string;
  createdAt: string;
  bank: string;
  instrument: PaymentInstrument;
  externalReference?: string;
}

// FormValues tipada por forma de pagamento
type FormValues = {
  bankAccountId: number;
  bankConfigurationId: number;
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
  const [resultModal, setResultModal] = useState<ChargeResponseDTO | null>(null);
  const [loadingTx, setLoadingTx] = useState(false);
  const [totalTx, setTotalTx] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const PAGE_SIZE = 50;

  // Dados das contas e configs
  const [accounts, setAccounts] = useState<BankAccountResponse[]>([]);
  const [configs, setConfigs] = useState<BankConfigurationResponse[]>([]);
  const [loadingAccounts, setLoadingAccounts] = useState(false);
  const [loadingConfigs, setLoadingConfigs] = useState(false);

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
    if (!user?.tenantId || accounts.length > 0) return;
    setLoadingAccounts(true);
    try {
      const data = await FinanceApi.listBankAccounts(user.tenantId);
      setAccounts(data.filter((a) => a.active));
    } catch {
      message.error('Erro ao carregar contas bancárias');
    } finally {
      setLoadingAccounts(false);
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

  const handleSubmit = async (values: FormValues) => {
    const selectedAccount = accounts.find((a) => a.id === values.bankAccountId);
    const bank = (selectedAccount?.bank ?? 'EFI') as PaymentBankType;
    const bankConfigurationId = values.bankConfigurationId;
    setSubmitting(true);
    try {
      let response: ChargeResponseDTO;
      let description = '';

      if (values.instrument === 'PIX_IMMEDIATE') {
        description = values.pixDescription ?? '';
        response = await PaymentApi.createPixImmediate({
          bank,
          bankConfigurationId,
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
      setResultModal(response);
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
      <Form.Item label="TXID (identificador único)" name="pixDueTxid" rules={[{ required: true, message: 'Informe o TXID' }]}>
        <Input placeholder="Ex: abc123 (32 chars alfanumérico)" />
      </Form.Item>
      <Space style={{ width: '100%', gap: 8, display: 'flex' }}>
        <Form.Item label="Data de Vencimento" name="pixDueDataVencimento" rules={[{ required: true, message: 'Informe a data' }]} style={{ flex: 1 }}>
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
        <Form.Item label="Nome do cliente" name="boletoNome" rules={[{ required: true, message: 'Informe o nome' }]} style={{ flex: 2 }}>
          <Input />
        </Form.Item>
        <Form.Item label="CPF" name="boletoCpf" rules={[{ required: true, message: 'Informe o CPF' }]} style={{ flex: 1 }}>
          <Input placeholder="000.000.000-00" />
        </Form.Item>
      </Space>
      <Space style={{ width: '100%', gap: 8, display: 'flex' }}>
        <Form.Item label="E-mail" name="boletoEmail" style={{ flex: 2 }}>
          <Input type="email" placeholder="Opcional" />
        </Form.Item>
        <Form.Item label="Vencimento" name="boletoExpireAt" rules={[{ required: true, message: 'Informe a data' }]} style={{ flex: 1 }}>
          <Input type="date" />
        </Form.Item>
      </Space>
      <Divider plain style={{ fontSize: 12 }}>Item da cobrança</Divider>
      <Space style={{ width: '100%', gap: 8, display: 'flex' }}>
        <Form.Item label="Descrição do item" name="boletoItemName" initialValue="Cobrança" rules={[{ required: true }]} style={{ flex: 2 }}>
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
          <Text type="secondary">Crie cobranças e acompanhe o histórico da sessão.</Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleOpenModal}>
          Novo Pagamento
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
          emptyText: 'Nenhuma transação encontrada. Clique em "Novo Pagamento" para criar uma cobrança.',
        }}
        scroll={{ x: 650 }}
      />

      {/* ── Modal de criação ── */}
      <Modal
        title="Nova Cobrança"
        open={modalOpen}
        onCancel={handleCloseModal}
        onOk={() => form.submit()}
        okText="Processar pagamento"
        cancelText="Cancelar"
        confirmLoading={submitting}
        width={600}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ marginTop: 16 }}>
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
        title="Cobrança criada com sucesso"
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
              <Text type="secondary" style={{ fontSize: 12 }}>ID da cobrança</Text>
              <br />
              <Text copyable>{resultModal.id}</Text>
            </div>

            {resultModal.pixCopiaECola && (
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>PIX Copia e Cola</Text>
                <Alert
                  message={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Text
                        style={{ flex: 1, wordBreak: 'break-all', fontSize: 12, fontFamily: 'monospace' }}
                      >
                        {resultModal.pixCopiaECola}
                      </Text>
                      <Button
                        size="small"
                        icon={<CopyOutlined />}
                        onClick={() => {
                          navigator.clipboard.writeText(resultModal.pixCopiaECola!);
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

            {resultModal.barcode && (
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>Linha Digitável</Text>
                <Alert
                  message={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Text style={{ flex: 1, wordBreak: 'break-all', fontSize: 12, fontFamily: 'monospace' }}>
                        {resultModal.barcode}
                      </Text>
                      <Button
                        size="small"
                        icon={<CopyOutlined />}
                        onClick={() => {
                          navigator.clipboard.writeText(resultModal.barcode!);
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

            {(resultModal.billetLink || resultModal.pdfLink || resultModal.link) && (
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {resultModal.billetLink && (
                  <Button href={resultModal.billetLink} target="_blank" size="small">
                    Abrir Boleto
                  </Button>
                )}
                {resultModal.pdfLink && (
                  <Button href={resultModal.pdfLink} target="_blank" size="small">
                    PDF
                  </Button>
                )}
                {resultModal.link && (
                  <Button href={resultModal.link} target="_blank" size="small">
                    Link de Pagamento
                  </Button>
                )}
              </div>
            )}

            <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
              {resultModal.amount && (
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Valor</Text>
                  <br />
                  <Text strong>
                    {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(resultModal.amount))}
                  </Text>
                </div>
              )}
              {resultModal.status && (
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Status</Text>
                  <br />
                  <Tag color={STATUS_COLORS[resultModal.status] ?? 'default'}>
                    {STATUS_LABELS[resultModal.status] ?? resultModal.status}
                  </Tag>
                </div>
              )}
              {resultModal.dueDate && (
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Vencimento</Text>
                  <br />
                  <Text>{resultModal.dueDate}</Text>
                </div>
              )}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
