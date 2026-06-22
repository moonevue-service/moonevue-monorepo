"use client";

import { useEffect, useState } from "react";
import { Button, Segmented, Tag } from "antd";
import {
  ApiOutlined,
  ArrowRightOutlined,
  BankOutlined,
  BarChartOutlined,
  CheckCircleOutlined,
  CreditCardOutlined,
  LineChartOutlined,
  RocketOutlined,
  SafetyOutlined,
  TeamOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";

const container: React.CSSProperties = {
  maxWidth: 1200,
  margin: "0 auto",
  padding: "0 24px",
};
const INK = "#0a0a0a";
const TEXT = "#141414";
const MUTED = "#595959";
const BORDER = "#ececec";

type MethodKey = "PIX_IMMEDIATE" | "PIX_DUE" | "BOLETO";

const METHODS: Record<
  MethodKey,
  {
    label: string;
    amount: string;
    status: string;
    tag: string;
    desc: string;
    series: number[];
    color: string;
  }
> = {
  PIX_IMMEDIATE: {
    label: "PIX imediato",
    amount: "R$ 149,90",
    status: "Confirmado",
    tag: "green",
    desc: "Liquidação na hora via QR Code.",
    series: [12, 18, 15, 24, 22, 30, 36],
    color: "#16a34a",
  },
  PIX_DUE: {
    label: "PIX com vencimento",
    amount: "R$ 2.480,00",
    status: "Aguardando",
    tag: "gold",
    desc: "Cobrança com data, multa e juros.",
    series: [8, 11, 14, 13, 21, 24, 31],
    color: "#d97706",
  },
  BOLETO: {
    label: "Boleto bancário",
    amount: "R$ 890,00",
    status: "Registrado",
    tag: "blue",
    desc: "Linha digitável e PDF automáticos.",
    series: [6, 9, 8, 13, 16, 19, 23],
    color: "#2563eb",
  },
};

const CHIPS = [
  "PIX imediato",
  "PIX com vencimento",
  "Boleto bancário",
  "Checkout hospedado",
  "API pública",
  "Webhooks assinados",
  "Idempotência",
  "Multi-tenant",
  "RBAC",
  "Analytics em tempo real",
  "Projeções",
  "Recomendações",
];

const FEATURES = [
  {
    icon: ThunderboltOutlined,
    title: "Cobranças versáteis",
    text: "PIX imediato, PIX com vencimento e boleto através de uma única API consistente.",
  },
  {
    icon: CreditCardOutlined,
    title: "Checkout hospedado",
    text: "Página de pagamento pronta e responsiva, com link compartilhável por cliente.",
  },
  {
    icon: BankOutlined,
    title: "Integrações bancárias",
    text: "Conecte provedores como o ASAAS e gerencie contas e credenciais com segurança.",
  },
  {
    icon: ApiOutlined,
    title: "API pública & Webhooks",
    text: "API Keys com escopo, idempotência, rate limit e eventos assinados por HMAC.",
  },
  {
    icon: BarChartOutlined,
    title: "Analytics inteligente",
    text: "Indicadores executivos, projeções de receita e recomendações acionáveis.",
  },
  {
    icon: SafetyOutlined,
    title: "Multi-tenant & RBAC",
    text: "Isolamento por tenant e controle de acesso por papel para toda a operação.",
  },
];

const STEPS = [
  {
    icon: BankOutlined,
    title: "Conecte seu banco",
    text: "Cadastre credenciais e contas bancárias em poucos minutos.",
  },
  {
    icon: ThunderboltOutlined,
    title: "Crie cobranças",
    text: "Gere PIX ou boletos pelo painel ou diretamente pela API.",
  },
  {
    icon: LineChartOutlined,
    title: "Acompanhe tudo",
    text: "Status, transações e analytics consolidados em um só lugar.",
  },
];

function MiniChart({ series, color }: { series: number[]; color: string }) {
  const w = 300;
  const h = 92;
  const pad = 8;
  const max = Math.max(...series);
  const stepX = (w - pad * 2) / (series.length - 1);
  const pts = series.map((v, i) => {
    const x = pad + i * stepX;
    const y = h - pad - (v / max) * (h - pad * 2);
    return [x, y] as const;
  });
  const line = pts
    .map((p, i) => `${i ? "L" : "M"}${p[0].toFixed(1)},${p[1].toFixed(1)}`)
    .join(" ");
  const last = pts[pts.length - 1];
  const area = `${line} L${last[0].toFixed(1)},${h - pad} L${pts[0][0].toFixed(1)},${h - pad} Z`;
  return (
    <svg
      viewBox={`0 0 ${w} ${h}`}
      width="100%"
      height={h}
      key={color + series.join()}
      aria-hidden
    >
      <defs>
        <linearGradient id="mv-area" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity={0.2} />
          <stop offset="100%" stopColor={color} stopOpacity={0} />
        </linearGradient>
      </defs>
      <path d={area} fill="url(#mv-area)" />
      <path
        className="mv-draw-line"
        pathLength={1}
        d={line}
        fill="none"
        stroke={color}
        strokeWidth={2.2}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx={last[0]} cy={last[1]} r={3.5} fill={color} />
    </svg>
  );
}

function DashboardPreview() {
  const [method, setMethod] = useState<MethodKey>("PIX_IMMEDIATE");
  const m = METHODS[method];
  return (
    <div
      className="mv-float"
      style={{
        background: "#fff",
        border: `1px solid ${BORDER}`,
        borderRadius: 16,
        boxShadow: "0 24px 60px -32px rgba(0,0,0,0.35)",
        padding: 20,
        display: "flex",
        flexDirection: "column",
        gap: 16,
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span
            style={{
              width: 10,
              height: 10,
              borderRadius: 99,
              background: "#ff5f57",
            }}
          />
          <span
            style={{
              width: 10,
              height: 10,
              borderRadius: 99,
              background: "#febc2e",
            }}
          />
          <span
            style={{
              width: 10,
              height: 10,
              borderRadius: 99,
              background: "#28c840",
            }}
          />
        </div>
        <Tag color="green" style={{ marginInlineEnd: 0 }}>
          Ambiente sandbox
        </Tag>
      </div>

      <Segmented
        block
        value={method}
        onChange={(v) => setMethod(v as MethodKey)}
        options={[
          { label: "PIX", value: "PIX_IMMEDIATE" },
          { label: "PIX venc.", value: "PIX_DUE" },
          { label: "Boleto", value: "BOLETO" },
        ]}
      />

      <div>
        <div style={{ fontSize: 13, color: MUTED }}>{m.label}</div>
        <div
          style={{
            display: "flex",
            alignItems: "baseline",
            gap: 10,
            marginTop: 2,
          }}
        >
          <span style={{ fontSize: 30, fontWeight: 600, color: TEXT }}>
            {m.amount}
          </span>
          <Tag color={m.tag} style={{ marginInlineEnd: 0 }}>
            {m.status}
          </Tag>
        </div>
        <div style={{ fontSize: 13, color: MUTED, marginTop: 4 }}>{m.desc}</div>
      </div>

      <MiniChart series={m.series} color={m.color} />

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        {[
          { k: "Recebido (7d)", v: "R$ 38,2 mil" },
          { k: "Conversão", v: "92,4%" },
        ].map((kpi) => (
          <div
            key={kpi.k}
            style={{
              background: "#fafafa",
              border: `1px solid ${BORDER}`,
              borderRadius: 10,
              padding: "10px 12px",
            }}
          >
            <div style={{ fontSize: 12, color: MUTED }}>{kpi.k}</div>
            <div style={{ fontSize: 16, fontWeight: 600, color: TEXT }}>
              {kpi.v}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

const TYPING_WORDS = ["PIX", "Boleto", "Cartão"];

function TypingWord() {
  const [index, setIndex] = useState(0);
  const [sub, setSub] = useState("");
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    const current = TYPING_WORDS[index % TYPING_WORDS.length];
    let delay = deleting ? 55 : 95;
    if (!deleting && sub === current) delay = 1400;
    else if (deleting && sub === "") delay = 350;

    const timer = setTimeout(() => {
      if (!deleting && sub === current) {
        setDeleting(true);
      } else if (deleting && sub === "") {
        setDeleting(false);
        setIndex((i) => (i + 1) % TYPING_WORDS.length);
      } else {
        setSub((prev) =>
          deleting
            ? current.slice(0, prev.length - 1)
            : current.slice(0, prev.length + 1),
        );
      }
    }, delay);
    return () => clearTimeout(timer);
  }, [sub, deleting, index]);

  return (
    <span
      style={{
        display: "block",
        whiteSpace: "nowrap",
        minHeight: "1.12em",
      }}
    >
      {sub}
      <span className="mv-caret" aria-hidden />
    </span>
  );
}

function SectionTag({ children }: { children: React.ReactNode }) {
  return (
    <span
      style={{
        display: "inline-block",
        alignSelf: "flex-start",
        width: "fit-content",
        background: "#f5f5f5",
        color: INK,
        padding: "4px 12px",
        borderRadius: 99,
        fontSize: 12,
        fontWeight: 600,
        letterSpacing: 0.3,
        textTransform: "uppercase",
      }}
    >
      {children}
    </span>
  );
}

export default function Hero() {
  return (
    <main>
      {/* Hero */}
      <section style={{ ...container, paddingTop: 56, paddingBottom: 56 }}>
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
            gap: 48,
            alignItems: "center",
          }}
        >
          <div
            className="mv-fade-up"
            style={{
              display: "flex",
              flexDirection: "column",
              gap: 22,
              maxWidth: 560,
            }}
          >
            <SectionTag>Plataforma de pagamentos SaaS</SectionTag>
            <h1
              style={{
                fontSize: "clamp(34px, 5vw, 56px)",
                fontWeight: 600,
                lineHeight: 1.12,
                color: TEXT,
                margin: 0,
                letterSpacing: -0.5,
              }}
            >
              Uma operação financeira inteira para receber por <TypingWord />
            </h1>
            <p
              style={{ fontSize: 17, color: MUTED, margin: 0, lineHeight: 1.6 }}
            >
              Crie cobranças, conecte bancos, ofereça checkout e acompanhe tudo
              com analytics em tempo real, pelo painel ou por uma API pública
              pronta para escalar.
            </p>
            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
              <Button
                type="primary"
                size="large"
                href="/register"
                icon={<RocketOutlined />}
              >
                Começar agora
              </Button>
              <Button
                size="large"
                href="/login"
                icon={<ArrowRightOutlined />}
                iconPosition="end"
              >
                Entrar no painel
              </Button>
            </div>
            <div
              style={{
                display: "flex",
                flexWrap: "wrap",
                gap: 16,
                color: MUTED,
                fontSize: 13,
              }}
            >
              {["PIX & Boleto", "API pública", "Webhooks", "Multi-tenant"].map(
                (t) => (
                  <span
                    key={t}
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      gap: 6,
                    }}
                  >
                    <CheckCircleOutlined style={{ color: "#16a34a" }} />
                    {t}
                  </span>
                ),
              )}
            </div>
          </div>

          <div className="mv-fade-up" style={{ animationDelay: "0.12s" }}>
            <DashboardPreview />
          </div>
        </div>
      </section>

      {/* Capabilities marquee */}
      <section
        style={{
          borderTop: `1px solid ${BORDER}`,
          borderBottom: `1px solid ${BORDER}`,
          padding: "18px 0",
          background: "#fff",
        }}
      >
        <div className="mv-marquee-mask">
          <div className="mv-marquee-track">
            {[...CHIPS, ...CHIPS].map((chip, i) => (
              <span
                key={i}
                style={{
                  flexShrink: 0,
                  margin: "0 10px",
                  padding: "6px 16px",
                  border: `1px solid ${BORDER}`,
                  borderRadius: 99,
                  fontSize: 13,
                  color: TEXT,
                  whiteSpace: "nowrap",
                  background: "#fafafa",
                }}
              >
                {chip}
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* Possibilidades */}
      <section
        id="possibilidades"
        style={{ ...container, paddingTop: 72, paddingBottom: 24 }}
      >
        <div style={{ maxWidth: 640, marginBottom: 36 }}>
          <SectionTag>Possibilidades</SectionTag>
          <h2
            style={{
              fontSize: "clamp(26px, 3.5vw, 38px)",
              fontWeight: 600,
              color: TEXT,
              margin: "16px 0 8px",
              letterSpacing: -0.3,
            }}
          >
            Tudo que sua operação de cobrança precisa
          </h2>
          <p style={{ color: MUTED, fontSize: 16, margin: 0, lineHeight: 1.6 }}>
            Recursos pensados para crescer com você, do primeiro PIX à automação
            completa via API.
          </p>
        </div>
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))",
            gap: 16,
          }}
        >
          {FEATURES.map((f, i) => {
            const Icon = f.icon;
            return (
              <div
                key={f.title}
                className="mv-card-hover mv-fade-up"
                style={{
                  animationDelay: `${0.05 * i}s`,
                  border: `1px solid ${BORDER}`,
                  borderRadius: 14,
                  padding: 22,
                  background: "#fff",
                  display: "flex",
                  flexDirection: "column",
                  gap: 12,
                }}
              >
                <span
                  style={{
                    display: "inline-flex",
                    background: INK,
                    color: "#fff",
                    borderRadius: 10,
                    padding: 11,
                    width: "fit-content",
                  }}
                >
                  <Icon style={{ fontSize: 18 }} />
                </span>
                <strong style={{ fontSize: 16, color: TEXT }}>{f.title}</strong>
                <p
                  style={{
                    color: MUTED,
                    fontSize: 14,
                    margin: 0,
                    lineHeight: 1.55,
                  }}
                >
                  {f.text}
                </p>
              </div>
            );
          })}
        </div>
      </section>

      {/* Como funciona */}
      <section
        id="como-funciona"
        style={{ ...container, paddingTop: 72, paddingBottom: 24 }}
      >
        <div style={{ maxWidth: 640, marginBottom: 36 }}>
          <SectionTag>Como funciona</SectionTag>
          <h2
            style={{
              fontSize: "clamp(26px, 3.5vw, 38px)",
              fontWeight: 600,
              color: TEXT,
              margin: "16px 0 8px",
              letterSpacing: -0.3,
            }}
          >
            Da configuração à primeira cobrança em minutos
          </h2>
        </div>
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))",
            gap: 16,
          }}
        >
          {STEPS.map((s, i) => {
            const Icon = s.icon;
            return (
              <div
                key={s.title}
                style={{
                  border: `1px solid ${BORDER}`,
                  borderRadius: 14,
                  padding: 24,
                  background: "#fff",
                  position: "relative",
                }}
              >
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    marginBottom: 14,
                  }}
                >
                  <span
                    style={{
                      display: "inline-flex",
                      background: "#f5f5f5",
                      color: INK,
                      borderRadius: 10,
                      padding: 11,
                    }}
                  >
                    <Icon style={{ fontSize: 18 }} />
                  </span>
                  <span
                    style={{ fontSize: 30, fontWeight: 700, color: "#ededed" }}
                  >
                    0{i + 1}
                  </span>
                </div>
                <strong style={{ fontSize: 16, color: TEXT }}>{s.title}</strong>
                <p
                  style={{
                    color: MUTED,
                    fontSize: 14,
                    margin: "8px 0 0",
                    lineHeight: 1.55,
                  }}
                >
                  {s.text}
                </p>
              </div>
            );
          })}
        </div>
      </section>

      {/* Desenvolvedores */}
      <section
        id="desenvolvedores"
        style={{ ...container, paddingTop: 72, paddingBottom: 24 }}
      >
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
            gap: 40,
            alignItems: "center",
          }}
        >
          <div
            style={{
              maxWidth: 520,
              display: "flex",
              flexDirection: "column",
              gap: 18,
            }}
          >
            <SectionTag>Para desenvolvedores</SectionTag>
            <h2
              style={{
                fontSize: "clamp(26px, 3.5vw, 38px)",
                fontWeight: 600,
                color: TEXT,
                margin: 0,
                letterSpacing: -0.3,
              }}
            >
              Uma API pensada para integrar rápido
            </h2>
            <p
              style={{ color: MUTED, fontSize: 16, margin: 0, lineHeight: 1.6 }}
            >
              Autentique com API Keys, garanta segurança com idempotência e
              receba eventos em tempo real com webhooks assinados.
            </p>
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {[
                "API Keys com escopo e rotação de segredo",
                "Idempotency-Key para requisições seguras",
                "Rate limit configurável por minuto",
                "Webhooks com assinatura HMAC",
              ].map((item) => (
                <span
                  key={item}
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 10,
                    color: TEXT,
                    fontSize: 14,
                  }}
                >
                  <CheckCircleOutlined style={{ color: "#16a34a" }} />
                  {item}
                </span>
              ))}
            </div>
            <div>
              <Button
                href="/login"
                icon={<ArrowRightOutlined />}
                iconPosition="end"
              >
                Ver documentação
              </Button>
            </div>
          </div>

          <div
            style={{
              background: "#0a0a0a",
              borderRadius: 14,
              overflow: "hidden",
              border: "1px solid #1f1f1f",
              boxShadow: "0 24px 60px -36px rgba(0,0,0,0.6)",
            }}
          >
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: 8,
                padding: "12px 16px",
                borderBottom: "1px solid #1f1f1f",
              }}
            >
              <span
                style={{
                  width: 10,
                  height: 10,
                  borderRadius: 99,
                  background: "#ff5f57",
                }}
              />
              <span
                style={{
                  width: 10,
                  height: 10,
                  borderRadius: 99,
                  background: "#febc2e",
                }}
              />
              <span
                style={{
                  width: 10,
                  height: 10,
                  borderRadius: 99,
                  background: "#28c840",
                }}
              />
              <span style={{ marginLeft: 8, color: "#8c8c8c", fontSize: 12 }}>
                POST /api/v1/charges
              </span>
            </div>
            <pre
              style={{
                margin: 0,
                padding: "18px 20px",
                color: "#e5e5e5",
                fontSize: 12.5,
                lineHeight: 1.7,
                overflowX: "auto",
                fontFamily:
                  "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
              }}
            >
              {`curl -X POST https://api.moonevue.com/api/v1/charges \\
  -H "Authorization: Bearer mvk_live_••••" \\
  -H "Idempotency-Key: <uuid>" \\
  -H "Content-Type: application/json" \\
  -d '{
    "method": "PIX_IMMEDIATE",
    "amount": 149.90,
    "description": "Pedido #9988",
    "customer": {
      "name": "Maria Souza",
      "email": "maria@exemplo.com"
    }
  }'`}
            </pre>
          </div>
        </div>
      </section>

      {/* Final CTA */}
      <section style={{ ...container, paddingTop: 72, paddingBottom: 96 }}>
        <div
          style={{
            background: INK,
            borderRadius: 20,
            padding: "clamp(32px, 5vw, 56px)",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            textAlign: "center",
            gap: 18,
          }}
        >
          <h2
            style={{
              fontSize: "clamp(26px, 4vw, 40px)",
              fontWeight: 600,
              color: "#fff",
              margin: 0,
              letterSpacing: -0.3,
              maxWidth: 620,
            }}
          >
            Comece a receber hoje mesmo
          </h2>
          <p
            style={{
              color: "#bfbfbf",
              fontSize: 16,
              margin: 0,
              maxWidth: 520,
              lineHeight: 1.6,
            }}
          >
            Crie sua conta, conecte um banco e gere a primeira cobrança em
            poucos minutos.
          </p>
          <div
            style={{
              display: "flex",
              gap: 12,
              flexWrap: "wrap",
              justifyContent: "center",
              marginTop: 4,
            }}
          >
            <Button
              type="primary"
              size="large"
              href="/register"
              icon={<RocketOutlined />}
            >
              Criar conta gratuita
            </Button>
            <Button
              size="large"
              href="/client-area"
              ghost
              icon={<TeamOutlined />}
            >
              Acompanhar cobrança
            </Button>
          </div>
        </div>
      </section>
    </main>
  );
}
