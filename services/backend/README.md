# Backend

Monorepo Maven do backend do Moonevue. O projeto é dividido em serviços Spring Boot independentes e módulos compartilhados para manter o domínio, a autenticação e o gateway desacoplados.

## Módulos

- `auth`: registro, login, logout, introspecção e renovação de sessão.
- `gateway`: API pública, clientes, pagamentos, checkout e webhooks.
- `finance`: contas bancárias, configurações bancárias e upload de certificados.
- `core`: entidades, enums, repositórios e filtros compartilhados.
- `useful`: utilidades internas.

## Como rodar só o backend

### Pré-requisitos

- Java 21
- Maven Wrapper
- PostgreSQL 16 ou o banco disponível via Docker Compose
- Docker, se você quiser subir a stack completa

### Stack completa de backend

Na raiz `services/backend`:

```bash
cp .env.example .env
docker compose up --build
```

### Backend isolado com o compose local do próprio serviço

```bash
cd services/backend
cp .env.example .env
docker compose up --build
```

### Produção / reprodução

```bash
cd services/backend
cp .env.example .env.prod
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
```

### Build e testes

```bash
cd services/backend
./mvnw -B test
./mvnw -B -DskipTests package
```

## Portas e serviços

- Auth: `8081`
- Gateway: `8080`
- Finance: `8082`
- PostgreSQL: `5432`

### Debug

- Auth JDWP: `5007 -> 5005`
- Gateway JDWP: `5005 -> 5005`
- Finance JDWP: `5006 -> 5005`

## Contratos por serviço

### Auth

Base: `/auth`

- `POST /auth/register`
- `POST /auth/login`
- `GET /auth/logout`
- `GET /auth/introspect`
- `POST /auth/touch`
- `POST /auth/employees`

### Gateway

Base pública sem prefixo adicional

- `GET /clients`
- `GET /clients/{clientId}`
- `POST /clients`
- `PUT /clients/{clientId}`
- `GET /clients/{clientId}/transactions`
- `GET /payments`
- `POST /payments`
- `POST /payments/checkout`
- `POST /payments/pix/immediate`
- `POST /payments/pix/due`
- `POST /payments/boleto`
- `GET /checkout/{token}`
- `GET /checkout/{token}/status`
- `GET /checkout/{token}/client-lookup`
- `POST /checkout/{token}/identify`
- `POST /checkout/{token}/pay`
- `POST /webhooks/banks/{provider}/events`

### Finance

Base: `/api/tenant/{tenantId}`

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

- `core` concentra entidades, enums e o filtro `SessionValidationFilter`.
- `auth` é a autoridade de sessão para gateway e finance.
- `gateway` consulta auth para introspecção de sessão.
- `finance` compartilha o mesmo esquema de banco e o mesmo padrão de autenticação por cookie.

### Externas

- Spring Boot 3.5.
- Spring Security.
- Spring Data JPA.
- Flyway.
- PostgreSQL.
- Springdoc OpenAPI.
- Logstash encoder para logs estruturados.

## Fluxo de autenticação

1. O frontend envia o cookie `sid` com `credentials: include`.
2. O gateway ou finance chamam `auth/introspect` com o token interno `X-Internal-Token`.
3. O auth devolve `userId`, `email`, `tenantId` e `roles`.
4. O filtro compartilhado monta a autenticação no Spring Security.
5. Em seguida o serviço executa a autorização por tenant e/ou por papel, conforme o controller.

## Fluxo de webhook

1. O request chega em `POST /webhooks/banks/{provider}/events`.
2. O filtro `WebhookSignatureFilter` lê o corpo, calcula o HMAC e compara com o header configurado.
3. Se a assinatura for válida, o request recebe a autoridade `WEBHOOK`.
4. O controller processa o evento.

## Gotchas

- O gateway tem migrations em `src/main/resources/db/migration`, mas o perfil padrão de Docker desabilita Flyway. Confirme o comportamento ao ativar schema novo.
- O finance tem autorização por tenant em parte dos controllers; mantenha essa checagem consistente entre endpoints.
- O auth usa cookie HTTP-only com `SameSite` e `Secure` parametrizados por ambiente.
- O backend depende fortemente de `AUTH_INTERNAL_TOKEN`; se ele divergir entre serviços, tudo falha com 401.
- O diretório de certificados precisa existir e ter permissão correta quando houver upload de certificados bancários.

## Variáveis de ambiente obrigatórias

O boot falha explicitamente com mensagem de erro se estas variáveis estiverem ausentes (fora do profile `test`):

| Serviço | Variáveis obrigatórias |
| --- | --- |
| auth | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `AUTH_INTERNAL_TOKEN` |
| gateway | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `AUTH_BASE_URL`, `AUTH_INTERNAL_TOKEN`, `WEBHOOK_HMAC_SECRET`, `STORAGE_CERTS_DIR` |
| finance | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `AUTH_BASE_URL`, `AUTH_INTERNAL_TOKEN`, `STORAGE_CERTS_DIR` |

Copie `services/backend/.env.example` como ponto de partida e preencha todas as variáveis antes de subir os containers.

## Autorização de configuração bancária

O controller `BankConfigurationController` no módulo `finance` é protegido com:

```java
@PreAuthorize("hasAnyAuthority('ADMIN_TENANT', 'ADMIN')")
```

Regra: apenas usuários com papel `ADMIN_TENANT` ou `ADMIN` podem listar, criar, atualizar, e fazer upload de certificados em configurações bancárias.

Outros papéis (`USER`, `FINANCE`, `SUPPORT`, `EMPLOYED`) recebem 403.

O `GlobalExceptionHandler` do finance mapeia `AccessDeniedException` para 403 de forma consistente.

## Como testar

- Auth: `./mvnw -B -pl auth test`
- Gateway: `./mvnw -B -pl gateway test`
- Finance: `./mvnw -B -pl finance test`
- Reactor completo: `./mvnw -B test`

## Como debugar

- Veja logs com `docker compose logs -f auth`, `docker compose logs -f gateway` e `docker compose logs -f finance`.
- Healthchecks: `/actuator/health` em cada serviço.
- Para depurar localmente, conecte ao JDWP nas portas expostas pelo compose.
- Se a sessão falhar, valide primeiro auth; se um endpoint de negócio falhar, valide o tenant no token introspectado.

## Owners sugeridos

- Auth: time de identidade e segurança.
- Gateway: time de integrações e APIs públicas.
- Finance: time de domínio financeiro.
- Core/shared: time plataforma/backend.
