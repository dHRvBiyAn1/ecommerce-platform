import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { addStock, listInventory } from "@/api/inventory";
import { compact } from "@/lib/utils";

export const AdminInventoryPage: React.FC = () => {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ["admin", "inventory"],
    queryFn: () => listInventory(0, 50),
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
        <Card>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b text-left text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  <tr>
                    <th className="p-4 font-medium">SKU</th>
                    <th className="p-4 font-medium">On hand</th>
                    <th className="p-4 font-medium">Reserved</th>
                    <th className="p-4 font-medium">Available</th>
                    <th className="p-4 font-medium">Threshold</th>
                    <th className="p-4 font-medium">Status</th>
                    <th className="p-4 font-medium">Add stock</th>
                  </tr>
                </thead>
                <tbody>
                  {(data?.content ?? []).map((row) => (
                    <InventoryRow key={row.id} row={row} onRestock={(qty) => restock.mutate({ productId: row.productId, qty })} />
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
};

const InventoryRow: React.FC<{
  row: import("@/api/types").InventoryItem;
  onRestock: (qty: number) => void;
}> = ({ row, onRestock }) => {
  const [qty, setQty] = React.useState<string>("10");
  const status =
    row.availableQuantity === 0
      ? { label: "Out of stock", variant: "destructive" as const }
      : row.quantity <= row.lowStockThreshold
      ? { label: "Low", variant: "warning" as const }
      : { label: "Healthy", variant: "success" as const };

  return (
    <tr className="border-b last:border-b-0 hover:bg-secondary/40">
      <td className="p-4 font-mono text-xs">{row.sku}</td>
      <td className="p-4 font-mono text-xs">{compact(row.quantity)}</td>
      <td className="p-4 font-mono text-xs">{compact(row.reservedQuantity)}</td>
      <td className="p-4 font-mono text-xs">{compact(row.availableQuantity)}</td>
      <td className="p-4 font-mono text-xs">{row.lowStockThreshold}</td>
      <td className="p-4">
        <Badge variant={status.variant}>{status.label}</Badge>
      </td>
      <td className="p-4">
        <form
          className="inline-flex items-center gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            const n = Number(qty);
            if (n > 0) onRestock(n);
          }}
        >
          <Input
            value={qty}
            onChange={(e) => setQty(e.target.value)}
            className="h-9 w-20"
            type="number"
            min={1}
          />
          <Button size="sm" type="submit" variant="outline">
            <Plus className="h-3.5 w-3.5" /> Add
          </Button>
        </form>
      </td>
    </tr>
  );
};
