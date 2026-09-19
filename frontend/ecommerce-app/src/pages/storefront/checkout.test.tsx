import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Route, Routes } from "react-router-dom";
import userEvent from "@testing-library/user-event";
import { act, screen, waitFor } from "@testing-library/react";
import { loadStripe } from "@stripe/stripe-js";
import { CheckoutPage } from "@/pages/storefront/checkout";
import { useCart } from "@/stores/cart";
import { useAuthStore } from "@/stores/auth";
import { formatMoney } from "@/lib/utils";
import { renderWithProviders } from "@/test/render";
import { server } from "@/test/server";

vi.mock("@stripe/stripe-js", () => ({ loadStripe: vi.fn() }));

const formControls = vi.hoisted(() => ({
  setValue: null as ((name: string, value: unknown) => void) | null,
}));

vi.mock("react-hook-form", async () => {
  const actual = await vi.importActual<typeof import("react-hook-form")>("react-hook-form");
  return {
    ...actual,
    useForm: (...args: any[]) => {
      const form = actual.useForm(...args);
      formControls.setValue = form.setValue as (name: string, value: unknown) => void;
      return form;
    },
  };
});

const order = {
  id: "order-1",
  orderNumber: "ORD-1",
  userId: "user-1",
  userEmail: "customer@example.com",
  status: "PENDING" as const,
  items: [{ productId: "product-1", productName: "Lamp", quantity: 1, unitPrice: 100, totalPrice: 100 }],
  subtotal: 100,
  taxAmount: 18,
  shippingCost: 49,
  discountAmount: 0,
  totalAmount: 167,
  currency: "INR",
  paymentId: "payment-1",
  paymentMethod: "card",
  paymentStatus: "PENDING" as const,
  createdAt: "2026-09-16T00:00:00Z",
  updatedAt: "2026-09-16T00:00:00Z",
};

const originalClear = useCart.getState().clear;

async function fillCheckout() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Full name"), "Ada Lovelace");
  await user.type(screen.getByLabelText("Phone"), "1234567890");
  await user.type(screen.getByLabelText("Street"), "1 Analytical Engine Way");
  await user.type(screen.getByLabelText("City"), "London");
  await user.type(screen.getByLabelText("State"), "London");
  await user.type(screen.getByLabelText("ZIP / Postal code"), "SW1A");
  return user;
}

describe("CheckoutPage", () => {
  beforeEach(() => {
    vi.stubEnv("VITE_STRIPE_PUBLISHABLE_KEY", "pk_test_checkout");
    useCart.setState({
      lines: [{ productId: "product-1", sku: "LAMP", name: "Lamp", price: 100, quantity: 1 }],
      couponCode: null,
      discountAmount: 0,
      pendingAttempt: null,
    });
  });

  afterEach(() => {
    useCart.setState({ clear: originalClear });
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it("retries a failed confirmation with the same key and in-memory secret without persisting the secret", async () => {
    const clear = vi.fn(originalClear);
    useCart.setState({ clear });
    const orderKeys: string[] = [];
    const paymentKeys: string[] = [];
    const readyHandlers: (() => void)[] = [];
    const cardElement = {
      on: vi.fn((event: string, handler: () => void) => {
        if (event === "ready") readyHandlers.push(handler);
      }),
      mount: vi.fn(() => readyHandlers.forEach((handler) => handler())),
      destroy: vi.fn(),
    };
    const confirmCardPayment = vi
      .fn()
      .mockRejectedValueOnce(new Error("card declined"))
      .mockResolvedValueOnce({ paymentIntent: { status: "succeeded" } });
    vi.mocked(loadStripe).mockResolvedValue({
      elements: () => ({ create: vi.fn(() => cardElement) }),
      confirmCardPayment,
    } as never);

    server.use(
      http.post("*/api/v1/orders", ({ request }) => {
        orderKeys.push(request.headers.get("X-Idempotency-Key") ?? "");
        return HttpResponse.json({ status: 201, message: "created", data: order });
      }),
      http.post("*/api/v1/payments", ({ request }) => {
        paymentKeys.push(request.headers.get("X-Idempotency-Key") ?? "");
        return HttpResponse.json({
          status: 201,
          message: "created",
          data: { payment: { ...order, id: "payment-1" }, clientSecret: paymentKeys.length === 1 ? "pi_secret" : null },
        });
      }),
    );

    renderWithProviders(
      <Routes>
        <Route path="/checkout" element={<CheckoutPage />} />
        <Route path="/order-success/order-1" element={<div>confirmed</div>} />
      </Routes>,
      { initialEntries: ["/checkout"] },
    );

    expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    expect(screen.getByLabelText("Method")).toBeInTheDocument();
    expect(screen.getByLabelText("Card details")).toBeInTheDocument();
    expect(screen.getByLabelText("Notes")).toBeInTheDocument();
    await waitFor(() => expect(cardElement.mount).toHaveBeenCalled());
    expect(cardElement.on).toHaveBeenCalledWith("ready", expect.any(Function));
    const user = await fillCheckout();
    await user.click(screen.getByRole("button", { name: "Place order" }));

    await waitFor(() =>
      expect(confirmCardPayment).toHaveBeenCalledWith("pi_secret", {
        payment_method: { card: cardElement },
      }),
    );
    expect(clear).not.toHaveBeenCalled();
    expect(screen.queryByText("confirmed")).not.toBeInTheDocument();
    const persistedAfterFailure = localStorage.getItem("ecom-cart-v2") ?? "";
    expect(persistedAfterFailure).not.toContain("pi_secret");
    expect(sessionStorage.getItem("ecom-cart-v2")).toBeNull();

    await user.click(screen.getByRole("button", { name: "Place order" }));

    await waitFor(() => expect(screen.getByText("confirmed")).toBeInTheDocument());
    expect(clear).not.toHaveBeenCalled();
    expect(orderKeys).toHaveLength(2);
    expect(paymentKeys).toHaveLength(2);
    expect(orderKeys[0]).toBe(orderKeys[1]);
    expect(paymentKeys[0]).toBe(paymentKeys[1]);
    expect(orderKeys[0]).toBe(paymentKeys[0]);
    expect(orderKeys[0]).toMatch(/^[0-9a-f-]{36}$/);
    expect(paymentKeys[0]).toMatch(/^[0-9a-f-]{36}$/);
    expect(confirmCardPayment).toHaveBeenNthCalledWith(2, "pi_secret", {
      payment_method: { card: cardElement },
    });
    expect(localStorage.getItem("ecom-cart-v2")).not.toContain("pi_secret");
    expect(cardElement.destroy).toHaveBeenCalledOnce();
    screen.getByText("confirmed");
  });

  it("waits for non-card cart clearing before navigating", async () => {
    let resolveClear!: () => void;
    const clear = vi.fn(() => new Promise<void>((resolve) => {
      resolveClear = resolve;
    }));
    useCart.setState({ clear });
    server.use(
      http.post("*/api/v1/orders", () => HttpResponse.json({ status: 201, message: "created", data: order })),
      http.post("*/api/v1/payments", () => HttpResponse.json({
        status: 201,
        message: "created",
        data: { payment: { ...order, id: "payment-1" }, clientSecret: null },
      })),
    );

    renderWithProviders(
      <Routes>
        <Route path="/checkout" element={<CheckoutPage />} />
        <Route path="/order-success/order-1" element={<div>confirmed</div>} />
      </Routes>,
      { initialEntries: ["/checkout"] },
    );

    const user = await fillCheckout();
    act(() => formControls.setValue?.("paymentMethod", "upi"));

    await user.click(screen.getByRole("button", { name: "Place order" }));
    await waitFor(() => expect(clear).toHaveBeenCalledOnce());
    expect(screen.queryByText("confirmed")).not.toBeInTheDocument();
    resolveClear();
    await waitFor(() => expect(screen.getByText("confirmed")).toBeInTheDocument());
  });

  it("destroys the mounted card element when checkout unmounts", async () => {
    vi.stubEnv("VITE_STRIPE_PUBLISHABLE_KEY", "pk_test_cleanup");
    const cardElement = { on: vi.fn(), mount: vi.fn(), destroy: vi.fn() };
    vi.mocked(loadStripe).mockResolvedValue({
      elements: () => ({ create: vi.fn(() => cardElement) }),
    } as never);

    const view = renderWithProviders(<CheckoutPage />);
    await waitFor(() => expect(cardElement.mount).toHaveBeenCalled());
    view.unmount();
    expect(cardElement.destroy).toHaveBeenCalledOnce();
  });

  it("routes a persisted secret-less attempt to status recovery without replaying writes", async () => {
    const postCalls: string[] = [];
    let paymentCalls = 0;
    useCart.setState({
      pendingAttempt: { idempotencyKey: "4c1b2f0a-0af4-4c55-a1ac-1f7e4d3e88f0", orderId: "order-1", paymentId: "payment-1" },
    });
    server.use(
      http.post("*/api/v1/orders", () => {
        postCalls.push("order");
        return HttpResponse.json({ status: 500, message: "must not post" }, { status: 500 });
      }),
      http.post("*/api/v1/payments", () => {
        postCalls.push("payment");
        return HttpResponse.json({ status: 500, message: "must not post" }, { status: 500 });
      }),
      http.get("*/api/v1/orders/order-1", () =>
        HttpResponse.json({ status: 200, message: "ok", data: { ...order, status: "CONFIRMED" } })),
      http.get("*/api/v1/payments/payment-1", () => {
        paymentCalls += 1;
        return HttpResponse.json({
          status: 200,
          message: "ok",
          data: { ...order, id: "payment-1", status: paymentCalls === 1 ? "PENDING" : "COMPLETED" },
        });
      }),
    );

    renderWithProviders(
      <Routes>
        <Route path="/checkout" element={<CheckoutPage />} />
        <Route path="/order-success/:id" element={<div>recovery-ui</div>} />
      </Routes>,
      { initialEntries: ["/checkout"] },
    );

    await waitFor(() => expect(screen.getByText("recovery-ui")).toBeInTheDocument());
    expect(postCalls).toEqual([]);
    expect(useCart.getState().pendingAttempt?.orderId).toBe("order-1");
  });

  it("keeps checkout totals safe for empty and zero-value cart data", () => {
    expect(formatMoney(null)).toBe("—");
    expect(formatMoney("not-a-number")).toBe("—");

    useCart.setState({ lines: [{ productId: "product-1", sku: "LAMP", name: "Lamp", price: 0, quantity: 0 }] });
    expect(useCart.getState().totalItems()).toBe(0);
    expect(useCart.getState().subtotal()).toBe(0);

    useCart.setState({ lines: undefined as never });
    expect(useCart.getState().totalItems()).toBe(0);
    expect(useCart.getState().subtotal()).toBe(0);
  });

  it("clears a pending attempt when the authenticated cart is reset", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    server.use(
      http.delete("*/api/v1/cart", () => HttpResponse.json({ id: "cart-1", items: [] })),
    );
    useAuthStore.setState({
      accessToken: "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ1c2VyLTEifQ.signature",
    });
    useCart.setState({ pendingAttempt: { idempotencyKey: "attempt-1", clientSecret: "secret" } });

    await useCart.getState().clear();

    expect(useCart.getState().pendingAttempt).toBeNull();
    server.use(http.delete("*/api/v1/cart", () => HttpResponse.json({ message: "failed" }, { status: 500 })));
    await useCart.getState().clear();
    useAuthStore.setState({ accessToken: null, user: null });
  });
});
