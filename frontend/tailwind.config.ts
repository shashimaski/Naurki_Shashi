import type { Config } from "tailwindcss";
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: { base: "#050915", accent: "#0b1226" },
        text: { primary: "#e6edf3", muted: "#94a3b8" },
        accent: { DEFAULT: "#22d3ee", cyan: "#22d3ee", violet: "#a855f7" },
        status: { ok: "#22c55e", warn: "#f59e0b", fail: "#ef4444", manual: "#a855f7" }
      },
      fontFamily: {
        head: ['"JetBrains Mono"', "ui-monospace", "monospace"],
        body: ["Inter", "system-ui", "sans-serif"]
      },
      borderRadius: { card: "16px" },
      boxShadow: { card: "0 8px 30px rgba(0,0,0,.35)" }
    }
  },
  plugins: []
} satisfies Config;
