/**
 * Pure analytics helpers: forecasting, distribution statistics, a composite
 * financial health score and a grounded recommendation engine. No UI here so
 * the logic stays testable and reusable.
 */

export type RecPriority = "HIGH" | "MEDIUM" | "LOW" | "POSITIVE";

export interface Recommendation {
  priority: RecPriority;
  category: string;
  title: string;
  message: string;
  action: string;
}

export interface HealthFactor {
  label: string;
  score: number; // 0..100
  weight: number; // 0..1
}

export interface HealthResult {
  score: number;
  label: "Saudável" | "Atenção" | "Crítico";
  factors: HealthFactor[];
}

const clamp = (v: number, min = 0, max = 100) =>
  Math.max(min, Math.min(max, v));

/** Ordinary least squares forecast of the next `steps` values. */
export function linearForecast(values: number[], steps: number): number[] {
  const n = values.length;
  if (n === 0) return Array(steps).fill(0);
  if (n < 2) return Array(steps).fill(Math.max(0, values[0]));
  let sx = 0;
  let sy = 0;
  let sxx = 0;
  let sxy = 0;
  for (let i = 0; i < n; i++) {
    sx += i;
    sy += values[i];
    sxx += i * i;
    sxy += i * values[i];
  }
  const denom = n * sxx - sx * sx;
  const slope = denom ? (n * sxy - sx * sy) / denom : 0;
  const intercept = (sy - slope * sx) / n;
  const out: number[] = [];
  for (let k = 1; k <= steps; k++)
    out.push(Math.max(0, intercept + slope * (n - 1 + k)));
  return out;
}

/** Trailing simple moving average; first (window-1) items are null. */
export function movingAverage(
  values: number[],
  window: number,
): (number | null)[] {
  return values.map((_, i) => {
    if (i < window - 1) return null;
    let sum = 0;
    for (let j = i - window + 1; j <= i; j++) sum += values[j];
    return sum / window;
  });
}

export function stdDev(values: number[]): number {
  const n = values.length;
  if (!n) return 0;
  const mean = values.reduce((a, b) => a + b, 0) / n;
  const variance = values.reduce((a, b) => a + (b - mean) ** 2, 0) / n;
  return Math.sqrt(variance);
}

/** Coefficient of variation (%) — volatility relative to the mean. */
export function coefficientOfVariation(values: number[]): number {
  const n = values.length;
  if (!n) return 0;
  const mean = values.reduce((a, b) => a + b, 0) / n;
  if (mean === 0) return 0;
  return (stdDev(values) / mean) * 100;
}

/** Gini coefficient (0 = perfectly even, 1 = fully concentrated). */
export function gini(values: number[]): number {
  const v = values.filter((x) => x >= 0).sort((a, b) => a - b);
  const n = v.length;
  if (!n) return 0;
  const sum = v.reduce((a, b) => a + b, 0);
  if (sum === 0) return 0;
  let cum = 0;
  for (let i = 0; i < n; i++) cum += (i + 1) * v[i];
  return (2 * cum) / (n * sum) - (n + 1) / n;
}

/** Minimum number of items (sorted by share) needed to reach `target`% of total. */
export function paretoCount(shares: number[], target = 80): number {
  const sorted = [...shares].sort((a, b) => b - a);
  let acc = 0;
  let count = 0;
  for (const s of sorted) {
    acc += s;
    count++;
    if (acc >= target) break;
  }
  return count;
}

export interface HealthInput {
  netMarginPct: number;
  conversionRatePct: number;
  refundRatePct: number;
  overdueRatioPct: number;
  top3SharePct: number;
  momentumPct: number;
}

export function computeHealthScore(i: HealthInput): HealthResult {
  const factors: HealthFactor[] = [
    {
      label: "Margem líquida",
      score: clamp((i.netMarginPct / 60) * 100),
      weight: 0.24,
    },
    {
      label: "Conversão",
      score: clamp((i.conversionRatePct / 60) * 100),
      weight: 0.2,
    },
    {
      label: "Estornos",
      score: clamp(100 - i.refundRatePct * 10),
      weight: 0.14,
    },
    {
      label: "Inadimplência",
      score: clamp(100 - i.overdueRatioPct * 2.5),
      weight: 0.16,
    },
    {
      label: "Diversificação",
      score: clamp(100 - i.top3SharePct),
      weight: 0.11,
    },
    { label: "Momentum", score: clamp(50 + i.momentumPct * 2), weight: 0.15 },
  ];
  const score = clamp(factors.reduce((a, f) => a + f.score * f.weight, 0));
  const label = score >= 75 ? "Saudável" : score >= 50 ? "Atenção" : "Crítico";
  return { score, label, factors };
}

export interface RecommendationInput {
  netMarginPct: number;
  feeLoadPct: number;
  refundRatePct: number;
  conversionRatePct: number;
  overdueRatioPct: number;
  top3SharePct: number;
  top1SharePct: number;
  momentumPct: number;
  forecastDeltaPct: number;
  payingClients: number;
  unpaidTx: number;
  totalOverdue: number;
  totalFees: number;
  fmtCurrency: (v: number) => string;
}

const PRIORITY_RANK: Record<RecPriority, number> = {
  HIGH: 0,
  MEDIUM: 1,
  LOW: 2,
  POSITIVE: 3,
};

/** Threshold-based, evidence-backed recommendations. */
export function buildRecommendations(i: RecommendationInput): Recommendation[] {
  const recs: Recommendation[] = [];
  const pct = (v: number) => `${v.toFixed(1)}%`;

  if (i.overdueRatioPct >= 20) {
    recs.push({
      priority: "HIGH",
      category: "Inadimplência",
      title: "Inadimplência acima do saudável",
      message: `${pct(i.overdueRatioPct)} dos recebíveis estão vencidos (${i.fmtCurrency(i.totalOverdue)}). Acima de 20% o fluxo de caixa fica pressionado.`,
      action:
        "Ative régua de cobrança automática (lembretes D-3, D0, D+3) e ofereça PIX para quitação imediata.",
    });
  } else if (i.overdueRatioPct >= 10) {
    recs.push({
      priority: "MEDIUM",
      category: "Inadimplência",
      title: "Inadimplência em atenção",
      message: `${pct(i.overdueRatioPct)} dos recebíveis vencidos (${i.fmtCurrency(i.totalOverdue)}).`,
      action:
        "Reforce lembretes antes do vencimento e monitore os maiores valores em aberto.",
    });
  }

  if (i.conversionRatePct < 50) {
    recs.push({
      priority: "HIGH",
      category: "Conversão",
      title: "Conversão baixa de cobranças",
      message: `Apenas ${pct(i.conversionRatePct)} das cobranças emitidas são pagas; ${i.unpaidTx} ficaram em aberto no período.`,
      action:
        "Reduza prazo de expiração, priorize PIX no checkout e dispare lembrete automático em cobranças pendentes.",
    });
  } else if (i.conversionRatePct < 70) {
    recs.push({
      priority: "MEDIUM",
      category: "Conversão",
      title: "Há espaço para elevar a conversão",
      message: `Conversão de ${pct(i.conversionRatePct)}. Pequenos ganhos aqui têm efeito direto na receita.`,
      action: "Teste lembretes e simplifique o checkout para reduzir abandono.",
    });
  }

  if (i.refundRatePct >= 5) {
    recs.push({
      priority: "HIGH",
      category: "Estornos",
      title: "Taxa de estorno elevada",
      message: `${pct(i.refundRatePct)} da receita foi estornada. Acima de 5% sinaliza disputa, fraude ou insatisfação.`,
      action:
        "Investigue os principais motivos de estorno e revise a política de reembolso e a comunicação de cobrança.",
    });
  }

  if (i.feeLoadPct >= 8) {
    recs.push({
      priority: "MEDIUM",
      category: "Custos",
      title: "Carga de tarifas alta",
      message: `Tarifas consomem ${pct(i.feeLoadPct)} da receita bruta (${i.fmtCurrency(i.totalFees)}).`,
      action:
        "Direcione mais volume para PIX (tarifa menor) e renegocie taxas com os provedores de maior volume.",
    });
  }

  if (i.top3SharePct >= 60 || i.top1SharePct >= 35) {
    recs.push({
      priority: "HIGH",
      category: "Carteira",
      title: "Receita concentrada em poucos clientes",
      message: `Os 3 maiores clientes representam ${pct(i.top3SharePct)} da receita (maior cliente: ${pct(i.top1SharePct)}). A perda de um deles teria impacto severo.`,
      action:
        "Crie ações de aquisição para diluir a dependência e fortaleça o relacionamento com as contas-chave.",
    });
  }

  if (i.momentumPct <= -10) {
    recs.push({
      priority: "HIGH",
      category: "Tendência",
      title: "Queda de receita no período",
      message: `A receita da 2ª metade do período caiu ${pct(Math.abs(i.momentumPct))} frente à 1ª metade.`,
      action:
        "Reative clientes inativos com ofertas direcionadas e revise causas (sazonalidade, churn, preço).",
    });
  }

  if (i.netMarginPct < 50 && i.netMarginPct > 0) {
    recs.push({
      priority: "MEDIUM",
      category: "Rentabilidade",
      title: "Margem líquida comprimida",
      message: `Margem líquida de ${pct(i.netMarginPct)}. Custos (tarifas/estornos) estão pesando no resultado.`,
      action:
        "Combine redução de tarifas com aumento de ticket médio para recompor a margem.",
    });
  }

  if (i.payingClients > 0 && i.payingClients < 5) {
    recs.push({
      priority: "MEDIUM",
      category: "Crescimento",
      title: "Base de clientes pagantes pequena",
      message: `Somente ${i.payingClients} clientes pagantes no período.`,
      action:
        "Invista em aquisição e em integrações via API para escalar a emissão de cobranças.",
    });
  }

  if (i.momentumPct >= 15 || i.forecastDeltaPct >= 10) {
    recs.push({
      priority: "POSITIVE",
      category: "Tendência",
      title: "Tendência de crescimento",
      message: `Momentum positivo (${pct(i.momentumPct)}) e projeção em alta (${pct(i.forecastDeltaPct)}).`,
      action:
        "Garanta capacidade operacional e amplie limites/contas bancárias para sustentar o crescimento.",
    });
  }

  if (recs.length === 0) {
    recs.push({
      priority: "POSITIVE",
      category: "Saúde",
      title: "Indicadores saudáveis",
      message:
        "Nenhum risco relevante detectado: conversão, margem, estornos e concentração estão em níveis adequados.",
      action: "Mantenha o monitoramento e foque em crescimento sustentável.",
    });
  }

  return recs.sort(
    (a, b) => PRIORITY_RANK[a.priority] - PRIORITY_RANK[b.priority],
  );
}
