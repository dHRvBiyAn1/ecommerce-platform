import client from "@/api/client";
import type { ApiResponse, UserProfile } from "@/api/types";

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface Role {
  id: string;
  name: string;
}

export async function listAdminUsers(page = 0, size = 100): Promise<PageResponse<UserProfile>> {
  const { data } = await client.get<ApiResponse<PageResponse<UserProfile>>>(
    "/admin/users",
    { params: { page, size } },
  );
  return data.data;
}

export async function getAdminUser(userId: string): Promise<UserProfile> {
  const { data } = await client.get<ApiResponse<UserProfile>>(`/admin/users/${userId}`);
  return data.data;
}

export async function setUserActive(userId: string, active: boolean): Promise<UserProfile> {
  const { data } = await client.put<ApiResponse<UserProfile>>(
    `/admin/users/${userId}/active`,
    null,
    { params: { active } },
  );
  return data.data;
}

export async function setUserRoles(userId: string, roles: string[]): Promise<UserProfile> {
  const { data } = await client.put<ApiResponse<UserProfile>>(
    `/admin/users/${userId}/roles`,
    { roles },
  );
  return data.data;
}

export async function listRoles(): Promise<Role[]> {
  const { data } = await client.get<ApiResponse<Role[]>>("/admin/roles");
  return data.data;
}
