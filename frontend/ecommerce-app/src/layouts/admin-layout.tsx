import * as React from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  ChevronLeft,
  ClipboardCheck,
  LayoutDashboard,
  ListChecks,
  LogOut,
  Menu,
  Package,
  ShieldCheck,
  ShoppingBag,
  Users,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth";
import { useCart } from "@/stores/cart";
import { useQueryClient } from "@tanstack/react-query";
import { logout } from "@/api/auth";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  Sheet,
  SheetBody,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { ThemeToggle } from "@/components/theme-toggle";
import { SkipToContent } from "@/components/skip-to-content";
import { cn, initials } from "@/lib/utils";

const ITEMS: {
  to: string;
  label: string;
  icon: React.ComponentType<any>;
  admin?: boolean;
}[] = [
  { to: "/admin", label: "Overview", icon: LayoutDashboard },
  { to: "/admin/products", label: "Products", icon: Package },
  { to: "/admin/inventory", label: "Inventory", icon: ListChecks },
  { to: "/admin/orders", label: "Orders", icon: ShoppingBag },
  { to: "/admin/users", label: "Users", icon: Users, admin: true },
  { to: "/admin/seller-applications", label: "Seller applications", icon: ShieldCheck, admin: true },
  { to: "/admin/product-approvals", label: "Product approvals", icon: ClipboardCheck, admin: true },
];

export const AdminLayout: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const isAdmin = useAuthStore((s) => s.isAdmin());
  const clearAuth = useAuthStore((s) => s.clear);
  const clearCart = useCart((s) => s.clear);
  const [mobileOpen, setMobileOpen] = React.useState(false);

  const items = ITEMS.filter((i) => !i.admin || isAdmin);

  async function onLogout() {
    try {
      await logout();
    } catch {
      /* ignore */
    } finally {
      clearAuth();
      await clearCart();
      queryClient.clear();
      navigate("/");
    }
  }

  const SidebarBody = (
    <>
      <div className="px-6 pt-6">
        <Link
          to="/"
          className="inline-flex items-center gap-2 text-xs uppercase tracking-[0.18em] text-primary-foreground/60 transition-colors hover:text-accent"
        >
          <ChevronLeft className="h-3 w-3" /> Back to store
        </Link>
        <div className="mt-3 font-display text-2xl font-semibold tracking-tight">
          Console
        </div>
        <Separator className="my-4 bg-primary-foreground/10" />
      </div>

      <nav className="flex-1 px-3" aria-label="Admin">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === "/admin"}
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) =>
              cn(
                "mb-1 flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                isActive
                  ? "bg-accent text-accent-foreground"
                  : "text-primary-foreground/70 hover:bg-primary-foreground/5 hover:text-primary-foreground",
              )
            }
          >
            <item.icon className="h-4 w-4" />
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-primary-foreground/10 p-4">
        <div className="flex items-center gap-3">
          <span className="grid h-9 w-9 place-items-center rounded-full bg-accent font-display text-sm font-bold text-accent-foreground">
            {initials(user?.displayName ?? user?.email)}
          </span>
          <div className="min-w-0">
            <div className="truncate text-sm font-medium">
              {user?.displayName ?? "—"}
            </div>
            <div className="truncate text-xs text-primary-foreground/50">
              {user?.email}
            </div>
          </div>
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={onLogout}
          className="mt-3 w-full justify-start text-primary-foreground/70 hover:bg-primary-foreground/10 hover:text-primary-foreground"
        >
          <LogOut className="h-4 w-4" /> Sign out
        </Button>
      </div>
    </>
  );

  return (
    <div className="grid min-h-screen lg:grid-cols-[16rem_1fr]">
      <SkipToContent />

      {/* Desktop sidebar */}
      <aside className="hidden border-r bg-primary text-primary-foreground lg:flex lg:flex-col">
        {SidebarBody}
      </aside>

      {/* Mobile top bar + drawer */}
      <div className="lg:hidden">
        <header className="sticky top-0 z-40 flex h-14 items-center justify-between border-b bg-background px-4">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setMobileOpen(true)}
            aria-label="Open menu"
            className="-ml-2"
          >
            <Menu className="h-5 w-5" />
          </Button>
          <span className="font-display text-base font-semibold">Console</span>
          <ThemeToggle />
        </header>

        <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
          <SheetContent side="left" className="bg-primary p-0 text-primary-foreground">
            <SheetHeader className="border-b border-primary-foreground/10 bg-primary text-primary-foreground">
              <SheetTitle className="text-primary-foreground">Console</SheetTitle>
            </SheetHeader>
            <SheetBody className="flex flex-col p-0 px-0">
              <div className="flex h-full flex-col">{SidebarBody}</div>
            </SheetBody>
          </SheetContent>
        </Sheet>
      </div>

      <main id="main-content" tabIndex={-1} className="bg-background">
        <div className="mx-auto w-full max-w-7xl p-4 sm:p-6 lg:p-10">
          <Outlet />
        </div>
      </main>
    </div>
  );
};
