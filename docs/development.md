# Development

## Meta

Guia do fluxo diário para trabalhar no Moonevue sem precisar revarrer o código a cada tarefa.

## Setup local

1. Copie os ambientes:

```bash
cp .env.example .env
cp services/backend/.env.example services/backend/.env
```

2. Suba a stack completa:

```bash
docker compose up --build
```

3. Abra os pontos principais:

- Frontend: `http://localhost:3000`
- Gateway: `http://localhost:8080`
- Auth: `http://localhost:8081`
- Finance: `http://localhost:8082`

## Fluxo recomendado de trabalho

### Frontend

- Editar telas em `apps/frontend/app`.
- Centralizar chamadas HTTP em `apps/frontend/lib/api`.
- Reutilizar componentes em `apps/frontend/components/ui`.
- Validar com `npm run lint:frontend` e `npm run build:frontend`.

### Backend

- Módulo auth: autenticação e sessão.
- Módulo gateway: APIs públicas, checkout e webhooks.
- Módulo finance: contas bancárias e configurações.
- Modelo compartilhado: `services/backend/core`.

### Quando mudar contrato

1. Ajuste o backend primeiro.
2. Atualize o client do frontend.
3. Rode lint/build/test do recorte afetado.
4. Atualize o README do módulo se o fluxo externo mudou.

## Comandos úteis

### Frontend

```bash
npm run dev:frontend
npm run lint:frontend
npm run build:frontend
```

### Backend

```bash
cd services/backend
./mvnw -B test
./mvnw -B -DskipTests package
```

### Stack completa

```bash
docker compose up --build
docker compose ps
docker compose logs -f gateway
docker compose down
```

## Variáveis mais importantes

- `AUTH_INTERNAL_TOKEN`
- `AUTH_COOKIE_DOMAIN`
- `AUTH_COOKIE_SECURE`
- `AUTH_COOKIE_SAMESITE`
- `WEBHOOK_HMAC_SECRET`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `NEXT_PUBLIC_API_BASE_URL`
- `INTERNAL_API_BASE_URL`
- `AUTH_INTERNAL_API_BASE_URL`
- `FINANCE_INTERNAL_API_BASE_URL`

## Rotina de verificação antes de abrir PR

- Rodar lint do frontend.
- Rodar build do frontend.
- Rodar testes do backend afetado.
- Validar healthchecks com os containers ativos.
- Conferir se não houve quebra de contrato nas APIs.
