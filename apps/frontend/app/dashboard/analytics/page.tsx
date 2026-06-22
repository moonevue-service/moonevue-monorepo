"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Dropdown,
  Empty,
  Grid,
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
} from "antd";
import type { MenuProps } from "antd";
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  BarChartOutlined,
  BulbOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  CrownOutlined,
  DollarOutlined,
  DownloadOutlined,
  FallOutlined,
  FieldTimeOutlined,
  FundOutlined,
  HeartOutlined,
  InfoCircleOutlined,
  LeftOutlined,
  LineChartOutlined,
  PercentageOutlined,
  RightOutlined,
  RiseOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import dayjs, { Dayjs } from "dayjs";
import { useAuth } from "@/app/providers";
import {
  AnalyticsApi,
  AnalyticsDashboard,
  ClientRevenue,
  Granularity,
  Insight,
  InsightSeverity,
  StatusBreakdown,
} from "@/lib/api";
import {
  CHART,
  DonutChart,
  FunnelBar,
  RevenueTrendChart,
  ScoreGauge,
  Sparkline,
  VolumeBarChart,
} from "./charts";
import {
  buildRecommendations,
  coefficientOfVariation,
  computeHealthScore,
  gini,
  linearForecast,
  movingAverage,
  paretoCount,
  RecPriority,
  Recommendation,
} from "./metrics";

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

const BRL = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});
const BRL_COMPACT = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
  notation: "compact",
  maximumFractionDigits: 1,
});

const formatCurrency = (value: number | undefined | null) =>
  BRL.format(value ?? 0);
const formatCompactCurrency = (value: number | undefined | null) =>
  BRL_COMPACT.format(value ?? 0);
const formatPercent = (value: number | undefined | null, digits = 1) =>
  `${(value ?? 0).toFixed(digits)}%`;
const safeDiv = (a: number, b: number) => (b ? a / b : 0);

function downloadCsv(filename: string, rows: (string | number)[][]) {
  const csv = rows
    .map((row) =>
      row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(";"),
    )
    .join("\n");
  const blob = new Blob([`\ufeff${csv}`], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function GrowthTag({ value }: { value: number }) {
  if (!value) return <Tag>0%</Tag>;
  const up = value > 0;
  return (
    <Tag
      color={up ? "green" : "red"}
      icon={up ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
    >
      {Math.abs(value)}%
    </Tag>
  );
}

const PAID_STATUSES = ["PAID", "SETTLED", "CAPTURED", "CONFIRMED"];
const PENDING_STATUSES = ["PENDING", "AUTHORIZED", "PROCESSING"];

function statusHex(status: string): string {
  if (PAID_STATUSES.includes(status)) return CHART.green;
  if (PENDING_STATUSES.includes(status)) return CHART.amber;
  return CHART.red;
}
function statusColor(status: string): string {
  if (PAID_STATUSES.includes(status)) return "green";
  if (PENDING_STATUSES.includes(status)) return "gold";
  return "red";
}

const INSIGHT_VISUAL: Record<
  InsightSeverity,
  { color: string; icon: React.ReactNode }
> = {
  INFO: { color: CHART.blue, icon: <InfoCircleOutlined /> },
  POSITIVE: { color: CHART.green, icon: <CheckCircleOutlined /> },
  WARNING: { color: CHART.amber, icon: <WarningOutlined /> },
  CRITICAL: { color: CHART.red, icon: <CloseCircleOutlined /> },
};

const REC_META: Record<RecPriority, { color: string; label: string }> = {
  HIGH: { color: "red", label: "Prioridade alta" },
  MEDIUM: { color: "gold", label: "Prioridade média" },
  LOW: { color: "blue", label: "Oportunidade" },
  POSITIVE: { color: "green", label: "Ponto forte" },
};

const DONUT_PALETTE = ["#171717", "#404040", "#525252", "#737373", "#a3a3a3"];
const WEEKDAYS = ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"];

function unitFor(g: Granularity): "day" | "week" | "month" | "year" {
  if (g === "WEEK") return "week";
  if (g === "MONTH") return "month";
  if (g === "YEAR") return "year";
  return "day";
}

function recBorder(priority: RecPriority): string {
  if (priority === "HIGH") return CHART.red;
  if (priority === "MEDIUM") return CHART.amber;
  if (priority === "POSITIVE") return CHART.green;
  return CHART.blue;
}

export default function AnalyticsPage() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<AnalyticsDashboard | null>(null);
  const [granularity, setGranularity] = useState<Granularity>("DAY");
  const [range, setRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(30, "day"),
    dayjs(),
  ]);
  const [chartMetric, setChartMetric] = useState<"revenue" | "volume">(
    "revenue",
  );
  const [showForecast, setShowForecast] = useState(true);
  const [clientSearch, setClientSearch] = useState("");

  const load = useCallback(async () => {
    if (!user?.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const result = await AnalyticsApi.getDashboard(user.tenantId, {
        from: range[0].format("YYYY-MM-DD"),
        to: range[1].format("YYYY-MM-DD"),
        granularity,
        topClients: 10,
      });
      setData(result);
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : "Falha ao carregar os dados de analytics.",
      );
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

  const pointLabel = useCallback(
    (date: string) => {
      const d = dayjs(date);
      if (granularity === "MONTH" || granularity === "QUARTER")
        return d.format("MMM/YY");
      if (granularity === "YEAR") return d.format("YYYY");
      return d.format("DD/MM");
    },
    [granularity],
  );

  /* ---- derived series & forecast ---- */
  const series = useMemo(() => {
    const gross = points.map((p) => p.grossRevenue);
    const net = points.map((p) => p.netRevenue);
    const count = points.map((p) => p.paidCount);
    const window = points.length >= 14 ? 7 : points.length >= 6 ? 3 : 1;
    const ma =
      window > 1 ? movingAverage(gross, window) : gross.map(() => null);

    const stepsByGran: Record<Granularity, number> = {
      DAY: 7,
      WEEK: 4,
      MONTH: 3,
      QUARTER: 2,
      YEAR: 1,
    };
    const steps = Math.min(
      stepsByGran[granularity],
      Math.max(1, Math.round(points.length / 3)) || 1,
    );
    const forecastValues =
      points.length >= 3 && showForecast ? linearForecast(gross, steps) : [];

    const last = points.length
      ? dayjs(points[points.length - 1].date)
      : dayjs();
    const forecast = forecastValues.map((value, k) => {
      const d =
        granularity === "QUARTER"
          ? last.add((k + 1) * 3, "month")
          : last.add(k + 1, unitFor(granularity));
      return { label: pointLabel(d.format("YYYY-MM-DD")), value };
    });

    const avgForecast = forecastValues.length
      ? forecastValues.reduce((a, b) => a + b, 0) / forecastValues.length
      : 0;
    const recentN = Math.min(forecastValues.length || 1, gross.length);
    const recent = gross.slice(-recentN);
    const recentAvg = recent.length
      ? recent.reduce((a, b) => a + b, 0) / recent.length
      : 0;
    const forecastDeltaPct = recentAvg
      ? ((avgForecast - recentAvg) / recentAvg) * 100
      : 0;

    return {
      gross,
      net,
      count,
      ma,
      forecast,
      forecastSum: forecastValues.reduce((a, b) => a + b, 0),
      forecastDeltaPct,
      volatility: coefficientOfVariation(gross.filter((v) => v > 0)),
    };
  }, [points, granularity, showForecast, pointLabel]);

  const trendData = useMemo(
    () =>
      points.map((p) => ({
        label: pointLabel(p.date),
        gross: p.grossRevenue,
        net: p.netRevenue,
      })),
    [points, pointLabel],
  );
  const volumeData = useMemo(
    () =>
      points.map((p) => ({ label: pointLabel(p.date), value: p.paidCount })),
    [points, pointLabel],
  );

  /* ---- seasonality (daily only) ---- */
  const seasonality = useMemo(() => {
    if (granularity !== "DAY" || points.length < 7) return null;
    const sums = Array(7).fill(0);
    const counts = Array(7).fill(0);
    points.forEach((p) => {
      const wd = dayjs(p.date).day();
      sums[wd] += p.grossRevenue;
      counts[wd] += 1;
    });
    const avg = sums.map((s, i) => (counts[i] ? s / counts[i] : 0));
    let best = 0;
    let worst = 0;
    avg.forEach((v, i) => {
      if (v > avg[best]) best = i;
      if (v < avg[worst]) worst = i;
    });
    return {
      best: WEEKDAYS[best],
      worst: WEEKDAYS[worst],
      hasData: avg.some((v) => v > 0),
    };
  }, [points, granularity]);

  /* ---- core derived metrics ---- */
  const metrics = useMemo(() => {
    const totalGross = series.gross.reduce((a, b) => a + b, 0);
    const totalNet = series.net.reduce((a, b) => a + b, 0);
    const totalPaidCount = series.count.reduce((a, b) => a + b, 0);
    const daysInRange = Math.max(1, range[1].diff(range[0], "day") + 1);
    const dailyRevenue = totalGross / daysInRange;
    const activePeriods = points.filter((p) => p.grossRevenue > 0).length;
    const peak = points.reduce<(typeof points)[number] | null>(
      (best, p) => (p.grossRevenue > (best?.grossRevenue ?? -1) ? p : best),
      null,
    );
    const mid = Math.floor(points.length / 2);
    const avgOf = (arr: typeof points) =>
      arr.length ? arr.reduce((a, p) => a + p.grossRevenue, 0) / arr.length : 0;
    const firstHalf = avgOf(points.slice(0, mid));
    const secondHalf = avgOf(points.slice(mid));
    const momentumPct = firstHalf
      ? ((secondHalf - firstHalf) / firstHalf) * 100
      : 0;

    const feeLoadPct = summary
      ? safeDiv(summary.totalFees, summary.grossRevenue) * 100
      : 0;
    const refundRatePct = summary
      ? safeDiv(summary.totalRefunds, summary.grossRevenue) * 100
      : 0;
    const netMarginPct = summary
      ? safeDiv(summary.netRevenue, summary.grossRevenue) * 100
      : 0;
    const arpu = summary
      ? safeDiv(summary.grossRevenue, summary.payingClients)
      : 0;
    const txPerClient = summary
      ? safeDiv(summary.paidTransactions, summary.payingClients)
      : 0;
    const unpaidTx = summary
      ? Math.max(0, summary.totalTransactions - summary.paidTransactions)
      : 0;

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
  }, [series, points, range, summary]);

  /* ---- concentration ---- */
  const concentration = useMemo(() => {
    const list = [...(data?.topClients ?? [])].sort((a, b) => a.rank - b.rank);
    const share = (n: number) =>
      list.slice(0, n).reduce((a, c) => a + c.sharePct, 0);
    const hhi = list.reduce((a, c) => a + c.sharePct ** 2, 0);
    const giniIndex = gini(list.map((c) => c.revenue));
    const pareto = paretoCount(
      list.map((c) => c.sharePct),
      80,
    );
    const top1 = list[0]?.sharePct ?? 0;
    const top3 = share(3);
    const top5 = share(5);
    let level: { label: string; color: string };
    if (top1 >= 40 || top3 >= 70) level = { label: "Alto risco", color: "red" };
    else if (top1 >= 25 || top3 >= 50)
      level = { label: "Concentração moderada", color: "gold" };
    else level = { label: "Carteira diversificada", color: "green" };
    return {
      top1,
      top3,
      top5,
      hhi,
      giniIndex,
      pareto,
      level,
      count: list.length,
    };
  }, [data]);

  const clientDonut = useMemo(() => {
    const list = [...(data?.topClients ?? [])].sort((a, b) => a.rank - b.rank);
    const top = list.slice(0, 5);
    const slices = top.map((c, i) => ({
      label: c.clientName,
      value: c.sharePct,
      color: DONUT_PALETTE[i],
    }));
    const rest = 100 - top.reduce((a, c) => a + c.sharePct, 0);
    if (rest > 0.5)
      slices.push({ label: "Demais", value: rest, color: "#d4d4d4" });
    return slices;
  }, [data]);

  const statusDonut = useMemo(
    () =>
      (data?.statusBreakdown ?? [])
        .filter((s) => s.txCount > 0)
        .map((s) => ({
          label: s.status,
          value: s.txCount,
          color: statusHex(s.status),
        })),
    [data],
  );

  /* ---- funnel ---- */
  const funnel = useMemo(() => {
    if (!summary) return [];
    const settled = (data?.statusBreakdown ?? [])
      .filter((s) => s.status === "SETTLED" || s.status === "CONFIRMED")
      .reduce((a, s) => a + s.txCount, 0);
    return [
      {
        label: "Cobranças emitidas",
        value: summary.totalTransactions,
        color: CHART.blue,
      },
      { label: "Pagas", value: summary.paidTransactions, color: CHART.ink },
      {
        label: "Liquidadas / confirmadas",
        value: settled || summary.paidTransactions,
        color: CHART.green,
      },
    ];
  }, [summary, data]);

  /* ---- health score ---- */
  const health = useMemo(
    () =>
      computeHealthScore({
        netMarginPct: metrics.netMarginPct,
        conversionRatePct: summary?.conversionRatePct ?? 0,
        refundRatePct: metrics.refundRatePct,
        overdueRatioPct: receivables?.overdueRatioPct ?? 0,
        top3SharePct: concentration.top3,
        momentumPct: metrics.momentumPct,
      }),
    [metrics, summary, receivables, concentration],
  );

  /* ---- recommendations ---- */
  const recommendations: Recommendation[] = useMemo(() => {
    if (!summary) return [];
    return buildRecommendations({
      netMarginPct: metrics.netMarginPct,
      feeLoadPct: metrics.feeLoadPct,
      refundRatePct: metrics.refundRatePct,
      conversionRatePct: summary.conversionRatePct,
      overdueRatioPct: receivables?.overdueRatioPct ?? 0,
      top3SharePct: concentration.top3,
      top1SharePct: concentration.top1,
      momentumPct: metrics.momentumPct,
      forecastDeltaPct: series.forecastDeltaPct,
      payingClients: summary.payingClients,
      unpaidTx: metrics.unpaidTx,
      totalOverdue: receivables?.totalOverdue ?? 0,
      totalFees: summary.totalFees,
      fmtCurrency: formatCurrency,
    });
  }, [summary, metrics, receivables, concentration, series]);

  const filteredClients = useMemo(() => {
    const q = clientSearch.trim().toLowerCase();
    const list = data?.topClients ?? [];
    if (!q) return list;
    return list.filter((c) => c.clientName.toLowerCase().includes(q));
  }, [data, clientSearch]);

  const exportTimeSeries = () =>
    downloadCsv("receita-serie.csv", [
      ["Data", "Receita bruta", "Receita líquida", "Pagas"],
      ...points.map((p) => [p.date, p.grossRevenue, p.netRevenue, p.paidCount]),
    ]);
  const exportRanking = () =>
    downloadCsv("ranking-clientes.csv", [
      ["#", "Cliente", "Receita", "Transações", "Participação (%)"],
      ...(data?.topClients ?? []).map((c) => [
        c.rank,
        c.clientName,
        c.revenue,
        c.txCount,
        c.sharePct,
      ]),
    ]);

  const exportMenu: MenuProps = {
    items: [
      {
        key: "ts",
        label: "Série de receita (CSV)",
        icon: <LineChartOutlined />,
      },
      {
        key: "rk",
        label: "Ranking de clientes (CSV)",
        icon: <CrownOutlined />,
      },
    ],
    onClick: ({ key }) => (key === "ts" ? exportTimeSeries() : exportRanking()),
  };

  const clientColumns: ColumnsType<ClientRevenue> = [
    { title: "#", dataIndex: "rank", key: "rank", width: 56 },
    { title: "Cliente", dataIndex: "clientName", key: "clientName" },
    {
      title: "Receita",
      dataIndex: "revenue",
      key: "revenue",
      align: "right",
      render: (v: number) => formatCurrency(v),
    },
    {
      title: "Transações",
      dataIndex: "txCount",
      key: "txCount",
      align: "right",
      width: 110,
    },
    {
      title: "Ticket médio",
      key: "avgTicket",
      align: "right",
      width: 130,
      render: (_: unknown, row: ClientRevenue) =>
        formatCurrency(safeDiv(row.revenue, row.txCount)),
    },
    {
      title: "Participação",
      dataIndex: "sharePct",
      key: "sharePct",
      width: 200,
      render: (v: number) => (
        <Progress
          percent={Math.min(100, v)}
          size="small"
          status={v >= 30 ? "exception" : "normal"}
          format={(p) => `${p?.toFixed(1)}%`}
        />
      ),
    },
  ];

  const statusColumns: ColumnsType<StatusBreakdown> = [
    {
      title: "Status",
      dataIndex: "status",
      key: "status",
      render: (s: string) => <Tag color={statusColor(s)}>{s}</Tag>,
    },
    {
      title: "Qtd.",
      dataIndex: "txCount",
      key: "txCount",
      align: "right",
      width: 80,
    },
    {
      title: "Valor",
      dataIndex: "totalAmount",
      key: "totalAmount",
      align: "right",
      render: (v: number) => formatCurrency(v),
    },
    {
      title: "Participação",
      dataIndex: "sharePct",
      key: "sharePct",
      width: 200,
      render: (v: number, row: StatusBreakdown) => (
        <Progress
          percent={Math.min(100, v)}
          size="small"
          strokeColor={statusHex(row.status)}
          format={(p) => `${p?.toFixed(1)}%`}
        />
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <Space style={{ width: "100%", justifyContent: "space-between" }} wrap>
        <div>
          <Title level={3} style={{ margin: 0 }}>
            Analytics Corporativo
          </Title>
          <Text type="secondary">
            Inteligência de negócio, projeções e recomendações acionáveis.
          </Text>
        </div>
        <Space wrap>
          <Text type="secondary" style={{ fontSize: 13 }}>
            {range[0].format("DD/MM/YYYY")} – {range[1].format("DD/MM/YYYY")} ·{" "}
            {metrics.daysInRange} dias
          </Text>
          <Segmented
            value={granularity}
            onChange={(value) => setGranularity(value as Granularity)}
            options={[
              { label: "Dia", value: "DAY" },
              { label: "Semana", value: "WEEK" },
              { label: "Mês", value: "MONTH" },
              { label: "Trimestre", value: "QUARTER" },
            ]}
          />
          <RangePicker
            value={range}
            allowClear={false}
            presets={[
              {
                label: "Últimos 7 dias",
                value: [dayjs().subtract(6, "day"), dayjs()],
              },
              {
                label: "Últimos 30 dias",
                value: [dayjs().subtract(29, "day"), dayjs()],
              },
              {
                label: "Últimos 90 dias",
                value: [dayjs().subtract(89, "day"), dayjs()],
              },
              { label: "Este mês", value: [dayjs().startOf("month"), dayjs()] },
              { label: "Este ano", value: [dayjs().startOf("year"), dayjs()] },
            ]}
            onChange={(values) => {
              if (values && values[0] && values[1])
                setRange([values[0], values[1]]);
            }}
          />
          <Dropdown menu={exportMenu}>
            <Button icon={<DownloadOutlined />}>Exportar</Button>
          </Dropdown>
        </Space>
      </Space>

      {error && (
        <Alert
          type="error"
          message={error}
          showIcon
          closable
          onClose={() => setError(null)}
        />
      )}

      <Spin spinning={loading}>
        {!data && !loading ? (
          <Empty description="Sem dados para o período selecionado" />
        ) : (
          <Space direction="vertical" size="large" style={{ width: "100%" }}>
            {/* Executive KPIs with sparklines */}
            <Row gutter={[16, 16]}>
              <Col xs={24} sm={12} xl={6}>
                <KpiCard
                  title="Receita bruta"
                  icon={<DollarOutlined />}
                  value={formatCurrency(summary?.grossRevenue)}
                  growth={summary?.growth.grossRevenuePct ?? 0}
                  spark={series.gross}
                  color={CHART.ink}
                />
              </Col>
              <Col xs={24} sm={12} xl={6}>
                <KpiCard
                  title="Receita líquida"
                  icon={<LineChartOutlined />}
                  value={formatCurrency(summary?.netRevenue)}
                  growth={summary?.growth.netRevenuePct ?? 0}
                  spark={series.net}
                  color={CHART.green}
                />
              </Col>
              <Col xs={24} sm={12} xl={6}>
                <KpiCard
                  title="Lucro líquido"
                  icon={<RiseOutlined />}
                  value={formatCurrency(summary?.netProfit)}
                  badge={
                    <Tag color="blue">
                      Margem {formatPercent(summary?.contributionMarginPct)}
                    </Tag>
                  }
                  spark={series.net}
                  color={CHART.blue}
                />
              </Col>
              <Col xs={24} sm={12} xl={6}>
                <KpiCard
                  title="Transações pagas"
                  icon={<ThunderboltOutlined />}
                  value={`${summary?.paidTransactions ?? 0}`}
                  badge={
                    <Tag color="geekblue">
                      Conv. {formatPercent(summary?.conversionRatePct)}
                    </Tag>
                  }
                  spark={series.count}
                  color={CHART.inkSoft}
                />
              </Col>
            </Row>

            {/* Health score + scorecard */}
            <Row gutter={[16, 16]}>
              <Col xs={24} lg={8}>
                <Card
                  title={
                    <Space>
                      <HeartOutlined /> Saúde financeira
                    </Space>
                  }
                  style={{ height: "100%" }}
                >
                  <Space
                    direction="vertical"
                    align="center"
                    style={{ width: "100%" }}
                  >
                    <ScoreGauge
                      value={health.score}
                      label={health.label}
                      size={210}
                    />
                    <Tag
                      color={
                        health.score >= 75
                          ? "green"
                          : health.score >= 50
                            ? "gold"
                            : "red"
                      }
                      style={{ marginTop: -8 }}
                    >
                      {health.label}
                    </Tag>
                    <Text
                      type="secondary"
                      style={{ fontSize: 12, textAlign: "center" }}
                    >
                      Índice ponderado de margem, conversão, estornos,
                      inadimplência, diversificação e momentum.
                    </Text>
                  </Space>
                </Card>
              </Col>
              <Col xs={24} lg={16}>
                <Card title="Indicadores-chave" style={{ height: "100%" }}>
                  <Row gutter={[16, 16]}>
                    {health.factors.map((f) => (
                      <Col xs={12} md={8} key={f.label}>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {f.label}
                        </Text>
                        <Progress
                          percent={Math.round(f.score)}
                          size="small"
                          strokeColor={
                            f.score >= 75
                              ? CHART.green
                              : f.score >= 50
                                ? CHART.amber
                                : CHART.red
                          }
                          format={(p) => `${p}`}
                        />
                      </Col>
                    ))}
                  </Row>
                  <Row gutter={[16, 16]} style={{ marginTop: 4 }}>
                    <Col xs={12} md={8}>
                      <Statistic
                        title="Margem líquida"
                        value={metrics.netMarginPct}
                        precision={1}
                        suffix="%"
                        prefix={<PercentageOutlined />}
                      />
                    </Col>
                    <Col xs={12} md={8}>
                      <Statistic
                        title="Carga de tarifas"
                        value={metrics.feeLoadPct}
                        precision={1}
                        suffix="%"
                      />
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        {formatCurrency(summary?.totalFees)}
                      </Text>
                    </Col>
                    <Col xs={12} md={8}>
                      <Statistic
                        title="Taxa de estorno"
                        value={metrics.refundRatePct}
                        precision={1}
                        suffix="%"
                        valueStyle={
                          metrics.refundRatePct >= 5
                            ? { color: CHART.red }
                            : undefined
                        }
                        prefix={
                          metrics.refundRatePct >= 5 ? (
                            <FallOutlined />
                          ) : undefined
                        }
                      />
                    </Col>
                    <Col xs={12} md={8}>
                      <Statistic
                        title="ARPU"
                        value={metrics.arpu}
                        precision={2}
                        prefix={<TeamOutlined />}
                        formatter={(v) => formatCurrency(Number(v))}
                      />
                    </Col>
                    <Col xs={12} md={8}>
                      <Statistic
                        title="Ticket médio"
                        value={summary?.averageTicket ?? 0}
                        precision={2}
                        formatter={(v) => formatCurrency(Number(v))}
                      />
                    </Col>
                    <Col xs={12} md={8}>
                      <Statistic
                        title="Inadimplência"
                        value={receivables?.overdueRatioPct ?? 0}
                        precision={1}
                        suffix="%"
                        valueStyle={
                          (receivables?.overdueRatioPct ?? 0) >= 20
                            ? { color: CHART.red }
                            : undefined
                        }
                      />
                    </Col>
                  </Row>
                </Card>
              </Col>
            </Row>

            {/* Recommendations carousel */}
            <RecommendationsCarousel items={recommendations} />

            {data?.insights && data.insights.length > 0 && (
              <Card
                title="Insights do sistema"
                size="small"
                styles={{ body: { paddingTop: 4, paddingBottom: 4 } }}
              >
                <Row gutter={[16, 0]}>
                  {data.insights.map((insight: Insight, idx: number) => {
                    const visual = INSIGHT_VISUAL[insight.severity];
                    return (
                      <Col xs={24} md={12} key={idx}>
                        <Tooltip title={insight.message}>
                          <div
                            style={{
                              display: "flex",
                              alignItems: "center",
                              gap: 8,
                              padding: "6px 0",
                              cursor: "default",
                            }}
                          >
                            <span
                              style={{
                                color: visual.color,
                                fontSize: 14,
                                lineHeight: 1,
                                flexShrink: 0,
                              }}
                            >
                              {visual.icon}
                            </span>
                            <Text
                              strong
                              style={{
                                fontSize: 13,
                                whiteSpace: "nowrap",
                                flexShrink: 0,
                              }}
                            >
                              {insight.title}
                            </Text>
                            <Text
                              type="secondary"
                              ellipsis
                              style={{ fontSize: 12, flex: 1, minWidth: 0 }}
                            >
                              {insight.message}
                            </Text>
                          </div>
                        </Tooltip>
                      </Col>
                    );
                  })}
                </Row>
              </Card>
            )}

            {/* Revenue trend (full width) */}
            <Card
              title={
                <Space>
                  <FundOutlined /> Evolução da receita
                </Space>
              }
              extra={
                <Space>
                  {chartMetric === "revenue" && (
                    <Button
                      size="small"
                      type={showForecast ? "primary" : "default"}
                      icon={<ThunderboltOutlined />}
                      onClick={() => setShowForecast((s) => !s)}
                    >
                      Projeção
                    </Button>
                  )}
                  <Segmented
                    size="small"
                    value={chartMetric}
                    onChange={(v) => setChartMetric(v as "revenue" | "volume")}
                    options={[
                      {
                        label: "Receita",
                        value: "revenue",
                        icon: <BarChartOutlined />,
                      },
                      {
                        label: "Volume",
                        value: "volume",
                        icon: <LineChartOutlined />,
                      },
                    ]}
                  />
                </Space>
              }
            >
              <Space size="large" wrap style={{ marginBottom: 8 }}>
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
                {showForecast && series.forecast.length > 0 && (
                  <Statistic
                    title={`Projeção (${series.forecast.length} períodos)`}
                    value={metrics.totalGross + series.forecastSum}
                    valueStyle={{ fontSize: 18, color: CHART.green }}
                    formatter={(v) => formatCompactCurrency(Number(v))}
                  />
                )}
                <div>
                  <Text type="secondary" style={{ fontSize: 14 }}>
                    Volatilidade
                  </Text>
                  <div>
                    <Tag
                      color={
                        series.volatility > 60
                          ? "red"
                          : series.volatility > 30
                            ? "gold"
                            : "green"
                      }
                    >
                      {series.volatility.toFixed(0)}%
                    </Tag>
                  </div>
                </div>
              </Space>

              {points.length === 0 ? (
                <Empty description="Sem receita no período" />
              ) : chartMetric === "revenue" ? (
                <>
                  <RevenueTrendChart
                    data={trendData}
                    movingAvg={series.ma}
                    forecast={showForecast ? series.forecast : []}
                    formatValue={(v) => formatCompactCurrency(v)}
                    formatTooltip={(d) => (
                      <>
                        <strong>{d.label}</strong>
                        <br /> Bruto: {formatCurrency(d.gross)}
                        <br /> Líquido: {formatCurrency(d.net)}
                      </>
                    )}
                  />
                  <Space size="middle" wrap style={{ marginTop: 8 }}>
                    <LegendDot
                      color={CHART.netStroke}
                      label="Receita líquida"
                    />
                    <LegendDot
                      color={CHART.grossStroke}
                      label="Receita bruta"
                    />
                    <LegendDot color={CHART.blue} label="Média móvel" dashed />
                    {showForecast && series.forecast.length > 0 && (
                      <LegendDot color={CHART.green} label="Projeção" dashed />
                    )}
                  </Space>
                </>
              ) : (
                <VolumeBarChart data={volumeData} />
              )}

              {seasonality?.hasData && (
                <Text
                  type="secondary"
                  style={{ fontSize: 12, display: "block", marginTop: 10 }}
                >
                  <FieldTimeOutlined /> Melhor dia:{" "}
                  <Text strong>{seasonality.best}</Text> · Menor receita:{" "}
                  {seasonality.worst}
                </Text>
              )}
            </Card>

            {/* Funil + recebíveis + distribuição por status */}
            <Row gutter={[16, 16]}>
              <Col xs={24} lg={8}>
                <Card
                  title="Funil de conversão"
                  size="small"
                  style={{ height: "100%" }}
                >
                  {funnel.length ? (
                    <FunnelBar stages={funnel} />
                  ) : (
                    <Empty description="Sem dados" />
                  )}
                </Card>
              </Col>
              <Col xs={24} lg={8}>
                <Card
                  title="Recebíveis"
                  size="small"
                  style={{ height: "100%" }}
                >
                  <Space
                    direction="vertical"
                    size="middle"
                    style={{ width: "100%" }}
                  >
                    <Statistic
                      title="Total a receber"
                      value={receivables?.totalReceivable ?? 0}
                      formatter={(v) => formatCurrency(Number(v))}
                    />
                    <div>
                      <Text type="secondary">Inadimplência</Text>
                      <Progress
                        percent={Math.min(
                          100,
                          receivables?.overdueRatioPct ?? 0,
                        )}
                        status={
                          (receivables?.overdueRatioPct ?? 0) >= 25
                            ? "exception"
                            : "active"
                        }
                        format={(p) => `${p?.toFixed(1)}%`}
                      />
                      <Text
                        type={
                          (receivables?.overdueRatioPct ?? 0) >= 25
                            ? "danger"
                            : "secondary"
                        }
                        style={{ fontSize: 12 }}
                      >
                        {(receivables?.overdueRatioPct ?? 0) >= 25 && (
                          <WarningOutlined />
                        )}{" "}
                        Vencido: {formatCurrency(receivables?.totalOverdue)} · A
                        vencer: {formatCurrency(receivables?.totalToDue)}
                      </Text>
                    </div>
                  </Space>
                </Card>
              </Col>
              <Col xs={24} lg={8}>
                <Card
                  title="Distribuição por status"
                  style={{ height: "100%" }}
                >
                  <Space
                    direction="vertical"
                    align="center"
                    style={{ width: "100%" }}
                  >
                    <DonutChart
                      data={statusDonut}
                      centerTop={`${summary?.totalTransactions ?? 0}`}
                      centerBottom="transações"
                    />
                    <Space
                      direction="vertical"
                      size={2}
                      style={{ width: "100%" }}
                    >
                      {statusDonut.map((s) => (
                        <Space
                          key={s.label}
                          style={{
                            justifyContent: "space-between",
                            width: "100%",
                          }}
                        >
                          <LegendDot color={s.color} label={s.label} />
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {s.value}
                          </Text>
                        </Space>
                      ))}
                    </Space>
                  </Space>
                </Card>
              </Col>
            </Row>

            {/* Distribuição por status (detalhe) */}
            <Card
              title="Distribuição por status (detalhe)"
              styles={{ body: { padding: 0 } }}
            >
              <Table
                rowKey="status"
                size="small"
                columns={statusColumns}
                dataSource={data?.statusBreakdown ?? []}
                pagination={false}
                locale={{ emptyText: "Sem transações no período." }}
              />
            </Card>

            <Row gutter={[16, 16]}>
              {/* Client ranking */}
              <Col xs={24} lg={14}>
                <Card
                  title="Ranking de clientes por receita"
                  styles={{ body: { padding: 0 } }}
                  extra={
                    <Button
                      size="small"
                      icon={<DownloadOutlined />}
                      onClick={exportRanking}
                    >
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
                    locale={{
                      emptyText: "Nenhum cliente com receita no período.",
                    }}
                  />
                </Card>
              </Col>

              {/* Concentration */}
              <Col xs={24} lg={10}>
                <Card
                  title="Concentração da carteira"
                  style={{ height: "100%" }}
                >
                  <Row gutter={16} align="middle">
                    <Col xs={24} sm={10}>
                      <Space
                        direction="vertical"
                        align="center"
                        style={{ width: "100%" }}
                      >
                        <DonutChart
                          data={clientDonut}
                          size={160}
                          centerTop={formatPercent(concentration.top5, 0)}
                          centerBottom="top 5"
                        />
                      </Space>
                    </Col>
                    <Col xs={24} sm={14}>
                      <Space
                        direction="vertical"
                        size="small"
                        style={{ width: "100%" }}
                      >
                        <Space>
                          <Tag color={concentration.level.color}>
                            {concentration.level.label}
                          </Tag>
                        </Space>
                        <ConcentrationRow
                          label="Maior cliente"
                          value={concentration.top1}
                          danger={concentration.top1 >= 40}
                        />
                        <ConcentrationRow
                          label="Top 3 clientes"
                          value={concentration.top3}
                          danger={concentration.top3 >= 70}
                        />
                        <ConcentrationRow
                          label="Top 5 clientes"
                          value={concentration.top5}
                        />
                        <Space size="large" wrap>
                          <Tooltip title="Índice Herfindahl-Hirschman: quanto maior, mais concentrada a carteira.">
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              HHI{" "}
                              <Text strong>{concentration.hhi.toFixed(0)}</Text>
                            </Text>
                          </Tooltip>
                          <Tooltip title="Coeficiente de Gini da receita por cliente (0 = distribuída, 1 = concentrada).">
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              Gini{" "}
                              <Text strong>
                                {concentration.giniIndex.toFixed(2)}
                              </Text>
                            </Text>
                          </Tooltip>
                          <Tooltip title="Quantidade de clientes que respondem por 80% da receita (princípio de Pareto).">
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              Pareto 80%{" "}
                              <Text strong>{concentration.pareto}</Text>{" "}
                              clientes
                            </Text>
                          </Tooltip>
                        </Space>
                      </Space>
                    </Col>
                  </Row>
                </Card>
              </Col>
            </Row>
          </Space>
        )}
      </Spin>
    </Space>
  );
}

/* ------------------------------------------------------------------ */
/* Presentational helpers                                              */
/* ------------------------------------------------------------------ */

function LegendDot({
  color,
  label,
  dashed,
}: {
  color: string;
  label: string;
  dashed?: boolean;
}) {
  return (
    <Text type="secondary" style={{ fontSize: 12 }}>
      <span
        style={{
          display: "inline-block",
          width: 12,
          height: dashed ? 0 : 10,
          borderTop: dashed ? `2px dashed ${color}` : undefined,
          background: dashed ? undefined : color,
          borderRadius: 2,
          marginRight: 6,
          verticalAlign: "middle",
        }}
      />
      {label}
    </Text>
  );
}

function ConcentrationRow({
  label,
  value,
  danger,
}: {
  label: string;
  value: number;
  danger?: boolean;
}) {
  return (
    <div>
      <Text type="secondary" style={{ fontSize: 12 }}>
        {label}
      </Text>
      <Progress
        percent={Math.min(100, value)}
        format={(p) => `${p?.toFixed(1)}%`}
        status={danger ? "exception" : "normal"}
        size="small"
      />
    </div>
  );
}

function KpiCard({
  title,
  icon,
  value,
  growth,
  badge,
  spark,
  color,
}: {
  title: string;
  icon: React.ReactNode;
  value: string;
  growth?: number;
  badge?: React.ReactNode;
  spark: number[];
  color: string;
}) {
  return (
    <Card style={{ height: "100%" }}>
      <Space
        style={{ justifyContent: "space-between", width: "100%" }}
        align="start"
      >
        <div>
          <Text type="secondary" style={{ fontSize: 13 }}>
            {icon} {title}
          </Text>
          <Title level={3} style={{ margin: "4px 0 0" }}>
            {value}
          </Title>
          <div style={{ marginTop: 8 }}>
            {growth != null ? (
              <Space size={6}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  vs. anterior
                </Text>
                <GrowthTag value={growth} />
              </Space>
            ) : (
              badge
            )}
          </div>
        </div>
        <div style={{ paddingTop: 8 }}>
          <Sparkline values={spark.length ? spark : [0, 0]} color={color} />
        </div>
      </Space>
    </Card>
  );
}

function RecommendationCard({ r }: { r: Recommendation }) {
  return (
    <Card
      size="small"
      style={{
        height: "100%",
        borderLeft: `3px solid ${recBorder(r.priority)}`,
      }}
    >
      <Space direction="vertical" size={4} style={{ width: "100%" }}>
        <Space style={{ justifyContent: "space-between", width: "100%" }} wrap>
          <Text strong>{r.title}</Text>
          <Tag color={REC_META[r.priority].color}>
            {REC_META[r.priority].label}
          </Tag>
        </Space>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {r.message}
        </Text>
        <Text style={{ fontSize: 12 }}>
          <BulbOutlined style={{ color: CHART.amber, marginRight: 6 }} />
          {r.action}
        </Text>
      </Space>
    </Card>
  );
}

function RecommendationsCarousel({ items }: { items: Recommendation[] }) {
  const screens = Grid.useBreakpoint();
  const perView = screens.lg ? 2 : 1;
  const pages: Recommendation[][] = [];
  for (let i = 0; i < items.length; i += perView)
    pages.push(items.slice(i, i + perView));
  const pageCount = Math.max(1, pages.length);
  const [page, setPage] = useState(0);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (page > pageCount - 1) setPage(0);
  }, [pageCount, page]);

  useEffect(() => {
    if (paused || pageCount <= 1) return;
    const id = setInterval(() => setPage((p) => (p + 1) % pageCount), 6000);
    return () => clearInterval(id);
  }, [paused, pageCount]);

  if (items.length === 0) return null;

  return (
    <Card
      title={
        <Space>
          <BulbOutlined /> Recomendações
        </Space>
      }
      size="small"
      extra={
        pageCount > 1 ? (
          <Space size={4}>
            <Button
              size="small"
              type="text"
              icon={<LeftOutlined />}
              onClick={() => setPage((p) => (p - 1 + pageCount) % pageCount)}
            />
            <Text type="secondary" style={{ fontSize: 12 }}>
              {page + 1}/{pageCount}
            </Text>
            <Button
              size="small"
              type="text"
              icon={<RightOutlined />}
              onClick={() => setPage((p) => (p + 1) % pageCount)}
            />
          </Space>
        ) : null
      }
    >
      <div
        onMouseEnter={() => setPaused(true)}
        onMouseLeave={() => setPaused(false)}
        style={{ overflow: "hidden" }}
      >
        <div
          style={{
            display: "flex",
            transition: "transform .4s ease",
            transform: `translateX(-${page * 100}%)`,
          }}
        >
          {pages.map((group, gi) => (
            <div
              key={gi}
              style={{
                flex: "0 0 100%",
                display: "flex",
                gap: 16,
                alignItems: "stretch",
              }}
            >
              {group.map((r, idx) => (
                <div key={idx} style={{ flex: 1, minWidth: 0 }}>
                  <RecommendationCard r={r} />
                </div>
              ))}
              {group.length < perView &&
                Array.from({ length: perView - group.length }).map((_, k) => (
                  <div key={`f-${k}`} style={{ flex: 1 }} />
                ))}
            </div>
          ))}
        </div>
      </div>
      {pageCount > 1 && (
        <div
          style={{
            display: "flex",
            justifyContent: "center",
            gap: 6,
            marginTop: 12,
          }}
        >
          {pages.map((_, i) => (
            <span
              key={i}
              onClick={() => setPage(i)}
              style={{
                width: i === page ? 18 : 6,
                height: 6,
                borderRadius: 3,
                background: i === page ? CHART.ink : "#d4d4d4",
                cursor: "pointer",
                transition: "all .3s",
              }}
            />
          ))}
        </div>
      )}
    </Card>
  );
}
