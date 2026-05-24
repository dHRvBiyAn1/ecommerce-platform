import * as React from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listMyOrders, cancelOrder } from "@/api/orders";
import { ProductArt } from "@/components/product-art";
import { PageSpinner } from "@/components/ui/spinner";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { formatDate, formatMoney } from "@/lib/utils";

export const OrdersPage: React.FC = () => {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ["my-orders"],
    queryFn: () => listMyOrders(0, 40),
  });

  const cancel = useMutation({
    mutationFn: cancelOrder,
    onSuccess: () => {
      toast.success("Order cancelled");
      qc.invalidateQueries({ queryKey: ["my-orders"] });
    },
    onError: (e: any) => toast.error(e?.message ?? "Failed to cancel"),
  });

  if (isLoading) return <PageSpinner />;

  const orders = data?.content ?? [];

  return (
    <div className="container py-12">
      <header className="mb-8">
        <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Account</p>
        <h1 className="mt-2 font-display text-4xl font-semibold tracking-tight">My orders</h1>
      </header>

      {orders.length === 0 ? (
        <Card>
          <CardContent className="p-10 text-center">
            <p className="font-display text-lg">You haven't placed an order yet.</p>
            <Button asChild variant="accent" className="mt-4">
              <Link to="/products">Start shopping</Link>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <ul className="space-y-4">
          {orders.map((o) => (
            <li key={o.id}>
              <Card>
                <CardContent className="grid gap-4 p-5 sm:grid-cols-[auto_1fr_auto] sm:items-center">
                  <div className="flex -space-x-2">
                    {o.items.slice(0, 4).map((it, i) => (
                      <ProductArt
                        key={it.productId + i}
                        seed={it.sku || it.productId}
                        ratio="square"
                        className="h-12 w-12 ring-2 ring-background"
                      />
                    ))}
                    {o.items.length > 4 && (
                      <div className="grid h-12 w-12 place-items-center rounded-xl border bg-secondary text-xs">
                        +{o.items.length - 4}
                      </div>
                    )}
                  </div>
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <Link
                        to={`/account/orders/${o.id}`}
                        className="font-display text-base font-semibold hover:text-accent"
                      >
                        {o.orderNumber}
                      </Link>
                      <StatusBadge status={o.status} />
                      <Badge variant="outline">{o.paymentStatus ?? "—"}</Badge>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                      Placed {formatDate(o.createdAt)} · {o.items.length} item
                      {o.items.length === 1 ? "" : "s"}
                    </p>
                  </div>
                  <div className="flex flex-col items-end gap-2">
                    <span className="font-mono text-base font-semibold">
                      {formatMoney(o.totalAmount, o.currency)}
                    </span>
                    {(o.status === "PENDING" || o.status === "CONFIRMED") && (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => cancel.mutate(o.id)}
                      >
                        Cancel
                      </Button>
                    )}
                  </div>
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

const StatusBadge: React.FC<{ status: string }> = ({ status }) => {
  const variant = (
    {
      PENDING: "warning",
      CONFIRMED: "info",
      PROCESSING: "info",
      SHIPPED: "info",
      DELIVERED: "success",
      CANCELLED: "destructive",
      REFUNDED: "secondary",
    } as Record<string, any>
  )[status] ?? "secondary";
  return <Badge variant={variant}>{status}</Badge>;
};
