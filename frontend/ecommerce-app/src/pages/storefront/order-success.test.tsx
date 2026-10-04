import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Route, Routes } from "react-router-dom";
import { useCart } from "@/stores/cart";
import { OrderSuccessPage } from "@/pages/storefront/order-success";
import { renderWithProviders } from "@/test/render";
import { server } from "@/test/server";
import { screen, waitFor } from "@testing-library/react";

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
  paymentStatus: "PENDING" as const,
  createdAt: "2026-09-16T00:00:00Z",
  updatedAt: "2026-09-16T00:00:00Z",
};

const originalClear = useCart.getState().clear;

afterEach(() => {
  useCart.setState({ clear: originalClear });
});

describe("OrderSuccessPage", () => {
  it("polls secret-free payment status and updates the rendered confirmation", async () => {
    const clear = vi.fn(originalClear);
    useCart.setState({ clear });
    useCart.setState({ pendingAttempt: { idempotencyKey: "attempt-1", orderId: "order-1", paymentId: "payment-1" } });
    let calls = 0;
    const paymentRequests: Request[] = [];
    server.use(
      http.get("*/api/v1/orders/order-1", () => HttpResponse.json({ status: 200, message: "ok", data: order })),
      http.get("*/api/v1/payments/payment-1", ({ request }) => {
        paymentRequests.push(request);
        calls += 1;
        return HttpResponse.json({
          status: 200,
          message: "ok",
          data: {
            id: "payment-1",
            paymentReference: "pay_1",
            orderId: "order-1",
            userId: "user-1",
            userEmail: "customer@example.com",
            status: calls === 1 ? "PENDING" : "COMPLETED",
            paymentMethod: "card",
            amount: 167,
            currency: "INR",
            createdAt: "2026-09-16T00:00:00Z",
            updatedAt: "2026-09-16T00:00:00Z",
          },
        });
      }),
    );

    renderWithProviders(
      <Routes>
        <Route path="/order-success/:id" element={<OrderSuccessPage />} />
      </Routes>,
      { initialEntries: ["/order-success/order-1"] },
    );
    await waitFor(() => expect(screen.getByText("Payment · PENDING")).toBeInTheDocument());
    expect(clear).not.toHaveBeenCalled();
    expect(useCart.getState().pendingAttempt).not.toBeNull();
    expect(paymentRequests[0].url).toContain("/api/v1/payments/payment-1");
    expect(await paymentRequests[0].text()).toBe("");

    await waitFor(() => expect(screen.getByText("Payment · COMPLETED")).toBeInTheDocument(), {
      timeout: 3500,
    });
    expect(calls).toBe(2);
    expect(clear).toHaveBeenCalledOnce();
    await waitFor(() => expect(useCart.getState().pendingAttempt).toBeNull());
  });

  it.each([
    ["absent", null],
    ["nonmatching", { idempotencyKey: "attempt-2", orderId: "order-2", paymentId: "payment-1" }],
  ])("does not clear when the pending attempt is %s", async (_label, pendingAttempt) => {
    const clear = vi.fn(originalClear);
    useCart.setState({ clear, pendingAttempt });
    server.use(
      http.get("*/api/v1/orders/order-1", () => HttpResponse.json({ status: 200, message: "ok", data: order })),
      http.get("*/api/v1/payments/payment-1", () => HttpResponse.json({
        status: 200,
        message: "ok",
        data: {
          id: "payment-1",
          paymentReference: "pay_1",
          orderId: "order-1",
          userId: "user-1",
          userEmail: "customer@example.com",
          status: "COMPLETED",
          paymentMethod: "card",
          amount: 167,
          currency: "INR",
          createdAt: "2026-09-16T00:00:00Z",
          updatedAt: "2026-09-16T00:00:00Z",
        },
      })),
    );

    renderWithProviders(
      <Routes>
        <Route path="/order-success/:id" element={<OrderSuccessPage />} />
      </Routes>,
      { initialEntries: ["/order-success/order-1"] },
    );

    await waitFor(() => expect(screen.getByText("Payment · COMPLETED")).toBeInTheDocument());
    expect(clear).not.toHaveBeenCalled();
  });
});
