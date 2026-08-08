/**
 * TDD tests for Stepper — written RED first, then Stepper implemented.
 *
 * Created by: Adikarthik Gupta C B
 */
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Stepper } from "./Stepper";

describe("Stepper", () => {
  it("renders all three step nodes", () => {
    render(<Stepper current="setup" />);
    expect(screen.getByTestId("step-setup")).toBeInTheDocument();
    expect(screen.getByTestId("step-run")).toBeInTheDocument();
    expect(screen.getByTestId("step-results")).toBeInTheDocument();
  });

  it("active node has data-active='true'", () => {
    render(<Stepper current="run" />);
    expect(screen.getByTestId("step-run")).toHaveAttribute("data-active", "true");
  });

  it("non-active nodes have data-active='false'", () => {
    render(<Stepper current="run" />);
    expect(screen.getByTestId("step-setup")).toHaveAttribute("data-active", "false");
    expect(screen.getByTestId("step-results")).toHaveAttribute("data-active", "false");
  });

  it("active node has accent gradient class", () => {
    render(<Stepper current="setup" />);
    const node = screen.getByTestId("step-setup");
    expect(node.className).toMatch(/accent-gradient/);
  });

  it("completed nodes have data-complete='true'", () => {
    render(<Stepper current="results" />);
    expect(screen.getByTestId("step-setup")).toHaveAttribute("data-complete", "true");
    expect(screen.getByTestId("step-run")).toHaveAttribute("data-complete", "true");
    expect(screen.getByTestId("step-results")).toHaveAttribute("data-complete", "false");
  });

  it("shows checkmark in completed nodes", () => {
    render(<Stepper current="results" />);
    const setupNode = screen.getByTestId("step-setup");
    expect(setupNode.textContent).toBe("✓");
  });

  it("shows step number in pending nodes", () => {
    render(<Stepper current="setup" />);
    const runNode = screen.getByTestId("step-run");
    expect(runNode.textContent).toBe("2");
  });

  it("renders step labels", () => {
    render(<Stepper current="setup" />);
    expect(screen.getByText("Setup")).toBeInTheDocument();
    expect(screen.getByText("Run")).toBeInTheDocument();
    expect(screen.getByText("Results")).toBeInTheDocument();
  });
});
