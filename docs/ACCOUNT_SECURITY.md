# Account password changes

Signed-in users can choose **Change password** in the workspace navigation. The form requires the current password, a new password and confirmation. `POST /api/me/password` accepts only the authenticated account's change; it does not accept a target user ID. The new password follows the registration minimum of 10 characters and the encoder's 72-byte UTF-8 maximum.

The service locks the account, rechecks its enabled state and credential version, verifies the current password, hashes the replacement and increments the credential version in one transaction. JWT validation reads the account version on each authenticated request. Earlier tokens are rejected on subsequent requests; requests already executing are not retroactively cancelled. The successful form signs out, and the user signs in with the new password.

V18 adds the credential counter and an optimistic entity version. The latter prevents an older account update, such as a concurrent login timestamp write, from overwriting a newer password. Existing signed tokens without a credential-version claim represent version zero and remain valid until expiration or the first password change. Disabled or deleted users cannot authenticate with a previously issued token. Database availability is now required for JWT account validation; plan capacity for this lookup rather than caching indefinitely and delaying revocation.

Validation uses signed tokens through the real HTTP security chain, checks old/new password login behavior, rejected changes, anonymous denial, legacy tokens and isolation from other users. These tests also run against PostgreSQL in CI. Browser interaction remains unverified.

This is an authenticated password-change flow, **not forgotten-password recovery**. Recovery and email verification still need secure token delivery and acceptance with a configured email service. No email is sent by this feature. No production migration or deployment has been performed.
