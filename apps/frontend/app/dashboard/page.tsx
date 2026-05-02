'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Button, Card, Col, Row, Statistic, Steps, Tag, Typography, theme } from 'antd';
import {
  BankOutlined,
  CheckCircleOutlined,
  RightOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/app/providers';
import { FinanceApi } from '@/lib/api';

const { Title, Text, Paragraph } = Typography;

export default function DashboardPage() {
  const { user } = useAuth();
  const { token } = theme.useToken();
  const [bankAccountCount, setBankAccountCount] = useState<number | null>(null);

  useEffect(() => {
    if (user?.tenantId) {
      FinanceApi.listBankAccounts(user.tenantId)
        .then((accounts) => setBankAccountCount(accounts.length))
        .catch(() => setBankAccountCount(0));
    }
  }, [user?.tenantId]);

  const activationStep = bankAccountCount === null ? 0 : bankAccountCount > 0 ? 1 : 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div>
        <Title level={3} style={{ marginBottom: 4 }}>
          Visão Geral
        </Title>
        <Text type="secondary">
          Monitore contas, transações e o status do ambiente de pagamentos.
        </Text>
      </div>

      {/* Account summary */}
      <Card>
        <Row gutter={[24, 16]}>
          <Col xs={24} sm={8}>
            <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
              Usuário
            </Text>
            <Text strong style={{ fontSize: 15 }}>
              {user?.email ?? '—'}
            </Text>
          </Col>
          <Col xs={24} sm={8}>
            <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
              Tenant ID
            </Text>
            <Text strong style={{ fontSize: 15, fontFamily: 'monospace' }}>
              {user?.tenantId ?? '—'}
            </Text>
          </Col>
          <Col xs={24} sm={8}>
            <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
              Papéis
            </Text>
            <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginTop: 2 }}>
              {user?.roles?.length ? (
                user.roles.map((r) => (
                  <Tag key={r} color="blue">
                    {r}
                  </Tag>
                ))
              ) : (
                <Text type="secondary">—</Text>
              )}
            </div>
          </Col>
        </Row>
      </Card>

      {/* Stats */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Contas Bancárias"
              value={bankAccountCount ?? '—'}
              prefix={<BankOutlined style={{ color: token.colorPrimary }} />}
              valueStyle={{ color: bankAccountCount ? token.colorPrimary : token.colorTextTertiary }}
              loading={bankAccountCount === null}
            />
            <Button
              type="link"
              size="small"
              icon={<RightOutlined />}
              style={{ padding: 0, marginTop: 8 }}
            >
              <Link href="/dashboard/bank-accounts">Ver contas</Link>
            </Button>
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Transações"
              value="—"
              prefix={<SwapOutlined style={{ color: token.colorTextTertiary }} />}
              valueStyle={{ color: token.colorTextTertiary }}
            />
            <Button
              type="link"
              size="small"
              icon={<RightOutlined />}
              style={{ padding: 0, marginTop: 8 }}
            >
              <Link href="/dashboard/transactions">Ver transações</Link>
            </Button>
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Status do Ambiente"
              value="Ativo"
              prefix={<CheckCircleOutlined style={{ color: token.colorSuccess }} />}
              valueStyle={{ color: token.colorSuccess }}
            />
          </Card>
        </Col>
      </Row>

      {/* Activation roadmap */}
      <Card
        title="Roteiro de Ativação"
        extra={
          <Text type="secondary" style={{ fontSize: 13 }}>
            Siga a sequência para operar sem retrabalho
          </Text>
        }
      >
        <Steps
          direction="vertical"
          current={activationStep}
          items={[
            {
              title: 'Configure suas contas bancárias',
              description: (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 8 }}>
                  <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    Cadastre a conta que vai operar os recebimentos. Tenha em mãos banco, agência,
                    número da conta, dígito e tipo de conta.
                  </Paragraph>
                  <div>
                    <Button size="small" type="primary">
                      <Link href="/dashboard/bank-accounts">Abrir contas bancárias</Link>
                    </Button>
                  </div>
                </div>
              ),
            },
            {
              title: 'Revise os dados de integração',
              description: (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 8 }}>
                  <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    Guarde seu Tenant ID e confirme os papéis da conta. Essas informações são
                    necessárias para configurar certificados e validar permissões.
                  </Paragraph>
                  <div>
                    <Button size="small">
                      <Link href="/dashboard/settings">Abrir configurações</Link>
                    </Button>
                  </div>
                </div>
              ),
            },
            {
              title: 'Execute seu primeiro pagamento de teste',
              description: (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 8 }}>
                  <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    Com conta e configuração bancária prontas, crie uma transação de teste informando
                    banco, ID da configuração, valor e descrição.
                  </Paragraph>
                  <div>
                    <Button size="small">
                      <Link href="/dashboard/transactions">Abrir transações</Link>
                    </Button>
                  </div>
                </div>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
}




