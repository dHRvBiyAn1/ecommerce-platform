import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Product, CartItem } from "@/api/types";
import { useAuthStore } from "./auth";
import * as cartApi from "@/api/cart";

/**
 * Cart store that mirrors the server-side cart for authenticated users
 * and uses local storage for guests.
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
  add: (product: Product, quantity?: number) => Promise<void>;
  setQuantity: (productId: string, quantity: number) => Promise<void>;
  remove: (productId: string) => Promise<void>;
  clear: () => Promise<void>;
  fetch: () => Promise<void>;
  totalItems: () => number;
  subtotal: () => number;
}

const mapItem = (item: CartItem): CartLine => ({
  productId: item.productId,
  sku: item.sku,
  name: item.productName,
  price: item.unitPrice,
  quantity: item.quantity,
});

export const useCart = create<CartState>()(
  persist(
    (set, get) => ({
      lines: [],

      fetch: async () => {
        if (!useAuthStore.getState().isAuthenticated()) return;
        try {
          const cart = await cartApi.getCart();
          set({ lines: cart.items?.map(mapItem) ?? [] });
        } catch (err) {
          console.error("Failed to fetch cart", err);
        }
      },

      add: async (product, quantity = 1) => {
        const isAuthed = useAuthStore.getState().isAuthenticated();
        
        if (isAuthed) {
          try {
            const cart = await cartApi.addItem({
              productId: product.id,
              sku: product.sku,
              productName: product.name,
              imageUrl: product.imageUrls?.[0] ?? null,
              unitPrice: product.price,
              quantity,
            });
            set({ lines: cart.items?.map(mapItem) ?? [] });
          } catch (err) {
            console.error("Failed to add to server cart", err);
            throw err;
          }
        } else {
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
        }
      },

      setQuantity: async (productId, quantity) => {
        if (useAuthStore.getState().isAuthenticated()) {
          try {
            const cart = await cartApi.updateQuantity(productId, quantity);
            set({ lines: cart.items?.map(mapItem) ?? [] });
          } catch (err) {
            console.error("Failed to update quantity", err);
            throw err;
          }
        } else {
          if (quantity <= 0) return get().remove(productId);
          set({
            lines: get().lines.map((l) =>
              l.productId === productId ? { ...l, quantity } : l,
            ),
          });
        }
      },

      remove: async (productId) => {
        if (useAuthStore.getState().isAuthenticated()) {
          try {
            const cart = await cartApi.removeItem(productId);
            set({ lines: cart.items?.map(mapItem) ?? [] });
          } catch (err) {
            console.error("Failed to remove item", err);
            throw err;
          }
        } else {
          set({ lines: get().lines.filter((l) => l.productId !== productId) });
        }
      },

      clear: async () => {
        if (useAuthStore.getState().isAuthenticated()) {
          try {
            await cartApi.clearCart();
          } catch (err) {
            console.error("Failed to clear cart", err);
          }
        }
        set({ lines: [] });
      },

      totalItems: () => {
        const lines = get().lines;
        return Array.isArray(lines) ? lines.reduce((s, l) => s + (l.quantity || 0), 0) : 0;
      },
      subtotal: () => {
        const lines = get().lines;
        return Array.isArray(lines) ? lines.reduce((s, l) => s + (l.price || 0) * (l.quantity || 0), 0) : 0;
      },
    }),
    {
      name: "ecom-cart-v2", // Increment version to clear old incompatible state
      partialize: (state) => ({ lines: state.lines }), // ONLY persist the data
    },
  ),
);
