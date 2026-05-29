import * as React from "react";
import { useAuthStore } from "@/stores/auth";
import { UnauthorizedPage } from "./unauthorized";

interface CustomerOnlyRouteProps {
  children: React.ReactNode;
}

export const CustomerOnlyRoute: React.FC<CustomerOnlyRouteProps> = ({ children }) => {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const isSeller = useAuthStore((s) => s.isSeller());
  const isAdmin = useAuthStore((s) => s.isAdmin());

  if (isAuthenticated && (isSeller || isAdmin)) {
    return <UnauthorizedPage />;
  }
  return <>{children}</>;
};
