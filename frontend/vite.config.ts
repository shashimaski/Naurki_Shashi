/// <reference types="vitest" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

export default defineConfig({
  // Relative asset paths — required for Electron loading index.html via file://.
  // Without this, Vite emits <script src="/assets/..."> which resolves to the
  // filesystem root under file:// and 404s, leaving the renderer blank.
  base: "./",
  plugins: [react()],
  resolve: { alias: { "@": path.resolve(__dirname, "src") } },
  define: {
    __BUILD_TS__: JSON.stringify(new Date().toISOString()),
  },
  // Dev-server proxy — lets you test the FE in a plain browser at
  // http://127.0.0.1:5173 while the Java BE runs standalone on port 8080.
  // No Electron, no packaging, no install/uninstall loop.
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true
      },
      "/ws": {
        target: "ws://127.0.0.1:8080",
        ws: true,
        changeOrigin: true
      }
    }
  },
  test: { environment: "jsdom", setupFiles: ["./src/test/setup.ts"], globals: true }
});
