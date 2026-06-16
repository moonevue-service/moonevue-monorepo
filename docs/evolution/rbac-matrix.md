# Matriz RBAC e permissoes

## 1. Modelo recomendado

RBAC hibrido:

1. Perfil (Role) para agrupamento funcional.
2. Permissao granular para recurso e acao.
3. Regra ABAC complementar para ownership (cliente so acessa recurso proprio).

Formato da permissao: modulo.acao

Exemplos:

1. customers.read
2. transactions.create
3. charges.emit_immediate
4. employees.deactivate

## 2. Perfis sugeridos

1. SUPER_ADMIN
2. TENANT_ADMIN
3. FINANCE_MANAGER
4. BILLING_OPERATOR
5. SUPPORT_READONLY
6. CUSTOMER

## 3. Permissoes por modulo

### customers

1. customers.read
2. customers.create
3. customers.update
4. customers.merge
5. customers.export

### transactions

1. transactions.read
2. transactions.create
3. transactions.update
4. transactions.cancel
5. transactions.regenerate_checkout_link

### charges

1. charges.read
2. charges.emit
3. charges.emit_immediate
4. charges.cancel
5. charges.retry
6. charges.reconcile

### checkout

1. checkout.read_context
2. checkout.identify
3. checkout.select_payment_method
4. checkout.pay

### employees

1. employees.read
2. employees.create
3. employees.activate
4. employees.deactivate
5. employees.assign_roles
6. employees.revoke_roles

### roles_permissions

1. roles.read
2. roles.manage
3. permissions.read
4. permissions.manage

### settings

1. settings.read
2. settings.update

### audit

1. audit.read
2. audit.export

### webhooks

1. webhooks.read
2. webhooks.reprocess

## 4. Matriz de atribuicao recomendada

Legenda:

1. Y = permitido
2. N = nao permitido
3. OWN = somente recurso proprio (ABAC)

| Permissao | SUPER_ADMIN | TENANT_ADMIN | FINANCE_MANAGER | BILLING_OPERATOR | SUPPORT_READONLY | CUSTOMER |
| --- | --- | --- | --- | --- | --- | --- |
| customers.read | Y | Y | Y | Y | Y | OWN |
| customers.create | Y | Y | Y | Y | N | N |
| customers.update | Y | Y | Y | Y | N | OWN |
| customers.merge | Y | Y | N | N | N | N |
| customers.export | Y | Y | Y | N | N | N |
| transactions.read | Y | Y | Y | Y | Y | OWN |
| transactions.create | Y | Y | Y | Y | N | N |
| transactions.update | Y | Y | Y | Y | N | N |
| transactions.cancel | Y | Y | Y | N | N | N |
| transactions.regenerate_checkout_link | Y | Y | Y | Y | N | N |
| charges.read | Y | Y | Y | Y | Y | OWN |
| charges.emit | Y | Y | Y | Y | N | N |
| charges.emit_immediate | Y | Y | Y | N | N | N |
| charges.cancel | Y | Y | Y | N | N | N |
| charges.retry | Y | Y | Y | N | N | N |
| charges.reconcile | Y | Y | Y | N | N | N |
| checkout.read_context | Y | Y | Y | Y | Y | OWN |
| checkout.identify | Y | Y | Y | Y | N | OWN |
| checkout.select_payment_method | Y | Y | Y | Y | N | OWN |
| checkout.pay | Y | Y | Y | Y | N | OWN |
| employees.read | Y | Y | N | N | N | N |
| employees.create | Y | Y | N | N | N | N |
| employees.activate | Y | Y | N | N | N | N |
| employees.deactivate | Y | Y | N | N | N | N |
| employees.assign_roles | Y | Y | N | N | N | N |
| employees.revoke_roles | Y | Y | N | N | N | N |
| roles.read | Y | Y | N | N | N | N |
| roles.manage | Y | Y | N | N | N | N |
| permissions.read | Y | Y | N | N | N | N |
| permissions.manage | Y | Y | N | N | N | N |
| settings.read | Y | Y | Y | N | Y | N |
| settings.update | Y | Y | N | N | N | N |
| audit.read | Y | Y | Y | N | Y | N |
| audit.export | Y | Y | N | N | N | N |
| webhooks.read | Y | Y | Y | N | Y | N |
| webhooks.reprocess | Y | Y | Y | N | N | N |

## 5. Regras de autorizacao obrigatorias no backend

1. Toda rota valida tenantId do principal versus recurso.
2. Toda rota valida permissao explicita no controller/service.
3. Rotas CUSTOMER usam ownership check por customer_id.
4. Operacoes criticas exigem trilha de auditoria.
5. Operacoes de risco podem exigir step-up auth (MFA).

## 6. Recomendações de implementacao

1. Manter catalogo de permissoes versionado por migration.
2. Resolver permissoes efetivas no login/introspect para cache curto.
3. Invalidar cache de permissoes ao alterar role/permission.
4. Manter testes de autorizacao por endpoint e por perfil.
