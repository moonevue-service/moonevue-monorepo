# Frontend

Aplicação web do Moonevue, construída com Next.js 15, React 19, TypeScript, Ant Design e Tailwind CSS 4. Ela entrega a home pública, login, cadastro, checkout público e a área autenticada de dashboard.

O frontend não fala diretamente com múltiplos serviços pela mão do componente. Em vez disso, a camada `lib/api` centraliza as chamadas HTTP e o `next.config.ts` faz rewrites para `auth`, `gateway` e `finance`.

## Responsabilidade do módulo

- Home e marketing da aplicação.
- Fluxos de autenticação: login, registro, logout e manutenção de sessão.
- Dashboard multi-tenant com visão geral, contas bancárias, clientes, transações e configurações.
- Checkout público por token.
- Encapsulamento de chamadas HTTP com cookie de sessão.

## Como rodar só este módulo

### Pré-requisitos

- Node.js 20+
- npm
- Os serviços internos acessíveis pela rede do Next.js ou via Docker Compose

### Desenvolvimento local

No diretório `apps/frontend`:

```bash
npm install
npm run dev
```

Se o frontend estiver rodando fora do Docker, ajuste as variáveis abaixo para apontar para os serviços que você realmente consegue alcançar:

- `NEXT_PUBLIC_API_BASE_URL`
- `INTERNAL_API_BASE_URL`
- `AUTH_INTERNAL_API_BASE_URL`
- `FINANCE_INTERNAL_API_BASE_URL`

### Build e start

```bash
npm run build
npm run start
```

### Lint

```bash
npm run lint
```

### Teste

```bash
npm run test
npm run test:coverage
```

## Modos de execução

### Docker mode (padrão)

Recomendado para desenvolvimento integrado. Os rewrites do Next.js resolvem `gateway`, `auth` e `finance` pelos hostnames internos do Docker Compose.

```bash
# da raiz do monorepo
cp .env.example .env
docker compose up --build
```

### Standalone mode (host local)

Para rodar o frontend no host e consumir o backend exposto pelo Docker:

1. Suba só o backend:

```bash
cd services/backend
docker compose up --build
```

2. Copie o arquivo de variáveis do frontend:

```bash
cd apps/frontend
cp .env.example .env.local
```

3. Edite `.env.local` para apontar para localhost:

```bash
NEXT_PUBLIC_API_BASE_URL=
INTERNAL_API_BASE_URL=http://localhost:8080
AUTH_INTERNAL_API_BASE_URL=http://localhost:8081
FINANCE_INTERNAL_API_BASE_URL=http://localhost:8082
```

4. Rode o frontend:

```bash
npm install
npm run dev
```

**Limitações do modo standalone**: o Next.js usa as variáveis `*_INTERNAL_API_BASE_URL` para fazer rewrites no servidor. Em modo standalone, substitua os hostnames Docker por `localhost` e confirme que as portas 8080, 8081 e 8082 estão expostas no `docker compose ps`.

## Rotas e contratos

### Rotas públicas

- `/` -> landing page.
- `/login` -> autenticação.
- `/register` -> criação de tenant e primeiro usuário.
- `/checkout/[token]` -> checkout público por token.

### Rotas autenticadas

- `/dashboard` -> visão geral.
- `/dashboard/bank-accounts` -> CRUD de contas bancárias.
- `/dashboard/bank-accounts/[id]/config` -> configurações da conta bancária.
- `/dashboard/clients` -> cadastro e consulta de clientes.
- `/dashboard/transactions` -> criação e listagem de transações.
- `/dashboard/settings` -> dados da conta e logout.

### Contratos HTTP consumidos

- `AuthApi`
  - `POST /auth/register`
  - `POST /auth/login`
  - `GET /auth/logout`
  - `GET /auth/introspect`
  - `POST /auth/touch`
  - `POST /auth/employees`
- `ClientsApi`
  - `GET /clients`
  - `GET /clients/{clientId}`
  - `POST /clients`
  - `PUT /clients/{clientId}`
  - `GET /clients/{clientId}/transactions`
- `PaymentApi`
  - `GET /payments`
  - `POST /payments`
  - `POST /payments/pix/immediate`
  - `POST /payments/pix/due`
  - `POST /payments/boleto`
- `CheckoutApi`
  - `GET /checkout/{token}`
  - `GET /checkout/{token}/status`
  - `GET /checkout/{token}/client-lookup?document=...`
  - `POST /checkout/{token}/identify`
  - `POST /checkout/{token}/pay`
- `FinanceApi`
  - `GET /api/tenant/{tenantId}/bank-account`
  - `POST /api/tenant/{tenantId}/bank-account`
  - `PUT /api/tenant/{tenantId}/bank-account/{bankAccountId}`
  - `DELETE /api/tenant/{tenantId}/bank-account/{bankAccountId}`
  - `GET /api/tenant/{tenantId}/bank-account/{bankAccountId}/configuration`
  - `POST /api/tenant/{tenantId}/bank-account/{bankAccountId}/configuration`
  - `PUT /api/tenant/{tenantId}/bank-account/{bankAccountId}/configuration/{configId}`
  - `POST /api/tenant/{tenantId}/bank-account/{bankAccountId}/configuration/{configId}/certificate`

## Dependências internas e externas

### Internas

- `app/providers.tsx` para estado global de autenticação.
- `app/protected-route.tsx` para proteção de rotas.
- `lib/api/*` para contratos HTTP.
- `app/antd-provider.tsx` e `components/ui/*` para UI.
- Rewrites do `next.config.ts` para alcançar os serviços backend.

### Externas

- Next.js App Router.
- React e React DOM.
- Ant Design.
- Tailwind CSS 4.
- Radix UI.
- Framer Motion e `lucide-react` em componentes visuais.

## Gotchas

- `npm run dev:frontend` só funciona sozinho se os hosts internos `gateway`, `auth` e `finance` resolverem no ambiente em que o Next está rodando.
- O frontend depende de cookie HTTP-only com `credentials: include`; sem isso a sessão não sobe.
- O rewrite para `/api/*` aponta para o finance; isso é correto para as rotas de banco e configuração, mas não para o gateway.
- O checkout público usa o mesmo frontend, mas a autenticação é diferente da área interna.
- `NEXT_PUBLIC_API_BASE_URL` pode ficar vazio quando o Next estiver atrás de rewrites; isso é intencional.

## Autorização de configuração bancária

A página `/dashboard/bank-accounts/[id]/config` e as chamadas à API de bank configuration são restritas.

Papéis que têm acesso: `ADMIN_TENANT` e `ADMIN`.

Implementação no frontend: `apps/frontend/lib/authz.ts` exporta `canManageBankConfigurations(roles)`.

Implementação no backend: `@PreAuthorize("hasAnyAuthority('ADMIN_TENANT', 'ADMIN')")` no `BankConfigurationController`.

Consequências:
- Usuários com outros papéis (ex: `USER`, `FINANCE`, `SUPPORT`, `EMPLOYED`) recebem 403 no backend.
- O botão de configuração na listagem de contas é desativado visualmente para usuários sem esses papéis.

## Como testar

- `npm run lint`
- `npm run build`
- Fluxo manual recomendado:
  - abrir `/register`
  - criar tenant
  - entrar em `/dashboard`
  - cadastrar conta bancária
  - abrir transações
  - testar `/checkout/[token]`

## Como debugar

- Abra as DevTools do navegador e verifique os requests HTTP.
- Se receber 401, confirme se o cookie `sid` existe e se o serviço auth está respondendo.
- Se a tela ficar carregando, verifique se `AuthApi.introspect()` está retornando dados.
- Use `docker compose logs -f frontend` quando a aplicação estiver dentro do container.

## Owners sugeridos

- Frontend / UX: time de produto ou engenharia de frontend.
- Integração de API: time responsável pelo gateway.
- Autenticação e sessão: time responsável pelo auth.
