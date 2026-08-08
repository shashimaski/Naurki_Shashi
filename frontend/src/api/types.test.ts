import { describe, it, expect } from "vitest";
import type { AccountStatus, JobEvent, StartJobRequest, StartJobResponse } from "./types";

describe("types smoke", () => {
  it("AccountStatus values are valid string literals", () => {
    const statuses: AccountStatus[] = ["OK", "AUTH_FAILED", "REQUIRES_MANUAL", "FAILED", "SKIPPED"];
    expect(statuses).toHaveLength(5);
  });

  it("StartJobRequest shape is correct", () => {
    const req: StartJobRequest = {
      emails: ["a@x.com"],
      password: "secret",
      headless: true,
      manualLogin: false,
      outputFolder: "/tmp/out"
    };
    expect(req.emails).toHaveLength(1);
    expect(req.headless).toBe(true);
    expect(req.baseUrlOverride).toBeUndefined();
  });

  it("StartJobResponse has jobId and wsUrl", () => {
    const res: StartJobResponse = { jobId: "abc-123", wsUrl: "ws://127.0.0.1:5000/ws/jobs/abc-123" };
    expect(res.jobId).toBe("abc-123");
  });

  it("JobEvent discriminated union covers all 9 types", () => {
    const events: JobEvent[] = [
      { type: "RUN_STARTED", jobId: "j1", timestamp: "2026-01-01T00:00:00Z", total: 3 },
      { type: "ACCOUNT_STARTED", jobId: "j1", timestamp: "2026-01-01T00:00:01Z", email: "a@x.com", index: 0 },
      { type: "STEP_STARTED", jobId: "j1", timestamp: "2026-01-01T00:00:02Z", email: "a@x.com", step: "LOGIN" },
      { type: "STEP_COMPLETED", jobId: "j1", timestamp: "2026-01-01T00:00:03Z", email: "a@x.com", step: "LOGIN", durationMs: 500 },
      { type: "STEP_FAILED", jobId: "j1", timestamp: "2026-01-01T00:00:04Z", email: "a@x.com", step: "LOGIN", error: "auth error" },
      { type: "AWAIT_MANUAL_LOGIN", jobId: "j1", timestamp: "2026-01-01T00:00:05Z", email: "a@x.com", timeoutSec: 120 },
      { type: "ACCOUNT_COMPLETED", jobId: "j1", timestamp: "2026-01-01T00:00:06Z", email: "a@x.com", status: "OK" },
      { type: "RUN_COMPLETED", jobId: "j1", timestamp: "2026-01-01T00:00:07Z", summary: { ok: 1, authFailed: 0, requiresManual: 0, failed: 0, skipped: 0 } },
      { type: "RUN_STOPPED", jobId: "j1", timestamp: "2026-01-01T00:00:08Z" }
    ];
    expect(events).toHaveLength(9);
    expect(events[0].type).toBe("RUN_STARTED");
    expect(events[8].type).toBe("RUN_STOPPED");
  });
});
