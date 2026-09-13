# Electronics Store frontend

React + TypeScript + Vite + Axios + React Router. MVP authentication, with the existing health screen preserved.

## Run locally

From `frontend/`:

```powershell
npm install
if (!(Test-Path .env)) { Copy-Item .env.example .env }
npm run dev -- --port 5173 --strictPort
```

`.env` is ignored. It contains public configuration only:

```dotenv
VITE_API_BASE_URL=http://localhost:8080/api
```

Restart Vite after environment changes. Start Spring Boot separately on port 8080.

## Verified backend contracts

Contracts were read from AuthController, UserController, LoginRequest, RegisterRequest, AuthResponse, UserResponse, AuthServiceImpl and GlobalExceptionHandler.

| Endpoint | Request | Response |
| --- | --- | --- |
| POST /api/auth/register | fullName, email, password, optional phone | 201, ApiResponse<AuthResponse> |
| POST /api/auth/login | email, password | 200, ApiResponse<AuthResponse> |
| GET /api/users/me | Bearer JWT | 200, ApiResponse<UserResponse> |

`ApiResponse<T>`: success, message, optional data, timestamp. Errors use success=false and message; validation returns the first field error, not a field-error map. AuthResponse contains accessToken, tokenType="Bearer", user. UserResponse contains id, fullName, email, nullable phone, role (USER/ADMIN), status (ACTIVE/INACTIVE), createdAt and updatedAt.

Register creates an ACTIVE USER and returns a JWT, so the frontend automatically starts a session. Full name is required and limited to 150 characters; registration email to 255; password to 8–72 characters and 72 UTF-8 bytes; optional phone follows the backend's 7–30 character pattern. Password whitespace is preserved. Login requires a valid nonblank email and a nonblank password without applying registration length rules.

## Authentication flow

- `/login` and `/register` use controlled inputs, validation, a pending state, disabled submit and friendly errors. Password is only held in form memory and cleared after each submitted request.
- Both flows persist the returned accessToken, then call `/users/me`. Only a successful current-user response establishes authenticated state and its role.
- Startup with a saved token also calls `/users/me`; storage alone never grants access.
- `ProtectedRoute` waits during verification, redirects guests to `/login`, and renders `/account` for a verified user. `GuestRoute` redirects signed-in users to `/account`.
- A network/server failure while verifying keeps the token but blocks account access and offers retry or return to login.
- `/account` displays backend-provided name, email, role and optional phone. Logout removes the token, clears state, cancels pending requests and returns to login through the route guard.
- Token changes in another tab revalidate or clear the local session. Cancelled/stale responses cannot restore a logged-out session.
- `/health` preserves the previous backend health check. Unknown routes show a simple not-found page.

## JWT and Axios

`src/auth/authToken.ts` centralizes localStorage access under the key `authToken`; no password or user/role data is persisted. localStorage is an MVP choice: its token is accessible to JavaScript and must be protected against XSS.

The existing Axios client keeps the environment base URL, JSON headers and 10-second timeout. It sends Authorization: Bearer <token> when a token exists, except on login/register, where an expired token must not block credential exchange. It sends no Authorization header without a token.

A 401 clears the session only if the failed request used the current stored token. A delayed 401 for an older token cannot clear a newer session. Interceptors do not redirect or retry. Route guards perform navigation. 403 preserves the session. 400/409 show backend validation/conflict messages; 401/403/5xx, timeouts and network failures have friendly messages, with no raw server stack traces.

There is no refresh-token or server logout endpoint in the existing contract. Logout ends the local session; an already issued JWT remains valid at the backend until expiry. Backend authorization remains authoritative; frontend route guards are navigation controls.

## Structure

- `src/api/`: shared Axios client, auth API and health API.
- `src/types/`: backend response and request contracts.
- `src/auth/`: token utility, context/provider, hook, validation and error formatting.
- `src/components/`: shared auth form, session status and route guards.
- `src/pages/`: login, register, account and preserved health page.
- `tests/auth.test.tsx`: React/context/router/Axios tests in jsdom with simulated API responses.

Existing backend CORS allows http://localhost:5173 and Authorization headers. No backend changes are required by the reviewed contract.

## Verification

```powershell
npm run lint
npm test
npm run build
```

Automated tests exercise login/register, token persistence and Bearer headers, current-user verification, route guards, logout, invalid tokens, transient network errors, storage errors, cross-tab logout, stale responses and validation/error boundaries. These tests use an Axios adapter with simulated responses; they do not prove live Spring Boot integration or browser CORS.

To verify against a running backend:

1. Open /account as a guest; expect /login.
2. Register a unique test account; expect /account with backend user data.
3. Reload /account; verify GET /api/users/me succeeds with Authorization.
4. Log out; verify authToken is removed and /account redirects to /login.
5. Log in with that account; verify the same profile is loaded.
6. Try incorrect credentials and duplicate registration; verify readable errors.
7. Replace authToken with an invalid value in your local browser tools and reload; expect token removal and /login.
8. Verify an authenticated visit to /login or /register redirects to /account.

During this implementation, localhost:8080 was unreachable. Live register/login/current-user, real JWT validity and browser CORS therefore remain unverified. No real test account was created.

When hosting the production build, configure SPA fallback to index.html so direct navigation to /account, /login and /register works.
