import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { accountApi, authApi, getAccessToken, setAccessToken, SESSION_EXPIRED_EVENT } from '../src/lib/api.ts';

let savedFetch;
beforeEach(() => {
    savedFetch = globalThis.fetch;
    const values = new Map();
    globalThis.sessionStorage = { getItem: key => values.get(key) ?? null,
        setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
    globalThis.window = new EventTarget();
});
afterEach(() => { globalThis.fetch = savedFetch; delete globalThis.sessionStorage; delete globalThis.window; });

const unauthorized = () => new Response(JSON.stringify({ message: 'Unauthorized' }), {
    status: 401, headers: { 'content-type': 'application/json' }
});

test('authenticated 401 ends the session and notifies the app', async () => {
    setAccessToken('expired');
    let events = 0;
    window.addEventListener(SESSION_EXPIRED_EVENT, () => events++);
    globalThis.fetch = async () => unauthorized();
    await assert.rejects(accountApi.getCurrent(), error => error.status === 401);
    assert.equal(getAccessToken(), null);
    assert.equal(events, 1);
});

test('a late 401 cannot erase a newer login', async () => {
    setAccessToken('old');
    let respond, events = 0;
    window.addEventListener(SESSION_EXPIRED_EVENT, () => events++);
    globalThis.fetch = () => new Promise(resolve => { respond = resolve; });
    const pending = accountApi.getCurrent();
    setAccessToken('new');
    respond(unauthorized());
    await assert.rejects(pending);
    assert.equal(getAccessToken(), 'new');
    assert.equal(events, 0);
});

test('network failure and service failure preserve the session', async () => {
    setAccessToken('valid');
    globalThis.fetch = async () => { throw new TypeError('offline'); };
    await assert.rejects(accountApi.getCurrent());
    assert.equal(getAccessToken(), 'valid');
    globalThis.fetch = async () => new Response('Unavailable', { status: 503 });
    await assert.rejects(accountApi.getCurrent(), error => error.status === 503);
    assert.equal(getAccessToken(), 'valid');
});

test('invalid login credentials do not expire an existing session', async () => {
    setAccessToken('valid');
    globalThis.fetch = async () => unauthorized();
    await assert.rejects(authApi.login({ email: 'test@example.com', password: 'incorrect' }));
    assert.equal(getAccessToken(), 'valid');
});

test('malformed unauthorized response still reports 401 and expires once', async () => {
    setAccessToken('expired');
    let events = 0;
    window.addEventListener(SESSION_EXPIRED_EVENT, () => events++);
    globalThis.fetch = async () => new Response('{', { status: 401, headers: { 'content-type': 'application/json' } });
    await assert.rejects(accountApi.getCurrent(), error => error.status === 401);
    assert.equal(getAccessToken(), null);
    assert.equal(events, 1);
});
