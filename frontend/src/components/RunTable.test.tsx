import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RunTable } from "./RunTable";
import type { AccountView } from "../hooks/useJobStream";

const baseAccount: AccountView = {
  email: "alice@example.com",
  index: 0,
  currentStep: "LOGIN",
  status: "RUNNING",
  elapsedMs: 0,
  startedAt: Date.now()
};

describe("RunTable", () => {
  it("renders a row for each account", () => {
    const rows: AccountView[] = [
      { ...baseAccount, email: "alice@example.com" },
      { ...baseAccount, email: "bob@example.com", index: 1 }
    ];
    render(<RunTable rows={rows} />);
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
    expect(screen.getByText("bob@example.com")).toBeInTheDocument();
  });

  it("renders nothing when rows is empty", () => {
    const { container } = render(<RunTable rows={[]} />);
    const tbody = container.querySelector("tbody");
    expect(tbody?.children.length).toBe(0);
  });

  it("shows OK status pill for an OK account", () => {
    const rows: AccountView[] = [{ ...baseAccount, status: "OK" }];
    render(<RunTable rows={rows} />);
    expect(screen.getByText("OK")).toBeInTheDocument();
  });

  it("shows AUTH_FAILED status pill", () => {
    const rows: AccountView[] = [{ ...baseAccount, status: "AUTH_FAILED" }];
    render(<RunTable rows={rows} />);
    expect(screen.getByText("AUTH_FAILED")).toBeInTheDocument();
  });

  it("shows REQUIRES_MANUAL status pill", () => {
    const rows: AccountView[] = [{ ...baseAccount, status: "REQUIRES_MANUAL" }];
    render(<RunTable rows={rows} />);
    expect(screen.getByText("REQUIRES_MANUAL")).toBeInTheDocument();
  });

  it("shows FAILED status pill", () => {
    const rows: AccountView[] = [{ ...baseAccount, status: "FAILED" }];
    render(<RunTable rows={rows} />);
    expect(screen.getByText("FAILED")).toBeInTheDocument();
  });

  it("shows PENDING status pill", () => {
    const rows: AccountView[] = [{ ...baseAccount, status: "PENDING" }];
    render(<RunTable rows={rows} />);
    expect(screen.getByText("PENDING")).toBeInTheDocument();
  });

  it("shows the current step text", () => {
    const rows: AccountView[] = [{ ...baseAccount, currentStep: "UPLOAD_RESUME" }];
    render(<RunTable rows={rows} />);
    expect(screen.getByText("UPLOAD_RESUME")).toBeInTheDocument();
  });

  it("renders the correct number of rows in the table body", () => {
    const rows: AccountView[] = [
      { ...baseAccount, email: "a@x.com" },
      { ...baseAccount, email: "b@x.com", index: 1 },
      { ...baseAccount, email: "c@x.com", index: 2 }
    ];
    const { container } = render(<RunTable rows={rows} />);
    const tbody = container.querySelector("tbody");
    expect(tbody?.children.length).toBe(3);
  });
});
