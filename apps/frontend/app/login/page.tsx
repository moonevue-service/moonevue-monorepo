'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Button, Card, Divider, Form, Input, Spin, Typography } from 'antd';
import { LockOutlined, MailOutlined } from '@ant-design/icons';
import { useAuth } from '@/app/providers';

const { Title, Text, Paragraph } = Typography;

type FormValues = { email: string; password: string };

export default function LoginPage() {
  const router = useRouter();
  const { login, isLoading: authLoading } = useAuth();
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
      await login(values.email, values.password);
      router.push('/dashboard');
    } catch (err: any) {
      form.setFields([
        { name: 'password', errors: [err?.message || 'Email ou senha inválidos'] },
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
          Plataforma Moonevue
        </div>
        <Title level={1} style={{ maxWidth: 440, lineHeight: 1.25, marginBottom: 0, fontSize: 40 }}>
          Entre e acompanhe operações financeiras com total clareza.
        </Title>
        <Paragraph type="secondary" style={{ fontSize: 15, maxWidth: 400, marginBottom: 0 }}>
          Ambiente projetado para reduzir ruído visual e acelerar ações do dia a dia.
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
            Entrar
          </Title>
          <Text type="secondary">Acesse sua conta para continuar.</Text>

          <Form form={form} layout="vertical" onFinish={handleFinish} style={{ marginTop: 24 }}>
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
              rules={[{ required: true, message: 'Informe a senha' }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="••••••••" size="large" />
            </Form.Item>

            <Form.Item style={{ marginBottom: 12 }}>
              <Button type="primary" htmlType="submit" size="large" block>
                Entrar no painel
              </Button>
            </Form.Item>
          </Form>

          <div style={{ textAlign: 'center' }}>
            <Text type="secondary">Não tem uma conta? </Text>
            <Link href="/register">Criar agora</Link>
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

