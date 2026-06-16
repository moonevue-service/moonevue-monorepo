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
      cpfCnpj: client.cpfCnpj,
      email: client.email,
      phone: client.phone,
    });
    setModalOpen(true);
  };

  const handleUpsertClient = async (values: ClientFormValues) => {
    setSubmitting(true);
    try {
      if (editingClient) {
        await ClientsApi.update(editingClient.id, values);
      } else {
        await ClientsApi.create(values);
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
    { title: 'Documento', dataIndex: 'cpfCnpj', key: 'cpfCnpj', width: 160 },
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
          <Form.Item label="Nome" name="name" rules={[{ required: true, message: 'Informe o nome' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="CPF/CNPJ" name="cpfCnpj" rules={[{ required: true, message: 'Informe o documento' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="E-mail" name="email" rules={[{ required: true, message: 'Informe o e-mail' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Telefone" name="phone">
            <Input />
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
              <Text><strong>Documento:</strong> {selectedClient.cpfCnpj}</Text>
              <Text><strong>E-mail:</strong> {selectedClient.email}</Text>
              <Text><strong>Telefone:</strong> {selectedClient.phone || '-'}</Text>
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
