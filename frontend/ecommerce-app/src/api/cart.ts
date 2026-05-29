import client from "@/api/client";
import type {
  AddCartItemRequest,
  ApplyCouponRequest,
  Cart,
  UpdateQuantityRequest,
} from "@/api/types";

export async function getCart(): Promise<Cart> {
  const { data } = await client.get<Cart>("/v1/cart");
  return data;
}

export async function addItem(req: AddCartItemRequest): Promise<Cart> {
  const { data } = await client.post<Cart>("/v1/cart/items", req);
  return data;
}

export async function updateQuantity(
  productId: string,
  quantity: number,
): Promise<Cart> {
  const { data } = await client.patch<Cart>(`/v1/cart/items/${productId}`, {
    quantity,
  } as UpdateQuantityRequest);
  return data;
}

export async function removeItem(productId: string): Promise<Cart> {
  const { data } = await client.delete<Cart>(`/v1/cart/items/${productId}`);
  return data;
}

export async function clearCart(): Promise<Cart> {
  const { data } = await client.delete<Cart>("/v1/cart");
  return data;
}

export async function applyCoupon(code: string): Promise<Cart> {
  const { data } = await client.post<Cart>("/v1/cart/coupon", {
    code,
  } as ApplyCouponRequest);
  return data;
}

export async function removeCoupon(): Promise<Cart> {
  const { data } = await client.delete<Cart>("/v1/cart/coupon");
  return data;
}
