# Development review handoff

Status: draft, not approved for production. Branch: `dev/splynt-platform`; proposed base: `main`.

The connected GitHub integration rejected PR creation with HTTP 403 (`Resource not accessible by integration`) on 2026-10-07. No PR was created. Use the description below when PR write permission is available; first check for an existing PR from this branch to avoid duplicates.

## Proposed PR

Title: **Build store-connected inventory, sales insights and public Splynt website**

Splynt needs a complete store onboarding and inventory workflow. This branch adds store-specific Clover connections, reconciled inventory and low-stock visibility, sales analysis, optional AI reports, and a public discovery/contact website.

Implemented:

- Authenticated password changes with credential-version session invalidation; signup workspace flow, store settings and role/tenant controls; store-bound Clover OAuth with encrypted credentials, token refresh and scheduled imports.
- Exact decimal stock, explicit unknown balances, actionable import issues, paginated category memberships and currency-qualified provider prices/costs. Local targets/costs remain independent.
- Stock reconciliation history and concurrent-edit protection. Repeated adjustments no longer collide on reused provider product IDs.
- Paid-order ingestion, product velocity/cover, distinct-order timing and monthly/year-over-year patterns. Product-specific quantities retain their own units.
- Optional evidence-backed AI reports, persisted snapshots, owner/admin generation and a tested hourly concurrency limit.
- Public marketing/contact pages, private operator inquiry inbox, redesigned workspace/dialogs, CI and setup/staging documentation.

Validation includes backend tests, frontend build/lint, PostgreSQL migrations/schema, and a simulated-provider signup/login/OAuth/import/low-stock/failure-recovery journey. Concurrent AI success/failure requests and report retention are covered. Inspect CI at the current PR head; earlier green runs do not prove later changes.

Migrations V7–V21 are included. Imported currency support is limited to two-decimal currencies. Location is context, not measured local demand. New connections start with 90 days of sales; partially refunded orders are conservatively excluded. Contact submissions do not send email. AI generation holds a store lock during its bounded provider request.

Do not merge or deploy production until the outstanding acceptance items below are resolved.

## Requirement evidence and remaining acceptance

| User requirement | Current evidence | Still needed |
| --- | --- | --- |
| Work separately from the deployed version | Development branch and push history; CI has no deploy steps | Inspect actual Render production branch/settings; create and verify isolated staging before any release |
| Create an account and connect own store | `StoreOnboardingAcceptanceTest` exercises signup, real JWT login, browser-bound OAuth callback and tenant denial with real services/database | Actual Clover sandbox browser consent, redirect configuration, scopes and reconnect/expiry acceptance |
| Pull stock and show low inventory | Decimal, unknown-stock, barcode identity, pagination, import issue, product HTTP and connected low-stock tests | Representative live catalogs, large-import/rate-limit behavior, and read-only stock parity with Clover |
| Read stock flow and best sellers | Paid-order ingestion, adjustments separated from sales, order-ranked products and stock-cover calculations | Verify retry-window recovery on live catalogs and verify historical unit snapshots and missing-unit behavior on real orders; live order/refund parity |
| Day/time/season/location and product ideas | Order-based timing, covered-month comparisons, optional AI with evidence snapshots and coarse location context | Actual model quality/adversarial-name evaluation, consented provider setup; location is not independent market evidence |
| Polished startup UI/UX | Implemented responsive landing/workspace/dialog styles; TypeScript build/lint pass | Permitted browser rendering, mobile/desktop, keyboard/focus, error/loading and accessibility checks. Earlier browser denial remains in force |
| Discovery website and contact | Landing, product explanation, FAQs, inquiry persistence and authorized inbox tests | Visual acceptance, designated verified operator, operational response workflow and desired public contact identity |
| Proper working software | Backend/DB/CI coverage, docs and simulated connected workflow | Account recovery/email verification, remaining correctness review, real integrations, staging/recovery checks and final requirement-by-requirement audit |

These are implementation and test evidence, not a claim of overall completion. Do not convert pending checks into passed checks because CI is green. No production release is authorized by this document.

## External inputs

- Renewed permission for browser-based QA after the earlier denied request.
- Clover sandbox application/test-merchant setup in the intended staging environment.
- Optional server-side OpenAI key/model configuration for real-output acceptance; do not paste credentials into chat or commit them.
- GitHub integration PR-write permission to publish the prepared draft.

## Review focus

Before expanding feature scope, inspect sales coverage after rejected records, measurement-unit changes in product history, snapshot consistency during large imports, and error handling outside the Clover sync boundary. Review migration compatibility and recovery before choosing a production promotion strategy. Use `RENDER_STAGING.md` for the concrete staging acceptance procedure.
