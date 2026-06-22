"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Progress,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  ApiOutlined,
  BarChartOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  DollarOutlined,
  KeyOutlined,
  PlusOutlined,
  ReloadOutlined,
  RiseOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import {
  API_KEY_SCOPES,
  ApiKey,
  ApiKeyEnvironment,
  CreateApiKeyRequest,
  IntegrationAnalytics,
  IntegrationsApi,
} from "@/lib/api";
import { useAuth } from "@/app/providers";
import { canManageIntegrations } from "@/lib/authz";

const { Title, Text, Paragraph } = Typography;

interface KeyFormValues {
  name: string;
  environment: ApiKeyEnvironment;
  scopes: string[];
}

function formatDateTime(value?: string | null): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString("pt-BR");
}

function ApiKeysTab() {
  const { message } = App.useApp();
  const [form] = Form.useForm<KeyFormValues>();

  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [createdKey, setCreatedKey] = useState<ApiKey | null>(null);

  const loadKeys = async () => {
    setLoading(true);
    try {
      const data = await IntegrationsApi.listKeys();
      setKeys(data ?? []);
    } catch (err: any) {
      message.error(err?.message || "Falha ao carregar chaves de API");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadKeys();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCreate = () => {
    form.setFieldsValue({
      environment: "TEST",
      scopes: ["charges:write", "charges:read"],
      name: "",
    });
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload: CreateApiKeyRequest = {
        name: values.name.trim(),
        environment: values.environment,
        scopes: values.scopes,
      };
      const created = await IntegrationsApi.createKey(payload);
      setCreateOpen(false);
      form.resetFields();
      setCreatedKey(created);
      await loadKeys();
    } catch (err: any) {
      if (err?.errorFields) return; // erro de validação do form
      message.error(err?.message || "Falha ao criar chave de API");
    } finally {
      setSubmitting(false);
    }
  };

  const handleRevoke = async (key: ApiKey) => {
    try {
      await IntegrationsApi.revokeKey(key.id);
      message.success("Chave revogada");
      await loadKeys();
    } catch (err: any) {
      message.error(err?.message || "Falha ao revogar chave");
    }
  };

  const handleRotate = async (key: ApiKey) => {
    try {
      const created = await IntegrationsApi.rotateKey(key.id);
      setCreatedKey(created);
      message.success("Chave rotacionada. A anterior foi revogada.");
      await loadKeys();
    } catch (err: any) {
      message.error(err?.message || "Falha ao rotacionar chave");
    }
  };

  const columns: ColumnsType<ApiKey> = useMemo(
    () => [
      {
        title: "Nome",
        dataIndex: "name",
        key: "name",
        render: (name: string, record) => (
          <Space direction="vertical" size={0}>
            <Text strong>{name}</Text>
            <Text
              type="secondary"
              style={{ fontFamily: "monospace", fontSize: 12 }}
            >
              {record.keyPrefix}
            </Text>
          </Space>
        ),
      },
      {
        title: "Ambiente",
        dataIndex: "environment",
        key: "environment",
        render: (env: ApiKeyEnvironment) => (
          <Tag color={env === "LIVE" ? "green" : "gold"}>
            {env === "LIVE" ? "Produção" : "Homologação"}
          </Tag>
        ),
      },
      {
        title: "Escopos",
        dataIndex: "scopes",
        key: "scopes",
        render: (scopes: string[]) => (
          <Space size={[0, 4]} wrap>
            {scopes.map((s) => (
              <Tag key={s}>{s}</Tag>
            ))}
          </Space>
        ),
      },
      {
        title: "Status",
        dataIndex: "status",
        key: "status",
        render: (status: string) => (
          <Tag color={status === "ACTIVE" ? "blue" : "red"}>
            {status === "ACTIVE" ? "Ativa" : "Revogada"}
          </Tag>
        ),
      },
      {
        title: "Último uso",
        dataIndex: "lastUsedAt",
        key: "lastUsedAt",
        render: (value?: string | null) => (
          <Text type="secondary">{formatDateTime(value)}</Text>
        ),
      },
      {
        title: "Ações",
        key: "actions",
        align: "right",
        render: (_, record) =>
          record.status === "ACTIVE" ? (
            <Space>
              <Popconfirm
                title="Rotacionar chave"
                description="Gera uma nova chave e revoga a atual. Atualize seus sistemas."
                okText="Rotacionar"
                cancelText="Cancelar"
                onConfirm={() => handleRotate(record)}
              >
                <Button size="small" icon={<ReloadOutlined />}>
                  Rotacionar
                </Button>
              </Popconfirm>
              <Popconfirm
                title="Revogar chave"
                description="Esta ação é irreversível. Sistemas que usam esta chave deixarão de funcionar."
                okText="Revogar"
                okButtonProps={{ danger: true }}
                cancelText="Cancelar"
                onConfirm={() => handleRevoke(record)}
              >
                <Button size="small" danger icon={<DeleteOutlined />}>
                  Revogar
                </Button>
              </Popconfirm>
            </Space>
          ) : (
            <Text type="secondary">—</Text>
          ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  return (
    <>
      <Space
        style={{
          width: "100%",
          justifyContent: "space-between",
          marginBottom: 16,
        }}
      >
        <Text type="secondary">
          Crie chaves para que sistemas externos (loja online, ERP) emitam
          cobranças via API.
        </Text>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Criar chave
        </Button>
      </Space>

      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={keys}
        pagination={false}
        locale={{ emptyText: "Nenhuma chave de API criada ainda" }}
      />

      <Modal
        title="Criar chave de API"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={handleCreate}
        confirmLoading={submitting}
        okText="Criar chave"
        cancelText="Cancelar"
        destroyOnClose
      >
        <Form form={form} layout="vertical" requiredMark="optional">
          <Form.Item
            name="name"
            label="Nome"
            rules={[
              {
                required: true,
                message: "Informe um nome para identificar a chave",
              },
            ]}
          >
            <Input placeholder="Ex.: Loja online — produção" maxLength={120} />
          </Form.Item>
          <Form.Item
            name="environment"
            label="Ambiente"
            rules={[{ required: true, message: "Selecione o ambiente" }]}
          >
            <Select
              options={[
                { value: "TEST", label: "Homologação (sandbox)" },
                { value: "LIVE", label: "Produção" },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="scopes"
            label="Escopos"
            rules={[
              { required: true, message: "Selecione ao menos um escopo" },
            ]}
          >
            <Select
              mode="multiple"
              placeholder="Selecione os escopos"
              options={API_KEY_SCOPES.map((s) => ({
                value: s.value,
                label: `${s.label} (${s.value})`,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Chave criada"
        open={!!createdKey}
        onCancel={() => setCreatedKey(null)}
        footer={[
          <Button key="done" type="primary" onClick={() => setCreatedKey(null)}>
            Já copiei minha chave
          </Button>,
        ]}
        closable={false}
        maskClosable={false}
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="Guarde esta chave agora"
          description="Por segurança, ela não será exibida novamente. Se perder, gere uma nova."
        />
        <Paragraph
          copyable={{ text: createdKey?.plaintextKey ?? "" }}
          style={{
            fontFamily: "monospace",
            background: "#f5f5f5",
            padding: "12px",
            borderRadius: 8,
            wordBreak: "break-all",
          }}
        >
          {createdKey?.plaintextKey}
        </Paragraph>
      </Modal>
    </>
  );
}

const BRL = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const BRL_COMPACT = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
  notation: "compact",
  maximumFractionDigits: 1,
});

const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: "Pendente", color: "#faad14" },
  PROCESSING: { label: "Processando", color: "#1677ff" },
  PAID: { label: "Pago", color: "#52c41a" },
  CONFIRMED: { label: "Confirmado", color: "#52c41a" },
  SETTLED: { label: "Liquidado", color: "#389e0d" },
  CAPTURED: { label: "Capturado", color: "#389e0d" },
  AUTHORIZED: { label: "Autorizado", color: "#1677ff" },
  CANCELED: { label: "Cancelado", color: "#8c8c8c" },
  EXPIRED: { label: "Expirado", color: "#bfbfbf" },
  REFUNDED: { label: "Estornado", color: "#722ed1" },
  FAILED: { label: "Falhou", color: "#ff4d4f" },
  CHECKOUT_OPEN: { label: "Checkout aberto", color: "#13c2c2" },
};

function statusMeta(status: string) {
  return STATUS_META[status] ?? { label: status, color: "#8c8c8c" };
}

function AnalyticsTab() {
  const { message } = App.useApp();
  const [data, setData] = useState<IntegrationAnalytics | null>(null);
  const [loading, setLoading] = useState(false);
  const [days, setDays] = useState(30);
  const [metric, setMetric] = useState<"count" | "amount">("count");

  const load = useCallback(
    async (range: number) => {
      setLoading(true);
      try {
        const d = await IntegrationsApi.analytics(range);
        setData(d);
      } catch (err: any) {
        message.error(err?.message || "Falha ao carregar métricas de integrações");
      } finally {
        setLoading(false);
      }
    },
    [message]
  );

  useEffect(() => {
    load(days);
  }, [days, load]);

  const maxSeries = useMemo(() => {
    if (!data) return 0;
    return data.timeseries.reduce(
      (acc, p) => Math.max(acc, metric === "count" ? p.count : Number(p.amount)),
      0
    );
  }, [data, metric]);

  const totalByStatus = useMemo(
    () => (data ? data.byStatus.reduce((acc, s) => acc + s.count, 0) : 0),
    [data]
  );

  if (loading && !data) {
    return (
      <div style={{ display: "flex", justifyContent: "center", padding: 64 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!data) {
    return <Empty description="Sem dados de utilização" />;
  }

  const { keys, usage } = data;
  const successPct = Math.round((usage.successRate ?? 0) * 100);

  const perKeyColumns: ColumnsType<IntegrationAnalytics["perKey"][number]> = [
    {
      title: "Chave",
      dataIndex: "name",
      key: "name",
      render: (name: string) => (
        <Space>
          <KeyOutlined style={{ color: "#8c8c8c" }} />
          <Text strong>{name}</Text>
        </Space>
      ),
    },
    {
      title: "Ambiente",
      dataIndex: "environment",
      key: "environment",
      width: 130,
      render: (env: string) => (
        <Tag color={env === "LIVE" ? "green" : "gold"}>
          {env === "LIVE" ? "Produção" : "Homologação"}
        </Tag>
      ),
    },
    {
      title: "Status",
      dataIndex: "status",
      key: "status",
      width: 110,
      render: (status: string) => (
        <Tag color={status === "ACTIVE" ? "blue" : "default"}>
          {status === "ACTIVE" ? "Ativa" : "Revogada"}
        </Tag>
      ),
    },
    {
      title: "Cobranças",
      dataIndex: "charges",
      key: "charges",
      width: 110,
      align: "right",
      sorter: (a, b) => a.charges - b.charges,
      defaultSortOrder: "descend",
    },
    {
      title: "Pagas",
      dataIndex: "paidCharges",
      key: "paidCharges",
      width: 90,
      align: "right",
    },
    {
      title: "Volume",
      dataIndex: "amount",
      key: "amount",
      width: 140,
      align: "right",
      render: (amount: number) => BRL.format(Number(amount) || 0),
      sorter: (a, b) => Number(a.amount) - Number(b.amount),
    },
    {
      title: "Último uso",
      dataIndex: "lastUsedAt",
      key: "lastUsedAt",
      width: 170,
      render: (value?: string | null) => (
        <Text type="secondary">{formatDateTime(value)}</Text>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <Row justify="space-between" align="middle" gutter={[12, 12]}>
        <Col>
          <Text type="secondary">
            Utilização das API Keys e cobranças originadas pela API pública.
          </Text>
        </Col>
        <Col>
          <Space>
            <Segmented
              value={days}
              onChange={(v) => setDays(v as number)}
              options={[
                { label: "7 dias", value: 7 },
                { label: "30 dias", value: 30 },
                { label: "90 dias", value: 90 },
              ]}
            />
            <Button icon={<ReloadOutlined />} onClick={() => load(days)} loading={loading}>
              Atualizar
            </Button>
          </Space>
        </Col>
      </Row>

      {/* KPIs */}
      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="Cobranças via API"
              value={usage.totalCharges}
              prefix={<ThunderboltOutlined style={{ color: "#1677ff" }} />}
            />
            <Text type="secondary" style={{ fontSize: 12 }}>
              {usage.chargesLast7d} nos últimos 7 dias
            </Text>
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="Volume pago"
              value={BRL_COMPACT.format(usage.paidAmount ?? 0)}
              prefix={<DollarOutlined style={{ color: "#52c41a" }} />}
            />
            <Text type="secondary" style={{ fontSize: 12 }}>
              de {BRL_COMPACT.format(usage.totalAmount ?? 0)} emitidos
            </Text>
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="Taxa de conversão"
              value={successPct}
              suffix="%"
              prefix={<RiseOutlined style={{ color: "#722ed1" }} />}
            />
            <Progress percent={successPct} showInfo={false} strokeColor="#722ed1" size="small" />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="Chaves ativas"
              value={keys.active}
              suffix={`/ ${keys.total}`}
              prefix={<KeyOutlined style={{ color: "#faad14" }} />}
            />
            <Text type="secondary" style={{ fontSize: 12 }}>
              {keys.usedLast7d} usadas em 7 dias · {keys.neverUsed} nunca usadas
            </Text>
          </Card>
        </Col>
      </Row>

      {/* Série temporal */}
      <Card
        title={
          <Space>
            <BarChartOutlined />
            <span>Cobranças via API no período</span>
          </Space>
        }
        extra={
          <Segmented
            value={metric}
            onChange={(v) => setMetric(v as "count" | "amount")}
            options={[
              { label: "Quantidade", value: "count" },
              { label: "Volume", value: "amount" },
            ]}
          />
        }
      >
        {data.timeseries.every((p) => p.count === 0) ? (
          <Empty description="Nenhuma cobrança via API no período" />
        ) : (
          (() => {
            const fmtAxis = (v: number) =>
              metric === "count" ? String(Math.round(v)) : BRL_COMPACT.format(v);
            const ticks = [maxSeries, maxSeries / 2, 0];
            const labelStep = Math.ceil(data.timeseries.length / 12);
            return (
              <div>
                {/* Legenda */}
                <div style={{ marginBottom: 12 }}>
                  <Space size={6}>
                    <span
                      style={{
                        display: "inline-block",
                        width: 12,
                        height: 12,
                        borderRadius: 3,
                        background: "linear-gradient(180deg, #1677ff 0%, #69b1ff 100%)",
                      }}
                    />
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {metric === "count" ? "Quantidade de cobranças" : "Volume (R$)"}
                    </Text>
                  </Space>
                </div>

                <div style={{ display: "flex", gap: 8 }}>
                  {/* Eixo Y */}
                  <div
                    style={{
                      display: "flex",
                      flexDirection: "column",
                      justifyContent: "space-between",
                      height: 200,
                      paddingBottom: 4,
                      textAlign: "right",
                      minWidth: 36,
                    }}
                  >
                    {ticks.map((t, i) => (
                      <Text key={i} type="secondary" style={{ fontSize: 11, lineHeight: 1 }}>
                        {fmtAxis(t)}
                      </Text>
                    ))}
                  </div>

                  {/* Área das barras + eixo X */}
                  <div style={{ flex: 1, overflowX: "auto" }}>
                    <div
                      style={{
                        position: "relative",
                        display: "flex",
                        alignItems: "flex-end",
                        gap: 4,
                        height: 200,
                        borderLeft: "1px solid #f0f0f0",
                        borderBottom: "1px solid #f0f0f0",
                        paddingLeft: 4,
                      }}
                    >
                      {/* Linhas de grade */}
                      <div
                        style={{
                          position: "absolute",
                          inset: 0,
                          borderTop: "1px dashed #f5f5f5",
                          borderBottom: "1px dashed #f5f5f5",
                          pointerEvents: "none",
                        }}
                      />
                      <div
                        style={{
                          position: "absolute",
                          top: "50%",
                          left: 0,
                          right: 0,
                          borderTop: "1px dashed #f5f5f5",
                          pointerEvents: "none",
                        }}
                      />
                      {data.timeseries.map((p) => {
                        const value = metric === "count" ? p.count : Number(p.amount);
                        const heightPct = maxSeries > 0 ? (value / maxSeries) * 100 : 0;
                        const label =
                          metric === "count"
                            ? `${p.date} — ${p.count} cobrança(s)`
                            : `${p.date} — ${BRL.format(Number(p.amount) || 0)}`;
                        return (
                          <Tooltip key={p.date} title={label}>
                            <div
                              style={{
                                flex: "1 0 14px",
                                display: "flex",
                                flexDirection: "column",
                                alignItems: "center",
                                justifyContent: "flex-end",
                                height: "100%",
                                zIndex: 1,
                              }}
                            >
                              <div
                                style={{
                                  width: "100%",
                                  minHeight: value > 0 ? 3 : 0,
                                  height: `${heightPct}%`,
                                  background:
                                    "linear-gradient(180deg, #1677ff 0%, #69b1ff 100%)",
                                  borderRadius: 4,
                                  transition: "height 0.3s ease",
                                }}
                              />
                            </div>
                          </Tooltip>
                        );
                      })}
                    </div>

                    {/* Eixo X (datas) */}
                    <div style={{ display: "flex", gap: 4, paddingLeft: 4, marginTop: 4 }}>
                      {data.timeseries.map((p, i) => (
                        <div
                          key={p.date}
                          style={{
                            flex: "1 0 14px",
                            textAlign: "center",
                            overflow: "hidden",
                          }}
                        >
                          {i % labelStep === 0 ? (
                            <Text
                              type="secondary"
                              style={{ fontSize: 10, whiteSpace: "nowrap" }}
                            >
                              {p.date.slice(8, 10)}/{p.date.slice(5, 7)}
                            </Text>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            );
          })()
        )}
      </Card>

      <Row gutter={[16, 16]}>
        {/* Status breakdown */}
        <Col xs={24} md={12}>
          <Card title="Cobranças por status" style={{ height: "100%" }}>
            {data.byStatus.length === 0 ? (
              <Empty description="Sem cobranças" />
            ) : (
              <Space direction="vertical" style={{ width: "100%" }} size={12}>
                {data.byStatus.map((s) => {
                  const meta = statusMeta(s.status);
                  const pct = totalByStatus > 0 ? Math.round((s.count / totalByStatus) * 100) : 0;
                  return (
                    <div key={s.status}>
                      <Row justify="space-between">
                        <Col>
                          <Tag color={meta.color}>{meta.label}</Tag>
                        </Col>
                        <Col>
                          <Text type="secondary">
                            {s.count} · {pct}%
                          </Text>
                        </Col>
                      </Row>
                      <Progress
                        percent={pct}
                        showInfo={false}
                        strokeColor={meta.color}
                        size="small"
                      />
                    </div>
                  );
                })}
              </Space>
            )}
          </Card>
        </Col>

        {/* Environment split */}
        <Col xs={24} md={12}>
          <Card title="Produção × Homologação" style={{ height: "100%" }}>
            {data.byEnvironment.length === 0 ? (
              <Empty description="Sem cobranças" />
            ) : (
              <Space direction="vertical" style={{ width: "100%" }} size={16}>
                {data.byEnvironment.map((e) => {
                  const isLive = e.environment === "LIVE";
                  const label = isLive
                    ? "Produção"
                    : e.environment === "TEST"
                      ? "Homologação"
                      : "Desconhecido";
                  return (
                    <Card key={e.environment} size="small" type="inner">
                      <Row justify="space-between" align="middle">
                        <Col>
                          <Space>
                            <CheckCircleOutlined
                              style={{ color: isLive ? "#52c41a" : "#faad14" }}
                            />
                            <Text strong>{label}</Text>
                          </Space>
                        </Col>
                        <Col style={{ textAlign: "right" }}>
                          <div>
                            <Text strong>{e.count}</Text>{" "}
                            <Text type="secondary">cobranças</Text>
                          </div>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {BRL.format(Number(e.amount) || 0)}
                          </Text>
                        </Col>
                      </Row>
                    </Card>
                  );
                })}
              </Space>
            )}
          </Card>
        </Col>
      </Row>

      {/* Per-key usage */}
      <Card title="Utilização por chave">
        <Table
          rowKey="apiKeyId"
          columns={perKeyColumns}
          dataSource={data.perKey}
          pagination={false}
          size="middle"
          locale={{ emptyText: "Nenhuma chave criada ainda" }}
        />
      </Card>
    </Space>
  );
}

export default function IntegrationsPage() {
  const { user } = useAuth();
  const allowed = canManageIntegrations(user?.roles, user?.permissions);

  if (!allowed) {
    return (
      <Alert
        type="error"
        showIcon
        message="Acesso negado"
        description="Você não tem permissão para gerenciar integrações."
      />
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <Space align="center">
        <ApiOutlined style={{ fontSize: 22 }} />
        <Title level={3} style={{ margin: 0 }}>
          Integrações
        </Title>
      </Space>

      <Tabs
        defaultActiveKey="keys"
        items={[
          { key: "keys", label: "Chaves de API", children: <ApiKeysTab /> },
          { key: "analytics", label: "Analytics", children: <AnalyticsTab /> },
        ]}
      />
    </Space>
  );
}
