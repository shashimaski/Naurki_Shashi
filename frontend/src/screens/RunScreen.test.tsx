import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { AccountView, JobStreamState } from "../hooks/useJobStream";
import type { RunSummary } from "../screens/RunScreen";

// ── Mock useJobStream ─────────────────────────────────────────────────────────

const mockUseJobStream = vi.fn<[string | undefined], JobStreamState>();

vi.mock("../hooks/useJobStream", () => ({
  useJobStream: (jobId: string | undefined) => mockUseJobStream(jobId)
}));

// ── Mock stopJob ──────────────────────────────────────────────────────────────

const mockStopJob = vi.fn<[string], Promise<void>>().mockResolvedValue(undefined);

vi.mock("../api/rest", () => ({
  stopJob: (id: string) => mockStopJob(id),
  startJob: vi.fn(),
  continueJob: vi.fn(),
  skipJob: vi.fn(),
  downloadTemplate: vi.fn(),
  parseExcel: vi.fn()
}));

// ── Import component AFTER mocks ──────────────────────────────────────────────

const { RunScreen } = await import("../screens/RunScreen");

// ── Helpers ──────────────────────────────────────────────────────────────────

const emptyAccount: AccountView = {
  email: "test@x.com",
  index: 0,
  currentStep: "",
  status: "PENDING",
  elapsedMs: 0,
  startedAt: 0
};

const emptySummary: RunSummary = {
  ok: 0,
  authFailed: 0,
  requiresManual: 0,
  failed: 0,
  skipped: 0
};

function makeIdleState(): JobStreamState {
  return {
    events: [],
    byEmail: {},
    summary: { ...emptySummary, total: 3 },
    awaitingManual: null,
    connectionState: "connecting"
  };
}

function makeRunningState(rows: AccountView[]): JobStreamState {
  const byEmail: Record<string, AccountView> = {};
  rows.forEach(row => { byEmail[row.email] = row; });
  return {
    events: [],
    byEmail,
    summary: { ...emptySummary, total: rows.length },
    awaitingManual: null,
    connectionState: "open"
  };
}

function makeAwaitingState(email: string): JobStreamState {
  return {
    events: [],
    byEmail: { [email]: { ...emptyAccount, email, status: "RUNNING" } },
    summary: { ...emptySummary, total: 1 },
    awaitingManual: { email, deadline: Date.now() + 60_000 },
    connectionState: "open"
  };
}

function makeCompletedState(): JobStreamState {
  return {
    events: [],
    byEmail: {},
    summary: { ok: 2, authFailed: 1, requiresManual: 0, failed: 0, skipped: 0, total: 3 },
    awaitingManual: null,
    connectionState: "closed"
  };
}

function makeStoppedState(): JobStreamState {
  return {
    ...makeCompletedState(),
    connectionState: "closed"
  };
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("RunScreen", () => {
  beforeEach(() => {
    mockUseJobStream.mockReset();
    mockStopJob.mockReset().mockResolvedValue(undefined);
  });

  it("renders the run-screen root element", () => {
    mockUseJobStream.mockReturnValue(makeIdleState());
    render(<RunScreen jobId="job-1" total={3} onCompleted={vi.fn()} onStopped={vi.fn()} />);
    expect(screen.getByTestId("run-screen")).toBeInTheDocument();
  });

  it("idle state: shows ProgressRing with 0/N and no callout", () => {
    mockUseJobStream.mockReturnValue(makeIdleState());
    render(<RunScreen jobId="job-1" total={3} onCompleted={vi.fn()} onStopped={vi.fn()} />);
    expect(screen.getByText("0/3")).toBeInTheDocument();
    expect(screen.queryByTestId("manual-callout")).not.toBeInTheDocument();
  });

  it("running state: ring updates ok count and table is rendered", () => {
    const rows: AccountView[] = [
      { ...emptyAccount, email: "alice@x.com", status: "OK" },
      { ...emptyAccount, email: "bob@x.com", index: 1, status: "RUNNING" }
    ];
    const state = makeRunningState(rows);
    state.summary.ok = 1;
    mockUseJobStream.mockReturnValue(state);
    render(<RunScreen jobId="job-1" total={2} onCompleted={vi.fn()} onStopped={vi.fn()} />);
    expect(screen.getByText("alice@x.com")).toBeInTheDocument();
    expect(screen.getByText("bob@x.com")).toBeInTheDocument();
    expect(screen.getByText("1/2")).toBeInTheDocument();
  });

  it("awaiting-manual state: callout is visible with correct email", () => {
    mockUseJobStream.mockReturnValue(makeAwaitingState("carol@x.com"));
    render(<RunScreen jobId="job-1" total={1} onCompleted={vi.fn()} onStopped={vi.fn()} />);
    const callout = screen.getByTestId("manual-callout");
    expect(callout).toBeInTheDocument();
    expect(within(callout).getByText(/carol@x\.com/)).toBeInTheDocument();
  });

  it("callout is not shown when awaitingManual is null", () => {
    mockUseJobStream.mockReturnValue(makeRunningState([{ ...emptyAccount }]));
    render(<RunScreen jobId="job-1" total={1} onCompleted={vi.fn()} onStopped={vi.fn()} />);
    expect(screen.queryByTestId("manual-callout")).not.toBeInTheDocument();
  });

  it("completed state: calls onCompleted with summary", () => {
    const onCompleted = vi.fn();
    mockUseJobStream.mockReturnValue(makeCompletedState());
    render(<RunScreen jobId="job-1" total={3} onCompleted={onCompleted} onStopped={vi.fn()} />);
    expect(onCompleted).toHaveBeenCalledOnce();
    expect(onCompleted).toHaveBeenCalledWith(
      expect.objectContaining({ ok: 2, authFailed: 1 })
    );
  });

  it("stopped state: calls onStopped", () => {
    const onStopped = vi.fn();
    // Make it a stopped (not completed with summary) scenario.
    // At least one event must be present so the effect distinguishes a
    // "job ran then was stopped with no completions" from a WS connection failure.
    const stoppedState: JobStreamState = {
      events: [{ type: "RUN_STARTED" } as unknown as import("../api/types").JobEvent],
      byEmail: {},
      summary: { ...emptySummary, total: 3 },
      awaitingManual: null,
      connectionState: "closed"
    };
    // Distinguish stopped from completed: no summary counts > 0
    mockUseJobStream.mockReturnValue(stoppedState);
    const onCompleted = vi.fn();
    render(<RunScreen jobId="job-1" total={3} onCompleted={onCompleted} onStopped={onStopped} />);
    // With zero summary counts and closed state → stopped
    expect(onStopped).toHaveBeenCalledOnce();
    expect(onCompleted).not.toHaveBeenCalled();
  });

  it("renders a Stop button", () => {
    mockUseJobStream.mockReturnValue(makeIdleState());
    render(<RunScreen jobId="job-1" total={3} onCompleted={vi.fn()} onStopped={vi.fn()} />);
    expect(screen.getByTestId("stop")).toBeInTheDocument();
  });

  it("Stop button click opens confirm dialog", async () => {
    mockUseJobStream.mockReturnValue(makeIdleState());
    render(<RunScreen jobId="job-1" total={3} onCompleted={vi.fn()} onStopped={vi.fn()} />);
    await userEvent.click(screen.getByTestId("stop"));
    expect(screen.getByTestId("confirm-dialog")).toBeInTheDocument();
  });

  it("confirming stop calls stopJob with the jobId", async () => {
    mockUseJobStream.mockReturnValue(makeIdleState());
    render(<RunScreen jobId="job-1" total={3} onCompleted={vi.fn()} onStopped={vi.fn()} />);
    await userEvent.click(screen.getByTestId("stop"));
    await userEvent.click(screen.getByTestId("confirm-stop"));
    expect(mockStopJob).toHaveBeenCalledWith("job-1");
  });

  it("cancelling stop dialog does not call stopJob", async () => {
    mockUseJobStream.mockReturnValue(makeIdleState());
    render(<RunScreen jobId="job-1" total={3} onCompleted={vi.fn()} onStopped={vi.fn()} />);
    await userEvent.click(screen.getByTestId("stop"));
    await userEvent.click(screen.getByTestId("cancel-stop"));
    expect(mockStopJob).not.toHaveBeenCalled();
    expect(screen.queryByTestId("confirm-dialog")).not.toBeInTheDocument();
  });
});
