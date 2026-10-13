# Clover inventory pilot submission preparation

Updated audit and submission package (2026-10-13): see [readiness and blockers](clover-review/READINESS.md), [submission copy](clover-review/SUBMISSION_COPY.md), and [video script](clover-review/VIDEO_SCRIPT.md). These supersede older readiness assumptions below.

Status: draft for owner review, not submitted or approved. Scope: one real shop, inventory reads and threshold-based reorder planning. Existing sample deployment stays unchanged.

## Suggested app description

Splynt helps independent retailers review their Clover inventory in a private business workspace. Retailers can search products, identify low or unknown stock, and set reorder thresholds and target quantities. Suggested reorder quantities are calculated from those settings and the latest successfully imported stock. Splynt displays the last successful refresh and import issues. It does not place purchase orders, process payments, or change Clover inventory.

## Permission justification

Request only the current Clover permissions needed for these actual calls; verify the labels in the production Developer Dashboard:

- Merchant read: verify the merchant identity during authorization and read the merchant currency so product amounts are interpreted correctly.
- Inventory read: read products, item stock, categories, and category membership to display and filter inventory.
- No order, payment, customer, employee, or inventory-write permission is required by this pilot's provider calls. Token exchange/refresh is part of OAuth.

The application uses server-side token exchange, encrypted stored tokens, single-use browser-bound OAuth state, and store membership checks. These are implementation details to verify in acceptance, not a security certification.

## Reviewer walkthrough to record after deployment

1. Register a Splynt business and store, sign in, set the store currency to match Clover.
2. Choose Connect POS and authorize the intended test merchant through Clover.
3. Choose Sync now. Compare representative product names, quantities and categories against Clover.
4. Set a product threshold and target. With quantity 2, threshold 5, and target 20, verify a suggested reorder of 18.
5. Update a test merchant's stock in Clover and sync again. Confirm the new quantity and alert state.
6. Show last successful sync and an import failure/unknown-stock state using controlled test data.
7. Sign into a second independent business. Confirm it cannot access the first store's products, settings or connection.
8. Confirm the pilot has no sales-history or AI controls and does not request order data.

Do not record credentials or tokens. Use reviewer/test accounts and the merchant environment specified by Clover, never distribute the owner's login.

## Owner information still required

- Country confirmed: United States (Miami Beach). Production developer verification last reported pending; confirm current status and whether a production app exists.
- Hosting choice/account access; final HTTPS application URL and callback URL.
- Clover app ID/secret placed directly into hosting secrets, plus merchant authorization performed by the owner.
- Legal operator/business name, monitored support email, privacy-policy and terms URLs, and a decision on retention/deletion procedures. Do not invent these or submit placeholder policies.
- Any current Clover review checklist requirements visible in the developer dashboard, including screenshots/video and release-group eligibility.

The developer account/app approval process is controlled by Clover. A working sandbox simulator is not evidence of production approval or live merchant connectivity.

References to recheck at submission:
- https://docs.clover.com/dev/docs/from-sandbox-to-app-launch-checklist
- https://docs.clover.com/dev/docs/basic-app-configuration
- https://docs.clover.com/dev/docs/oauth-flows-in-clover
