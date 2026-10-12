import { test } from 'node:test';
import assert from 'node:assert/strict';
import { intelligenceReadiness } from '../src/lib/intelligenceReadiness.ts';
const now = Date.parse('2026-10-12T12:00:00Z');
const source = {lastSyncedAt: '2026-10-12T11:00:00Z', syncError: null, summary: {completeDays: 14, orders: 30, sufficientForVelocity: true, topProducts: [{}]}};
test('readiness requires sufficient matched sales and recent successful import', () => {
 assert.ok(intelligenceReadiness(source, now).every(check => check.ready));
 for (const timestamp of [null, 'invalid', '2026-10-10T11:00:00Z', '2026-10-13T11:00:00Z']) assert.equal(intelligenceReadiness({...source, lastSyncedAt: timestamp}, now)[0].ready, false);
 assert.equal(intelligenceReadiness({...source, syncError: 'failed'}, now)[0].ready, false);
});
test('small samples and unmatched products cannot appear ready', () => {
 const checks = intelligenceReadiness({...source, summary: {completeDays: 13, orders: 29, sufficientForVelocity: false, topProducts: []}}, now);
 assert.ok(checks.slice(1).every(check => !check.ready));
});
