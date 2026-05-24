import client from "@/api/client";
import type { InventoryItem, SpringPage } from "@/api/types";

export interface InventoryRequest {
  productId: string;
  sku: string;
  quantity: number;
  lowStockThreshold: number;
  location?: string;
}

export async function listInventory(page = 0, size = 20): Promise<SpringPage<InventoryItem>> {
  const { data } = await client.get<SpringPage<InventoryItem>>("/v1/inventory", {
    params: { page, size },
  });
  return data;
}

export async function getInventory(productId: string): Promise<InventoryItem> {
  const { data } = await client.get<InventoryItem>(`/v1/inventory/${productId}`);
  return data;
}

export async function getLowStock(): Promise<InventoryItem[]> {
  const { data } = await client.get<InventoryItem[]>("/v1/inventory/low-stock");
  return data;
}

export async function createInventory(req: InventoryRequest): Promise<InventoryItem> {
  const { data } = await client.post<InventoryItem>("/v1/inventory", req);
  return data;
}

export async function updateInventory(id: string, req: InventoryRequest): Promise<InventoryItem> {
  const { data } = await client.put<InventoryItem>(`/v1/inventory/${id}`, req);
  return data;
}

export async function addStock(productId: string, quantity: number): Promise<InventoryItem> {
  const { data } = await client.post<InventoryItem>(
    `/v1/inventory/${productId}/add-stock`,
    null,
    { params: { quantity } },
  );
  return data;
}
