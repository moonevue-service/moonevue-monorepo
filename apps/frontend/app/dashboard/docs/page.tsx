"use client";

import {
  Alert,
  Anchor,
  App,
  Button,
  Card,
  Col,
  Divider,
  Row,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  ApiOutlined,
  AppstoreOutlined,
  BankOutlined,
  BarcodeOutlined,
  BookOutlined,
  ClockCircleOutlined,
  CodeOutlined,
  CopyOutlined,
  DollarOutlined,
  LineChartOutlined,
  LockOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  ShoppingCartOutlined,
  SwapOutlined,
  SyncOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import Link from "next/link";
import { API_KEY_SCOPES } from "@/lib/api";

const { Title, Text, Paragraph } = Typography;

/* ------------------------------------------------------------------ */
/* Helpers compartilhados                                              */
/* ------------------------------------------------------------------ */

function DocSection({
  id,
  icon,
  title,
  children,
}: {
  id: string;
  icon?: React.ReactNode;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section id={id} style={{ scrollMarginTop: 16 }}>
      <Title level={4} style={{ marginTop: 0 }}>
        <Space>
          {icon}
          {title}
        </Space>
      </Title>
      {children}
    </section>
  );
}

const CODE_BG = "#0d1117";

function CodeBlock({
  code,
  copyable = true,
}: {
  code: string;
  copyable?: boolean;
}) {
  const { message } = App.useApp();
  return (
    <div style={{ position: "relative" }}>
      {copyable && (
        <Button
          size="small"
          icon={<CopyOutlined />}
          onClick={async () => {
            try {
              await navigator.clipboard.writeText(code);
              message.success("Copiado");
            } catch {
              message.error("Não foi possível copiar");
            }
          }}
          style={{
            position: "absolute",
            top: 8,
            right: 8,
            zIndex: 2,
            background: "rgba(255,255,255,0.08)",
            color: "#e6e6e6",
            border: "1px solid rgba(255,255,255,0.15)",
          }}
        >
          Copiar
        </Button>
      )}
      <pre
        style={{
          background: CODE_BG,
          color: "#e6e6e6",
          padding: 16,
          paddingRight: 92,
          borderRadius: 8,
          overflowX: "auto",
          fontSize: 12.5,
          lineHeight: 1.65,
          margin: 0,
          fontFamily:
            "ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace",
        }}
      >
        {code}
      </pre>
    </div>
  );
}

const HTTP_METHOD_COLORS: Record<string, string> = {
  GET: "#1677ff",
  POST: "#52c41a",
  PUT: "#faad14",
  DELETE: "#ff4d4f",
};

function MethodBadge({ method }: { method: string }) {
  return (
    <span
      style={{
        background: HTTP_METHOD_COLORS[method] ?? "#8c8c8c",
        color: "#fff",
        fontWeight: 700,
        fontSize: 12,
        padding: "2px 10px",
        borderRadius: 6,
        letterSpacing: 0.5,
      }}
    >
      {method}
    </span>
  );
}

function EndpointHeader({
  method,
  path,
  scope,
}: {
  method: string;
  path: string;
  scope?: string;
}) {
  return (
    <Space size={12} wrap>
      <MethodBadge method={method} />
      <Text code style={{ fontSize: 14 }}>
        {path}
      </Text>
      {scope && (
        <Tag color="purple" icon={<LockOutlined />}>
          {scope}
        </Tag>
      )}
    </Space>
  );
}

/* ------------------------------------------------------------------ */
/* Documentação do sistema                                             */
/* ------------------------------------------------------------------ */

type ModuleRow = {
  key: string;
  icon: React.ReactNode;
  name: string;
  href: string;
  description: string;
  access: string;
};

const MODULES: ModuleRow[] = [
  {
    key: "overview",
    icon: <AppstoreOutlined />,
    name: "Visão Geral",
    href: "/dashboard",
    description:
      "Painel inicial com os principais indicadores do negócio e atalhos para as áreas mais usadas.",
    access: "Todos",
  },
  {
    key: "analytics",
    icon: <LineChartOutlined />,
    name: "Analytics",
    href: "/dashboard/analytics",
    description:
      "Métricas financeiras: volume cobrado, recebido, taxa de conversão e séries históricas.",
    access: "Todos",
  },
  {
    key: "bank-accounts",
    icon: <BankOutlined />,
    name: "Contas Bancárias",
    href: "/dashboard/bank-accounts",
    description:
      "Configurações de provedores de pagamento (ASAAS, EFI) em ambientes de sandbox e produção.",
    access: "Admin",
  },
  {
    key: "clients",
    icon: <UserOutlined />,
    name: "Clientes",
    href: "/dashboard/clients",
    description:
      "Cadastro de pagadores, com dados de contato e histórico de cobranças associadas.",
    access: "Admin, Financeiro, Suporte",
  },
  {
    key: "employees",
    icon: <TeamOutlined />,
    name: "Funcionários",
    href: "/dashboard/employees",
    description:
      "Gestão da equipe: convites, ativação/desativação e atribuição de papéis (RBAC).",
    access: "Admin",
  },
  {
    key: "transactions",
    icon: <SwapOutlined />,
    name: "Transações",
    href: "/dashboard/transactions",
    description:
      "Lista de cobranças emitidas, status, ambiente, link de checkout e filtros avançados.",
    access: "Todos",
  },
  {
    key: "integrations",
    icon: <ApiOutlined />,
    name: "Integrações",
    href: "/dashboard/integrations",
    description:
      "Chaves de API e analytics de uso da API pública de cobranças.",
    access: "Admin",
  },
  {
    key: "settings",
    icon: <SettingOutlined />,
    name: "Configurações",
    href: "/dashboard/settings",
    description: "Preferências da conta e do tenant.",
    access: "Admin",
  },
];

type StatusRow = {
  key: string;
  status: string;
  color: string;
  description: string;
};

const STATUS_LIFECYCLE: StatusRow[] = [
  {
    key: "1",
    status: "PENDING",
    color: "#faad14",
    description: "Cobrança criada, aguardando pagamento.",
  },
  {
    key: "2",
    status: "PROCESSING",
    color: "#1677ff",
    description: "Pagamento em processamento pelo provedor.",
  },
  {
    key: "3",
    status: "PAID",
    color: "#52c41a",
    description: "Pagamento recebido.",
  },
  {
    key: "4",
    status: "CONFIRMED",
    color: "#52c41a",
    description: "Pagamento confirmado pelo provedor.",
  },
  {
    key: "5",
    status: "SETTLED",
    color: "#389e0d",
    description: "Valor liquidado/compensado.",
  },
  {
    key: "6",
    status: "EXPIRED",
    color: "#bfbfbf",
    description: "Prazo de pagamento expirou sem quitação.",
  },
  {
    key: "7",
    status: "CANCELED",
    color: "#8c8c8c",
    description: "Cobrança cancelada.",
  },
  {
    key: "8",
    status: "REFUNDED",
    color: "#722ed1",
    description: "Valor estornado ao pagador.",
  },
  {
    key: "9",
    status: "FAILED",
    color: "#ff4d4f",
    description: "Falha ao processar a cobrança.",
  },
];

const statusColumns: ColumnsType<StatusRow> = [
  {
    title: "Status",
    dataIndex: "status",
    key: "status",
    width: 160,
    render: (s: string, row) => <Tag color={row.color}>{s}</Tag>,
  },
  { title: "Significado", dataIndex: "description", key: "description" },
];

type RoleRow = {
  key: string;
  role: string;
  description: string;
  scope: string;
};

const ROLES: RoleRow[] = [
  {
    key: "admin_tenant",
    role: "ADMIN_TENANT",
    description:
      "Administrador da organização. Acesso total: equipe, contas bancárias, integrações e configurações.",
    scope: "Tudo",
  },
  {
    key: "admin",
    role: "ADMIN",
    description: "Administrador com amplos poderes de gestão sobre o tenant.",
    scope: "Tudo",
  },
  {
    key: "finance",
    role: "FINANCE",
    description:
      "Perfil financeiro. Gerencia clientes e cobranças, sem acesso à gestão de equipe ou integrações.",
    scope: "Clientes, Transações",
  },
  {
    key: "support",
    role: "SUPPORT",
    description:
      "Perfil de suporte. Consulta clientes e transações para atendimento.",
    scope: "Clientes (leitura), Transações",
  },
];

const roleColumns: ColumnsType<RoleRow> = [
  {
    title: "Papel",
    dataIndex: "role",
    key: "role",
    width: 170,
    render: (r: string) => <Tag color="blue">{r}</Tag>,
  },
  { title: "Descrição", dataIndex: "description", key: "description" },
  {
    title: "Alcance",
    dataIndex: "scope",
    key: "scope",
    width: 200,
    render: (s: string) => (
      <Text type="secondary" style={{ fontSize: 12 }}>
        {s}
      </Text>
    ),
  },
];

const systemAnchorItems = [
  { key: "overview", href: "#sys-overview", title: "Visão geral" },
  {
    key: "getting-started",
    href: "#sys-getting-started",
    title: "Primeiros passos",
  },
  { key: "modules", href: "#sys-modules", title: "Módulos" },
  { key: "charges", href: "#sys-charges", title: "Cobranças e ciclo de vida" },
  { key: "methods", href: "#sys-methods", title: "Métodos de pagamento" },
  { key: "environments", href: "#sys-environments", title: "Ambientes" },
  { key: "checkout", href: "#sys-checkout", title: "Checkout" },
  { key: "rbac", href: "#sys-rbac", title: "Papéis e permissões" },
  { key: "support", href: "#sys-support", title: "Suporte" },
];

/**
 * Container de rolagem das abas de documentação. As âncoras "NESTA PÁGINA"
 * precisam observar este elemento (e não a janela), já que apenas o conteúdo
 * rola enquanto cabeçalho e abas permanecem fixos.
 */
const getDocsScrollContainer = (): HTMLElement =>
  (typeof document !== "undefined"
    ? (document.querySelector(
        ".docs-tabs .ant-tabs-content-holder",
      ) as HTMLElement | null)
    : null) ??
  (typeof window !== "undefined"
    ? (window as unknown as HTMLElement)
    : (null as unknown as HTMLElement));

function SystemDocs() {
  return (
    <Row gutter={0} wrap>
      <Col xs={0} lg={5} style={{ paddingRight: 24 }}>
        <div style={{ position: "sticky", top: 8 }}>
          <Text strong style={{ fontSize: 12, color: "#8c8c8c" }}>
            NESTA PÁGINA
          </Text>
          <Anchor
            affix={false}
            offsetTop={8}
            getContainer={getDocsScrollContainer}
            items={systemAnchorItems}
          />
        </div>
      </Col>

      <Col xs={24} lg={19}>
        <Space
          direction="vertical"
          size="large"
          style={{ width: "100%", maxWidth: 900 }}
        >
          <DocSection
            id="sys-overview"
            icon={<BookOutlined />}
            title="Visão geral"
          >
            <Paragraph>
              O <Text strong>Moonevue</Text> é uma plataforma de gestão
              financeira e cobranças. Com ela, sua organização emite cobranças
              PIX e boleto, acompanha pagamentos em tempo real, gerencia
              clientes e equipe, e integra os fluxos ao seu próprio sistema via
              API.
            </Paragraph>
            <Row gutter={[16, 16]}>
              <Col xs={24} md={8}>
                <Card size="small">
                  <Space direction="vertical" size={4}>
                    <DollarOutlined
                      style={{ fontSize: 22, color: "#52c41a" }}
                    />
                    <Text strong>Cobranças</Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      Emita PIX e boletos por diversos provedores.
                    </Text>
                  </Space>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card size="small">
                  <Space direction="vertical" size={4}>
                    <LineChartOutlined
                      style={{ fontSize: 22, color: "#1677ff" }}
                    />
                    <Text strong>Acompanhamento</Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      Status em tempo real e métricas de desempenho.
                    </Text>
                  </Space>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card size="small">
                  <Space direction="vertical" size={4}>
                    <ApiOutlined style={{ fontSize: 22, color: "#722ed1" }} />
                    <Text strong>Integração</Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      API pública para automatizar cobranças.
                    </Text>
                  </Space>
                </Card>
              </Col>
            </Row>
          </DocSection>

          <DocSection
            id="sys-getting-started"
            icon={<RocketOutlined />}
            title="Primeiros passos"
          >
            <Paragraph>Para começar a cobrar em poucos minutos:</Paragraph>
            <ol style={{ paddingLeft: 18, lineHeight: 1.9 }}>
              <li>
                Em <Text strong>Contas Bancárias</Text>, cadastre uma
                configuração do seu provedor (comece em sandbox para testar).
              </li>
              <li>
                Em <Text strong>Clientes</Text>, cadastre os pagadores (ou deixe
                que sejam criados automaticamente na cobrança).
              </li>
              <li>
                Em <Text strong>Transações</Text>, emita sua primeira cobrança e
                compartilhe o link de checkout.
              </li>
              <li>
                Acompanhe os recebimentos em <Text strong>Analytics</Text> e na
                própria lista de transações.
              </li>
              <li>
                Para automatizar, gere uma chave em{" "}
                <Text strong>Integrações</Text> e use a API (veja a aba{" "}
                <Text strong>Referência da API</Text>).
              </li>
            </ol>
          </DocSection>

          <DocSection
            id="sys-modules"
            icon={<AppstoreOutlined />}
            title="Módulos"
          >
            <Paragraph>
              Cada área do menu lateral atende a uma função. O acesso depende do
              seu papel (veja <a href="#sys-rbac">Papéis e permissões</a>).
            </Paragraph>
            <Row gutter={[16, 16]}>
              {MODULES.map((m) => (
                <Col xs={24} md={12} key={m.key}>
                  <Card size="small" style={{ height: "100%" }}>
                    <Space
                      direction="vertical"
                      size={6}
                      style={{ width: "100%" }}
                    >
                      <Space>
                        <span style={{ fontSize: 18, color: "#1677ff" }}>
                          {m.icon}
                        </span>
                        <Link href={m.href}>
                          <Text strong>{m.name}</Text>
                        </Link>
                      </Space>
                      <Text type="secondary" style={{ fontSize: 13 }}>
                        {m.description}
                      </Text>
                      <Tag style={{ marginTop: 4 }}>Acesso: {m.access}</Tag>
                    </Space>
                  </Card>
                </Col>
              ))}
            </Row>
          </DocSection>

          <DocSection
            id="sys-charges"
            icon={<DollarOutlined />}
            title="Cobranças e ciclo de vida"
          >
            <Paragraph>
              Uma <Text strong>cobrança</Text> é uma solicitação de pagamento
              gerada por um provedor. Ela percorre estados desde a criação até a
              liquidação (ou expiração/cancelamento). A tabela abaixo resume os
              principais status.
            </Paragraph>
            <Table
              size="small"
              pagination={false}
              columns={statusColumns}
              dataSource={STATUS_LIFECYCLE}
            />
          </DocSection>

          <DocSection
            id="sys-methods"
            icon={<ThunderboltOutlined />}
            title="Métodos de pagamento"
          >
            <Row gutter={[16, 16]}>
              <Col xs={24} md={8}>
                <Card
                  size="small"
                  title={
                    <Space>
                      <ThunderboltOutlined style={{ color: "#52c41a" }} />
                      PIX imediato
                    </Space>
                  }
                  style={{ height: "100%" }}
                >
                  <Text type="secondary" style={{ fontSize: 13 }}>
                    Pagamento instantâneo via QR Code e copia-e-cola, com
                    expiração curta. Ideal para checkout no ato.
                  </Text>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card
                  size="small"
                  title={
                    <Space>
                      <ThunderboltOutlined style={{ color: "#1677ff" }} />
                      PIX com vencimento
                    </Space>
                  }
                  style={{ height: "100%" }}
                >
                  <Text type="secondary" style={{ fontSize: 13 }}>
                    PIX com data de vencimento, podendo incluir juros/multa.
                    Indicado para faturas e mensalidades.
                  </Text>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card
                  size="small"
                  title={
                    <Space>
                      <BarcodeOutlined style={{ color: "#722ed1" }} />
                      Boleto
                    </Space>
                  }
                  style={{ height: "100%" }}
                >
                  <Text type="secondary" style={{ fontSize: 13 }}>
                    Boleto bancário com linha digitável e PDF. Compensação em
                    dias úteis após o pagamento.
                  </Text>
                </Card>
              </Col>
            </Row>
          </DocSection>

          <DocSection
            id="sys-environments"
            icon={<SafetyCertificateOutlined />}
            title="Ambientes"
          >
            <Paragraph>
              A plataforma opera em dois ambientes isolados. As contas bancárias
              e chaves de API pertencem a um ambiente específico, e precisam ser
              compatíveis entre si numa cobrança.
            </Paragraph>
            <Row gutter={[16, 16]}>
              <Col xs={24} md={12}>
                <Card
                  size="small"
                  type="inner"
                  title={<Tag color="gold">Homologação</Tag>}
                >
                  <Text type="secondary" style={{ fontSize: 13 }}>
                    Ambiente de testes (sandbox). Cobranças não movimentam
                    dinheiro real — perfeito para validar a integração.
                  </Text>
                </Card>
              </Col>
              <Col xs={24} md={12}>
                <Card
                  size="small"
                  type="inner"
                  title={<Tag color="green">Produção</Tag>}
                >
                  <Text type="secondary" style={{ fontSize: 13 }}>
                    Ambiente real. Cobranças geradas são efetivamente cobradas
                    dos seus clientes.
                  </Text>
                </Card>
              </Col>
            </Row>
          </DocSection>

          <DocSection
            id="sys-checkout"
            icon={<ShoppingCartOutlined />}
            title="Checkout"
          >
            <Paragraph>
              Cada cobrança gera um <Text strong>link de checkout</Text> que
              pode ser enviado ao pagador. Na página de checkout, ele visualiza
              o valor, o QR Code PIX (ou o boleto), copia o código e acompanha a
              confirmação do pagamento em tempo real.
            </Paragraph>
            <Paragraph type="secondary">
              O link pode ser regerado a partir da tela de Transações quando
              necessário.
            </Paragraph>
          </DocSection>

          <DocSection
            id="sys-rbac"
            icon={<LockOutlined />}
            title="Papéis e permissões"
          >
            <Paragraph>
              O acesso é controlado por papéis (RBAC). Um usuário pode ter um ou
              mais papéis, que determinam quais módulos e ações ficam
              disponíveis.
            </Paragraph>
            <Table
              size="small"
              pagination={false}
              columns={roleColumns}
              dataSource={ROLES}
            />
            <Paragraph type="secondary" style={{ marginTop: 12 }}>
              Permissões granulares (ex.: <Text code>customers.read</Text>,{" "}
              <Text code>integrations.manage</Text>) podem complementar os
              papéis para liberar ações específicas.
            </Paragraph>
          </DocSection>

          <DocSection id="sys-support" icon={<TeamOutlined />} title="Suporte">
            <Paragraph type="secondary">
              Dúvidas ou problemas? Acione o administrador da sua organização ou
              o canal de suporte do Moonevue. Para questões de integração,
              consulte as abas <Text strong>Início rápido</Text> e{" "}
              <Text strong>Referência da API</Text>.
            </Paragraph>
          </DocSection>
        </Space>
      </Col>
    </Row>
  );
}

/* ------------------------------------------------------------------ */
/* Início rápido (integração)                                          */
/* ------------------------------------------------------------------ */

function QuickStartDocs() {
  return (
    <Space
      direction="vertical"
      size="large"
      style={{ width: "100%", maxWidth: 820 }}
    >
      <div>
        <Title level={4} style={{ marginTop: 0 }}>
          Integração via API
        </Title>
        <Paragraph type="secondary">
          A API do Moonevue permite que seu sistema externo (loja online, ERP,
          checkout próprio) gere cobranças automaticamente. A comunicação é
          servidor-a-servidor: nunca exponha sua chave no navegador do cliente
          final.
        </Paragraph>
      </div>

      <div>
        <Title level={5}>1. Crie uma chave de API</Title>
        <Paragraph>
          Em <Link href="/dashboard/integrations">Integrações</Link>, na aba{" "}
          <Text strong>Chaves de API</Text>, clique em{" "}
          <Text strong>Criar chave</Text>, escolha o ambiente (comece por{" "}
          <Text code>Homologação</Text>) e os escopos. A chave completa é
          exibida uma única vez — copie e guarde com segurança.
        </Paragraph>
      </div>

      <div>
        <Title level={5}>2. Autentique suas requisições</Title>
        <Paragraph>
          Envie a chave no cabeçalho <Text code>Authorization</Text> em todas as
          chamadas:
        </Paragraph>
        <CodeBlock code={`Authorization: Bearer mvk_live_<sua_chave>`} />
      </div>

      <div>
        <Title level={5}>3. Crie uma cobrança</Title>
        <Paragraph>
          Use sempre um cabeçalho <Text code>Idempotency-Key</Text> único por
          cobrança para evitar duplicidade em caso de retry.
        </Paragraph>
        <CodeBlock
          code={`curl -X POST https://SEU_HOST/api/v1/charges \\
  -H "Authorization: Bearer $MOONEVUE_API_KEY" \\
  -H "Idempotency-Key: $(uuidgen)" \\
  -H "Content-Type: application/json" \\
  -d '{
    "method": "PIX_DUE",
    "bank": "EFI",
    "bankConfigurationId": 12,
    "amount": 149.90,
    "dueDate": "2026-07-10",
    "externalReference": "pedido-9988",
    "customer": {
      "name": "Maria Souza",
      "document": "12345678909",
      "email": "maria@exemplo.com"
    }
  }'`}
        />
      </div>

      <div>
        <Title level={5}>4. Consulte o status</Title>
        <Paragraph>
          Use o <Text code>id</Text> retornado na criação para acompanhar o
          pagamento:
        </Paragraph>
        <CodeBlock
          code={`curl https://SEU_HOST/api/v1/charges/<id> \\
  -H "Authorization: Bearer $MOONEVUE_API_KEY"`}
        />
      </div>

      <Alert
        type="info"
        showIcon
        message="Boas práticas"
        description={
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            <li>Conceda apenas os escopos necessários a cada chave.</li>
            <li>Rotacione chaves periodicamente e revogue as que não usar.</li>
            <li>
              Trate os códigos 401 (chave inválida), 403 (sem escopo) e 429
              (limite excedido).
            </li>
            <li>
              Veja a referência completa na aba{" "}
              <Text strong>Referência da API</Text>.
            </li>
          </ul>
        }
      />
    </Space>
  );
}

/* ------------------------------------------------------------------ */
/* Referência da API pública                                           */
/* ------------------------------------------------------------------ */

type FieldRow = {
  key: string;
  field: string;
  type: string;
  required: boolean;
  description: string;
};

const fieldColumns: ColumnsType<FieldRow> = [
  {
    title: "Campo",
    dataIndex: "field",
    key: "field",
    width: 200,
    render: (v: string) => <Text code>{v}</Text>,
  },
  {
    title: "Tipo",
    dataIndex: "type",
    key: "type",
    width: 130,
    render: (v: string) => (
      <Text type="secondary" style={{ fontSize: 12 }}>
        {v}
      </Text>
    ),
  },
  {
    title: "Obrigatório",
    dataIndex: "required",
    key: "required",
    width: 110,
    render: (req: boolean) =>
      req ? <Tag color="red">Sim</Tag> : <Tag>Opcional</Tag>,
  },
  {
    title: "Descrição",
    dataIndex: "description",
    key: "description",
  },
];

const REQUEST_FIELDS: FieldRow[] = [
  {
    key: "method",
    field: "method",
    type: "enum",
    required: true,
    description: "PIX_IMMEDIATE, PIX_DUE ou BOLETO.",
  },
  {
    key: "bank",
    field: "bank",
    type: "enum",
    required: true,
    description: "Provedor de pagamento: ASAAS ou EFI.",
  },
  {
    key: "bankConfigurationId",
    field: "bankConfigurationId",
    type: "number",
    required: true,
    description:
      "ID da configuração bancária. O ambiente dela (sandbox/produção) deve ser compatível com o da chave.",
  },
  {
    key: "amount",
    field: "amount",
    type: "decimal",
    required: true,
    description: "Valor da cobrança, em reais (ex.: 149.90).",
  },
  {
    key: "dueDate",
    field: "dueDate",
    type: "date (YYYY-MM-DD)",
    required: false,
    description: "Vencimento. Obrigatório para PIX_DUE e BOLETO.",
  },
  {
    key: "description",
    field: "description",
    type: "string",
    required: false,
    description: "Descrição exibida ao pagador.",
  },
  {
    key: "externalReference",
    field: "externalReference",
    type: "string",
    required: false,
    description: "Sua referência interna (ex.: número do pedido).",
  },
  {
    key: "pixKey",
    field: "pixKey",
    type: "string",
    required: false,
    description: "Chave PIX específica, quando aplicável ao provedor.",
  },
  {
    key: "customer",
    field: "customer",
    type: "object",
    required: false,
    description:
      "Dados do pagador. Recomendado para vincular a cobrança a um cliente.",
  },
];

const CUSTOMER_FIELDS: FieldRow[] = [
  {
    key: "name",
    field: "customer.name",
    type: "string",
    required: false,
    description: "Nome completo. Obrigatório para criar/vincular o cliente.",
  },
  {
    key: "document",
    field: "customer.document",
    type: "string",
    required: false,
    description:
      "CPF ou CNPJ (apenas dígitos). Obrigatório para vincular o cliente.",
  },
  {
    key: "email",
    field: "customer.email",
    type: "string",
    required: false,
    description: "E-mail do pagador.",
  },
  {
    key: "phone",
    field: "customer.phone",
    type: "string",
    required: false,
    description: "Telefone do pagador.",
  },
];

const RESPONSE_FIELDS: FieldRow[] = [
  {
    key: "id",
    field: "id",
    type: "string",
    required: true,
    description:
      "Identificador da cobrança no provedor. Use-o para consultar o status.",
  },
  {
    key: "status",
    field: "status",
    type: "string",
    required: true,
    description: "Situação atual (PENDING, PAID, CONFIRMED, EXPIRED, etc.).",
  },
  {
    key: "method",
    field: "method",
    type: "string",
    required: true,
    description: "Método utilizado na cobrança.",
  },
  {
    key: "provider",
    field: "provider",
    type: "string",
    required: true,
    description: "Provedor que processou a cobrança.",
  },
  {
    key: "amount",
    field: "amount",
    type: "decimal",
    required: true,
    description: "Valor da cobrança.",
  },
  {
    key: "currency",
    field: "currency",
    type: "string",
    required: true,
    description: "Moeda (BRL).",
  },
  {
    key: "externalReference",
    field: "externalReference",
    type: "string",
    required: false,
    description: "A referência enviada na criação.",
  },
  {
    key: "pix",
    field: "pix",
    type: "object",
    required: false,
    description:
      "Presente em cobranças PIX: { copyPaste, location, expiresInSeconds }.",
  },
  {
    key: "boleto",
    field: "boleto",
    type: "object",
    required: false,
    description: "Presente em boletos: { line, pdfUrl, invoiceUrl }.",
  },
  {
    key: "createdAt",
    field: "createdAt",
    type: "string (ISO-8601)",
    required: true,
    description: "Data/hora de criação.",
  },
];

const ERROR_CODES: {
  key: string;
  code: string;
  http: string;
  description: string;
}[] = [
  {
    key: "1",
    code: "unauthorized",
    http: "401",
    description: "API Key ausente, inválida ou revogada.",
  },
  {
    key: "2",
    code: "forbidden",
    http: "403",
    description: "A chave não possui o escopo necessário para a operação.",
  },
  {
    key: "3",
    code: "idempotency_key_required",
    http: "400",
    description: "Header Idempotency-Key obrigatório na criação de cobrança.",
  },
  {
    key: "4",
    code: "idempotency_conflict",
    http: "409",
    description: "Idempotency-Key reutilizada com um payload diferente.",
  },
  {
    key: "5",
    code: "environment_mismatch",
    http: "422",
    description:
      "Ambiente da chave incompatível com o da configuração bancária.",
  },
  {
    key: "6",
    code: "invalid_request",
    http: "422",
    description: "Dados da cobrança inválidos ou incompletos.",
  },
  {
    key: "7",
    code: "not_found",
    http: "404",
    description: "Cobrança não encontrada para a chave/tenant.",
  },
  {
    key: "8",
    code: "rate_limited",
    http: "429",
    description:
      "Limite de requisições por minuto excedido. Veja o header Retry-After.",
  },
  {
    key: "9",
    code: "provider_error",
    http: "502",
    description: "Falha ao comunicar com o provedor de pagamento.",
  },
];

const apiAnchorItems = [
  { key: "overview", href: "#doc-overview", title: "Visão geral" },
  { key: "auth", href: "#doc-auth", title: "Autenticação" },
  { key: "scopes", href: "#doc-scopes", title: "Escopos" },
  { key: "environments", href: "#doc-environments", title: "Ambientes" },
  { key: "idempotency", href: "#doc-idempotency", title: "Idempotência" },
  { key: "ratelimit", href: "#doc-ratelimit", title: "Limites de uso" },
  {
    key: "endpoints",
    href: "#doc-endpoints",
    title: "Endpoints",
    children: [
      { key: "ep-ping", href: "#doc-ep-ping", title: "Ping" },
      { key: "ep-create", href: "#doc-ep-create", title: "Criar cobrança" },
      { key: "ep-get", href: "#doc-ep-get", title: "Consultar cobrança" },
    ],
  },
  { key: "schemas", href: "#doc-schemas", title: "Referência de campos" },
  { key: "errors", href: "#doc-errors", title: "Erros" },
  { key: "examples", href: "#doc-examples", title: "Exemplos de código" },
];

function ApiDocs() {
  return (
    <Row gutter={0} wrap>
      <Col xs={0} lg={5} style={{ paddingRight: 24 }}>
        <div style={{ position: "sticky", top: 8 }}>
          <Text strong style={{ fontSize: 12, color: "#8c8c8c" }}>
            NESTA PÁGINA
          </Text>
          <Anchor
            affix={false}
            offsetTop={8}
            getContainer={getDocsScrollContainer}
            items={apiAnchorItems}
          />
        </div>
      </Col>

      <Col xs={24} lg={19}>
        <Space
          direction="vertical"
          size="large"
          style={{ width: "100%", maxWidth: 880 }}
        >
          <div>
            <Tag color="blue">API v1</Tag>
            <Title level={3} style={{ marginTop: 8, marginBottom: 4 }}>
              Referência da API
            </Title>
            <Paragraph type="secondary" style={{ marginBottom: 0 }}>
              Referência completa da API pública de cobranças do Moonevue.
              Integração servidor-a-servidor para emitir e consultar cobranças
              PIX e boleto de forma programática.
            </Paragraph>
          </div>

          <Divider style={{ margin: 0 }} />

          <DocSection
            id="doc-overview"
            icon={<BookOutlined />}
            title="Visão geral"
          >
            <Paragraph>
              Todas as chamadas usam HTTPS e trocam dados em{" "}
              <Text code>JSON</Text>. A URL base de todos os endpoints é:
            </Paragraph>
            <CodeBlock code={`https://SEU_HOST/api/v1`} />
            <Alert
              style={{ marginTop: 12 }}
              type="warning"
              showIcon
              icon={<WarningOutlined />}
              message="Comunicação apenas no servidor"
              description="As chamadas devem partir do seu backend. Nunca exponha a API Key no navegador, em apps mobile ou repositórios públicos."
            />
          </DocSection>

          <DocSection
            id="doc-auth"
            icon={<LockOutlined />}
            title="Autenticação"
          >
            <Paragraph>
              Autentique cada requisição enviando sua chave no cabeçalho{" "}
              <Text code>Authorization</Text>, no formato Bearer:
            </Paragraph>
            <CodeBlock code={`Authorization: Bearer mvk_live_<sua_chave>`} />
            <Paragraph style={{ marginTop: 12 }}>
              O prefixo identifica o ambiente da chave:
            </Paragraph>
            <ul style={{ paddingLeft: 18 }}>
              <li>
                <Text code>mvk_live_…</Text> — ambiente de{" "}
                <Tag color="green">Produção</Tag>
              </li>
              <li>
                <Text code>mvk_test_…</Text> — ambiente de{" "}
                <Tag color="gold">Homologação</Tag>
              </li>
            </ul>
            <Alert
              type="info"
              showIcon
              message="A chave é exibida uma única vez"
              description="No momento da criação ou rotação, copie e armazene a chave em um local seguro (cofre de segredos / variável de ambiente). Não é possível recuperá-la depois."
            />
          </DocSection>

          <DocSection
            id="doc-scopes"
            icon={<SafetyCertificateOutlined />}
            title="Escopos"
          >
            <Paragraph>
              Cada chave possui escopos que limitam suas permissões. Conceda
              apenas o necessário (princípio do menor privilégio).
            </Paragraph>
            <Table
              size="small"
              pagination={false}
              rowKey="value"
              dataSource={API_KEY_SCOPES}
              columns={[
                {
                  title: "Escopo",
                  dataIndex: "value",
                  key: "value",
                  width: 180,
                  render: (v: string) => <Text code>{v}</Text>,
                },
                {
                  title: "Permissão",
                  dataIndex: "label",
                  key: "label",
                  width: 180,
                },
                {
                  title: "Descrição",
                  dataIndex: "description",
                  key: "description",
                },
              ]}
            />
          </DocSection>

          <DocSection
            id="doc-environments"
            icon={<SyncOutlined />}
            title="Ambientes"
          >
            <Paragraph>
              Existem dois ambientes isolados. O ambiente da chave precisa ser
              compatível com o ambiente da configuração bancária (
              <Text code>bankConfigurationId</Text>) usada na cobrança.
            </Paragraph>
            <Row gutter={[16, 16]}>
              <Col xs={24} md={12}>
                <Card
                  size="small"
                  type="inner"
                  title={<Tag color="gold">Homologação · TEST</Tag>}
                >
                  <Paragraph style={{ marginBottom: 0 }} type="secondary">
                    Chaves <Text code>mvk_test_</Text> usam configurações em{" "}
                    <Text strong>sandbox</Text>. Ideal para testar a integração
                    sem movimentar dinheiro real.
                  </Paragraph>
                </Card>
              </Col>
              <Col xs={24} md={12}>
                <Card
                  size="small"
                  type="inner"
                  title={<Tag color="green">Produção · LIVE</Tag>}
                >
                  <Paragraph style={{ marginBottom: 0 }} type="secondary">
                    Chaves <Text code>mvk_live_</Text> usam configurações em{" "}
                    <Text strong>produção</Text>. Cobranças reais são geradas
                    para seus clientes.
                  </Paragraph>
                </Card>
              </Col>
            </Row>
            <Alert
              style={{ marginTop: 12 }}
              type="error"
              showIcon
              message="environment_mismatch (422)"
              description="Usar uma chave de homologação com uma configuração de produção (ou vice-versa) é bloqueado e retorna erro."
            />
          </DocSection>

          <DocSection
            id="doc-idempotency"
            icon={<SafetyCertificateOutlined />}
            title="Idempotência"
          >
            <Paragraph>
              A criação de cobrança exige o cabeçalho{" "}
              <Text code>Idempotency-Key</Text> — um valor único por cobrança
              (ex.: um UUID). Em caso de retry de rede, reenviar a mesma chave
              com o mesmo payload retorna a cobrança já criada, sem duplicar.
            </Paragraph>
            <CodeBlock
              code={`Idempotency-Key: 6f1e9c2a-2b8e-4f7a-9c10-7d3a1f0b2c44`}
            />
            <ul style={{ paddingLeft: 18, marginTop: 12 }}>
              <li>
                Mesma chave + mesmo payload → retorna a resposta original.
              </li>
              <li>
                Mesma chave + payload diferente →{" "}
                <Text code>409 idempotency_conflict</Text>.
              </li>
            </ul>
          </DocSection>

          <DocSection
            id="doc-ratelimit"
            icon={<ClockCircleOutlined />}
            title="Limites de uso"
          >
            <Paragraph>
              As requisições são limitadas por chave (padrão de{" "}
              <Text strong>120 requisições por minuto</Text>). Ao exceder, a API
              responde <Text code>429 rate_limited</Text>. Use os cabeçalhos da
              resposta para se ajustar:
            </Paragraph>
            <Table
              size="small"
              pagination={false}
              rowKey="header"
              dataSource={[
                {
                  header: "X-RateLimit-Limit",
                  desc: "Total de requisições permitidas na janela.",
                },
                {
                  header: "X-RateLimit-Remaining",
                  desc: "Requisições restantes na janela atual.",
                },
                {
                  header: "Retry-After",
                  desc: "Segundos a aguardar antes de tentar novamente (em 429).",
                },
              ]}
              columns={[
                {
                  title: "Cabeçalho",
                  dataIndex: "header",
                  key: "header",
                  width: 220,
                  render: (v: string) => <Text code>{v}</Text>,
                },
                { title: "Descrição", dataIndex: "desc", key: "desc" },
              ]}
            />
          </DocSection>

          <DocSection
            id="doc-endpoints"
            icon={<ApiOutlined />}
            title="Endpoints"
          >
            <Space direction="vertical" size="large" style={{ width: "100%" }}>
              <Card
                id="doc-ep-ping"
                size="small"
                title={<EndpointHeader method="GET" path="/api/v1/ping" />}
                style={{ scrollMarginTop: 16 }}
              >
                <Paragraph>
                  Verifica a validade da chave e retorna o tenant e os escopos
                  associados. Útil para testar a configuração.
                </Paragraph>
                <Text strong>Resposta 200</Text>
                <CodeBlock
                  code={`{
  "tenantId": 3,
  "scopes": ["charges:write", "charges:read"],
  "status": "ok"
}`}
                />
              </Card>

              <Card
                id="doc-ep-create"
                size="small"
                title={
                  <EndpointHeader
                    method="POST"
                    path="/api/v1/charges"
                    scope="charges:write"
                  />
                }
                style={{ scrollMarginTop: 16 }}
              >
                <Paragraph>
                  Cria uma nova cobrança. Requer o cabeçalho{" "}
                  <Text code>Idempotency-Key</Text>.
                </Paragraph>
                <Text strong>Requisição</Text>
                <CodeBlock
                  code={`POST /api/v1/charges
Authorization: Bearer $MOONEVUE_API_KEY
Idempotency-Key: <uuid>
Content-Type: application/json

{
  "method": "PIX_DUE",
  "bank": "EFI",
  "bankConfigurationId": 12,
  "amount": 149.90,
  "dueDate": "2026-07-10",
  "description": "Assinatura mensal",
  "externalReference": "pedido-9988",
  "customer": {
    "name": "Maria Souza",
    "document": "12345678909",
    "email": "maria@exemplo.com"
  }
}`}
                />
                <Text strong>Resposta 201</Text>
                <CodeBlock
                  code={`{
  "id": "pay_0d3f...",
  "status": "PENDING",
  "method": "PIX_DUE",
  "provider": "EFI",
  "amount": 149.90,
  "currency": "BRL",
  "externalReference": "pedido-9988",
  "pix": {
    "copyPaste": "00020126...5204000053039865802BR...",
    "location": "https://...",
    "expiresInSeconds": 86400
  },
  "createdAt": "2026-06-22T14:35:00-03:00"
}`}
                />
              </Card>

              <Card
                id="doc-ep-get"
                size="small"
                title={
                  <EndpointHeader
                    method="GET"
                    path="/api/v1/charges/{id}"
                    scope="charges:read"
                  />
                }
                style={{ scrollMarginTop: 16 }}
              >
                <Paragraph>
                  Consulta o status e os detalhes de uma cobrança pelo{" "}
                  <Text code>id</Text> retornado na criação.
                </Paragraph>
                <CodeBlock
                  code={`GET /api/v1/charges/pay_0d3f...
Authorization: Bearer $MOONEVUE_API_KEY`}
                />
              </Card>
            </Space>
          </DocSection>

          <DocSection
            id="doc-schemas"
            icon={<CodeOutlined />}
            title="Referência de campos"
          >
            <Title level={5}>Corpo da cobrança</Title>
            <Table
              size="small"
              pagination={false}
              columns={fieldColumns}
              dataSource={REQUEST_FIELDS}
            />
            <Title level={5} style={{ marginTop: 24 }}>
              Objeto customer
            </Title>
            <Table
              size="small"
              pagination={false}
              columns={fieldColumns}
              dataSource={CUSTOMER_FIELDS}
            />
            <Title level={5} style={{ marginTop: 24 }}>
              Resposta da cobrança
            </Title>
            <Table
              size="small"
              pagination={false}
              columns={fieldColumns}
              dataSource={RESPONSE_FIELDS}
            />
          </DocSection>

          <DocSection id="doc-errors" icon={<WarningOutlined />} title="Erros">
            <Paragraph>
              Os erros seguem um formato consistente, com um código estável em{" "}
              <Text code>error.code</Text> e uma mensagem legível:
            </Paragraph>
            <CodeBlock
              code={`{
  "error": {
    "code": "forbidden",
    "message": "A chave não possui o escopo charges:write"
  }
}`}
            />
            <Table
              style={{ marginTop: 12 }}
              size="small"
              pagination={false}
              rowKey="code"
              dataSource={ERROR_CODES}
              columns={[
                {
                  title: "HTTP",
                  dataIndex: "http",
                  key: "http",
                  width: 80,
                  render: (v: string) => <Tag>{v}</Tag>,
                },
                {
                  title: "Código",
                  dataIndex: "code",
                  key: "code",
                  width: 220,
                  render: (v: string) => <Text code>{v}</Text>,
                },
                {
                  title: "Quando ocorre",
                  dataIndex: "description",
                  key: "description",
                },
              ]}
            />
          </DocSection>

          <DocSection
            id="doc-examples"
            icon={<CodeOutlined />}
            title="Exemplos de código"
          >
            <Title level={5}>cURL</Title>
            <CodeBlock
              code={`curl -X POST https://SEU_HOST/api/v1/charges \\
  -H "Authorization: Bearer $MOONEVUE_API_KEY" \\
  -H "Idempotency-Key: $(uuidgen)" \\
  -H "Content-Type: application/json" \\
  -d '{
    "method": "PIX_IMMEDIATE",
    "bank": "ASAAS",
    "bankConfigurationId": 12,
    "amount": 99.90,
    "description": "Pedido 1234",
    "customer": { "name": "Joao Lima", "document": "12345678909" }
  }'`}
            />
            <Title level={5} style={{ marginTop: 24 }}>
              Node.js
            </Title>
            <CodeBlock
              code={`import { randomUUID } from "node:crypto";

const res = await fetch("https://SEU_HOST/api/v1/charges", {
  method: "POST",
  headers: {
    Authorization: \`Bearer \${process.env.MOONEVUE_API_KEY}\`,
    "Idempotency-Key": randomUUID(),
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    method: "PIX_IMMEDIATE",
    bank: "ASAAS",
    bankConfigurationId: 12,
    amount: 99.9,
    customer: { name: "Joao Lima", document: "12345678909" },
  }),
});

const charge = await res.json();
console.log(charge.id, charge.status, charge.pix?.copyPaste);`}
            />
            <Title level={5} style={{ marginTop: 24 }}>
              Python
            </Title>
            <CodeBlock
              code={`import os, uuid, requests

resp = requests.post(
    "https://SEU_HOST/api/v1/charges",
    headers={
        "Authorization": f"Bearer {os.environ['MOONEVUE_API_KEY']}",
        "Idempotency-Key": str(uuid.uuid4()),
    },
    json={
        "method": "PIX_IMMEDIATE",
        "bank": "ASAAS",
        "bankConfigurationId": 12,
        "amount": 99.90,
        "customer": {"name": "Joao Lima", "document": "12345678909"},
    },
)
charge = resp.json()
print(charge["id"], charge["status"])`}
            />
          </DocSection>
        </Space>
      </Col>
    </Row>
  );
}

/* ------------------------------------------------------------------ */
/* Página                                                              */
/* ------------------------------------------------------------------ */

export default function DocsPage() {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        height: "calc(100vh - 112px)",
      }}
    >
      <div style={{ flexShrink: 0, marginBottom: 16 }}>
        <Tag color="blue" icon={<BookOutlined />}>
          Documentação
        </Tag>
        <Title level={2} style={{ marginTop: 8, marginBottom: 4 }}>
          Central de Documentação do Moonevue
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0 }}>
          Guia completo da plataforma: módulos, conceitos de cobrança,
          ambientes, papéis de acesso e a referência da API de integração.
        </Paragraph>
      </div>

      <Tabs
        className="docs-tabs"
        style={{ flex: 1, minHeight: 0 }}
        defaultActiveKey="system"
        items={[
          {
            key: "system",
            label: (
              <Space>
                <BookOutlined />
                Sistema
              </Space>
            ),
            children: <SystemDocs />,
          },
          {
            key: "quickstart",
            label: (
              <Space>
                <RocketOutlined />
                Início rápido
              </Space>
            ),
            children: <QuickStartDocs />,
          },
          {
            key: "api",
            label: (
              <Space>
                <ApiOutlined />
                Referência da API
              </Space>
            ),
            children: <ApiDocs />,
          },
        ]}
      />
    </div>
  );
}
