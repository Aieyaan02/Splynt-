import { test } from 'node:test';
import assert from 'node:assert/strict';
import { confirmedMutation } from '../src/lib/confirmedMutation.ts';

test('a failed refresh retries the read without recording a second movement', async () => {
    const operation = confirmedMutation();
    let writes = 0, reads = 0;
    const write = async () => { writes++; };
    const read = async () => { if (++reads === 1) throw new Error('refresh offline'); };
    await assert.rejects(operation.run(write, read), /refresh offline/);
    assert.equal(operation.saved, true);
    await operation.run(write, read);
    assert.equal(writes, 1); assert.equal(reads, 2);
});
test('an unconfirmed write failure allows another attempt and does not refresh', async () => {
    const operation = confirmedMutation();
    let reads = 0;
    await assert.rejects(operation.run(async () => { throw new Error('write rejected'); }, () => { reads++; }));
    assert.equal(operation.saved, false); assert.equal(reads, 0);
    await operation.run(async () => {}, () => { reads++; });
    assert.equal(operation.saved, true); assert.equal(reads, 1);
});
test('concurrent submissions share one in-flight write and refresh', async () => {
    const operation = confirmedMutation();
    let release, writes = 0, reads = 0;
    const gate = new Promise(resolve => { release = resolve; });
    const write = async () => { writes++; await gate; };
    const read = async () => { reads++; };
    const first = operation.run(write, read);
    const second = operation.run(write, read);
    release();
    await Promise.all([first, second]);
    assert.equal(writes, 1); assert.equal(reads, 1);
});
