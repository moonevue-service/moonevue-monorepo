# Guia de Integração — API de Cobranças Moonevue

Este guia mostra como o seu sistema (loja online, ERP, checkout próprio) pode
emitir e acompanhar cobranças no Moonevue via API.

A comunicação é **servidor-a-servidor**. Sua API Key concede acesso às cobranças
do seu negócio — trate-a como uma senha e **nunca a exponha no navegador** do
cliente final.

---

## 1. Visão geral

| Item         | Valor                                              |
| ------------ | -------------------------------------------------- |
| Base URL     | `https://SEU_HOST/api/v1`                          |
| Autenticação | API Key via header `Authorization: Bearer mvk_...` |
| Formato      | JSON (`Content-Type: application/json`)            |
| Ambientes    | `mvk_test_...` (teste) e `mvk_live_...` (produção) |

---

## 2. Primeiros passos

1. Acesse **Dashboard → Integrações → Chaves de API**.
2. Clique em **Criar chave**.
3. Escolha o **ambiente** (comece por `Teste`) e os **escopos**:
   - `charges:write` — criar cobranças.
   - `charges:read` — consultar cobranças.
4. Copie a chave exibida. **Ela só aparece uma vez.**

---

## 3. Autenticação

Envie a chave em todas as requisições:

```http
Authorization: Bearer mvk_live_ab12cd34_xxxxxxxxxxxxxxxxxxxxxxxx
```

Alternativamente, o header `X-API-Key: mvk_live_...` é aceito.

Valide rapidamente sua chave:

```bash
curl https://SEU_HOST/api/v1/ping \
  -H "Authorization: Bearer $MOONEVUE_API_KEY"
```

Resposta:

```json
{ "tenantId": 1, "scopes": ["charges:write", "charges:read"], "status": "ok" }
```

---

## 4. Ambiente de teste vs produção

- Chaves `mvk_test_` operam no ambiente de teste/sandbox.
- Chaves `mvk_live_` operam em produção (movimenta dinheiro real).
- Valide todo o fluxo em teste antes de migrar para produção.

> **Importante — vínculo de ambiente.** O ambiente real da cobrança (homologação
> ou produção) é definido pela **configuração bancária** (`bankConfigurationId`),
> que possui ambiente `SANDBOX` ou `PRODUCTION`. Para evitar cobranças reais por
> engano, a API valida a coerência:
>
> - chave `mvk_test_` (TEST) **só** aceita `bankConfigurationId` de ambiente `SANDBOX`;
> - chave `mvk_live_` (LIVE) **só** aceita `bankConfigurationId` de ambiente `PRODUCTION`.
>
> Em caso de incompatibilidade a API retorna `422 environment_mismatch`. Para
> emitir em homologação, use uma chave `mvk_test_` e o `bankConfigurationId` da
> sua configuração `SANDBOX` (disponível na tela de Contas Bancárias).

---

## 5. Criar uma cobrança

`POST /api/v1/charges`

Use sempre um header **`Idempotency-Key`** único por cobrança. Se você repetir a
requisição com a mesma chave de idempotência e o mesmo corpo, recebe a mesma
resposta — evitando cobranças duplicadas.

### Campos

| Campo                 | Tipo                | Obrigatório                 | Observação                             |
| --------------------- | ------------------- | --------------------------- | -------------------------------------- |
| `method`              | string              | sim                         | `PIX_IMMEDIATE`, `PIX_DUE` ou `BOLETO` |
| `bank`                | string              | sim                         | `EFI` ou `ASAAS`                       |
| `bankConfigurationId` | number              | sim                         | Configuração bancária do seu tenant    |
| `amount`              | number              | sim                         | Valor em reais (ex.: `149.90`)         |
| `dueDate`             | string (YYYY-MM-DD) | sim para `PIX_DUE`/`BOLETO` | Vencimento                             |
| `description`         | string              | não                         | Descrição da cobrança                  |
| `externalReference`   | string              | não                         | Seu identificador (ex.: nº do pedido)  |
| `pixKey`              | string              | não                         | Chave PIX (se aplicável)               |
| `customer.name`       | string              | recomendado                 | Nome do pagador                        |
| `customer.document`   | string              | recomendado                 | CPF (11) ou CNPJ (14)                  |
| `customer.email`      | string              | não                         | E-mail do pagador                      |
| `customer.phone`      | string              | não                         | Telefone do pagador                    |

### Exemplo (curl)

```bash
curl -X POST https://SEU_HOST/api/v1/charges \
  -H "Authorization: Bearer $MOONEVUE_API_KEY" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "PIX_DUE",
    "bank": "EFI",
    "bankConfigurationId": 12,
    "amount": 149.90,
    "dueDate": "2026-07-10",
    "externalReference": "pedido-9988",
    "customer": {
      "name": "Maria Souza",
      "document": "12345678909",
      "email": "maria@exemplo.com"
    }
  }'
```

### Exemplo (Node.js)

```js
import { randomUUID } from "node:crypto";

const res = await fetch("https://SEU_HOST/api/v1/charges", {
  method: "POST",
  headers: {
    Authorization: `Bearer ${process.env.MOONEVUE_API_KEY}`,
    "Idempotency-Key": randomUUID(),
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    method: "PIX_DUE",
    bank: "EFI",
    bankConfigurationId: 12,
    amount: 149.9,
    dueDate: "2026-07-10",
    externalReference: "pedido-9988",
    customer: { name: "Maria Souza", document: "12345678909" },
  }),
});

if (!res.ok) throw new Error(`Falha: ${res.status}`);
const charge = await res.json();
console.log(charge.id, charge.status, charge.pix?.copyPaste);
```

### Exemplo (PHP / Guzzle)

```php
$client = new \GuzzleHttp\Client();
$res = $client->post('https://SEU_HOST/api/v1/charges', [
    'headers' => [
        'Authorization'    => 'Bearer ' . getenv('MOONEVUE_API_KEY'),
        'Idempotency-Key'  => bin2hex(random_bytes(16)),
        'Content-Type'     => 'application/json',
    ],
    'json' => [
        'method' => 'BOLETO',
        'bank' => 'EFI',
        'bankConfigurationId' => 12,
        'amount' => 149.90,
        'dueDate' => '2026-07-10',
        'externalReference' => 'pedido-9988',
        'customer' => ['name' => 'Maria Souza', 'document' => '12345678909'],
    ],
]);
$charge = json_decode($res->getBody(), true);
```

### Resposta `201 Created`

```json
{
  "id": "a1b2c3d4e5f6...",
  "status": "AWAITING_PAYMENT",
  "method": "PIX_DUE",
  "provider": "EFI",
  "amount": 149.9,
  "currency": "BRL",
  "externalReference": "pedido-9988",
  "pix": {
    "copyPaste": "00020126...",
    "location": "https://..."
  }
}
```

Para boleto, o objeto `boleto` traz `line` (linha digitável), `pdfUrl` e
`invoiceUrl`.

---

## 6. Consultar uma cobrança

`GET /api/v1/charges/{id}`

```bash
curl https://SEU_HOST/api/v1/charges/a1b2c3d4e5f6 \
  -H "Authorization: Bearer $MOONEVUE_API_KEY"
```

Retorna o status atual (`AWAITING_PAYMENT`, `PAID`, etc.) e os dados de pagamento.

---

## 7. Tratamento de erros

Todos os erros seguem o envelope:

```json
{ "error": { "code": "invalid_request", "message": "..." } }
```

| HTTP | code                       | Significado                                                                                    |
| ---- | -------------------------- | ---------------------------------------------------------------------------------------------- |
| 400  | `idempotency_key_required` | Falta o header `Idempotency-Key`                                                               |
| 401  | `invalid_api_key`          | Chave inválida, expirada ou revogada                                                           |
| 403  | `forbidden`                | A chave não possui o escopo necessário                                                         |
| 409  | `idempotency_conflict`     | `Idempotency-Key` reutilizada com payload diferente                                            |
| 422  | `invalid_request`          | Dados inválidos (ex.: `amount`, `dueDate`)                                                     |
| 422  | `environment_mismatch`     | Ambiente da chave (TEST/LIVE) incompatível com o da `bankConfigurationId` (SANDBOX/PRODUCTION) |
| 429  | `rate_limited`             | Limite de requisições excedido (respeite `Retry-After`)                                        |
| 502  | `provider_error`           | Falha ao emitir no provedor bancário                                                           |

---

## 8. Limites (rate limiting)

As requisições são limitadas por chave. Ao exceder, você recebe `429` com os
cabeçalhos `Retry-After`, `X-RateLimit-Limit` e `X-RateLimit-Remaining`.
Implemente retry com backoff exponencial.

---

## 9. Segurança e boas práticas

1. Conceda apenas os escopos necessários a cada chave.
2. Mantenha a chave em variável de ambiente/secret manager, nunca no código-fonte
   nem no front-end.
3. Rotacione chaves periodicamente (botão **Rotacionar** na tela de Integrações).
4. Revogue imediatamente qualquer chave comprometida.
5. Sempre use HTTPS.
6. Use `Idempotency-Key` em toda criação de cobrança.

---

## 10. Próximos passos (roadmap)

- **Webhooks de saída**: notificação automática ao seu sistema quando a cobrança
  for paga, com assinatura HMAC. Disponível na próxima fase.
- **API de clientes**: criação/consulta de clientes via API.

Consulte o design técnico em
[docs/evolution/api-publica-integracoes.md](../evolution/api-publica-integracoes.md).
