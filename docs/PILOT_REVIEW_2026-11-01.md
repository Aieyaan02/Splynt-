# Pilot verification — 2026-11-01

Base: pilot at 1cb3378. This review adds lifecycle controls; it does not deploy or submit the application.

Original backend: 245 tests passed and clean verify/package succeeded after Maven cache access was granted. Frontend baseline: 17 tests, lint, merchant build and separate admin build passed. Known-pattern tracked-file scan passed; it is not proof that all possible secrets are absent.

Changes: authenticated Clover uninstall callback; authorization timestamps to ignore delayed prior-install events; refresh HTTP 401 clears credentials; owner-confirmed workspace deletion and account deletion with password, exact confirmation and attempt limits; dashboard deletion dialog; explicit Mockito test JVM agent; PostgreSQL CI coverage for new lifecycle tests.

Release acceptance still requires live hosted Clover consent/import/reconnect/uninstall tests, realistic catalog memory testing, final operator/support/privacy details, backup retention and restore testing, and Clover approval. No blanket security certification is claimed.

Dependency audit: runtime frontend dependencies report zero known vulnerabilities. Full npm audit reports Vite development-tool advisories (one high, one low including dependent plugin) with no fix offered by npm for the resolved tree. Vite is build-time only in the Dockerfile; never expose its development server as production. Recheck available remediation before sharing development previews remotely.

## Final local results (completed 2026-11-02)

- Backend verify/package: 250 tests, zero failures/errors/skips.
- Broad PostgreSQL integration selection: 117 tests passed; final lifecycle rerun after refresh cleanup and populated-deletion test: 18 passed.
- Frontend: 17 tests, lint, merchant build and separate admin build passed.
- Docker production image built successfully. Fresh PostgreSQL database migrated and app started in approximately 6 seconds under a 512 MiB container memory limit.
- Packaged HTTP smoke test passed: landing/assets, database health, registration/login/account, anonymous rejection, merchant denial of platform overview, unauthenticated webhook denial, workspace/account deletion and old-token rejection.
- Observed approximately 384 MiB during the small smoke test; not a representative-catalog load test or a guarantee against out-of-memory failures.
- Known-pattern working-tree/history secret scans passed. No production secrets or store data were used for these tests.

The technical candidate can proceed to a controlled hosted acceptance test. It has not been deployed, connected to a real store, or submitted to Clover by this work. Public launch remains gated on the live acceptance and operator/privacy/support/backup items above. The production admin frontend is a separate build and must be hosted/configured separately.
