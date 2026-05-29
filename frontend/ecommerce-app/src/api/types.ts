/**
 * Shape contracts for the API. Mirror the Spring DTOs so changes there require
 * an obvious change here. Keep this in lockstep with services/common.
 */

export interface ApiResponse<T> {
  status: number;
  message: string;
  data: T;
  traceId?: string;
  timestamp?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// Spring's native Page<T> when ApiResponse isn't used (product/inventory return raw Page).
export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  path?: string;
  code?: string;
  fieldErrors?: Record<string, string>;
  traceId?: string;
  timestamp?: string;
}

// ---- Auth ----

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
}

export interface UserProfile {
  id: string;
  email: string;
  displayName: string | null;
  imageUrl: string | null;
  phone?: string | null;
  active: boolean;
  createdAt?: string;
  roles: string[];
  permissions: string[];
  shippingAddress?: Address | null;
  billingAddress?: Address | null;
  /**
   * True iff the user has a LOCAL credential. False for OAuth2-only users
   * (e.g. Google) — the frontend uses this to hide the change-password card
   * since they have no password to change.
   */
  hasPassword?: boolean;
}

export interface RegistrationRequest {
  email: string;
  password: string;
  displayName: string;
}

// ---- Product ----

export interface Product {
  id: string;
  sku: string;
  name: string;
  description: string;
  categoryId: string;
  price: number;
  stockQuantity: number;
  imageUrls: string[] | null;
  sellerId: string;
  active: boolean;
  /** Marketplace moderation status. New seller products default to PENDING. */
  approvalStatus?: "PENDING" | "APPROVED" | "REJECTED" | null;
  rejectionReason?: string | null;
}

export interface Category {
  id: string;
  name: string;
  description?: string;
  parentCategoryId?: string;
  imageUrl?: string | null;
}

export interface ProductRequest {
  sku: string;
  name: string;
  description: string;
  categoryId: string;
  price: number;
  stockQuantity: number;
  imageUrls?: string[] | null;
  sellerId?: string;
}

// ---- Inventory ----

export interface InventoryItem {
  id: string;
  productId: string;
  sku: string;
  quantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  lowStockThreshold: number;
  location?: string;
  lastRestockedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

// ---- Order ----

export type OrderStatus =
  | "PENDING"
  | "CONFIRMED"
  | "PROCESSING"
  | "SHIPPED"
  | "DELIVERED"
  | "CANCELLED"
  | "REFUNDED";

export type PaymentStatus =
  | "PENDING"
  | "COMPLETED"
  | "FAILED"
  | "REFUNDED"
  | "PARTIALLY_REFUNDED";

export interface OrderItem {
  productId: string;
  sku?: string;
  productName?: string;
  imageUrl?: string | null;
  quantity: number;
  unitPrice: number;
  discountAmount?: number;
  totalPrice: number;
}

export interface Address {
  fullName?: string | null;
  phone?: string | null;
  street?: string | null;
  city?: string | null;
  state?: string | null;
  zipCode?: string | null;
  country?: string | null;
}

export interface Order {
  id: string;
  orderNumber: string;
  userId: string;
  userEmail: string;
  status: OrderStatus;
  items: OrderItem[];
  subtotal: number;
  taxAmount: number;
  shippingCost: number;
  discountAmount: number;
  totalAmount: number;
  currency: string;
  shippingAddress?: Address;
  billingAddress?: Address;
  paymentId?: string;
  paymentMethod?: string;
  paymentStatus?: PaymentStatus;
  couponCode?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
  paidAt?: string;
  shippedAt?: string;
  deliveredAt?: string;
  cancelledAt?: string;
}

export interface OrderRequest {
  items: { productId: string; quantity: number }[];
  couponCode?: string;
  shippingAddress?: Address;
  billingAddress?: Address;
  paymentMethod: string;
  notes?: string;
}

// ---- Payment ----

export interface Payment {
  id: string;
  paymentReference: string;
  orderId: string;
  orderNumber?: string;
  userId: string;
  userEmail: string;
  status: PaymentStatus;
  paymentMethod: string;
  amount: number;
  currency: string;
  transactionId?: string;
  gatewayResponse?: string;
  failureReason?: string;
  retryCount?: number;
  description?: string;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
}

// ---- Notification ----

export type NotificationStatus = "PENDING" | "SENT" | "FAILED" | "READ";

export interface Notification {
  id: string;
  userId: string;
  recipient: string;
  channel: string;
  category: string;
  subject: string;
  body: string;
  status: NotificationStatus;
  failureReason?: string;
  retryCount?: number;
  createdAt: string;
  sentAt?: string;
  readAt?: string;
  sourceEventId?: string;
}

// ---- Cart ----

export interface CartItem {
  productId: string;
  sku: string;
  productName: string;
  imageUrl: string | null;
  unitPrice: number;
  quantity: number;
}

export interface Cart {
  id: string;
  userId: string;
  items: CartItem[];
  currency: string;
  appliedCouponCode?: string | null;
  appliedDiscountAmount?: number;
  subtotal: number;
  total: number;
  itemCount: number;
  updatedAt: string;
}

export interface AddCartItemRequest {
  productId: string;
  sku: string;
  productName: string;
  imageUrl?: string | null;
  unitPrice: number;
  quantity: number;
  currency?: string;
}

export interface UpdateQuantityRequest {
  quantity: number;
}

export interface ApplyCouponRequest {
  code: string;
}
