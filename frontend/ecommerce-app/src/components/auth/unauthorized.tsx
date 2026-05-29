import * as React from "react";
import { useNavigate } from "react-router-dom";
import { ShieldAlert, ArrowLeft, LogOut } from "lucide-react";
import { useAuthStore } from "@/stores/auth";
import { useCart } from "@/stores/cart";
import { logout } from "@/api/auth";
import { Button } from "@/components/ui/button";

export const UnauthorizedPage: React.FC = () => {
  const navigate = useNavigate();
  const clearAuth = useAuthStore((s) => s.clear);
  const clearCart = useCart((s) => s.clear);
  const isSeller = useAuthStore((s) => s.isSeller());

  async function onLogout() {
    try {
      await logout();
    } catch {
      // ignore
    } finally {
      clearAuth();
      await clearCart();
      navigate("/login");
    }
  }

  return (
    <div className="relative flex min-h-[80vh] items-center justify-center overflow-hidden p-6">
      {/* Premium ambient glow background */}
      <div className="absolute inset-0 -z-10 flex items-center justify-center">
        <div className="h-96 w-96 rounded-full bg-destructive/10 blur-3xl" />
        <div className="ml-24 h-72 w-72 rounded-full bg-accent/5 blur-3xl" />
      </div>

      {/* Glassmorphic Container */}
      <div className="w-full max-w-md border border-white/10 bg-background/40 p-8 text-center shadow-2xl backdrop-blur-xl rounded-2xl md:p-10">
        <div className="mx-auto mb-6 grid h-16 w-16 place-items-center bg-destructive/10 text-destructive rounded-full">
          <ShieldAlert className="h-8 w-8" />
        </div>

        <h1 className="font-display text-2xl font-bold tracking-tight text-foreground md:text-3xl">
          Access Restricted
        </h1>

        <p className="mt-4 text-sm leading-relaxed text-muted-foreground">
          Sellers and administrators are restricted from using storefront shopping pages. Please use the management console to manage your catalog and orders.
        </p>

        <div className="mt-8 flex flex-col gap-3">
          {isSeller ? (
            <Button onClick={() => navigate("/admin")} variant="accent" className="w-full">
              Go to Console
            </Button>
          ) : (
            <Button onClick={() => navigate("/")} variant="outline" className="w-full gap-2">
              <ArrowLeft className="h-4 w-4" /> Back to Home
            </Button>
          )}

          <Button onClick={onLogout} variant="ghost" className="w-full gap-2 text-muted-foreground hover:text-foreground">
            <LogOut className="h-4 w-4" /> Sign in as different user
          </Button>
        </div>
      </div>
    </div>
  );
};
