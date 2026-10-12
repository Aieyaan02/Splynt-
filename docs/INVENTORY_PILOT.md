# Single-store inventory pilot

Agreed scope, 2026-10-12: securely connect the owner's one Clover location, isolate its inventory from other businesses, display stock and threshold-based reorder suggestions. Defer sales history, AI, seasonal analysis, and other expansion features.

## Acceptance criteria

- Sign in to Splynt using a Splynt account; link Clover separately through merchant authorization. Never collect the merchant's Clover password.
- Every inventory, connection, and settings request checks active organization/store membership on the server. A different business must not read or change these resources even if it knows their IDs.
- Store-bound OAuth uses expiring, single-use browser-bound state, verified merchant identity, encrypted server-side credentials, and owner/admin authorization.
- Pilot sync reads catalog and stock only. Sales/history jobs and endpoints are disabled by default through splynt.sales.enabled=false; deferred panels are omitted from the pilot frontend.
- Request only the provider permissions required for merchant identity and inventory reads. No Clover stock writes or automatic purchasing.
- Show current known quantity, low-stock threshold, target stock, suggested reorder quantity, last successful sync, and actionable import failures. Unknown stock must not generate a reorder quantity.
- Reorder suggestion for low-stock products is max(0, target stock - current quantity); thresholds and targets are configured by the owner rather than presented as demand forecasts.
- Validate cross-business access denial, OAuth failures, expired credentials, incomplete imports, and preservation of the last good snapshot.
- Compare representative imported quantities with Clover, then verify a sale and a restock appear correctly on a subsequent sync.
- Use a separate Render service, database, signing secret, and encryption key. Do not modify main or the existing production deployment.

## Remaining external setup

Confirm the available Clover application and its live-merchant access, configure its callback and credentials securely in Render, and authorize the merchant through Clover. Do not paste passwords, access tokens, or client secrets into chat. Real merchant connectivity and browser acceptance are not yet verified.
