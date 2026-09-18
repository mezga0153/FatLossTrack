import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';

// The server is started as a child process with a deliberately invalid OpenAI
// key. That is the trick this suite turns on: if the auth hook ever stops
// halting the request, the route handler runs, calls OpenAI, and the key is
// rejected — which shows up in the server's own log. Asserting on the status
// code alone would not catch it, because the 401 the hook queues is sent either
// way.
const PORT = 3997;
const BASE = `http://127.0.0.1:${PORT}`;
const OPENAI_REACHED = /Incorrect API key|invalid_api_key/;

let server;
let log = '';

before(async () => {
  server = spawn('node', ['src/server.js'], {
    env: {
      ...process.env,
      OPENAI_API_KEY: 'sk-deliberately-invalid',
      FIREBASE_PROJECT_ID: 'test-project',
      PORT: String(PORT),
      HOST: '127.0.0.1',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  server.stdout.on('data', (c) => { log += c; });
  server.stderr.on('data', (c) => { log += c; });

  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`${BASE}/api/health`);
      if (res.ok) return;
    } catch {
      // not listening yet
    }
    await new Promise((r) => setTimeout(r, 200));
  }
  throw new Error(`server did not start:\n${log}`);
});

after(() => server?.kill());

test('health check is reachable without auth', async () => {
  const res = await fetch(`${BASE}/api/health`);
  assert.equal(res.status, 200);
  assert.equal((await res.json()).status, 'ok');
});

test('request with no Authorization header is rejected before the handler runs', async () => {
  log = '';
  const res = await fetch(`${BASE}/api/ai/chat`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ prompt: 'hi' }),
  });

  assert.equal(res.status, 401);
  assert.equal((await res.json()).error, 'unauthorized');

  await new Promise((r) => setTimeout(r, 500));
  assert.doesNotMatch(log, OPENAI_REACHED, 'handler ran despite the 401 — auth hook did not halt the request');
});

test('request with an unverifiable Bearer token is rejected before the handler runs', async () => {
  log = '';
  const res = await fetch(`${BASE}/api/ai/chat`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: 'Bearer not-a-real-token' },
    body: JSON.stringify({ prompt: 'hi' }),
  });

  assert.equal(res.status, 401);

  await new Promise((r) => setTimeout(r, 500));
  assert.doesNotMatch(log, OPENAI_REACHED, 'handler ran despite the 401 — auth hook did not halt the request');
});
