# Splynt

Inventory and sales insights for independent retailers. This development version includes account signup, organization/store workspaces, store-specific Clover OAuth, background inventory reconciliation, low-stock alerts, sales history and optional AI recommendations. The public website includes product discovery and a contact form with a private operator inbox.

Development happens on `dev/splynt-platform`. The live Render service must remain on its production branch. Changes here are not a release approval.

## Run locally

Requires Java 26, Node 22 (22.12 or later), npm and Docker. From the repository root, generate a local signing key and start the backend:

```sh
export SPLYNT_JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

Spring Boot starts the PostgreSQL service from `compose.yaml` and applies Flyway migrations. The compose database is for local development only. Keep the signing key stable across local restarts if you want existing sessions to remain valid; never commit it. If port 8080 is occupied, set `SERVER_PORT=8087` in this terminal before starting.

In another terminal:

```sh
npm --prefix frontend ci
npm --prefix frontend run dev
```

Open `http://localhost:5173`. If using backend port 8087, start Vite with `SPLYNT_API_PROXY=http://localhost:8087 npm --prefix frontend run dev`. Create an account to get an organization and initial store. Manual inventory works without Clover credentials. Configure the store's region, timezone and currency before importing products.

## Verify changes

```sh
./mvnw clean verify
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run build
```

GitHub Actions runs these checks, PostgreSQL migration/schema and HTTP tests, then builds the same Dockerfile used by Render. It does not publish an image or deploy. The PostgreSQL job uses an isolated disposable database, never production credentials.

## Integration and operations guides

- [Clover setup and import behavior](docs/CLOVER_SETUP.md)
- [Optional AI reports, evidence and limitations](docs/AI_RECOMMENDATIONS.md)
- [Contact inbox operations](docs/CONTACT_OPERATIONS.md)
- [Separate Render staging service](docs/RENDER_STAGING.md)
- [Implementation checkpoints and outstanding work](docs/IMPLEMENTATION.md)

## Current release limits

This branch is still under development. Live Clover authorization/import and real AI output quality have not been verified. Browser/visual acceptance remains outstanding. Imported Clover prices/costs require a verified two-decimal merchant currency and remain separate from local valuation costs. Location provides context for product experiments, not measured local-market demand. Historical comparisons require accumulated coverage; new connections initially import 90 days. Account email verification/password recovery are not yet self-service.

AI reports require server-side configuration and adequate sales evidence. Contact submissions are persisted to the operator inbox; they do not send email notifications. Do not describe these integrations as production-verified until the staging acceptance checks pass.
