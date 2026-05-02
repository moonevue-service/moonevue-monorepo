'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Button, Card, Divider, Form, Input, Spin, Typography } from 'antd';
import { LockOutlined, MailOutlined, ShopOutlined } from '@ant-design/icons';
import { useAuth } from '@/app/providers';
import { ApiError, AuthApi } from '@/lib/api';

const { Title, Text, Paragraph } = Typography;

type FormValues = {
  tenantName: string;
  tenantDocument: string;
  email: string;
  password: string;
  confirmPassword: string;
};

function mapRegisterError(err: unknown): string {
  const apiErr = err as ApiError | undefined;
  const code = apiErr?.data?.error;
  if (code === 'password_mismatch') return 'As senhas não correspondem';
  if (code === 'tenant_document_already_exists') return 'Empresa já registrada com este CNPJ/CPF';
  if (code === 'email_already_exists') return 'Este email já está em uso';
  if (apiErr?.status === 403) return 'Não foi possível criar o usuário administrador';
  return apiErr?.message || 'Registro falhou. Tente novamente.';
}

export default function RegisterPage() {
  const router = useRouter();
  const { refreshSession, isLoading: authLoading } = useAuth();
  const [form] = Form.useForm<FormValues>();

  if (authLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
        <Spin size="large" />
      </div>
    );
  }

  const handleFinish = async (values: FormValues) => {
    try {
      await AuthApi.registerWithSession(values);
      await refreshSession();
      router.push('/dashboard');
    } catch (err: unknown) {
      form.setFields([
        { name: 'confirmPassword', errors: [mapRegisterError(err)] },
      ]);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        alignItems: 'stretch',
      }}
    >
      {/* Marketing section — hidden on small screens via globals.css */}
      <section
        className="auth-marketing"
        style={{
          background: '#f0f5ff',
          padding: '60px 48px',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          gap: 20,
        }}
      >
        <div
          style={{
            display: 'inline-block',
            background: '#e6f4ff',
            color: '#1677ff',
            padding: '4px 14px',
            borderRadius: 99,
            fontSize: 13,
            fontWeight: 500,
            width: 'fit-content',
          }}
        >
          Nova operação
        </div>
        <Title level={1} style={{ maxWidth: 440, lineHeight: 1.25, marginBottom: 0, fontSize: 40 }}>
          Crie seu workspace financeiro em minutos.
        </Title>
        <Paragraph type="secondary" style={{ fontSize: 15, maxWidth: 400, marginBottom: 0 }}>
          Autenticação por tenant, integrações bancárias e acompanhamento de transações prontos para
          uso.
        </Paragraph>
      </section>

      {/* Form section */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          padding: '48px 24px',
          background: '#fff',
        }}
      >
        <Card style={{ width: '100%', maxWidth: 420, border: '1px solid #f0f0f0' }}>
          <Title level={3} style={{ marginBottom: 4 }}>
            Criar Conta
          </Title>
          <Text type="secondary">Configure o tenant e o primeiro usuário administrador.</Text>

          <Form form={form} layout="vertical" onFinish={handleFinish} style={{ marginTop: 24 }}>
            <Form.Item
              label="Nome da Empresa"
              name="tenantName"
              rules={[{ required: true, message: 'Informe o nome da empresa' }]}
            >
              <Input prefix={<ShopOutlined />} placeholder="Sua Empresa Ltda" size="large" />
            </Form.Item>

            <Form.Item
              label="CNPJ / CPF"
              name="tenantDocument"
              rules={[{ required: true, message: 'Informe o CNPJ ou CPF' }]}
            >
              <Input placeholder="00.000.000/0000-00" size="large" />
            </Form.Item>

            <Form.Item
              label="Email"
              name="email"
              rules={[
                { required: true, message: 'Informe o email' },
                { type: 'email', message: 'Email inválido' },
              ]}
            >
              <Input prefix={<MailOutlined />} placeholder="seu@email.com" size="large" />
            </Form.Item>

            <Form.Item
              label="Senha"
              name="password"
              rules={[
                { required: true, message: 'Informe a senha' },
                { min: 6, message: 'Mínimo 6 caracteres' },
              ]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="••••••••" size="large" />
            </Form.Item>

            <Form.Item
              label="Confirmar Senha"
              name="confirmPassword"
              dependencies={['password']}
              rules={[
                { required: true, message: 'Confirme a senha' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) return Promise.resolve();
                    return Promise.reject(new Error('As senhas não correspondem'));
                  },
                }),
              ]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="••••••••" size="large" />
            </Form.Item>

            <Form.Item style={{ marginBottom: 12 }}>
              <Button type="primary" htmlType="submit" size="large" block>
                Criar conta
              </Button>
            </Form.Item>
          </Form>

          <div style={{ textAlign: 'center' }}>
            <Text type="secondary">Já tem uma conta? </Text>
            <Link href="/login">Entrar</Link>
          </div>

          <Divider style={{ margin: '16px 0' }} />

          <div style={{ textAlign: 'center' }}>
            <Link href="/">← Voltar para home</Link>
          </div>
        </Card>
      </div>
    </div>
  );
}

