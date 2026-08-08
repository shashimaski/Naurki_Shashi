'use strict';

const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const { waitForPort } = require('./src/ipc');

let mainWindow = null;
let javaProcess = null;

// Parse --e2e-mock=<url> from argv (used by Playwright E2E tests).
// Example: electron . --e2e-mock=http://127.0.0.1:9000
const e2eMockArg = process.argv.find((a) => a.startsWith('--e2e-mock='));
const e2eMockUrl = e2eMockArg ? e2eMockArg.slice('--e2e-mock='.length) : null;

/**
 * Spawn the backend Java process using the packaged JRE and JAR.
 * In dev, falls back to the local backend/target jar.
 */
function spawnBackend() {
  const resourcesPath = process.resourcesPath || path.join(__dirname, '..');
  const javaExe = path.join(resourcesPath, 'jre', 'bin', 'javaw.exe');
  const jar = path.join(resourcesPath, 'backend', 'naukri-be.jar');

  const child = spawn(javaExe, ['-jar', jar, '--server.port=0'], {
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  child.stderr.on('data', (d) => {
    // Surface stderr to the main-process console for debugging
    process.stderr.write(d);
  });

  child.on('error', (err) => {
    console.error('[electron] Failed to start backend:', err.message);
  });

  return child;
}

async function createWindow(port) {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    backgroundColor: '#050915',
    autoHideMenuBar: true,
    title: 'NaukriAutomator',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
    },
  });

  // Load the renderer.  In production the FE dist is copied into renderer/.
  const indexPath = path.join(__dirname, 'renderer', 'index.html');
  const query = { port: String(port) };
  if (e2eMockUrl) {
    query.e2eMock = e2eMockUrl;
  }
  mainWindow.loadFile(indexPath, { query });

  mainWindow.on('closed', () => {
    mainWindow = null;
    if (javaProcess) {
      javaProcess.kill();
      javaProcess = null;
    }
  });
}

// IPC: pickFolder — opens a native folder-picker dialog
ipcMain.handle('pickFolder', async (_event, defaultPath) => {
  if (!mainWindow) return null;
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openDirectory'],
    defaultPath: defaultPath || undefined,
  });
  if (result.canceled || result.filePaths.length === 0) return null;
  return result.filePaths[0];
});

// IPC: openFolder — reveals a folder in the system file explorer
ipcMain.handle('openFolder', async (_event, folderPath) => {
  if (folderPath) {
    shell.openPath(folderPath);
  }
});

app.whenReady().then(async () => {
  try {
    javaProcess = spawnBackend();
    // Wait up to 60 s for the backend to announce its port
    const port = await waitForPort(javaProcess, 60_000);
    await createWindow(port);
  } catch (err) {
    console.error('[electron] Startup error:', err.message);
    // Still open a window so the user sees something; renderer will handle empty port
    await createWindow(0);
  }
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('will-quit', () => {
  if (javaProcess) {
    javaProcess.kill();
    javaProcess = null;
  }
});
