# Runbook

## Objetivo

Passos operacionais para subir, inspecionar e depurar o Moonevue em ambiente local ou de reprodução.

## Verificações rápidas

### Saúde dos serviços

```bash
curl -f http://localhost:8080/actuator/health
curl -f http://localhost:8081/actuator/health
curl -f http://localhost:8082/actuator/health
```

### Estado do compose

```bash
docker compose ps
docker compose logs -f auth
docker compose logs -f gateway
docker compose logs -f finance
docker compose logs -f frontend
```

## Debug local

### Ports JDWP

- Gateway: `5005`
- Auth: `5007`
- Finance: `5006`

### Como conectar

- Use o depurador do IDE e conecte na porta do serviço desejado.
- Interrompa o container apenas se precisar recarregar o processo Java.
- Prefira um único ponto de observação por vez para reduzir ruído.

## Cenários comuns

### 401 em rotas autenticadas

1. Verifique se o cookie `sid` existe no navegador.
2. Confirme se o auth responde em `/auth/introspect`.
3. Verifique `AUTH_INTERNAL_TOKEN` nos três serviços.
4. Confirme `AUTH_COOKIE_DOMAIN`, `AUTH_COOKIE_SECURE` e `AUTH_COOKIE_SAMESITE`.

### Webhook rejeitado com 401

1. Confirme se o request é `POST` em `/webhooks/banks/{provider}/events`.
2. Valide o header de assinatura configurado.
3. Confira se o HMAC usa o corpo exato enviado ao gateway.
4. Confirme se o relógio do cliente não depende de timestamp fora da janela esperada.

### Erro de banco / schema

1. Verifique `SPRING_DATASOURCE_URL`, usuário e senha.
2. Confirme se o PostgreSQL está saudável.
3. Se o erro veio do backend, valide se a migration esperada existe no módulo correto.
4. Se houver schema novo, confirme se o perfil atual executa Flyway.

### Frontend não consegue chamar APIs

1. Confirme se o frontend está dentro da rede do Docker ou se os hosts internos foram ajustados.
2. Verifique os rewrites do `next.config.ts`.
3. Confirme se a porta pública correta foi exposta.

## Deploy de reprodução

### Backend prod compose

```bash
cd services/backend
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
```

### Atenções

- Não exponha o PostgreSQL publicamente em produção.
- Exponha apenas o gateway se a aplicação estiver atrás de um LB ou reverse proxy.
- Use segredos fortes para tokens internos e HMAC.
- Confirme `AUTH_COOKIE_SECURE=true` em ambientes HTTPS.

## O que registrar em incidente

- Serviço afetado.
- Endpoint ou comando que falhou.
- Variáveis de ambiente envolvidas.
- Logs do container.
- Passos para reproduzir.
- Se a falha é de auth, tenant, banco ou webhook.
