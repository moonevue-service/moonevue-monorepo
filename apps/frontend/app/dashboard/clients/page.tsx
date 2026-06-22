'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { EditOutlined, PlusOutlined } from '@ant-design/icons';
import { ClientsApi, ClientSummary, ClientUpsertRequest, TransactionSummary } from '@/lib/api';
import { useAuth } from '@/app/providers';
import { canAccessClients } from '@/lib/authz';

const { Title, Text } = Typography;

type ClientFormValues = ClientUpsertRequest;

function onlyDigits(value?: string): string {
  return (value ?? '').replace(/\D/g, '');
}

function formatCpfCnpj(value?: string): string {
  const digits = onlyDigits(value).slice(0, 14);
  if (digits.length <= 11) {
    return digits
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
  }
  return digits
    .replace(/(\d{2})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1/$2')
    .replace(/(\d{4})(\d{1,2})$/, '$1-$2');
}

function formatPhoneBr(value?: string): string {
  const digits = onlyDigits(value).slice(0, 11);
  if (digits.length <= 10) {
    return digits
      .replace(/(\d{2})(\d)/, '($1) $2')
      .replace(/(\d{4})(\d{1,4})$/, '$1-$2');
  }
  return digits
    .replace(/(\d{2})(\d)/, '($1) $2')
    .replace(/(\d{5})(\d{1,4})$/, '$1-$2');
}

function isValidCpf(cpf: string): boolean {
  if (cpf.length !== 11 || /^([0-9])\1+$/.test(cpf)) return false;
  const calc = (base: string, factor: number) => {
    let total = 0;
    for (let i = 0; i < base.length; i++) total += Number(base[i]) * (factor - i);
    const mod = 11 - (total % 11);
    return mod > 9 ? 0 : mod;
  };
  const d1 = calc(cpf.slice(0, 9), 10);
  const d2 = calc(cpf.slice(0, 9) + d1, 11);
  return cpf === cpf.slice(0, 9) + String(d1) + String(d2);
}

function isValidCnpj(cnpj: string): boolean {
  if (cnpj.length !== 14 || /^([0-9])\1+$/.test(cnpj)) return false;
  const calc = (base: string, weights: number[]) => {
    const total = base.split('').reduce((sum, n, i) => sum + Number(n) * weights[i], 0);
    const mod = total % 11;
    return mod < 2 ? 0 : 11 - mod;
  };
  const d1 = calc(cnpj.slice(0, 12), [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
  const d2 = calc(cnpj.slice(0, 12) + d1, [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
  return cnpj === cnpj.slice(0, 12) + String(d1) + String(d2);
}

function isValidDocument(value?: string): boolean {
  const digits = onlyDigits(value);
  if (digits.length === 11) return isValidCpf(digits);
  if (digits.length === 14) return isValidCnpj(digits);
  return false;
}

function isValidPhone(value?: string): boolean {
  const digits = onlyDigits(value);
  if (!(digits.length === 10 || digits.length === 11)) return false;
  if (/^([0-9])\1+$/.test(digits)) return false;
  const ddd = Number(digits.slice(0, 2));
  if (ddd < 11 || ddd > 99) return false;
  if (digits.length === 11 && digits[2] !== '9') return false;
  return true;
}

export default function ClientsPage() {
  const { message } = App.useApp();
  const { user } = useAuth();
  const [form] = Form.useForm<ClientFormValues>();

  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [loadingClients, setLoadingClients] = useState(false);
  const [clientsPage, setClientsPage] = useState(1);
  const [clientsTotal, setClientsTotal] = useState(0);

  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [editingClient, setEditingClient] = useState<ClientSummary | null>(null);
  const [searchText, setSearchText] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');

  const [selectedClient, setSelectedClient] = useState<ClientSummary | null>(null);
  const [clientTransactions, setClientTransactions] = useState<TransactionSummary[]>([]);
  const [loadingClientTransactions, setLoadingClientTransactions] = useState(false);

  const PAGE_SIZE = 50;

  const allowed = canAccessClients(user?.roles, user?.permissions);

  const loadClients = async (page = 0) => {
    setLoadingClients(true);
    try {
      const data = await ClientsApi.list({ page, size: PAGE_SIZE });
      setClients(data.content);
      setClientsTotal(data.totalElements);
    } catch {
      message.error('Erro ao carregar clientes');
    } finally {
      setLoadingClients(false);
    }
  };

  useEffect(() => {
    if (!allowed) {
      return;
    }
    loadClients(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allowed]);

  if (!allowed) {
    return (
      <div style={{ maxWidth: 760 }}>
        <Typography.Title level={3} style={{ marginBottom: 8 }}>Clientes</Typography.Title>
        <Typography.Paragraph type="secondary">
          Seu perfil atual não possui acesso a esta área.
        </Typography.Paragraph>
        <Tag color="red">Permissão necessária: customers.read</Tag>
      </div>
    );
  }

  const openCreateModal = () => {
    setEditingClient(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEditModal = (client: ClientSummary) => {
    setEditingClient(client);
    form.setFieldsValue({
      name: client.name,
      cpfCnpj: formatCpfCnpj(client.cpfCnpj),
      email: client.email,
      phone: formatPhoneBr(client.phone),
    });
    setModalOpen(true);
  };

  const handleUpsertClient = async (values: ClientFormValues) => {
    setSubmitting(true);
    try {
      const payload: ClientUpsertRequest = {
        name: values.name.trim(),
        cpfCnpj: onlyDigits(values.cpfCnpj),
        email: values.email.trim().toLowerCase(),
        phone: values.phone ? onlyDigits(values.phone) : undefined,
      };
      if (editingClient) {
        await ClientsApi.update(editingClient.id, payload);
      } else {
        await ClientsApi.create(payload);
      }
      setModalOpen(false);
      setEditingClient(null);
      setClientsPage(1);
      await loadClients(0);
      message.success(editingClient ? 'Cliente atualizado com sucesso' : 'Cliente criado com sucesso');
    } catch (e: any) {
      message.error(e?.message || (editingClient ? 'Erro ao atualizar cliente' : 'Erro ao criar cliente'));
    } finally {
      setSubmitting(false);
    }
  };

  const openClientDetails = async (client: ClientSummary) => {
    setSelectedClient(client);
    setLoadingClientTransactions(true);
    try {
      const txPage = await ClientsApi.listTransactions(client.id, { page: 0, size: 100 });
      setClientTransactions(txPage.content);
    } catch {
      message.error('Erro ao carregar histórico do cliente');
    } finally {
      setLoadingClientTransactions(false);
    }
  };

  const filteredClients = useMemo(() => {
    const normalized = searchText.trim().toLowerCase();

    return clients.filter((client) => {
      const matchesText =
        !normalized ||
        client.name.toLowerCase().includes(normalized) ||
        client.email.toLowerCase().includes(normalized) ||
        client.cpfCnpj.toLowerCase().includes(normalized);

      const matchesStatus = statusFilter === 'ALL' || client.status === statusFilter;

      return matchesText && matchesStatus;
    });
  }, [clients, searchText, statusFilter]);

  const clientColumns: ColumnsType<ClientSummary> = [
    {
      title: 'Nome',
      dataIndex: 'name',
      key: 'name',
      render: (value: string, row) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => openClientDetails(row)}>
          {value}
        </Button>
      ),
    },
    {
      title: 'Documento',
      dataIndex: 'cpfCnpj',
      key: 'cpfCnpj',
      width: 180,
      render: (value: string) => formatCpfCnpj(value),
    },
    { title: 'E-mail', dataIndex: 'email', key: 'email' },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: string) => <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{status}</Tag>,
    },
    {
      title: 'Criado em',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (value: string) =>
        new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)),
    },
    {
      title: 'Ações',
      key: 'actions',
      width: 120,
      render: (_, row) => (
        <Button type="link" icon={<EditOutlined />} onClick={() => openEditModal(row)}>
          Editar
        </Button>
      ),
    },
  ];

  const txColumns: ColumnsType<TransactionSummary> = [
    {
      title: 'Data',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (value: string) =>
        new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)),
    },
    {
      title: 'Descrição',
      dataIndex: 'description',
      key: 'description',
      render: (value?: string) => value || '-',
    },
    {
      title: 'Valor',
      dataIndex: 'amount',
      key: 'amount',
      width: 120,
      align: 'right',
      render: (value: string) =>
        new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(value || 0)),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (value: string) => <Tag>{value}</Tag>,
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <Title level={3} style={{ marginBottom: 4 }}>Clientes</Title>
          <Text type="secondary">Cadastre clientes e acompanhe o histórico de transações.</Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
          Novo cliente
        </Button>
      </div>

      <Table
        columns={clientColumns}
        dataSource={filteredClients}
        rowKey="id"
        loading={loadingClients}
        title={() => (
          <Space wrap>
            <Input.Search
              allowClear
              placeholder="Buscar por nome, documento ou e-mail"
              style={{ width: 320 }}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
            />
            <Select
              value={statusFilter}
              style={{ width: 180 }}
              onChange={(value) => setStatusFilter(value)}
              options={[
                { label: 'Todos os status', value: 'ALL' },
                { label: 'Ativo', value: 'ACTIVE' },
                { label: 'Inativo', value: 'INACTIVE' },
              ]}
            />
          </Space>
        )}
        pagination={{
          current: clientsPage,
          pageSize: PAGE_SIZE,
          total: clientsTotal,
          showSizeChanger: false,
          onChange: (page) => {
            setClientsPage(page);
            loadClients(page - 1);
          },
        }}
        locale={{ emptyText: 'Nenhum cliente cadastrado.' }}
      />

      <Modal
        title={editingClient ? 'Editar cliente' : 'Novo cliente'}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          setEditingClient(null);
        }}
        onOk={() => form.submit()}
        okText={editingClient ? 'Salvar alterações' : 'Salvar'}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleUpsertClient}>
          <Form.Item
            label="Nome"
            name="name"
            rules={[
              { required: true, message: 'Informe o nome' },
              { max: 120, message: 'Máximo de 120 caracteres' },
            ]}
          >
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item
            label="CPF/CNPJ"
            name="cpfCnpj"
            rules={[
              { required: true, message: 'Informe o documento' },
              {
                validator: async (_, value) => {
                  if (!value || isValidDocument(value)) return;
                  throw new Error('CPF/CNPJ inválido');
                },
              },
            ]}
          >
            <Input
              maxLength={18}
              placeholder="000.000.000-00 ou 00.000.000/0000-00"
              onChange={(e) => form.setFieldValue('cpfCnpj', formatCpfCnpj(e.target.value))}
            />
          </Form.Item>
          <Form.Item
            label="E-mail"
            name="email"
            rules={[
              { required: true, message: 'Informe o e-mail' },
              { type: 'email', message: 'E-mail inválido' },
              { max: 180, message: 'Máximo de 180 caracteres' },
            ]}
          >
            <Input maxLength={180} />
          </Form.Item>
          <Form.Item
            label="Telefone"
            name="phone"
            rules={[
              {
                validator: async (_, value) => {
                  if (!value || isValidPhone(value)) return;
                  throw new Error('Telefone inválido. Ex: (63) 99999-9999');
                },
              },
            ]}
          >
            <Input
              maxLength={15}
              placeholder="(63) 99999-9999"
              onChange={(e) => form.setFieldValue('phone', formatPhoneBr(e.target.value))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={selectedClient ? `Cliente: ${selectedClient.name}` : 'Cliente'}
        open={!!selectedClient}
        onClose={() => {
          setSelectedClient(null);
          setClientTransactions([]);
        }}
        size="large"
      >
        {selectedClient && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Space direction="vertical" size={2}>
              <Text><strong>Documento:</strong> {formatCpfCnpj(selectedClient.cpfCnpj)}</Text>
              <Text><strong>E-mail:</strong> {selectedClient.email}</Text>
              <Text><strong>Telefone:</strong> {selectedClient.phone ? formatPhoneBr(selectedClient.phone) : '-'}</Text>
            </Space>

            <Title level={5} style={{ marginBottom: 0 }}>Histórico de transações</Title>

            <Table
              columns={txColumns}
              dataSource={clientTransactions}
              rowKey="id"
              loading={loadingClientTransactions}
              pagination={false}
              locale={{ emptyText: 'Nenhuma transação vinculada a este cliente.' }}
            />
          </Space>
        )}
      </Drawer>
    </div>
  );
}
