import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import type { JobEvent, AccountStatus } from "../api/types";

// ── Fake WS factory ──────────────────────────────────────────────────────────

interface FakeHandle {
  close(): void;
  emitEvent(event: JobEvent): void;
  emitClose(): void;
}

type WsFactory = (
  jobId: string,
  onEvent: (event: JobEvent) => void,
  onClose: () => void
) => { close(): void };

function makeFakeWsFactory(): { factory: WsFactory; lastHandle: () => FakeHandle } {
  let currentHandle: FakeHandle | null = null;

  const factory: WsFactory = (
    _jobId: string,
    onEvent: (event: JobEvent) => void,
    onClose: () => void
  ) => {
    let isClosed = false;
    currentHandle = {
      close() {
        isClosed = true;
      },
      emitEvent(event: JobEvent) {
        onEvent(event);
      },
      emitClose() {
        onClose();
      }
    };
    return {
      close() {
        isClosed = true;
        currentHandle = null;
      }
    };
  };

  return {
    factory,
    lastHandle: () => {
      if (!currentHandle) throw new Error("No active fake WS handle");
      return currentHandle;
    }
  };
}

// ── Import hook (after factory is ready) ────────────────────────────────────

const { useJobStream } = await import("./useJobStream");

// ── Helpers ──────────────────────────────────────────────────────────────────

const BASE_EVENT = { jobId: "job-1", timestamp: "2026-01-01T00:00:00Z" } as const;

function makeRunStarted(total: number): JobEvent {
  return { ...BASE_EVENT, type: "RUN_STARTED", total };
}

function makeAccountStarted(email: string, index: number): JobEvent {
  return { ...BASE_EVENT, type: "ACCOUNT_STARTED", email, index };
}

function makeStepStarted(email: string, step: string): JobEvent {
  return { ...BASE_EVENT, type: "STEP_STARTED", email, step };
}

function makeStepCompleted(email: string, step: string, durationMs: number): JobEvent {
  return { ...BASE_EVENT, type: "STEP_COMPLETED", email, step, durationMs };
}

function makeStepFailed(email: string, step: string): JobEvent {
  return { ...BASE_EVENT, type: "STEP_FAILED", email, step, error: "err" };
}

function makeAwaitManual(email: string, timeoutSec: number): JobEvent {
  return { ...BASE_EVENT, type: "AWAIT_MANUAL_LOGIN", email, timeoutSec };
}

function makeAccountCompleted(email: string, status: AccountStatus): JobEvent {
  return { ...BASE_EVENT, type: "ACCOUNT_COMPLETED", email, status };
}

function makeRunCompleted(): JobEvent {
  return {
    ...BASE_EVENT,
    type: "RUN_COMPLETED",
    summary: { ok: 2, authFailed: 1, requiresManual: 0, failed: 0, skipped: 0 }
  };
}

function makeRunStopped(): JobEvent {
  return { ...BASE_EVENT, type: "RUN_STOPPED" };
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("useJobStream", () => {
  let fakeFactory: ReturnType<typeof makeFakeWsFactory>;

  beforeEach(() => {
    fakeFactory = makeFakeWsFactory();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("starts in idle state when no jobId is given", () => {
    const { result } = renderHook(() => useJobStream(undefined, fakeFactory.factory));
    expect(result.current.connectionState).toBe("idle");
    expect(result.current.events).toHaveLength(0);
    expect(result.current.awaitingManual).toBeNull();
  });

  it("moves to connecting state when jobId is provided", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    expect(result.current.connectionState).toBe("connecting");
  });

  it("RUN_STARTED sets summary.total and moves connectionState to open", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => { fakeFactory.lastHandle().emitEvent(makeRunStarted(5)); });
    expect(result.current.connectionState).toBe("open");
    expect(result.current.summary.total).toBe(5);
  });

  it("ACCOUNT_STARTED creates byEmail entry with RUNNING status", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => { fakeFactory.lastHandle().emitEvent(makeAccountStarted("alice@x.com", 0)); });
    const account = result.current.byEmail["alice@x.com"];
    expect(account).toBeDefined();
    expect(account.status).toBe("RUNNING");
    expect(account.index).toBe(0);
    expect(account.startedAt).toBeGreaterThan(0);
  });

  it("STEP_STARTED updates currentStep for the account", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("alice@x.com", 0));
      fakeFactory.lastHandle().emitEvent(makeStepStarted("alice@x.com", "LOGIN"));
    });
    expect(result.current.byEmail["alice@x.com"].currentStep).toBe("LOGIN");
  });

  it("STEP_COMPLETED accumulates elapsedMs for the account", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("alice@x.com", 0));
      fakeFactory.lastHandle().emitEvent(makeStepCompleted("alice@x.com", "LOGIN", 200));
      fakeFactory.lastHandle().emitEvent(makeStepCompleted("alice@x.com", "SEARCH", 300));
    });
    expect(result.current.byEmail["alice@x.com"].elapsedMs).toBe(500);
  });

  it("STEP_FAILED does not change currentStep", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("alice@x.com", 0));
      fakeFactory.lastHandle().emitEvent(makeStepStarted("alice@x.com", "LOGIN"));
      fakeFactory.lastHandle().emitEvent(makeStepFailed("alice@x.com", "LOGIN"));
    });
    expect(result.current.byEmail["alice@x.com"].currentStep).toBe("LOGIN");
  });

  it("AWAIT_MANUAL_LOGIN sets awaitingManual with correct deadline", () => {
    const now = Date.now();
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("alice@x.com", 0));
      fakeFactory.lastHandle().emitEvent(makeAwaitManual("alice@x.com", 60));
    });
    expect(result.current.awaitingManual).not.toBeNull();
    expect(result.current.awaitingManual!.email).toBe("alice@x.com");
    const expectedDeadline = now + 60 * 1000;
    expect(result.current.awaitingManual!.deadline).toBeGreaterThanOrEqual(expectedDeadline - 100);
    expect(result.current.awaitingManual!.deadline).toBeLessThanOrEqual(expectedDeadline + 100);
  });

  it("ACCOUNT_COMPLETED sets account status and increments summary.ok counter", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeRunStarted(3));
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("alice@x.com", 0));
      fakeFactory.lastHandle().emitEvent(makeAccountCompleted("alice@x.com", "OK"));
    });
    expect(result.current.byEmail["alice@x.com"].status).toBe("OK");
    expect(result.current.summary.ok).toBe(1);
  });

  it("ACCOUNT_COMPLETED clears awaitingManual when it matches the email", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("alice@x.com", 0));
      fakeFactory.lastHandle().emitEvent(makeAwaitManual("alice@x.com", 60));
    });
    expect(result.current.awaitingManual).not.toBeNull();
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeAccountCompleted("alice@x.com", "OK"));
    });
    expect(result.current.awaitingManual).toBeNull();
  });

  it("ACCOUNT_COMPLETED increments authFailed counter for AUTH_FAILED status", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeRunStarted(2));
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("bob@x.com", 0));
      fakeFactory.lastHandle().emitEvent(makeAccountCompleted("bob@x.com", "AUTH_FAILED"));
    });
    expect(result.current.summary.authFailed).toBe(1);
  });

  it("ACCOUNT_COMPLETED increments requiresManual counter for REQUIRES_MANUAL status", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("carol@x.com", 0));
      fakeFactory.lastHandle().emitEvent(makeAccountCompleted("carol@x.com", "REQUIRES_MANUAL"));
    });
    expect(result.current.summary.requiresManual).toBe(1);
  });

  it("ACCOUNT_COMPLETED increments failed counter for FAILED status", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("dave@x.com", 0));
      fakeFactory.lastHandle().emitEvent(makeAccountCompleted("dave@x.com", "FAILED"));
    });
    expect(result.current.summary.failed).toBe(1);
  });

  it("ACCOUNT_COMPLETED increments skipped counter for SKIPPED status", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("eve@x.com", 0));
      fakeFactory.lastHandle().emitEvent(makeAccountCompleted("eve@x.com", "SKIPPED"));
    });
    expect(result.current.summary.skipped).toBe(1);
  });

  it("RUN_COMPLETED sets connectionState to closed and overwrites summary", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeRunStarted(3));
      fakeFactory.lastHandle().emitEvent(makeRunCompleted());
    });
    expect(result.current.connectionState).toBe("closed");
    expect(result.current.summary.ok).toBe(2);
    expect(result.current.summary.authFailed).toBe(1);
  });

  it("RUN_STOPPED sets connectionState to closed", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeRunStopped());
    });
    expect(result.current.connectionState).toBe("closed");
  });

  it("onClose callback sets connectionState to closed", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => { fakeFactory.lastHandle().emitClose(); });
    expect(result.current.connectionState).toBe("closed");
  });

  it("byEmail returns all accounts as rows via Object.values", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("a@x.com", 0));
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("b@x.com", 1));
    });
    expect(Object.keys(result.current.byEmail)).toHaveLength(2);
  });

  it("events array grows with each emitted event", () => {
    const { result } = renderHook(() => useJobStream("job-1", fakeFactory.factory));
    act(() => {
      fakeFactory.lastHandle().emitEvent(makeRunStarted(1));
      fakeFactory.lastHandle().emitEvent(makeAccountStarted("a@x.com", 0));
    });
    expect(result.current.events).toHaveLength(2);
  });
});
