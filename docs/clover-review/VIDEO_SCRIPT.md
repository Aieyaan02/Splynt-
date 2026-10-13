# Reviewer video and acceptance script

Draft; not recorded. Target roughly 4–6 minutes, extend if needed to show the complete workflow. Use only test merchant data and a separate merchant reviewer account. Never show secrets, setup keys, passwords, recovery codes, browser storage, hosting configuration or the owner-only admin portal.

| Segment | Action and narration | Evidence to capture |
|---|---|---|
| Purpose | “Splynt helps an independent retailer see low stock and plan restocking from their own inventory targets.” | Actual merchant dashboard; label sandbox/sample data |
| Install / launch | Open from Clover using the implemented launch flow; sign into/create the intended Splynt workspace | BLOCKED until launch flow exists; show actual redirect and consent |
| Connect | Choose Connect POS, authorize intended test merchant | Permission screen showing only required read access, without authorization-code URLs |
| Import | Run Sync now; compare products and stock with Clover | Normal quantity, fractional quantity, negative quantity and unavailable stock if supported by fixtures |
| Reorder | Use stock 2, threshold 5, target 20; show suggestion 18 | Explain deterministic merchant settings, not AI demand forecasting; restore fixture settings afterward |
| Ownership | Show imported quantities cannot be edited through Splynt | No Clover writes or automatic orders |
| Failure and retry | Use controlled denied/revoked authorization or a safe test failure; retry after recovery | Clear error, preserved last successful sync/data; no fake current-success timestamp |
| Isolation | Use second independent merchant test account | Cannot view the first store's inventory or connection; no cross-tenant response contents |
| Disconnect / uninstall | Demonstrate implemented lifecycle once available | BLOCKED: show sync stops and explain what data remains |
| Support / deletion | Open real published help/privacy pages; demonstrate verified deletion procedure | BLOCKED until policy and workflow exist |

## Acceptance record (complete during actual execution)

For every segment record: commit SHA, environment, test account alias, test merchant alias, expected result, actual result, pass/fail, timestamp, screenshot/video timestamp and any defect. Never record passwords, authorization codes or tokens.

Before recording, verify the browser is on the exact release candidate and reset only disposable fixtures with owner authorization. The hero's 5,810/228 counts are a marketing illustration, not proof of a successful import.

Before sharing, review the entire recording for secrets and merchant personal data. Confirm the hosting link is accessible to Clover reviewers. Put its URL into Clover's Functional Video field only after owner review; no video has been uploaded by this task.
