'use client';

import { useState } from 'react';
import {
  App,
  Button,
  Card,
  Descriptions,
  Space,
  Tag,
  Typography,
} from 'antd';
import { CheckOutlined, CopyOutlined, LogoutOutlined } from '@ant-design/icons';
import { useAuth } from '@/app/providers';
import { useRouter } from 'next/navigation';

const { Title, Text, Paragraph } = Typography;

export default function SettingsPage() {
  const { user, logout } = useAuth();
  const { modal } = App.useApp();
  const router = useRouter();
  const [copied, setCopied] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  const copyTenantId = async () => {
    if (user?.tenantId) {
      await navigator.clipboard.writeText(String(user.tenantId));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleLogout = () => {
    modal.confirm({
      title: 'Encerrar sessão',
      content: 'Você será desconectado e precisará fazer login novamente.',
      okText: 'Sair',
      cancelText: 'Cancelar',
      okButtonProps: { danger: true },
      async onOk() {
        setLoggingOut(true);
        try {
          await logout();
          router.push('/login');
        } catch {
          router.push('/login');
        } finally {
          setLoggingOut(false);
        }
      },
    });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, maxWidth: 800 }}>
      <div>
        <Title level={3} style={{ marginBottom: 4 }}>
          Configurações
        </Title>
        <Text type="secondary">Gerencie identidade da conta, segurança e dados de integração.</Text>
      </div>

      <Card title="Informações da Conta">
        <Descriptions column={1} size="default" bordered>
          <Descriptions.Item label="Email">{user?.email}</Descriptions.Item>
          <Descriptions.Item label="Tenant ID">
            <Space>
              <Text code>{user?.tenantId}</Text>
              <Button
                type="text"
                size="small"
                icon={
                  copied ? (
                    <CheckOutlined style={{ color: '#52c41a' }} />
                  ) : (
                    <CopyOutlined />
                  )
                }
                onClick={copyTenantId}
              >
                {copied ? 'Copiado' : 'Copiar'}
              </Button>
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="Papéis">
            <Space wrap>
              {user?.roles?.length ? (
                user.roles.map((r) => (
                  <Tag key={r} color="blue">
                    {r}
                  </Tag>
                ))
              ) : (
                <Text type="secondary">Nenhum papel atribuído</Text>
              )}
            </Space>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Segurança">
        <Space direction="vertical" style={{ width: '100%', gap: 16 }}>
          <div>
            <Text strong>Sessão Ativa</Text>
            <Paragraph type="secondary" style={{ marginBottom: 0, marginTop: 4 }}>
              Sua sessão é gerenciada via cookie HTTP-only e renovada automaticamente a cada 5
              minutos de atividade.
            </Paragraph>
          </div>
          <div>
            <Text strong>Alterar Senha</Text>
            <Paragraph type="secondary" style={{ marginBottom: 0, marginTop: 4 }}>
              Para trocar sua senha, saia da conta e utilize a opção de recuperação na página de
              login.
            </Paragraph>
          </div>
        </Space>
      </Card>

      <Card title="Integração com API">
        <Space direction="vertical" style={{ width: '100%' }}>
          <Paragraph type="secondary">
            A autenticação é gerenciada via cookie de sessão. Inclua o cookie em todas as
            requisições.
          </Paragraph>
          <div
            style={{
              background: '#f6f8fa',
              border: '1px solid #e6e9ed',
              borderRadius: 6,
              padding: '12px 16px',
              fontFamily: 'monospace',
              fontSize: 13,
            }}
          >
            Cookie: sid=&lt;session_id&gt;
          </div>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            Use <Text code>credentials: &apos;include&apos;</Text> em todas as chamadas fetch para
            que o cookie seja enviado automaticamente.
          </Paragraph>
        </Space>
      </Card>

      <Card
        title={<Text type="danger">Zona de Perigo</Text>}
        style={{ borderColor: 'rgba(255, 77, 79, 0.2)' }}
      >
        <Space direction="vertical">
          <Text strong>Encerrar Sessão</Text>
          <Paragraph type="secondary">
            Você será desconectado e precisará fazer login novamente para acessar o painel.
          </Paragraph>
          <Button
            danger
            icon={<LogoutOutlined />}
            onClick={handleLogout}
            loading={loggingOut}
          >
            Sair da Conta
          </Button>
        </Space>
      </Card>
    </div>
  );
}

