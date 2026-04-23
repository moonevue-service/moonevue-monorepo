# API Integration Technical Documentation

## Overview

The Moonevue frontend integrates with the backend APIs using a type-safe client layer built on top of the native Fetch API. All requests include credentials for cookie-based session management.

## API Client Architecture

### Base Client (`lib/api/client.ts`)

```typescript
ApiClient.get<T>(endpoint: string)
ApiClient.post<T>(endpoint: string, body?: any)
ApiClient.put<T>(endpoint: string, body?: any)
ApiClient.delete<T>(endpoint: string)
```

**Features:**
- Automatic JSON serialization/deserialization
- Cookie credentials included in all requests
- Error handling with typed `ApiError` response
- Support for 204 No Content responses
- Base URL from environment variables

### Auth API (`lib/api/auth.ts`)

```typescript
AuthApi.register(data: RegisterRequest) → Promise<AuthResponse>
AuthApi.login(data: LoginRequest) → Promise<AuthResponse>
AuthApi.logout() → Promise<void>
AuthApi.introspect() → Promise<User>
AuthApi.touch() → Promise<void>
AuthApi.createEmployee(data: EmployeeRegisterRequest) → Promise<AuthResponse>
```

**Types:**
```typescript
interface RegisterRequest {
  tenantName: string;
  tenantDocument: string;
  email: string;
  password: string;
  confirmPassword: string;
}

interface User {
  userId: number;
  email: string;
  tenantId: number | null;
  roles: string[];
}
```

### Finance API (`lib/api/finance.ts`)

```typescript
// Bank Accounts
FinanceApi.createBankAccount(tenantId: number, data: BankAccountRequest) 
FinanceApi.updateBankAccount(tenantId: number, bankAccountId: number, data: BankAccountRequest)
FinanceApi.deleteBankAccount(tenantId: number, bankAccountId: number)

// Bank Configurations
FinanceApi.createBankConfiguration(tenantId: number, bankAccountId: number, data)
FinanceApi.updateBankConfiguration(tenantId: number, bankAccountId: number, configId: number, data)
FinanceApi.uploadCertificate(tenantId: number, bankAccountId: number, configId: number, file: File, password?: string)
```

**Key Enums:**
```typescript
enum BankType {
  BRADESCO, ITAU, SANTANDER, CEF, BB
}

enum AccountType {
  CHECKING, SAVINGS
}

enum Environment {
  SANDBOX, PRODUCTION
}
```

### Payments API (`lib/api/payments.ts`)

```typescript
PaymentApi.createPayment(data: ChargeRequestDTO) → Promise<ChargeResponseDTO>
```

**Types:**
```typescript
interface ChargeRequestDTO {
  bank: BankType;
  bankConfigurationId: number;
  payment: {
    instrument: string;
    amount: number;
    description?: string;
  };
}

interface ChargeResponseDTO {
  id: string;
  status: TransactionStatus;
  amount: number;
  locId?: string;
  location?: string;
}
```

## Authentication Flow

### Session Management

1. **Initial Login/Register**
   ```
   POST /auth/register or /auth/login
   Response Headers: Set-Cookie: sid=<session-uuid>
   ```

2. **Session Persistence**
   - Session cookie stored in browser
   - All subsequent requests include cookie via `credentials: 'include'`
   - Backend validates session from cookie

3. **Session Refresh**
   - Frontend calls `AuthApi.touch()` every 5 minutes
   - Backend returns fresh cookie if needed
   - Automatic if session approaching expiration

4. **Session Introspection**
   - Frontend calls `AuthApi.introspect()` on app load
   - Returns current user info or 401 Unauthorized
   - Used to verify session validity

5. **Logout**
   ```
   GET /auth/logout
   Response Headers: Set-Cookie: sid=; Max-Age=0
   ```

## Error Handling

### Error Types

```typescript
interface ApiError {
  status: number;
  message: string;
  data?: any;
}
```

### Common Error Scenarios

| Status | Scenario | Handling |
|--------|----------|----------|
| 400 | Validation error | Display error message to user |
| 401 | Unauthorized | Redirect to login |
| 403 | Forbidden | Show permission error |
| 404 | Not found | Show 404 error |
| 409 | Conflict | Show conflict error (e.g., duplicate) |
| 500+ | Server error | Show generic error message |

## Request Examples

### Register User
```typescript
const response = await AuthApi.register({
  tenantName: "Acme Corp",
  tenantDocument: "00.000.000/0000-00",
  email: "user@example.com",
  password: "secure123",
  confirmPassword: "secure123"
});
// Returns: { tenantId, userId, email }
```

### Create Bank Account
```typescript
const response = await FinanceApi.createBankAccount(123, {
  name: "Conta Principal",
  bank: BankType.BRADESCO,
  cdAgency: "1234",
  cdAccount: "567890",
  cdAccountDigit: "0",
  accountType: AccountType.CHECKING,
  active: true
});
// Returns: BankAccountResponse with id
```

### Create Payment
```typescript
const response = await PaymentApi.createPayment({
  bank: BankType.BRADESCO,
  bankConfigurationId: 456,
  payment: {
    instrument: "PIX",
    amount: 150.50,
    description: "Invoice #123"
  }
});
// Returns: { id, status, amount, ... }
```

### Upload Certificate
```typescript
const response = await FinanceApi.uploadCertificate(
  123,  // tenantId
  456,  // bankAccountId
  789,  // configId
  file, // File object
  "password123" // optional password
);
// Returns: { certificatePath, expiresAt }
```

## Context & Hooks

### Auth Context

```typescript
const {
  user,                    // Current user or null
  isLoading,              // Initial load state
  isAuthenticated,        // Boolean
  error,                  // Last error message
  login,                  // (email, password) => Promise<void>
  logout,                 // () => Promise<void>
  register,               // (data) => Promise<void>
  refreshSession,         // () => Promise<void>
} = useAuth();
```

**Usage:**
```typescript
import { useAuth } from '@/app/providers';

function MyComponent() {
  const { user, isAuthenticated, login } = useAuth();
  
  if (!isAuthenticated) {
    return <div>Not logged in</div>;
  }
  
  return <div>Welcome {user?.email}</div>;
}
```

## Protected Routes

### Usage
```typescript
import { ProtectedRoute } from '@/app/protected-route';

export default function Page() {
  return (
    <ProtectedRoute>
      <YourContent />
    </ProtectedRoute>
  );
}
```

**Behavior:**
- If loading: Shows spinner
- If not authenticated: Redirects to /login
- If authenticated: Shows content

## Configuration

### Environment Variables

```
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

Default value in `lib/api/client.ts`:
```typescript
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';
```

### Cookie Configuration (Backend)

The backend sets cookies with these properties (from application.yml):
- Domain: Set via `AUTH_COOKIE_DOMAIN`
- Secure: Set via `AUTH_COOKIE_SECURE`
- SameSite: Set via `AUTH_COOKIE_SAMESITE`
- Max-Age: Set via `AUTH_COOKIE_MAX_AGE_SECONDS`

Frontend respects these automatically.

## Debugging

### Check Network Requests
1. Open DevTools → Network tab
2. Filter by "Fetch/XHR"
3. Check request/response headers
4. Look for cookie in request headers

### Check Session Cookie
1. DevTools → Application tab
2. Cookies → http://localhost:3000
3. Look for "sid" cookie
4. Verify it has a UUID value

### Enable Debug Logging
```typescript
// In lib/api/client.ts
console.log('Request:', method, endpoint);
console.log('Response:', status, data);
```

### Common Issues & Solutions

**Issue**: Getting 401 Unauthorized
- **Solution**: Session cookie missing or expired
- **Action**: Log out and log back in via `/login`

**Issue**: CORS errors
- **Solution**: Backend not allowing credentials
- **Check**: Backend has proper CORS configuration

**Issue**: Cookies not persisting
- **Solution**: Browser privacy settings blocking cookies
- **Action**: Check browser cookie settings

**Issue**: Environment URL not loading
- **Solution**: Incorrect `NEXT_PUBLIC_API_BASE_URL`
- **Action**: Check `.env.local` or docker-compose config

## Performance Considerations

1. **Session Refresh Interval** - Currently 5 minutes
   - Located in `app/providers.tsx`
   - Can be adjusted based on need

2. **Error State** - Errors cleared on successful operations
   - Users see most recent error only
   - Consider adding error history for debugging

3. **Request Caching** - Currently no caching
   - Could implement React Query/SWR for caching
   - Important for frequently accessed data

4. **Credential Handling**
   - Passwords sent over HTTPS only in production
   - Consider PKCE for OAuth if scaling
   - Session tokens validated by backend

## Security Notes

1. **HTTPS in Production** - Always use HTTPS
2. **Secure Cookies** - Must set `Secure` flag on cookies
3. **CSRF Protection** - Backend handles via SameSite cookies
4. **Content Security Policy** - Can be added via Next.js headers
5. **XSS Prevention** - React handles automatic escaping

## Future Enhancements

1. Implement request/response interceptors
2. Add retry logic for failed requests
3. Implement offline queue for operations
4. Add request caching layer
5. Implement GraphQL layer
6. Add API versioning handling
7. Implement request throttling
