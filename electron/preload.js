'use strict';

const { contextBridge, ipcRenderer } = require('electron');

// Read the backend port injected as a query-string parameter by main.js
const urlParams = new URLSearchParams(window.location.search);
const port = parseInt(urlParams.get('port') || '0', 10);
const e2eMock = urlParams.get('e2eMock') || null;

// Expose the port + optional E2E mock URL to the renderer via contextBridge.
// Direct `window.X = Y` here would only set X in the preload's isolated world;
// contextBridge.exposeInMainWorld makes them visible to the renderer.
contextBridge.exposeInMainWorld('NAUKRI_BE_PORT', port);
if (e2eMock) {
  contextBridge.exposeInMainWorld('__E2E_MOCK__', e2eMock);
}

contextBridge.exposeInMainWorld('electronAPI', {
  /** Opens a native folder-picker.  Returns the selected path or null. */
  pickFolder: (defaultPath) => ipcRenderer.invoke('pickFolder', defaultPath),

  /** Reveals a folder in the OS file explorer. */
  openFolder: (folderPath) => ipcRenderer.invoke('openFolder', folderPath),

  /** Returns the backend port number (set by main.js via ?port= query param). */
  portInfo: () => port,
});
