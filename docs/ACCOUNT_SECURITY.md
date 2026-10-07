# Account password changes

Signed-in users can choose **Change password** in the workspace navigation. The form requires the current password, a new password and confirmation. `POST /api/me/password` accepts only the authenticated account's change; it does not accept a target user ID. The new password follows the registration minimum of 10 characters and the encoder's 72-byte UTF-8 maximum.

The service locks the account, rechecks its enabled state and credential version, verifies the current password, hashes the replacement and increments the credential version in one transaction. JWT validation reads the account version on each authenticated request. Earlier tokens are rejected on subsequent requests; requests already executing are not retroactively cancelled. The successful form signs out, and the user signs in with the new password.

V18 adds the credential counter and an optimistic entity version. The latter prevents an older account update, such as a concurrent login timestamp write, from overwriting a newer password. Existing signed tokens without a credential-version claim represent version zero and remain valid until expiration or the first password change. Disabled or deleted users cannot authenticate with a previously issued token. Database availability is now required for JWT account validation; plan capacity for this lookup rather than caching indefinitely and delaying revocation.

Validation uses signed tokens through the real HTTP security chain, checks old/new password login behavior, rejected changes, anonymous denial, legacy tokens and isolation from other users. These tests also run against PostgreSQL in CI. Browser interaction remains unverified.

This is an authenticated password-change flow, **not forgotten-password recovery**. Recovery and email verification have a separate token/email workflow below; live delivery and browser acceptance are still required. No email is sent by this feature. No production migration or deployment has been performed.

## Recovery and verification tokens

V19 adds internal account-action tokens for password recovery and email verification. Each token contains 256 bits of random entropy; only its SHA-256 hash is persisted. Password-reset links expire after 30 minutes and verification links after 24 hours. Issuing a replacement removes the previous token of the same purpose. Tokens are bound to the account, current email and credential version. Changing email also increments that version, so changing it back cannot revive an older link.

Redemption locks the account before the token, re-reads the token after acquiring the lock, and atomically changes the account and deletes the token. A successful password reset invalidates previous JWTs. Invalid new passwords do not consume otherwise valid links. Verification cannot be used as a password-reset token, or vice versa.

Public request/redemption endpoints, request limits and browser pages are implemented below. Never return the raw issuance token in a public API response or log it. The delivery workflow must address failures/retries and use a configured trusted application origin for links. No recovery or verification emails have been sent in this implementation checkpoint.

## Queued SMTP delivery

V20 stores pending email actions with an account/email/credential snapshot. It never stores the raw action token. The worker generates a fresh token and submits the email within the job transaction, then marks the job SENT. SENT means the SMTP transport accepted the submission, not that the recipient received it. Jobs are locked for processing; repeated processing of a completed job does not send again. Duplicate pending requests for the same account/purpose are coalesced.

Failures roll back token issuance and job completion. The scheduler records a separate failed attempt, retries after 2, 4, 8 and 16 minutes, and stops after five failures. Requests older than an hour or whose account credentials/email changed are cancelled. Already-verified accounts do not receive another verification message. SMTP and database commit are not an atomic external transaction: a crash after SMTP acceptance can lead to a duplicate email on retry, and a link sent before rollback may be invalid. Messages tell users to use the latest link. A permanent delivery failure still needs operator monitoring and a fresh user request.

Sending defaults to disabled. Configuration, when a staging SMTP provider is ready:

- `SPLYNT_ACCOUNT_EMAIL_ENABLED=true`
- `SPLYNT_ACCOUNT_EMAIL_FROM`: provider-approved sender mailbox.
- `SPLYNT_ACCOUNT_EMAIL_ORIGIN`: trusted HTTPS application origin, no credentials, query, fragment or subpath.
- `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`: SMTP configuration; keep credentials outside Git/chat.

Authentication and required STARTTLS are enabled, with 5-second connect and 10-second read/write timeouts. Use a provider compatible with this transport configuration; do not disable certificate verification. Links place their token in the URL fragment so it is not included in the initial HTTP request. Operational delivery acceptance remains pending; cleanup is described below. Tests substitute a fake sender; no real mail was sent.

SMTP configuration follows [Spring Boot's email documentation](https://docs.spring.io/spring-boot/reference/io/email.html).

## Public recovery API

The following endpoints support the browser recovery pages:

- `GET /api/auth/recovery/status` reports whether email delivery is configured.
- `POST /api/auth/password-reset/request` and `/api/auth/email-verification/request` accept `{ "email": "..." }` and return the same 202 message for eligible, unknown, disabled, already-verified or throttled addresses. No raw token is returned. Disabled delivery returns 503 without queuing.
- `POST /api/auth/password-reset/confirm` accepts `{ "token": "...", "password": "..." }`.
- `POST /api/auth/email-verification/confirm` accepts `{ "token": "..." }`.

Confirmation uses POST, validates input and consumes the token atomically. It does not require an existing login. A configured delivery provider is needed to request a new email; existing valid links can still be redeemed during a delivery outage.

V21 adds a shared database guard and request ledger. Requests are limited to three per normalized email per hour across both purposes, and twenty accepted requests per minute globally, including unknown addresses. A pessimistic database lock serializes admission across replicas. Ledger entries store an email hash, not the submitted address, and expire after an hour during subsequent requests. Rate-limited requests have the same response as other eligible-shaped requests; no guarantee of constant response timing is made. Edge request limits are still appropriate for high-volume abuse because application requests reach the database. Queue and token cleanup are described below.

## Browser flow

Sign-in includes Forgot your password. Unverified accounts have Verify email in the workspace navigation. Request screens check delivery availability and show an unavailable state when SMTP is disabled. Eligible requests show the generic API response. Email links open dedicated reset/verification pages; verification requires an explicit button press rather than consuming the link on page load.

The page captures the token from the hash route and removes it from the visible URL/history entry without storing it in local/session storage. Reloading therefore requires reopening the email link. Invalid/duplicate token parameters cannot trigger confirmation. Expired-login events preserve the recovery route. Successful password reset clears the local login and offers sign-in; verification refreshes any active account before returning to the workspace.

Frontend unit tests cover route parsing and unauthenticated API calls; build/lint pass. They are not rendered UI tests. Desktop/mobile/keyboard and real-email acceptance remain required.

## Retention and maintenance

Hourly maintenance removes expired action tokens and request-ledger entries older than an hour. Pending email jobs older than an hour are cancelled; all email jobs older than seven days are removed, including stored recipient addresses. Recent SENT/FAILED/CANCELLED jobs remain available for operational diagnosis. Cleanup starts a minute after startup and continues even when SMTP is disabled/unavailable. If the application is stopped, cleanup resumes when it next starts. The schedule can be disabled with `splynt.account-maintenance.enabled=false` for isolated tests.

Cleanup uses job-before-token lock order consistent with delivery. PostgreSQL tests verify old data removal, stale-job cancellation and preservation of recent jobs and usable links. No retention change has been applied to production.
