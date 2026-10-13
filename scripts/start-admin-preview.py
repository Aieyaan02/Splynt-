#!/usr/bin/env python3
"""Start the private, persistent local backend. Never prints credential values."""
from pathlib import Path
import base64, os, secrets, socket, subprocess
root = Path(__file__).resolve().parent.parent
os.chdir(root)
with socket.socket() as check:
    if check.connect_ex(('127.0.0.1', 8087)) == 0:
        raise SystemExit('The backend is already running on port 8087. Stop the existing preview before starting this one.')
folder = root / '.env.admin-preview'
folder.mkdir(mode=0o700, exist_ok=True)
config = folder / 'runtime.env'
values = {}
if config.exists():
    values = dict(line.split('=', 1) for line in config.read_text().splitlines() if '=' in line)
for key, size in [('SPLYNT_JWT_SECRET', 48), ('SPLYNT_PLATFORM_MFA_KEY', 32)]:
    values.setdefault(key, base64.b64encode(secrets.token_bytes(size)).decode())
values.setdefault('SPRING_DATASOURCE_PASSWORD', secrets.token_urlsafe(32))
if not config.exists(): config.touch(mode=0o600)
os.chmod(config, 0o600)
config.write_text(''.join(f'{key}={value}\n' for key, value in values.items()))
subprocess.run(['./mvnw', '-q', '-DskipTests', 'compile', 'dependency:build-classpath', '-Dmdep.outputFile=target/platform-password-classpath.txt'], check=True)
env = os.environ.copy()
env.update(values)
env.update(SPLYNT_PLATFORM_BOOTSTRAP_ENABLED='false', SPRING_DATASOURCE_URL='jdbc:h2:file:'+str(folder/'database')+';MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH', SPRING_DATASOURCE_USERNAME='splynt_local', SPRING_DATASOURCE_DRIVER_CLASS_NAME='org.h2.Driver', SPRING_DOCKER_COMPOSE_ENABLED='false', SERVER_ADDRESS='127.0.0.1', SERVER_PORT='8087', SPLYNT_INTEGRATIONS_CLOVER_BACKGROUND_SYNC='false', SPLYNT_ACCOUNT_MAINTENANCE_ENABLED='false', SPLYNT_SALES_ENABLED='false')
print('Starting local backend with persistent storage and MFA enabled. Keep this Terminal open.')
os.execvpe('java', ['java', '-Dspring.devtools.restart.enabled=false', '-cp', 'target/classes:'+(root/'target/platform-password-classpath.txt').read_text().strip(), 'com.aieyaan.splynt.SplyntApplication'], env)
