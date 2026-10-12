# Inventory pilot checkpoint — 2026-10-12

Work is local on dev/splynt-platform. Main and the deployed sample service have not been changed. This is not a live Clover connection or an approved application.

## Verified

- Existing inventory and store authorization implementation reused, not rebuilt.
- Default backend configuration disables sales import/backfill jobs and removes sales/history/AI controllers. Deferred service code remains for later.
- Pilot dashboard omits sales-history and AI panels; public website no longer advertises those as current features.
- Sync status now includes the date and explains server availability rather than promising uninterrupted background sync.
- Simulated merchant acceptance verifies signup/login, browser-bound OAuth, inventory import, low-stock/reorder quantities, access denial for a different business, recovery from provider failure, and zero order requests in inventory-only mode.
- Full backend suite: 222 tests, zero failures/errors. Run with Mockito loaded as a JVM agent because automatic self-attachment failed under the current runtime.
- Frontend: 15 tests passed, lint passed, production build passed. Git whitespace validation passed.

## Not verified / external dependencies

- PostgreSQL migration/schema acceptance: Docker daemon unavailable, and Docker application could not be found. The current successful backend suite used its test database configuration, not an independent PostgreSQL run.
- Actual browser visual/accessibility review: not performed; earlier browser permission denial has not been explicitly resolved.
- Hosting migration/deployment: not performed. Candidate free platforms require account selection, capacity checks and live setup.
- Real Clover authorization and data accuracy: requires correct production developer/app setup and owner merchant authorization.
- Legal/support identity and policy decisions: require owner input; submission draft deliberately does not invent them.
- No PR or Clover submission created. Earlier GitHub PR creation lacked permission. Current changes have not been pushed.
- Earlier uncommitted historical backfill work remains preserved and disabled for the pilot. Its tests pass in this suite; PostgreSQL verification remains outstanding.

## Handoff

Read CLOVER_PILOT_SUBMISSION.md for the app description, permissions justification, reviewer walkthrough and owner checklist. Read INVENTORY_PILOT_HOSTING.md for deployment settings. Historical/AI implementation is outside the pilot scope.
