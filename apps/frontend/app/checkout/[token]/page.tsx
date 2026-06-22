'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Divider,
  Form,
  Input,
  Result,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  CheckCircleFilled,
  CopyOutlined,
  LoadingOutlined,
  QrcodeOutlined,
} from '@ant-design/icons';
import { CheckoutApi, CheckoutInfo, CheckoutPayRequest } from '@/lib/api/checkout';

const { Title, Text, Paragraph } = Typography;

type Props = {
  params: Promise<{ token: string }>;
};

const INSTRUMENT_LABELS: Record<string, string> = {
  PIX_IMMEDIATE: 'PIX (pagamento na hora)',
  PIX_DUE: 'PIX com vencimento',
  BOLETO: 'Boleto bancário',
};

const isPixInstrument = (instrument?: string) =>
  !!instrument && instrument.toUpperCase().startsWith('PIX');

export default function CheckoutPage({ params }: Props) {
  const [token, setToken] = useState<string>('');
  const [info, setInfo] = useState<CheckoutInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);
  const [lookingUpClient, setLookingUpClient] = useState(false);
  const [identifying, setIdentifying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form] = Form.useForm<CheckoutPayRequest>();
  const watchedInstrument = Form.useWatch('instrument', form);

  useEffect(() => {
    (async () => {
      const resolved = await params;
      setToken(resolved.token);
      try {
        const data = await CheckoutApi.getInfo(resolved.token);
        setInfo(data);
      } catch (e: any) {
        setError(e?.message || 'Não foi possível carregar este checkout.');
      } finally {
        setLoading(false);
      }
    })();
  }, [params]);

  const allowedInstruments = useMemo(() => info?.allowedInstruments ?? [], [info]);
  const defaultInstrument = useMemo(
    () => allowedInstruments[0] || 'PIX_IMMEDIATE',
    [allowedInstruments]
  );
  const selectedInstrument = watchedInstrument || defaultInstrument;

  const paymentResult = info?.paymentResult;
  const pixCopiaECola = paymentResult?.pixCopiaECola;
  const hasPix = !!pixCopiaECola;

  const pixQrSrc = useMemo(() => {
    if (paymentResult?.pixQrCodeImage) {
      return `data:image/png;base64,${paymentResult.pixQrCodeImage}`;
    }
    if (pixCopiaECola) {
      return `https://api.qrserver.com/v1/create-qr-code/?size=260x260&data=${encodeURIComponent(
        pixCopiaECola
      )}`;
    }
    return null;
  }, [paymentResult?.pixQrCodeImage, pixCopiaECola]);

  const isPaid = info?.status === 'PAID';
  const isAwaiting = info?.status === 'PROCESSING' || info?.status === 'PENDING';
  const isClosed = info?.status === 'EXPIRED' || info?.status === 'CANCELED';
  const canPay = info?.status === 'CHECKOUT_OPEN' || info?.status === 'FAILED';

  const requiresDocumentValidation =
    info?.checkoutAccessMode === 'CLIENT_DOCUMENT' && !info?.identityVerified;

  const formatAmount = (value?: string | number) =>
    `R$ ${Number(value || 0)
      .toFixed(2)
      .replace('.', ',')}`;

  // Polling enquanto o pagamento aguarda confirmação do banco.
  useEffect(() => {
    if (!token || !isAwaiting) return;

    const intervalId = window.setInterval(async () => {
      try {
        const updated = await CheckoutApi.getStatus(token);
        setInfo(updated);
        if (
          updated.status === 'PAID' ||
          updated.status === 'FAILED' ||
          updated.status === 'EXPIRED' ||
          updated.status === 'CANCELED'
        ) {
          if (updated.status === 'PAID') message.success('Pagamento confirmado!');
          window.clearInterval(intervalId);
        }
      } catch {
        // mantém o polling silencioso
      }
    }, 3000);

    return () => window.clearInterval(intervalId);
  }, [token, isAwaiting]);

  const copy = useCallback(async (text?: string) => {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      message.success('Código PIX copiado');
    } catch {
      message.error('Não foi possível copiar automaticamente. Copie manualmente.');
    }
  }, []);

  const onLookupClient = async () => {
    const document = form.getFieldValue('payerDocument');
    if (!token || !document) {
      message.warning('Informe o CPF/CNPJ para buscar seus dados');
      return;
    }
    setLookingUpClient(true);
    try {
      const result = await CheckoutApi.lookupClient(token, document);
      if (!result.found) {
        message.info('Nenhum cadastro encontrado para este documento');
        return;
      }
      form.setFieldsValue({
        payerName: result.name,
        payerEmail: result.email,
        payerPhone: result.phone,
      });
      message.success('Dados preenchidos com sucesso');
    } catch (e: any) {
      message.error(e?.message || 'Não foi possível buscar seus dados');
    } finally {
      setLookingUpClient(false);
    }
  };

  const onIdentify = async () => {
    if (!token) return;
    const document = form.getFieldValue('payerDocument');
    if (!document) {
      message.warning('Informe o CPF/CNPJ para validar sua identidade');
      return;
    }
    setIdentifying(true);
    try {
      const updated = await CheckoutApi.identify(token, { document });
      setInfo(updated);
      message.success('Identidade validada com sucesso');
    } catch (e: any) {
      message.error(e?.message || 'Falha ao validar identidade');
    } finally {
      setIdentifying(false);
    }
  };

  const onPay = async () => {
    if (!token) return;
    try {
      await form.validateFields();
    } catch {
      return;
    }
    if (requiresDocumentValidation) {
      message.warning('Valide sua identidade antes de pagar');
      return;
    }

    const values = form.getFieldsValue();
    const payload: CheckoutPayRequest = {
      instrument: values.instrument || defaultInstrument,
      payerName: values.payerName,
      payerDocument: values.payerDocument,
      payerEmail: values.payerEmail,
      payerPhone: values.payerPhone,
      pixKey: values.pixKey,
    };

    setPaying(true);
    setError(null);
    try {
      const result = await CheckoutApi.pay(token, payload);
      setInfo(result);
      if (result.status === 'FAILED') {
        message.error('Não foi possível processar o pagamento. Revise os dados e tente novamente.');
      } else if (result.status === 'PAID') {
        message.success('Pagamento confirmado!');
      } else if (isPixInstrument(payload.instrument)) {
        message.success('PIX gerado! Escaneie o QR Code ou copie o código para pagar.');
      } else {
        message.info('Pagamento iniciado. Aguardando confirmação...');
      }
    } catch (e: any) {
      setError(e?.message || 'Falha ao processar pagamento');
    } finally {
      setPaying(false);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-xl p-4 md:p-8">
      <Card styles={{ body: { padding: 24 } }}>
        {error && <Alert type="error" message={error} showIcon className="!mb-4" />}

        {!info ? (
          <Alert
            type="error"
            showIcon
            message="Checkout indisponível"
            description="Não foi possível carregar este checkout."
          />
        ) : (
          <Space direction="vertical" size="large" className="w-full">
            {/* Cabeçalho com valor */}
            <div className="text-center">
              <Text type="secondary">Total a pagar</Text>
              <div style={{ fontSize: 38, fontWeight: 800, lineHeight: 1.1, color: '#111' }}>
                {formatAmount(info.amount)}
              </div>
              {info.description && (
                <Text type="secondary" style={{ display: 'block', marginTop: 4 }}>
                  {info.description}
                </Text>
              )}
              {info.clientName && (
                <Tag color="blue" style={{ marginTop: 10 }}>
                  {info.clientName}
                  {info.clientDocumentMasked ? ` · doc. ${info.clientDocumentMasked}` : ''}
                </Tag>
              )}
            </div>

            {/* PAGO */}
            {isPaid && (
              <Result
                status="success"
                title="Pagamento confirmado"
                subTitle="Recebemos a confirmação do seu pagamento."
              >
                {info.paymentResult && (
                  <div className="text-left">
                    {info.paymentResult.billetLink && (
                      <Paragraph>
                        <a href={info.paymentResult.billetLink} target="_blank" rel="noreferrer">
                          Abrir comprovante / boleto
                        </a>
                      </Paragraph>
                    )}
                    {info.paymentResult.pdfLink && (
                      <Paragraph>
                        <a href={info.paymentResult.pdfLink} target="_blank" rel="noreferrer">
                          Abrir PDF
                        </a>
                      </Paragraph>
                    )}
                  </div>
                )}
              </Result>
            )}

            {/* ENCERRADO */}
            {isClosed && (
              <Alert
                showIcon
                type="warning"
                message={info.status === 'EXPIRED' ? 'Este link expirou' : 'Este link foi cancelado'}
                description="Solicite um novo link de cobrança ao estabelecimento."
              />
            )}

            {/* PIX GERADO — QR + copia e cola */}
            {!isPaid && !isClosed && hasPix && (
              <div className="text-center">
                <Space align="center" size="small" style={{ marginBottom: 8 }}>
                  <QrcodeOutlined style={{ fontSize: 18, color: '#1677ff' }} />
                  <Title level={5} className="!mb-0">
                    Pague com PIX
                  </Title>
                </Space>
                <Paragraph type="secondary" className="!mb-3">
                  Abra o app do seu banco, escolha pagar com PIX e escaneie o QR Code abaixo.
                </Paragraph>

                {pixQrSrc && (
                  <div
                    style={{
                      display: 'inline-block',
                      padding: 12,
                      background: '#fff',
                      border: '1px solid #f0f0f0',
                      borderRadius: 12,
                    }}
                  >
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={pixQrSrc} alt="QR Code PIX" width={240} height={240} />
                  </div>
                )}

                <Divider plain style={{ margin: '20px 0 12px' }}>
                  ou copie o código
                </Divider>

                <Input.TextArea
                  readOnly
                  value={pixCopiaECola}
                  autoSize={{ minRows: 3, maxRows: 4 }}
                  onFocus={(e) => e.target.select()}
                  style={{ fontSize: 12, fontFamily: 'monospace' }}
                />
                <Button
                  type="primary"
                  icon={<CopyOutlined />}
                  block
                  size="large"
                  style={{ marginTop: 12 }}
                  onClick={() => copy(pixCopiaECola)}
                >
                  Copiar código PIX
                </Button>

                <div
                  style={{
                    marginTop: 16,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 8,
                    color: '#888',
                  }}
                >
                  {isAwaiting ? (
                    <>
                      <Spin indicator={<LoadingOutlined spin />} size="small" />
                      <Text type="secondary">Aguardando confirmação do pagamento…</Text>
                    </>
                  ) : (
                    <>
                      <CheckCircleFilled style={{ color: '#52c41a' }} />
                      <Text type="secondary">
                        Atualizamos esta tela assim que o pagamento for confirmado.
                      </Text>
                    </>
                  )}
                </div>
              </div>
            )}

            {/* AGUARDANDO (não-PIX, ex.: boleto/cartão) */}
            {!isPaid && !isClosed && !hasPix && isAwaiting && (
              <Alert
                showIcon
                type="info"
                message="Aguardando confirmação do pagamento"
                description="Assim que o banco confirmar, esta tela será atualizada automaticamente."
              />
            )}

            {/* FORMULÁRIO DE PAGAMENTO */}
            {canPay && !hasPix && (
              <>
                {info.checkoutAccessMode &&
                  info.checkoutAccessMode !== 'PUBLIC' &&
                  !info.identityVerified && (
                    <Alert
                      showIcon
                      type="info"
                      message="Validação de identidade necessária"
                      description="Informe o documento do cliente vinculado e valide antes de pagar."
                    />
                  )}

                {info.status === 'FAILED' && (
                  <Alert
                    showIcon
                    type="error"
                    message="Não foi possível processar o pagamento anterior"
                    description="Revise os dados e tente novamente."
                  />
                )}

                <Form
                  form={form}
                  layout="vertical"
                  requiredMark={false}
                  initialValues={{ instrument: defaultInstrument }}
                  onValuesChange={() => error && setError(null)}
                >
                  {allowedInstruments.length > 1 ? (
                    <Form.Item
                      label="Forma de pagamento"
                      name="instrument"
                      rules={[{ required: true, message: 'Selecione um método' }]}
                    >
                      <Select
                        options={allowedInstruments.map((m) => ({
                          value: m,
                          label: INSTRUMENT_LABELS[m] ?? m,
                        }))}
                      />
                    </Form.Item>
                  ) : (
                    <Form.Item name="instrument" hidden>
                      <Input />
                    </Form.Item>
                  )}

                  <Form.Item
                    label="CPF/CNPJ"
                    name="payerDocument"
                    rules={[{ required: true, message: 'Informe CPF ou CNPJ' }]}
                  >
                    <Input
                      placeholder="Somente números"
                      addonAfter={
                        <a onClick={onLookupClient}>
                          {lookingUpClient ? 'Buscando…' : 'Buscar dados'}
                        </a>
                      }
                    />
                  </Form.Item>

                  {requiresDocumentValidation && (
                    <Button
                      onClick={onIdentify}
                      loading={identifying}
                      block
                      style={{ marginBottom: 16 }}
                    >
                      Validar identidade
                    </Button>
                  )}

                  <Form.Item
                    label="Nome"
                    name="payerName"
                    rules={[{ required: true, message: 'Informe o nome' }]}
                  >
                    <Input placeholder="Nome completo" />
                  </Form.Item>

                  <Form.Item label="E-mail (opcional)" name="payerEmail">
                    <Input placeholder="email@exemplo.com" />
                  </Form.Item>

                  <Form.Item label="Telefone (opcional)" name="payerPhone">
                    <Input placeholder="(00) 00000-0000" />
                  </Form.Item>

                  {(allowedInstruments.includes('PIX_IMMEDIATE') ||
                    allowedInstruments.includes('PIX_DUE')) && (
                    <Form.Item
                      label="Sua chave PIX (opcional)"
                      name="pixKey"
                      extra="Informe apenas se quiser usar uma chave específica."
                    >
                      <Input placeholder="CPF, e-mail, telefone ou chave aleatória" />
                    </Form.Item>
                  )}

                  <Button type="primary" size="large" block loading={paying} onClick={onPay}>
                    {isPixInstrument(selectedInstrument) ? 'Gerar PIX' : 'Pagar'}
                  </Button>
                </Form>

                {info.status === 'CHECKOUT_OPEN' && info.expiresAt && (
                  <Text
                    type="secondary"
                    style={{ display: 'block', textAlign: 'center', fontSize: 12 }}
                  >
                    Link válido até {new Date(info.expiresAt).toLocaleString('pt-BR')}
                  </Text>
                )}
              </>
            )}
          </Space>
        )}
      </Card>
    </div>
  );
}
