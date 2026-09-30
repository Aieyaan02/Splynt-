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
