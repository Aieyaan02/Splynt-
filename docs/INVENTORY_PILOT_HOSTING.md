# Inventory-only hosting handoff

Create a separate pilot service and database. Do not replace the existing Render service or reuse its database. The existing Dockerfile serves the React frontend and Java backend together on PORT (default 8080); health endpoint: /actuator/health.

Selected free pilot: a separate Render web service named splynt-store-pilot plus a new Neon project named splynt-pilot, deployed from branch pilot. This combination has not yet been deployed/validated for this pilot. Free hosting may sleep, and a 512 MB Java instance must be tested for startup and import memory before relying on it. Check current quotas in the provider dashboard. Do not promise always-on refresh on a free tier.

## Required settings

- SPRING_DATASOURCE_URL: JDBC PostgreSQL connection URL to the pilot database with TLS (sslmode=require); credentials via SPRING_DATASOURCE_USERNAME and SPRING_DATASOURCE_PASSWORD.
- SPLYNT_JWT_SECRET: independent random base64 secret of at least 32 bytes.
- SPLYNT_JWT_ISSUER: pilot HTTPS origin.
- CLOVER_ENCRYPTION_KEY: independent random 32-byte base64 key. Preserve securely across redeployments; losing it prevents decrypting stored Clover credentials.
- CLOVER_CLIENT_ID / CLOVER_CLIENT_SECRET: matching application's credentials, configured as host secrets.
- CLOVER_REDIRECT_URI: https://PILOT-HOST/api/integrations/clover/connect, registered with Clover.
- CLOVER_BASE_URL / CLOVER_AUTHORIZATION_URL: matching environment and merchant region; confirm against Clover's current documentation. Do not mix sandbox credentials with production URLs.
- SPLYNT_SALES_ENABLED=false: inventory-only backend; default is false. Frontend omits deferred panels regardless of this switch.
- SPLYNT_INTEGRATIONS_CLOVER_BACKGROUND_SYNC=false: Spring relaxed-binding setting to disable periodic import for the initial free-host pilot. Use Sync now. Do not enable minute-by-minute syncing without checking database compute allowance.
- SPLYNT_ACCOUNT_EMAIL_ENABLED=false until a real sender and supported delivery provider are configured and tested. Password recovery must not be advertised as operational before then.

Flyway applies migrations on startup, and Hibernate validates the schema. Use an empty pilot database for the real shop; do not import sample products. Before handling real data establish database backup/export and restore procedures; short free-tier restore windows are not a complete backup strategy.

## Acceptance before owner use

Verify HTTPS, successful startup/migrations, correct callback and OAuth cookie handling, user/store isolation, expected memory use during a representative catalog sync, accurate imported quantities, useful errors, and a successful restart with credentials still decryptable. Verify the selected provider's free-tier and outbound email limitations. Keep the old deployment unchanged until the owner decides otherwise.
