#!/usr/bin/env python3
"""Scan tracked text (or all reachable Git history) without printing secret values."""
import re
import subprocess
import sys

PATTERNS = [
    r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
    r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b',
    r'\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{32,}\b',
    r'\bxkeysib-[A-Za-z0-9-]{40,}\b',
    r'\bAKIA[0-9A-Z]{16}\b',
    r'postgres(?:ql)?://[^\s:/]+:[^\s@]+@[^\s]*neon\.tech',
]
compiled = [re.compile(pattern) for pattern in PATTERNS]
def git(*args):
    return subprocess.check_output(['git', *args])

def scan(label, content):
    text = content.decode('utf-8', errors='replace')
    return [f'{label}:{number}: suspected credential (value redacted)'
            for number, line in enumerate(text.splitlines(), 1)
            if any(pattern.search(line) for pattern in compiled)]

findings = []
if '--history' in sys.argv:
    seen = set()
    for entry in git('rev-list', '--objects', '--all').decode().splitlines():
        oid, _, path = entry.partition(' ')
        if oid in seen or not path:
            continue
        seen.add(oid)
        if git('cat-file', '-t', oid).strip() == b'blob':
            findings.extend(scan(f'{oid[:12]}:{path}', git('cat-file', 'blob', oid)))
else:
    from pathlib import Path
    for path in git('ls-files', '-z').decode().split('\0'):
        if path and Path(path).is_file():
            if Path(path).name.startswith('.env') and Path(path).name != '.env.example':
                findings.append(f'{path}: environment file must not be tracked')
            findings.extend(scan(path, Path(path).read_bytes()))
print('\n'.join(findings) if findings else 'No known credential patterns found. This is not a guarantee against every secret format.')
sys.exit(bool(findings))
