import * as React from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Spinner } from "@/components/ui/spinner";
import { ProductArt } from "@/components/product-art";
import { deleteProduct, myProducts, setProductActive } from "@/api/products";
import { useAuthStore } from "@/stores/auth";
import { toast } from "sonner";
import { formatMoney } from "@/lib/utils";

export const AdminProductsPage: React.FC = () => {
  const qc = useQueryClient();
  const isAdmin = useAuthStore((s) => s.isAdmin());

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "my-products"],
    queryFn: () => myProducts({ size: 50 }),
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
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin", "my-products"] }),
  });

  return (
    <div className="space-y-8">
      <header className="flex items-center justify-between">
        <div>
          <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Catalog</p>
          <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight">
            My products
          </h1>
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
        <Card>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b text-left text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  <tr>
                    <th className="p-4 font-medium">Product</th>
                    <th className="p-4 font-medium">SKU</th>
                    <th className="p-4 font-medium">Price</th>
                    <th className="p-4 font-medium">Stock</th>
                    <th className="p-4 font-medium">State</th>
                    <th className="p-4 font-medium" aria-label="actions" />
                  </tr>
                </thead>
                <tbody>
                  {(data?.content ?? []).map((p) => (
                    <tr key={p.id} className="border-b last:border-b-0 hover:bg-secondary/40">
                      <td className="p-4">
                        <Link
                          to={`/admin/products/${p.id}`}
                          className="flex items-center gap-3"
                        >
                          <ProductArt
                            seed={p.sku || p.id}
                            ratio="square"
                            className="h-10 w-10"
                          />
                          <span className="font-medium hover:text-accent">{p.name}</span>
                        </Link>
                      </td>
                      <td className="p-4 font-mono text-xs">{p.sku}</td>
                      <td className="p-4 font-mono text-xs">{formatMoney(p.price)}</td>
                      <td className="p-4 font-mono text-xs">{p.stockQuantity}</td>
                      <td className="p-4">
                        <Badge variant={p.active ? "success" : "secondary"}>
                          {p.active ? "Active" : "Inactive"}
                        </Badge>
                      </td>
                      <td className="p-4 text-right">
                        <div className="inline-flex items-center gap-2">
                          {isAdmin && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() =>
                                toggle.mutate({ id: p.id, active: !p.active })
                              }
                            >
                              {p.active ? "Deactivate" : "Activate"}
                            </Button>
                          )}
                          <Button asChild size="sm" variant="ghost">
                            <Link to={`/admin/products/${p.id}`}>Edit</Link>
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => remove.mutate(p.id)}
                            disabled={remove.isPending}
                          >
                            <Trash2 className="h-4 w-4 text-destructive" />
                          </Button>
                        </div>
                      </td>
                    </tr>
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
