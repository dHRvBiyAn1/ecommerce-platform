import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { ColumnDef } from "@tanstack/react-table";
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
import { DataTable } from "@/components/ui/data-table";
import { listAllOrders, updateOrderStatus } from "@/api/orders";
import type { Order, OrderStatus } from "@/api/types";
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

const PAYMENT_VARIANT: Record<string, any> = {
  PENDING: "warning",
  PROCESSING: "info",
  COMPLETED: "success",
  FAILED: "destructive",
  REFUNDED: "secondary",
  PARTIALLY_REFUNDED: "secondary",
  CANCELLED: "secondary",
};

const PaymentStatusBadge: React.FC<{ status?: string | null }> = ({ status }) => {
  if (!status) return <span className="text-xs text-muted-foreground">—</span>;
  return <Badge variant={PAYMENT_VARIANT[status] ?? "outline"}>{status}</Badge>;
};

export const AdminOrdersPage: React.FC = () => {
  const qc = useQueryClient();
  const isAdmin = useAuthStore((s) => s.isAdmin());
  const { data, isLoading } = useQuery({
    queryKey: ["admin", "orders-list"],
    queryFn: () => listAllOrders(0, 200),
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

  const columns = React.useMemo<ColumnDef<Order>[]>(
    () => [
      {
        accessorKey: "orderNumber",
        header: "Order",
        cell: ({ row }) => (
          <Link
            to={`/account/orders/${row.original.id}`}
            className="font-mono text-xs hover:text-accent"
          >
            {row.original.orderNumber}
          </Link>
        ),
      },
      {
        id: "items",
        header: "Items",
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex -space-x-2">
            {row.original.items.slice(0, 3).map((it, i) => (
              <ProductArt
                key={it.productId + i}
                seed={it.sku || it.productId}
                ratio="square"
                className="h-8 w-8 ring-2 ring-background"
              />
            ))}
          </div>
        ),
      },
      {
        accessorKey: "userEmail",
        header: "Customer",
        cell: ({ row }) => (
          <div className="text-xs">
            <div className="font-medium">{row.original.userEmail}</div>
            <div className="text-muted-foreground">{row.original.userId.slice(0, 8)}…</div>
          </div>
        ),
      },
      {
        accessorKey: "totalAmount",
        header: "Total",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {formatMoney(row.original.totalAmount, row.original.currency)}
          </span>
        ),
      },
      {
        accessorKey: "createdAt",
        header: "Placed",
        cell: ({ row }) => (
          <span className="text-xs text-muted-foreground">
            {formatDate(row.original.createdAt)}
          </span>
        ),
      },
      {
        accessorKey: "status",
        header: "Status",
        cell: ({ row }) =>
          isAdmin ? (
            <Select
              value={row.original.status}
              onValueChange={(v) =>
                update.mutate({ id: row.original.id, status: v as OrderStatus })
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
            <Badge variant="info">{row.original.status}</Badge>
          ),
      },
      {
        accessorKey: "paymentStatus",
        header: "Payment",
        cell: ({ row }) => <PaymentStatusBadge status={row.original.paymentStatus} />,
      },
    ],
    [isAdmin, update],
  );

  if (isLoading) return <PageSpinner />;

  return (
    <div className="space-y-8">
      <header>
        <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Operations</p>
        <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight">Orders</h1>
      </header>

      {(data?.content ?? []).length === 0 ? (
        <Card>
          <CardContent className="p-10 text-center text-sm text-muted-foreground">
            No orders yet.
          </CardContent>
        </Card>
      ) : (
        <DataTable
          columns={columns}
          data={data?.content ?? []}
          searchColumn="orderNumber"
          searchPlaceholder="Search order # or customer…"
          pageSize={20}
          emptyState="No orders match your search."
        />
      )}
    </div>
  );
};
