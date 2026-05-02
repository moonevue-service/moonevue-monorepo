import Link from 'next/link';
import { Button, Card } from 'antd';
import {
  ArrowRightOutlined,
  BarChartOutlined,
  SafetyOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';

const highlights = [
  {
    icon: ThunderboltOutlined,
    title: 'Integração rápida',
    text: 'Conecte bancos e inicie cobranças sem complexidade técnica.',
  },
  {
    icon: SafetyOutlined,
    title: 'Sessão segura',
    text: 'Autenticação por cookie HTTP-only e controles de acesso por tenant.',
  },
  {
    icon: BarChartOutlined,
    title: 'Visão operacional',
    text: 'Acompanhe configurações, transações e status em um painel único.',
  },
];

const containerStyle: React.CSSProperties = { maxWidth: 1280, margin: '0 auto', padding: '0 24px' };

export default function Hero() {
  return (
    <main>
      {/* Hero section */}
      <section style={{ ...containerStyle, paddingTop: 72, paddingBottom: 80 }}>
        <div
          style={{
            maxWidth: 720,
            margin: '0 auto',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 24,
            textAlign: 'center',
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
            }}
          >
            Plataforma de pagamentos SaaS
          </div>
          <h1
            style={{
              fontSize: 'clamp(32px, 5vw, 56px)',
              fontWeight: 600,
              lineHeight: 1.2,
              color: '#141414',
              margin: 0,
            }}
          >
            Operação financeira moderna, clara e sem excesso visual.
          </h1>
          <p style={{ fontSize: 16, color: '#595959', maxWidth: 540, margin: 0, lineHeight: 1.6 }}>
            Centralize contas bancárias, integrações e cobranças com uma interface limpa, focada em
            produtividade e leitura rápida.
          </p>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', justifyContent: 'center' }}>
            <Button type="primary" size="large" href="/register">
              Começar agora
            </Button>
            <Button size="large" href="/login" icon={<ArrowRightOutlined />} iconPosition="end">
              Entrar no painel
            </Button>
          </div>
        </div>
      </section>

      {/* Features section */}
      <section id="beneficios" style={{ ...containerStyle, paddingBottom: 80 }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
            gap: 16,
          }}
        >
          {highlights.map((item) => {
            const Icon = item.icon;
            return (
              <Card key={item.title} style={{ border: '1px solid #f0f0f0' }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <div
                    style={{
                      display: 'inline-flex',
                      background: '#e6f4ff',
                      color: '#1677ff',
                      borderRadius: 8,
                      padding: 10,
                      width: 'fit-content',
                    }}
                  >
                    <Icon style={{ fontSize: 18 }} />
                  </div>
                  <strong style={{ fontSize: 15 }}>{item.title}</strong>
                  <p style={{ color: '#595959', fontSize: 14, margin: 0, lineHeight: 1.5 }}>
                    {item.text}
                  </p>
                </div>
              </Card>
            );
          })}
        </div>
      </section>

      {/* CTA section */}
      <section id="integracoes" style={{ ...containerStyle, paddingBottom: 96 }}>
        <Card
          style={{
            background: '#f0f5ff',
            border: '1px solid #d6e4ff',
          }}
        >
          <div
            style={{
              display: 'flex',
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: 16,
            }}
          >
            <div>
              <p
                style={{ color: '#1677ff', fontSize: 13, fontWeight: 500, margin: '0 0 4px 0' }}
              >
                Pronto para escalar
              </p>
              <strong style={{ fontSize: 16 }}>Estrutura modular para auth, gateway e finance</strong>
              <p style={{ color: '#595959', fontSize: 14, margin: '8px 0 0 0' }}>
                Arquitetura separada por serviço para evoluir o produto sem comprometer a
                experiência do usuário.
              </p>
            </div>
            <Button href="/login">Ver dashboard</Button>
          </div>
        </Card>
      </section>
    </main>
  );
}

