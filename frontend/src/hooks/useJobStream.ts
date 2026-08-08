import { useEffect, useReducer, useRef } from "react";
import type { JobEvent, AccountStatus } from "../api/types";
import { connectJobStream } from "../api/ws";

// ── Types ─────────────────────────────────────────────────────────────────────

export type AccountView = {
  email: string;
  index: number;
  currentStep: string;
  status: AccountStatus | "RUNNING" | "PENDING";
  elapsedMs: number;
  startedAt: number;
};

export type RunSummary = {
  ok: number;
  authFailed: number;
  requiresManual: number;
  failed: number;
  skipped: number;
  total: number;
};

export type ConnectionState = "idle" | "connecting" | "open" | "closed";

export type JobStreamState = {
  events: JobEvent[];
  byEmail: Record<string, AccountView>;
  summary: RunSummary;
  awaitingManual: { email: string; deadline: number } | null;
  connectionState: ConnectionState;
};

type Action =
  | { type: "CONNECTING" }
  | { type: "WS_CLOSED" }
  | { type: "EVENT"; payload: JobEvent };

// ── Initial state ─────────────────────────────────────────────────────────────

const initialSummary: RunSummary = {
  ok: 0,
  authFailed: 0,
  requiresManual: 0,
  failed: 0,
  skipped: 0,
  total: 0
};

const initialState: JobStreamState = {
  events: [],
  byEmail: {},
  summary: { ...initialSummary },
  awaitingManual: null,
  connectionState: "idle"
};

// ── Summary counter helper ────────────────────────────────────────────────────

function incrementSummaryForStatus(
  summary: RunSummary,
  status: AccountStatus
): RunSummary {
  const STATUS_COUNTER_MAP: Partial<Record<AccountStatus, keyof RunSummary>> = {
    OK: "ok",
    AUTH_FAILED: "authFailed",
    REQUIRES_MANUAL: "requiresManual",
    FAILED: "failed",
    SKIPPED: "skipped"
  };
  const counterKey = STATUS_COUNTER_MAP[status];
  if (!counterKey) return summary;
  return { ...summary, [counterKey]: (summary[counterKey] as number) + 1 };
}

// ── Reducer ───────────────────────────────────────────────────────────────────

function reducer(state: JobStreamState, action: Action): JobStreamState {
  if (action.type === "CONNECTING") {
    return { ...state, connectionState: "connecting" };
  }

  if (action.type === "WS_CLOSED") {
    return { ...state, connectionState: "closed" };
  }

  const event = action.payload;
  const nextEvents = [...state.events, event];

  switch (event.type) {
    case "RUN_STARTED":
      return {
        ...state,
        events: nextEvents,
        connectionState: "open",
        summary: { ...state.summary, total: event.total }
      };

    case "ACCOUNT_STARTED":
      return {
        ...state,
        events: nextEvents,
        byEmail: {
          ...state.byEmail,
          [event.email]: {
            email: event.email,
            index: event.index,
            currentStep: "",
            status: "RUNNING",
            elapsedMs: 0,
            startedAt: Date.now()
          }
        }
      };

    case "STEP_STARTED": {
      const existing = state.byEmail[event.email];
      if (!existing) return { ...state, events: nextEvents };
      return {
        ...state,
        events: nextEvents,
        byEmail: {
          ...state.byEmail,
          [event.email]: { ...existing, currentStep: event.step }
        }
      };
    }

    case "STEP_COMPLETED": {
      const existing = state.byEmail[event.email];
      if (!existing) return { ...state, events: nextEvents };
      return {
        ...state,
        events: nextEvents,
        byEmail: {
          ...state.byEmail,
          [event.email]: {
            ...existing,
            elapsedMs: existing.elapsedMs + event.durationMs
          }
        }
      };
    }

    case "STEP_FAILED":
      return { ...state, events: nextEvents };

    case "AWAIT_MANUAL_LOGIN":
      return {
        ...state,
        events: nextEvents,
        awaitingManual: {
          email: event.email,
          deadline: Date.now() + event.timeoutSec * 1000
        }
      };

    case "ACCOUNT_COMPLETED": {
      const existing = state.byEmail[event.email];
      const updatedByEmail = existing
        ? {
            ...state.byEmail,
            [event.email]: { ...existing, status: event.status }
          }
        : state.byEmail;

      const isAwaitingThisEmail =
        state.awaitingManual?.email === event.email;

      return {
        ...state,
        events: nextEvents,
        byEmail: updatedByEmail,
        summary: incrementSummaryForStatus(state.summary, event.status),
        awaitingManual: isAwaitingThisEmail ? null : state.awaitingManual
      };
    }

    case "RUN_COMPLETED":
      return {
        ...state,
        events: nextEvents,
        connectionState: "closed",
        summary: { ...state.summary, ...event.summary }
      };

    case "RUN_STOPPED":
      return { ...state, events: nextEvents, connectionState: "closed" };

    default:
      return { ...state, events: nextEvents };
  }
}

// ── WS factory type ───────────────────────────────────────────────────────────

type WsFactory = typeof connectJobStream;

// ── Hook ──────────────────────────────────────────────────────────────────────

export function useJobStream(
  jobId: string | undefined,
  wsFactory: WsFactory = connectJobStream
): JobStreamState {
  const [state, dispatch] = useReducer(reducer, initialState);
  const handleRef = useRef<ReturnType<WsFactory> | null>(null);

  useEffect(() => {
    if (!jobId) return;

    dispatch({ type: "CONNECTING" });

    const handle = wsFactory(
      jobId,
      (event: JobEvent) => dispatch({ type: "EVENT", payload: event }),
      () => dispatch({ type: "WS_CLOSED" })
    );

    handleRef.current = handle;

    return () => {
      handle.close();
      handleRef.current = null;
    };
  }, [jobId, wsFactory]);

  return state;
}
