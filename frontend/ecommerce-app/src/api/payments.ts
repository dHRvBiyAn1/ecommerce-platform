import client from "@/api/client";
import type { ApiResponse, Payment } from "@/api/types";

export interface PaymentRequest {
  orderId: string;
  orderNumber?: string;
  paymentMethod: string;
  amount: number;
  currency: string;
  description?: string;
}

export async function createPayment(
  req: PaymentRequest,
  idempotencyKey?: string,
): Promise<Payment> {
  const { data } = await client.post<ApiResponse<Payment>>("/v1/payments", req, {
    headers: idempotencyKey ? { "X-Idempotency-Key": idempotencyKey } : {},
  });
  return data.data;
}

export async function processPayment(id: string): Promise<Payment> {
  const { data } = await client.post<ApiResponse<Payment>>(`/v1/payments/${id}/process`);
  return data.data;
}

export async function listMyPayments(): Promise<Payment[]> {
  const { data } = await client.get<ApiResponse<Payment[]>>("/v1/payments");
  return data.data;
}

export async function getPaymentByOrder(orderId: string): Promise<Payment> {
  const { data } = await client.get<ApiResponse<Payment>>(`/v1/payments/order/${orderId}`);
  return data.data;
}
