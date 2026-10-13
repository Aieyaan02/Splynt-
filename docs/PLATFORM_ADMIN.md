# Private platform administration

Separate portal: target `https://admin.splynt.com/`, local `http://127.0.0.1:5174/`. The store frontend no longer provides the platform screen. No public domain or cloud deployment is configured by this change.

## Separate frontend deployment

Run `npm --prefix frontend run dev:admin` for the local portal (API proxy defaults to localhost:8087). Build with `npm --prefix frontend run build:admin`; publish **only** `frontend/dist-admin` to the admin host. The usual store build produces `frontend/dist` without monitoring UI. CI builds both artifacts. They share branding and dependencies, but have separate entry points and browser sessions; the admin token is not copied from the store app.

Production hosting must proxy `/api` on the admin origin over HTTPS to the intended Splynt backend, preserving Authorization and disabling response caching. The backend still enforces the singleton admin permission on every monitoring request; a separate hostname alone grants no access. The portal uses the existing backend/database, not a second copy of merchant data. DNS, HTTPS, hosting and the production proxy remain to be configured after approval. Never place backend secrets in frontend configuration.

## Access model

The `platform_admin` table permits exactly one designated user. No public API or signup field creates this grant. Organization OWNER/ADMIN roles never grant platform access. Access checks require the designated ID, enabled account, verified identity and current credential version on every request. Revocation or disabling takes effect on subsequent requests. A separate account is provisioned without an organization/store membership.

The dashboard is read-only. It displays platform organization/store/user counts, seven-day registrations, paginated newest users and organizations, login and approximate API activity, connection/sync state, last failed sync, fixed safe error text, received/processed product counts and stores needing attention. Queries explicitly select non-secret columns and responses use no-store caching.

## Private setup (owner action required)

The requested email is in ignored `.env.platform.local`, not in Git. Do not put a password in chat, commit messages, frontend variables or command-line arguments.

1. From your own terminal in this repository, run `./scripts/set-platform-password.sh`. Enter a unique password twice at the hidden prompts. The command writes only a bcrypt hash into `.env.platform.local` with owner-only permissions. Bootstrap remains disabled.
2. Review the email. An existing registered email is rejected; the bootstrap will not promote an existing store user. If this email already exists, select a different dedicated address or design an explicit reviewed migration.
3. When deploying the approved pilot, put the email and password hash directly into the host's secret configuration as `SPLYNT_PLATFORM_BOOTSTRAP_EMAIL` and `SPLYNT_PLATFORM_BOOTSTRAP_PASSWORD_HASH`. Set `SPLYNT_PLATFORM_BOOTSTRAP_ENABLED=true` for provisioning. This asserts that the server operator has verified ownership of the admin identity. Normal email verification remains unchanged.
4. Start against the intended persistent database with migration V25 applied. Provisioning creates a single account/grant transactionally. If the grant already exists, no additional admin is created. Check login at the separate admin portal.
5. Disable bootstrap and remove the bootstrap password hash from host configuration after success. The database retains only the normal password hash. The private local file is not automatically loaded by the app.

For a local preview, use a persistent local database and explicitly supply the same private properties. Do not reset an existing preview database just to provision an account. The local admin preview uses an ignored persistent H2 database under `.env.admin-preview` with migrations enabled. This is separate from the eventual Neon pilot database. Local provisioning does not create a production account.

## Metric definitions and limits

- Counts cover this application's connected database, including inactive records; active counts are shown separately. They do not combine the demo, pilot and other deployments.
- Last activity is a successful authenticated API response, including background browser reads, written at most once per five minutes. It is not an exact human-presence indicator. Old activity remains null.
- Last failure timestamps and imported counts start with this migration. Historical missing values stay unknown; no provider errors/tokens are returned.
- Last imported count means created + updated products in the last completed import, not a lifetime total or inventory unit count. Received count and current active imported product count are shown separately.
- Attention flags active stores with no connection, no success, data over 24 hours old or current failure/review warnings. Free-service sleeping can cause stale syncs. A saved connection is not proof the provider currently authorizes requests.
- Application/database health is a point-in-time result from a responding process and successful DB reads. A DB outage can also prevent admin authorization. The UI then shows monitoring unavailable and clears prior health data; it never claims a healthy current snapshot. Independent uptime alerts are future work.

## Verification / future hardening

Tests cover private provisioning, actual JWT login, store-owner denial, disabled/unverified/revoked/stale admin denial, pagination/counts, safe telemetry DTOs, activity throttling and attention criteria. PostgreSQL CI includes platform tests. No live credentials or merchant API calls are needed.

Before a wider public launch: configure HTTPS and trusted edge rate limits, independent uptime/security alerts, database backups, audit retention and a rehearsed admin-account recovery procedure. Authenticator MFA, login throttling and database audit records are implemented below. This page does not provide impersonation, destructive controls, customer exports or financial data.

## Authentication hardening

Platform monitoring now requires password sign-in **and** authenticator verification. Previously issued password-only tokens cannot read monitoring data. After sign-in, set up a time-based authenticator using the private setup key and verify its six-digit code. Save the eight recovery codes offline; each is single-use and is shown only once. An elevated admin token expires after 15 minutes. Verification requires a password session issued within five minutes. Used time steps cannot be replayed; a ±30-second clock tolerance is allowed.

Set `SPLYNT_PLATFORM_MFA_KEY` to an independently generated 32-byte base64 secret in backend secret configuration before enrollment. Back it up securely with your database recovery plan; never rotate it blindly, commit it, or place it in frontend variables. Authenticator secrets are encrypted with AES-GCM; recovery codes are hashed. Password resets do not remove MFA. Losing both authenticator and recovery codes requires an operator-reviewed database recovery: verify identity offline, revoke existing sessions by incrementing `app_users.credential_version`, remove only that admin's MFA and recovery-code rows in a transaction, and re-enroll. There is intentionally no public MFA-disable endpoint.

Both store and admin password logins are limited to 10 attempts per normalized account per 15 minutes and 60 attempts per network peer per 15 minutes. MFA verification allows five attempts per account per five minutes; enrollment allows three per 15 minutes. Counters are database-backed and bounded to 10,000 active keys. Account identities are hashed in counters. Forwarded IP headers are not trusted by the limiter; behind a proxy, the peer allowance is shared, so configure trusted edge protection and review limits before larger deployments. These controls are not DDoS protection and can temporarily delay legitimate sign-ins.

The security activity view records password-login outcomes and admin access/MFA outcomes, timestamps and authenticated actor IDs, without payloads, codes, tokens, email addresses or query strings. It is database-resident, not tamper-proof against database administrators. Invalid bearer tokens rejected before the audit filter are not captured here; production edge logs and external alerts remain necessary. Establish an audit retention/export policy before public launch.

Local activation: stop the old preview backend, then run `python3 scripts/start-admin-preview.py`. This preserves `.env.admin-preview/database`, creates a private MFA encryption key if missing, disables bootstrap and starts the backend. Keep this Terminal open. Run `npm --prefix frontend run dev:admin` in another Terminal if the admin frontend is stopped. Then sign in and enroll your authenticator yourself; never send a setup key, code or recovery code in chat.
