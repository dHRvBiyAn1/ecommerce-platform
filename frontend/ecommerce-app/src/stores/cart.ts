import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Product } from "@/api/types";

/**
 * Local-only cart for now. Once cart-service is built, this store becomes a
 * facade that mirrors the server-side cart for an authenticated user and the
 * Redis guest cart for visitors.
 */
export interface CartLine {
  productId: string;
  sku: string;
  name: string;
  price: number;
  quantity: number;
}

interface CartState {
  lines: CartLine[];
  add: (product: Product, quantity?: number) => void;
  setQuantity: (productId: string, quantity: number) => void;
  remove: (productId: string) => void;
  clear: () => void;
  totalItems: () => number;
  subtotal: () => number;
}

export const useCart = create<CartState>()(
  persist(
    (set, get) => ({
      lines: [],
      add: (product, quantity = 1) => {
        const lines = [...get().lines];
        const existing = lines.findIndex((l) => l.productId === product.id);
        if (existing >= 0) {
          lines[existing] = {
            ...lines[existing],
            quantity: lines[existing].quantity + quantity,
          };
        } else {
          lines.push({
            productId: product.id,
            sku: product.sku,
            name: product.name,
            price: product.price,
            quantity,
          });
        }
        set({ lines });
      },
      setQuantity: (productId, quantity) => {
        if (quantity <= 0) return get().remove(productId);
        set({
          lines: get().lines.map((l) =>
            l.productId === productId ? { ...l, quantity } : l,
          ),
        });
      },
      remove: (productId) => {
        set({ lines: get().lines.filter((l) => l.productId !== productId) });
      },
      clear: () => set({ lines: [] }),
      totalItems: () => get().lines.reduce((s, l) => s + l.quantity, 0),
      subtotal: () => get().lines.reduce((s, l) => s + l.price * l.quantity, 0),
    }),
    { name: "ecom-cart" },
  ),
);
