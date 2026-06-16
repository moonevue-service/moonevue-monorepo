# Backlog tecnico por fases

## Premissas

1. Priorizar entregas sem ruptura no schema atual (BIGINT e tabelas legadas).
2. Entregar seguranca e observabilidade junto com funcionalidade.
3. Ativar recursos novos por feature flag quando houver risco de regressao.

## Epico E1 - Fundacao de dados e estados

Objetivo: preparar base para novo fluxo sem quebrar operacao atual.

### Historia E1-S1 - Evoluir schema de clientes e transacoes

1. Como sistema, preciso armazenar campos de deduplicacao e ciclo de vida expandido.
2. Dependencias: nenhuma.
3. Entregaveis:
   1. Migration auth V007 aplicada.
   2. Indices para consulta por documento/email/telefone normalizados.
4. Criterios de aceite:
   1. Migration executa em banco novo e banco existente sem erro.
   2. Listagem de clientes por tenant continua funcional.

### Historia E1-S2 - Introduzir entidade de cobranca (charges)

1. Como operacao, preciso separar transacao interna da cobranca no banco.
2. Dependencias: E1-S1.
3. Entregaveis:
   1. Tabela charges criada e indexada.
   2. Relacao transaction -> charges disponivel.
4. Criterios de aceite:
   1. E possivel registrar cobranca PIX/BOLETO sem alterar tabela transactions para payloads extensos.
   2. Consulta por transaction_id retorna cobrancas associadas.

### Historia E1-S3 - Ledger de tokens de checkout

1. Como plataforma, preciso rastrear emissao/uso/revogacao de links.
2. Dependencias: E1-S1.
3. Entregaveis:
   1. Tabela checkout_tokens com expiracao e contador de tentativas.
4. Criterios de aceite:
   1. Token revogado nao pode ser reutilizado.
   2. Tentativas acima do limite sao bloqueadas.

## Epico E2 - RBAC granular e funcionarios

Objetivo: controlar acesso por modulo e acao com enforcement no backend.

### Historia E2-S1 - Catalogo de permissoes

1. Como admin, quero permissao granular para reduzir acesso excessivo.
2. Dependencias: E1.
3. Entregaveis:
   1. Tabelas auth_permission, auth_role_permission, user_permission.
   2. Seed de permissoes basicas.
4. Criterios de aceite:
   1. Permissoes sao carregadas no banco sem duplicidade.
   2. Mapeamento inicial de roles existentes aplicado.

### Historia E2-S2 - Endpoints de gestao de funcionarios

1. Como admin do tenant, quero ativar/desativar e atribuir papeis/permissoes.
2. Dependencias: E2-S1.
3. Entregaveis:
   1. Endpoints para listar funcionarios e alterar status.
   2. Endpoint para atribuir/remover perfis.
4. Criterios de aceite:
   1. Usuario sem permissao recebe 403.
   2. Toda alteracao gera evento de auditoria.

### Historia E2-S3 - Enforcement de permissao no gateway e finance

1. Como seguranca, quero validação real no backend para cada acao.
2. Dependencias: E2-S1.
3. Entregaveis:
   1. Middleware/annotation para verificar permissao por rota.
4. Criterios de aceite:
   1. Testes de autorizacao por perfil passam.
   2. Frontend nao consegue burlar permissão via chamada direta.

## Epico E3 - Novo fluxo de transacao interna

Objetivo: permitir criacao incompleta e completar no checkout.

### Historia E3-S1 - Criar transacao incompleta

1. Como operador, quero criar transacao sem dados de pagamento.
2. Dependencias: E1.
3. Entregaveis:
   1. Endpoint de criacao valida campos minimos.
   2. Status inicial DRAFT/READY_FOR_CHECKOUT.
4. Criterios de aceite:
   1. Transacao pode existir com client_id nulo.
   2. Link de checkout e gerado automaticamente.

### Historia E3-S2 - Criacao em contexto de cliente

1. Como operador, ao criar em cliente, cliente deve vir pre-vinculado.
2. Dependencias: E3-S1.
3. Entregaveis:
   1. Fluxo de UI com pre-selecao e opcao de desvincular.
4. Criterios de aceite:
   1. client_id chega preenchido por padrao.
   2. Logs registram desvinculo manual.

## Epico E4 - Checkout em etapas e seguranca

Objetivo: separar cenario avulso e cenario vinculado com protecao adequada.

### Historia E4-S1 - Etapa de identificacao (cenario avulso)

1. Como cliente avulso, informo documento para me identificar/criar cadastro.
2. Dependencias: E1-S1, E3.
3. Entregaveis:
   1. lookup por documento normalizado.
   2. Vinculo automatico da transacao ao cliente encontrado/criado.
4. Criterios de aceite:
   1. Duplicidade de cliente nao cresce em cenarios de retry.
   2. Existe limite de tentativas por token/IP.

### Historia E4-S2 - Etapa de pagamento

1. Como cliente, escolho meio ou uso meio fixo definido na transacao.
2. Dependencias: E4-S1.
3. Entregaveis:
   1. Suporte a PIX, boleto e cartao.
   2. Persistencia de estado PAYMENT_PROCESSING.
4. Criterios de aceite:
   1. PIX retorna payload esperado (qr/copia e cola) quando aplicavel.
   2. Boleto retorna linha digitavel/url quando aplicavel.

### Historia E4-S3 - Cenario vinculado com autenticacao obrigatoria

1. Como cliente ja vinculado, preciso autenticar para acessar dados da cobranca.
2. Dependencias: E2.
3. Entregaveis:
   1. Gate de autenticacao no checkout para recursos sensiveis.
4. Criterios de aceite:
   1. Link isolado nao revela dados sensiveis sem login.
   2. Ownership check bloqueia cliente errado.

## Epico E5 - Emissao bancaria, webhook e conciliacao

Objetivo: resiliencia operacional de ponta a ponta.

### Historia E5-S1 - Emissao imediata opcional no momento da criacao

1. Como financeiro, para cliente existente posso emitir cobranca antecipada.
2. Dependencias: E3, E2.
3. Entregaveis:
   1. Regra de negocio para criterio de emissao imediata.
   2. Permissao charges.emit_immediate.
4. Criterios de aceite:
   1. Fluxo registra justificativa e auditoria.
   2. Falha externa nao perde transacao interna.

### Historia E5-S2 - Inbox e idempotencia de webhook

1. Como plataforma, preciso processar webhooks sem duplicidade.
2. Dependencias: E1-S2.
3. Entregaveis:
   1. Uso de webhook_events/webhook inbox com chave unica por evento.
   2. Reprocessamento seguro de eventos falhos.
4. Criterios de aceite:
   1. Evento repetido nao duplica atualizacao de status.
   2. Reprocessamento manual e auditado.

### Historia E5-S3 - Conciliacao e painel operacional

1. Como operacao, quero ver inconsistencias e corrigi-las rapidamente.
2. Dependencias: E5-S2.
3. Entregaveis:
   1. Lista de pendencias de conciliacao.
   2. Acoes de retry e cancelamento assistido.
4. Criterios de aceite:
   1. Pendencias exibem causa e ultimo erro.
   2. Tempo medio de resolucao monitoravel.

## Epico E6 - UX e qualidade

Objetivo: consolidar experiencia e reduzir falhas de uso.

### Historia E6-S1 - Telas de clientes e funcionarios

1. Como usuario interno, quero busca/filtros e visao detalhada por abas.
2. Dependencias: E2, E3.
3. Criterios de aceite:
   1. Busca por nome/documento/email/telefone.
   2. Aba de transacoes no detalhe de cliente.

### Historia E6-S2 - Telemetria, logs e auditoria ponta a ponta

1. Como SRE/seguranca, preciso rastrear cada acao critica por correlationId.
2. Dependencias: E1..E5.
3. Criterios de aceite:
   1. Logs estruturados por fluxo.
   2. Dashboards minimos de conversao/falha/reprocessamento.

## Priorizacao recomendada

1. Sprint 1-2: E1 + E2-S1.
2. Sprint 3-4: E3 + E4-S1.
3. Sprint 5-6: E4-S2 + E4-S3.
4. Sprint 7-8: E5.
5. Sprint 9+: E6.

## Riscos e mitigacoes por backlog

1. Risco: regressao em checkout legado.
   1. Mitigacao: feature flag + testes de regressao automatizados.
2. Risco: role antiga sem permissao nova.
   1. Mitigacao: script de backfill e fallback read-only temporario.
3. Risco: duplicidade residual de clientes.
   1. Mitigacao: fila de merge assistido e regra de bloqueio por documento.
