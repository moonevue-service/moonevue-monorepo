'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { Alert, App, Button, Card, Col, Divider, Form, Input, Row, Space, Statistic, Steps, Switch, Tag, Typography } from 'antd';
import {
  ArrowRightOutlined,
  CalendarOutlined,
  CreditCardOutlined,
  ReloadOutlined,
  SearchOutlined,
  ShopOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { CheckoutApi, CheckoutInfo } from '@/lib/api';

const { Title, Text, Paragraph } = Typography;

type LookupForm = {
  checkoutTokenOrUrl: string;
  document?: string;
};

function extractToken(input: string) {
  const value = input.trim();
  if (!value) {
    return '';
  }

  if (value.includes('/checkout/')) {
    const parts = value.split('/checkout/');
    return parts[1]?.split('?')[0] || '';
  }

  return value;
}

export default function ClientAreaPage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<LookupForm>();
  const [loading, setLoading] = useState(false);
  const [info, setInfo] = useState<CheckoutInfo | null>(null);
  const [lookupToken, setLookupToken] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [soundOnPaid, setSoundOnPaid] = useState(true);
  const [backgroundSyncing, setBackgroundSyncing] = useState(false);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);
  const previousStatusRef = useRef<CheckoutInfo['status'] | null>(null);

  const statusUi = useMemo(() => {
    if (!info) {
      return {
        color: 'default',
        label: 'NÃO CONSULTADO',
        current: 0,
      };
    }

    const status = info.status;
    if (status === 'PAID') {
      return { color: 'green', label: 'PAGO', current: 3 };
    }
    if (status === 'PROCESSING' || status === 'PENDING') {
      return { color: 'blue', label: 'EM PROCESSAMENTO', current: 2 };
    }
    if (status === 'EXPIRED') {
      return { color: 'orange', label: 'EXPIRADO', current: 1 };
    }
    if (status === 'CANCELED' || status === 'FAILED') {
      return { color: 'red', label: 'NÃO CONCLUÍDO', current: 1 };
    }

    return { color: 'purple', label: 'ABERTO', current: 1 };
  }, [info]);

  const onLookup = async (values: LookupForm) => {
    const token = extractToken(values.checkoutTokenOrUrl);
    if (!token) {
      message.warning('Informe um token ou link válido');
      return;
    }

    setLoading(true);
    setInfo(null);
    try {
      const data = await CheckoutApi.getInfo(token);
      setInfo(data);
      setLookupToken(token);
      setLastUpdatedAt(new Date());

      if (values.document) {
        await CheckoutApi.lookupClient(token, values.document);
      }
    } catch (e: any) {
      message.error(e?.message || 'Não foi possível localizar seu checkout');
    } finally {
      setLoading(false);
    }
  };

  const shouldPoll =
    !!info &&
    autoRefresh &&
    (info.status === 'CHECKOUT_OPEN' || info.status === 'PROCESSING' || info.status === 'PENDING');

  const refreshStatus = async (silent = false) => {
    const token = lookupToken || info?.token;
    if (!token) {
      return;
    }

    if (!silent) {
      setRefreshing(true);
    } else {
      setBackgroundSyncing(true);
    }

    try {
      const updated = await CheckoutApi.getStatus(token);
      setInfo(updated);
      setLastUpdatedAt(new Date());
    } catch (e: any) {
      if (!silent) {
        message.error(e?.message || 'Falha ao atualizar status');
      }
    } finally {
      if (!silent) {
        setRefreshing(false);
      } else {
        setBackgroundSyncing(false);
      }
    }
  };

  useEffect(() => {
    if (!shouldPoll) {
      return;
    }

    const timer = window.setInterval(() => {
      refreshStatus(true);
    }, 5000);

    return () => window.clearInterval(timer);
  }, [shouldPoll, lookupToken]);

  useEffect(() => {
    const current = info?.status;
    const previous = previousStatusRef.current;

    if (previous && previous !== 'PAID' && current === 'PAID') {
      message.success('Pagamento confirmado com sucesso');

      if (soundOnPaid && typeof window !== 'undefined') {
        try {
          const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
          if (AudioCtx) {
            const ctx = new AudioCtx();
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();

            osc.type = 'sine';
            osc.frequency.value = 880;
            gain.gain.value = 0.04;

            osc.connect(gain);
            gain.connect(ctx.destination);

            osc.start();
            osc.stop(ctx.currentTime + 0.18);
            window.setTimeout(() => void ctx.close(), 250);
          }
        } catch {
          // Ignora falhas de áudio por bloqueio do navegador.
        }
      }
    }

    previousStatusRef.current = current || null;
  }, [info?.status, message, soundOnPaid]);

  return (
    <main style={{ maxWidth: 980, margin: '0 auto', padding: '28px 16px 72px' }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card
          style={{
            border: '1px solid #d8e8ff',
            background:
              'radial-gradient(130% 100% at 0% 0%, #f8fbff 0%, #edf5ff 40%, #ffffff 100%)',
          }}
        >
          <Row gutter={[24, 24]} align="middle">
            <Col xs={24} md={14}>
              <Space direction="vertical" size={8}>
                <Tag color="blue" style={{ width: 'fit-content' }}>
                  Área do Cliente
                </Tag>
                <Title level={2} style={{ margin: 0 }}>
                  Acompanhe sua cobrança com clareza
                </Title>
                <Text type="secondary">
                  Consulte o checkout pelo token, veja o status em tempo real e siga para o pagamento sem fricção.
                </Text>
              </Space>
            </Col>
            <Col xs={24} md={10}>
              <Card size="small" title="Jornada" bordered>
                <Steps
                  size="small"
                  direction="vertical"
                  current={statusUi.current}
                  items={[
                    { title: 'Checkout recebido' },
                    { title: 'Checkout aberto' },
                    { title: 'Pagamento processando' },
                    { title: 'Pagamento concluído' },
                  ]}
                />
              </Card>
            </Col>
          </Row>
        </Card>

        <Card title="Consultar checkout">
          <Form form={form} layout="vertical" onFinish={onLookup}>
            <Form.Item
              label="Token ou link do checkout"
              name="checkoutTokenOrUrl"
              rules={[{ required: true, message: 'Informe o token ou link recebido' }]}
            >
              <Input placeholder="Ex.: a1b2c3d4... ou https://app.exemplo.com/checkout/a1b2c3" />
            </Form.Item>

            <Form.Item label="CPF/CNPJ (opcional)" name="document">
              <Input placeholder="Use para validar identificação, quando solicitado" />
            </Form.Item>

            <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={loading}>
              Consultar
            </Button>
          </Form>
        </Card>

        {info && (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Card
              title="Resumo do checkout"
              extra={
                <Space>
                  <Tag color={statusUi.color}>{statusUi.label}</Tag>
                  <Button
                    icon={<ReloadOutlined />}
                    onClick={() => refreshStatus(false)}
                    loading={refreshing}
                  >
                    Atualizar
                  </Button>
                </Space>
              }
            >
              <Row gutter={[16, 12]} style={{ marginBottom: 12 }}>
                <Col xs={24} md={14}>
                  <Text type="secondary">
                    Última atualização:{' '}
                    {lastUpdatedAt
                      ? new Intl.DateTimeFormat('pt-BR', {
                          dateStyle: 'short',
                          timeStyle: 'medium',
                        }).format(lastUpdatedAt)
                      : '-'}
                  </Text>
                </Col>
                <Col xs={24} md={10}>
                  <Space wrap>
                    <Text>Atualização automática</Text>
                    <Switch checked={autoRefresh} onChange={setAutoRefresh} />
                    <Text>Som ao confirmar</Text>
                    <Switch checked={soundOnPaid} onChange={setSoundOnPaid} />
                    {backgroundSyncing && (
                      <Tag icon={<SyncOutlined spin />} color="processing">
                        Consultando status...
                      </Tag>
                    )}
                  </Space>
                </Col>
              </Row>

              <Row gutter={[16, 16]}>
                <Col xs={24} sm={8}>
                  <Card size="small">
                    <Statistic
                      title="Valor"
                      prefix={<CreditCardOutlined />}
                      value={Number(info.amount || 0)}
                      precision={2}
                      suffix="BRL"
                    />
                  </Card>
                </Col>
                <Col xs={24} sm={8}>
                  <Card size="small">
                    <Statistic
                      title="Banco"
                      value={info.bank}
                      prefix={<ShopOutlined />}
                    />
                  </Card>
                </Col>
                <Col xs={24} sm={8}>
                  <Card size="small">
                    <Statistic
                      title="Expira em"
                      value={new Intl.DateTimeFormat('pt-BR', {
                        day: '2-digit',
                        month: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit',
                      }).format(new Date(info.expiresAt))}
                      prefix={<CalendarOutlined />}
                    />
                  </Card>
                </Col>
              </Row>

              <Divider />

              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                <Text>
                  <strong>Descrição:</strong> {info.description || '-'}
                </Text>
                <Text>
                  <strong>Token:</strong> <Text code>{info.token}</Text>
                </Text>
                {!!info.allowedInstruments?.length && (
                  <Space wrap>
                    <Text strong>Métodos aceitos:</Text>
                    {info.allowedInstruments.map((instrument) => (
                      <Tag key={instrument}>{instrument}</Tag>
                    ))}
                  </Space>
                )}
                {info.clientName && (
                  <Text>
                    <strong>Cliente:</strong> {info.clientName}
                    {info.clientDocumentMasked ? ` (${info.clientDocumentMasked})` : ''}
                  </Text>
                )}
              </Space>

              {(info.status === 'EXPIRED' || info.status === 'CANCELED') && (
                <Alert
                  style={{ marginTop: 16 }}
                  type="warning"
                  showIcon
                  message={
                    info.status === 'EXPIRED'
                      ? 'Este checkout expirou. Solicite um novo link ao estabelecimento.'
                      : 'Este checkout foi cancelado.'
                  }
                />
              )}

              {info.status === 'PAID' && (
                <Alert
                  style={{ marginTop: 16 }}
                  type="success"
                  showIcon
                  message="Pagamento confirmado"
                  description="Recebemos a confirmação deste checkout com sucesso."
                />
              )}

              <Space style={{ marginTop: 16 }} wrap>
                <Button type="primary" icon={<ArrowRightOutlined />}>
                  <Link href={`/checkout/${info.token}`}>Abrir checkout</Link>
                </Button>
                <Button>
                  <Link href="/login">Acessar área administrativa</Link>
                </Button>
              </Space>
            </Card>
          </Space>
        )}

        <Paragraph type="secondary" style={{ marginBottom: 0 }}>
          Não encontrou seu token? Solicite o link diretamente ao estabelecimento responsável pela cobrança.
        </Paragraph>
      </Space>
    </main>
  );
}
