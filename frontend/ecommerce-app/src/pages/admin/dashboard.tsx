import * as React from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowUpRight,
  Box,
  CheckCircle2,
  ChevronRight,
  Package,
  ShoppingBag,
  TrendingUp,
} from "lucide-react";
import { useAuthStore } from "@/stores/auth";
import { Card, CardContent } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { listProducts, myProducts } from "@/api/products";
import { listMyOrders } from "@/api/orders";
import { getLowStock } from "@/api/inventory";
import { formatMoney, formatDate } from "@/lib/utils";

export const AdminDashboardPage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const isAdmin = useAuthStore((s) => s.isAdmin());

  const products = useQuery({
    queryKey: ["admin", "products", isAdmin ? "all" : "mine"],
    queryFn: () => (isAdmin ? listProducts({ size: 5 }) : myProducts({ size: 5 })),
  });
  const orders = useQuery({ queryKey: ["admin", "orders"], queryFn: () => listMyOrders(0, 5) });
  const lowStock = useQuery({
    queryKey: ["admin", "low-stock"],
    queryFn: getLowStock,
    enabled: isAdmin,
  });

  return (
    <div className="space-y-10">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
            Console
          </p>
          <h1 className="mt-2 font-display text-4xl font-semibold tracking-tight">
            Hello, {user?.displayName ?? "there"}
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Today's overview of {isAdmin ? "the platform" : "your store"}.
          </p>
        </div>
      </header>

      {/* KPI cards */}
      <section className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Kpi
          icon={Package}
          label="Active products"
          value={products.data?.totalElements ?? "—"}
          link="/admin/products"
        />
        <Kpi
          icon={ShoppingBag}
          label="Recent orders"
          value={orders.data?.totalElements ?? "—"}
          link="/admin/orders"
        />
        <Kpi
          icon={Box}
          label="Low stock"
          value={lowStock.data?.length ?? (isAdmin ? "—" : 0)}
          tone={(lowStock.data?.length ?? 0) > 0 ? "warning" : "default"}
          link="/admin/inventory"
        />
        <Kpi icon={TrendingUp} label="Revenue (30d)" value="—" />
      </section>

      <section className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardContent className="p-6">
            <Header title="Recent products" link="/admin/products" />
            {products.isLoading ? (
              <Spinner />
            ) : (products.data?.content ?? []).length === 0 ? (
              <Empty
                primary="No products yet"
                cta={{ to: "/admin/products/new", label: "Add product" }}
              />
            ) : (
              <ul className="divide-y">
                {products.data!.content.slice(0, 5).map((p) => (
                  <li key={p.id} className="flex items-center justify-between py-3">
                    <div className="min-w-0">
                      <Link
                        to={`/admin/products/${p.id}`}
                        className="truncate font-medium hover:text-accent"
                      >
                        {p.name}
                      </Link>
                      <div className="mt-0.5 text-xs text-muted-foreground">
                        SKU {p.sku} · stock {p.stockQuantity}
                      </div>
                    </div>
                    <span className="font-mono text-sm">{formatMoney(p.price)}</span>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-6">
            <Header title="Latest orders" link="/admin/orders" />
            {orders.isLoading ? (
              <Spinner />
            ) : (orders.data?.content ?? []).length === 0 ? (
              <Empty primary="No orders yet" />
            ) : (
              <ul className="divide-y">
                {orders.data!.content.slice(0, 5).map((o) => (
                  <li key={o.id} className="flex items-center justify-between py-3">
                    <div>
                      <Link
                        to={`/account/orders/${o.id}`}
                        className="font-medium hover:text-accent"
                      >
                        {o.orderNumber}
                      </Link>
                      <div className="mt-0.5 text-xs text-muted-foreground">
                        {formatDate(o.createdAt)} · {o.items.length} item
                        {o.items.length === 1 ? "" : "s"}
                      </div>
                    </div>
                    <span className="font-mono text-sm">
                      {formatMoney(o.totalAmount, o.currency)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </section>
    </div>
  );
};

const Kpi: React.FC<{
  icon: React.ComponentType<any>;
  label: string;
  value: React.ReactNode;
  tone?: "default" | "warning";
  link?: string;
}> = ({ icon: Icon, label, value, tone, link }) => {
  const card = (
    <Card className="transition-colors hover:border-accent">
      <CardContent className="flex items-start justify-between p-5">
        <div>
          <div className="flex items-center gap-2 text-xs uppercase tracking-[0.18em] text-muted-foreground">
            <Icon className="h-3.5 w-3.5" />
            {label}
          </div>
          <p
            className={`mt-3 font-display text-3xl font-semibold tracking-tight ${
              tone === "warning" ? "text-amber-600" : ""
            }`}
          >
            {value}
          </p>
        </div>
        {link && (
          <ChevronRight className="h-4 w-4 text-muted-foreground" />
        )}
      </CardContent>
    </Card>
  );
  return link ? <Link to={link}>{card}</Link> : card;
};

const Header: React.FC<{ title: string; link?: string }> = ({ title, link }) => (
  <div className="mb-3 flex items-center justify-between">
    <h2 className="font-display text-lg font-semibold tracking-tight">{title}</h2>
    {link && (
      <Link to={link} className="inline-flex items-center text-xs text-muted-foreground hover:text-accent">
        View all <ArrowUpRight className="h-3 w-3" />
      </Link>
    )}
  </div>
);

const Empty: React.FC<{ primary: string; cta?: { to: string; label: string } }> = ({
  primary,
  cta,
}) => (
  <div className="rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground">
    <CheckCircle2 className="mx-auto h-6 w-6 text-muted-foreground" />
    <p className="mt-2">{primary}</p>
    {cta && (
      <Link to={cta.to} className="mt-3 inline-block text-accent underline-offset-4 hover:underline">
        {cta.label}
      </Link>
    )}
  </div>
);
