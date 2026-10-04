import userEvent from "@testing-library/user-event";
import { act, cleanup, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Route, Routes } from "react-router-dom";
import { toast } from "sonner";
import { CartPage } from "@/pages/storefront/cart";
import { renderWithProviders } from "@/test/render";
import { useAuthStore } from "@/stores/auth";
import { useCart } from "@/stores/cart";

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const original = useCart.getState();
const line = { productId: "product-1", sku: "LAMP", name: "Lamp", price: 100, quantity: 2 };

describe("CartPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, user: null });
    useCart.setState({
      lines: [line],
      couponCode: null,
      discountAmount: 0,
      setQuantity: vi.fn().mockResolvedValue(undefined),
      remove: vi.fn().mockResolvedValue(undefined),
      clear: vi.fn().mockResolvedValue(undefined),
      applyCoupon: vi.fn().mockResolvedValue(undefined),
      removeCoupon: vi.fn().mockResolvedValue(undefined),
    });
  });

  afterEach(() => {
    cleanup();
    useCart.setState(original, true);
    vi.clearAllMocks();
  });

  it("shows the empty-cart recovery link", () => {
    useCart.setState({ lines: [] });
    renderWithProviders(<CartPage />);

    expect(screen.getByRole("heading", { name: "Your bag is empty" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse products" })).toHaveAttribute("href", "/products");
  });

  it("updates and removes line items and cleans up on unmount", async () => {
    const user = userEvent.setup();
    const view = renderWithProviders(<CartPage />);

    await user.click(screen.getByRole("button", { name: "Increment" }));
    await user.click(screen.getByRole("button", { name: "Decrement" }));
    await user.click(screen.getByRole("button", { name: "Remove" }));
    await user.click(screen.getByRole("button", { name: "Clear bag" }));

    expect(useCart.getState().setQuantity).toHaveBeenNthCalledWith(1, "product-1", 3);
    expect(useCart.getState().setQuantity).toHaveBeenNthCalledWith(2, "product-1", 1);
    expect(useCart.getState().remove).toHaveBeenCalledWith("product-1");
    expect(useCart.getState().clear).toHaveBeenCalledOnce();
    view.unmount();
  });

  it("sends guests to login with checkout return state", async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <Routes>
        <Route path="/cart" element={<CartPage />} />
        <Route path="/login" element={<p>Login destination</p>} />
      </Routes>,
      { initialEntries: ["/cart"] },
    );

    await user.click(screen.getByRole("button", { name: "Sign in to checkout" }));
    expect(screen.getByText("Login destination")).toBeInTheDocument();
  });

  it("applies and removes coupons for authenticated users", async () => {
    useAuthStore.setState({ accessToken: "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ1c2VyLTEifQ.signature" });
    const user = userEvent.setup();
    const view = renderWithProviders(<CartPage />);

    await user.type(screen.getByPlaceholderText("Coupon code"), "SAVE10");
    await user.click(screen.getByRole("button", { name: "Apply" }));
    await waitFor(() => expect(useCart.getState().applyCoupon).toHaveBeenCalledWith("SAVE10"));
    expect(toast.success).toHaveBeenCalledWith("Coupon applied");

    act(() => useCart.setState({ couponCode: "SAVE10", discountAmount: 10 }));
    const removeCoupon = screen.getByPlaceholderText("Coupon code").parentElement?.querySelector("button");
    expect(removeCoupon).not.toBeNull();
    await user.click(removeCoupon!);
    await waitFor(() => expect(useCart.getState().removeCoupon).toHaveBeenCalledOnce());
    expect(toast.success).toHaveBeenCalledWith("Coupon removed");
    view.unmount();
  });
});
