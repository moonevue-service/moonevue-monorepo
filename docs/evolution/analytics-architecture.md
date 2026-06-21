# Analytics Corporativo — Arquitetura Enterprise

> Módulo de Business Intelligence que usa o **Finance** como backend centralizador de
> dados, regras de negócio e serviços. Transforma os dados transacionais em
> inteligência acionável para gestores, diretores e executivos.

---

## 1. Visão geral da arquitetura

O Analytics **não** cria um banco paralelo: ele lê o *system of record* do Finance
(tabela `transactions` e dimensões `clients`, `bank_accounts`) através de uma camada
analítica de leitura, otimizada com índices de cobertura, agregações no banco e cache
de curta duração para dashboards em tempo quase real.

```mermaid
flowchart LR
    subgraph FE[Frontend Next.js]
      DASH[Dashboard Analytics<br/>KPIs · gráficos · insights]
    end
    subgraph FIN[Finance Service :8082]
      CTRL[AnalyticsController<br/>/api/tenant/{id}/analytics/**]
      SVC[AnalyticsService<br/>regras · growth · margem]
      INS[InsightEngine<br/>regras de negócio]
      CACHE[(Caffeine cache<br/>TTL 60s)]
      REPO[AnalyticsRepository<br/>agregações SQL]
    end
    subgraph DB[(PostgreSQL)]
      TX[transactions]
      CL[clients]
      BA[bank_accounts]
      MV[(mv_tx_daily_revenue<br/>materialized view)]
    end
    DASH -->|cookie sid| CTRL --> SVC --> REPO --> TX
    SVC --> INS
    SVC <--> CACHE
    REPO --> CL & BA
    MV -. refresh agendado .- TX
```

### Princípios

| Princípio | Decisão |
|-----------|---------|
| **Finance como fonte única** | Toda métrica deriva de `transactions` + dimensões. Sem ETL duplicado nesta fase. |
| **Push-down de agregação** | `SUM/COUNT/GROUP BY` executados no Postgres, não na JVM. |
| **Multi-tenant first** | Todo acesso filtra por `tenant_id` (mesmo modelo de RBAC do Finance). |
| **Tempo quase real** | Consulta direta às tabelas + cache curto (60s). Histórico pesado → materialized views. |
| **Modular e evolutivo** | Pacote isolado `com.moonevue.finance.analytics`, pronto para virar microserviço próprio. |
| **Preparado para múltiplas fontes** | A camada de repositório é uma *porta*; novas fontes (CRM, ERP) entram como novos adapters. |

---

## 2. Modelagem de dados

### 2.1 Fontes atuais (Finance / core)

- **`transactions`** — fato principal. Campos relevantes para analytics:
  `amount`, `net_amount`, `fee_amount`, `type` (CHARGE/REFUND/PAYOUT/TRANSFER/FEE),
  `status` (PAID/SETTLED/CAPTURED/PENDING/...), `created_at`, `paid_at`, `due_date`,
  `tenant_id`, `account_id`, `client_id`.
- **`clients`** — dimensão cliente (`name`, `status`, `cpf_cnpj`).
- **`bank_accounts`** — dimensão conta / unidade.

### 2.2 Convenções analíticas (regras de negócio)

| Conceito | Definição |
|----------|-----------|
| **Receita bruta** | `SUM(amount)` de `type = CHARGE` e `status ∈ {PAID, SETTLED, CAPTURED}` |
| **Receita líquida** | `SUM(net_amount)` nas mesmas condições |
| **Tarifas** | `SUM(fee_amount)` das transações liquidadas |
| **Estornos** | `SUM(amount)` de `type = REFUND` liquidado |
| **Lucro líquido (proxy)** | `receita líquida − estornos` (EBITDA/ROI completos exigem dados de custo — Fase 2) |
| **Ticket médio** | `receita bruta / nº de cobranças pagas` |
| **Taxa de conversão** | `nº pagas / nº total de transações no período` |
| **A receber** | `SUM(amount)` de `status ∈ {PENDING, AUTHORIZED, PROCESSING}` |
| **Inadimplência (overdue)** | A receber com `due_date < hoje` |

### 2.3 Índices de cobertura (migration `V008`)

```sql
idx_tx_tenant_created       (tenant_id, created_at)
idx_tx_tenant_status_created(tenant_id, status, created_at)
idx_tx_tenant_type_status   (tenant_id, type, status, created_at)
idx_tx_tenant_client_status (tenant_id, client_id, status)
idx_tx_due_open             (tenant_id, due_date) WHERE status IN (...)  -- parcial
```

### 2.4 Materialized view (histórico pesado — Fase 2)

`mv_tx_daily_revenue (tenant_id, day, gross_revenue, net_revenue, fees, paid_count)`
com índice único `(tenant_id, day)` para `REFRESH MATERIALIZED VIEW CONCURRENTLY`.
Refresh agendado (cron/`@Scheduled`) a cada N minutos. Dashboards de longo período
consultam a MV; janelas curtas consultam a tabela base.

### 2.5 Modelo dimensional futuro (Fase 3 — star schema)

Para EBITDA, ROI, DRE completo e centros de custo é necessário introduzir:

- `fact_financial_entry` (lançamentos: receita, despesa, custo)
- `dim_cost_center`, `dim_category`, `dim_business_unit`, `dim_date`, `dim_seller`
- `budget` (orçado x realizado)

---

## 3. APIs

Base: `/api/tenant/{tenantId}/analytics` · autenticação por cookie `sid` (Finance
`SessionValidationFilter`) · escopo de tenant validado em todo endpoint.

| Método | Rota | Descrição |
|--------|------|-----------|
| GET | `/dashboard` | Payload único do dashboard executivo (resumo + série + ranking + status + recebíveis + insights). **Cacheado**. |
| GET | `/summary` | KPIs executivos + crescimento vs período anterior |
| GET | `/revenue/timeseries` | Série temporal de receita (granularidade configurável) |
| GET | `/clients/ranking` | Ranking de clientes por receita + % de concentração |
| GET | `/status-breakdown` | Distribuição por status de transação |
| GET | `/receivables` | A receber, a vencer e inadimplência |
| GET | `/insights` | Insights automáticos (regras de negócio) |

**Parâmetros comuns:** `from`, `to` (ISO date, default últimos 30 dias),
`granularity` (`DAY`/`WEEK`/`MONTH`/`QUARTER`/`YEAR`, default `DAY`),
`topClients` (default 10).

---

## 4. KPIs recomendados

### Dashboard Executivo
Receita total · Receita líquida · Lucro líquido · Ticket médio · Margem de
contribuição · Taxa de conversão · Clientes pagantes · Crescimento (MoM) · Comparação
com período anterior. *(EBITDA, Fluxo de caixa projetado, ROI → Fase 2/3.)*

### Financeiro
DRE gerencial · Receita por período · Despesas por categoria · A pagar/receber ·
Inadimplência · Realizado x projetado · Rentabilidade por unidade.

### Comercial
Receita por cliente · por produto · por vendedor · Conversão · LTV · CAC · Churn ·
Ranking e concentração de clientes (risco).

### Operacional
Produtividade · Eficiência · Custos operacionais · SLA · Tempo médio de execução ·
Gargalos.

> Marcadores de fase indicam o que já está disponível com os dados atuais (Fase 1) e o
> que depende da expansão do modelo (custos, produtos, vendedores, SLA).

---

## 5. Estrutura de dashboards (frontend)

- **KPI cards inteligentes** com valor, variação % e cor semântica (verde/vermelho).
- **Série temporal** (linha/área/barras) de receita; drill-down por granularidade.
- **Ranking de clientes** (tabela + barra de concentração) com alerta de risco.
- **Breakdown por status** (treemap/heatmap/progress).
- **Recebíveis** (gauge de inadimplência).
- **Painel de insights** (cards de severidade INFO/POSITIVE/WARNING/CRITICAL).
- **Filtros globais**: período, granularidade, conta/unidade.
- **Drill-through**: do KPI para a lista de transações filtrada.

> Fase 1 entrega cards, ranking, breakdown, recebíveis e insights com gráficos leves
> (sem dependência nova). Biblioteca de gráficos rica (`@ant-design/plots`/`recharts`)
> entra na Fase 2.

---

## 6. Estratégia de processamento dos dados

1. **Tempo quase real (Fase 1)** — consulta direta às tabelas com índices de cobertura
   + cache Caffeine TTL 60s por (tenant, período, granularidade).
2. **Pré-agregação (Fase 2)** — materialized views por dia/mês com refresh agendado;
   dashboards de longo período consultam a MV.
3. **Pipeline analítico (Fase 3)** — star schema + jobs de carga (batch/CDC) e,
   se necessário, banco colunar/OLAP para grandes volumes.
4. **Forecast & anomalias (Fase 3)** — séries temporais (média móvel/Holt-Winters) e
   detecção de outliers; projeções de receita, despesa e fluxo de caixa.

---

## 7. Roadmap de implementação em fases

| Fase | Entrega | Status |
|------|---------|--------|
| **1 — Fundação (este PR)** | Endpoints de summary, série temporal, ranking, status, recebíveis e insights; cache; índices; dashboard inicial no frontend | ✅ implementado |
| **2 — Aceleração** | Materialized views + refresh agendado; biblioteca de gráficos rica; drill-down/through; comparativos temporais avançados | ⏳ |
| **3 — Enterprise BI** | Star schema (custos, despesas, produtos, vendedores, centros de custo); DRE/EBITDA/ROI; orçado x realizado; forecast e detecção de anomalias | ⏳ |
| **4 — Escala** | Banco OLAP/colunar; múltiplas fontes (CRM/ERP) via adapters; export/agendamento de relatórios; alertas proativos | ⏳ |

---

## 8. Boas práticas

### Performance
- Agregação no banco (push-down), nunca em memória.
- Índices de cobertura por padrão de consulta (tenant + período + status/type).
- Cache de curta duração para dashboards; MV para histórico.
- `open-in-view: false` (já no Finance) evita N+1 na camada web.
- Endpoint `/dashboard` consolidado evita múltiplas chamadas do frontend.

### Segurança
- Multi-tenant: todo query filtra `tenant_id`; controller valida o token do tenant.
- Autenticação por sessão (`SessionValidationFilter`) reutilizada do Finance.
- RBAC por método (`@PreAuthorize`) quando o relatório for sensível.
- Sem PII desnecessária no payload analítico (agregados, não documentos completos).
- Cache isolado por tenant na chave (sem vazamento entre tenants).

### Escalabilidade
- Pacote isolado, pronto para extração como microserviço `analytics` (porta própria).
- Camada de repositório como porta → novas fontes entram como adapters.
- Stateless: cache local pode migrar para Redis distribuído sem mudar a API.
- Preparado para read-replica do Postgres (carga analítica fora do OLTP).

---

## 9. Insights automáticos (exemplos gerados)

O `InsightEngine` aplica regras de negócio sobre os agregados:

- "A receita aumentou 18% em relação ao período anterior." *(POSITIVE)*
- "O Cliente X representa 34% da receita total e apresenta risco de concentração." *(WARNING)*
- "A inadimplência está em 22% dos recebíveis." *(CRITICAL)*
- "Taxa de conversão de pagamentos em 61%." *(INFO)*

> Regras de orçamento estourado e déficit de fluxo de caixa projetado entram na Fase 2/3,
> quando os dados de orçamento e projeção estiverem modelados.
