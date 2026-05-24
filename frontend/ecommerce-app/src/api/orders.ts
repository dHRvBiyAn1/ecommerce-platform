import client from "@/api/client";
import type { ApiResponse, Order, OrderRequest, PageResponse, OrderStatus } from "@/api/types";

export async function createOrder(
  req: OrderRequest,
  idempotencyKey?: string,
): Promise<Order> {
  const { data } = await client.post<ApiResponse<Order>>("/v1/orders", req, {
    headers: idempotencyKey ? { "X-Idempotency-Key": idempotencyKey } : {},
  });
  return data.data;
}

export async function listMyOrders(page = 0, size = 20): Promise<PageResponse<Order>> {
  const { data } = await client.get<ApiResponse<PageResponse<Order>>>("/v1/orders", {
    params: { page, size },
  });
  return data.data;
}

export async function listAllOrders(page = 0, size = 20): Promise<PageResponse<Order>> {
  // Same endpoint, just admin path; backend decides scope by role.
  return listMyOrders(page, size);
}

export async function getOrder(id: string): Promise<Order> {
  const { data } = await client.get<ApiResponse<Order>>(`/v1/orders/${id}`);
  return data.data;
}

export async function cancelOrder(id: string): Promise<Order> {
  const { data } = await client.post<ApiResponse<Order>>(`/v1/orders/${id}/cancel`);
  return data.data;
}

export async function updateOrderStatus(id: string, status: OrderStatus): Promise<Order> {
  const { data } = await client.put<ApiResponse<Order>>(`/v1/orders/${id}/status`, {
    status,
  });
  return data.data;
}
