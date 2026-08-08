'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const PRELOAD_PATH = path.join(__dirname, '..', 'preload.js');

test('preload.js exposes exactly { pickFolder, openFolder, portInfo } via contextBridge AND top-level NAUKRI_BE_PORT', () => {
  const source = fs.readFileSync(PRELOAD_PATH, 'utf8');

  // 1. Must call contextBridge.exposeInMainWorld with "electronAPI"
  assert.ok(
    source.includes('contextBridge.exposeInMainWorld'),
    'preload.js must call contextBridge.exposeInMainWorld'
  );
  assert.ok(
    source.includes('"electronAPI"') || source.includes("'electronAPI'"),
    'the exposed world name must be "electronAPI"'
  );

  // 2. Extract the object literal passed as the second argument.
  //    Look for the three required keys as property names.
  const requiredKeys = ['pickFolder', 'openFolder', 'portInfo'];
  for (const key of requiredKeys) {
    const keyPattern = new RegExp(`\\b${key}\\s*:`);
    assert.ok(
      keyPattern.test(source),
      `electronAPI must expose key: ${key}`
    );
  }

  // 3. Ensure no unexpected top-level keys are present inside electronAPI.
  //    We parse the lines inside the exposeInMainWorld('electronAPI', {...}) block naively:
  //    find lines that look like "  <identifier>:" inside the object literal.
  //    NOTE: The contract has widened — NAUKRI_BE_PORT and optionally __E2E_MOCK__ are
  //    now exposed as separate top-level contextBridge entries (not inside electronAPI),
  //    so we only check the electronAPI object body here.
  const electronAPIStart = source.indexOf("exposeInMainWorld('electronAPI'");
  const electronAPIFallback = source.indexOf('exposeInMainWorld("electronAPI"');
  const exposeStart = electronAPIStart >= 0 ? electronAPIStart : electronAPIFallback;
  assert.ok(exposeStart >= 0, 'Could not find exposeInMainWorld for electronAPI');

  const objectStart = source.indexOf('{', exposeStart);
  // Find matching closing brace (accounting for nested braces from arrow functions)
  let depth = 0;
  let objectEnd = -1;
  for (let i = objectStart; i < source.length; i++) {
    if (source[i] === '{') depth++;
    else if (source[i] === '}') {
      depth--;
      if (depth === 0) { objectEnd = i; break; }
    }
  }
  assert.ok(objectEnd > objectStart, 'Could not locate the closing brace of the electronAPI object');

  const objectBody = source.slice(objectStart + 1, objectEnd);

  // Extract property keys: lines matching /^\s+(\w+)\s*:/  (top-level props only)
  // We only check top-level depth-0 keys within the object body
  const topLevelKeys = [];
  let innerDepth = 0;
  const lines = objectBody.split('\n');
  for (const line of lines) {
    const trimmed = line.trim();
    // Count braces to track nesting
    for (const ch of trimmed) {
      if (ch === '{' || ch === '(') innerDepth++;
      else if (ch === '}' || ch === ')') innerDepth--;
    }
    // A top-level key looks like `identifier:` at depth 0
    if (innerDepth === 0) {
      const m = trimmed.match(/^(\w+)\s*:/);
      if (m) topLevelKeys.push(m[1]);
    }
  }

  const unexpectedKeys = topLevelKeys.filter((k) => !requiredKeys.includes(k));
  assert.deepEqual(
    unexpectedKeys,
    [],
    `electronAPI must NOT expose extra keys beyond ${requiredKeys.join(', ')}; found: ${unexpectedKeys.join(', ')}`
  );

  // 4. Widened contract: NAUKRI_BE_PORT must now be exposed as a separate
  //    top-level contextBridge entry (not as window.NAUKRI_BE_PORT in the
  //    preload's isolated world). Verify the call is present.
  assert.ok(
    source.includes("exposeInMainWorld('NAUKRI_BE_PORT'") ||
    source.includes('exposeInMainWorld("NAUKRI_BE_PORT"'),
    'preload.js must expose NAUKRI_BE_PORT via contextBridge.exposeInMainWorld (not window.NAUKRI_BE_PORT)'
  );

  // 5. window.NAUKRI_BE_PORT direct assignment must NOT be present (would only
  //    set it in the isolated preload world, invisible to the renderer).
  assert.ok(
    !source.includes('window.NAUKRI_BE_PORT'),
    'preload.js must NOT use window.NAUKRI_BE_PORT — use contextBridge instead'
  );
});
