import * as React from "react";
import { NavLink } from "react-router-dom";
import { Home, Search, ShoppingBag, User2, Sparkles } from "lucide-react";
import { useCart } from "@/stores/cart";
import { cn } from "@/lib/utils";

const ITEMS = [
  { to: "/", label: "Home", icon: Home, end: true },
  { to: "/products", label: "Shop", icon: Search },
  { to: "/products?sort=createdAt,desc", label: "New", icon: Sparkles },
  { to: "/cart", label: "Bag", icon: ShoppingBag, badge: true },
  { to: "/account/orders", label: "Account", icon: User2 },
];

/**
 * Storefront mobile bottom navigation. Visible only at <md. Adds the
 * always-thumb-reachable nav pattern that modern ecommerce apps use.
 */
export const MobileBottomNav: React.FC = () => {
  const cartCount = useCart((s) => s.totalItems());

  return (
    <nav
      aria-label="Primary"
      className="fixed inset-x-0 bottom-0 z-30 border-t bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80 md:hidden pb-[env(safe-area-inset-bottom)]"
    >
      <ul className="grid grid-cols-5">
        {ITEMS.map((item) => (
          <li key={item.to}>
            <NavLink
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  "flex h-14 flex-col items-center justify-center gap-0.5 text-[11px] font-medium transition-colors",
                  isActive ? "text-accent" : "text-muted-foreground",
                )
              }
            >
              <span className="relative">
                <item.icon className="h-5 w-5" />
                {item.badge && cartCount > 0 && (
                  <span className="absolute -right-2 -top-1.5 grid h-4 min-w-[1rem] place-items-center rounded-full bg-accent px-1 text-[9px] font-bold text-accent-foreground">
                    {cartCount > 99 ? "99+" : cartCount}
                  </span>
                )}
              </span>
              <span>{item.label}</span>
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
};
