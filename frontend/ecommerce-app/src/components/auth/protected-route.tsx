import * as React from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuthStore } from "@/stores/auth";
import { UnauthorizedPage } from "./unauthorized";

interface ProtectedRouteProps {
  children: React.ReactNode;
  /** If set, requires at least one of these roles. Otherwise, just authentication. */
  roles?: string[];
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, roles }) => {
  const location = useLocation();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const hasRole = useAuthStore((s) => s.hasRole);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (roles && roles.length > 0 && !roles.some((r) => hasRole(r))) {
    return <UnauthorizedPage />;
  }
  return <>{children}</>;
};
