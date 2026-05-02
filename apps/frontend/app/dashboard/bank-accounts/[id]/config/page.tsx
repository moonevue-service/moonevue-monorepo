'use client';

import { useEffect, useRef, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  App,
  Badge,
  Breadcrumb,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Tag,
  Typography,
  Upload,
} from 'antd';
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  EditOutlined,
  FileProtectOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import type { UploadFile } from 'antd';
import Link from 'next/link';
import { useAuth } from '@/app/providers';
import {
  BankAccountResponse,
  BankConfigurationRequest,
  BankConfigurationResponse,
  CertificateUploadResponse,
  Environment,
  FinanceApi,
} from '@/lib/api';

const { Title, Text } = Typography;
const { Option } = Select;

// EFI extraConfig shape used in the form
interface EfiFormValues {
  environment: Environment;
  webhookUrl?: string;
  isActive: boolean;
  // PIX namespace
  pix_client_id: string;
  pix_client_secret: string;
  pix_pixKey?: string;
  pix_scope?: string;
  pix_tokenUrl?: string;
  pix_baseUrl?: string;
  // Charges namespace
  charges_client_id?: string;
  charges_client_secret?: string;
  charges_scope?: string;
  charges_tokenUrl?: string;
  charges_baseUrl?: string;
}

function formValuesToExtraConfig(v: EfiFormValues): Record<string, any> {
  const pix: Record<string, string> = {
    client_id: v.pix_client_id,
    client_secret: v.pix_client_secret,
  };
  if (v.pix_pixKey) pix.pixKey = v.pix_pixKey;
  if (v.pix_scope) pix.scope = v.pix_scope;
  if (v.pix_tokenUrl) pix.tokenUrl = v.pix_tokenUrl;
  if (v.pix_baseUrl) pix.baseUrl = v.pix_baseUrl;

  const extraConfig: Record<string, any> = { pix };

  if (v.charges_client_id && v.charges_client_secret) {
    const charges: Record<string, string> = {
      client_id: v.charges_client_id,
      client_secret: v.charges_client_secret,
    };
    if (v.charges_scope) charges.scope = v.charges_scope;
    if (v.charges_tokenUrl) charges.tokenUrl = v.charges_tokenUrl;
    if (v.charges_baseUrl) charges.baseUrl = v.charges_baseUrl;
    extraConfig.charges = charges;
  }

  return extraConfig;
}

function extraConfigToFormValues(
  cfg: BankConfigurationResponse
): Partial<EfiFormValues> {
  const pix = (cfg.extraConfig?.pix ?? {}) as Record<string, string>;
  const charges = (cfg.extraConfig?.charges ?? {}) as Record<string, string>;
  return {
    environment: cfg.environment,
    webhookUrl: cfg.webhookUrl ?? '',
    isActive: cfg.isActive ?? true,
    pix_client_id: pix.client_id ?? '',
    pix_client_secret: pix.client_secret ?? '',
    pix_pixKey: pix.pixKey ?? '',
    pix_scope: pix.scope ?? '',
    pix_tokenUrl: pix.tokenUrl ?? '',
    pix_baseUrl: pix.baseUrl ?? '',
    charges_client_id: charges.client_id ?? '',
    charges_client_secret: charges.client_secret ?? '',
    charges_scope: charges.scope ?? '',
    charges_tokenUrl: charges.tokenUrl ?? '',
    charges_baseUrl: charges.baseUrl ?? '',
  };
}

// ── Certificate upload modal ──────────────────────────────────────────────────

function CertUploadModal({
  open,
  config,
  tenantId,
  bankAccountId,
  onClose,
  onSuccess,
}: {
  open: boolean;
  config: BankConfigurationResponse | null;
  tenantId: number;
  bankAccountId: number;
  onClose: () => void;
  onSuccess: (res: CertificateUploadResponse) => void;
}) {
  const { message } = App.useApp();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [password, setPassword] = useState('');
  const [uploading, setUploading] = useState(false);

  const handleUpload = async () => {
    if (!config || fileList.length === 0) {
      message.warning('Selecione um arquivo de certificado (.p12 / .pfx)');
      return;
    }
    const raw = fileList[0].originFileObj;
    if (!raw) return;
    setUploading(true);
    try {
      const res = await FinanceApi.uploadCertificate(
        tenantId,
        bankAccountId,
        config.id,
        raw,
        password || undefined
      );
      message.success('Certificado enviado com sucesso!');
      onSuccess(res);
      setFileList([]);
      setPassword('');
      onClose();
    } catch (err: any) {
      message.error(err?.message || 'Erro ao enviar certificado');
    } finally {
      setUploading(false);
    }
  };

  return (
    <Modal
      title={
        <Space>
          <SafetyCertificateOutlined style={{ color: '#1677ff' }} />
          Enviar Certificado mTLS
        </Space>
      }
      open={open}
      onCancel={() => {
        if (!uploading) {
          setFileList([]);
          setPassword('');
          onClose();
        }
      }}
      footer={[
        <Button key="cancel" onClick={onClose} disabled={uploading}>
          Cancelar
        </Button>,
        <Button
          key="upload"
          type="primary"
          icon={<CloudUploadOutlined />}
          loading={uploading}
          disabled={fileList.length === 0}
          onClick={handleUpload}
        >
          Enviar
        </Button>,
      ]}
    >
      <Space direction="vertical" style={{ width: '100%', marginTop: 8 }}>
        <Text type="secondary" style={{ fontSize: 13 }}>
          O certificado é obrigatório para operações PIX com a EFI (Efí Pay). Utilize o arquivo{' '}
          <strong>.p12</strong> ou <strong>.pfx</strong> gerado no portal da EFI.
        </Text>

        {config?.certificatePathMasked && (
          <Tag icon={<CheckCircleOutlined />} color="success" style={{ marginBottom: 4 }}>
            Certificado atual: {config.certificatePathMasked}
          </Tag>
        )}

        <Upload
          accept=".p12,.pfx,.pem"
          maxCount={1}
          fileList={fileList}
          beforeUpload={(file) => {
            setFileList([{ ...file, originFileObj: file, uid: file.uid, name: file.name, status: 'done' }]);
            return false; // prevent auto-upload
          }}
          onRemove={() => setFileList([])}
        >
          <Button icon={<FileProtectOutlined />}>Selecionar arquivo (.p12 / .pfx)</Button>
        </Upload>

        <Input.Password
          placeholder="Senha do certificado (opcional)"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </Space>
    </Modal>
  );
}

// ── Config form modal ─────────────────────────────────────────────────────────

function ConfigModal({
  open,
  editing,
  existingEnvironments,
  tenantId,
  bankAccountId,
  onClose,
  onSuccess,
}: {
  open: boolean;
  editing: BankConfigurationResponse | null;
  existingEnvironments: Environment[];
  tenantId: number;
  bankAccountId: number;
  onClose: () => void;
  onSuccess: (cfg: BankConfigurationResponse) => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<EfiFormValues>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      if (editing) {
        form.setFieldsValue(extraConfigToFormValues(editing));
      } else {
        form.resetFields();
        form.setFieldsValue({ isActive: true, environment: Environment.SANDBOX });
      }
    }
  }, [open, editing, form]);

  const handleSubmit = async (values: EfiFormValues) => {
    setSubmitting(true);
    try {
      const extraConfig = formValuesToExtraConfig(values);
      if (editing) {
        const res = await FinanceApi.updateBankConfiguration(
          tenantId,
          bankAccountId,
          editing.id,
          { webhookUrl: values.webhookUrl, isActive: values.isActive, extraConfig }
        );
        message.success('Configuração atualizada');
        onSuccess(res);
      } else {
        const req: BankConfigurationRequest = {
          environment: values.environment,
          webhookUrl: values.webhookUrl,
          isActive: values.isActive,
          extraConfig,
        };
        const res = await FinanceApi.createBankConfiguration(tenantId, bankAccountId, req);
        message.success('Configuração criada');
        onSuccess(res);
      }
      onClose();
    } catch (err: any) {
      message.error(err?.message || 'Erro ao salvar configuração');
    } finally {
      setSubmitting(false);
    }
  };

  const availableEnvs = editing
    ? [editing.environment]
    : ([Environment.SANDBOX, Environment.PRODUCTION] as Environment[]).filter(
        (e) => !existingEnvironments.includes(e)
      );

  return (
    <Modal
      title={editing ? 'Editar Configuração EFI' : 'Nova Configuração EFI'}
      open={open}
      width={640}
      onCancel={() => !submitting && onClose()}
      onOk={() => form.submit()}
      okText={editing ? 'Salvar' : 'Criar'}
      cancelText="Cancelar"
      confirmLoading={submitting}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ marginTop: 8 }}>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="environment" label="Ambiente" rules={[{ required: true }]}>
              <Select disabled={!!editing}>
                {availableEnvs.map((e) => (
                  <Option key={e} value={e}>
                    {e === Environment.SANDBOX ? 'Sandbox (homologação)' : 'Produção'}
                  </Option>
                ))}
              </Select>
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="isActive" label="Ativo" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="webhookUrl" label="URL de Webhook (PIX recebido)">
          <Input placeholder="https://meu-site.com/webhooks/pix" />
        </Form.Item>

        <Divider orientation="left" style={{ fontSize: 13 }}>
          PIX — Credenciais
        </Divider>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="pix_client_id"
              label="Client ID (PIX)"
              rules={[{ required: true, message: 'Obrigatório' }]}
            >
              <Input />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="pix_client_secret"
              label="Client Secret (PIX)"
              rules={[{ required: true, message: 'Obrigatório' }]}
            >
              <Input.Password />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="pix_pixKey" label="Chave PIX padrão">
              <Input placeholder="CPF, CNPJ, e-mail, telefone ou aleatória" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="pix_scope" label="Scope (opcional)">
              <Input placeholder="cob.write cob.read..." />
            </Form.Item>
          </Col>
        </Row>

        <Divider orientation="left" style={{ fontSize: 13 }}>
          Boleto (Cobranças) — Credenciais
        </Divider>
        <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 12 }}>
          Deixe em branco para usar as mesmas credenciais do PIX.
        </Text>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="charges_client_id" label="Client ID (Boleto)">
              <Input />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="charges_client_secret" label="Client Secret (Boleto)">
              <Input.Password />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function BankConfigPage() {
  const params = useParams<{ id: string }>();
  const bankAccountId = Number(params.id);
  const router = useRouter();
  const { user } = useAuth();
  const { message } = App.useApp();

  const [account, setAccount] = useState<BankAccountResponse | null>(null);
  const [configs, setConfigs] = useState<BankConfigurationResponse[]>([]);
  const [loading, setLoading] = useState(true);

  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [editingConfig, setEditingConfig] = useState<BankConfigurationResponse | null>(null);

  const [certModalOpen, setCertModalOpen] = useState(false);
  const [certTargetConfig, setCertTargetConfig] = useState<BankConfigurationResponse | null>(null);

  useEffect(() => {
    if (!user?.tenantId || !bankAccountId) return;
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.tenantId, bankAccountId]);

  const loadData = async () => {
    if (!user?.tenantId) return;
    setLoading(true);
    try {
      const [accounts, cfgs] = await Promise.all([
        FinanceApi.listBankAccounts(user.tenantId),
        FinanceApi.listBankConfigurations(user.tenantId, bankAccountId),
      ]);
      const acc = accounts.find((a) => a.id === bankAccountId) ?? null;
      setAccount(acc);
      setConfigs(cfgs);
    } catch (err: any) {
      message.error(err?.message || 'Erro ao carregar dados');
    } finally {
      setLoading(false);
    }
  };

  const openCreate = () => {
    setEditingConfig(null);
    setConfigModalOpen(true);
  };

  const openEdit = (cfg: BankConfigurationResponse) => {
    setEditingConfig(cfg);
    setConfigModalOpen(true);
  };

  const openCertUpload = (cfg: BankConfigurationResponse) => {
    setCertTargetConfig(cfg);
    setCertModalOpen(true);
  };

  const handleConfigSuccess = (cfg: BankConfigurationResponse) => {
    setConfigs((prev) => {
      const idx = prev.findIndex((c) => c.id === cfg.id);
      if (idx >= 0) {
        const copy = [...prev];
        copy[idx] = cfg;
        return copy;
      }
      return [...prev, cfg];
    });
  };

  const handleCertSuccess = (res: CertificateUploadResponse) => {
    setConfigs((prev) =>
      prev.map((c) =>
        c.id === res.configurationId ? { ...c, certificatePathMasked: res.maskedPath } : c
      )
    );
  };

  const existingEnvironments = configs.map((c) => c.environment);
  const canAddMore = existingEnvironments.length < 2;

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 64 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* Breadcrumb */}
      <Breadcrumb
        items={[
          { title: <Link href="/dashboard">Dashboard</Link> },
          { title: <Link href="/dashboard/bank-accounts">Contas Bancárias</Link> },
          { title: account?.name ?? `Conta #${bankAccountId}` },
          { title: 'Configurações EFI' },
        ]}
      />

      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <Title level={3} style={{ marginBottom: 4 }}>
            Configurações EFI — {account?.name ?? `Conta #${bankAccountId}`}
          </Title>
          <Text type="secondary">
            Gerencie credenciais, ambiente e certificado mTLS para integração com a Efí Pay.
          </Text>
        </div>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => router.push('/dashboard/bank-accounts')}>
            Voltar
          </Button>
          {canAddMore && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              Nova Configuração
            </Button>
          )}
        </Space>
      </div>

      {/* Config cards */}
      {configs.length === 0 ? (
        <Card style={{ textAlign: 'center', padding: '32px 0' }}>
          <SafetyCertificateOutlined style={{ fontSize: 40, color: '#bfbfbf', marginBottom: 16 }} />
          <div>
            <Text type="secondary">Nenhuma configuração criada ainda.</Text>
          </div>
          <Button type="primary" icon={<PlusOutlined />} style={{ marginTop: 16 }} onClick={openCreate}>
            Criar Configuração
          </Button>
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          {configs.map((cfg) => (
            <Col xs={24} lg={12} key={cfg.id}>
              <Card
                title={
                  <Space>
                    <Badge
                      status={cfg.isActive ? 'success' : 'default'}
                      text={cfg.environment === Environment.PRODUCTION ? 'Produção' : 'Sandbox'}
                    />
                  </Space>
                }
                extra={
                  <Button type="text" icon={<EditOutlined />} onClick={() => openEdit(cfg)}>
                    Editar
                  </Button>
                }
                style={{ height: '100%' }}
              >
                <Descriptions column={1} size="small">
                  <Descriptions.Item label="Status">
                    <Tag color={cfg.isActive ? 'success' : 'default'}>
                      {cfg.isActive ? 'Ativo' : 'Inativo'}
                    </Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="Webhook">
                    <Text copyable={!!cfg.webhookUrl} style={{ fontSize: 12 }}>
                      {cfg.webhookUrl || <Text type="secondary">não configurado</Text>}
                    </Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="PIX Client ID">
                    <Text style={{ fontSize: 12 }}>
                      {(cfg.extraConfig?.pix as any)?.client_id
                        ? String((cfg.extraConfig.pix as any).client_id).slice(0, 8) + '••••••••'
                        : <Text type="secondary">não configurado</Text>}
                    </Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="Chave PIX">
                    <Text style={{ fontSize: 12 }}>
                      {(cfg.extraConfig?.pix as any)?.pixKey || (
                        <Text type="secondary">não configurada</Text>
                      )}
                    </Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="Certificado mTLS">
                    {cfg.certificatePathMasked ? (
                      <Tag icon={<CheckCircleOutlined />} color="success" style={{ fontSize: 11 }}>
                        {cfg.certificatePathMasked}
                      </Tag>
                    ) : (
                      <Tag color="warning" style={{ fontSize: 11 }}>
                        Nenhum certificado
                      </Tag>
                    )}
                  </Descriptions.Item>
                </Descriptions>

                <Divider style={{ margin: '12px 0' }} />

                <Button
                  block
                  icon={<SafetyCertificateOutlined />}
                  onClick={() => openCertUpload(cfg)}
                >
                  {cfg.certificatePathMasked ? 'Substituir Certificado' : 'Enviar Certificado'}
                </Button>
              </Card>
            </Col>
          ))}
        </Row>
      )}

      {/* Modals */}
      <ConfigModal
        open={configModalOpen}
        editing={editingConfig}
        existingEnvironments={existingEnvironments}
        tenantId={user?.tenantId ?? 0}
        bankAccountId={bankAccountId}
        onClose={() => setConfigModalOpen(false)}
        onSuccess={handleConfigSuccess}
      />

      <CertUploadModal
        open={certModalOpen}
        config={certTargetConfig}
        tenantId={user?.tenantId ?? 0}
        bankAccountId={bankAccountId}
        onClose={() => setCertModalOpen(false)}
        onSuccess={handleCertSuccess}
      />
    </div>
  );
}
