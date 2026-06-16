'use client';

import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Radio, Space, Spin, Steps, Typography, message } from 'antd';
import { CheckoutApi, CheckoutInfo, CheckoutPayRequest } from '@/lib/api/checkout';

const { Title, Text } = Typography;

type Props = {
  params: Promise<{ token: string }>;
};

export default function CheckoutPage({ params }: Props) {
  const [token, setToken] = useState<string>('');
  const [info, setInfo] = useState<CheckoutInfo | null>(null);
  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);
  const [lookingUpClient, setLookingUpClient] = useState(false);
  const [identifying, setIdentifying] = useState(false);
  const [waitingConfirmation, setWaitingConfirmation] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form] = Form.useForm<CheckoutPayRequest>();
  const watchedInstrument = Form.useWatch('instrument', form);

  const isCreditCardInstrument = (instrument?: string) => {
    if (!instrument) return false;
    const normalized = instrument.toUpperCase();
    return normalized.includes('CARD') || normalized.includes('CREDIT');
  };

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

  const defaultInstrument = useMemo(
    () => info?.allowedInstruments?.[0] || 'PIX_IMMEDIATE',
    [info]
  );

  const currentInstrument = useMemo(
    () => info?.allowedInstruments?.[0] || watchedInstrument || defaultInstrument,
    [defaultInstrument, info?.allowedInstruments, watchedInstrument]
  );

  useEffect(() => {
    if (!info) return;

    if (info.status === 'PAID') {
      setStep(3);
      return;
    }

    if (info.status === 'PROCESSING' || info.status === 'PENDING') {
      setStep(2);
      return;
    }

    if (info.status === 'CHECKOUT_OPEN') {
      setStep((prev) => (prev > 1 ? 1 : prev));
      return;
    }

    if (info.status === 'FAILED') {
      setStep((prev) => (prev > 1 ? 1 : prev));
    }
  }, [info]);

  const requiresDocumentValidation =
    info?.checkoutAccessMode === 'CLIENT_DOCUMENT' && !info?.identityVerified;

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

  const onNextToSummary = async () => {
    try {
      await form.validateFields(['instrument', 'payerName', 'payerDocument']);
      if (requiresDocumentValidation) {
        message.warning('Valide sua identidade antes de continuar');
        return;
      }
      setStep(1);
    } catch {
      // validação visual do formulário
    }
  };

  const onPay = async (values: any) => {
    if (!token) return;
    setPaying(true);
    setError(null);

    const payload: CheckoutPayRequest = {
      instrument: values.instrument,
      payerName: values.payerName,
      payerDocument: values.payerDocument,
      payerEmail: values.payerEmail,
      payerPhone: values.payerPhone,
      pixKey: values.pixKey,
    };

    try {
      const result = await CheckoutApi.pay(token, payload);
      setInfo(result);

      if (result.status === 'PROCESSING' || result.status === 'PENDING') {
        setStep(2);
        setWaitingConfirmation(isCreditCardInstrument(values.instrument));
        message.info('Pagamento iniciado. Aguardando confirmação...');
      } else {
        if (result.status === 'PAID') {
          setStep(3);
          message.success('Pagamento processado com sucesso');
        } else if (result.status === 'FAILED') {
          setStep(1);
          message.error('Não foi possível processar o pagamento. Revise os dados e tente novamente.');
        } else if (result.status === 'EXPIRED' || result.status === 'CANCELED') {
          message.warning('Este checkout não está mais disponível para pagamento.');
        }
        setWaitingConfirmation(false);
      }
    } catch (e: any) {
      setError(e?.message || 'Falha ao processar pagamento');
    } finally {
      setPaying(false);
    }
  };

  useEffect(() => {
    if (!token) return;
    if (!(info?.status === 'PROCESSING' || info?.status === 'PENDING')) {
      setWaitingConfirmation(false);
      return;
    }

    setWaitingConfirmation(isCreditCardInstrument(currentInstrument));

    const intervalId = window.setInterval(async () => {
      try {
        const updated = await CheckoutApi.getStatus(token);
        setInfo(updated);

        if (updated.status === 'PAID') {
          setStep(3);
          setWaitingConfirmation(false);
          message.success('Pagamento confirmado!');
          window.clearInterval(intervalId);
        }

        if (updated.status === 'FAILED' || updated.status === 'EXPIRED' || updated.status === 'CANCELED') {
          setWaitingConfirmation(false);
          window.clearInterval(intervalId);
        }
      } catch {
        // Mantém polling silencioso para não poluir a experiência do usuário.
      }
    }, 3000);

    return () => window.clearInterval(intervalId);
  }, [token, info?.status, currentInstrument]);

  const pixCopiaECola = info?.paymentResult?.pixCopiaECola;
  const pixQrUrl = pixCopiaECola
    ? `https://api.qrserver.com/v1/create-qr-code/?size=280x280&data=${encodeURIComponent(pixCopiaECola)}`
    : null;

  const copy = async (text?: string) => {
    if (!text) return;
    await navigator.clipboard.writeText(text);
    message.success('Copiado');
  };

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

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl p-4 md:p-8">
      <Card>
        <Space direction="vertical" size="middle" className="w-full">
          <Title level={3} className="!mb-0">Checkout</Title>

          <Steps
            size="small"
            current={step}
            items={[
              { title: 'Dados' },
              { title: 'Resumo' },
              { title: 'Aguardando' },
              { title: 'Concluído' },
            ]}
          />

          {error && <Alert type="error" message={error} showIcon />}

          {info && (
            <>
              <div style={{ background: '#fafafa', borderRadius: 8, padding: '12px 16px' }}>
                <div style={{ fontSize: 28, fontWeight: 700, color: '#1a1a1a' }}>
                  R$ {Number(info.amount || 0).toFixed(2).replace('.', ',')}
                </div>
                {info.description && (
                  <div style={{ color: '#555', marginTop: 4 }}>{info.description}</div>
                )}
                {info.status === 'CHECKOUT_OPEN' && info.expiresAt && (
                  <div style={{ color: '#888', fontSize: 12, marginTop: 4 }}>
                    Link válido até {new Date(info.expiresAt).toLocaleString('pt-BR')}
                  </div>
                )}
              </div>

              {info.clientName && (
                <Alert
                  showIcon
                  type="info"
                  message={`Esta cobrança está vinculada a ${info.clientName}${info.clientDocumentMasked ? ` (doc. terminado em ${info.clientDocumentMasked})` : ''}`}
                />
              )}

              {info.checkoutAccessMode && info.checkoutAccessMode !== 'PUBLIC' && (
                <Alert
                  showIcon
                  type="info"
                  message="Checkout com validação de identidade"
                  description={
                    info.identityVerified
                      ? 'Identidade já validada para esta sessão.'
                      : 'Antes de pagar, valide o documento do cliente vinculado.'
                  }
                />
              )}

              {(info.status === 'PROCESSING' || info.status === 'PENDING') && (
                <Alert
                  showIcon
                  type="info"
                  message="Aguardando confirmação do pagamento"
                  description="Assim que o banco confirmar, esta tela será atualizada automaticamente."
                />
              )}

              {pixCopiaECola && (
                <Card type="inner" title="Pague com PIX">
                  <Space direction="vertical" className="w-full" size="middle">
                    <Text>Escaneie o QR Code no app do seu banco:</Text>
                    {pixQrUrl && (
                      <div className="flex justify-center">
                        <img src={pixQrUrl} alt="QR Code PIX" width={280} height={280} />
                      </div>
                    )}

                    <Text>Ou copie o código PIX:</Text>
                    <Input.TextArea readOnly rows={4} value={pixCopiaECola} />
                    <Button onClick={() => copy(pixCopiaECola)}>Copiar PIX Copia e Cola</Button>
                  </Space>
                </Card>
              )}

              {info.status === 'PAID' && info.paymentResult && (
                <Card type="inner" title="Pagamento realizado">
                  <Space direction="vertical" className="w-full">
                    <Text>ID: {info.paymentResult.id}</Text>
                    <Text>Status: {info.paymentResult.status}</Text>
                    {info.paymentResult.barcode && <Text>Código de barras: {info.paymentResult.barcode}</Text>}
                    {info.paymentResult.billetLink && (
                      <a href={info.paymentResult.billetLink} target="_blank" rel="noreferrer">Abrir boleto</a>
                    )}
                    {info.paymentResult.pdfLink && (
                      <a href={info.paymentResult.pdfLink} target="_blank" rel="noreferrer">Abrir PDF</a>
                    )}
                  </Space>
                </Card>
              )}

              {(info.status === 'EXPIRED' || info.status === 'CANCELED') && (
                <Alert
                  showIcon
                  type="warning"
                  message={info.status === 'EXPIRED' ? 'Este link expirou' : 'Este link foi cancelado'}
                />
              )}

              {info.status === 'FAILED' && (
                <Alert
                  showIcon
                  type="error"
                  message="Falha ao processar pagamento"
                  description="Revise os dados informados e tente novamente."
                />
              )}

              {(info.status === 'CHECKOUT_OPEN' || info.status === 'FAILED') && step === 0 && (
                <Form
                  form={form}
                  layout="vertical"
                  onValuesChange={() => {
                    if (error) {
                      setError(null);
                    }
                  }}
                  initialValues={{ instrument: defaultInstrument }}
                >
                  <Form.Item
                    label="Método de pagamento"
                    name="instrument"
                    rules={[{ required: true, message: 'Selecione um método' }]}
                  >
                    <Radio.Group>
                      <Space direction="vertical">
                        {info.allowedInstruments.map((method) => (
                          <Radio key={method} value={method}>
                            {method === 'PIX_IMMEDIATE' ? '⚡ PIX (pagamento na hora)'
                              : method === 'PIX_DUE' ? '📅 PIX com vencimento'
                              : method === 'BOLETO' ? '🏦 Boleto bancário'
                              : method}
                          </Radio>
                        ))}
                      </Space>
                    </Radio.Group>
                  </Form.Item>

                  <Form.Item
                    label="Nome"
                    name="payerName"
                    rules={[{ required: true, message: 'Informe o nome' }]}
                  >
                    <Input />
                  </Form.Item>

                  <Form.Item
                    label="CPF/CNPJ"
                    name="payerDocument"
                    rules={[{ required: true, message: 'Informe CPF ou CNPJ' }]}
                  >
                    <Input />
                  </Form.Item>

                  <Button onClick={onLookupClient} loading={lookingUpClient} style={{ marginBottom: 16 }}>
                    Buscar meus dados
                  </Button>

                  {requiresDocumentValidation && (
                    <Button onClick={onIdentify} loading={identifying} style={{ marginBottom: 16 }}>
                      Validar identidade
                    </Button>
                  )}

                  <Form.Item label="E-mail (opcional)" name="payerEmail">
                    <Input />
                  </Form.Item>

                  <Form.Item label="Telefone (opcional)" name="payerPhone">
                    <Input />
                  </Form.Item>

                  {(info.allowedInstruments.includes('PIX_IMMEDIATE') || info.allowedInstruments.includes('PIX_DUE')) && (
                    <Form.Item
                      label="Sua chave PIX (opcional)"
                      name="pixKey"
                      extra="Informe se quiser usar uma chave PIX específica."
                    >
                      <Input placeholder="CPF, email, telefone ou chave aleatoria" />
                    </Form.Item>
                  )}

                  <Button type="primary" onClick={onNextToSummary}>
                    Continuar
                  </Button>
                </Form>
              )}

              {(info.status === 'CHECKOUT_OPEN' || info.status === 'FAILED') && step === 1 && (
                <Card type="inner" title="Resumo da cobrança">
                  <Space direction="vertical" className="w-full">
                    <Text><strong>Nome:</strong> {form.getFieldValue('payerName') || '-'}</Text>
                    <Text><strong>Documento:</strong> {form.getFieldValue('payerDocument') || '-'}</Text>
                    <Text><strong>Forma de pagamento:</strong> {
                      (() => {
                        const m = form.getFieldValue('instrument') || defaultInstrument;
                        return m === 'PIX_IMMEDIATE' ? 'PIX (pagamento na hora)'
                          : m === 'PIX_DUE' ? 'PIX com vencimento'
                          : m === 'BOLETO' ? 'Boleto bancário'
                          : m;
                      })()
                    }</Text>
                    <Text><strong>Valor a pagar:</strong> R$ {Number(info.amount || 0).toFixed(2).replace('.', ',')}</Text>

                    <Space>
                      <Button onClick={() => setStep(0)}>Voltar</Button>
                      <Button
                        type="primary"
                        loading={paying}
                        onClick={() => form.submit()}
                      >
                        Confirmar e pagar
                      </Button>
                    </Space>
                  </Space>
                </Card>
              )}

              {step === 1 && (
                <Form form={form} onFinish={onPay} style={{ display: 'none' }}>
                  <Form.Item name="instrument"><Input /></Form.Item>
                  <Form.Item name="payerName"><Input /></Form.Item>
                  <Form.Item name="payerDocument"><Input /></Form.Item>
                  <Form.Item name="payerEmail"><Input /></Form.Item>
                  <Form.Item name="payerPhone"><Input /></Form.Item>
                  <Form.Item name="pixKey"><Input /></Form.Item>
                </Form>
              )}

              {(info?.status === 'PROCESSING' || info?.status === 'PENDING') && step === 2 && (
                <Alert
                  showIcon
                  type="info"
                  message="Aguardando confirmação do pagamento"
                  description="Quando o banco confirmar, você verá a tela de pagamento concluído automaticamente."
                />
              )}
            </>
          )}
        </Space>
      </Card>

      <Modal
        open={waitingConfirmation}
        closable={false}
        footer={null}
        centered
        title="Aguardando confirmação"
      >
        <Space direction="vertical" size="middle" className="w-full">
          <Text>Seu pagamento foi iniciado. Estamos aguardando o retorno do banco.</Text>
          <div className="flex justify-center">
            <Spin size="large" />
          </div>
        </Space>
      </Modal>
    </div>
  );
}
