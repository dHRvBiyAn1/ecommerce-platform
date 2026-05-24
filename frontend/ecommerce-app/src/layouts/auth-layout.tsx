import * as React from "react";
import { Outlet } from "react-router-dom";

/**
 * Auth layout: editorial split. Left panel is a tall pink → black gradient
 * with stamped wordmark; right panel hosts the form. Strong typographic
 * statement to set the brand tone before the user sees the rest of the app.
 */
export const AuthLayout: React.FC = () => {
  const brand = import.meta.env.VITE_BRAND_NAME ?? "Étoile";
  const tagline =
    import.meta.env.VITE_BRAND_TAGLINE ?? "A modern marketplace for considered things.";

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <aside className="relative hidden overflow-hidden bg-primary text-primary-foreground lg:flex lg:flex-col">
        <div
          aria-hidden
          className="absolute inset-0"
          style={{
            backgroundImage:
              "radial-gradient(ellipse 80% 60% at 30% 30%, hsl(var(--accent) / 0.55), transparent 60%), radial-gradient(ellipse 90% 70% at 70% 80%, hsl(220 80% 30% / 0.6), transparent 60%)",
          }}
        />
        <div className="relative z-10 flex h-full flex-col justify-between p-12">
          <header className="flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-md bg-accent text-accent-foreground font-display text-lg font-bold">
              {brand[0]}
            </div>
            <span className="font-display text-xl font-semibold tracking-tight">
              {brand}
            </span>
          </header>

          <div className="space-y-6">
            <h1 className="font-display text-5xl font-semibold leading-[1.05] tracking-tight">
              Sell.
              <br />
              Discover.
              <br />
              <span className="text-accent">Belong.</span>
            </h1>
            <p className="max-w-md text-balance text-lg text-primary-foreground/70">
              {tagline}
            </p>
          </div>

          <footer className="flex items-end justify-between text-xs uppercase tracking-[0.18em] text-primary-foreground/50">
            <span>vol. 01 — issue 01</span>
            <span>2026</span>
          </footer>
        </div>
      </aside>

      <main className="flex items-center justify-center bg-background p-6 lg:p-12">
        <div className="w-full max-w-md">
          <Outlet />
        </div>
      </main>
    </div>
  );
};
