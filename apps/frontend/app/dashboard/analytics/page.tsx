'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Dropdown,
  Empty,
  Input,
  Progress,
  Row,
  Segmented,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { MenuProps } from 'antd';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  BarChartOutlined,
  CrownOutlined,
  DollarOutlined,
  DownloadOutlined,
  FallOutlined,
  FieldTimeOutlined,
  LineChartOutlined,
  PercentageOutlined,
  RiseOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { Dayjs } from 'dayjs';
import { useAuth } from '@/app/providers';
import {
  AnalyticsApi,
  AnalyticsDashboard,
  ClientRevenue,
  Granularity,
  Insight,
  InsightSeverity,
  RevenueTimeSeriesPoint,
  StatusBreakdown,
} from '@/lib/api';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

const BRL = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

const BRL_COMPACT = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  notation: 'compact',
  maximumFractionDigits: 1,
});

function formatCurrency(value: number | undefined | null): string {
  return BRL.format(value ?? 0);
}

function formatCompactCurrency(value: number | undefined | null): string {
  return BRL_COMPACT.format(value ?? 0);
}

function formatPercent(value: number | undefined | null, digits = 1): string {
  return `${(value ?? 0).toFixed(digits)}%`;
}

function safeDiv(a: number, b: number): number {
  return b ? a / b : 0;
}

function downloadCsv(filename: string, rows: (string | number)[][]) {
  const csv = rows
    .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(';'))
    .join('\n');
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function GrowthTag({ value }: { value: number }) {
  if (value === 0) return <Tag>0%</Tag>;
  const up = value > 0;
  return (
    <Tag color={up ? 'green' : 'red'} icon={up ? <ArrowUpOutlined /> : <ArrowDownOutlined />}>
      {Math.abs(value)}%
    </Tag>
  );
}

const PAID_STATUSES = ['PAID', 'SETTLED', 'CAPTURED', 'CONFIRMED'];
const PENDING_STATUSES = ['PENDING', 'AUTHORIZED', 'PROCESSING'];

function statusColor(status: string): string {
  if (PAID_STATUSES.includes(status)) return 'green';
  if (PENDING_STATUSES.includes(status)) return 'gold';
  return 'red';
}

const INSIGHT_ALERT_TYPE: Record<InsightSeverity, 'info' | 'success' | 'warning' | 'error'> = {
  INFO: 'info',
  POSITIVE: 'success',
  WARNING: 'warning',
  CRITICAL: 'error',
};

export default function AnalyticsPage() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<AnalyticsDashboard | null>(null);
  const [granularity, setGranularity] = useState<Granularity>('DAY');
  const [range, setRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(30, 'day'),
    dayjs(),
  ]);
  const [chartMetric, setChartMetric] = useState<'revenue' | 'volume'>('revenue');
  const [clientSearch, setClientSearch] = useState('');

  const load = useCallback(async () => {
    if (!user?.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const result = await AnalyticsApi.getDashboard(user.tenantId, {
        from: range[0].format('YYYY-MM-DD'),
        to: range[1].format('YYYY-MM-DD'),
        granularity,
        topClients: 10,
      });
      setData(result);
    } catch (err: any) {
      setError(err?.message || 'Falha ao carregar os dados de analytics.');
    } finally {
      setLoading(false);
    }
  }, [user?.tenantId, range, granularity]);

  useEffect(() => {
    load();
  }, [load]);

  const summary = data?.summary;
  const receivables = data?.receivables;
  const points = useMemo(() => data?.revenueTimeSeries.points ?? [], [data]);

  const maxRevenue = useMemo(
    () => points.reduce((max, p) => Math.max(max, p.grossRevenue), 0),
    [points]
  );
  const maxCount = useMemo(
    () => points.reduce((max, p) => Math.max(max, p.paidCount), 0),
    [points]
  );

  const metrics = useMemo(() => {
    const totalGross = points.reduce((a, p) => a + p.grossRevenue, 0);
    const totalNet = points.reduce((a, p) => a + p.netRevenue, 0);
    const totalPaidCount = points.reduce((a, p) => a + p.paidCount, 0);
    const daysInRange = Math.max(1, range[1].diff(range[0], 'day') + 1);
    const dailyRevenue = totalGross / daysInRange;
    const activePeriods = points.filter((p) => p.grossRevenue > 0).length;
    const peak = points.reduce<RevenueTimeSeriesPoint | null>(
      (best, p) => (p.grossRevenue > (best?.grossRevenue ?? -1) ? p : best),
      null
    );
    const mid = Math.floor(points.length / 2);
    const avgOf = (arr: RevenueTimeSeriesPoint[]) =>
      arr.length ? arr.reduce((a, p) => a + p.grossRevenue, 0) / arr.length : 0;
    const firstHalf = avgOf(points.slice(0, mid));
    const secondHalf = avgOf(points.slice(mid));
    const momentumPct = firstHalf ? ((secondHalf - firstHalf) / firstHalf) * 100 : 0;

    const feeLoadPct = summary ? safeDiv(summary.totalFees, summary.grossRevenue) * 100 : 0;
    const refundRatePct = summary ? safeDiv(summary.totalRefunds, summary.grossRevenue) * 100 : 0;
    const netMarginPct = summary ? safeDiv(summary.netRevenue, summary.grossRevenue) * 100 : 0;
    const arpu = summary ? safeDiv(summary.grossRevenue, summary.payingClients) : 0;
    const txPerClient = summary ? safeDiv(summary.paidTransactions, summary.payingClients) : 0;
    const unpaidTx = summary ? Math.max(0, summary.totalTransactions - summary.paidTransactions) : 0;

    return {
      totalGross,
      totalNet,
      totalPaidCount,
      daysInRange,
      dailyRevenue,
      projection30: dailyRevenue * 30,
      activePeriods,
      peak,
      momentumPct,
      feeLoadPct,
      refundRatePct,
      netMarginPct,
      arpu,
      txPerClient,
      unpaidTx,
    };
  }, [points, range, summary]);

  const concentration = useMemo(() => {
    const list = [...(data?.topClients ?? [])].sort((a, b) => a.rank - b.rank);
    const share = (n: number) => list.slice(0, n).reduce((a, c) => a + c.sharePct, 0);
    const hhi = list.reduce((a, c) => a + Math.pow(c.sharePct, 2), 0);
    const top1 = list[0]?.sharePct ?? 0;
    const top3 = share(3);
    const top5 = share(5);
    let level: { label: string; color: string };
    if (top1 >= 40 || top3 >= 70) level = { label: 'Alto risco', color: 'red' };
    else if (top1 >= 25 || top3 >= 50) level = { label: 'Concentração moderada', color: 'gold' };
    else level = { label: 'Carteira diversificada', color: 'green' };
    return { top1, top3, top5, hhi, level, count: list.length };
  }, [data]);

  const filteredClients = useMemo(() => {
    const q = clientSearch.trim().toLowerCase();
    const list = data?.topClients ?? [];
    if (!q) return list;
    return list.filter((c) => c.clientName.toLowerCase().includes(q));
  }, [data, clientSearch]);

  const exportTimeSeries = () => {
    downloadCsv('receita-serie.csv', [
      ['Data', 'Receita bruta', 'Receita líquida', 'Pagas'],
      ...points.map((p) => [p.date, p.grossRevenue, p.netRevenue, p.paidCount]),
    ]);
  };

  const exportRanking = () => {
    downloadCsv('ranking-clientes.csv', [
      ['#', 'Cliente', 'Receita', 'Transações', 'Participação (%)'],
      ...(data?.topClients ?? []).map((c) => [c.rank, c.clientName, c.revenue, c.txCount, c.sharePct]),
    ]);
  };

  const exportMenu: MenuProps = {
    items: [
      { key: 'ts', label: 'Série de receita (CSV)', icon: <LineChartOutlined /> },
      { key: 'rk', label: 'Ranking de clientes (CSV)', icon: <CrownOutlined /> },
    ],
    onClick: ({ key }) => (key === 'ts' ? exportTimeSeries() : exportRanking()),
  };

  const clientColumns: ColumnsType<ClientRevenue> = [
    { title: '#', dataIndex: 'rank', key: 'rank', width: 56 },
    { title: 'Cliente', dataIndex: 'clientName', key: 'clientName' },
    {
      title: 'Receita',
      dataIndex: 'revenue',
      key: 'revenue',
      align: 'right',
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Transações',
      dataIndex: 'txCount',
      key: 'txCount',
      align: 'right',
      width: 110,
    },
    {
      title: 'Ticket médio',
      key: 'avgTicket',
      align: 'right',
      width: 130,
      render: (_: unknown, row: ClientRevenue) => formatCurrency(safeDiv(row.revenue, row.txCount)),
    },
    {
      title: 'Participação',
      dataIndex: 'sharePct',
      key: 'sharePct',
      width: 200,
      render: (v: number) => (
        <Progress
          percent={Math.min(100, v)}
          size="small"
          status={v >= 30 ? 'exception' : 'normal'}
          format={(p) => `${p?.toFixed(1)}%`}
        />
      ),
    },
  ];

  const statusColumns: ColumnsType<StatusBreakdown> = [
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => <Tag color={statusColor(s)}>{s}</Tag>,
    },
    { title: 'Qtd.', dataIndex: 'txCount', key: 'txCount', align: 'right', width: 80 },
    {
      title: 'Valor',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      align: 'right',
      render: (v: number) => formatCurrency(v),
    },
    {
      title: 'Participação',
      dataIndex: 'sharePct',
      key: 'sharePct',
      width: 200,
      render: (v: number, row: StatusBreakdown) => (
        <Progress
          percent={Math.min(100, v)}
          size="small"
          strokeColor={
            PAID_STATUSES.includes(row.status)
              ? '#16a34a'
              : PENDING_STATUSES.includes(row.status)
              ? '#d97706'
              : '#dc2626'
          }
          format={(p) => `${p?.toFixed(1)}%`}
        />
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
        <div>
          <Title level={3} style={{ margin: 0 }}>
            Analytics Corporativo
          </Title>
          <Text type="secondary">
            Inteligência de negócio e performance financeira em tempo quase real.
          </Text>
        </div>
        <Space wrap>
          <Text type="secondary" style={{ fontSize: 13 }}>
            {range[0].format('DD/MM/YYYY')} – {range[1].format('DD/MM/YYYY')} · {metrics.daysInRange} dias
          </Text>
          <Segmented
            value={granularity}
            onChange={(value) => setGranularity(value as Granularity)}
            options={[
              { label: 'Dia', value: 'DAY' },
              { label: 'Semana', value: 'WEEK' },
              { label: 'Mês', value: 'MONTH' },
              { label: 'Trimestre', value: 'QUARTER' },
            ]}
          />
          <RangePicker
            value={range}
            allowClear={false}
            presets={[
              { label: 'Últimos 7 dias', value: [dayjs().subtract(6, 'day'), dayjs()] },
              { label: 'Últimos 30 dias', value: [dayjs().subtract(29, 'day'), dayjs()] },
              { label: 'Últimos 90 dias', value: [dayjs().subtract(89, 'day'), dayjs()] },
              { label: 'Este mês', value: [dayjs().startOf('month'), dayjs()] },
              { label: 'Este ano', value: [dayjs().startOf('year'), dayjs()] },
            ]}
            onChange={(values) => {
              if (values && values[0] && values[1]) {
                setRange([values[0], values[1]]);
              }
            }}
          />
          <Dropdown menu={exportMenu}>
            <Button icon={<DownloadOutlined />}>Exportar</Button>
          </Dropdown>
        </Space>
      </Space>

      {error && <Alert type="error" message={error} showIcon closable onClose={() => setError(null)} />}

      <Spin spinning={loading}>
        {!data && !loading ? (
          <Empty description="Sem dados para o período selecionado" />
        ) : (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            {/* KPIs executivos */}
            <Row gutter={[16, 16]}>
              <Col xs={24} sm={12} xl={6}>
                <Card>
                  <Statistic
                    title="Receita bruta"
                    value={summary?.grossRevenue ?? 0}
                    precision={2}
                    prefix={<DollarOutlined />}
                    formatter={(v) => formatCurrency(Number(v))}
                  />
                  <Space style={{ marginTop: 8 }}>
                    <Text type="secondary">vs. anterior</Text>
                    <GrowthTag value={summary?.growth.grossRevenuePct ?? 0} />
                  </Space>
                </Card>
              </Col>
              <Col xs={24} sm={12} xl={6}>
                <Card>
                  <Statistic
                    title="Receita líquida"
                    value={summary?.netRevenue ?? 0}
                    precision={2}
                    prefix={<LineChartOutlined />}
                    formatter={(v) => formatCurrency(Number(v))}
                  />
                  <Space style={{ marginTop: 8 }}>
                    <Text type="secondary">vs. anterior</Text>
                    <GrowthTag value={summary?.growth.netRevenuePct ?? 0} />
                  </Space>
                </Card>
              </Col>
              <Col xs={24} sm={12} xl={6}>
                <Card>
                  <Statistic
                    title="Lucro líquido"
                    value={summary?.netProfit ?? 0}
                    precision={2}
                    prefix={<RiseOutlined />}
                    formatter={(v) => formatCurrency(Number(v))}
                  />
                  <Space style={{ marginTop: 8 }}>
                    <Text type="secondary">Margem contrib.</Text>
                    <Tag color="blue">{formatPercent(summary?.contributionMarginPct)}</Tag>
                  </Space>
                </Card>
              </Col>
              <Col xs={24} sm={12} xl={6}>
                <Card>
                  <Statistic
                    title="Ticket médio"
                    value={summary?.averageTicket ?? 0}
                    precision={2}
                    formatter={(v) => formatCurrency(Number(v))}
                  />
                  <Space style={{ marginTop: 8 }}>
                    <Text type="secondary">Conversão</Text>
                    <Tag color="geekblue">{formatPercent(summary?.conversionRatePct)}</Tag>
                  </Space>
                </Card>
              </Col>
            </Row>

            {/* Indicadores de margem e custo (derivados) */}
            <Row gutter={[16, 16]}>
              <Col xs={24} sm={12} xl={6}>
                <Card>
                  <Statistic
                    title="Margem líquida"
                    value={metrics.netMarginPct}
                    precision={1}
                    suffix="%"
                    prefix={<PercentageOutlined />}
                  />
                  <Progress
                    percent={Math.min(100, Math.max(0, metrics.netMarginPct))}
                    showInfo={false}
                    size="small"
                    strokeColor="#16a34a"
                    style={{ marginTop: 8 }}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={12} xl={6}>
                <Card>
                  <Statistic title="Carga de tarifas" value={metrics.feeLoadPct} precision={1} suffix="%" />
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {formatCurrency(summary?.totalFees)} em tarifas
                  </Text>
                </Card>
              </Col>
              <Col xs={24} sm={12} xl={6}>
                <Card>
                  <Statistic
                    title="Taxa de estorno"
                    value={metrics.refundRatePct}
                    precision={1}
                    suffix="%"
                    valueStyle={metrics.refundRatePct >= 5 ? { color: '#dc2626' } : undefined}
                    prefix={metrics.refundRatePct >= 5 ? <FallOutlined /> : undefined}
                  />
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {formatCurrency(summary?.totalRefunds)} estornados
                  </Text>
                </Card>
              </Col>
              <Col xs={24} sm={12} xl={6}>
                <Card>
                  <Statistic
                    title="Receita por cliente (ARPU)"
                    value={metrics.arpu}
                    precision={2}
                    prefix={<TeamOutlined />}
                    formatter={(v) => formatCurrency(Number(v))}
                  />
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {metrics.txPerClient.toFixed(1)} transações/cliente
                  </Text>
                </Card>
              </Col>
            </Row>

            {/* Indicadores operacionais */}
            <Card size="small" title="Indicadores operacionais">
              <Row gutter={[16, 16]}>
                <Col xs={12} sm={8} lg={6}>
                  <Statistic title="Clientes pagantes" value={summary?.payingClients ?? 0} prefix={<TeamOutlined />} />
                </Col>
                <Col xs={12} sm={8} lg={6}>
                  <Statistic
                    title="Transações pagas"
                    value={summary?.paidTransactions ?? 0}
                    suffix={`/ ${summary?.totalTransactions ?? 0}`}
                  />
                </Col>
                <Col xs={12} sm={8} lg={6}>
                  <Statistic title="Não pagas" value={metrics.unpaidTx} valueStyle={{ color: '#d97706' }} />
                </Col>
                <Col xs={12} sm={8} lg={6}>
                  <Statistic
                    title="Projeção 30 dias"
                    value={metrics.projection30}
                    precision={0}
                    prefix={<ThunderboltOutlined />}
                    formatter={(v) => formatCompactCurrency(Number(v))}
                  />
                </Col>
                <Col xs={12} sm={8} lg={6}>
                  <Statistic
                    title="Receita média/dia"
                    value={metrics.dailyRevenue}
                    precision={0}
                    formatter={(v) => formatCompactCurrency(Number(v))}
                  />
                </Col>
                <Col xs={12} sm={8} lg={6}>
                  <Statistic
                    title="Pico de receita"
                    value={metrics.peak?.grossRevenue ?? 0}
                    precision={0}
                    formatter={(v) => formatCompactCurrency(Number(v))}
                  />
                  {metrics.peak && (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {dayjs(metrics.peak.date).format('DD/MM/YYYY')}
                    </Text>
                  )}
                </Col>
                <Col xs={12} sm={8} lg={6}>
                  <Statistic
                    title="Períodos ativos"
                    value={metrics.activePeriods}
                    suffix={`/ ${points.length}`}
                    prefix={<FieldTimeOutlined />}
                  />
                </Col>
                <Col xs={12} sm={8} lg={6}>
                  <Text type="secondary" style={{ fontSize: 14 }}>
                    Momentum (2ª metade)
                  </Text>
                  <div style={{ marginTop: 4 }}>
                    <GrowthTag value={Number(metrics.momentumPct.toFixed(1))} />
                  </div>
                </Col>
              </Row>
            </Card>

            {/* Insights automáticos */}
            {data?.insights && data.insights.length > 0 && (
              <Card title="Insights automáticos" size="small">
                <Space direction="vertical" size="small" style={{ width: '100%' }}>
                  {data.insights.map((insight: Insight, idx: number) => (
                    <Alert
                      key={idx}
                      type={INSIGHT_ALERT_TYPE[insight.severity]}
                      showIcon
                      message={insight.title}
                      description={insight.message}
                    />
                  ))}
                </Space>
              </Card>
            )}

            <Row gutter={[16, 16]}>
              {/* Série temporal de receita */}
              <Col xs={24} lg={16}>
                <Card
                  title="Evolução da receita"
                  extra={
                    <Segmented
                      size="small"
                      value={chartMetric}
                      onChange={(v) => setChartMetric(v as 'revenue' | 'volume')}
                      options={[
                        { label: 'Receita', value: 'revenue', icon: <BarChartOutlined /> },
                        { label: 'Volume', value: 'volume', icon: <LineChartOutlined /> },
                      ]}
                    />
                  }
                >
                  <Space size="large" wrap style={{ marginBottom: 12 }}>
                    <Statistic
                      title="Total bruto"
                      value={metrics.totalGross}
                      precision={2}
                      valueStyle={{ fontSize: 18 }}
                      formatter={(v) => formatCurrency(Number(v))}
                    />
                    <Statistic
                      title="Total líquido"
                      value={metrics.totalNet}
                      precision={2}
                      valueStyle={{ fontSize: 18 }}
                      formatter={(v) => formatCurrency(Number(v))}
                    />
                    <Statistic title="Transações pagas" value={metrics.totalPaidCount} valueStyle={{ fontSize: 18 }} />
                  </Space>

                  {points.length === 0 ? (
                    <Empty description="Sem receita no período" />
                  ) : (
                    <>
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'flex-end',
                          gap: 6,
                          height: 220,
                          overflowX: 'auto',
                          paddingTop: 8,
                        }}
                      >
                        {points.map((point) => {
                          const isRevenue = chartMetric === 'revenue';
                          const denom = isRevenue ? maxRevenue : maxCount;
                          const value = isRevenue ? point.grossRevenue : point.paidCount;
                          const heightPct = denom > 0 ? (value / denom) * 100 : 0;
                          const netPct = isRevenue ? safeDiv(point.netRevenue, point.grossRevenue) * 100 : 100;
                          return (
                            <div
                              key={point.date}
                              title={
                                isRevenue
                                  ? `${dayjs(point.date).format('DD/MM/YYYY')} — Bruto ${formatCurrency(point.grossRevenue)} · Líquido ${formatCurrency(point.netRevenue)}`
                                  : `${dayjs(point.date).format('DD/MM/YYYY')} — ${point.paidCount} pagas`
                              }
                              style={{
                                flex: '1 0 24px',
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                justifyContent: 'flex-end',
                                height: '100%',
                              }}
                            >
                              <div
                                style={{
                                  position: 'relative',
                                  width: '100%',
                                  minHeight: 2,
                                  height: `${heightPct}%`,
                                  background: isRevenue
                                    ? '#d4d4d4'
                                    : 'linear-gradient(180deg, #171717 0%, #525252 100%)',
                                  borderRadius: 4,
                                  transition: 'height 0.3s ease',
                                  overflow: 'hidden',
                                }}
                              >
                                {isRevenue && (
                                  <div
                                    style={{
                                      position: 'absolute',
                                      bottom: 0,
                                      left: 0,
                                      right: 0,
                                      height: `${netPct}%`,
                                      background: 'linear-gradient(180deg, #171717 0%, #525252 100%)',
                                    }}
                                  />
                                )}
                              </div>
                              <Text type="secondary" style={{ fontSize: 10, marginTop: 4, whiteSpace: 'nowrap' }}>
                                {dayjs(point.date).format('DD/MM')}
                              </Text>
                            </div>
                          );
                        })}
                      </div>
                      {chartMetric === 'revenue' && (
                        <Space size="middle" style={{ marginTop: 8 }}>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            <span style={{ display: 'inline-block', width: 10, height: 10, background: '#171717', borderRadius: 2, marginRight: 4 }} />
                            Líquido
                          </Text>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            <span style={{ display: 'inline-block', width: 10, height: 10, background: '#d4d4d4', borderRadius: 2, marginRight: 4 }} />
                            Tarifas/estornos
                          </Text>
                        </Space>
                      )}
                    </>
                  )}
                </Card>
              </Col>

              {/* Recebíveis e inadimplência */}
              <Col xs={24} lg={8}>
                <Card title="Recebíveis">
                  <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                    <Statistic title="Total a receber" value={receivables?.totalReceivable ?? 0} formatter={(v) => formatCurrency(Number(v))} />
                    <Statistic title="A vencer" value={receivables?.totalToDue ?? 0} formatter={(v) => formatCurrency(Number(v))} />
                    <div>
                      <Text type="secondary">Inadimplência</Text>
                      <Progress
                        percent={Math.min(100, receivables?.overdueRatioPct ?? 0)}
                        status={(receivables?.overdueRatioPct ?? 0) >= 25 ? 'exception' : 'active'}
                        format={(p) => `${p?.toFixed(1)}%`}
                      />
                      <Text type={(receivables?.overdueRatioPct ?? 0) >= 25 ? 'danger' : 'secondary'}>
                        {(receivables?.overdueRatioPct ?? 0) >= 25 && <WarningOutlined />} Vencido: {formatCurrency(receivables?.totalOverdue)}
                      </Text>
                    </div>
                  </Space>
                </Card>
              </Col>
            </Row>

            <Row gutter={[16, 16]}>
              {/* Ranking de clientes */}
              <Col xs={24} lg={14}>
                <Card
                  title="Ranking de clientes por receita"
                  styles={{ body: { padding: 0 } }}
                  extra={
                    <Button size="small" icon={<DownloadOutlined />} onClick={exportRanking}>
                      Exportar
                    </Button>
                  }
                >
                  <Table
                    rowKey="clientId"
                    size="small"
                    columns={clientColumns}
                    dataSource={filteredClients}
                    pagination={false}
                    title={() => (
                      <Input.Search
                        allowClear
                        placeholder="Buscar cliente"
                        style={{ maxWidth: 280 }}
                        value={clientSearch}
                        onChange={(e) => setClientSearch(e.target.value)}
                      />
                    )}
                    locale={{ emptyText: 'Nenhum cliente com receita no período.' }}
                  />
                </Card>
              </Col>

              {/* Concentração de clientes */}
              <Col xs={24} lg={10}>
                <Card title="Concentração de clientes">
                  <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                    <Space>
                      <Tag color={concentration.level.color}>{concentration.level.label}</Tag>
                      <Tooltip title="Índice Herfindahl-Hirschman: quanto maior, mais concentrada a carteira.">
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          HHI {concentration.hhi.toFixed(0)}
                        </Text>
                      </Tooltip>
                    </Space>
                    <div>
                      <Text type="secondary" style={{ fontSize: 12 }}>Maior cliente</Text>
                      <Progress
                        percent={Math.min(100, concentration.top1)}
                        format={(p) => formatPercent(p)}
                        status={concentration.top1 >= 40 ? 'exception' : 'normal'}
                      />
                    </div>
                    <div>
                      <Text type="secondary" style={{ fontSize: 12 }}>Top 3 clientes</Text>
                      <Progress
                        percent={Math.min(100, concentration.top3)}
                        format={(p) => formatPercent(p)}
                        status={concentration.top3 >= 70 ? 'exception' : 'normal'}
                      />
                    </div>
                    <div>
                      <Text type="secondary" style={{ fontSize: 12 }}>Top 5 clientes</Text>
                      <Progress percent={Math.min(100, concentration.top5)} format={(p) => formatPercent(p)} />
                    </div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      Baseado em {concentration.count} clientes do ranking.
                    </Text>
                  </Space>
                </Card>
              </Col>
            </Row>

            {/* Breakdown por status */}
            <Card title="Distribuição por status" styles={{ body: { padding: 0 } }}>
              <Table
                rowKey="status"
                size="small"
                columns={statusColumns}
                dataSource={data?.statusBreakdown ?? []}
                pagination={false}
                locale={{ emptyText: 'Sem transações no período.' }}
              />
            </Card>
          </Space>
        )}
      </Spin>
    </Space>
  );
}
