# Saved reorder drafts

Available only on `feature/store-intelligence`; not merged into the inventory pilot or deployed.

## Workflow

1. Choose **Create from low stock** in the selected store. Up to 200 active, known-stock items with positive reorder suggestions are copied into an editable draft.
2. Enter one supplier name and contact/reference per draft. Remove items intended for another supplier.
3. Set the number of packs and stock units per pack for each line. The total is packs × units per pack; no inferred case sizes or automatic stock adjustments.
4. Save. The server stores the draft, supplier details, quantities and a product-name/barcode/unit snapshot in PostgreSQL. The panel shows the latest 20 drafts.
5. Order outside Splynt. Choose **Mark ordered**, then explicitly confirm. The saved draft becomes immutable and records the timestamp. This does not email a supplier, place an external order or receive stock.

Supplier details and pack sizes currently belong to each saved draft. A reusable supplier directory, product-to-supplier defaults, older-draft pagination, receiving workflow and partial deliveries remain future work.

## Permissions and safeguards

- Store members may read drafts; only owners/admins may create, edit or mark them ordered.
- Every product and draft lookup is scoped to the authorized store, including owners with access to several stores.
- Saves use a version check and row lock; stale edits are rejected. Repeated create requests use a stable UUID and repeated ordered confirmations are idempotent.
- Quantities use decimal arithmetic on the server. Fractional pack counts, zero/negative counts, unsupported precision, duplicate products and empty/oversized drafts are rejected.
- Active product identity is copied from the server rather than trusted from submitted names.
- No provider clients or inventory writes are called. Saved suggestions are snapshots; the user must review current stock before placing an order.
- Migration V24 adds only the reorder table/index. No changes to existing inventory tables or external hosting secrets.

## Verification

HTTP/database integration tests cover persistence, retry safety, stale updates, ordered immutability, unchanged stock, cross-store IDs/products, unauthorized users, employee permissions and invalid quantities. CI also runs these tests against PostgreSQL 17. Frontend lint, tests and production build are required. Manual browser review remains pending under the existing browser permission restriction.
