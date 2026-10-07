# AI recommendations

Splynt's owner/admin-requested reports use the OpenAI Responses API with strict structured output. Set `OPENAI_API_KEY` and `SPLYNT_AI_MODEL` in the staging service's environment. Choose a model available to your API project that supports Responses and Structured Outputs. Neither value is needed for basic signup, Clover inventory or deterministic sales charts. Never expose the key through a `VITE_` variable or commit it. Non-production Spring profiles can set `splynt.ai.api-key` and `splynt.ai.model` directly in secure runtime configuration.

The integration follows [OpenAI's Structured Outputs documentation](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses): `text.format` requests a strict JSON schema. The server also validates field lengths, collection sizes and referenced evidence identifiers. Incomplete responses, refusals and malformed content are not displayed as completed reports. No tools or browsing are enabled for the model.

## Merchant workflow and shared data

The dashboard explains the data transfer before an owner or admin clicks Generate. Requests send the store's city/region/country and timezone, date window, aggregate sales totals, local hourly/weekday totals, and up to ten leading product names, quantities, velocity and stock-cover estimates. They do not send customers, payment details, credentials, account email, raw orders or stock movement notes. The request sets `store: false`; this is not a promise that all provider-side retention is disabled. Consult the provider's current data controls and your merchant-facing privacy terms before a production release.

Reports contain suggested actions, product experiments and limitations. Every action/experiment references one or more supplied evidence identifiers. The UI displays those original values in a readable detail view. Evidence-ID validation prevents nonexistent citations; it does not prove the model's reasoning is factually correct. Product ideas must be treated as experiments, not proven local demand or guaranteed revenue. No stock writes, purchase orders or supplier messages are performed.

## Availability and operating limits

Generation requires at least 14 complete local calendar days, 30 included orders, recognized product sales, a successful import within 24 hours and no current sales-sync warning. Insufficient-data and disabled states explain what is missing. Seasonal conclusions are currently withheld because the rolling evidence does not establish seasonality. Location provides context; external market/demographic/weather evidence is not available.

V12 stores the latest report, its evidence snapshot, model and timestamps per store. All current store members may read a report; only owners/admins can generate one. Each store gets at most one attempt per hour. Provider failures also start the cooldown and retain the previous successful report with an error notice. Polling reads stored reports and never generates paid calls. Old reports explicitly show their generation time and are not presented as live stock recommendations.

Requests use a five-second connection and 45-second read timeout with no application retry. A database store-row lock serializes generation across instances and protects the initial report insert. That lock is held during the provider call, so a simultaneous sync/settings operation for the same store may wait. Before scaling, move generation to a bounded job queue with a durable lease, store-wide spend limits and global concurrency limits. Configure provider project spending limits as well; a per-store cooldown is not a global spend cap.

## Verification and release gate

Tests use a local HTTP provider simulator for request shape, storage flag, refusals, incomplete/malformed output and invalid citations. Database-backed HTTP tests cover authorization, snapshots, cooldown, sanitized failures and stale/insufficient-data handling. They do not establish real-model output quality or account/model availability.

Before release, configure staging credentials, test reports on representative consenting merchant data, review ideas and numeric claims against their snapshots, test adversarial product names, and verify UI behavior. Real OpenAI requests have not been made in this development checkpoint. Comparative seasonal analytics and external local-market evidence are still unfinished.
