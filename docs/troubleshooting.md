# Troubleshooting

## 1. Frontend abre, mas as chamadas falham

### Sintoma

A tela carrega, mas login, listagens ou checkout retornam erro de rede ou 404.

### Causa provável

Os rewrites do Next.js apontam para hosts internos que não existem fora da rede Docker, ou o gateway/auth/finance não estão ativos.

### Como corrigir

- Suba a stack completa com `docker compose up --build`.
- Se estiver rodando só o frontend, ajuste `INTERNAL_API_BASE_URL`, `AUTH_INTERNAL_API_BASE_URL` e `FINANCE_INTERNAL_API_BASE_URL`.
- Confirme o arquivo `apps/frontend/next.config.ts`.

## 2. Login retorna 401

### Sintoma

O usuário envia email e senha válidos, mas continua deslogado.

### Causa provável

- Cookie `sid` não foi gravado.
- `AUTH_INTERNAL_TOKEN` não bate entre frontend/gateway/auth.
- `AUTH_COOKIE_DOMAIN`, `AUTH_COOKIE_SECURE` ou `AUTH_COOKIE_SAMESITE` estão incorretos.

### Como corrigir

- Abra o DevTools e confirme se o response de login trouxe `Set-Cookie`.
- Verifique o endpoint `/auth/introspect`.
- Confirme os valores de sessão no `.env`.

## 3. Gateway ou finance voltam 401 logo no início

### Sintoma

O serviço sobe, mas todas as rotas protegidas retornam 401.

### Causa provável

O filtro de sessão não conseguiu introspectar o auth.

### Como corrigir

- Verifique se o auth está saudável.
- Confirme a URL interna do auth.
- Cheque o token interno e o cookie esperado.

## 4. PostgreSQL não conecta

### Sintoma

O boot falha com erro de conexão ou timeouts de datasource.

### Causa provável

- `SPRING_DATASOURCE_URL` errado.
- Usuário/senha não batem com o banco.
- O container do banco ainda não ficou saudável.

### Como corrigir

- Veja `docker compose ps`.
- Confirme o host `postgres` dentro do compose.
- Reinicie os containers se o volume antigo estiver com credenciais diferentes.

## 5. Webhook sempre volta 401

### Sintoma

O gateway rejeita a chamada do provedor.

### Causa provável

- HMAC incorreto.
- Header de assinatura ausente.
- Corpo assinado é diferente do corpo enviado.

### Como corrigir

- Garanta que o segredo usado para assinar é o mesmo do `WEBHOOK_HMAC_SECRET`.
- Verifique se o header está no formato esperado pelo provedor e pelo filtro.
- Confirme que o body não foi serializado de forma diferente entre assinatura e envio.

## 6. Migration não roda como esperado

### Sintoma

A tabela esperada não existe, ou o schema parece incompleto.

### Causa provável

O perfil em uso desabilitou Flyway ou a migration está no módulo errado.

### Como corrigir

- Verifique o perfil ativo.
- Confirme se a migration está em `src/main/resources/db/migration` do serviço correto.
- Revise a estratégia de bootstrap antes de adicionar novos objetos de banco.

## 7. Upload de certificado falha

### Sintoma

O finance rejeita o upload ou gravação do certificado.

### Causa provável

- Tamanho do arquivo excede o limite.
- Diretório de certificados não existe ou não tem permissão.
- O `STORAGE_CERTS_DIR` está errado.

### Como corrigir

- Verifique o volume montado em `/data/certs`.
- Confirme o valor de `STORAGE_CERTS_DIR`.
- Teste com arquivo pequeno para isolar o problema.

## 8. O que validar antes de concluir um incidente

- O serviço correto está saudável.
- O cookie/sessão foram emitidos.
- O tenant no token bate com o tenant da operação.
- O banco está acessível.
- O segredo interno/HMAC está consistente.
