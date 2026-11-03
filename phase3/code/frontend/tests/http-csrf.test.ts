import assert from 'node:assert/strict';
import { afterEach, beforeEach, test } from 'node:test';
import { fetchWithCsrf, invalidateCsrf } from '../src/api/http';

const originalFetch = globalThis.fetch;
beforeEach(() => invalidateCsrf());
afterEach(() => {
  globalThis.fetch = originalFetch;
  invalidateCsrf();
});

function json(status: number, code = 'OK', data: unknown = {}) {
  return Response.json(
    { code, data, message: code, serverTime: new Date().toISOString() },
    { status },
  );
}

test('concurrent first writes share one CSRF fetch and cookie token', async () => {
  let reads = 0;
  const tokens: string[] = [];
  globalThis.fetch = async (url, init) => {
    if (url === '/api/auth/csrf') {
      reads++;
      assert.equal(init?.cache, 'no-store');
      assert.equal(init?.credentials, 'same-origin');
      await Promise.resolve();
      return json(200, 'OK', { token: 'shared-token' });
    }
    tokens.push(new Headers(init?.headers).get('X-CSRF-TOKEN')!);
    return json(200);
  };
  await Promise.all([
    fetchWithCsrf('/me/registrations', { method: 'POST' }),
    fetchWithCsrf('/me/messages/read', { method: 'POST' }),
  ]);
  assert.equal(reads, 1);
  assert.deepEqual(tokens, ['shared-token', 'shared-token']);
});

test('stale cookie refreshes once and reuses the exact original body and operation key', async () => {
  let serverToken = 'first';
  let reads = 0;
  const writes: RequestInit[] = [];
  globalThis.fetch = async (url, init) => {
    if (url === '/api/auth/csrf') {
      reads++;
      return json(200, 'OK', { token: serverToken });
    }
    writes.push(init!);
    return new Headers(init?.headers).get('X-CSRF-TOKEN') === serverToken
      ? json(200)
      : json(403, 'CSRF_INVALID');
  };
  await fetchWithCsrf('/me/registrations', { method: 'POST', body: '{}' });
  serverToken = 'changed-by-another-tab';
  const body = JSON.stringify({ requestId: 'stable-registration-request' });
  const response = await fetchWithCsrf('/me/registrations', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': 'stable-operation-key' },
  });
  assert.equal(response.status, 200);
  assert.equal(reads, 2);
  assert.equal(writes.length, 3);
  for (const write of writes.slice(1)) {
    assert.equal(write.body, body);
    assert.equal(new Headers(write.headers).get('Idempotency-Key'), 'stable-operation-key');
  }
});

test('persistent CSRF rejection stops after one retry', async () => {
  let reads = 0,
    writes = 0;
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf') return json(200, 'OK', { token: `token-${++reads}` });
    writes++;
    return json(403, 'CSRF_INVALID');
  };
  const response = await fetchWithCsrf('/auth/login', { method: 'POST' });
  assert.equal(response.status, 403);
  assert.equal(reads, 2);
  assert.equal(writes, 2);
});

test('credential errors, authorization failures and server failures never replay a write', async () => {
  for (const [status, code] of [
    [401, 'INVALID_CREDENTIALS'],
    [403, 'FORBIDDEN'],
    [500, 'INTERNAL_ERROR'],
  ] as const) {
    invalidateCsrf();
    let writes = 0;
    globalThis.fetch = async (url) => {
      if (url === '/api/auth/csrf') return json(200, 'OK', { token: 'token' });
      writes++;
      return json(status, code);
    };
    assert.equal((await fetchWithCsrf('/auth/login', { method: 'POST' })).status, status);
    assert.equal(writes, 1);
  }
});

test('successful login and logout invalidate the cached token', async () => {
  let reads = 0;
  globalThis.fetch = async (url) =>
    url === '/api/auth/csrf' ? json(200, 'OK', { token: `token-${++reads}` }) : json(200);
  await fetchWithCsrf('/auth/login', { method: 'POST' });
  await fetchWithCsrf('/auth/logout', { method: 'POST' });
  await fetchWithCsrf('/auth/login', { method: 'POST' });
  assert.equal(reads, 3);
});

test('poster retry preserves FormData and idempotency key without a manual multipart boundary', async () => {
  let writes = 0;
  const form = new FormData();
  form.set('file', new Blob(['test'], { type: 'image/png' }), 'test.png');
  globalThis.fetch = async (url, init) => {
    if (url === '/api/auth/csrf') return json(200, 'OK', { token: 'token' });
    assert.equal(init?.body, form);
    const headers = new Headers(init?.headers);
    assert.equal(headers.get('Idempotency-Key'), 'same-upload-key');
    assert.equal(headers.has('Content-Type'), false);
    return ++writes === 1 ? json(403, 'CSRF_INVALID') : json(200);
  };
  assert.equal(
    (
      await fetchWithCsrf('/organizer/events/event/poster', {
        method: 'PUT',
        body: form,
        headers: { 'Idempotency-Key': 'same-upload-key' },
      })
    ).status,
    200,
  );
  assert.equal(writes, 2);
});

test('failed token refresh preserves its service error and does not submit again', async () => {
  let reads = 0,
    writes = 0;
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf')
      return ++reads === 1
        ? json(200, 'OK', { token: 'token' })
        : json(503, 'DATABASE_UNAVAILABLE');
    writes++;
    return json(403, 'CSRF_INVALID');
  };
  await assert.rejects(fetchWithCsrf('/auth/login', { method: 'POST' }), {
    code: 'DATABASE_UNAVAILABLE',
  });
  assert.equal(writes, 1);
});

test('uncertain network failures never replay a business request', async () => {
  let writes = 0;
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf') return json(200, 'OK', { token: 'token' });
    writes++;
    throw new TypeError('connection reset');
  };
  await assert.rejects(fetchWithCsrf('/me/registrations', { method: 'POST' }), {
    code: 'NETWORK_ERROR',
  });
  assert.equal(writes, 1);
});
