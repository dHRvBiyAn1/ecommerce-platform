import * as React from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { ColumnDef } from "@tanstack/react-table";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Spinner } from "@/components/ui/spinner";
import { ProductArt } from "@/components/product-art";
import { DataTable } from "@/components/data-table";
import { deleteProduct, myProducts, setProductActive } from "@/api/products";
import type { Product } from "@/api/types";
import { toast } from "sonner";
import { formatMoney } from "@/lib/utils";

/** Below this many units we colour the badge "warning"; 0 is "destructive". */
const LOW_STOCK_THRESHOLD = 5;

const StockBadge: React.FC<{ quantity: number }> = ({ quantity }) => {
  if (quantity <= 0) {
    return (
      <Badge variant="destructive" className="font-mono text-[10px] uppercase tracking-wider">
        Out of stock
      </Badge>
    );
  }
  if (quantity <= LOW_STOCK_THRESHOLD) {
    return (
      <Badge variant="warning" className="font-mono text-[10px] uppercase tracking-wider">
        Low · {quantity}
      </Badge>
    );
  }
  return (
    <Badge variant="success" className="font-mono text-[10px] uppercase tracking-wider">
      In stock · {quantity}
    </Badge>
  );
};

const APPROVAL_VARIANT = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "destructive",
} as const;

const ApprovalBadge: React.FC<{ status?: string | null }> = ({ status }) => {
  if (!status) return <span className="text-xs text-muted-foreground">—</span>;
  const v = (APPROVAL_VARIANT as any)[status] ?? "outline";
  return <Badge variant={v}>{status}</Badge>;
};

export const AdminProductsPage: React.FC = () => {
  const qc = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "my-products"],
    queryFn: () => myProducts({ size: 200 }),
  });

  const remove = useMutation({
    mutationFn: deleteProduct,
    onSuccess: () => {
      toast.success("Product deactivated");
      qc.invalidateQueries({ queryKey: ["admin", "my-products"] });
    },
    onError: (e: any) => toast.error(e?.message ?? "Delete failed"),
  });

  const toggle = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      setProductActive(id, active),
    onSuccess: (p) => {
      toast.success(`Product ${p.active ? "activated" : "deactivated"}`);
      qc.invalidateQueries({ queryKey: ["admin", "my-products"] });
    },
    onError: (e: any) => toast.error(e?.message ?? "Failed to toggle"),
  });

  const columns = React.useMemo<ColumnDef<Product>[]>(
    () => [
      {
        accessorKey: "name",
        header: "Product",
        cell: ({ row }) => {
          const p = row.original;
          return (
            <Link to={`/admin/products/${p.id}`} className="flex items-center gap-3">
              <ProductArt seed={p.sku || p.id} ratio="square" className="h-10 w-10" />
              <span className="font-medium hover:text-accent">{p.name}</span>
            </Link>
          );
        },
      },
      {
        accessorKey: "sku",
        header: "SKU",
        cell: ({ row }) => <span className="font-mono text-xs">{row.original.sku}</span>,
      },
      {
        accessorKey: "price",
        header: "Price",
        cell: ({ row }) => (
          <span className="font-mono text-xs">{formatMoney(row.original.price)}</span>
        ),
      },
      {
        accessorKey: "stockQuantity",
        header: "Stock",
        cell: ({ row }) => <StockBadge quantity={row.original.stockQuantity} />,
      },
      {
        accessorKey: "active",
        header: "State",
        cell: ({ row }) => (
          <Badge variant={row.original.active ? "success" : "secondary"}>
            {row.original.active ? "Active" : "Inactive"}
          </Badge>
        ),
      },
      {
        accessorKey: "approvalStatus",
        header: "Approval",
        cell: ({ row }) => <ApprovalBadge status={row.original.approvalStatus} />,
      },
      {
        id: "actions",
        header: "",
        enableSorting: false,
        cell: ({ row }) => {
          const p = row.original;
          return (
            <div className="inline-flex items-center gap-2">
              <Button
                size="sm"
                variant="outline"
                onClick={() => toggle.mutate({ id: p.id, active: !p.active })}
                disabled={toggle.isPending}
              >
                {p.active ? "Deactivate" : "Activate"}
              </Button>
              <Button asChild size="sm" variant="ghost">
                <Link to={`/admin/products/${p.id}`}>Edit</Link>
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => remove.mutate(p.id)}
                disabled={remove.isPending}
                aria-label="Delete product"
              >
                <Trash2 className="h-4 w-4 text-destructive" />
              </Button>
            </div>
          );
        },
      },
    ],
    [remove, toggle],
  );

  return (
    <div className="space-y-8">
      <header className="flex items-center justify-between">
        <div>
          <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Catalog</p>
          <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight">My products</h1>
        </div>
        <Button asChild variant="accent">
          <Link to="/admin/products/new">
            <Plus className="h-4 w-4" /> New product
          </Link>
        </Button>
      </header>

      {isLoading ? (
        <Spinner label="Loading…" />
      ) : (data?.content ?? []).length === 0 ? (
        <Card>
          <CardContent className="p-10 text-center">
            <p className="font-display text-lg">No products yet.</p>
            <p className="mt-2 text-sm text-muted-foreground">
              List your first item — it goes live in seconds.
            </p>
            <Button asChild variant="accent" className="mt-4">
              <Link to="/admin/products/new">Create product</Link>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <DataTable
          columns={columns}
          data={data?.content ?? []}
          searchColumn="name"
          searchPlaceholder="Search products by name…"
          pageSize={20}
          emptyState="No products match your search."
        />
      )}
    </div>
  );
};
