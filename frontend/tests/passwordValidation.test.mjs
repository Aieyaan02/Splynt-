import { test } from 'node:test';
import assert from 'node:assert/strict';
import { passwordEncodingError } from '../src/lib/passwordValidation.ts';

test('password limit matches encoded length without truncating Unicode', () => {
    assert.equal(passwordEncodingError('a'.repeat(72)), null);
    assert.ok(passwordEncodingError('a'.repeat(73)));
    assert.equal(passwordEncodingError('é'.repeat(36)), null);
    assert.ok(passwordEncodingError('é'.repeat(37)));
    assert.equal(passwordEncodingError('🔑'.repeat(18)), null);
    assert.ok(passwordEncodingError('🔑'.repeat(19)));
});
