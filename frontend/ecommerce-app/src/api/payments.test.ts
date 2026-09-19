import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createPayment, getPayment, getPaymentByOrder, listMyPayments, processPayment } from "@/api/payments";
import { server } from "@/test/server";

const payment = {
  id: "payment-1",
  paymentReference: "pay_1",
  orderId: "order-1",
  userId: "user-1",
  userEmail: "customer@example.com",
  status: "PENDING" as const,
  paymentMethod: "card",
  amount: 100,
  currency: "INR",
  createdAt: "2026-09-16T00:00:00Z",
  updatedAt: "2026-09-16T00:00:00Z",
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe("payments API", () => {
  it("reuses the idempotency key and keeps the initiation secret out of storage", async () => {
    let requestKey: string | null = null;
    server.use(
      http.post("*/api/v1/payments", ({ request }) => {
        requestKey = request.headers.get("X-Idempotency-Key");
        return HttpResponse.json({
          status: 201,
          message: "created",
          data: { payment, clientSecret: "secret_only_in_memory" },
        });
      }),
    );

    const result = await createPayment(
      {
        orderId: "order-1",
        paymentMethod: "card",
        amount: 100,
        currency: "INR",
      },
      "attempt-1",
    );

    expect(requestKey).toBe("attempt-1");
    expect(result.clientSecret).toBe("secret_only_in_memory");
    expect(localStorage.getItem("ecom-payment-attempt")).toBeNull();
    expect(sessionStorage.getItem("ecom-payment-attempt")).toBeNull();
  });

  it("reads payment status without requesting or exposing a replacement secret", async () => {
    let requested = false;
    server.use(
      http.get("*/api/v1/payments/payment-1", () => {
        requested = true;
        return HttpResponse.json({ status: 200, message: "ok", data: payment });
      }),
    );

    const result = await getPayment("payment-1");

    expect(requested).toBe(true);
    expect(result).toEqual(payment);
    expect("clientSecret" in result).toBe(false);
  });

  it("keeps every other payment read secret-free", async () => {
    server.use(
      http.post("*/api/v1/payments/payment-1/process", () =>
        HttpResponse.json({ status: 200, message: "ok", data: payment })),
      http.get("*/api/v1/payments", () =>
        HttpResponse.json({ status: 200, message: "ok", data: [payment] })),
      http.get("*/api/v1/payments/order/order-1", () =>
        HttpResponse.json({ status: 200, message: "ok", data: payment })),
    );

    await expect(processPayment("payment-1")).resolves.toEqual(payment);
    await expect(listMyPayments()).resolves.toEqual([payment]);
    await expect(getPaymentByOrder("order-1")).resolves.toEqual(payment);
  });
});
