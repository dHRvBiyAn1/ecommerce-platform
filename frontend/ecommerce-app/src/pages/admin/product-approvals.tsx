import * as React from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ColumnDef } from "@tanstack/react-table";
import { toast } from "sonner";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { ProductArt } from "@/components/product-art";
import { DataTable } from "@/components/ui/data-table";
import { approveProduct, listByApprovalStatus, rejectProduct } from "@/api/products";
import type { Product } from "@/api/types";
import { formatMoney } from "@/lib/utils";

type ApprovalFilter = "PENDING" | "APPROVED" | "REJECTED";

const STATUS_VARIANT = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "destructive",
} as const;

/**
 * Admin-only moderation queue. Lists products with the chosen approval
 * status via the dedicated {@code /api/v1/products/admin/by-status}
 * endpoint (the public listing only returns APPROVED).
 */
export const AdminProductApprovalsPage: React.FC = () => {
  const qc = useQueryClient();
  const [filter, setFilter] = React.useState<ApprovalFilter>("PENDING");
  const { data, isLoading } = useQuery({
    queryKey: ["admin", "product-approvals", filter],
    queryFn: () => listByApprovalStatus(filter, { size: 200 }),
  });

  const [rejectingProduct, setRejectingProduct] = React.useState<Product | null>(null);

  const approve = useMutation({
    mutationFn: approveProduct,
    onSuccess: (p) => {
      toast.success(`Approved ${p.name}`);
      qc.invalidateQueries({ queryKey: ["admin", "product-approvals"] });
      qc.invalidateQueries({ queryKey: ["products"] });
    },
    onError: (e: any) => toast.error(e?.message ?? "Approve failed"),
  });

  const reject = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => rejectProduct(id, reason),
    onSuccess: (p) => {
      toast.success(`Rejected ${p.name}`);
      qc.invalidateQueries({ queryKey: ["admin", "product-approvals"] });
      setRejectingProduct(null);
    },
    onError: (e: any) => toast.error(e?.message ?? "Reject failed"),
  });

  const filtered = data?.content ?? [];

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
              <div>
                <div className="font-medium hover:text-accent">{p.name}</div>
                <div className="text-[11px] text-muted-foreground">SKU {p.sku}</div>
              </div>
            </Link>
          );
        },
      },
      {
        accessorKey: "price",
        header: "Price",
        cell: ({ row }) => (
          <span className="font-mono text-xs">{formatMoney(row.original.price)}</span>
        ),
      },
      {
        accessorKey: "sellerId",
        header: "Seller",
        cell: ({ row }) => (
          <span className="font-mono text-[11px] text-muted-foreground">
            {row.original.sellerId.slice(0, 8)}…
          </span>
        ),
      },
      {
        accessorKey: "approvalStatus",
        header: "Status",
        cell: ({ row }) => {
          const s = row.original.approvalStatus ?? "—";
          const v = (STATUS_VARIANT as any)[s] ?? "outline";
          return <Badge variant={v}>{s}</Badge>;
        },
      },
      {
        id: "actions",
        header: "",
        enableSorting: false,
        cell: ({ row }) => {
          const p = row.original;
          if (p.approvalStatus !== "PENDING") {
            return p.rejectionReason ? (
              <span className="text-[11px] text-muted-foreground" title={p.rejectionReason}>
                Reason: {p.rejectionReason.slice(0, 40)}
                {p.rejectionReason.length > 40 ? "…" : ""}
              </span>
            ) : null;
          }
          return (
            <div className="inline-flex gap-2">
              <Button
                size="sm"
                variant="accent"
                onClick={() => approve.mutate(p.id)}
                disabled={approve.isPending}
              >
                Approve
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => setRejectingProduct(p)}
              >
                Reject
              </Button>
            </div>
          );
        },
      },
    ],
    [approve],
  );

  return (
    <div className="space-y-8">
      <header className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Operations</p>
          <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight">
            Product approvals
          </h1>
        </div>
        <div className="flex gap-2">
          {(["PENDING", "APPROVED", "REJECTED"] as ApprovalFilter[]).map((s) => (
            <Button
              key={s}
              size="sm"
              variant={filter === s ? "accent" : "outline"}
              onClick={() => setFilter(s)}
            >
              {s}
            </Button>
          ))}
        </div>
      </header>

      {isLoading ? (
        <Spinner />
      ) : filtered.length === 0 ? (
        <Card>
          <CardContent className="p-10 text-center text-sm text-muted-foreground">
            No {filter.toLowerCase()} products.
          </CardContent>
        </Card>
      ) : (
        <DataTable
          columns={columns}
          data={filtered}
          searchColumn="name"
          searchPlaceholder="Search by product name…"
          pageSize={20}
          emptyState="No products match your search."
        />
      )}

      <RejectDialog
        product={rejectingProduct}
        onClose={() => setRejectingProduct(null)}
        onConfirm={(reason) =>
          rejectingProduct && reject.mutate({ id: rejectingProduct.id, reason })
        }
        rejecting={reject.isPending}
      />
    </div>
  );
};

const RejectDialog: React.FC<{
  product: Product | null;
  onClose: () => void;
  onConfirm: (reason: string) => void;
  rejecting: boolean;
}> = ({ product, onClose, onConfirm, rejecting }) => {
  const [reason, setReason] = React.useState("");
  React.useEffect(() => setReason(""), [product]);
  if (!product) return null;
  return (
    <Dialog open={!!product} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reject product</DialogTitle>
          <DialogDescription>
            {product.name} · SKU {product.sku}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2 py-2">
          <Label>Reason (visible to the seller)</Label>
          <Textarea
            rows={4}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="What needs to change before this can be relisted?"
          />
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose} disabled={rejecting}>
            Cancel
          </Button>
          <Button
            variant="destructive"
            onClick={() => onConfirm(reason)}
            disabled={rejecting || reason.trim().length < 3}
          >
            {rejecting ? <Spinner /> : "Reject"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
};
