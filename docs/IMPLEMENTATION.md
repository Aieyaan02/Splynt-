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

## Store setup checkpoint — 2026-10-07
- Added owner/admin store settings and new-store creation within an authorized organization. Store name, city, state/region, ISO country, currency and IANA timezone are validated. A new store can be selected and connected to its own Clover merchant.
- Added V11 optimistic store versions and stale-edit rejection. The dashboard refreshes account/store context and remounts insights after regional changes. A successful save followed by a failed workspace refresh retries only the refresh, avoiding duplicate store creation.
- Currency can be configured before products or a Clover connection exist. Existing inventory costs are not silently relabeled or converted. Provider currency reconciliation remains to implement alongside richer imported metadata.
- Store authorization now requires an enabled user and active organization, closing access through previously issued tokens after account/organization deactivation.
- Verification: 117 backend tests passed; frontend build and lint passed. PostgreSQL 17.11 successfully applied migrations V9–V11, validated all 11 migrations and Hibernate schema, and passed all 10 store-settings HTTP tests. Isolated test database container stopped afterward. No production changes.
- Visual/browser QA is still pending the earlier denied permission. AI/seasonal/location recommendations, richer inventory metadata, broader end-to-end acceptance, CI/deployment documentation and PR remain incomplete. Store setup is now implemented rather than a remaining feature.

## AI report checkpoint — 2026-10-07
- Implemented optional OpenAI Responses integration with strict structured output, bounded response validation and exact evidence-ID checks. Owners/admins can request actions and small product experiments; the UI labels ideas as hypotheses and shows the original evidence snapshot in readable fields.
- Added V12 persisted store-scoped reports, read-only retrieval for store members, one-attempt-per-hour cooldown (including provider failures), timestamps and sanitized error handling. Reads/polling never trigger provider generation. Previous reports remain available after a failed refresh.
- Added explicit data-transfer description before generation. Only aggregate store evidence, leading products and coarse location/timezone are sent; no customer/payment records or credentials. API request uses store=false. Server key and compatible model are required; disabled/insufficient/stale-data states are implemented. Actual paid/live calls have not been made.
- Verification: 129 backend tests pass; frontend build and lint pass. PostgreSQL 17.11 applied V12, validated 12 migrations and Hibernate schema, and passed the six AI HTTP tests. Simulator tests cover request schema, refusal, incomplete/malformed output and fabricated evidence references. The test database container was stopped.
- Added docs/AI_RECOMMENDATIONS.md with configuration, data sharing, limitations and release checks. Current generation holds a store-row lock during the bounded provider call; a job queue/global spend/concurrency controls remain recommended before scaling. Tests do not prove model quality or concurrent cooldown behavior yet.
- Still incomplete: comparative seasonal analytics (currently honest insufficient-data explanation), external local-market evidence, full inventory metadata/quantity support, live merchant/provider verification, visual QA, CI/deployment handoff and PR. Goal remains active; no main or production writes.

## Historical and seasonal comparison checkpoint — 2026-10-07
- Added a 24-month completed-calendar-month view, daily-rate comparisons against the same month last year, and product comparisons for the latest completed month. Store-local boundaries, month length/leap years, partial coverage and zero-sales months are explicit.
- Added exploratory recurring-month signals only when two full cycles have at least 30 included orders in every month. A signal requires both monthly rates to be at least 20% above/below their respective annual daily averages. Product percentage comparisons require 10 included orders per product per period. The UI describes these as heuristics and names uncontrolled effects; no claims of causal seasonality or guaranteed forecasts.
- Dashboard tables and saved AI evidence now show those comparisons. The AI receives historical evidence rather than a permanent insufficient-data string. New connections still start with the actual 90-day import; older history is not fabricated and will accumulate while connected.
- Added a bounded, store-scoped sales query with an exclusive upper date. Verification: full backend suite 136 tests passed, followed by two new database-backed insights HTTP tests (138 total report cases), frontend build/lint passed, whitespace check passed. Tests cover leap-year normalization, annual growth, one-off spikes, local month boundaries, unknown coverage, sparse product samples, cross-organization denial and exclusion of other-store/future events.
- No new schema migration or production writes in this checkpoint. Visual/browser QA is still pending the earlier denied permission. Remaining major work: fuller Clover inventory metadata/quantity handling, live merchant/provider verification, concurrency/failure acceptance checks, CI/deployment handoff and PR. External local-market data is not integrated; AI location context must not be described as measured local demand.

## Clover catalog and identity checkpoint — 2026-10-07
- Added V13 provider catalog metadata (SKU, alternate name, unit, pricing type, availability, hidden flag and returned categories). Details/search/category filtering now expose those fields. Provider visibility does not alter local archives; local reorder/cost settings remain independent.
- Fixed barcode-based identity takeover: imports skip a barcode held by a manual product or another Clover item instead of converting/relinking it. A barcode change conflicting with another product also leaves both products unchanged. Deleted timestamps and overlong required fields are respected.
- Verification: final full backend suite 144 tests passed; frontend build/lint passed. PostgreSQL 17.11 applied V13, validated all 13 migrations/schema and passed eight product-settings HTTP tests, including metadata persistence/reload/API serialization. Import tests cover metadata preservation and all three barcode-conflict cases. Test DB stopped; branch remains isolated from production.
- Remaining inventory gaps: missing/negative/fractional stock states and decimal inventory support, monetary field/currency reconciliation, complete category-association pagination and actionable per-item import issues. The current category metadata is the returned provider expansion, not a verified complete association export. Live integrations and browser QA remain unverified; broader goal remains active.

## Decimal inventory and explicit unknown stock — 2026-10-07
- Upgraded quantities, reorder/target levels and audit balances to exact decimal values (six fractional places), with V14 preserving existing integer data. Manual workflows accept decimals and reject over-precision/overflow; Clover negative balances are preserved and treated as low stock. Audit adjustments support transitions through negative provider balances.
- Missing/unsupported Clover stock now imports catalog metadata with an explicit unknown state rather than omitting the item or displaying an old balance as current. API quantity is null for unknown stock; low-stock queries and stock-cover calculations exclude it. Returning balances reconcile against the stored last-known balance with an explanatory audit note.
- Updated forms, filters, unknown-state messaging and partial inventory valuation. The overview counts in-stock products instead of summing unlike quantity units. Preserved barcode identity conflict protection and local targets/costs.
- Verification: clean rebuild and all 151 backend tests passed; frontend build/lint passed. PostgreSQL 17.11 applied V14, validated all 14 migrations/schema and passed 13 product/decimal HTTP tests plus the Clover importer unit suite after the final review-count adjustment. The new tests verify exact fractional sales/reorder results, null unknown-stock API values, negative balances, oversized/over-precision rejection and recovery. Existing whole-number tests now assert exact integer conversion of decimal results.
- Broader goal remains active. Monetary field/currency import, complete category associations, detailed import issue handling, live Clover/AI verification, concurrency/failure acceptance checks and deployment/CI/PR handoff still require work. Browser QA remains pending the earlier permission denial. Production is unchanged.

## Actionable import review checkpoint — 2026-10-07
- Added persisted item-level import review with Clover item/name/barcode, reason and next step for identity/barcode problems and unknown stock. The UI displays details from the last completed import, capped at 100 entries with the full issue count. Intentional archives/deletions no longer generate actionable warnings.
- Successful imports replace the snapshot and clear resolved issues. Failed refreshes retain it. A timestamp guard prevents an older completed result from overwriting a newer status snapshot; connection status returns only explicitly selected public fields, never credentials.
- Verification: clean build and 158 backend tests passed; frontend build/lint passed. PostgreSQL 17.11 applied V15, validated all 15 migrations/schema and passed five import-review HTTP tests. Additional importer tests verify bounded issue output and exclusion of intentional archives. Test DB stopped after verification.
- Remaining major work: monetary/currency import and category association completeness, live merchant/provider acceptance and output quality, broader concurrency/failed-import acceptance, CI/deployment documentation and PR. Browser verification remains pending the earlier permission denial. Production/main remain unchanged; goal is active.

## Missing provider catalog checkpoint — 2026-10-07
- Fixed stale known balances for active Clover products absent or explicitly deleted in a completed catalog import. These now become unknown with a persisted actionable review entry; no invented zero balance, sale, or automatic archival. A returning product resumes reconciliation with its retained identity/history/settings.
- Absence detection runs only after both paginated collections finish successfully and remains scoped to the current store and Clover source. Manual inventory and local archives are preserved.
- Verification: clean backend suite passed (162 tests, zero failures/errors), frontend build/lint passed, whitespace check passed. Tests cover disappearance/reappearance, explicit deletion, fetch failure and manual inventory isolation. No schema change. Browser/live Clover verification remains outstanding.
- Goal remains active. Currency/monetary fields, full category associations, remaining acceptance/concurrency checks, live integrations, visual QA and deployment/CI/PR handoff are still incomplete. No production/main changes.

## CI and staging handoff checkpoint — 2026-10-07
- Added a root README with local startup, verification commands and explicit current integration/release limits. Added a Render staging guide with branch separation, independent database/credentials, environment mapping, acceptance checks and migration-aware recovery.
- Added a read-only GitHub Actions workflow for development pushes, pull requests and manual runs. Jobs verify Java/frontend, run 34 database-backed HTTP tests with PostgreSQL 17 and Flyway/schema validation, then build the Render Dockerfile. No deployment/publishing credentials or actions. Action references are pinned to SHAs verified from upstream major-version refs.
- Local verification: workflow YAML parses; the exact selected PostgreSQL test group passed all 34 tests against PostgreSQL 17.11 and validated 15 migrations. Isolated DB stopped afterward. Hosted workflow and container build results must be checked separately; adding the workflow is not proof of a green hosted run.
- No Render resources/settings changed. Remaining implementation/live acceptance and visual QA requirements are unchanged; goal remains active.

## Complete category membership traversal — 2026-10-07
- Replaced nested category expansion with explicit pagination of the category list and each category's items. Memberships are assembled by provider ID before the catalog is returned. Empty memberships remove stale categories; failed pages never publish partial category data. Traversal is by category rather than one request per inventory item.
- Verification: clean full suite passed 165 tests, then the additional category-list pagination test passed in the eight-case client suite (166 total cases in reports). Coverage includes membership pagination, >100 categories, empty memberships and failed later pages. No schema/frontend change. Live provider/load testing is still outstanding; independent API collections are not an atomic Clover snapshot.
- Hosted CI for 56d7a04 passed all three jobs, including PostgreSQL and the Docker runtime build: https://github.com/Aieyaan02/Splynt-/actions/runs/37583149862 . Local redundant Docker build remained stalled at registry metadata and was explicitly terminated (session 69797 exit 130) after hosted build success, not restarted. The new commit still requires its own CI result.
- Monetary/currency import, live integrations and AI quality, visual acceptance, remaining workflow/concurrency coverage and review handoff remain incomplete. Goal stays active; production/main unchanged.

## Public product explanation checkpoint — 2026-10-07
- Replaced stale future-only sales/AI copy with the implemented conditional capabilities: timing/product insights, history-dependent comparisons and optional evidence-backed AI reports. Added native keyboard-accessible FAQ disclosures explaining setup, read-only Clover integration, sync freshness/unknown stock, history requirements and AI data sharing.
- Verification: frontend production build and lint passed; whitespace check passed. Visual/browser acceptance remains unverified under the pending permission restriction. Hosted CI for category commit 70ca2c8 was still in progress when checked (run 37583468142); the preceding CI commit is fully green.
- This is a public-site explanation update, not proof of live provider readiness. Monetary import, live acceptance, remaining workflow checks, browser QA and final review are still outstanding. Goal active, production/main unchanged.

## Clover monetary metadata checkpoint — 2026-10-07
- Added merchant-currency verification and exact Clover selling-price/item-cost metadata in existing catalog JSON. Only supported two-decimal ISO currencies and bounded nonnegative integer cents are rendered. Fixed/per-unit prices are distinguished from variable prices; unsupported/unknown values remain unavailable.
- Product details display provider money with the explicit currency, and flag mismatch with the Splynt store currency. Local unit costs/valuation are independent; no silent conversion or replacement. Merchant-properties failure does not block stock reconciliation; raw merchant properties are never persisted.
- Verification: clean full backend suite passed 170 cases, then the final importer/client/product HTTP suites passed with an added merchant-properties test (171 total report cases). Frontend build/lint and whitespace checks passed. Persistence/API tests cover monetary JSON round trips; importer tests cover exact cents, mismatched currency, optional-property failure, invalid currency/amounts and variable pricing. No schema migration. Real merchant verification remains outstanding.
- Hosted CI for website commit 062024c passed; category commit 70ca2c8 was superseded/cancelled by that run. This monetary commit still requires hosted verification.
- Remaining work includes full signup-to-import acceptance, concurrency/failure checks, live merchant and AI quality verification, visual QA, final review/PR handoff and an explicit completion audit. Email recovery/verification remain absent. Goal remains active; no production/main changes.

## Connected onboarding acceptance and reconciliation fix — 2026-10-07
- Added an acceptance test using real HTTP controllers, JWT issuance/validation, application services and database transactions, with only Clover simulated by a local HTTP server. Covers signup/login, initial empty workspace, browser-bound OAuth initiation/callback, catalog/stock/currency import, low-stock results, subsequent stock changes, repeat-sync history, unauthenticated denial and cross-account denial.
- The new journey exposed a real defect: later stock changes collided with the unique external-event reference because reconciliation used the Clover product ID as an event ID. Snapshot adjustments now have no external event reference; their product relation preserves identity. Existing audit entries are retained. Unchanged balances still create no movement, and stock differences are adjustments rather than sales.
- Verification: clean full backend suite passed all 172 tests. The final acceptance test also passed on PostgreSQL 17.11 with all 15 migrations/schema validated. Isolated test DB stopped afterward. Added this acceptance case to the PostgreSQL CI job. Hosted CI for prior monetary commit 3ac88b8 passed; new commit requires its own run.
- This proves the simulated API journey, not actual Clover consent/browser operation or live merchant data. Live integrations, AI quality, visual QA, remaining concurrency/failure checks and review handoff remain outstanding. Goal stays active; production/main untouched.

## AI concurrency verification checkpoint — 2026-10-07
- Added separate-thread, separately committed transaction tests for simultaneous first-report generation. A controlled provider latch verifies the second request waits; both success and provider failure produce exactly one provider call and retain the hourly cooldown. Added failed-refresh coverage preserving the prior successful report, evidence and generation timestamp.
- Verification: both concurrency cases passed on H2 and PostgreSQL 17.11. Final PostgreSQL run passed all nine advice HTTP/concurrency cases with Flyway/schema validation. Added concurrency coverage to the PostgreSQL CI job; test DB stopped afterward. No application behavior/schema/frontend change was needed. Hosted CI for onboarding fix 0e79fc4 passed.
- Live model quality/account availability and browser interaction remain unverified. This closes the previously missing concurrent cooldown check, not overall production readiness. Goal remains active; production/main unchanged.

## Failed sync preservation and CI portability — 2026-10-07
- Extended the connected onboarding acceptance test with provider stock failure and recovery. It verifies the prior completed balance/timestamp survive, failure status is visible, recovery clears the warning, and no failed/unchanged refresh creates an audit movement.
- Sync jobs now expose one actionable sanitized 503 message for inventory failures instead of arbitrary exception text. Added an HTTP regression using an internal diagnostic exception to prove it is not returned to clients.
- Verification: both affected suites passed on H2 and PostgreSQL 17.11 (seven cases); isolated database stopped. Final clean full backend run passed 176 tests with zero failures/errors.
- Hosted CI c71c5fd exposed a test-only nanosecond-versus-microsecond timestamp assertion failure on Linux, absent on the local clock. Inspected both job logs; only the two new concurrency timestamp assertions failed. Allowed at most one microsecond of database rounding, preserving checks for one provider call, serialization and persistent cooldown. The next hosted run must confirm this repair.
- No schema/frontend changes. Live provider/model acceptance, browser QA and remaining review still prevent goal completion. Production/main unchanged.

## Current-window mixed-unit correction — 2026-10-07
- Changed current-window timing charts and AI timing evidence to distinct included orders. Product leaders now rank by the number of orders containing each product (stable ID tiebreak), with per-product unit labels and retained product-specific quantities/velocity. Removed the summed-units headline/window AI input so weights and counts are not added into one headline metric.
- Kept legacy hourlyUnits/weekdayUnits/units fields in the API; current UI/AI timing uses new hourlyOrders/weekdayOrders fields. Existing saved report renderers still understand old evidence keys. AI instructions explicitly forbid comparing unlike physical units.
- Verification: clean backend run passed 176 cases; added mixed-unit/deduplication/ranking regression passed in targeted insights tests (177 report cases). Frontend build/lint and whitespace checks passed. Prior hosted CI 6caadab passed, confirming the timestamp assertion repair.
- Follow-up required: historical month aggregates still sum quantities and need correction before release. Live provider/AI and browser acceptance, review handoff remain outstanding. No production/main changes; goal active.

## Historical order-rate correction — 2026-10-07
- Store-wide monthly totals, year-over-year rates and recurring calendar-month patterns now use distinct included orders per day. Product comparisons retain their quantities/unit labels and rank by orders containing each product. Multiple lines and unlike physical quantities cannot inflate the store-wide order count.
- New history payloads identify metric ORDERS and expose ordersPerDay/dailyOrdersChangePercent. Saved older AI snapshots remain readable with an explicit legacy summed-unit warning; their evidence is not rewritten. Product unit labels currently reflect catalog metadata, so changes to a product's measurement basis over time still require care when interpreting product-specific history.
- Verification: clean backend suite passed 177 cases; final historical/HTTP tests passed with the new mixed-quantity and repeated-order regression (178 report cases). Existing leap-year, annual-growth, sparse-data and single-year-spike checks now exercise order rates. Frontend build/lint and whitespace checks passed. Hosted CI for f72f92d passed.
- Live merchant/model quality, browser verification, remaining review and release acceptance are still outstanding. Goal active; production/main unchanged.

## Draft review preparation — 2026-10-07
- Reviewed branch scope against the original user requirements and prepared docs/REVIEW_HANDOFF.md with a reusable draft PR description, evidence matrix and explicit remaining acceptance work.
- No existing open PR from dev/splynt-platform was found. Connected GitHub create_pull_request returned HTTP 403 Resource not accessible by integration; no PR exists from this attempt and nothing was merged. PR publishing requires the integration's write permission. Continue independent implementation/review; this alone is not a global blocker.
- Current head's hosted CI was in progress when inspected; no new test run is needed for this documentation-only checkpoint. Goal remains active; production/main unchanged.

## Incomplete sales-window recovery — 2026-10-07
- Added V16 persisted sales retry cursor. Unresolved paid order/line imports retain the affected window for re-reading; a subsequent clean incremental window cannot silently bypass older omissions. A clean re-read clears the retry cursor and warning. Existing connections get one re-read from stored coverage to recover records older versions may have skipped.
- Separated intentional exclusions (unpaid/refunded/test/deleted/out-of-coverage/fee lines) from unsupported or unmatched paid records. Intentional exclusions no longer block AI reports; actual incomplete imports still do. No sales or stock is fabricated.
- Verification: clean backend suite passed 180 tests. PostgreSQL 17.11 applied V16, validated 16 migrations/schema and passed the six sales-sync cases plus connected onboarding acceptance. Added sales-sync tests to PostgreSQL CI. Test DB stopped afterward. Frontend unchanged.
- Permanently unsupported paid records cause repeated window reads and need operational attention; large histories require rate-limit/load acceptance. Live Clover/AI, product measurement history, visual QA and final review remain outstanding. Draft PR publishing remains unavailable via connected GitHub permissions. Goal active; production/main unchanged.

## Historical measurement preservation — 2026-10-07
- V17 stores the measurement unit supplied by each sales line. Existing rows remain unknown and connected stores re-read covered history; no old weighted unit is inferred from current catalog settings. Ordinary item snapshots are explicitly identified.
- Current and annual product quantity calculations now require consistent known historical units. Mixed/unknown totals and rates are withheld, and stock cover additionally requires a match with the current catalog measurement. Order counts/rankings and store-wide seasonal patterns remain available. UI and AI evidence preserve the distinction; no guessed physical-unit conversion.
- Verification: clean backend suite passed 184 tests; frontend build/lint passed. PostgreSQL 17.11 applied V17, validated 17 migrations/schema and passed 11 affected sales/insights/onboarding tests. Regression cases cover historical ounces versus current pounds, mixed units in one window and units changing between years. Test database stopped afterward.
- Live Clover metadata/quality and AI output acceptance, browser QA, account recovery/verification and final review remain incomplete. PR publication still lacks integration permission. Goal active; production/main unchanged.

## Inventory dialog recovery and source accessibility review — 2026-10-07
- Dashboard refresh errors previously stayed at the parent and closed the inventory dialog over stale data. The manual movement flow now propagates refresh failure to the dialog after a confirmed save, disables further edits and offers a refresh-only retry. A small confirmed-mutation helper coalesces concurrent submissions and never repeats a confirmed write during refresh recovery. Ambiguous network failures before confirmation still require server-side idempotency work; this is not an exactly-once guarantee across lost responses.
- Added a labelled inventory dialog and alert semantics. Product-detail view selectors now use standard pressed buttons instead of incomplete tab roles lacking tab-panel/arrow-key behavior.
- Verification: three browser-independent Node regression tests cover failed refresh, rejected write and concurrent calls; frontend build/lint and whitespace checks passed. Added frontend tests to CI and README. No browser rendering or keyboard acceptance was performed under the pending permission restriction. Hosted CI for 30b8680 passed.
- Goal active; live integrations/model quality, visual/accessibility acceptance, account workflows and final review remain incomplete. Production/main unchanged.

### Inventory request replay protection

Manual sale/restock requests accept an optional UUID requestId, scoped to store and product. The inventory dialog retains that ID across retries while open. The server locks the product, records the ID with the movement, and returns the original movement on replay without changing stock again. Reusing the ID with a different quantity, note, or operation is rejected. Responses contain the current product balance and original movement. Legacy requests without an ID retain their previous behavior; reopening the dialog creates a new ID, so this is not a guarantee across reloads. Clover-owned stock remains read-only.

Validation: existing backend suite, three new database replay/concurrency tests, frontend retry tests, lint and production build. PostgreSQL replay tests are included in hosted CI. Live browser and provider acceptance remain outstanding.

### Session recovery and expiration

Authenticated 401 responses now notify the application, remove the expired token and return to sign-in with an explanation. A late response from an old token cannot clear a newer login. Startup network/service failures retain the token and show retry or account-switch actions, instead of silently signing out. Malformed error JSON preserves the HTTP status. Public login failures do not invalidate an existing session. Node tests cover expiration, token races, transient failures, public-login failure and malformed responses; UI browser acceptance remains pending.

### Dashboard refresh after provider sync

The Clover connection poll now waits for its prior check and dashboard refresh to settle before starting another check. A completed provider timestamp is acknowledged only after the dashboard read succeeds; failed reads retry at the next poll even when Clover has not produced a newer import. The dashboard callback propagates failures to this tracker. Node tests cover failed-read retry, overlapping refresh calls, newer snapshots and missing initial imports. Frontend tests (11), lint and build pass; browser acceptance remains outstanding.

### Consistent low-stock dashboard snapshot

The low-stock summary and list now derive from the active product response's server-calculated lowStock flags. Previously, separate inventory and low-stock requests could observe different committed imports and disagree. This removes a redundant request on each refresh while retaining server rules for unknown quantities and reorder thresholds. Frontend tests and build passed; lint is clean. Browser acceptance remains pending.

### Password encoding validation

Signup and login now validate the password encoder's 72-byte UTF-8 limit before calling it. The previous 72-character limit accepted some multibyte passwords that the encoder could reject. Matching frontend validation provides actionable feedback without truncating or normalizing passwords. Backend validation/auth tests and 12 frontend tests pass, as do lint/build. Account recovery/email verification are still incomplete; an email-service preference has been requested without asking for secrets.

### Authenticated password changes and session invalidation

Added a current-password-protected account endpoint and workspace dialog. V18 adds credential and optimistic account versions. JWT checks reject older credentials after a change and disabled/deleted accounts; legacy signed tokens map to version zero until a change. The transaction locks/rechecks the account before changing it. HTTP tests exercise real signed-token rejection, new/old login behavior, validation, anonymous denial, legacy compatibility and other-user isolation. See ACCOUNT_SECURITY.md for deployment and in-flight-request limitations. Forgotten-password recovery/email verification remain outstanding.

### Password-change concurrency acceptance

PostgreSQL HTTP tests now start two real signed-token password-change requests together. Exactly one succeeds; the other is denied, and the credential version increments once. A separate stale-entity test proves a delayed login/account update cannot write the old password back after a successful change. All six password-change HTTP cases pass on PostgreSQL with Flyway and schema validation. These checks verify concurrency behavior, not browser UX or forgotten-password delivery.
