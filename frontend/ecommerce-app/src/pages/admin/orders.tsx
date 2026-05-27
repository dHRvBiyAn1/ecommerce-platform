import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PageSpinner } from "@/components/ui/spinner";
import { ProductArt } from "@/components/product-art";
import { listAllOrders, updateOrderStatus } from "@/api/orders";
import type { OrderStatus } from "@/api/types";
import { useAuthStore } from "@/stores/auth";
import { formatDate, formatMoney } from "@/lib/utils";

const STATUSES: OrderStatus[] = [
  "PENDING",
  "CONFIRMED",
  "PROCESSING",
  "SHIPPED",
  "DELIVERED",
  "CANCELLED",
  "REFUNDED",
];

export const AdminOrdersPage: React.FC = () => {
  const qc = useQueryClient();
  const isAdmin = useAuthStore((s) => s.isAdmin());
  const { data, isLoading } = useQuery({
    queryKey: ["admin", "orders-list"],
    queryFn: () => listAllOrders(0, 50),
  });

  const update = useMutation({
    mutationFn: ({ id, status }: { id: string; status: OrderStatus }) =>
      updateOrderStatus(id, status),
    onSuccess: () => {
      toast.success("Status updated");
      qc.invalidateQueries({ queryKey: ["admin", "orders-list"] });
    },
    onError: (e: any) => toast.error(e?.message ?? "Update failed"),
  });

  if (isLoading) return <PageSpinner />;

  return (
    <div className="space-y-8">
      <header>
        <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Operations</p>
        <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight">Orders</h1>
      </header>

      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b text-left text-xs uppercase tracking-[0.18em] text-muted-foreground">
                <tr>
                  <th className="p-4 font-medium">Order</th>
                  <th className="p-4 font-medium">Items</th>
                  <th className="p-4 font-medium">Customer</th>
                  <th className="p-4 font-medium">Total</th>
                  <th className="p-4 font-medium">Placed</th>
                  <th className="p-4 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {(data?.content ?? []).map((o) => (
                  <tr key={o.id} className="border-b last:border-b-0 hover:bg-secondary/40">
                    <td className="p-4">
                      <Link
                        to={`/account/orders/${o.id}`}
                        className="font-mono text-xs hover:text-accent"
                      >
                        {o.orderNumber}
                      </Link>
                    </td>
                    <td className="p-4">
                      <div className="flex -space-x-2">
                        {o.items.slice(0, 3).map((it, i) => (
                          <ProductArt
                            key={it.productId + i}
                            seed={it.sku || it.productId}
                            ratio="square"
                            className="h-8 w-8 ring-2 ring-background"
                          />
                        ))}
                      </div>
                    </td>
                    <td className="p-4">
                      <div className="text-xs">
                        <div className="font-medium">{o.userEmail}</div>
                        <div className="text-muted-foreground">{o.userId.slice(0, 8)}…</div>
                      </div>
                    </td>
                    <td className="p-4 font-mono text-xs">
                      {formatMoney(o.totalAmount, o.currency)}
                    </td>
                    <td className="p-4 text-xs text-muted-foreground">
                      {formatDate(o.createdAt)}
                    </td>
                    <td className="p-4">
                      {isAdmin ? (
                        <Select
                          value={o.status}
                          onValueChange={(v) =>
                            update.mutate({ id: o.id, status: v as OrderStatus })
                          }
                        >
                          <SelectTrigger className="h-8 w-[140px]">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {STATUSES.map((s) => (
                              <SelectItem key={s} value={s}>
                                {s}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      ) : (
                        <Badge variant="info">{o.status}</Badge>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};
