# Store intelligence — separate feature branch

Branch: `feature/store-intelligence`. Not merged into `pilot` or `main`; no deployment.

## Implemented in this iteration

- Downloadable reorder CSV from the currently loaded store snapshot. Includes product, barcode, unit, quantity, minimum, target, suggested quantity and stock timestamp. Excludes other stores, archived items and unknown/non-actionable stock. Text cells neutralize spreadsheet formulas. It does not place orders or update stock. Spreadsheet applications may still reformat numeric-looking barcodes; import that column as text.
- Optional sales and AI panels behind `VITE_STORE_INTELLIGENCE_ENABLED=true` (off by default). The backend also requires `SPLYNT_SALES_ENABLED=true`; leave both disabled for the pilot.
- Readiness checks explain recent imports, 14 complete days, 30 included orders and matched product sales. These are explanatory UI checks; server authorization and eligibility checks remain authoritative.
- AI generation requires an explicit session consent checkbox and an owner/admin action. Store-keyed components reset consent and data when switching stores. Provider credentials/configuration remain server-side. No generation occurs on polling or page load.
- Historical copy no longer implies that missing years of sales are automatically available. Imports must come from actual authorized provider records; synthetic test fixtures are not business evidence.

## Validation

- 19 frontend tests pass, including CSV store filtering/formula handling and readiness boundaries; lint/build pass.
- Production frontend build tested with feature panels both disabled and enabled.
- Backend unchanged from the pilot audit: 227 tests passed with simulated providers/local H2. No live AI request, merchant import or PostgreSQL migration was performed locally.
- Visual review remains pending in the owner's browser; saved browser permissions prevented automated review.

## Saved reorder work now implemented

Store-scoped persistent drafts with editable pack quantities, supplier/contact fields, immutable ordered status and retry/concurrency protection are implemented. See REORDER_DRAFTS.md for limits and verification. Supplier fields are per draft; reusable defaults remain future work.

## What to build next, in order

1. Review the CSV with the owner and add supplier, pack-size and lead-time fields only when their ordering process is known. Keep calculations explainable and user editable.
2. Review the saved draft workflow with the owner; next add reusable supplier and per-product pack-size defaults. Receiving and partial deliveries need a separate design.
3. Test actual authorized sales import and historical coverage after approval, with clear missing-period indicators and quantity-unit checks.
4. Validate advice against controlled fixtures and actual store evidence. Preserve evidence links, cooldowns, strict output validation and uncertainty labels.
5. Before any live AI use, agree on a provider and spending policy. The current free-only constraint means no paid API calls, keys or automatic provider enablement.
6. Later: supplier workflow, multiple-store onboarding, monitoring/backups and paid always-on hosting only with explicit budget approval.

## Release criteria

Green GitHub application/PostgreSQL/container checks, mobile/keyboard review, store isolation verification, no secrets in Git, real stock reconciliation, owner approval of any AI provider costs/data transfer, and a reviewed PR into pilot. Main remains the deployed-demo branch unless the owner deliberately changes the release setup.
