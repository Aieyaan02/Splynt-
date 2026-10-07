# Splynt platform implementation

Branch: dev/splynt-platform. Never push main or deploy production as part of development.

## Accepted objective
Build a usable retail inventory SaaS: signup, connect a merchant's own Clover store, import stock, ongoing synchronization and low-stock dashboard. Polish responsive onboarding/dashboard and add a public discovery/contact website. Prepare a follow-up intelligence feature for sales velocity, time, season and location with honest data requirements.

## Delivery gates
- Store-specific OAuth with expiring, single-use state; tokens never exposed to the browser.
- Isolated credentials per store, merchant identity verified, role-restricted integration changes.
- Paginated inventory import, safe missing-stock handling, reconciliation audit, preserve archives, concurrency safety.
- Background synchronization with visible connection state, failures and last successful refresh.
- Signup → connection → imported dashboard browser flow, recoverable errors and useful empty states.
- Search/filter/edit products and reorder settings; accessible responsive UI.
- Public marketing and working contact path without fabricated testimonials or customer claims.
- Sales-history foundation and explainable insights; never infer sales from stock adjustments.
- Automated backend/frontend checks and browser verification; deployment guide with separate staging DB and credentials.
- Pull request and final report; actual Clover OAuth requires configured application credentials/redirect and a consenting merchant. Production release is separate.

## Progress
- Repository inspected and development branch created from main.
- Existing OAuth callback only displays text; global merchant credentials must be replaced.
- Existing sync has no audit records or pagination; can overwrite manual stock and restore archives.
- Existing backend tests present; Java 26, Node and Docker CLI available locally.
- Added optimistic versioning with a database migration and a database-backed stale-write regression test.
- Sync records stock deltas as Clover adjustments (not sales), preserves archives, and skips unknown/unsupported quantities without erasing stock.
- First checkpoint: 68 backend tests passed after concurrency/reconciliation changes.

## Next implementation steps
1. Replace global Clover token/merchant configuration with per-store connections. Implement OAuth initiation, hashed one-time state, code exchange and merchant verification; restrict setup to owner/admin.
2. Add pagination, background jobs, durable connection health and initial-sync onboarding. Tests must cover two merchants/stores and failed OAuth/replayed state.
3. Update product APIs, public site, signup/connect flow and responsive dashboard. Keep site in the existing app/repository; no Sites production publishing.
4. Implement sales event ingestion/insight foundation, docs, CI, end-to-end browser tests and staging instructions.

Development checkout: work/splynt under this chat. Logs are in its parent work directory. No Render settings or production resources have been modified.

## Store connection checkpoint
- Replaced the placeholder OAuth callback and global merchant tokens with per-store OAuth initiation, code exchange, merchant verification, encrypted credentials, and owner/admin checks.
- Authorization state is hashed, expires after 10 minutes, is browser-bound and consumed under a database lock before exchange. Concurrent replay and concurrent refresh tests added.
- Added paginated inventory fetching and failure-on-partial-import behavior. Sync is serialized per store and runs server-side on a schedule.
- Existing dashboard now supports connect/reconnect, manual sync, polling connection health, and recoverable callback messages. Full visual redesign remains outstanding.
- Verification: full backend suite 82 tests passing; frontend build and lint passing; PostgreSQL 17 migration/schema validation and 11 OAuth integration tests pass, including concurrent single-use state/refresh. Earlier PostgreSQL stale-write regression also passed.
- Added docs/CLOVER_SETUP.md with staging credentials, key handling, callback registration, upgrade behavior and limitations. Real merchant authorization remains unverified without configured credentials.
- Contact email requested asynchronously; no response yet. Public website/contact workflow remains to build.
- Temporary PostgreSQL container splynt-platform-test-db is for this task only; stopped after tests.

Next: polish public site/auth/dashboard, product editing/search/reorder settings, real browser tests with a local provider simulator, then actual sales-history ingestion and explainable insights. Expand security and sync acceptance coverage before release. No production deployment or main-branch writes.

## Public website and workspace checkpoint
- Added public marketing site at /#/ with product walkthrough, explicit sample inventory illustration, signup/login links, and honest upcoming-insights copy. Built in the existing React app, no separate hosting/deployment.
- Added shared visual styling/brand, responsive landing/auth/dashboard styles, product search/category/stock filtering and clearer sync/onboarding states.
- Signup now signs in after successful registration; if automatic sign-in fails, it safely returns to login instead of repeating registration.
- Guarded inventory requests against stale store-switch responses, including old sync callbacks.
- Added validated, durable contact inquiries with honeypot/basic rate limits; added private paginated operator inbox at /#/inquiries. It requires verified email ownership plus explicit environment allowlist, never just tenant ownership. See CONTACT_OPERATIONS.md.
- Verification: 89 backend tests pass, frontend build and lint pass. Visual/browser QA is NOT verified.
- Browser tool denied opening http://127.0.0.1:5173, explicitly stating user permission denied. Do not work around through other browsers, Playwright/CDP, screenshots or alternate URLs. Renewed permission was requested asynchronously and is still pending. Continue independent coding/tests.
- Public contact email question is also pending; contact requests currently persist to private inbox, not automatic email.
- Preview processes: backend exec session 90197 on 8087 (isolated in-memory H2), frontend session 84658 on 5173 with SPLYNT_API_PROXY=http://127.0.0.1:8087. Revalidate process handles before relying on them. An unrelated Docker service occupies 8080; do not touch it.

Next work: editable product settings/stock targets, visible inventory history, account/store settings, actual Clover sales-event ingestion and explainable velocity/time/season/location analysis. AI narrative/recommendation provider and truthful insufficient-data states still need implementation. Browser approval, actual Clover credentials/test merchant and staging configuration remain external verification needs; goal is far from complete.

## Product controls and sales insights checkpoint — 2026-10-07
- Added owner/admin product settings with optimistic version checks, reorder/target validation, and provider-owned identity protection. Product detail dialogs expose stock movement history, including archived products. Clover stock must be changed in Clover so a local edit cannot be silently overwritten by the next sync.
- Added V10 sales-event storage, store-specific paginated order ingestion with an overlapping modified-time cursor, atomic order replacement and refund removal. Sales events never mutate stock or infer sales from reconciliation adjustments. Inventory remains usable if sales permissions/import fail.
- Added sales summary, product velocity/stock cover and local-time weekday/hour charts. Estimates require 14 complete days and 30 included orders. The rolling window ends at the last successful sales import, so missing sync days are not misclassified as zero sales. Initial partial days and the current day are excluded; daylight saving uses local calendar days.
- Verification: full backend suite 106 tests passed; then the expanded Clover client suite passed (4 tests, adding cursor query encoding, 107 total cases in reports). Frontend production build and lint passed; git diff whitespace check passed. Tests cover stale edits, role/tenant boundaries for product settings, refund/idempotency/quantity semantics and timezone/window arithmetic. Actual Clover merchant and visual/browser verification are still outstanding.
- Limitations: partially refunded orders are conservatively excluded; timing is order creation, not payment completion. Seasonal comparisons, location-based recommendations and AI recommendations are NOT implemented. Location is displayed as context only. Current store regional defaults still need a settings workflow. Full imported product metadata and fractional inventory support remain incomplete.
- Next: account/store settings, sales ingestion failure/tenant-isolation acceptance coverage, richer inventory metadata, evidence-grounded recommendations, deployment documentation/CI and PR. Continue respecting the pending browser permission request; no production deployment or main-branch changes.
