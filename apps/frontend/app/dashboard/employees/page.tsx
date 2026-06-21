'use client';

import { useMemo, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Form,
  Input,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CopyOutlined, ReloadOutlined, UserAddOutlined } from '@ant-design/icons';
import { AuthApi } from '@/lib/api';
import { useAuth } from '@/app/providers';
import { canManageEmployees } from '@/lib/authz';

const { Title, Text } = Typography;

type EmployeeFormValues = {
  email: string;
  password: string;
};

type CreatedEmployee = {
  key: string;
  userId: number;
  email: string;
  tenantId: number;
  createdAt: string;
};

function generatePassword() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%';
  let value = '';
  for (let i = 0; i < 14; i += 1) {
    value += chars[Math.floor(Math.random() * chars.length)];
  }
  return value;
}

export default function EmployeesPage() {
  const { message } = App.useApp();
  const { user } = useAuth();
  const [form] = Form.useForm<EmployeeFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [createdEmployees, setCreatedEmployees] = useState<CreatedEmployee[]>([]);
  const [searchText, setSearchText] = useState('');

  const allowed = canManageEmployees(user?.roles, user?.permissions);

  const initialPassword = useMemo(() => generatePassword(), []);

  const handleGeneratePassword = () => {
    form.setFieldValue('password', generatePassword());
  };

  const handleCopyPassword = async () => {
    const password = form.getFieldValue('password');
    if (!password) {
      message.warning('Gere uma senha antes de copiar');
      return;
    }
    await navigator.clipboard.writeText(password);
    message.success('Senha copiada');
  };

  const handleCreate = async (values: EmployeeFormValues) => {
    setSubmitting(true);
    try {
      const created = await AuthApi.createEmployee(values);
      setCreatedEmployees((prev) => [
        {
          key: String(created.userId),
          userId: created.userId,
          email: created.email,
          tenantId: created.tenantId,
          createdAt: new Date().toISOString(),
        },
        ...prev,
      ]);

      form.setFieldsValue({ email: '', password: generatePassword() });
      message.success('Funcionário criado com sucesso');
    } catch (e: any) {
      if (e?.status === 403) {
        message.error('Você não possui permissão para criar funcionários');
      } else {
        message.error(e?.message || 'Erro ao criar funcionário');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const filteredEmployees = useMemo(() => {
    const normalized = searchText.trim().toLowerCase();
    if (!normalized) return createdEmployees;
    return createdEmployees.filter((e) => e.email.toLowerCase().includes(normalized));
  }, [createdEmployees, searchText]);

  const columns: ColumnsType<CreatedEmployee> = [
    { title: 'ID', dataIndex: 'userId', key: 'userId', width: 100 },
    { title: 'E-mail', dataIndex: 'email', key: 'email' },
    {
      title: 'Tenant',
      dataIndex: 'tenantId',
      key: 'tenantId',
      width: 120,
      render: (value: number) => <Text code>{value}</Text>,
    },
    {
      title: 'Criado em',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (value: string) =>
        new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)),
    },
    {
      title: 'Perfil',
      key: 'profile',
      width: 140,
      render: () => <Tag color="blue">EMPLOYED</Tag>,
    },
  ];

  if (!allowed) {
    return (
      <div style={{ maxWidth: 760 }}>
        <Title level={3} style={{ marginBottom: 8 }}>
          Funcionários
        </Title>
        <Text type="secondary">
          Você não possui permissão para gerenciar funcionários.
        </Text>
        <div style={{ marginTop: 12 }}>
          <Tag color="red">Permissão necessária: employees.create</Tag>
        </div>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div>
        <Title level={3} style={{ marginBottom: 4 }}>
          Funcionários
        </Title>
        <Text type="secondary">
          Cadastre contas internas para operações do tenant com o perfil padrão de colaborador.
        </Text>
      </div>

      <Alert
        type="info"
        showIcon
        message="Fluxo atual"
        description="No momento, o backend expõe criação de funcionário. A listagem histórica completa será exibida quando o endpoint de consulta estiver disponível."
      />

      <Card title="Novo funcionário">
        <Form
          form={form}
          layout="vertical"
          initialValues={{ password: initialPassword }}
          onFinish={handleCreate}
        >
          <Form.Item
            label="E-mail do funcionário"
            name="email"
            rules={[
              { required: true, message: 'Informe o e-mail' },
              { type: 'email', message: 'Informe um e-mail válido' },
            ]}
          >
            <Input placeholder="colaborador@empresa.com" />
          </Form.Item>

          <Form.Item
            label="Senha inicial"
            name="password"
            rules={[{ required: true, message: 'Informe a senha inicial' }]}
          >
            <Input.Password placeholder="Defina uma senha segura" />
          </Form.Item>

          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={handleGeneratePassword}>
              Gerar senha forte
            </Button>
            <Button icon={<CopyOutlined />} onClick={handleCopyPassword}>
              Copiar senha
            </Button>
            <Button type="primary" htmlType="submit" icon={<UserAddOutlined />} loading={submitting}>
              Criar funcionário
            </Button>
          </Space>
        </Form>
      </Card>

      <Card title="Criados nesta sessão">
        <Table
          rowKey="key"
          columns={columns}
          dataSource={filteredEmployees}
          pagination={false}
          title={() => (
            <Space wrap>
              <Input.Search
                allowClear
                placeholder="Buscar por e-mail"
                style={{ width: 320 }}
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
              />
            </Space>
          )}
          locale={{ emptyText: 'Nenhum funcionário criado nesta sessão.' }}
        />
      </Card>
    </div>
  );
}
