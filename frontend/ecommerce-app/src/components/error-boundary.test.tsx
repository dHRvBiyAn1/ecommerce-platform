import { fireEvent, render, screen } from "@testing-library/react";
import { useEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ErrorBoundary } from "@/components/error-boundary";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("ErrorBoundary", () => {
  it("logs render errors and shows the default recovery controls", () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    const Broken = () => {
      throw new Error("broken child");
    };

    render(<ErrorBoundary><Broken /></ErrorBoundary>);

    expect(screen.getByRole("heading", { name: "Something snapped." })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reload" })).toBeInTheDocument();
    expect(error).toHaveBeenCalledWith("[ErrorBoundary]", expect.any(Error), expect.any(String));
  });

  it("resets the boundary and renders the child again", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    let shouldThrow = true;
    const Recoverable = () => {
      if (shouldThrow) throw new Error("retry me");
      return <p>Recovered</p>;
    };

    render(<ErrorBoundary><Recoverable /></ErrorBoundary>);
    shouldThrow = false;
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(screen.getByText("Recovered")).toBeInTheDocument();
  });

  it("uses a caller-provided fallback", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const Broken = () => {
      throw new Error("custom fallback");
    };

    const view = render(<ErrorBoundary fallback={<p>Custom recovery</p>}><Broken /></ErrorBoundary>);
    expect(screen.getByText("Custom recovery")).toBeInTheDocument();
    view.unmount();
  });

  it("cleans up child effects when an error replaces the subtree", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const cleanup = vi.fn();
    const EffectfulChild = () => {
      useEffect(() => cleanup, []);
      return <p>Effect active</p>;
    };
    const Broken = ({ fail }: { fail: boolean }) => {
      if (fail) throw new Error("replace subtree");
      return null;
    };
    const view = render(
      <ErrorBoundary>
        <EffectfulChild />
        <Broken fail={false} />
      </ErrorBoundary>,
    );

    expect(cleanup).not.toHaveBeenCalled();
    view.rerender(
      <ErrorBoundary>
        <EffectfulChild />
        <Broken fail />
      </ErrorBoundary>,
    );

    expect(screen.getByRole("heading", { name: "Something snapped." })).toBeInTheDocument();
    expect(cleanup).toHaveBeenCalledOnce();
  });
});
