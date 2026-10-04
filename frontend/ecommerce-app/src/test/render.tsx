import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderOptions, type RenderResult } from "@testing-library/react";
import type { ReactElement } from "react";
import { MemoryRouter, type MemoryRouterProps } from "react-router-dom";
import { ThemeProvider } from "@/components/theme-provider";

type ProviderOptions = Omit<RenderOptions, "wrapper"> & {
  initialEntries?: MemoryRouterProps["initialEntries"];
  queryClient?: QueryClient;
};

export function renderWithProviders(
  ui: ReactElement,
  { initialEntries = ["/"], queryClient = new QueryClient(), ...options }: ProviderOptions = {},
): RenderResult {
  return render(
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter
          initialEntries={initialEntries}
          future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
        >
          {ui}
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
    options,
  );
}
