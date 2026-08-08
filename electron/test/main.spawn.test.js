'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { parsePortLine, waitForPort } = require('../src/ipc');

// ── parsePortLine ──────────────────────────────────────────────────────────────

test('parsePortLine extracts the integer port from a NAUKRI_BE_PORT line', () => {
  const text = 'Something\nNAUKRI_BE_PORT=54321\nOther';
  const result = parsePortLine(text);
  assert.equal(result, 54321);
});

test('parsePortLine returns null when the marker is absent', () => {
  assert.equal(parsePortLine('no marker here'), null);
});

test('parsePortLine returns null for non-string input', () => {
  assert.equal(parsePortLine(null), null);
  assert.equal(parsePortLine(undefined), null);
});

// ── waitForPort ────────────────────────────────────────────────────────────────

test('waitForPort rejects with a timeout when child.stdout emits nothing', async () => {
  // Build a minimal mock child process whose stdout is a plain EventEmitter
  // (never emits 'data') so the timeout fires.
  const mockStdout = new EventEmitter();

  const mockChild = { stdout: mockStdout };

  await assert.rejects(
    () => waitForPort(mockChild, 200),
    (err) => {
      assert.ok(err instanceof Error, 'should be an Error');
      assert.ok(
        err.message.includes('200') || err.message.toLowerCase().includes('timed out'),
        `unexpected message: ${err.message}`
      );
      return true;
    }
  );
});

test('waitForPort resolves with the port when stdout emits a matching line', async () => {
  const mockStdout = new EventEmitter();
  const mockChild = { stdout: mockStdout };

  // Emit the port line after a short delay
  setImmediate(() => {
    mockStdout.emit('data', Buffer.from('Spring boot started\nNAUKRI_BE_PORT=8080\n'));
  });

  const port = await waitForPort(mockChild, 1000);
  assert.equal(port, 8080);
});
