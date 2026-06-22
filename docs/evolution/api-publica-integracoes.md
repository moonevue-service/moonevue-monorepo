# API Pública de Cobranças e Integrações

## 1. Resumo executivo

Hoje a empresa (tenant) emite cobranças apenas pela interface interna (sessão por
cookie). Esta evolução adiciona um **segundo canal de emissão**: uma **API pública
autenticada por API Key**, para que sistemas externos do próprio tenant (loja
online, ERP, app próprio) gerem cobranças programaticamente no Moonevue.

A entrega tem três frentes:

1. **Backend**: novo contexto de autenticação por API Key + superfície de API
   pública versionada que reaproveita o fluxo de emissão de cobrança existente.
2. **Frontend**: nova tela **Integrações** no dashboard para gerenciar API Keys
   (criar, listar, revogar, ver escopos e último uso) e webhooks de saída.
3. **Documentação**: tutorial de integração para o cliente final (referência da
   API + guia passo a passo + exemplos).

Princípio de design: **não duplicar regra de negócio**. A API pública é apenas um
novo _adapter de entrada_ (authentication + transport) sobre o mesmo
`PaymentService` já existente. O que muda é **quem** está autenticado (API Key em
vez de sessão) e **como** a autorização é resolvida (escopos da chave em vez de
roles do usuário).

---

## 2. Objetivos e não-objetivos

### Objetivos

1. Emitir cobrança (PIX imediato, PIX com vencimento, boleto) via API com API Key.
2. Consultar status de uma cobrança/transação via API.
3. Gerenciar API Keys pela tela de Integrações (CRUD + revogação + rotação).
4. Escopos por chave (`charges:write`, `charges:read`, `customers:write`...).
5. Idempotência obrigatória em criação de cobrança via API.
6. Rate limiting por tenant/chave.
7. Webhook de saída (notificar o sistema externo quando a cobrança for paga).
8. Tutorial de integração e referência de API para o cliente.

### Não-objetivos (desta fase)

1. OAuth2 client-credentials completo (fica como evolução futura; API Key cobre o
   caso de uso atual com menor complexidade).
2. Marketplace/multi-app por tenant com consentimento granular de terceiros.
3. SDK oficial publicado em package manager (apenas snippets de exemplo).

---

## 3. Modelo de autenticação por API Key

### 3.1 Formato da chave

- Apresentada ao cliente **uma única vez** na criação, no formato:
  - `mvk_live_<keyId>_<secret>` (produção)
  - `mvk_test_<keyId>_<secret>` (sandbox/teste)
- `keyId`: identificador público curto (indexável, aparece em logs e na UI).
- `secret`: 32+ bytes aleatórios (base62). **Nunca** é persistido em claro.
- Persistimos apenas `secret_hash = HMAC-SHA-256(pepper, secret)` (ou
  Argon2/BCrypt). Comparação em tempo constante na validação.
- O par `keyId + secret` é enviado em cada request no header:
  - `Authorization: Bearer mvk_live_<keyId>_<secret>` (preferido), ou
  - `X-API-Key: mvk_live_<keyId>_<secret>` (alternativa).

### 3.2 Por que API Key e não reaproveitar a sessão

| Aspecto     | Sessão (cookie `sid`)        | API Key                      |
| ----------- | ---------------------------- | ---------------------------- |
| Ator        | Usuário humano interno       | Sistema externo (servidor)   |
| Transporte  | Cookie HTTP-only             | Header `Authorization`       |
| Expiração   | Curta, com `touch`           | Longa, com rotação/revogação |
| Autorização | Roles/permissions do usuário | Escopos da chave             |
| CSRF        | Relevante                    | Não aplicável (sem cookie)   |

São contextos distintos e **não devem se misturar**: a API Key nunca deve poder
abrir telas internas, e a sessão nunca deve ser exigida na API pública.

### 3.3 Fluxo de validação (gateway)

```mermaid
flowchart TB
  EXT[Sistema externo] -->|Authorization: Bearer mvk_...| GW[gateway /api/v1/**]
  GW --> F1{ApiKeyAuthFilter}
  F1 -->|sem header de API key| F2[SessionValidationFilter - fluxo atual]
  F1 -->|header presente| RESOLVE[Resolve keyId, valida hash, status, expiração]
  RESOLVE -->|inválida| R401[401]
  RESOLVE -->|válida| TOKEN[ApiKeyAuthToken tenantId + scopes]
  TOKEN --> RL{Rate limit por tenant/chave}
  RL -->|excedido| R429[429]
  RL -->|ok| CTRL[PublicChargeController]
  CTRL --> SVC[PaymentService.createCharge - reaproveitado]
```

1. `ApiKeyAuthFilter` roda **antes** do `SessionValidationFilter`.
2. Se o request traz header de API Key, o filtro:
   1. Extrai `keyId` do token.
   2. Carrega a chave (cache curto + DB), valida `status=ACTIVE`, `expiresAt`,
      `secret_hash` (tempo constante) e ambiente (`live`/`test`).
   3. Cria um `Authentication` (`ApiKeyAuthToken`) com `tenantId` e _authorities_
      derivadas dos escopos (ex.: `charges.emit`, `charges.read`).
   4. Atualiza `last_used_at` de forma assíncrona (best-effort).
3. Se não há header de API Key, o filtro não faz nada e o fluxo segue para a
   `SessionValidationFilter` (comportamento atual intacto).
4. O `SecurityConfig` libera `/api/v1/**` da exigência de cookie e exige
   `authenticated()` (que será satisfeito pelo `ApiKeyAuthToken`).

### 3.4 Reaproveitamento de `tenantId`/authorities

O `PaymentController` atual extrai `tenantId` de `IntrospectedAuthToken`. Para não
duplicar lógica, o `ApiKeyAuthToken` deve expor `tenantId` da mesma forma (mesma
interface/contrato de `getDetails()` → `Map{ tenantId }`), de modo que o
`PaymentService` permaneça **agnóstico à origem da autenticação**.

---

## 4. Superfície da API pública

Namespace dedicado e versionado: **`/api/v1`** (separado do `/payments` interno
e do `/v1` da evolução interna, para isolar contrato público e versionamento).

### 4.1 Cobranças

| Método | Rota                                | Escopo           | Descrição                                                                      |
| ------ | ----------------------------------- | ---------------- | ------------------------------------------------------------------------------ |
| POST   | `/api/v1/charges`                   | `charges:write`  | Cria cobrança (PIX imediato/vencimento/boleto). `Idempotency-Key` obrigatório. |
| GET    | `/api/v1/charges/{chargeId}`        | `charges:read`   | Consulta status/detalhe da cobrança.                                           |
| GET    | `/api/v1/charges`                   | `charges:read`   | Lista cobranças (paginado, filtros por status/data/`externalReference`).       |
| POST   | `/api/v1/charges/{chargeId}/cancel` | `charges:cancel` | Cancela cobrança quando o provedor permitir.                                   |

### 4.2 Clientes (opcional, fase 2)

| Método | Rota                     | Escopo            | Descrição                            |
| ------ | ------------------------ | ----------------- | ------------------------------------ |
| POST   | `/api/v1/customers`      | `customers:write` | Cria/atualiza cliente por documento. |
| GET    | `/api/v1/customers/{id}` | `customers:read`  | Consulta cliente.                    |

### 4.3 Webhooks de saída

| Método | Rota                         | Escopo            | Descrição                         |
| ------ | ---------------------------- | ----------------- | --------------------------------- |
| GET    | `/api/v1/webhooks/endpoints` | `webhooks:read`   | Lista endpoints configurados.     |
| POST   | `/api/v1/webhooks/endpoints` | `webhooks:manage` | Registra endpoint de notificação. |

### 4.4 Meta

| Método | Rota                   | Auth    | Descrição                                                    |
| ------ | ---------------------- | ------- | ------------------------------------------------------------ |
| GET    | `/api/v1/ping`         | API Key | Valida a chave e retorna `tenantId`, `environment`, escopos. |
| GET    | `/api/v1/openapi.json` | pública | Especificação OpenAPI da API pública.                        |

### 4.5 Contrato de criação de cobrança (exemplo)

Request `POST /api/v1/charges`:

```http
POST /api/v1/charges HTTP/1.1
Authorization: Bearer mvk_live_ab12cd34_xxxxxxxxxxxxxxxxxxxxxxxx
Idempotency-Key: 2c9f8b0e-7a1e-4f3d-9c2a-9f0b1e6d5a44
Content-Type: application/json

{
  "method": "PIX_DUE",
  "bankConfigurationId": 12,
  "amount": 149.90,
  "dueDate": "2026-07-10",
  "externalReference": "pedido-9988",
  "customer": {
    "name": "Maria Souza",
    "document": "12345678909",
    "email": "maria@exemplo.com"
  },
  "callbackUrl": "https://loja.cliente.com/webhooks/moonevue"
}
```

Response `201 Created`:

```json
{
  "id": "chg_01J9X...",
  "status": "AWAITING_PAYMENT",
  "method": "PIX_DUE",
  "amount": 149.9,
  "externalReference": "pedido-9988",
  "pix": {
    "qrCode": "00020126...",
    "copyPaste": "00020126...",
    "expiresAt": "2026-07-10T23:59:59Z"
  },
  "createdAt": "2026-06-22T14:02:11Z"
}
```

Observações de contrato:

- O DTO público é **estável e desacoplado** dos DTOs internos (`ChargeRequestDTO`
  da EFI/ASAAS). Um mapper converte o payload público → `ChargeRequestDTO`.
- Erros seguem um envelope único: `{ "error": { "code", "message", "details" } }`.
- `Idempotency-Key` reutilizada com payload diferente → `409 Conflict` (já
  suportado pelo `IdempotencyService`).

---

## 5. Modelo de dados

Novas tabelas (donas: serviço que valida a chave — recomendado o **gateway**, ou o
**auth** caso se queira centralizar credenciais; ver seção 9). Proposta de DDL:

```sql
CREATE TABLE api_key (
  id              BIGSERIAL PRIMARY KEY,
  tenant_id       BIGINT NOT NULL REFERENCES tenant(id),
  key_id          VARCHAR(32) NOT NULL UNIQUE,      -- parte pública (mvk_live_<key_id>_...)
  secret_hash     VARCHAR(255) NOT NULL,            -- HMAC/Argon2 do secret; nunca em claro
  name            VARCHAR(120) NOT NULL,            -- rótulo dado pelo usuário
  environment     VARCHAR(10) NOT NULL,             -- LIVE | TEST
  scopes          TEXT[] NOT NULL DEFAULT '{}',     -- ex.: {charges:write,charges:read}
  status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | REVOKED
  last_used_at    TIMESTAMPTZ,
  expires_at      TIMESTAMPTZ,                       -- nulo = sem expiração
  created_by      BIGINT REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at      TIMESTAMPTZ,
  revoked_by      BIGINT REFERENCES users(id)
);
CREATE INDEX idx_api_key_tenant ON api_key(tenant_id);
CREATE INDEX idx_api_key_status ON api_key(status);

-- Trilha de uso para auditoria/segurança (opcional, particionável por mês)
CREATE TABLE api_key_usage (
  id            BIGSERIAL PRIMARY KEY,
  api_key_id    BIGINT NOT NULL REFERENCES api_key(id),
  tenant_id     BIGINT NOT NULL,
  route         VARCHAR(160) NOT NULL,
  method        VARCHAR(10) NOT NULL,
  status_code   INT NOT NULL,
  ip            VARCHAR(64),
  correlation_id VARCHAR(64),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_key_usage_key ON api_key_usage(api_key_id, created_at);

-- Webhooks de saída (fase 2)
CREATE TABLE webhook_endpoint (
  id           BIGSERIAL PRIMARY KEY,
  tenant_id    BIGINT NOT NULL REFERENCES tenant(id),
  url          TEXT NOT NULL,
  secret_hash  VARCHAR(255) NOT NULL,    -- para HMAC de assinatura do payload
  events       TEXT[] NOT NULL,          -- ex.: {charge.paid,charge.expired}
  status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

A migration deve seguir a convenção do serviço dono (Flyway `V0xx__api_keys.sql`).
A `Charge`/`Transaction` ganham os campos opcionais `external_reference` e
`source_channel` (`INTERNAL` | `API`) para distinguir origem e permitir relatório.

---

## 6. Segurança

1. **Segredo nunca persistido em claro**; só `secret_hash`. Mostrar a chave plena
   apenas uma vez na criação.
2. **Comparação em tempo constante** do hash; pepper em variável de ambiente.
3. **Escopos mínimos** por chave (princípio do menor privilégio).
4. **Rate limiting** por tenant + por chave (ex.: bucket por minuto) → `429` com
   `Retry-After`.
5. **Idempotência obrigatória** em `POST /api/v1/charges`.
6. **Isolamento de tenant**: toda query filtra por `tenant_id` da chave; jamais
   confiar em `tenant_id` vindo do corpo.
7. **Rotação e revogação** imediatas (revogação invalida em ≤ TTL do cache).
8. **Ambiente** `LIVE`/`TEST` separa credenciais e (idealmente) provedores/sandbox.
9. **Auditoria**: criação/revogação de chave gera `audit_log`; uso registrado em
   `api_key_usage` com `correlationId`.
10. **Mascaramento**: logs exibem apenas `key_id`, nunca o secret.
11. **CORS**: a API pública é server-to-server; não habilitar CORS amplo (evitar
    uso de API Key direto do browser do cliente final).
12. **TLS obrigatório**; rejeitar chave em conexões não seguras em produção.
13. **Anti-replay/HMAC** nos webhooks de saída (assinatura `X-Moonevue-Signature`).

---

## 7. Frontend — Tela de Integrações

Nova rota: `app/dashboard/integrations/page.tsx`, com item no sidebar
(`baseNavItems`), ícone `ApiOutlined`/`CodeOutlined`, gated por permissão
`integrations.manage` (somente admin do tenant por padrão).

### 7.1 Estrutura da tela

1. **Aba "Chaves de API"**
   - Tabela: `name`, `key_id` (mascarado `mvk_live_ab12…`), ambiente, escopos,
     `last_used_at`, status, criada por/em.
   - Ação **"Criar chave"**: modal com nome, ambiente, seleção de escopos.
     - Ao criar, exibir a chave **completa uma única vez** com botão copiar e
       aviso "guarde agora, não será exibida novamente".
   - Ações por linha: **Revogar** (confirmação), **Rotacionar** (gera nova,
     opção de manter a antiga ativa por janela de migração).
2. **Aba "Webhooks"** (fase 2)
   - Cadastro de endpoints, eventos assinados, segredo de assinatura, teste de
     entrega ("send test event") e histórico de tentativas.
3. **Aba "Tutorial / Documentação"**
   - Guia passo a passo embutido (ver seção 8) com snippets `curl`, Node, PHP.
   - Link para a referência OpenAPI (`/api/v1/openapi.json` renderizada).

### 7.2 Camada de API no frontend

Novo módulo `lib/api/integrations.ts` (exportado por `lib/api/index.ts`),
seguindo o padrão do `ApiClient` (cookie de sessão — pois o gerenciamento das
chaves é feito pelo **usuário interno**, não pela API Key):

```ts
export const IntegrationsApi = {
  listKeys: () => ApiClient.get<ApiKey[]>("/integrations/api-keys"),
  createKey: (body: CreateApiKeyRequest) =>
    ApiClient.post<CreatedApiKey>("/integrations/api-keys", body), // retorna secret 1x
  revokeKey: (id: number) =>
    ApiClient.delete<void>(`/integrations/api-keys/${id}`),
  rotateKey: (id: number) =>
    ApiClient.post<CreatedApiKey>(`/integrations/api-keys/${id}/rotate`),
};
```

> Importante: o **gerenciamento** das chaves (`/integrations/api-keys`) é
> autenticado por **sessão** (admin interno). O **uso** das chaves
> (`/api/v1/**`) é autenticado pela própria **API Key**. São superfícies
> distintas no mesmo serviço.

### 7.3 Autorização no frontend

Adicionar helper em `lib/authz.ts` (`canManageIntegrations(roles, permissions)`)
e filtrar o item de menu, como já é feito para Clientes/Funcionários.

---

## 8. Tutorial de integração (conteúdo para o cliente)

Documento de referência: `docs/integracoes/guia-api.md` (e versão embutida na aba
Tutorial). Estrutura sugerida:

1. **Visão geral**: o que é a API, casos de uso (loja online, ERP, checkout
   próprio), modelo server-to-server.
2. **Primeiros passos**
   1. Acesse Dashboard → Integrações → Criar chave.
   2. Selecione ambiente (Teste primeiro) e escopos.
   3. Copie e guarde a chave (exibida uma vez).
3. **Autenticação**: como enviar `Authorization: Bearer mvk_...`.
4. **Ambiente de teste vs produção** (`mvk_test_` x `mvk_live_`).
5. **Criar uma cobrança** (exemplos completos):
   - `curl`, Node.js (`fetch`), PHP (`guzzle`).
6. **Idempotência**: por que e como usar `Idempotency-Key`.
7. **Consultar status** da cobrança.
8. **Receber notificações (webhook)**: validar assinatura `X-Moonevue-Signature`,
   responder `2xx`, idempotência no consumidor.
9. **Tratamento de erros**: tabela de códigos (`401`, `403`, `409`, `422`, `429`).
10. **Boas práticas e segurança**: rotação de chaves, escopos mínimos, não expor a
    chave no front, retries com backoff.
11. **Referência completa**: link para OpenAPI.

### 8.1 Exemplo de snippet (para o tutorial)

```bash
curl -X POST https://api.moonevue.com/api/v1/charges \
  -H "Authorization: Bearer $MOONEVUE_API_KEY" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "PIX_DUE",
    "bankConfigurationId": 12,
    "amount": 149.90,
    "dueDate": "2026-07-10",
    "externalReference": "pedido-9988",
    "customer": { "name": "Maria Souza", "document": "12345678909" }
  }'
```

---

## 9. Decisões em aberto (a confirmar antes da implementação)

1. **Serviço dono das API Keys**: `gateway` (mais próximo da emissão e do rate
   limit) ou `auth` (centraliza credenciais e introspecção)? Recomendação:
   **gateway** valida no `ApiKeyAuthFilter` lendo de tabela própria; criação/CRUD
   também no gateway sob sessão. Alternativa: `auth` emite/valida e o gateway só
   consome via introspecção (mais consistente com o modelo atual de sessão, porém
   adiciona um hop por request — mitigável com cache).
2. **Namespace**: `/api/v1` (proposto) vs reusar `/v1`. Recomendação: `/api/v1`
   para isolar contrato público.
3. **Webhooks de saída** entram na fase 1 ou ficam para fase 2? Recomendação:
   fase 2 (a emissão síncrona já entrega valor; webhook exige outbox/retry).
4. **Ambiente TEST**: usar sandbox real dos provedores (EFI/ASAAS) ou um provedor
   "mock" interno? Recomendação: sandbox do provedor quando existir; senão, mock.
5. **Rate limit**: in-memory por instância (simples) vs Redis (correto em
   multi-instância). Recomendação: começar in-memory com cabeçalhos padronizados e
   evoluir para Redis quando escalar.

---

## 10. Roadmap por fases

### Fase 1 — Emissão via API (núcleo)

1. Migration `api_key` (+ campos `external_reference`/`source_channel` em charge).
2. Entidade/repository `ApiKey` no `core`; `ApiKeyService` (criar/validar/revogar).
3. `ApiKeyAuthFilter` + `ApiKeyAuthToken` no gateway; ajuste no `SecurityConfig`.
4. `PublicChargeController` (`/api/v1/charges`, `GET .../{id}`, `GET /ping`) +
   mapper público → `ChargeRequestDTO`, reusando `PaymentService`.
5. Idempotência obrigatória + rate limit básico + auditoria de uso.
6. CRUD de chaves sob sessão (`/integrations/api-keys`) + RBAC `integrations.manage`.
7. Frontend: tela **Integrações** (aba Chaves) + `lib/api/integrations.ts` + menu.
8. Tutorial inicial (`docs/integracoes/guia-api.md`) + aba Tutorial.
9. Testes: filtro de auth, isolamento de tenant, idempotência, escopos, 401/403/429.

### Fase 2 — Webhooks de saída e clientes

1. `webhook_endpoint` + entrega com outbox/retry/backoff + assinatura HMAC.
2. `POST /api/v1/customers`, consulta de cobranças paginada com filtros.
3. Aba Webhooks no frontend (cadastro, teste de entrega, histórico).

### Fase 3 — Hardening e escala

1. Rate limit distribuído (Redis), métricas por chave, alertas de abuso.
2. Rotação assistida com janela de migração e expiração programada.
3. (Opcional) OAuth2 client-credentials para clientes que exigirem.

---

## 11. Riscos e mitigações

| Risco                                        | Mitigação                                                        |
| -------------------------------------------- | ---------------------------------------------------------------- |
| Vazamento de API Key                         | Hash + exibição única + rotação/revogação + escopos mínimos      |
| Mistura de contexto sessão x API Key         | Filtros separados; `/api/v1/**` nunca aceita cookie e vice-versa |
| Acoplamento ao DTO do provedor               | DTO público estável + mapper dedicado                            |
| Cobrança duplicada por retry do cliente      | `Idempotency-Key` obrigatório                                    |
| Abuso/DoS via API                            | Rate limit + auditoria + bloqueio por chave                      |
| Inconsistência multi-instância no rate limit | Evoluir para Redis na fase 3                                     |

```

```
