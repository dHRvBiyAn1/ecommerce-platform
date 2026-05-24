import client from "@/api/client";
import type { ApiResponse, Notification, PageResponse } from "@/api/types";

export async function listMyNotifications(page = 0, size = 20): Promise<PageResponse<Notification>> {
  const { data } = await client.get<ApiResponse<PageResponse<Notification>>>("/v1/notifications", {
    params: { page, size },
  });
  return data.data;
}

export async function unreadCount(): Promise<number> {
  const { data } = await client.get<ApiResponse<number>>("/v1/notifications/unread/count");
  return data.data;
}

export async function markRead(id: string): Promise<Notification> {
  const { data } = await client.post<ApiResponse<Notification>>(`/v1/notifications/${id}/read`);
  return data.data;
}
