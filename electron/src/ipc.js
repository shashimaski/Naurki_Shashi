'use strict';

/**
 * parsePortLine - extracts the port number from a NAUKRI_BE_PORT=<n> line.
 * @param {string} text - multi-line stdout text
 * @returns {number|null} port integer, or null if not found
 */
function parsePortLine(text) {
  if (typeof text !== 'string') return null;
  const match = text.match(/NAUKRI_BE_PORT=(\d+)/);
  if (!match) return null;
  return parseInt(match[1], 10);
}

/**
 * waitForPort - resolves with the BE port number once the child process emits
 * a NAUKRI_BE_PORT=<n> line on stdout; rejects if timeoutMs elapses first.
 *
 * @param {import('child_process').ChildProcess} child
 * @param {number} timeoutMs
 * @returns {Promise<number>}
 */
function waitForPort(child, timeoutMs) {
  return new Promise((resolve, reject) => {
    let buffer = '';

    const timer = setTimeout(() => {
      cleanup();
      reject(new Error(`Timed out after ${timeoutMs}ms waiting for NAUKRI_BE_PORT`));
    }, timeoutMs);

    function onData(chunk) {
      buffer += chunk.toString();
      const port = parsePortLine(buffer);
      if (port !== null) {
        cleanup();
        resolve(port);
      }
    }

    function onClose() {
      cleanup();
      reject(new Error('Child process closed before emitting NAUKRI_BE_PORT'));
    }

    function cleanup() {
      clearTimeout(timer);
      if (child.stdout) {
        child.stdout.removeListener('data', onData);
        child.stdout.removeListener('close', onClose);
      }
    }

    if (child.stdout) {
      child.stdout.on('data', onData);
      child.stdout.on('close', onClose);
    } else {
      clearTimeout(timer);
      reject(new Error('Child process has no stdout stream'));
    }
  });
}

module.exports = { parsePortLine, waitForPort };
