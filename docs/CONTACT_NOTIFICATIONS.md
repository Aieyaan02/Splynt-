# Inquiry email notifications

The pilot can notify one configured operator through Brevo's HTTPS transactional email API. HTTPS avoids Render Free's blocked SMTP ports. Use Brevo's Free plan only; no paid plan, automatic upgrade or paid add-on is authorized.

## Required configuration

- SPLYNT_CONTACT_EMAIL_TO: owner's chosen recipient (recorded in ignored `.env.pilot.local`, not committed). This does not grant private-inbox access.
- SPLYNT_CONTACT_EMAIL_FROM: sender approved/verified in Brevo. A recipient Gmail address does not itself provide a sending identity. Follow the provider's sender/domain requirements.
- BREVO_API_KEY: API key placed directly into hosting secrets, never source code or chat.
- SPLYNT_CONTACT_EMAIL_ENABLED=true only after the above is configured and a controlled test is ready. Default false.

The local `.env.pilot.local` is a deployment reference, not automatically loaded by Spring Boot. Do not upload it to Git. Configure the values in Render explicitly. The owner must sign up/log in, accept any provider terms, and verify the sender themselves. No live account or sender has been configured yet.

## Behavior and limits

Every new inquiry is saved first, with pending notification state. An enabled worker checks every 15 seconds while the server is awake, up to ten records per cycle. The provider recipient and sender are fixed by server configuration. Visitor fields appear only in plain text, not HTML/headers. Replies must be addressed to the visitor email shown in the message.

Row locks prevent simultaneous workers from processing the same inquiry. Failed attempts retry after 2, 4, 8 and 16 minutes, then remain FAILED after five total attempts. FAILED records remain available in the private inbox/database; an operator must investigate delivery and arrange follow-up. Provider acceptance is not confirmed inbox delivery. A crash after provider acceptance but before transaction commit can produce a duplicate notification. No exactly-once guarantee is made.

V23 marks pre-existing inquiries SKIPPED so deployment does not automatically email an old backlog. New inquiries collected while disabled remain pending and will send when enabled; inspect the pending queue before enabling. Free-host sleeping delays notifications. Free provider quotas can delay/fail delivery; no automatic paid upgrade occurs in application code. Monitor usage and delivery in the provider dashboard.

No provider error bodies, keys, email addresses or inquiry messages are logged by the worker. Public submission confirms storage only. Email notifications do not verify an operator account or grant inquiry-inbox access: the existing verified-email allowlist applies independently.

## Acceptance before launch

Set up a Free provider account and verified sender; configure secrets in the pilot only; enable and submit a clearly labeled test inquiry. Confirm the email arrives at the intended recipient, inspect spam folder if necessary, confirm the saved inquiry is accessible only to an authorized operator, and test replying to the visitor. Real delivery, PostgreSQL migration and provider quota behavior remain unverified until these checks are run.

References:
- https://developers.brevo.com/docs/send-a-transactional-email
- https://help.brevo.com/hc/en-us/articles/208589409-About-Brevo-s-pricing-plans
- https://render.com/docs/free
