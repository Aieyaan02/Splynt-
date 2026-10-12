# Secret handling

Never commit real credentials, `.env` files, merchant exports, database dumps or production logs. Public API route code and environment variable names are source code, not secrets; actual key/token/password values belong only in local ignored configuration or the hosting secret store. Do not put secrets in frontend VITE variables, because frontend configuration is public.

Run `python3 scripts/check-secrets.py` before a commit and `python3 scripts/check-secrets.py --history` to check reachable history. The scan detects common provider credentials, private-key headers and Neon credential URLs, prints only locations, and runs in CI. It is a limited pattern scan, not a complete security audit. Review new configuration manually as well. If a real credential is ever committed, revoke/rotate it first; deleting the file does not remove Git history.

Email, sales import and AI remain disabled/unconfigured for the inventory pilot. Never copy secrets between the demo and pilot. Use separate database credentials, JWT signing key and token encryption key. Real provider authorization must occur through the server-side integration.
