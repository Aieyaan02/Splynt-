# Splynt Clover submission readiness

Audited 2026-10-13 against the local `pilot` source. **Not ready to submit.** No production dashboard, app permissions, live Clover consent flow, video, or public deployment was verified in this audit. This is a source review, not a penetration test or legal approval.

## Release being reviewed

Private web app for one US merchant/location. Read-only inventory import, low/unknown stock visibility and merchant-configured reorder targets. Not sales forecasting, AI, automatic purchasing, payment processing or inventory writes to Clover. The platform-owner portal is a separately built frontend; reviewers receive a merchant test account, never the platform administrator's account.

## Evidence and gaps

| Area | Evidence | Status / next action |
|---|---|---|
| Developer verification | Owner reported submission pending | Owner confirms Clover approval; account approval is distinct from app approval |
| Production private app | Only sandbox app previously shown | Create/configure production private web app after account approval; do not claim existing sandbox app is production-ready |
| OAuth | CloverOAuthController, CloverConnectionService, CloverOAuthStateService; code exchange, browser-bound state, encrypted expiring tokens and refresh | Implemented; actual Clover browser consent/expiry/reconnect testing outstanding |
| Clover-origin launch | Only authenticated POST begin and GET callback exist | BLOCKER: dedicated safe launch/onboarding flow needed. Do not configure callback as Alternate Launch Path; it expects an existing state/cookie |
| Minimal permissions | CloverInventoryClient reads items, item_stocks, categories, category items, merchant properties; connection verifies merchant identity | Request merchant/inventory READ only; confirm exact dashboard labels and denial behavior. Sales disabled |
| Read-only behavior | Clover inventory calls use GET; OAuth token exchanges use POST | No purchase orders or Clover stock updates in pilot; demonstrate with real sandbox evidence |
| Sync and recommendations | Paginated import and threshold/target calculation; tenant tests in existing suite | Automated evidence exists; compare representative catalog in Clover UI before recording |
| Disconnect / uninstall | No disconnect endpoint or UI found in source | BLOCKER: implement local credential removal, stop future syncs, handle uninstall/revocation, specify retained-data behavior; do not promise remote revocation unless implemented and tested |
| Data deletion / retention | No complete merchant-account deletion workflow found | BLOCKER: define and implement verified deletion, dependent records, backups and retention before publishing policy |
| PostgreSQL release check | MFA timestamp mismatch fixed in 53e4610; application, PostgreSQL and container jobs passed in run 38365852207 | Verified for 53e4610; rerun checks after functional changes |
| Admin security | MFA/throttling/audit code on pilot; local enrollment still unconfirmed | Finish owner enrollment and latest CI review; no claim of MFA activation based solely on code |
| Support / recovery | Contact form saves requests; email delivery disabled | Confirm monitored channel and workable reviewer onboarding/recovery; no promise of email notifications |
| Public privacy / terms | No published policy pages found | Owner identity, support contact, actual retention and hosting must be settled first |
| Hosting / HTTPS | Separate Neon project reported; no pilot Render deployment | Deploy review candidate before app review; validate free plan in account, startup, HTTPS, secrets and backups |
| Functional video | No recording located | Use VIDEO_SCRIPT.md after sandbox acceptance; hide all secrets and mark test data |
| Branding | UI generic store and sample counts are illustrative | Do not present landing illustration as imported merchant results; capture actual inventory screen |

## Ordered release gates

1. Resolve lifecycle gaps (launch, disconnect/uninstall, verified deletion) and test authorization/races.
2. Owner supplies legal operator and public support contact; decide implementable retention and publish accurate privacy/terms.
3. Test the sandbox lifecycle: install/launch, consent, sync, error/retry, reconnect, disconnect/uninstall and deletion.
4. When developer approval permits, configure the production app and review hosting. Set exact HTTPS Site URL, launch path and callback; use separate production credentials/database. Keep AI/email features disabled unless deliberately configured and tested.
5. Record video and screenshots from the release candidate. Provide a reviewer merchant account through Clover's approved channel; no credentials in this repository.
6. Complete Clover's live submission checklist, review the exact package with the owner, then submit. App approval remains Clover's decision.

No app submission, hosting purchase or real-store authorization was performed by this audit.

## Current official references

- https://docs.clover.com/dev/docs/gdp-submit-your-app-for-approval — account approval prerequisite, functional description/video, submission checklist.
- https://docs.clover.com/dev/docs/gdp-work-with-private-apps — private distribution still requires approval; Alternate Launch Path and expiring OAuth tokens.
- https://docs.clover.com/dev/docs/clover-functional-review-playbook — functional review process. Confirm dashboard-specific checklist at submission.
