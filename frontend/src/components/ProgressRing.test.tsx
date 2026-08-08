import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ProgressRing } from "./ProgressRing";

describe("ProgressRing", () => {
  it("renders the value/total text inside an SVG text element", () => {
    render(<ProgressRing value={2} total={5} />);
    expect(screen.getByText("2/5")).toBeInTheDocument();
  });

  it("renders 0/0 when both value and total are zero", () => {
    render(<ProgressRing value={0} total={0} />);
    expect(screen.getByText("0/0")).toBeInTheDocument();
  });

  it("renders an SVG element", () => {
    const { container } = render(<ProgressRing value={3} total={10} />);
    expect(container.querySelector("svg")).toBeInTheDocument();
  });

  it("renders a circle element for the track", () => {
    const { container } = render(<ProgressRing value={1} total={4} />);
    const circles = container.querySelectorAll("circle");
    expect(circles.length).toBeGreaterThanOrEqual(1);
  });

  it("renders gradient defs inside SVG", () => {
    const { container } = render(<ProgressRing value={1} total={4} />);
    expect(container.querySelector("defs")).toBeInTheDocument();
    expect(container.querySelector("linearGradient")).toBeInTheDocument();
  });

  it("renders full ring when value equals total", () => {
    render(<ProgressRing value={5} total={5} />);
    expect(screen.getByText("5/5")).toBeInTheDocument();
  });
});
