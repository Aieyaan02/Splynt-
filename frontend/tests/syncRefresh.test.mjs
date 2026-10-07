import { test } from 'node:test';
import assert from 'node:assert/strict';
import { syncRefresh } from '../src/lib/syncRefresh.ts';

test('failed dashboard reads retry the same completed sync timestamp', async () => {
    const snapshot = syncRefresh();
    let reads = 0;
    const refresh = async () => { if (++reads === 1) throw new Error('offline'); };
    await assert.rejects(snapshot.run('sync-1', refresh));
    await snapshot.run('sync-1', refresh);
    await snapshot.run('sync-1', refresh);
    assert.equal(reads, 2);
    await snapshot.run('sync-2', refresh);
    assert.equal(reads, 3);
});

test('overlapping checks share the read and later revisit a newer snapshot', async () => {
    const snapshot = syncRefresh();
    let resolve, reads = 0;
    const gate = new Promise(done => { resolve = done; });
    const refresh = async () => { reads++; await gate; };
    const first = snapshot.run('sync-1', refresh);
    const next = snapshot.run('sync-2', refresh);
    resolve();
    await Promise.all([first, next]);
    assert.equal(reads, 1);
    await snapshot.run('sync-2', refresh);
    assert.equal(reads, 2);
});

test('no completed provider import does not claim a refreshed snapshot', async () => {
    let reads = 0;
    await syncRefresh().run(null, async () => { reads++; });
    assert.equal(reads, 0);
});
