import client from "@/api/client";
import type { ApiResponse, Address } from "@/api/types";

export type SellerApplicationStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface SellerApplication {
  id: string;
  userId: string;
  userEmail: string | null;
  userDisplayName: string | null;
  status: SellerApplicationStatus;
  businessName: string;
  gstin: string | null;
  contactPhone: string;
  pickupAddress: Address;
  bankAccountLast4: string | null;
  notes: string | null;
  rejectionReason: string | null;
  submittedAt: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
}

export interface SellerApplicationRequest {
  businessName: string;
  gstin?: string;
  contactPhone: string;
  pickupAddress: Address;
  bankAccountLast4?: string;
  notes?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// ----- Customer

/** Returns null when the user has never applied (HTTP 204). */
export async function getMyApplication(): Promise<SellerApplication | null> {
  const res = await client.get<ApiResponse<SellerApplication>>(
    "/user/seller-application",
    { validateStatus: (s) => s === 200 || s === 204 },
  );
  if (res.status === 204) return null;
  return res.data.data;
}

export async function applyAsSeller(
  req: SellerApplicationRequest,
): Promise<SellerApplication> {
  const { data } = await client.post<ApiResponse<SellerApplication>>(
    "/user/seller-application",
    req,
  );
  return data.data;
}

// ----- Admin

export async function listSellerApplications(
  status?: SellerApplicationStatus,
  page = 0,
  size = 50,
): Promise<PageResponse<SellerApplication>> {
  const { data } = await client.get<ApiResponse<PageResponse<SellerApplication>>>(
    "/admin/seller-applications",
    { params: { status, page, size } },
  );
  return data.data;
}

export async function approveSellerApplication(id: string): Promise<SellerApplication> {
  const { data } = await client.put<ApiResponse<SellerApplication>>(
    `/admin/seller-applications/${id}/approve`,
  );
  return data.data;
}

export async function rejectSellerApplication(
  id: string,
  reason: string,
): Promise<SellerApplication> {
  const { data } = await client.put<ApiResponse<SellerApplication>>(
    `/admin/seller-applications/${id}/reject`,
    { reason },
  );
  return data.data;
}
