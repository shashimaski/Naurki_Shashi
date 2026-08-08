/**
 * TDD tests for ResultsScreen — written RED first.
 *
 * Created by: Adikarthik Gupta C B
 */
import { describe, it, expect, vi, beforeAll, afterAll, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { ResultsScreen } from "./ResultsScreen";
import type { RunSummary } from "./RunScreen";
import type { AccountView } from "../hooks/useJobStream";

const BASE = "http://127.0.0.1:5000";

const server = setupServer(
  http.get(`${BASE}/api/runs/:jobId/report.csv`, () =>
    new HttpResponse("email,status\nalice@x.com,OK\n", {
      status: 200,
      headers: { "Content-Type": "text/csv" }
    })
  )
);

beforeAll(() => server.listen({ onUnhandledRequest: "warn" }));
afterEach(() => {
  server.resetHandlers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
afterAll(() => server.close());

const sampleSummary: RunSummary = {
  ok: 3,
  authFailed: 1,
  requiresManual: 0,
  failed: 2,
  skipped: 1,
};

const sampleAccounts: AccountView[] = [
  { email: "alice@x.com",   index: 0, currentStep: "", status: "OK",          elapsedMs: 100, startedAt: 0 },
  { email: "bob@x.com",     index: 1, currentStep: "", status: "AUTH_FAILED",  elapsedMs: 200, startedAt: 0 },
  { email: "carol@x.com",   index: 2, currentStep: "", status: "SKIPPED",      elapsedMs: 0,   startedAt: 0 },
];

describe("ResultsScreen", () => {
  it("renders the results-screen root", () => {
    render(
      <ResultsScreen
        jobId="job-1"
        summary={sampleSummary}
        accounts={sampleAccounts}
        outputFolder="C:/runs"
        onNewRun={vi.fn()}
      />
    );
    expect(screen.getByTestId("results-screen")).toBeInTheDocument();
  });

  it("shows OK count tile", () => {
    render(
      <ResultsScreen
        jobId="job-1"
        summary={sampleSummary}
        accounts={[]}
        outputFolder="C:/runs"
        onNewRun={vi.fn()}
      />
    );
    const tile = screen.getByTestId("count-ok");
    expect(tile).toBeInTheDocument();
    expect(tile.textContent).toContain("3");
  });

  it("shows all five summary counter tiles with correct values", () => {
    render(
      <ResultsScreen
        jobId="job-1"
        summary={sampleSummary}
        accounts={[]}
        outputFolder="C:/runs"
        onNewRun={vi.fn()}
      />
    );
    expect(screen.getByTestId("count-ok").textContent).toContain("3");
    expect(screen.getByTestId("count-auth-failed").textContent).toContain("1");
    expect(screen.getByTestId("count-requires-manual").textContent).toContain("0");
    expect(screen.getByTestId("count-failed").textContent).toContain("2");
    expect(screen.getByTestId("count-skipped").textContent).toContain("1");
  });

  it("clicking New run calls onNewRun", async () => {
    const onNewRun = vi.fn();
    render(
      <ResultsScreen
        jobId="job-1"
        summary={sampleSummary}
        accounts={[]}
        outputFolder="C:/runs"
        onNewRun={onNewRun}
      />
    );
    await userEvent.click(screen.getByTestId("new-run"));
    expect(onNewRun).toHaveBeenCalledOnce();
  });

  it("Open report folder calls window.electronAPI.openFolder", async () => {
    const openFolder = vi.fn();
    // Set on window directly — vi.stubGlobal sets on globalThis which equals window in jsdom
    vi.stubGlobal("electronAPI", { openFolder, pickFolder: vi.fn() });

    render(
      <ResultsScreen
        jobId="job-1"
        summary={sampleSummary}
        accounts={[]}
        outputFolder="C:/runs/out"
        onNewRun={vi.fn()}
      />
    );
    await userEvent.click(screen.getByTestId("open-folder"));
    expect(openFolder).toHaveBeenCalledWith("C:/runs/out");
  });

  it("Export CSV triggers anchor download to /api/runs/{jobId}/report.csv", async () => {
    render(
      <ResultsScreen
        jobId="job-42"
        summary={sampleSummary}
        accounts={[]}
        outputFolder="C:/runs"
        onNewRun={vi.fn()}
      />
    );

    // Set the spy AFTER render so RTL's mount call isn't captured
    const appended: HTMLAnchorElement[] = [];
    vi.spyOn(document.body, "appendChild").mockImplementation((node) => {
      if (node instanceof HTMLAnchorElement) appended.push(node);
      return node;
    });
    vi.spyOn(document.body, "removeChild").mockImplementation((node) => node);

    await userEvent.click(screen.getByTestId("export-csv"));

    expect(appended.length).toBeGreaterThan(0);
    expect(appended[0].href).toContain("/api/runs/job-42/report.csv");
  });

  it("renders account table rows", () => {
    render(
      <ResultsScreen
        jobId="job-1"
        summary={sampleSummary}
        accounts={sampleAccounts}
        outputFolder="C:/runs"
        onNewRun={vi.fn()}
      />
    );
    expect(screen.getByText("alice@x.com")).toBeInTheDocument();
    expect(screen.getByText("bob@x.com")).toBeInTheDocument();
    expect(screen.getByText("carol@x.com")).toBeInTheDocument();
  });
});
