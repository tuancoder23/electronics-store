# Electronics Store frontend

React + TypeScript + Vite. This feature checks the connection to the Spring Boot health API.

## Run locally

```powershell
npm install
Copy-Item .env.example .env
npm run dev -- --port 5173 --strictPort
```

Keep an existing `.env` if already configured. The local default is:

```dotenv
VITE_API_BASE_URL=http://localhost:8080/api
```

Start the backend on port 8080 and open http://localhost:5173. The page displays loading, then `Backend online` with the health payload, or an error. Restart Vite after changing environment variables. Vite exposes `VITE_*` values to the browser; use only public configuration here.

`.env` is ignored by the repository; `.env.example` documents the required setting.

## API structure

- `src/api/axiosClient.ts`: shared Axios instance, environment base URL, JSON headers and a 10-second timeout. The request interceptor validates configuration and has a JWT integration TODO; no token storage or authentication flow is implemented.
- `src/api/healthApi.ts`: calls `/health`, unwraps `ApiResponse<HealthResponse>` and checks `success` and `status: UP`.
- `src/types/`: backend response contracts.
- `src/App.tsx`: temporary health screen with request cancellation on unmount, including React StrictMode cleanup.

Add components, pages, hooks or services when they are needed. The existing backend health endpoint is public, and its CORS configuration allows http://localhost:5173. Requests go directly to Spring Boot without a Vite proxy.

## Verification

```powershell
npm run build
npm run lint
```

With the backend running, load the page and verify `Backend online`. To check the failure state without stopping the backend, use the browser Network request blocking feature for `http://localhost:8080/api/health`, reload, then remove the block. Slow the network and reload to inspect loading.
