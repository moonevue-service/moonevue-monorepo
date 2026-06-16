# Evolucao do Sistema de Gestao Bancaria e Cobranca

## 1. Resumo executivo

Este documento define uma evolucao implementavel para o Moonevue, preservando a arquitetura atual descrita nos READMEs (frontend Next.js + backend Spring Boot em auth/gateway/finance + sessao por cookie HTTP-only).

Objetivos principais:

1. Consolidar o modulo de Clientes com vinculo opcional na criacao de transacao.
2. Introduzir modulo de Funcionarios com controle de acesso por perfil e permissao.
3. Redesenhar o ciclo de vida de Transacao para permitir criacao incompleta.
4. Estruturar Checkout em etapas, com regra distinta para transacao avulsa e vinculada.
5. Separar claramente transacao interna de cobranca gerada no banco.
6. Garantir confiabilidade assincrona em webhook, idempotencia e conciliacao.

---

## 2. Requisitos refinados

### Regra de negocio

1. Cliente possui 0..N transacoes.
2. Transacao pode existir sem cliente no cadastro inicial.
3. Em contexto de cliente, nova transacao deve vir com cliente pre-selecionado.
4. Checkout de transacao avulsa exige etapa de identificacao do cliente.
5. Checkout de transacao vinculada exige autenticacao do cliente para exibir dados sensiveis.
6. Funcionario acessa somente recursos autorizados por perfil/permissao.
7. Cliente acessa somente os proprios dados, transacoes e meios de pagamento.

### Decisao tecnica

1. Dois contextos de autenticacao: interno (funcionario) e cliente.
2. RBAC com extensao ABAC para ownership (recurso proprio).
3. Autorizacao obrigatoria no backend; frontend apenas reforca UX.

### Sugestao de produto

1. Criacao rapida de transacao com campos minimos e enriquecimento posterior.
2. Timeline de eventos por transacao/cobranca para suporte e auditoria.

---

## 3. Regras de negocio detalhadas

### 3.1 Clientes

1. Duplicidade deve ser evitada por normalizacao de documento, email e telefone.
2. Cadastro permite pessoa fisica e juridica.
3. Deve existir estrategia de merge assistido para possiveis duplicados.

### 3.2 Transacoes

1. Transacao nasce como objeto interno de cobranca.
2. Dados de pagamento (cartao, escolha de metodo, autorizacao) ficam para checkout.
3. Emissao no banco pode ocorrer:
   1. No checkout (padrao).
   2. Na criacao (excecao para cliente ja identificado + regra de juros/multa).

### 3.3 Checkout

1. Link de checkout sempre gerado apos criacao da transacao.
2. Transacao sem cliente:
   1. Etapa 1 identifica ou cria cliente.
   2. Etapa 2 seleciona/efetua pagamento.
3. Transacao com cliente:
   1. Link funciona como pre-acesso controlado.
   2. Login obrigatorio para visualizar dados pessoais e concluir pagamento.

### 3.4 Cobranca bancaria

1. Cobranca bancaria e entidade separada da transacao.
2. Juros e multa formais dependem de cobranca criada no banco.
3. Conciliacao deve reconciliar estado interno e retorno externo.

---

## 4. Duvidas e ambiguidades

1. Funcionario e sempre escopo tenant ou existe operador global multi-tenant?
2. Checkout de transacao vinculada permite alguma pre-visualizacao sem login?
3. Armazenamento de cartao sera por token PSP ou sem cofre?
4. Emissao imediata no banco sera automatica por regra ou manual por permissao?
5. Portal do cliente sera auto-cadastro ou apenas por convite/link?

Alternativas recomendadas:

1. Login obrigatorio em todo checkout:
   1. Pro: seguranca maxima.
   2. Contra: menor conversao.
2. Pre-acesso por link e login apenas em etapa sensivel:
   1. Pro: melhor conversao.
   2. Contra: exige maior rigor de token e anti-abuso.

---

## 5. Sugestoes de melhoria de produto

1. Indicador de completude da transacao (dados faltantes e risco operacional).
2. Reenvio de checkout por canal com historico de tentativas.
3. Painel de pendencias (webhooks falhos, cobrancas inconsistentes, expiracoes).
4. Notificacoes automatizadas de vencimento e inadimplencia.
5. Regua de cobranca por status (a vencer, vencida, negociada, paga).

---

## 6. Modelagem de entidades

Descricao funcional em alto nivel. Contrato SQL inicial em docs/evolution/data-model.sql.

1. Customer: cadastro principal do cliente por tenant.
2. Transaction: intencao de cobranca interna.
3. Charge: cobranca emitida no provedor bancario/adquirente.
4. CheckoutToken: token de acesso controlado ao checkout.
5. Employee: usuario interno do tenant.
6. Role/Permission: autorizacao por perfil e acao.
7. AuditEvent: trilha de operacao e seguranca.
8. WebhookEventInbox: eventos externos com idempotencia e reprocessamento.

---

## 7. Fluxos principais

### 7.1 Criacao de transacao interna

1. Funcionario informa banco, valor, juros, multa, vencimento, descricao e cliente opcional.
2. Sistema cria Transaction em DRAFT ou READY_FOR_CHECKOUT.
3. Sistema gera token de checkout com expiracao e escopo minimo.

### 7.2 Checkout sem cliente vinculado (Cenario A)

1. Cliente abre link.
2. Etapa Informacoes coleta dados minimos.
3. Sistema tenta localizar cliente por documento normalizado + tenant.
4. Se existir, vincula transacao ao cliente existente.
5. Se nao existir, cria cliente e vincula.
6. Avanca para etapa de pagamento.

### 7.3 Checkout com cliente vinculado (Cenario B)

1. Cliente abre link.
2. Sistema exige autenticacao para exibir dados sensiveis.
3. Sistema valida ownership: cliente autenticado deve ser dono da transacao.
4. Cliente conclui etapa de pagamento.

### 7.4 Emissao de cobranca no banco

1. Checkout chama emissao no provedor quando necessario.
2. Para cliente existente, emissao antecipada e permitida por regra/permissao.
3. Falha de emissao nao perde transacao interna; status vai para erro tratavel.

### 7.5 Confirmacao assincrona

1. Banco envia webhook assinado.
2. Sistema valida assinatura, janela temporal e idempotencia.
3. Atualiza Charge e Transaction.
4. Publica evento interno e notifica telas de status.

---

## 8. Status e transicoes

### 8.1 TransactionStatus

1. DRAFT
2. READY_FOR_CHECKOUT
3. AWAITING_CUSTOMER_INFO
4. AWAITING_PAYMENT_METHOD
5. PAYMENT_PROCESSING
6. PAID
7. OVERDUE
8. CANCELED
9. FAILED

### 8.2 ChargeStatus

1. NOT_CREATED
2. CREATING
3. CREATED
4. AWAITING_PAYMENT
5. PARTIALLY_PAID
6. PAID
7. EXPIRED
8. CANCELED
9. FAILED

### 8.3 Regras de transicao

1. Transaction.PAID somente com Charge.PAID confirmada.
2. Transaction.OVERDUE depende de vencimento e ausencia de pagamento.
3. Cancelamento de transacao com cobranca ativa exige tentativa de cancelamento externo.
4. Transicao para FAILED exige erroCode e erroMessage rastreaveis.

---

## 9. APIs sugeridas

Especificacao OpenAPI inicial em docs/evolution/openapi-evolution.yaml.

### Clientes

1. GET /v1/customers
2. POST /v1/customers
3. GET /v1/customers/{customerId}
4. PATCH /v1/customers/{customerId}
5. GET /v1/customers/{customerId}/transactions
6. POST /v1/customers/{customerId}:merge

### Funcionarios e permissoes

1. GET /v1/employees
2. POST /v1/employees
3. PATCH /v1/employees/{employeeId}/status
4. GET /v1/roles
5. POST /v1/employees/{employeeId}/roles
6. DELETE /v1/employees/{employeeId}/roles/{roleId}
7. POST /v1/roles/{roleId}/permissions
8. DELETE /v1/roles/{roleId}/permissions/{permissionKey}

### Transacoes e cobrancas

1. GET /v1/transactions
2. POST /v1/transactions
3. GET /v1/transactions/{transactionId}
4. PATCH /v1/transactions/{transactionId}
5. POST /v1/transactions/{transactionId}/checkout-link/regenerate
6. POST /v1/transactions/{transactionId}/charges/emit

### Checkout

1. GET /v1/checkout/{token}/context
2. POST /v1/checkout/{token}/identify
3. POST /v1/checkout/{token}/payment-method
4. POST /v1/checkout/{token}/pay
5. GET /v1/checkout/{token}/status

### Webhook

1. POST /v1/webhooks/banks/{provider}/events
2. POST /v1/internal/webhooks/reprocess/{eventId}

---

## 10. Arquitetura de autenticacao e autorizacao

### 10.1 Contextos de login

1. Internal Access:
   1. Login de funcionario.
   2. Sessao com papel e permissoes internas.
2. Customer Access:
   1. Login de cliente final.
   2. Escopo exclusivo a recursos proprios.

### 10.2 Sessao

1. Recomendado separar cookie de sessao interna e cookie de sessao cliente.
2. Cookie HTTP-only, Secure em producao, SameSite adequado ao fluxo.
3. Renovacao de sessao por atividade, com timeout absoluto.

### 10.3 Autorizacao

1. RBAC para macro-permissoes.
2. ABAC para ownership e boundary tenant.
3. Toda rota valida tenant + actorType + permissao.

Detalhamento em docs/evolution/rbac-matrix.md.

---

## 11. Cuidados de seguranca

1. Nao expor dados de um cliente para outro em nenhuma consulta.
2. Token de checkout com assinatura forte, expiracao curta e escopo.
3. Rate limiting por IP, token e documento no checkout.
4. Protecao anti-enumeracao no lookup de cliente.
5. Idempotency-Key para operacoes criticas.
6. Webhook com HMAC, nonce/timestamp e anti-replay.
7. Mascaramento de PII em logs e trilha de auditoria.
8. Rotacao de segredos (HMAC, internal token, chaves de assinatura).
9. Alertas de seguranca para tentativas repetidas de acesso invalido.

---

## 12. Telas e navegacao

### 12.1 Area interna

1. Dashboard
2. Contas Bancarias
3. Clientes
4. Transacoes
5. Funcionarios
6. Configuracoes

### 12.2 Modulo Clientes

1. Listagem:
   1. Busca por nome, documento, email, telefone.
   2. Filtros por status, inadimplencia, sem cliente vinculado em transacoes.
2. Detalhe:
   1. Aba Dados.
   2. Aba Transacoes.
   3. Aba Cobrancas.
   4. Aba Historico.
3. Acao primaria: Nova transacao para este cliente.

### 12.3 Modulo Funcionarios

1. Listagem com status e ultimo acesso.
2. Edicao de perfis e permissoes efetivas.
3. Ativar/desativar com auditoria obrigatoria.

### 12.4 Checkout

1. Etapa 1 Informacoes (quando necessario).
2. Etapa 2 Pagamento.
3. Etapa 3 Processamento/Confirmacao.

---

## 13. Roadmap por fases

### Fase 1 - Fundacao de dominio e seguranca

1. Novos estados de transacao e cobranca.
2. Entidades Customer/Charge/CheckoutToken e auditoria.
3. Idempotencia de webhook e tabela de inbox.
4. RBAC minimo no backend.

### Fase 2 - Clientes e Funcionarios

1. Evolucao da tela de clientes com deduplicacao.
2. Modulo de funcionarios e permissoes.
3. Guards de autorizacao no frontend.

### Fase 3 - Novo checkout

1. Fluxo em etapas com cenario A/B.
2. Vinculo inteligente de cliente na etapa de identificacao.
3. Pagamento com PIX/boleto/cartao.

### Fase 4 - Emissao antecipada e conciliacao

1. Criterios para emissao no banco ja na criacao.
2. Painel de conciliacao e reprocessamento.
3. SLA operacional de tratamento de falhas.

### Fase 5 - Escala e otimização

1. Regua de cobranca automatizada.
2. Metricas de conversao e inadimplencia.
3. Hardening de seguranca e antifraude.

---

## 14. Riscos tecnicos e operacionais

1. Inconsistencia entre transacao interna e cobranca externa em falhas parciais.
2. Vazamento por autorizacao incompleta em endpoints.
3. Duplicidade de cliente sem normalizacao e merge.
4. Queda de conversao se login for exigido cedo demais.
5. Acumulo de pendencias sem operacao de reprocessamento.

Mitigacoes:

1. Padrao outbox/inbox + retries com backoff.
2. Testes automatizados de autorizacao por endpoint/perfil.
3. Chaves unicas e fila de revisao de duplicidade.
4. Observabilidade com correlationId ponta a ponta.
5. Runbook de incidentes para webhook e conciliacao.

---

## 15. Diferenciacao explicita de conceitos

1. Regra de negocio: cliente pode ser opcional na origem da transacao.
2. Decisao tecnica: separar Transaction e Charge para consistencia e conciliacao.
3. Sugestao de produto: emissao antecipada no banco para cliente existente com governanca por permissao.

---

## 16. Campos recomendados adicionais

### Customer

1. preferredLanguage
2. consentLGPDAt
3. riskLevel
4. tags

### Transaction

1. externalReference
2. sourceChannel
3. tags
4. checkoutExpiresAt
5. allowPartialPayment

### Charge

1. providerFee
2. netAmount
3. paidAt
4. settlementDate
5. reconciliationBatchId

---

## 17. Validacoes importantes

1. Documento valido por tipo de pessoa.
2. dueDate nao pode ser anterior a data atual (salvo permissao especial).
3. amountPrincipal > 0.
4. Juros e multa dentro de limite configurado por tenant.
5. Emissao imediata no banco somente com dados minimos obrigatorios completos.
6. Alteracao de valor apos cobranca criada exige politica explicita (cancelar e reemitir, ou proibido).

---

## 18. Auditoria e logs

1. Toda acao administrativa gera AuditEvent.
2. Campos minimos: actor, tenant, recurso, antes/depois, motivo, ip, userAgent, correlationId.
3. Eventos de webhook tambem auditados com payload hash e resultado de processamento.
4. Reprocessamentos ficam rastreados com quem executou e justificativa.
