# Website inquiry inbox

The public website saves validated demo/support requests in `contact_inquiries`. It does not claim to send an email or depend on an unconfigured mailbox. Each submission stores the submitted name, email, optional business, message and timestamp. Do not put credentials into submissions.

The private inbox is at `/#/inquiries` and the paginated API is `/api/operations/inquiries`. Access requires all of:
- Signed-in Splynt account.
- Enabled account with verified email ownership.
- Exact email allowlisted in the staging/production service's `SPLYNT_CONTACT_OPERATOR_EMAILS` environment variable (comma-separated).

The default allowlist is empty. Store owners are not platform operators and cannot read other retailers' inquiries. Never grant access merely because an account registered with an operator email: email ownership must be independently verified. With SMTP configured, the operator can use the workspace’s Verify email flow to establish ownership. If delivery is not configured, the deployment operator must establish ownership out of band before marking their own account verified in the database. Do not mark arbitrary customer accounts verified.

Operators can read and reply using their email client from the inbox. Optional Brevo HTTPS notifications are now implemented; see CONTACT_NOTIFICATIONS.md. Sending is disabled until a verified sender and API key are configured and live delivery is tested. The owner has supplied a notification recipient in private local configuration. Operators should check the inbox even when email is enabled; email acceptance does not guarantee inbox delivery. Configurable retention remains a follow-up improvement.

Abuse controls: validated field lengths, a honeypot, three submissions per email per hour, and twenty submissions per minute globally. The current rate-limit check serializes requests in a single application instance; deployments with multiple replicas should use a shared atomic rate limiter. Add edge rate limiting or a bot challenge before accepting high-volume public traffic. Retain inquiries only as needed for follow-up, and avoid putting them into application logs.
