import client from "@/api/client";
import type {
  Category,
  Product,
  ProductRequest,
  SpringPage,
} from "@/api/types";

export interface ProductQuery {
  page?: number;
  size?: number;
  sort?: string;
}

export async function listProducts(q: ProductQuery = {}): Promise<SpringPage<Product>> {
  const { data } = await client.get<SpringPage<Product>>("/v1/products", {
    params: { page: q.page ?? 0, size: q.size ?? 20, sort: q.sort },
  });
  return data;
}

export async function searchProducts(
  keyword: string,
  q: ProductQuery = {},
): Promise<SpringPage<Product>> {
  const { data } = await client.get<SpringPage<Product>>("/v1/products/search", {
    params: { keyword, page: q.page ?? 0, size: q.size ?? 20 },
  });
  return data;
}

export async function productsByCategory(
  categoryId: string,
  q: ProductQuery = {},
): Promise<SpringPage<Product>> {
  const { data } = await client.get<SpringPage<Product>>(
    `/v1/products/category/${categoryId}`,
    { params: { page: q.page ?? 0, size: q.size ?? 20 } },
  );
  return data;
}

export async function productsBySeller(
  sellerId: string,
  q: ProductQuery = {},
): Promise<SpringPage<Product>> {
  const { data } = await client.get<SpringPage<Product>>(
    `/v1/products/seller/${sellerId}`,
    { params: { page: q.page ?? 0, size: q.size ?? 20 } },
  );
  return data;
}

export async function myProducts(q: ProductQuery = {}): Promise<SpringPage<Product>> {
  const { data } = await client.get<SpringPage<Product>>("/v1/products/seller", {
    params: { page: q.page ?? 0, size: q.size ?? 20 },
  });
  return data;
}

export async function productByPrice(
  minPrice: number,
  maxPrice: number,
  q: ProductQuery = {},
): Promise<SpringPage<Product>> {
  const { data } = await client.get<SpringPage<Product>>("/v1/products/filter", {
    params: { minPrice, maxPrice, page: q.page ?? 0, size: q.size ?? 20 },
  });
  return data;
}

export async function getProduct(id: string): Promise<Product> {
  const { data } = await client.get<Product>(`/v1/products/${id}`);
  return data;
}

export async function createProduct(req: ProductRequest): Promise<Product> {
  const { data } = await client.post<Product>("/v1/products", req);
  return data;
}

export async function updateProduct(id: string, req: ProductRequest): Promise<Product> {
  const { data } = await client.put<Product>(`/v1/products/${id}`, req);
  return data;
}

export async function deleteProduct(id: string): Promise<void> {
  await client.delete(`/v1/products/${id}`);
}

export async function setProductActive(id: string, active: boolean): Promise<Product> {
  const { data } = await client.put<Product>(
    `/v1/products/${id}/active`,
    null,
    { params: { active } },
  );
  return data;
}

// ---- Admin moderation ----

export async function approveProduct(id: string): Promise<Product> {
  const { data } = await client.put<Product>(`/v1/products/${id}/approve`);
  return data;
}

export async function rejectProduct(id: string, reason: string): Promise<Product> {
  const { data } = await client.put<Product>(
    `/v1/products/${id}/reject`,
    null,
    { params: { reason } },
  );
  return data;
}

/** Admin: list products by approvalStatus (PENDING / APPROVED / REJECTED). */
export async function listByApprovalStatus(
  status: "PENDING" | "APPROVED" | "REJECTED",
  q: ProductQuery = {},
): Promise<SpringPage<Product>> {
  const { data } = await client.get<SpringPage<Product>>(
    "/v1/products/admin/by-status",
    { params: { status, page: q.page ?? 0, size: q.size ?? 50 } },
  );
  return data;
}

// ---- Categories ----

export async function listCategories(): Promise<Category[]> {
  const { data } = await client.get<Category[]>("/v1/categories");
  return data;
}

export async function getCategory(id: string): Promise<Category> {
  const { data } = await client.get<Category>(`/v1/categories/${id}`);
  return data;
}
