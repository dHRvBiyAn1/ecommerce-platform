import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/test/server";
import { useAuthStore } from "@/stores/auth";
import { createPendingAttempt, useCart } from "@/stores/cart";

const product = {
  id: "product-1",
  sku: "LAMP",
  name: "Lamp",
  price: 100,
  imageUrls: ["lamp.jpg"],
} as never;

const serverCart = {
  id: "cart-1",
  items: [{ productId: "product-1", sku: "LAMP", productName: "Lamp", unitPrice: 100, quantity: 2 }],
  appliedCouponCode: "SAVE10",
  appliedDiscountAmount: 10,
};

describe("cart store", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, user: null });
    useCart.setState({ lines: [], couponCode: null, discountAmount: 0, pendingAttempt: null });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("manages a guest cart locally", async () => {
    await useCart.getState().add(product, 2);
    await useCart.getState().add(product);

    expect(useCart.getState().lines).toEqual([
      { productId: "product-1", sku: "LAMP", name: "Lamp", price: 100, quantity: 3 },
    ]);
    expect(useCart.getState().totalItems()).toBe(3);
    expect(useCart.getState().subtotal()).toBe(300);

    await useCart.getState().setQuantity("product-1", 1);
    expect(useCart.getState().lines[0].quantity).toBe(1);
    await useCart.getState().setQuantity("product-1", 0);
    expect(useCart.getState().lines).toEqual([]);
  });

  it("mirrors authenticated cart mutations from the server", async () => {
    useAuthStore.setState({ accessToken: "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ1c2VyLTEifQ.signature" });
    const requests: string[] = [];
    server.use(
      http.get("*/api/v1/cart", () => HttpResponse.json(serverCart)),
      http.post("*/api/v1/cart/items", () => HttpResponse.json(serverCart)),
      http.patch("*/api/v1/cart/items/:id", () => HttpResponse.json(serverCart)),
      http.delete("*/api/v1/cart/items/:id", () => HttpResponse.json({ ...serverCart, items: [] })),
      http.post("*/api/v1/cart/coupon", () => HttpResponse.json(serverCart)),
      http.delete("*/api/v1/cart/coupon", () => HttpResponse.json({ ...serverCart, appliedCouponCode: null, appliedDiscountAmount: 0 })),
      http.delete("*/api/v1/cart", () => {
        requests.push("clear");
        return HttpResponse.json({ ...serverCart, items: [] });
      }),
    );

    await useCart.getState().fetch();
    expect(useCart.getState().lines[0]).toMatchObject({ name: "Lamp", quantity: 2 });
    await useCart.getState().add(product);
    await useCart.getState().setQuantity("product-1", 2);
    await useCart.getState().remove("product-1");
    await useCart.getState().applyCoupon("SAVE10");
    expect(useCart.getState().couponCode).toBe("SAVE10");
    await useCart.getState().removeCoupon();
    expect(useCart.getState().couponCode).toBeNull();

    useCart.getState().setPendingAttempt(createPendingAttempt("order-1", "payment-1"));
    await useCart.getState().clear();
    expect(requests).toEqual(["clear"]);
    expect(useCart.getState()).toMatchObject({ lines: [], pendingAttempt: null });
  });

  it("keeps checkout secrets out of persisted state", () => {
    const attempt = { ...createPendingAttempt(), clientSecret: "secret-only-in-memory" };
    useCart.getState().setPendingAttempt(attempt);

    expect(useCart.getState().pendingAttempt).toEqual(attempt);
    expect(localStorage.getItem("ecom-cart-v2")).not.toContain("secret-only-in-memory");
  });

  it("requires authentication for coupons and clears guest coupon state locally", async () => {
    await expect(useCart.getState().applyCoupon("SAVE10")).rejects.toThrow("Must be logged in");
    useCart.setState({ couponCode: "OLD", discountAmount: 5 });
    await useCart.getState().removeCoupon();
    expect(useCart.getState()).toMatchObject({ couponCode: null, discountAmount: 0 });
  });

  it("logs fetch failures without replacing the current cart", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    useAuthStore.setState({ accessToken: "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ1c2VyLTEifQ.signature" });
    useCart.setState({ lines: [{ productId: "old", sku: "OLD", name: "Old", price: 1, quantity: 1 }] });
    server.use(http.get("*/api/v1/cart", () => HttpResponse.json({ message: "failed" }, { status: 500 })));

    await useCart.getState().fetch();

    expect(useCart.getState().lines[0].productId).toBe("old");
    expect(console.error).toHaveBeenCalledWith("Failed to fetch cart", expect.anything());
  });
});
