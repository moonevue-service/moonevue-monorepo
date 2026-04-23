# Moonevue Frontend-Backend Integration Guide

## Overview

I've successfully integrated the frontend with the backend APIs. The frontend now includes:

✅ **Authentication System** - Register, Login, Logout with session management
✅ **Dashboard** - Protected routes with sidebar navigation
✅ **Bank Account Management** - Create, view, and delete bank accounts
✅ **Transaction Management** - Create payments and view transaction history
✅ **Settings Page** - Account information and security settings

## Quick Start

### 1. Start the Stack
```bash
cd /workspaces/moonevue-monorepo
docker compose up --build
```

Wait for all services to be healthy:
- Frontend: http://localhost:3000
- Backend/Gateway: http://localhost:8080
- Auth Service: http://localhost:8081
- PostgreSQL: localhost:5432

### 2. Access the Application

#### First Time Users
1. Go to http://localhost:3000
2. You'll be redirected to `/login`
3. Click "Crie uma" (Create an account)
4. Fill in the registration form:
   - **Nome da Empresa** (Company Name): e.g., "Minha Empresa"
   - **CNPJ/CPF**: Any valid format (e.g., "00.000.000/0000-00")
   - **Email**: e.g., "user@example.com"
   - **Senha**: Password
   - **Confirmar Senha**: Confirm password

5. After registration, you'll be logged in and redirected to the dashboard

#### Returning Users
1. Go to http://localhost:3000
2. You'll see the login page
3. Enter your email and password
4. Click "Entrar" (Sign in)

### 3. Features to Test

#### Dashboard
- View welcome message and quick stats
- See account information (Tenant ID, Email, Roles)
- Follow "Primeiros Passos" (Getting Started) guide

#### Bank Accounts (`/dashboard/bank-accounts`)
- Click "Nova Conta" (New Account)
- Fill in bank account details:
  - **Nome da Conta**: Account name
  - **Banco**: Select a bank (Bradesco, Itaú, Santander, etc.)
  - **Agência**: Agency code
  - **Conta**: Account number
  - **Dígito**: Check digit
  - **Tipo de Conta**: Account type (Checking or Savings)
- Click "Adicionar Conta" (Add Account)
- View, edit, or delete accounts

#### Transactions (`/dashboard/transactions`)
- Click "Novo Pagamento" (New Payment)
- Fill in payment details:
  - **Banco**: Select a bank
  - **Configuração Bancária**: Bank configuration ID (from backend)
  - **Valor**: Amount in R$
  - **Descrição**: Payment description
- Click "Processar Pagamento" (Process Payment)
- View transaction history

#### Settings (`/dashboard/settings`)
- View account information
- Copy Tenant ID
- See assigned roles
- Logout from your account

## Architecture

### Frontend Structure
```
apps/frontend/
├── lib/api/           # API client and service layer
├── app/               # Pages and layouts
│   ├── login/         # Login page
│   ├── register/      # Registration page
│   └── dashboard/     # Protected dashboard
└── components/ui/     # shadcn/ui components
```

### Tech Stack
- **Framework**: Next.js 15+ with App Router
- **Styling**: Tailwind CSS + shadcn/ui
- **State Management**: React Context (Auth)
- **HTTP Client**: Native Fetch API
- **Session**: Cookie-based (auto-refreshed)

### API Integration
- **Base URL**: http://localhost:8080
- **Auth Endpoints**: `/auth/*`
- **Finance Endpoints**: `/api/tenant/{tenantId}/*`
- **Payment Endpoints**: `/payments`
- **Session Management**: Automatic via cookies

## Testing Tips

### Test Authentication Flow
1. Register a new account
2. Check that session cookie is set (DevTools → Application → Cookies)
3. Refresh the page - you should stay logged in
4. Go to settings and click "Sair" (Logout)
5. Try accessing `/dashboard` - should redirect to login

### Test Bank Accounts
1. Go to Bank Accounts page
2. Add a new account
3. Verify it appears in the list
4. Try deleting it
5. The account should be removed from the list

### Test Transactions
1. Ensure you have a bank account configured
2. Go to Transactions page
3. Click "Novo Pagamento"
4. Try to create a payment with a valid configuration ID
5. Check response in the transaction list

### Test Error Handling
1. Try logging in with wrong credentials
2. Try registering with an existing email
3. Try submitting forms with missing fields
4. Check error messages display correctly

## Environment Variables

Frontend uses:
```
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

This is set in the docker-compose.yml and can be overridden if needed.

## Troubleshooting

### Cannot access http://localhost:3000
- Check if frontend container is running: `docker compose ps`
- Check logs: `docker compose logs frontend`

### Getting "Unauthorized" errors
- Make sure backend is running: `docker compose logs gateway`
- Check that cookies are included in requests (should be automatic)
- Try logging out and logging back in

### Bank account operations fail
- Verify you have a valid Tenant ID (check in dashboard/settings)
- Check backend logs: `docker compose logs gateway`
- Ensure PostgreSQL is healthy: `docker compose logs postgres`

### Styling looks broken
- Clear browser cache (Ctrl+Shift+Delete or Cmd+Shift+Delete)
- Rebuild containers: `docker compose down && docker compose up --build`

## Next Steps for Enhancement

1. **Bank Configuration UI** - Add interface to upload certificates
2. **Employee Management** - Invite and manage team members
3. **Webhook Management** - Configure webhooks for notifications
4. **Transaction Filtering** - Add date, status, and amount filters
5. **Real-time Updates** - WebSocket for transaction status
6. **Reports & Analytics** - Charts and revenue reports
7. **Mobile Responsive** - Optimize for mobile devices
8. **Internationalization** - Multi-language support

## Support

For issues or questions:
1. Check the backend health: `/actuator/health`
2. Review browser console for errors
3. Check Docker logs: `docker compose logs [service]`
4. Ensure all services are running: `docker compose ps`

Happy testing! 🚀
