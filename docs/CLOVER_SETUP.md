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

Inventory currently supports nonnegative whole units. Items with unknown, fractional, negative, or out-of-range stock are skipped, not rounded or set to zero. Skipped counts appear in connection status. Archived products stay archived. Quantity changes imported from Clover are audit adjustments, not evidence of individual sales; sales insights must ingest actual order/line-item data separately.

## Verification status

Automated tests use a local HTTP simulator for authorization-code exchange, merchant verification, per-store credentials, wrong-browser/expired/replayed state, revoked roles, token encryption, and pagination failures. These do not establish approval or real-account connectivity in Clover. The end-to-end real merchant acceptance check remains required before release.

### Sales-history permission and interpretation

Enable Clover order-read permission before connecting a staging store to import sales history. Inventory imports remain available when order access is missing; the Sales insights panel reports the failure separately. V10 adds sales events and cursor state. The initial import includes orders created within the last 90 days; subsequent imports reread orders modified since the previous cursor with a five-minute overlap. Changed orders replace their previous events in one transaction.

Only paid, non-refunded supported product lines are counted. Partially refunded orders are currently excluded rather than estimated. Ordinary line items count as one unit; per-unit quantities use Clover's thousandths representation. Unknown catalog items and unsupported quantities are skipped. No customer/payment details are persisted. Timing reflects order creation, not payment time.

Dashboard calculations use up to 30 full local calendar days covered by the last successful import. Failed or stopped imports do not add zero-sale days. The dashboard shows the last import and date window; stock-cover estimates assume the observed average continues and are not demand forecasts. Seasonal and AI product recommendations remain future implementation work.

### Store location and local reporting

Owners and admins can open **Store settings** beside the store picker to set the store name, city, region, country and IANA timezone. This changes how existing sales timestamps are grouped; it does not alter original order times. Set the store currency before adding products or connecting Clover. Splynt does not convert existing inventory costs between currencies.

Use **Add store** to create another store inside an organization you own or administer. Each store has its own Clover connection, stock and reporting context. Choose the new store in the picker, then connect the matching Clover merchant. Settings edits use version checks to reject stale forms. Disabled accounts and inactive organizations cannot access stores with previously issued tokens.

### Historical comparisons

The dashboard now keeps a 24-month view of completed calendar months, compares daily sales rates against the same month in the preceding year, and compares leading products for the latest completed month. Missing or partially covered months are unknown; fully covered months with no included sales are zero. Month length and leap years are accounted for. The same historical evidence is available to AI reports.

The importer initially fetches up to 90 days, then accumulates history. The comparison feature does not fabricate older history. A recurring month pattern is shown only after two full annual cycles with at least 30 included orders per month, and requires both observations to be at least 20% above/below the corresponding annual daily averages. This is exploratory evidence; promotions, stockouts, store closures and assortment changes are not controlled. Product percentage comparisons require at least 10 included orders for that product in each month. These sample thresholds are practical heuristics, not statistical confidence guarantees.
