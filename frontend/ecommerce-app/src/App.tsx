import { lazy } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { StorefrontLayout } from "@/layouts/storefront-layout";
import { AuthLayout } from "@/layouts/auth-layout";
import { AdminLayout } from "@/layouts/admin-layout";
import { ProtectedRoute } from "@/components/protected-route";

// Auth — likely first hop, keep eager for snappier login.
import { LoginPage } from "@/pages/auth/login";
import { RegisterPage } from "@/pages/auth/register";
import { HomePage } from "@/pages/storefront/home";

// Code-split everything else to keep the initial bundle small.
const CatalogPage = lazy(() => import("@/pages/storefront/catalog").then((m) => ({ default: m.CatalogPage })));
const ProductDetailPage = lazy(() => import("@/pages/storefront/product-detail").then((m) => ({ default: m.ProductDetailPage })));
const CartPage = lazy(() => import("@/pages/storefront/cart").then((m) => ({ default: m.CartPage })));
const CheckoutPage = lazy(() => import("@/pages/storefront/checkout").then((m) => ({ default: m.CheckoutPage })));
const OrderSuccessPage = lazy(() => import("@/pages/storefront/order-success").then((m) => ({ default: m.OrderSuccessPage })));
const OrdersPage = lazy(() => import("@/pages/account/orders").then((m) => ({ default: m.OrdersPage })));
const OrderDetailPage = lazy(() => import("@/pages/account/order-detail").then((m) => ({ default: m.OrderDetailPage })));
const ProfilePage = lazy(() => import("@/pages/account/profile").then((m) => ({ default: m.ProfilePage })));
const NotificationsPage = lazy(() => import("@/pages/account/notifications").then((m) => ({ default: m.NotificationsPage })));
const AdminDashboardPage = lazy(() => import("@/pages/admin/dashboard").then((m) => ({ default: m.AdminDashboardPage })));
const AdminProductsPage = lazy(() => import("@/pages/admin/products").then((m) => ({ default: m.AdminProductsPage })));
const AdminProductFormPage = lazy(() => import("@/pages/admin/product-form").then((m) => ({ default: m.AdminProductFormPage })));
const AdminInventoryPage = lazy(() => import("@/pages/admin/inventory").then((m) => ({ default: m.AdminInventoryPage })));
const AdminOrdersPage = lazy(() => import("@/pages/admin/orders").then((m) => ({ default: m.AdminOrdersPage })));
const AdminUsersPage = lazy(() => import("@/pages/admin/users").then((m) => ({ default: m.AdminUsersPage })));
const NotFoundPage = lazy(() => import("@/pages/not-found").then((m) => ({ default: m.NotFoundPage })));

export default function App() {
  return (
    <Routes>
      {/* Auth */}
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>

      {/* Storefront */}
      <Route element={<StorefrontLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/products" element={<CatalogPage />} />
        <Route path="/products/:id" element={<ProductDetailPage />} />
        <Route path="/categories/:id" element={<CatalogPage />} />
        <Route path="/cart" element={<CartPage />} />
        <Route
          path="/checkout"
          element={
            <ProtectedRoute>
              <CheckoutPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/order-success/:id"
          element={
            <ProtectedRoute>
              <OrderSuccessPage />
            </ProtectedRoute>
          }
        />

        {/* Account */}
        <Route
          path="/account"
          element={
            <ProtectedRoute>
              <Navigate to="/account/orders" replace />
            </ProtectedRoute>
          }
        />
        <Route
          path="/account/orders"
          element={
            <ProtectedRoute>
              <OrdersPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/account/orders/:id"
          element={
            <ProtectedRoute>
              <OrderDetailPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/account/profile"
          element={
            <ProtectedRoute>
              <ProfilePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/account/notifications"
          element={
            <ProtectedRoute>
              <NotificationsPage />
            </ProtectedRoute>
          }
        />
      </Route>

      {/* Admin */}
      <Route
        element={
          <ProtectedRoute roles={["ADMIN", "SELLER"]}>
            <AdminLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/admin" element={<AdminDashboardPage />} />
        <Route path="/admin/products" element={<AdminProductsPage />} />
        <Route path="/admin/products/new" element={<AdminProductFormPage />} />
        <Route path="/admin/products/:id" element={<AdminProductFormPage />} />
        <Route path="/admin/inventory" element={<AdminInventoryPage />} />
        <Route path="/admin/orders" element={<AdminOrdersPage />} />
        <Route path="/admin/users" element={<AdminUsersPage />} />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
