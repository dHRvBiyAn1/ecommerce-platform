import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it } from "vitest";
import { renderWithProviders } from "./render";

function ApiProbe() {
  return (
    <button
      onClick={async () => {
        const response = await fetch("/api/test");
        const body = (await response.json()) as { message: string };
        document.title = body.message;
      }}
    >
      Probe API
    </button>
  );
}

it("renders with providers and intercepts an API call through MSW", async () => {
  renderWithProviders(<ApiProbe />);

  await userEvent.click(screen.getByRole("button", { name: "Probe API" }));
  await waitFor(() => expect(document.title).toBe("MSW intercepted"));
  expect(screen.getByRole("button", { name: "Probe API" })).toBeInTheDocument();
});
