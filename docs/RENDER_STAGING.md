# Render staging and release handoff

This guide prepares a separate testing environment. It does not authorize replacing the live service or merging this branch. No Render service settings have been verified or changed by this implementation.

## Keep development separate

1. Inspect the existing live service's Settings → Build & Deploy. Record its linked repository, production branch, deployed commit and auto-deploy policy. Leave it on that branch (commonly `main`), never `dev/splynt-platform`.
2. Create a **new** PostgreSQL database for staging. Do not reuse the production database, even for a short test: Flyway applies migrations automatically at startup.
3. Create a **new** Docker web service from this repository, branch `dev/splynt-platform`, Dockerfile `./Dockerfile`, repository root as build context. Choose a distinct name such as `splynt-staging`. Use its own onrender.com hostname; leave the production custom domain attached to production.
4. Start with staging auto-deploy disabled. After a passing GitHub Actions run, manually deploy that verified commit. Render can also be configured to deploy after CI checks pass. Creating services may incur hosting charges; select the plan intentionally.
5. Configure staging-only environment variables below. Do not attach production environment groups. Use a Clover sandbox application and test merchant. Leave AI disabled until an intentional provider test is ready.

## Environment

| Variable | Staging value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` (production runtime settings also apply to staging) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<staging-internal-host>:5432/<database>`; use the new database's internal host in the same region |
| `SPRING_DATASOURCE_USERNAME` | New staging database user |
| `SPRING_DATASOURCE_PASSWORD` | New staging database password |
| `SPLYNT_JWT_SECRET` | Independent base64 key generated with `openssl rand -base64 32` |
| `SPLYNT_JWT_ISSUER` | The staging HTTPS origin |
| `CLOVER_ENCRYPTION_KEY` | A different base64 32-byte key; preserve it across deploys so saved Clover tokens remain decryptable |
| `CLOVER_CLIENT_ID`, `CLOVER_CLIENT_SECRET` | Staging Clover application credentials |
| `CLOVER_BASE_URL` | `https://apisandbox.dev.clover.com` |
| `CLOVER_AUTHORIZATION_URL` | `https://sandbox.dev.clover.com/oauth/v2/authorize` |
| `CLOVER_REDIRECT_URI` | `https://<staging-host>/api/integrations/clover/connect`; register this exact URI in the Clover app |
| `OPENAI_API_KEY`, `SPLYNT_AI_MODEL` | Optional; see AI guide. Keep unset for initial staging checks. |
| `SPLYNT_CONTACT_OPERATOR_EMAILS` | Optional explicit operator allowlist; verified email ownership is also required, see contact guide |

Render supplies `PORT`; the application honors it. Render's Postgres URL is not directly a JDBC URL: provide host/database in the format above and credentials separately. Do not paste secrets into frontend variables, repository files, build commands or logs. The image runs as user 10001 and bundles the React build into Spring Boot.

Use `/actuator/health` as the HTTP health-check path after confirming access on staging. A green health check proves runtime health, not successful Clover or AI integration.

## Acceptance before considering release

- Create two unrelated accounts; confirm each sees only its own stores, products, reports and movements. Sign out and sign back in.
- Configure a store's regional settings, then connect a sandbox Clover merchant through the browser. Check cancellation, expired authorization and reconnect behavior.
- Import known catalog fixtures: normal, fractional, negative and missing stock; barcode conflict; deleted item; multiple categories. Compare with Clover directly. Confirm low-stock results, unknown-state messaging and history. Some catalog fields remain incomplete as described in README.
- Repeat sync with unchanged data; confirm no duplicate adjustments. Change stock in Clover, then confirm background refresh while the dashboard is closed. Reopen and inspect sync timestamps.
- Verify paid sales, changed/refunded orders and local-time boundaries against the sandbox records. Inventory adjustments must never count as sales.
- Configure an AI test only after adequate legitimate test sales coverage exists. Inspect report claims against the saved evidence; verify cooldown, failure handling and previous-report retention. Do not manufacture historical coverage to bypass eligibility.
- Submit a contact inquiry and read it as a verified allowlisted operator. Confirm ordinary accounts cannot read the inbox.
- Inspect mobile/desktop layout, keyboard navigation, dialogs, errors and loading states in a permitted browser. Automated builds do not prove visual quality.

Record the exact commit, database migration version and results. Do not mark this list complete solely because automated tests pass.

## Release and recovery

Production promotion requires a separate release decision after staging acceptance. Back up production and confirm the restore procedure before applying new migrations. A Render code rollback does **not** roll back database migrations. Verify old-code compatibility or restore to a separate database and plan recovery explicitly; never delete Flyway history to force startup.

The CI workflow contains no deploy hooks, Render API tokens or image publishing. This guide creates no resources by itself.

References: [Render deployments](https://render.com/docs/deploys), [Docker services](https://render.com/docs/docker), [health checks](https://render.com/docs/health-checks), [preview environments](https://render.com/docs/preview-environments).
