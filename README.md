# Moonevue Monorepo

This repository is now the single development home for:

- frontend: `moonevue-front-end` (imported to `apps/frontend`)
- backend: `moonevue-back-end` (imported to `services/backend`)

The backend keeps its existing Spring multi-service architecture:

- gateway (public)
- auth (private)
- finance (private)

## Monorepo Layout

```text
.
├── apps/
│   └── frontend/
├── services/
│   └── backend/
├── docker-compose.yml
├── .env.example
├── package.json
└── .do/
	└── app.yaml
```

## What Was Implemented First

- Imported both source repositories with preserved git ancestry (merge strategy with unrelated histories).
- Added root npm workspace configuration.
- Added root local Docker Compose for full-stack development.
- Added initial DigitalOcean App Platform spec template.
- Added frontend development Dockerfile to run Next.js inside Compose.

## Quick Start

### 1. Configure environment

```bash
cp .env.example .env
```

### 2. Start full stack locally

```bash
docker compose up --build
```

Expected services:

- Frontend: `http://localhost:3000`
- Gateway: `http://localhost:8080`
- Auth: `http://localhost:8081`
- Finance: `http://localhost:8082`

### 3. Workspace scripts

```bash
npm run dev:frontend
npm run dev:backend
npm run dev
```

## DigitalOcean App Platform

Base spec is at `.do/app.yaml`.

Current topology in spec:

- web service: frontend
- web service: gateway (public API)
- worker/private service: auth
- worker/private service: finance
- managed postgres database

Before applying in DigitalOcean, review and set:

- repository source information
- domains and ingress behavior
- secrets (database URL, internal token, webhook secret)
- cookie and CORS values for your production domain

## Notes

- Backend source remains Maven-native under `services/backend`.
- Root npm workspaces are currently used for frontend and shared JS tooling.
- Existing backend docker and runtime behavior were preserved as much as possible in this first pass.
