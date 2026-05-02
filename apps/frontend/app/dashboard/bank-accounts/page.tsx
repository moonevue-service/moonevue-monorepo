'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  App,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, SettingOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useAuth } from '@/app/providers';
import { AccountType, BankAccountResponse, BankType, FinanceApi } from '@/lib/api';

const { Title, Text } = Typography;

type FormValues = {
  name: string;
  bank: BankType;
  cdAgency: string;
  cdAccount: string;
  cdAccountDigit: string;
  accountType: AccountType;
};

export default function BankAccountsPage() {
  const { user } = useAuth();
  const { message } = App.useApp();
  const router = useRouter();
  const [form] = Form.useForm<FormValues>();
  const [accounts, setAccounts] = useState<BankAccountResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);

  useEffect(() => {
    if (user?.tenantId) loadAccounts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.tenantId]);

  const loadAccounts = async () => {
    if (!user?.tenantId) return;
    setLoading(true);
    try {
      const data = await FinanceApi.listBankAccounts(user.tenantId);
      setAccounts(data);
    } catch (err: any) {
      message.error(err?.message || 'Erro ao carregar contas bancárias');
    } finally {
      setLoading(false);
    }
  };

  const openCreate = () => {
    setEditingId(null);
    form.resetFields();
    form.setFieldsValue({ bank: 'EFI' as BankType, accountType: 'CHECKING' as AccountType });
    setModalOpen(true);
  };

  const openEdit = (record: BankAccountResponse) => {
    setEditingId(record.id);
    form.setFieldsValue({
      name: record.name,
      bank: record.bank as BankType,
      cdAgency: record.cdAgency,
      cdAccount: record.cdAccount,
      cdAccountDigit: record.cdAccountDigit,
      accountType: record.accountType as AccountType,
    });
    setModalOpen(true);
  };

  const handleSubmit = async (values: FormValues) => {
    if (!user?.tenantId) return;
    setSubmitting(true);
    try {
      if (editingId !== null) {
        const updated = await FinanceApi.updateBankAccount(user.tenantId, editingId, {
          ...values,
          active: true,
        });
        setAccounts((prev) => prev.map((a) => (a.id === editingId ? updated : a)));
        message.success('Conta atualizada com sucesso');
      } else {
        const created = await FinanceApi.createBankAccount(user.tenantId, {
          ...values,
          active: true,
        });
        setAccounts((prev) => [...prev, created]);
        message.success('Conta criada com sucesso');
      }
      setModalOpen(false);
    } catch (err: any) {
      message.error(err?.message || 'Erro ao salvar conta bancária');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!user?.tenantId) return;
    try {
      await FinanceApi.deleteBankAccount(user.tenantId, id);
      setAccounts((prev) => prev.filter((a) => a.id !== id));
      message.success('Conta removida');
    } catch (err: any) {
      message.error(err?.message || 'Erro ao remover conta');
    }
  };

  const columns: ColumnsType<BankAccountResponse> = [
    { title: 'Nome', dataIndex: 'name', key: 'name' },
    { title: 'Banco', dataIndex: 'bank', key: 'bank', width: 80 },
    { title: 'Agência', dataIndex: 'cdAgency', key: 'cdAgency', width: 100 },
    {
      title: 'Conta',
      key: 'account',
      width: 130,
      render: (_, r) => `${r.cdAccount}-${r.cdAccountDigit}`,
    },
    {
      title: 'Tipo',
      dataIndex: 'accountType',
      key: 'accountType',
      width: 110,
      render: (type) => (type === 'CHECKING' ? 'Corrente' : 'Poupança'),
    },
    {
      title: 'Status',
      dataIndex: 'active',
      key: 'active',
      width: 90,
      render: (active: boolean) => (
        <Tag color={active ? 'success' : 'default'}>{active ? 'Ativa' : 'Inativa'}</Tag>
      ),
    },
    {
      title: 'Ações',
      key: 'actions',
      width: 130,
      render: (_, record) => (
        <Space>
          <Tooltip title="Configurações EFI">
            <Button
              type="text"
              size="small"
              icon={<SettingOutlined />}
              onClick={() => router.push(`/dashboard/bank-accounts/${record.id}/config`)}
            />
          </Tooltip>
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEdit(record)}
          />
          <Popconfirm
            title="Remover conta bancária"
            description="Esta ação não pode ser desfeita."
            onConfirm={() => handleDelete(record.id)}
            okText="Remover"
            cancelText="Cancelar"
            okButtonProps={{ danger: true }}
          >
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <Title level={3} style={{ marginBottom: 4 }}>
            Contas Bancárias
          </Title>
          <Text type="secondary">Cadastre e gerencie contas usadas nas integrações.</Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Nova Conta
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={accounts}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 10, showSizeChanger: false, hideOnSinglePage: true }}
        locale={{ emptyText: 'Nenhuma conta bancária configurada. Clique em "Nova Conta" para começar.' }}
        scroll={{ x: 600 }}
      />

      <Modal
        title={editingId !== null ? 'Editar Conta Bancária' : 'Nova Conta Bancária'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        okText={editingId !== null ? 'Salvar alterações' : 'Adicionar conta'}
        cancelText="Cancelar"
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ marginTop: 16 }}>
          <Form.Item
            label="Nome da Conta"
            name="name"
            rules={[{ required: true, message: 'Informe o nome da conta' }]}
          >
            <Input placeholder="Ex: Conta Principal" />
          </Form.Item>

          <Form.Item label="Banco" name="bank" rules={[{ required: true }]}>
            <Select options={[{ value: 'EFI', label: 'EFI (Efí Pay)' }]} />
          </Form.Item>

          <Form.Item label="Tipo de Conta" name="accountType" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'CHECKING', label: 'Corrente' },
                { value: 'SAVINGS', label: 'Poupança' },
              ]}
            />
          </Form.Item>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Form.Item
              label="Agência"
              name="cdAgency"
              rules={[{ required: true, message: 'Informe a agência' }]}
            >
              <Input placeholder="1234" />
            </Form.Item>
            <Form.Item
              label="Conta"
              name="cdAccount"
              rules={[{ required: true, message: 'Informe o número da conta' }]}
            >
              <Input placeholder="123456" />
            </Form.Item>
          </div>

          <Form.Item
            label="Dígito verificador"
            name="cdAccountDigit"
            rules={[
              { required: true, message: 'Informe o dígito' },
              { max: 2, message: 'Máximo 2 caracteres' },
            ]}
          >
            <Input placeholder="0" maxLength={2} style={{ width: 80 }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
