# Inventory pilot checkpoint — 2026-10-12

## Current scope

The `pilot` branch is the inventory-only release candidate. Main and the existing deployed demo remain unchanged. The owner has created a separate Neon Free project but has not deployed a new Render service. Deployment is paused pending Clover developer verification; production application approval and real merchant authorization may require additional steps.

## Completed and checked

- Store-scoped authorization, browser-bound OAuth, encrypted provider tokens and read-only inventory imports.
- Low-stock recommendations use configured minimum and target stock, not fabricated sales history or forecasts.
- Import tests cover missing/invalid/negative/fractional quantities, archived/deleted items, duplicate barcodes, failed sync preservation and recovery.
- Full local backend suite: 227 tests passed, zero failures/errors/skips. Mockito loaded as a startup agent. Tests use H2; this is not production PostgreSQL acceptance.
- Frontend: 15 tests passed, lint and production build passed. Updated source-map-js to the patched release; npm audit reports zero known vulnerabilities.
- Provider-neutral UI labels and explicit Syncing status; warmer surfaces, card depth, stock emphasis and first-use guidance retain the original green/cream design.
- Inquiry persistence and optional email queue implemented. Email delivery remains disabled at the owner's request; no provider key or live email test.
- Known-pattern credential scan found no matches in tracked working files and reachable local Git history. Local `.env` files are ignored. CI runs the same scan. This is a bounded check, not a guarantee that every possible private value has been detected.

## Required before real-store use

- Visual review in the owner's browser, including mobile and keyboard use. Browser automation remains unavailable under the saved permission state; no visual validation is claimed.
- PostgreSQL migration/workflow and application jobs passed on GitHub at `8df9eb1`. Its container job hit a Maven Central HTTP 502. Confirm the final dependency-update CI run, including the container build.
- Complete provider production setup/approval; authorize the real merchant.
- Configure isolated Free hosting/database secrets and verify startup, callback URL, TLS and database backup/export procedure.
- Compare actual product quantities, archived items and recommendation settings against the POS.
- Complete owner business/support/privacy details in the submission draft.

## Branch plan

Additional reorder-export and AI work begins on `feature/store-intelligence`, branched from this pilot checkpoint. Do not deploy or merge it until separately reviewed. Legacy sales/AI code already exists in pilot history but is disabled and absent from its dashboard; new feature changes stay on the feature branch.

AI provider use remains unconfigured. Automated local tests use simulated responses and must never be described as real store evidence. No paid AI requests are authorized by the free-only requirement.
