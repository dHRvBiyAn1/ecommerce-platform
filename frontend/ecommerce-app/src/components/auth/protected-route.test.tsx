import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";
import { ProtectedRoute } from "./protected-route";
import { useAuthStore } from "@/stores/auth";

function LoginDestination() {
  const location = useLocation();
  const state = location.state as { from?: string } | null;
  return (
    <main>
      <h1>Login</h1>
      <output aria-label="requested location">{state?.from}</output>
    </main>
  );
}

describe("ProtectedRoute", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, user: null });
  });

  it("redirects an unauthenticated user to login and preserves the requested URL", () => {
    render(
      <MemoryRouter initialEntries={["/checkout?coupon=SAVE10"]}
        future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
        <Routes>
          <Route path="/checkout" element={<ProtectedRoute><h1>Checkout</h1></ProtectedRoute>} />
          <Route path="/login" element={<LoginDestination />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "Login" })).toBeInTheDocument();
    expect(screen.getByLabelText("requested location")).toHaveTextContent("/checkout?coupon=SAVE10");
    expect(screen.queryByRole("heading", { name: "Checkout" })).not.toBeInTheDocument();
  });
});
