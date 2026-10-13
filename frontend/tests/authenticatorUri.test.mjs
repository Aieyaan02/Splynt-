import test from 'node:test';
import assert from 'node:assert/strict';
import {authenticatorUri} from '../admin/authenticatorUri.ts';

test('provisioning uses interoperable TOTP parameters matching the server', () => {
  const secret = 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP'; // Synthetic fixture only.
  const uri = new URL(authenticatorUri(secret));
  assert.equal(uri.protocol, 'otpauth:');
  assert.equal(uri.hostname, 'totp');
  assert.equal(decodeURIComponent(uri.pathname), '/Splynt:Platform Admin');
  assert.deepEqual(Object.fromEntries(uri.searchParams), {
    secret, issuer: 'Splynt', algorithm: 'SHA1', digits: '6', period: '30',
  });
});

test('rejects malformed keys without including sensitive values in the error', () => {
  for (const key of ['', 'private-invalid-key', 'A'.repeat(31), '0'.repeat(32)]) {
    assert.throws(() => authenticatorUri(key), {message: 'Invalid authenticator setup key'});
  }
});
