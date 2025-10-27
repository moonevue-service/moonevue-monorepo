# Uma arquitetura modular para gerenciamento financeiro: gateway, autenticação e microserviço de finance

Autor: Marcelo CP Junior  
Disciplina: TCC I  
Data: 2025-10-27

## Resumo
Este trabalho apresenta o desenvolvimento de uma aplicação modular para gerenciamento financeiro composta por três serviços independentes: gateway, auth e finance. O objetivo é demonstrar práticas de engenharia de software modernas — separação de responsabilidades, autenticação por sessão, documentação OpenAPI, versionamento de banco com migrações (Flyway), segurança em webhooks (HMAC) e deploy reprodutível por containers (Docker). A solução foi projetada para ser reprodutível em ambiente de desenvolvimento e facilmente implantada em ambiente de teste/produção.

Palavras-chave: microserviços, autenticação, Docker, OpenAPI, Flyway, webhooks

---

## 1. Introdução
Sistemas distribuídos e orientados a serviços têm se tornado padrão para aplicações web modernas, pois permitem escalabilidade, deploy independente e isolamento de responsabilidades. Para o TCC I, este projeto implementa uma plataforma mínima porém completa para demonstrar esses conceitos aplicados ao domínio financeiro: autenticação sólida, roteamento e segurança de borda (gateway) e um microserviço de domínio (finance) responsável por regras de negócio.

## 2. Objetivos
Objetivo geral:
- Construir e validar uma arquitetura modular e segura para um sistema financeiro de demonstração.

Objetivos específicos:
- Implementar fluxo de autenticação por sessão, com tratamento correto de renovação e revogação.
- Documentar a API com OpenAPI/Swagger facilitando integração com frontend.
- Controlar schema do banco com migrações (Flyway).
- Implementar recebimento de webhooks com validação HMAC e proteção contra replay.
- Empacotar serviços em containers e prover arquivos de orquestração (docker-compose) para replicação do ambiente.

## 3. Arquitetura do sistema
A arquitetura proposta é composta por três serviços Spring Boot independentes:

- auth: responsável por autenticação, criação e validação de sessões, emissão de cookies seguros e endpoints relacionados ao usuário.
- gateway: ponto de entrada público, valida sessão (introspecção via auth), aplica políticas de segurança, expõe APIs públicas e termina webhooks.
- finance: microserviço de domínio que contém endpoints específicos do negócio (ex.: contas bancárias vinculadas a contratantes).

Os serviços comunicam-se por HTTP em uma rede privada (quando orquestrados via Docker Compose) e compartilham um token interno (AUTH_INTERNAL_TOKEN) para chamadas de confiança entre serviços. O banco de dados utilizado é PostgreSQL.

Arquitetura lógica:
- Frontend <-> Gateway (público) -> Auth / Finance (internos)
- Gateway valida sessão via cookie e, quando necessário, consulta o auth.
- Webhooks do provedor bancário chegam ao gateway, que valida assinatura HMAC e reencaminha ou persiste para processamento.

## 4. Implementação

### 4.1 Autenticação e sessões
- Sessões persistidas na tabela `auth_session` contendo: id (UUID), user_id, created_at, last_seen_at, expires_at, ip_address, user_agent, revoked.
- Fluxo idempotente de login:
  - Se existe uma sessão ativa para o usuário, ela é reutilizada/renovada; se o cookie recebido pertence a outro usuário, a sessão antiga é revogada.
- Repositório fornece query para recuperar a sessão ativa mais recente:
  - `findFirstByUserAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtDesc(User, now)`.
- Recomenda-se criar índice parcial único para garantir no banco ao máximo uma sessão não revogada por usuário.

### 4.2 Endpoints e documentação
- As APIs são documentadas com OpenAPI/Swagger, garantindo visibilidade clara dos parâmetros de rota, query e schemas de request/response.
- Exemplo de endpoint de domínio:
  - `POST /api/contractors/{contractorId}/bank-accounts` — cria uma conta bancária associada a um contratante; `contractorId` é declarado como `@PathVariable("contractorId")` para que o Swagger apresente corretamente o campo no UI.
- Endpoints internos (webhooks, admin) são ocultados no Swagger com anotações apropriadas.

### 4.3 Migrações e persistência
- Flyway gerencia as migrations; em produção o `spring.jpa.hibernate.ddl-auto` está definido como `none`.
- Migrations versionadas aplicam criação de tabelas e índices (incluindo scripts de saneamento quando necessário).

### 4.4 Webhooks
- Webhooks terminam preferencialmente no gateway (`/webhooks/**`).
- Validação de assinatura HMAC (header `X-Signature`) com timestamp (`X-Signature-Timestamp`) para proteção contra requisições forjadas e replay (janela permitida configurável).
- Filtro de autenticação converte requisições válidas em uma autenticação interna com autoridade `WEBHOOK`.
- Processamento resiliente: recomenda-se persistir corpo/headers e usar idempotency-key para evitar processamento duplicado; o gateway pode repassar o evento ao serviço finance de forma síncrona ou via fila.

### 4.5 Containerização e deployment
- Cada serviço possui Dockerfile de produção (multi-stage Maven build + runtime Eclipse Temurin JRE) e Dockerfile de desenvolvimento (mvn spring-boot:run com DevTools).
- docker-compose.prod.yml orquestra os serviços e o banco; em produção recomenda-se usar Postgres gerenciado (Neon, RDS, Cloud SQL).
- Gateway é o único serviço público por padrão; auth e finance ficam na rede interna.
- Variáveis sensíveis (tokens, segredos HMAC, secrets do finance) devem ser definidas via environment/secret manager.

## 5. Segurança
- Cookies de sessão: em produção usar `Secure=true` e `SameSite` adequado — `None` + HTTPS para cross-site; `Lax` para same-site.
- Webhooks: HMAC + timestamp para validar autenticidade e prevenir replay attacks.
- Comunicação interna: AUTH_INTERNAL_TOKEN para chamadas confiáveis entre serviços.
- Princípio do menor privilégio: serviços expostos minimamente e endpoints administrativos ocultos/em perfil protegido.

## 6. Testes e observabilidade
- Actuator expõe `/actuator/health` e `/actuator/info` para healthchecks e monitoramento.
- Healthchecks integrados ao docker-compose permitem reinício automático de containers com falha.
- Logs padronizados no stdout/stderr para agregadores de logs em produção.
- Recomenda-se integração futura com Prometheus/Grafana para métricas e com central de logs (ELK/Cloud provider).

## 7. Resultados atuais
- Implementação funcional dos três serviços com endpoints principais, autenticação por sessão e documentação OpenAPI.
- Fluxo de login testado contra cenários de sessão duplicada (corrigido via query que retorna a sessão ativa mais recente).
- Webhooks terminam no gateway com validação básica; idempotência e persistência de eventos preparada para extensão.
- Dockerfiles e compose prontos para subir ambientes de desenvolvimento e teste; arquivos de produção organizados (docker-compose.prod.yml e .env.prod.example).

## 8. Limitações e trabalhos futuros
- Escalabilidade horizontal e orquestração: migrar para Kubernetes para alta disponibilidade e autoscaling.
- Persistência de arquivos: migrar para object storage (S3/GCS) em vez de volume local.
- CI/CD: automatizar build/publish das imagens (GitHub Actions para GHCR/Docker Hub).
- Observabilidade avançada e alertas: integrar métricas e tracing distribuído (OpenTelemetry).
- Segurança: rate-limiting, WAF e hardening adicional de endpoints administrativos.

## 9. Como executar (mínimo para validação local)
1. Build dos JARs:
   - `mvn -DskipTests -pl auth,gateway,finance -am package`
2. Construir imagens (exemplo local):
   - `docker build -t meuuser/moonevue-auth:prod -f auth/Dockerfile .`
   - `docker build -t meuuser/moonevue-gateway:prod -f gateway/Dockerfile .`
   - `docker build -t meuuser/moonevue-finance:prod -f finance/Dockerfile .`
3. Subir com compose de teste:
   - Copiar `.env.prod.example` para `.env` e preencher segredos.
   - `docker compose -f docker-compose.prod.yml up -d`
4. Testes:
   - Gateway (ponto único público): `http://localhost:8080/swagger-ui.html`
   - Auth (interno): `http://localhost:8081/actuator/health` (se exposto)
   - Finance: `http://localhost:8082/actuator/health` (se exposto)

## 10. Conclusão
O projeto entrega uma base técnica sólida para o TCC I, demonstrando domínio sobre desenho de serviços, autenticação segura, documentação e infra reproducível via containers. Mantendo os serviços desacoplados, a solução facilita evoluções (escala, novas features, hardening), ao mesmo tempo em que fornece um artefato prático para integração com front-end e demonstrações.

## Referências
- Spring Boot Reference Documentation
- Flyway Documentation
- Docker Documentation
- OWASP Secure Coding Practices
- OpenAPI / Swagger
