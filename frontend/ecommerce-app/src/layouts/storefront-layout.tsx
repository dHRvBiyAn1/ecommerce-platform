import * as React from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  Bell,
  LogOut,
  Menu,
  Package,
  Search,
  Settings,
  ShoppingBag,
  Store,
  User2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Sheet,
  SheetBody,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { useAuthStore } from "@/stores/auth";
import { useCart } from "@/stores/cart";
import { logout } from "@/api/auth";
import { ThemeToggle } from "@/components/theme-toggle";
import { SkipToContent } from "@/components/skip-to-content";
import { MobileBottomNav } from "@/components/mobile-bottom-nav";
import { cn, initials } from "@/lib/utils";
import { toast } from "sonner";

const NAV = [
  { to: "/products", label: "Shop" },
  { to: "/products?sort=createdAt,desc", label: "New" },
  { to: "/products?minPrice=0&maxPrice=999", label: "Sale" },
];

export const StorefrontLayout: React.FC = () => {
  const brand = import.meta.env.VITE_BRAND_NAME ?? "Étoile";
  const navigate = useNavigate();
  const [search, setSearch] = React.useState("");
  const [mobileOpen, setMobileOpen] = React.useState(false);

  const isAuthed = useAuthStore((s) => s.isAuthenticated());
  const user = useAuthStore((s) => s.user);
  const isSeller = useAuthStore((s) => s.isSeller());
  const clear = useAuthStore((s) => s.clear);
  const cartCount = useCart((s) => s.totalItems());

  function onSearch(e: React.FormEvent) {
    e.preventDefault();
    if (!search.trim()) return;
    setMobileOpen(false);
    navigate(`/products?keyword=${encodeURIComponent(search.trim())}`);
  }

  async function onLogout() {
    try {
      await logout();
    } catch {
      /* swallow — clear locally anyway */
    } finally {
      clear();
      toast.success("Signed out");
      navigate("/");
    }
  }

  return (
    <div className="flex min-h-screen flex-col">
      <SkipToContent />

      {/* Top announcement marquee — editorial flourish */}
      <div className="overflow-hidden border-b bg-primary text-primary-foreground" aria-hidden="true">
        <div className="flex animate-marquee whitespace-nowrap py-1.5 text-xs uppercase tracking-[0.18em]">
          {Array.from({ length: 6 }).map((_, i) => (
            <span key={i} className="mx-8 inline-flex items-center gap-2">
              <span className="text-accent">★</span> Free shipping over ₹499
              <span className="text-accent">·</span> 30-day returns
              <span className="text-accent">·</span> COD on prepaid orders
            </span>
          ))}
        </div>
      </div>

      <header className="sticky top-0 z-40 border-b bg-background/85 backdrop-blur supports-[backdrop-filter]:bg-background/70">
        <div className="container flex h-16 items-center gap-3 sm:gap-6">
          <Link to="/" className="flex items-center gap-2 shrink-0" aria-label={`${brand} home`}>
            <span className="grid h-9 w-9 place-items-center rounded-md bg-primary font-display text-lg font-bold text-accent">
              {brand[0]}
            </span>
            <span className="hidden font-display text-xl font-semibold tracking-tight sm:inline">
              {brand}
            </span>
          </Link>

          <nav className="hidden items-center gap-1 md:flex" aria-label="Primary">
            {NAV.map((n) => (
              <NavLink
                key={n.label}
                to={n.to}
                className={({ isActive }) =>
                  cn(
                    "rounded-md px-3 py-2 text-sm font-medium transition-colors hover:bg-secondary",
                    isActive && "text-foreground",
                  )
                }
              >
                {n.label}
              </NavLink>
            ))}
          </nav>

          <form
            onSubmit={onSearch}
            role="search"
            className="ml-auto hidden flex-1 items-center md:flex md:max-w-md"
          >
            <label htmlFor="site-search" className="sr-only">Search products</label>
            <div className="relative w-full">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="site-search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search products, SKUs, sellers"
                className="pl-9"
              />
            </div>
          </form>

          <div className="ml-auto flex items-center gap-1 md:ml-0">
            <ThemeToggle className="hidden md:inline-flex" />

            <Button
              variant="ghost"
              size="icon"
              asChild
              aria-label="Notifications"
              className="hidden md:inline-flex"
            >
              <Link to="/account/notifications">
                <Bell className="h-5 w-5" />
              </Link>
            </Button>

            <Button
              variant="ghost"
              size="icon"
              asChild
              aria-label={`Cart, ${cartCount} item${cartCount === 1 ? "" : "s"}`}
              className="relative"
            >
              <Link to="/cart">
                <ShoppingBag className="h-5 w-5" />
                {cartCount > 0 && (
                  <span className="absolute -right-1 -top-1 grid h-5 min-w-[1.25rem] place-items-center rounded-full bg-accent px-1 text-[10px] font-bold text-accent-foreground">
                    {cartCount}
                  </span>
                )}
              </Link>
            </Button>

            {isAuthed ? (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="icon" aria-label="Account menu">
                    <span className="grid h-7 w-7 place-items-center rounded-full bg-primary text-xs font-semibold text-primary-foreground">
                      {initials(user?.displayName ?? user?.email)}
                    </span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-56">
                  <DropdownMenuLabel>
                    <div className="flex flex-col">
                      <span className="text-sm font-semibold normal-case tracking-normal">
                        {user?.displayName ?? "Account"}
                      </span>
                      <span className="truncate text-xs font-normal lowercase tracking-normal text-muted-foreground">
                        {user?.email}
                      </span>
                    </div>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <Link to="/account/orders">
                      <Package className="h-4 w-4" /> My orders
                    </Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem asChild>
                    <Link to="/account/profile">
                      <User2 className="h-4 w-4" /> Profile
                    </Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem asChild>
                    <Link to="/account/notifications">
                      <Bell className="h-4 w-4" /> Notifications
                    </Link>
                  </DropdownMenuItem>
                  {isSeller && (
                    <>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem asChild>
                        <Link to="/admin">
                          <Settings className="h-4 w-4" /> Seller / admin
                        </Link>
                      </DropdownMenuItem>
                    </>
                  )}
                  {!isSeller && (
                    <>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem asChild>
                        <Link to="/account/become-seller">
                          <Store className="h-4 w-4" /> Become a seller
                        </Link>
                      </DropdownMenuItem>
                    </>
                  )}
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onClick={onLogout}>
                    <LogOut className="h-4 w-4" /> Sign out
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : (
              <div className="hidden items-center gap-2 sm:flex">
                <Button asChild variant="ghost" size="sm">
                  <Link to="/login">Sign in</Link>
                </Button>
                <Button asChild variant="accent" size="sm">
                  <Link to="/register">Join</Link>
                </Button>
              </div>
            )}

            <Button
              variant="ghost"
              size="icon"
              className="md:hidden"
              onClick={() => setMobileOpen(true)}
              aria-label="Open menu"
            >
              <Menu className="h-5 w-5" />
            </Button>
          </div>
        </div>
      </header>

      {/* Mobile sheet menu */}
      <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetContent side="left">
          <SheetHeader>
            <SheetTitle>{brand}</SheetTitle>
          </SheetHeader>
          <SheetBody className="space-y-6">
            <form onSubmit={onSearch} role="search">
              <label htmlFor="m-search" className="sr-only">Search</label>
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  id="m-search"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Search"
                  className="pl-9"
                />
              </div>
            </form>
            <nav aria-label="Primary mobile" className="flex flex-col gap-1">
              {NAV.map((n) => (
                <Link
                  key={n.label}
                  to={n.to}
                  onClick={() => setMobileOpen(false)}
                  className="rounded-md px-3 py-2.5 text-base font-medium hover:bg-secondary"
                >
                  {n.label}
                </Link>
              ))}
            </nav>
            <div className="border-t pt-4">
              <ThemeToggle className="w-full justify-start gap-3 px-3" />
            </div>
            {!isAuthed && (
              <div className="border-t pt-4 grid grid-cols-2 gap-3">
                <Button asChild variant="outline">
                  <Link to="/login" onClick={() => setMobileOpen(false)}>Sign in</Link>
                </Button>
                <Button asChild variant="accent">
                  <Link to="/register" onClick={() => setMobileOpen(false)}>Join</Link>
                </Button>
              </div>
            )}
          </SheetBody>
        </SheetContent>
      </Sheet>

      <main id="main-content" tabIndex={-1} className="flex-1 pb-16 md:pb-0">
        <Outlet />
      </main>

      <footer className="mt-16 border-t bg-primary text-primary-foreground md:mt-24">
        <div className="container grid gap-8 py-12 md:grid-cols-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="grid h-9 w-9 place-items-center rounded-md bg-accent font-display text-lg font-bold text-accent-foreground">
                {brand[0]}
              </span>
              <span className="font-display text-xl font-semibold tracking-tight">
                {brand}
              </span>
            </div>
            <p className="mt-4 max-w-xs text-sm text-primary-foreground/65">
              {import.meta.env.VITE_BRAND_TAGLINE ??
                "A modern marketplace for considered things."}
            </p>
          </div>
          <FooterCol
            title="Shop"
            links={[
              ["New arrivals", "/products?sort=createdAt,desc"],
              ["All products", "/products"],
              ["Sellers", "/products"],
            ]}
          />
          <FooterCol
            title="Account"
            links={[
              ["Sign in", "/login"],
              ["Create account", "/register"],
              ["Orders", "/account/orders"],
              ["Profile", "/account/profile"],
            ]}
          />
          <FooterCol
            title="Help"
            links={[
              ["Returns", "#"],
              ["Shipping", "#"],
              ["Contact", "#"],
            ]}
          />
        </div>
        <div className="border-t border-primary-foreground/10">
          <div className="container flex flex-col items-start justify-between gap-2 py-6 text-xs uppercase tracking-[0.18em] text-primary-foreground/50 md:flex-row md:items-center">
            <span>© {new Date().getFullYear()} {brand}.</span>
            <span>All goods are real. All transactions secured.</span>
          </div>
        </div>
      </footer>

      <MobileBottomNav />
    </div>
  );
};

const FooterCol: React.FC<{ title: string; links: [string, string][] }> = ({
  title,
  links,
}) => (
  <div>
    <h4 className="mb-4 font-display text-sm font-semibold uppercase tracking-[0.18em] text-primary-foreground/50">
      {title}
    </h4>
    <ul className="space-y-2 text-sm">
      {links.map(([label, href]) => (
        <li key={label}>
          <Link
            to={href}
            className="text-primary-foreground/85 transition-colors hover:text-accent"
          >
            {label}
          </Link>
        </li>
      ))}
    </ul>
  </div>
);
