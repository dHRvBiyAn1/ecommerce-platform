import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ColumnDef } from "@tanstack/react-table";
import { toast } from "sonner";
import { Plus } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { DataTable } from "@/components/data-table";
import { addStock, listInventory } from "@/api/inventory";
import type { InventoryItem } from "@/api/types";
import { compact } from "@/lib/utils";

export const AdminInventoryPage: React.FC = () => {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ["admin", "inventory"],
    queryFn: () => listInventory(0, 200),
  });

  const restock = useMutation({
    mutationFn: ({ productId, qty }: { productId: string; qty: number }) =>
      addStock(productId, qty),
    onSuccess: () => {
      toast.success("Stock added");
      qc.invalidateQueries({ queryKey: ["admin", "inventory"] });
    },
    onError: (e: any) => toast.error(e?.message ?? "Failed"),
  });

  const columns = React.useMemo<ColumnDef<InventoryItem>[]>(
    () => [
      {
        accessorKey: "sku",
        header: "SKU",
        cell: ({ row }) => <span className="font-mono text-xs">{row.original.sku}</span>,
      },
      {
        accessorKey: "quantity",
        header: "On hand",
        cell: ({ row }) => <span className="font-mono text-xs">{compact(row.original.quantity)}</span>,
      },
      {
        accessorKey: "reservedQuantity",
        header: "Reserved",
        cell: ({ row }) => (
          <span className="font-mono text-xs">{compact(row.original.reservedQuantity)}</span>
        ),
      },
      {
        accessorKey: "availableQuantity",
        header: "Available",
        cell: ({ row }) => (
          <span className="font-mono text-xs">{compact(row.original.availableQuantity)}</span>
        ),
      },
      {
        accessorKey: "lowStockThreshold",
        header: "Threshold",
        cell: ({ row }) => (
          <span className="font-mono text-xs">{row.original.lowStockThreshold}</span>
        ),
      },
      {
        // Derived state for sort + filter; uses availableQuantity vs threshold.
        id: "status",
        header: "Status",
        accessorFn: (r) =>
          r.availableQuantity === 0
            ? "Out of stock"
            : r.quantity <= r.lowStockThreshold
              ? "Low"
              : "Healthy",
        cell: ({ getValue }) => {
          const label = getValue<string>();
          const variant: any =
            label === "Out of stock" ? "destructive" : label === "Low" ? "warning" : "success";
          return <Badge variant={variant}>{label}</Badge>;
        },
      },
      {
        id: "restock",
        header: "Add stock",
        enableSorting: false,
        cell: ({ row }) => (
          <RestockForm
            onSubmit={(qty) =>
              restock.mutate({ productId: row.original.productId, qty })
            }
          />
        ),
      },
    ],
    [restock],
  );

  return (
    <div className="space-y-8">
      <header>
        <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Operations</p>
        <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight">Inventory</h1>
      </header>

      {isLoading ? (
        <Spinner />
      ) : (data?.content ?? []).length === 0 ? (
        <Card>
          <CardContent className="p-10 text-center text-sm text-muted-foreground">
            No inventory rows yet. Each product needs an inventory item before it can be reserved.
          </CardContent>
        </Card>
      ) : (
        <DataTable
          columns={columns}
          data={data?.content ?? []}
          searchColumn="sku"
          searchPlaceholder="Search by SKU…"
          pageSize={20}
          emptyState="No inventory rows match your search."
        />
      )}
    </div>
  );
};

const RestockForm: React.FC<{ onSubmit: (qty: number) => void }> = ({ onSubmit }) => {
  const [qty, setQty] = React.useState<string>("10");
  return (
    <form
      className="inline-flex items-center gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        const n = Number(qty);
        if (n > 0) onSubmit(n);
      }}
    >
      <Input
        value={qty}
        onChange={(e) => setQty(e.target.value)}
        className="h-9 w-20"
        type="number"
        min={1}
        aria-label="Quantity to add"
      />
      <Button size="sm" type="submit" variant="outline">
        <Plus className="h-3.5 w-3.5" /> Add
      </Button>
    </form>
  );
};
