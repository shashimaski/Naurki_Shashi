import { describe, it, expect, vi, beforeEach } from "vitest";
import type { JobEvent } from "./types";

// ---- Inline MockWebSocket ----
type WsListener = (event: { data?: string } | Event) => void;

interface MockWsInstance {
  url: string;
  listeners: Map<string, WsListener[]>;
  closed: boolean;
  addEventListener(type: string, listener: WsListener): void;
  close(): void;
  // test helpers
  _emit(type: string, payload?: unknown): void;
}

let lastMockWs: MockWsInstance | null = null;

class MockWebSocket implements MockWsInstance {
  url: string;
  listeners = new Map<string, WsListener[]>();
  closed = false;

  constructor(url: string) {
    this.url = url;
    lastMockWs = this;
  }

  addEventListener(type: string, listener: WsListener) {
    if (!this.listeners.has(type)) this.listeners.set(type, []);
    this.listeners.get(type)!.push(listener);
  }

  close() {
    this.closed = true;
  }

  _emit(type: string, payload?: unknown) {
    const handlers = this.listeners.get(type) ?? [];
    for (const h of handlers) {
      if (type === "message") {
        h({ data: typeof payload === "string" ? payload : JSON.stringify(payload) } as { data: string });
      } else {
        h(new Event(type));
      }
    }
  }
}

// Inject mock before importing ws module
vi.stubGlobal("WebSocket", MockWebSocket);

// Import AFTER stubbing global
const { connectJobStream } = await import("./ws");

beforeEach(() => {
  lastMockWs = null;
});

describe("WebSocket client", () => {
  it("connects to the correct URL using default port 5000", () => {
    const onEvent = vi.fn();
    const onClose = vi.fn();
    connectJobStream("job-abc", onEvent, onClose);
    expect(lastMockWs).not.toBeNull();
    expect(lastMockWs!.url).toBe("ws://127.0.0.1:5000/ws/jobs/job-abc");
  });

  it("parses incoming JSON frame and calls onEvent", () => {
    const onEvent = vi.fn<[JobEvent], void>();
    const onClose = vi.fn();
    connectJobStream("job-123", onEvent, onClose);

    const event: JobEvent = {
      type: "RUN_STARTED",
      jobId: "job-123",
      timestamp: "2026-01-01T00:00:00Z",
      total: 5
    };
    lastMockWs!._emit("message", event);

    expect(onEvent).toHaveBeenCalledOnce();
    expect(onEvent).toHaveBeenCalledWith(event);
  });

  it("calls onClose when socket closes", () => {
    const onEvent = vi.fn();
    const onClose = vi.fn();
    connectJobStream("job-456", onEvent, onClose);

    lastMockWs!._emit("close");
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("calls onClose on socket error", () => {
    const onEvent = vi.fn();
    const onClose = vi.fn();
    connectJobStream("job-789", onEvent, onClose);

    lastMockWs!._emit("error");
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("handle.close() closes the underlying socket", () => {
    const onEvent = vi.fn();
    const onClose = vi.fn();
    const handle = connectJobStream("job-close", onEvent, onClose);

    handle.close();
    expect(lastMockWs!.closed).toBe(true);
  });

  it("ignores malformed (non-JSON) frames without throwing", () => {
    const onEvent = vi.fn();
    const onClose = vi.fn();
    connectJobStream("job-bad", onEvent, onClose);

    expect(() => {
      lastMockWs!._emit("message", "not valid json }{{{");
    }).not.toThrow();
    expect(onEvent).not.toHaveBeenCalled();
  });

  it("decodes ACCOUNT_COMPLETED event with optional fields", () => {
    const onEvent = vi.fn<[JobEvent], void>();
    const onClose = vi.fn();
    connectJobStream("job-acc", onEvent, onClose);

    const event: JobEvent = {
      type: "ACCOUNT_COMPLETED",
      jobId: "job-acc",
      timestamp: "2026-01-01T00:00:06Z",
      email: "alice@example.com",
      status: "OK",
      resumeOldName: "resume_old.pdf",
      resumeNewName: "resume_new.pdf"
    };
    lastMockWs!._emit("message", event);
    expect(onEvent).toHaveBeenCalledWith(event);
  });

  it("handles multiple sequential events", () => {
    const events: JobEvent[] = [];
    const onEvent = (e: JobEvent) => events.push(e);
    const onClose = vi.fn();
    connectJobStream("job-multi", onEvent, onClose);

    const e1: JobEvent = { type: "RUN_STARTED", jobId: "job-multi", timestamp: "t1", total: 2 };
    const e2: JobEvent = { type: "ACCOUNT_STARTED", jobId: "job-multi", timestamp: "t2", email: "a@x.com", index: 0 };
    const e3: JobEvent = { type: "RUN_STOPPED", jobId: "job-multi", timestamp: "t3" };

    lastMockWs!._emit("message", e1);
    lastMockWs!._emit("message", e2);
    lastMockWs!._emit("message", e3);

    expect(events).toHaveLength(3);
    expect(events[0].type).toBe("RUN_STARTED");
    expect(events[1].type).toBe("ACCOUNT_STARTED");
    expect(events[2].type).toBe("RUN_STOPPED");
  });
});
