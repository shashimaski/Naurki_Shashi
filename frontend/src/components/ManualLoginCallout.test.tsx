import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act, fireEvent } from "@testing-library/react";
import { ManualLoginCallout } from "./ManualLoginCallout";

describe("ManualLoginCallout", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("displays the provided email", () => {
    const deadline = Date.now() + 60_000;
    render(
      <ManualLoginCallout
        email="alice@example.com"
        deadline={deadline}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />
    );
    expect(screen.getByText(/alice@example\.com/)).toBeInTheDocument();
  });

  it("displays an initial countdown greater than 0 when deadline is in the future", () => {
    const deadline = Date.now() + 60_000;
    render(
      <ManualLoginCallout
        email="alice@example.com"
        deadline={deadline}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />
    );
    // Should show something like "60s" or ">0"
    const countdown = screen.getByTestId("countdown");
    const seconds = parseInt(countdown.textContent ?? "0", 10);
    expect(seconds).toBeGreaterThan(0);
  });

  it("ticks countdown down by 1 after 1 second", () => {
    const deadline = Date.now() + 60_000;
    render(
      <ManualLoginCallout
        email="alice@example.com"
        deadline={deadline}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />
    );
    const before = parseInt(screen.getByTestId("countdown").textContent ?? "0", 10);
    act(() => { vi.advanceTimersByTime(1000); });
    const after = parseInt(screen.getByTestId("countdown").textContent ?? "0", 10);
    expect(after).toBe(before - 1);
  });

  it("calls onContinue when Continue button is clicked", () => {
    const onContinue = vi.fn();
    const deadline = Date.now() + 60_000;
    render(
      <ManualLoginCallout
        email="alice@example.com"
        deadline={deadline}
        onContinue={onContinue}
        onSkip={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: /continue/i }));
    expect(onContinue).toHaveBeenCalledOnce();
  });

  it("calls onSkip when Skip button is clicked", () => {
    const onSkip = vi.fn();
    const deadline = Date.now() + 60_000;
    render(
      <ManualLoginCallout
        email="alice@example.com"
        deadline={deadline}
        onContinue={vi.fn()}
        onSkip={onSkip}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: /skip/i }));
    expect(onSkip).toHaveBeenCalledOnce();
  });

  it("shows 0 when deadline has already passed", () => {
    const deadline = Date.now() - 1000;
    render(
      <ManualLoginCallout
        email="alice@example.com"
        deadline={deadline}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />
    );
    const countdown = screen.getByTestId("countdown");
    expect(countdown.textContent).toBe("0s");
  });
});
