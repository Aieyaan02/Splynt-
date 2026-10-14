#!/usr/bin/env python3
"""Collect sandbox credentials locally without echoing or logging the app secret."""
import base64
import getpass
import os
from pathlib import Path
import re
import secrets
import sys

def main():
    if not sys.stdin.isatty():
        raise SystemExit('Run this script in your own interactive Terminal.')
    root = Path(__file__).resolve().parent.parent
    folder = root / '.env.admin-preview'
    folder.mkdir(mode=0o700, exist_ok=True)
    config = folder / 'runtime.env'
    values = dict(line.split('=', 1) for line in config.read_text().splitlines() if '=' in line) if config.exists() else {}
    print('Use the Clover SANDBOX app only. Your secret will not appear as you paste it.')
    app_id = input('Sandbox App ID: ').strip()
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', app_id):
        raise SystemExit('Invalid app ID. No changes saved.')
    secret = getpass.getpass('Sandbox App Secret (hidden): ').strip()
    if not secret or any(char.isspace() for char in secret):
        raise SystemExit('Invalid app secret. No changes saved.')
    prefix = 'SPLYNT_INTEGRATIONS_CLOVER_'
    values.update({
        prefix + 'CLIENTID': app_id,
        prefix + 'CLIENTSECRET': secret,
        prefix + 'BASEURL': 'https://apisandbox.dev.clover.com',
        prefix + 'AUTHORIZATIONURL': 'https://sandbox.dev.clover.com/oauth/v2/authorize',
        prefix + 'REDIRECTURI': 'http://127.0.0.1:5173/api/integrations/clover/connect',
    })
    values.setdefault(prefix + 'ENCRYPTIONKEY', base64.b64encode(secrets.token_bytes(32)).decode())
    # Preserve existing JWT, MFA and database keys. Never print any values.
    temporary = folder / 'runtime.env.tmp'
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, 'w') as output:
        output.write(''.join(f'{key}={value}\n' for key, value in values.items()))
    os.replace(temporary, config)
    print('Sandbox configuration saved privately. Restart the local backend to activate it.')

if __name__ == '__main__':
    main()
