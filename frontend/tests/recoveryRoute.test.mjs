import { test } from 'node:test';
import assert from 'node:assert/strict';
import { recoveryRoute } from '../src/lib/recoveryRoute.ts';

test('recovery links keep action and token distinct', () => {
    const token = 'a'.repeat(43);
    assert.deepEqual(recoveryRoute('/reset-password?token=' + token), { path: '/reset-password', token });
    assert.deepEqual(recoveryRoute('/verify-email?token=' + token), { path: '/verify-email', token });
    assert.deepEqual(recoveryRoute('/forgot-password'), { path: '/forgot-password', token: null });
    assert.equal(recoveryRoute('/app?token=' + token), null);
});
test('invalid or duplicate tokens cannot become confirmation actions', () => {
    const token = 'a'.repeat(43);
    for (const query of ['token=short', 'token=' + token + '&token=' + token, 'token=%3Cscript%3E', 'token=' + 'a'.repeat(44), '']) {
        assert.deepEqual(recoveryRoute('/verify-email?' + query), { path: '/verify-email', token: null });
    }
});
