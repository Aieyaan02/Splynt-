# Draft Clover submission copy

Not submitted. Use only after READINESS.md blockers are resolved. Reconcile field limits and required information with the current production dashboard.

## App identity

- Name: Splynt
- Intended type: private web app
- Country: United States; initial merchant location Miami Beach, Florida
- Language: English
- Legal operator: OWNER INPUT REQUIRED
- Public support contact: OWNER INPUT REQUIRED
- HTTPS Site URL / Alternate Launch Path / callback: NOT CONFIGURED
- Privacy / terms URLs: NOT PUBLISHED
- Functional video URL: NOT RECORDED
- Reviewer access: provision privately; do not put credentials here

## Short description

Inventory visibility and target-based restock suggestions for independent retailers.

## Functional description

Splynt gives a retailer a private workspace for reviewing inventory imported from their authorized Clover merchant account. The retailer can search products, review low or unknown stock, and set reorder thresholds and target quantities. Suggested quantities use those settings and the latest successfully imported stock. The dashboard shows refresh status and import issues so the retailer can identify data needing review.

The initial pilot is read-only with respect to Clover inventory. It does not process payments, modify Clover stock, send supplier orders, or use AI forecasting. Store records are restricted to authorized members of the corresponding business. The retailer remains responsible for verifying recommendations before purchasing stock.

## Permissions justification

| Proposed access | Calls / purpose |
|---|---|
| Merchant READ | Validate authorized merchant identity and read merchant properties/currency |
| Inventory READ | GET items, item_stocks, categories and category-item associations for inventory display/filtering |

Confirm exact permission names in Clover; this table is not a verified export of configured permissions. No customer, employee, payment, order or inventory-write permissions are intended for this pilot. OAuth token exchange/refresh POST requests are authentication operations, not inventory writes.

## Honest reviewer notes

The review deployment is separate from the existing sample deployment. Low-stock suggestions are rule-based, not predictive. Unknown stock is marked for review rather than invented as zero. Sync is manually triggered in the initial free-host setup. Describe any cold-start delay accurately after measuring it. Reviewer access is merchant-level; the private platform portal is not required for normal merchant functionality.

## Privacy and support facts to settle before writing final public policies

1. Legal operator and public support contact.
2. Data collected: account/profile and organization/store details; imported catalog, stock and inventory changes; POS authorization tokens; operational sync and security audit records; contact inquiries. Confirm each against release configuration.
3. Actual hosting providers/regions and any email provider enabled for release. AI processing stays disabled in this pilot.
4. Why each category is needed, authorized recipients, and how merchant requests are verified.
5. Exact deletion workflow, retention schedule and backup expiry, with operational ownership. No “immediate deletion” or fixed deadline may be promised without implementation.
6. Disconnect versus deletion: removing POS access must not be represented as erasing all historical data.
7. Support hours and response expectations the owner can actually maintain.

This is a factual policy outline, not finished legal terms. Do not publish placeholders or claim legal/security certification.
