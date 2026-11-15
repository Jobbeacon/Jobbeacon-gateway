# JobBeacon Gateway

Spring Cloud Gateway (Spring Boot 3.3, Java 21, WebFlux). It is the **only public entry point** of JobBeacon.

| Path | Forwarded to | Route URI |
|---|---|---|
| `/jobbeacon-be/api/**` (+ Swagger, health) | backend (`Jobbeacon-be`) | `lb://jobbeacon-be` |
| `/jobbeacon-recommendation/api/**` (+ health) | recommendation service (rcm) | `lb://jobbeacon-recommendation` |

There are no fixed downstream URLs: the gateway is a Eureka client of `../Jobbeacon-discovery`, and `lb://<name>` routes are resolved to a registered instance by the Spring Cloud load balancer. A service that is not registered (yet) answers 503 through the gateway.

Every service serves under its own context path (= `spring.application.name`), so the gateway forwards paths unchanged. Only the listed sub-paths are routed: `/<service>/internal/**` is not reachable through the gateway.

## Auth flow (decision B2)

1. Client sends `Authorization: Bearer <Firebase ID token>` to the gateway.
2. Gateway verifies the token against Google's public keys (issuer `https://securetoken.google.com/<FIREBASE_PROJECT_ID>`, audience = project id). No service-account file is needed.
3. Gateway **removes every client-sent `X-User-*` header** (also on public routes) and removes `Authorization`.
4. For authenticated requests it adds `X-User-Id`, `X-User-Role`, `X-User-Email`, `X-User-Email-Verified`, `X-User-Name`, `X-User-Picture` (avatar URL from the `picture` claim, raw).
5. Backend/rcm do not verify Firebase tokens; they trust these headers as-is (there is no shared secret), so network isolation is the only protection. Missing `X-User-Id` means anonymous.

Invalid/missing tokens on protected paths get a JSON 401/403 (`status`, `message`, `data`, `timestamp`).

Public endpoints (no token needed) are configured in `application.yml` and must cover every backend endpoint that does not read `X-User-Id`:
- `app.public-paths` — any method: health, actuator, swagger / api-docs.
- `app.public-get-paths` — GET only: `/jobbeacon-be/api/v1/jobs/**`, `/jobbeacon-be/api/v1/locations/**`, `/jobbeacon-be/api/v1/categories`, `/jobbeacon-be/api/v1/companies/**`.

On a public endpoint a valid token still adds the `X-User-*` headers; an invalid or expired token is treated as anonymous instead of 401.

> **Warning:** backend and rcm must NOT be reachable from outside the internal network. Anyone who can reach them directly can forge identity headers. In docker-compose their ports are bound to `127.0.0.1` only.

### Requirements for downstream services
- Set `server.forward-headers-strategy: framework` so `X-Forwarded-*` from the gateway are honoured (correct scheme/host in redirects, Swagger, links).
- Decode `X-User-Name` with `URLDecoder.decode(value, StandardCharsets.UTF_8)` — the gateway form-URL-encodes it (UTF-8, space → `+`).

## Configuration

| Variable                  | Meaning |
|---------------------------|---------|
| `FIREBASE_PROJECT_ID`     | Firebase project id. **Required outside the `local` profile** — the app fails fast at startup if blank. The `local` profile falls back to `jobbeacon-local`. |
| `EUREKA_URL`              | Discovery server, default `http://localhost:8761/eureka/` (`http://discovery:8761/eureka/` in docker-compose). |
| `BACKEND_SERVICE_ID`, `RECOMMENDATION_SERVICE_ID` | Eureka names used in the route URIs, default `jobbeacon-be` / `jobbeacon-recommendation`. |
| `CORS_ORIGINS`            | Comma-separated allowed origins (default `http://localhost:3000,http://localhost:5173` when running locally; under docker-compose an unset value means empty). **Empty = all cross-origin requests are blocked.** CORS is handled only at the gateway; downstream services should not add CORS headers. |
| `SERVER_PORT`             | Default `8088`. |
| `PROFILE_ACTIVE`          | Default `local`. |

**Never commit `.env` or real secrets.**

## Run locally

Ports: gateway `8088`, discovery `8761`. Backend and rcm can run on any port: the gateway finds them through discovery.

```bash
# discovery (../Jobbeacon-discovery), backend and rcm running and registered, then:
mvn spring-boot:run                         # profile "local"
# or
FIREBASE_PROJECT_ID=your-project mvn spring-boot:run
```

Other commands:

```bash
mvn test
mvn -B -DskipTests package     # target/jobbeacon-gateway.jar
docker build -t jobbeacon-gateway .
```

## Docker Compose

The full stack (gateway, backend, recommendation, infrastructure) is defined in `../infras-deployment/docker-compose.yml`. The gateway is published on `${GATEWAY_PORT:-8088}`; backend has no host port and is reachable only through the gateway. Set `FIREBASE_PROJECT_ID` in `../infras-deployment/.env`.
