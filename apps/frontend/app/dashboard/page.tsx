'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import {
  Avatar,
  Button,
  Card,
  Col,
  Empty,
  Row,
  Space,
  Steps,
  Table,
  Tag,
  Typography,
  theme,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  BankOutlined,
  CheckCircleOutlined,
  LineChartOutlined,
  PlusOutlined,
  RightOutlined,
  SwapOutlined,
  TeamOutlined,
  UserAddOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/app/providers';
import {
  AnalyticsApi,
  ClientsApi,
  ExecutiveSummary,
  FinanceApi,
  PaymentApi,
  TransactionSummary,
} from '@/lib/api';
import { canAccessClients } from '@/lib/authz';

const { Title, Text, Paragraph } = Typography;

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendente',
  CONFIRMED: 'Confirmado',
  PAID: 'Pago',
  FAILED: 'Falhou',
  CANCELLED: 'Cancelado',
  EXPIRED: 'Expirado',
};

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'warning',
  CONFIRMED: 'success',
  PAID: 'success',
  FAILED: 'error',
  CANCELLED: 'default',
  EXPIRED: 'default',
};

const brl = (value: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);

const formatDate = (date: string) =>
  new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(date));

function greeting() {
  const h = new Date().getHours();
  if (h < 12) return 'Bom dia';
  if (h < 18) return 'Boa tarde';
  return 'Boa noite';
}

function displayName(email?: string) {
  if (!email) return 'por aqui';
  const handle = email.split('@')[0]?.replace(/[._-]+/g, ' ') ?? '';
  return (
    handle
      .split(' ')
      .filter(Boolean)
      .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
      .join(' ') || 'por aqui'
  );
}

function GrowthTag({ pct }: { pct?: number | null }) {
  if (pct == null) return null;
  const up = pct >= 0;
  return (
    <Tag color={up ? 'success' : 'error'} style={{ margin: 0, fontWeight: 500 }}>
      {up ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {Math.abs(pct).toFixed(1)}%
    </Tag>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const { token } = theme.useToken();

  const [bankAccountCount, setBankAccountCount] = useState<number | null>(null);
  const [txTotal, setTxTotal] = useState<number | null>(null);
  const [recentTx, setRecentTx] = useState<TransactionSummary[]>([]);
  const [loadingTx, setLoadingTx] = useState(true);
  const [clientsTotal, setClientsTotal] = useState<number | null>(null);
  const [summary, setSummary] = useState<ExecutiveSummary | null>(null);

  const showClients = canAccessClients(user?.roles, user?.permissions);

  useEffect(() => {
    if (!user?.tenantId) return;
    const tenantId = user.tenantId;

    FinanceApi.listBankAccounts(tenantId)
      .then((accounts) => setBankAccountCount(accounts.length))
      .catch(() => setBankAccountCount(0));

    setLoadingTx(true);
    PaymentApi.listTransactions({ page: 0, size: 5 })
      .then((resp) => {
        setRecentTx(resp.content);
        setTxTotal(resp.totalElements);
      })
      .catch(() => {
        setRecentTx([]);
        setTxTotal(0);
      })
      .finally(() => setLoadingTx(false));

    AnalyticsApi.getSummary(tenantId)
      .then(setSummary)
      .catch(() => setSummary(null));

    if (showClients) {
      ClientsApi.list({ page: 0, size: 1 })
        .then((resp) => setClientsTotal(resp.totalElements))
        .catch(() => setClientsTotal(0));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.tenantId]);

  const hasBank = (bankAccountCount ?? 0) > 0;
  const hasTx = (txTotal ?? 0) > 0;
  const activationStep = hasTx ? 2 : hasBank ? 1 : 0;
  const fullyActivated = hasBank && hasTx;

  const quickActions = (
    [
      {
        key: 'tx',
        label: 'Nova transação',
        desc: 'Gere uma cobrança PIX ou boleto',
        icon: <SwapOutlined />,
        href: '/dashboard/transactions',
      },
      {
        key: 'bank',
        label: 'Adicionar conta',
        desc: 'Cadastre uma conta bancária',
        icon: <BankOutlined />,
        href: '/dashboard/bank-accounts',
      },
      showClients && {
        key: 'client',
        label: 'Cadastrar cliente',
        desc: 'Adicione um novo cliente',
        icon: <UserAddOutlined />,
        href: '/dashboard/clients',
      },
      {
        key: 'analytics',
        label: 'Ver analytics',
        desc: 'Acompanhe receita e métricas',
        icon: <LineChartOutlined />,
        href: '/dashboard/analytics',
      },
    ] as ({ key: string; label: string; desc: string; icon: React.ReactNode; href: string } | false)[]
  ).filter(Boolean) as { key: string; label: string; desc: string; icon: React.ReactNode; href: string }[];

  const kpis = [
    {
      key: 'revenue',
      label: 'Receita bruta',
      value: summary ? brl(summary.grossRevenue) : '—',
      hint: summary ? 'no período atual' : 'sem dados ainda',
      growth: summary?.growth?.grossRevenuePct ?? null,
      icon: <WalletOutlined />,
      href: '/dashboard/analytics',
    },
    {
      key: 'tx',
      label: 'Transações',
      value: txTotal ?? '—',
      hint: summary ? `${summary.paidTransactions} pagas` : 'total registradas',
      growth: undefined,
      icon: <SwapOutlined />,
      href: '/dashboard/transactions',
    },
    ...(showClients
      ? [
          {
            key: 'clients',
            label: 'Clientes',
            value: clientsTotal ?? '—',
            hint: 'cadastrados',
            growth: undefined,
            icon: <TeamOutlined />,
            href: '/dashboard/clients',
          },
        ]
      : []),
    {
      key: 'accounts',
      label: 'Contas bancárias',
      value: bankAccountCount ?? '—',
      hint: 'ativas e configuradas',
      growth: undefined,
      icon: <BankOutlined />,
      href: '/dashboard/bank-accounts',
    },
  ];

  const txColumns: ColumnsType<TransactionSummary> = [
    {
      title: 'Data',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 140,
      render: (date: string) => <Text style={{ fontSize: 13 }}>{formatDate(date)}</Text>,
    },
    {
      title: 'Descrição',
      key: 'desc',
      render: (_: unknown, t: TransactionSummary) => (
        <Text style={{ fontSize: 13 }} ellipsis>
          {t.clientName || t.description || t.externalReference || 'Transação'}
        </Text>
      ),
    },
    {
      title: 'Valor',
      dataIndex: 'amount',
      key: 'amount',
      width: 130,
      render: (amount: string) => (
        <Text strong style={{ fontSize: 13 }}>
          {brl(Number(amount))}
        </Text>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: string) => (
        <Tag color={STATUS_COLORS[status] ?? 'default'} style={{ margin: 0 }}>
          {STATUS_LABELS[status] ?? status}
        </Tag>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* Hero / boas-vindas */}
      <Card
        styles={{ body: { padding: 28 } }}
        style={{
          background: 'linear-gradient(120deg, #0a0a0a 0%, #262626 100%)',
          border: 'none',
        }}
      >
        <Row gutter={[24, 24]} align="middle" justify="space-between">
          <Col xs={24} md={15}>
            <Text style={{ color: 'rgba(255,255,255,0.55)', fontSize: 13 }}>{greeting()},</Text>
            <Title level={2} style={{ color: '#fff', margin: '2px 0 8px' }}>
              {displayName(user?.email)}
            </Title>
            <Paragraph style={{ color: 'rgba(255,255,255,0.65)', maxWidth: 460, marginBottom: 20 }}>
              Acompanhe contas, transações e a saúde do seu ambiente de pagamentos em um só lugar.
            </Paragraph>
            <Space wrap>
              <Link href="/dashboard/transactions">
                <Button
                  icon={<PlusOutlined />}
                  style={{ background: '#fff', color: '#0a0a0a', borderColor: '#fff', fontWeight: 500 }}
                >
                  Nova transação
                </Button>
              </Link>
              <Link href="/dashboard/bank-accounts">
                <Button ghost style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)' }}>
                  Adicionar conta
                </Button>
              </Link>
            </Space>
          </Col>
          <Col xs={24} md={9}>
            <div
              style={{
                background: 'rgba(255,255,255,0.06)',
                border: '1px solid rgba(255,255,255,0.12)',
                borderRadius: token.borderRadiusLG,
                padding: 16,
                display: 'flex',
                flexDirection: 'column',
                gap: 12,
              }}
            >
              <Space>
                <Avatar style={{ background: '#fff', color: '#0a0a0a' }} icon={<TeamOutlined />} />
                <div style={{ minWidth: 0 }}>
                  <Text style={{ color: '#fff', display: 'block', fontSize: 13 }} ellipsis>
                    {user?.email ?? '—'}
                  </Text>
                  <Text style={{ color: 'rgba(255,255,255,0.5)', fontSize: 12 }}>
                    Tenant #{user?.tenantId ?? '—'}
                  </Text>
                </div>
              </Space>
              <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                {user?.roles?.length ? (
                  user.roles.map((r) => (
                    <Tag
                      key={r}
                      style={{
                        margin: 0,
                        background: 'rgba(255,255,255,0.1)',
                        border: 'none',
                        color: '#fff',
                      }}
                    >
                      {r}
                    </Tag>
                  ))
                ) : (
                  <Text style={{ color: 'rgba(255,255,255,0.5)', fontSize: 12 }}>Sem papéis</Text>
                )}
              </div>
            </div>
          </Col>
        </Row>
      </Card>

      {/* KPIs */}
      <Row gutter={[16, 16]}>
        {kpis.map((kpi) => (
          <Col key={kpi.key} xs={24} sm={12} xl={showClients ? 6 : 8}>
            <Link href={kpi.href}>
              <Card hoverable styles={{ body: { padding: 20 } }}>
                <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                  <div
                    style={{
                      width: 40,
                      height: 40,
                      borderRadius: 10,
                      background: '#f5f5f5',
                      color: '#0a0a0a',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: 18,
                    }}
                  >
                    {kpi.icon}
                  </div>
                  <GrowthTag pct={kpi.growth} />
                </div>
                <Text type="secondary" style={{ display: 'block', fontSize: 13, marginTop: 14 }}>
                  {kpi.label}
                </Text>
                <Title level={3} style={{ margin: '2px 0 0' }}>
                  {kpi.value}
                </Title>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {kpi.hint}
                </Text>
              </Card>
            </Link>
          </Col>
        ))}
      </Row>

      {/* Atalhos rápidos */}
      <div>
        <Title level={5} style={{ marginBottom: 12 }}>
          Atalhos rápidos
        </Title>
        <Row gutter={[16, 16]}>
          {quickActions.map((action) => (
            <Col key={action.key} xs={24} sm={12} lg={6}>
              <Link href={action.href} style={{ display: 'block', height: '100%' }}>
                <Card hoverable style={{ height: '100%' }} styles={{ body: { padding: 18 } }}>
                  <Space align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Space align="start">
                      <div
                        style={{
                          width: 38,
                          height: 38,
                          borderRadius: 10,
                          background: '#0a0a0a',
                          color: '#fff',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: 16,
                        }}
                      >
                        {action.icon}
                      </div>
                      <div>
                        <Text strong style={{ display: 'block' }}>
                          {action.label}
                        </Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {action.desc}
                        </Text>
                      </div>
                    </Space>
                    <RightOutlined style={{ color: token.colorTextTertiary }} />
                  </Space>
                </Card>
              </Link>
            </Col>
          ))}
        </Row>
      </div>

      {/* Transações recentes + onboarding */}
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={15}>
          <Card
            title="Transações recentes"
            styles={{ body: { padding: recentTx.length ? 0 : 24 } }}
            extra={
              <Link href="/dashboard/transactions">
                <Button type="link" size="small" style={{ padding: 0 }}>
                  Ver todas <RightOutlined />
                </Button>
              </Link>
            }
          >
            {!loadingTx && recentTx.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nenhuma transação ainda">
                <Link href="/dashboard/transactions">
                  <Button type="primary" icon={<PlusOutlined />}>
                    Criar primeira transação
                  </Button>
                </Link>
              </Empty>
            ) : (
              <Table
                rowKey="id"
                size="middle"
                loading={loadingTx}
                columns={txColumns}
                dataSource={recentTx}
                pagination={false}
                scroll={{ x: 480 }}
              />
            )}
          </Card>
        </Col>

        <Col xs={24} lg={9}>
          <Card title={fullyActivated ? 'Tudo pronto' : 'Comece por aqui'} style={{ height: '100%' }}>
            {fullyActivated ? (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space align="start">
                  <CheckCircleOutlined style={{ color: token.colorSuccess, fontSize: 20, marginTop: 2 }} />
                  <div>
                    <Text strong style={{ display: 'block' }}>
                      Ambiente configurado
                    </Text>
                    <Text type="secondary" style={{ fontSize: 13 }}>
                      Suas contas e transações já estão ativas. Continue operando ou explore as
                      métricas do negócio.
                    </Text>
                  </div>
                </Space>
                <Link href="/dashboard/analytics">
                  <Button block icon={<LineChartOutlined />}>
                    Ver analytics
                  </Button>
                </Link>
              </Space>
            ) : (
              <Steps
                direction="vertical"
                size="small"
                current={activationStep}
                items={[
                  {
                    title: 'Configure suas contas bancárias',
                    description: (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 8 }}>
                        <Text type="secondary" style={{ fontSize: 13 }}>
                          Cadastre a conta que vai operar os recebimentos.
                        </Text>
                        {!hasBank && (
                          <Link href="/dashboard/bank-accounts">
                            <Button size="small" type="primary">
                              Abrir contas bancárias
                            </Button>
                          </Link>
                        )}
                      </div>
                    ),
                  },
                  {
                    title: 'Revise os dados de integração',
                    description: (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 8 }}>
                        <Text type="secondary" style={{ fontSize: 13 }}>
                          Confirme certificados e permissões nas configurações.
                        </Text>
                        <Link href="/dashboard/settings">
                          <Button size="small">Abrir configurações</Button>
                        </Link>
                      </div>
                    ),
                  },
                  {
                    title: 'Crie seu primeiro pagamento',
                    description: (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 8 }}>
                        <Text type="secondary" style={{ fontSize: 13 }}>
                          Gere uma transação de teste para validar o fluxo completo.
                        </Text>
                        {hasBank && !hasTx && (
                          <Link href="/dashboard/transactions">
                            <Button size="small" type="primary">
                              Abrir transações
                            </Button>
                          </Link>
                        )}
                      </div>
                    ),
                  },
                ]}
              />
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}




