# JobBeacon Gateway

Spring Cloud Gateway (Spring Boot 3.3, Java 21, WebFlux). It is the **only public entry point** of JobBeacon.

| Path          | Forwarded to                  | Env var       | Local default           |
|---------------|-------------------------------|---------------|-------------------------|
| `/api/v1/**`  | backend (`Jobbeacon-be`)      | `BACKEND_URL` | `http://localhost:8081` |
| `/api/rcm/**` | recommendation service (rcm)  | `RCM_URL`     | `http://localhost:8082` |

## Auth flow (decision B2)

1. Client sends `Authorization: Bearer <Firebase ID token>` to the gateway.
2. Gateway verifies the token against Google's public keys (issuer `https://securetoken.google.com/<FIREBASE_PROJECT_ID>`, audience = project id). No service-account file is needed.
3. Gateway **removes every client-sent `X-User-*` and `X-Internal-Secret` header** (also on public routes) and removes `Authorization`.
4. For authenticated requests it adds `X-User-Id`, `X-User-Role`, `X-User-Email`, `X-User-Email-Verified`, `X-User-Name`, `X-User-Picture` (avatar URL from the `picture` claim, raw); on every forwarded request it adds `X-Internal-Secret`.
5. Backend/rcm do not verify Firebase tokens; they trust these headers and must reject requests with a missing or wrong `X-Internal-Secret`. Missing `X-User-Id` means anonymous.

Invalid/missing tokens on protected paths get a JSON 401/403 (`status`, `message`, `data`, `timestamp`).

Public endpoints (no token needed) are configured in `application.yml` and must mirror the backend's `permitAll` rules:
- `app.public-paths` — any method: health, actuator, swagger / api-docs.
- `app.public-get-paths` — GET only: `/api/v1/jobs/**`, `/api/v1/locations/**`, `/api/v1/categories`, `/api/v1/companies/**`.

On a public endpoint a valid token still adds the `X-User-*` headers; an invalid or expired token is treated as anonymous instead of 401.

> **Warning:** backend and rcm must NOT be reachable from outside the internal network. Anyone who can reach them directly can forge identity headers. In docker-compose they only use `expose`, never `ports`.

### Requirements for downstream services
- Set `server.forward-headers-strategy: framework` so `X-Forwarded-*` from the gateway are honoured (correct scheme/host in redirects, Swagger, links).
- Decode `X-User-Name` with `URLDecoder.decode(value, StandardCharsets.UTF_8)` — the gateway form-URL-encodes it (UTF-8, space → `+`).
- Use the same `GATEWAY_INTERNAL_SECRET` value as the gateway.

## Configuration

| Variable                  | Meaning |
|---------------------------|---------|
| `FIREBASE_PROJECT_ID`     | Firebase project id. **Required outside the `local` profile** — the app fails fast at startup if blank. The `local` profile falls back to `jobbeacon-local`. |
| `BACKEND_URL`, `RCM_URL`  | Downstream base URLs. |
| `CORS_ORIGINS`            | Comma-separated allowed origins (default `http://localhost:3000` when running locally; under docker-compose an unset value means empty). **Empty = all cross-origin requests are blocked.** CORS is handled only at the gateway; downstream services should not add CORS headers. |
| `GATEWAY_INTERNAL_SECRET` | Shared secret sent as `X-Internal-Secret`. Required outside `local`. |
| `SERVER_PORT`             | Default `8088`. |
| `PROFILE_ACTIVE`          | Default `local`. |

**Never commit `.env` or real secrets.**

## Run locally

Ports: gateway `8088`, backend `8081`, rcm `8082` (override with `BACKEND_URL` / `RCM_URL`).

```bash
# backend on 8081 (in ../Jobbeacon-be), rcm on 8082, then:
mvn spring-boot:run                         # profile "local"
# or
FIREBASE_PROJECT_ID=your-project GATEWAY_INTERNAL_SECRET=dev-secret mvn spring-boot:run
```

Other commands:

```bash
mvn test
mvn -B -DskipTests package     # target/jobbeacon-gateway.jar
docker build -t jobbeacon-gateway .
```

## Docker Compose

The full stack (gateway, backend, recommendation, infrastructure) is defined in `../infras-deployment/docker-compose.yml`. The gateway is published on `${GATEWAY_PORT:-8088}`; backend has no host port and is reachable only through the gateway. Set `FIREBASE_PROJECT_ID` and `GATEWAY_INTERNAL_SECRET` in `../infras-deployment/.env`.
