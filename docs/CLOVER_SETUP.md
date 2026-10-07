# Clover connection setup (development / staging)

Use branch `dev/splynt-platform` in a separate Render service with a separate PostgreSQL database. Do not point development at the production database. Existing production remains on its current branch and deployment.

## Environment

The Docker image activates Spring's `prod` profile. Set these variables on the **staging** service:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`: staging database only.
- `SPLYNT_JWT_SECRET`: a dedicated base64-encoded signing secret, at least 32 random bytes.
- `SPLYNT_JWT_ISSUER`: your staging origin.
- `CLOVER_CLIENT_ID`, `CLOVER_CLIENT_SECRET`: your registered Clover application's credentials.
- `CLOVER_ENCRYPTION_KEY`: base64 encoding of 32 random bytes (e.g. `openssl rand -base64 32`). Store securely and preserve across deployments; changing it requires reconnecting existing stores. Do not commit it or share it in chat.
- `CLOVER_REDIRECT_URI`: `https://YOUR-STAGING-HOST/api/integrations/clover/connect`.
- `CLOVER_BASE_URL`: `https://apisandbox.dev.clover.com` for sandbox.
- `CLOVER_AUTHORIZATION_URL`: `https://sandbox.dev.clover.com/oauth/v2/authorize` for sandbox.

Register the callback with Clover under the application's allowed Site URL. Configure merchant/inventory read permissions. Both endpoints must use the same Clover environment. For North American production, the API base is `https://api.clover.com` and authorization endpoint is `https://www.clover.com/oauth/v2/authorize`. Production requires the applicable Clover app approval and merchant authorization.

References: https://docs.clover.com/dev/docs/oauth-flows-in-clover

## Behavior

1. Register or sign in to Splynt and select your store.
2. As an owner/admin, choose Connect Clover and authorize the desired merchant.
3. Clover returns to the callback. Splynt verifies an expiring, single-use state and browser cookie, exchanges the code server-side, and verifies the merchant with the access token.
4. Credentials are encrypted and assigned to that store. The same merchant cannot be assigned to another store. Reconnect authorizes the same merchant; use a separate store for another merchant.
5. Background inventory refresh starts within the next scheduler cycle (default 60 seconds after the prior cycle completes). Sync now triggers it immediately. The dashboard polls status; closing the browser does not stop backend sync.

A provider authorization error returns to the app with `clover=failed`. No codes or tokens are included in the returned URL. The dashboard displays a recoverable connection message.

## Upgrade notes

V8 preserves legacy credentials but leaves them unassigned. There is deliberately no fallback to globally configured merchant tokens: each store must connect explicitly. Once an existing merchant is authorized, its credential row is attached to the store and replaced with encrypted tokens. The old `CLOVER_MERCHANT_ID`, `CLOVER_ACCESS_TOKEN`, and `CLOVER_REFRESH_TOKEN` variables are no longer used.

Inventory supports up to six decimal places. Clover negative balances are preserved. Missing, malformed, out-of-range or over-precision stock marks a catalog item as unknown rather than rounding it, setting it to zero, or hiding the catalog item. Connection warnings count items requiring review. Archived products stay archived. Quantity changes imported from Clover are audit adjustments, not evidence of individual sales; sales insights must ingest actual order/line-item data separately.

## Verification status

Automated tests use a local HTTP simulator for authorization-code exchange, merchant verification, per-store credentials, wrong-browser/expired/replayed state, revoked roles, token encryption, and pagination failures. These do not establish approval or real-account connectivity in Clover. The end-to-end real merchant acceptance check remains required before release.

### Sales-history permission and interpretation

Enable Clover order-read permission before connecting a staging store to import sales history. Inventory imports remain available when order access is missing; the Sales insights panel reports the failure separately. V10 adds sales events and cursor state. The initial import includes orders created within the last 90 days; subsequent imports reread orders modified since the previous cursor with a five-minute overlap. Changed orders replace their previous events in one transaction.

Only paid, non-refunded supported product lines are counted. Partially refunded orders are currently excluded rather than estimated. Ordinary line items count as one unit; per-unit quantities use Clover's thousandths representation. Unknown catalog items and unsupported quantities are skipped. No customer/payment details are persisted. Timing reflects order creation, not payment time.

Dashboard calculations use up to 30 full local calendar days covered by the last successful import. Failed or stopped imports do not add zero-sale days. The dashboard shows the last import and date window; stock-cover estimates assume the observed average continues and are not demand forecasts. Historical comparisons are available as described below; optional AI reports are documented in AI_RECOMMENDATIONS.md.

### Store location and local reporting

Owners and admins can open **Store settings** beside the store picker to set the store name, city, region, country and IANA timezone. This changes how existing sales timestamps are grouped; it does not alter original order times. Set the store currency before adding products or connecting Clover. Splynt does not convert existing inventory costs between currencies.

Use **Add store** to create another store inside an organization you own or administer. Each store has its own Clover connection, stock and reporting context. Choose the new store in the picker, then connect the matching Clover merchant. Settings edits use version checks to reject stale forms. Disabled accounts and inactive organizations cannot access stores with previously issued tokens.

### Historical comparisons

The dashboard now keeps a 24-month view of completed calendar months, compares daily sales rates against the same month in the preceding year, and compares leading products for the latest completed month. Missing or partially covered months are unknown; fully covered months with no included sales are zero. Month length and leap years are accounted for. The same historical evidence is available to AI reports.

The importer initially fetches up to 90 days, then accumulates history. The comparison feature does not fabricate older history. A recurring month pattern is shown only after two full annual cycles with at least 30 included orders per month, and requires both observations to be at least 20% above/below the corresponding annual daily averages. This is exploratory evidence; promotions, stockouts, store closures and assortment changes are not controlled. Product percentage comparisons require at least 10 included orders for that product in each month. These sample thresholds are practical heuristics, not statistical confidence guarantees.

### Imported catalog details and identity conflicts

Splynt imports paginated category memberships and stores SKU, alternate name, unit name, pricing type, availability, visibility and the returned category names. Product details show these fields; search includes SKU, alternate names and categories, and the category filter includes all returned category names. The alphabetically first returned category is used in the existing single-category table column. Missing optional values are shown as not provided. Availability/visibility do not archive or delete a Splynt product. This is catalog metadata, not a complete import of Clover taxes, modifiers, variants or every monetary/tax field.

Clover item ID is the imported product identity. A matching barcode alone will no longer convert a manual product or relink a different Clover item. Conflicts are skipped and included in the connection review list; no involved stock is overwritten. Open the import details to see the affected Clover item, barcode, reason and next step. Splynt does not merge records automatically. Deleted items and overlong required identifiers/names are also skipped. Local reorder targets and costs remain under the retailer's control.

Implementation references: [Clover inventory items and expansions](https://docs.clover.com/dev/reference/inventorygetitems), [Clover item fields](https://docs.clover.com/dev/reference/inventorycreateitem), and [category expansion behavior](https://docs.clover.com/dev/docs/managing-categories). The client separately pages all categories and each category’s items, then assembles memberships by item ID. Empty memberships clear stale categories. Any failed category page aborts the catalog snapshot instead of publishing partial memberships. This adds requests proportional to categories and membership pages; large catalogs should be load-tested against Clover rate limits. Collections can change between requests, so the import is not a provider-side atomic snapshot. Decimal and unknown-stock behavior is described below.


### Decimal quantities and unknown stock

V14 upgrades product quantities, reorder/target levels and audit balances to exact decimal columns with six fractional digits. Existing whole quantities migrate without conversion. Manual product creation, sales, restocks and target editing accept fractional values; extra precision or out-of-range input is rejected. Negative manual starting stock is rejected. Clover negative balances are preserved and trigger low-stock evaluation; they represent provider balances, not physical quantities to silently clamp to zero.

The product API returns `stockKnown: false` and `quantity: null` when provider stock is missing or unsupported. The previous numeric balance is retained internally for reconciliation history. Unknown stock does not produce a low-stock alert or stock-cover estimate; the dashboard highlights it and provides an Unknown stock filter. Catalog details still import. When known stock returns, audit adjustments describe reconciliation against the last recorded balance, not a sale.

Inventory valuation excludes unknown and negative balances and marks the value partial when needed. The dashboard counts products with positive stock rather than adding unlike units such as kilograms and bottles. Unit labels come from Clover catalog details when supplied. The sync response's legacy `skipped` count now means items requiring review (including products whose catalog imported but stock is unknown), so it must not be added to created/updated counts to derive a total.


### Reviewing import problems

The Clover connection panel exposes the last completed import's actionable issues: unknown stock, barcode conflicts, invalid barcodes and incomplete item identity. Each entry includes a bounded item name/ID/barcode and a suggested next step. The response retains at most 100 details while preserving the total issue count. Intentional Splynt archives and Clover deletions remain skipped but do not create actionable warnings.

A successful import replaces the review list, so resolved issues disappear. A failed refresh keeps the previous list and timestamp, with a separate refresh error. An older completed result cannot overwrite a newer review snapshot. Details use the existing store-member authorization and never include tokens or provider response bodies. V15 persists the issue snapshot and count. The legacy `skipped` field still counts records not fully imported; the new `issueCount` counts actionable review items only.

After a completed catalog and stock fetch, previously imported active Clover products that are absent or explicitly deleted are marked unknown and listed for review. Splynt preserves their last recorded balance, audit history, and local settings; it does not infer a sale or automatically archive them. Reappearance restores normal reconciliation. Failed fetches do not apply absence detection. Manual and locally archived products are unaffected.

## Imported prices and costs

Clover catalog details include the provider selling price and item cost in a verified merchant currency. Splynt reads only `defaultCurrency` from the merchant-properties response for this purpose; it does not persist the full properties payload. Clover documents inventory amounts in cents. Two-decimal ISO currencies are supported; absent/invalid/non-two-decimal currency leaves monetary values unavailable without blocking stock import. Each sync replaces these fields, so an unavailable currency cannot silently retain an old converted value.

Prices are displayed for FIXED/PER_UNIT pricing; VARIABLE prices are set at sale. Missing, fractional-cent, negative and oversized provider monetary amounts are unavailable rather than rounded. Values are stored as exact decimal strings in existing catalog JSON. Clover tax settings determine whether selling prices include tax; Splynt does not calculate tax here.

A mismatch with Splynt's store currency is shown in product details. There is no conversion or automatic relabeling of local costs. Imported cost is reference metadata; inventory valuation continues using the independently entered Splynt unit cost. Verify both currencies before using imported costs for purchasing decisions. Live merchant currency/price acceptance remains required.

References: [Clover inventory money format](https://docs.clover.com/dev/docs/managing-items-item-groups), [merchant properties](https://docs.clover.com/dev/reference/merchantgetmerchantproperties).

## Recovering incomplete sales imports

V16 adds a persisted retry cursor. If a paid order has unsupported quantities, missing product matches or malformed required fields, subsequent imports repeat the affected window rather than advancing past the missing data. The warning blocks AI generation until that window imports cleanly. Unpaid, refunded, test, deleted, fee and out-of-coverage records are intentional exclusions and do not create this warning. Failed provider requests roll back the sales transaction and preserve its cursor.

For existing connections V16 schedules one re-read from the stored coverage start, because older versions could clear a warning without retrying the original omissions. No history before that coverage is invented. Re-reading is idempotent by order/line identity. A permanently unsupported paid line keeps the retry window open, which increases provider traffic; inspect catalog/quantity issues and load-test larger histories before release. This is recovery for incomplete imports, not a claim that every Clover sale is supported.

## Historical quantity units

V17 stores each sales line's measurement unit separately from the current catalog. Existing rows start unknown; connected stores re-read the stored coverage window to recover provider units where available. Weighted lines without a unit stay unknown. Splynt does not guess past units from today's product settings or convert ounces/pounds automatically.

Current and yearly product quantity totals/rates are withheld when a comparison mixes units or includes unknown units. Stock-cover estimates additionally require the historical unit to match current inventory metadata. Distinct-order rankings/timing/monthly patterns remain available. These guards prevent a catalog measurement change from silently rewriting the meaning of old sales. Missing provider history can remain unknown after re-import.

### Bounded order time windows

Order reads now split the requested modified-time range into consecutive half-open windows of at most 30 days, each independently paginated. Clover documents a maximum 90-day span for time-filtered requests and may narrow larger requests to the latest 90 days; a single unbounded retry request is therefore not sufficient. Both lower and upper bounds are sent. If an order moves into a later modified-time window during the read, the later snapshot replaces its earlier entry. Any failed window aborts the whole response so the sales transaction cannot publish partial coverage. The total import remains capped at one million distinct orders.

This fixes long retry-window retrieval; it does not yet add an older-history backfill control. New connections still start with 90 days, so recurring seasonal comparisons require sufficient accumulated history. Real merchant parity remains unverified. Reference: [Clover date filters and 90-day restriction](https://docs.clover.com/dev/docs/applying-filters).
