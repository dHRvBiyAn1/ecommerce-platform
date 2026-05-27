import * as React from "react";
import { AlertTriangle, RefreshCcw } from "lucide-react";
import { Button } from "@/components/ui/button";

interface State {
  hasError: boolean;
  error?: unknown;
}

/**
 * App-level error boundary. Catches render errors anywhere below it and
 * presents a branded fallback. We deliberately avoid swallowing the error in
 * dev so the React stack still shows up in the console.
 */
export class ErrorBoundary extends React.Component<
  { children: React.ReactNode; fallback?: React.ReactNode },
  State
> {
  state: State = { hasError: false };

  static getDerivedStateFromError(error: unknown): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: unknown, info: React.ErrorInfo) {
    // eslint-disable-next-line no-console
    console.error("[ErrorBoundary]", error, info.componentStack);
  }

  reset = () => this.setState({ hasError: false, error: undefined });

  render() {
    if (!this.state.hasError) return this.props.children;
    if (this.props.fallback) return this.props.fallback;

    return (
      <div className="grid min-h-screen place-items-center bg-background p-6 text-center">
        <div className="max-w-md">
          <div className="mx-auto grid h-14 w-14 place-items-center rounded-full bg-destructive/10 text-destructive">
            <AlertTriangle className="h-6 w-6" />
          </div>
          <h1 className="mt-6 font-display text-3xl font-semibold tracking-tight">
            Something snapped.
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            The page hit an unexpected error. We've logged it. Try reloading —
            most of the time that's all it takes.
          </p>
          <div className="mt-6 flex justify-center gap-3">
            <Button variant="accent" onClick={() => window.location.reload()}>
              <RefreshCcw className="h-4 w-4" /> Reload
            </Button>
            <Button variant="outline" onClick={this.reset}>
              Try again
            </Button>
          </div>
        </div>
      </div>
    );
  }
}
