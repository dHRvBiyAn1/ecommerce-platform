import * as React from "react";
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
import { DataTable } from "@/components/data-table";
import {
  approveSellerApplication,
  listSellerApplications,
  rejectSellerApplication,
  type SellerApplication,
  type SellerApplicationStatus,
} from "@/api/seller";
import { formatDate } from "@/lib/utils";

const STATUS_VARIANT: Record<SellerApplicationStatus, any> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "destructive",
};

export const AdminSellerApplicationsPage: React.FC = () => {
  const qc = useQueryClient();
  const [statusFilter, setStatusFilter] = React.useState<SellerApplicationStatus | "ALL">(
    "PENDING",
  );
  const { data, isLoading } = useQuery({
    queryKey: ["admin", "seller-applications", statusFilter],
    queryFn: () =>
      listSellerApplications(statusFilter === "ALL" ? undefined : statusFilter, 0, 200),
  });

  const [rejectingApp, setRejectingApp] = React.useState<SellerApplication | null>(null);
  const [viewingApp, setViewingApp] = React.useState<SellerApplication | null>(null);

  const approve = useMutation({
    mutationFn: approveSellerApplication,
    onSuccess: (a) => {
      toast.success(`Approved ${a.businessName}`);
      qc.invalidateQueries({ queryKey: ["admin", "seller-applications"] });
    },
    onError: (e: any) => toast.error(e?.message ?? "Approve failed"),
  });

  const reject = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      rejectSellerApplication(id, reason),
    onSuccess: (a) => {
      toast.success(`Rejected ${a.businessName}`);
      qc.invalidateQueries({ queryKey: ["admin", "seller-applications"] });
      setRejectingApp(null);
    },
    onError: (e: any) => toast.error(e?.message ?? "Reject failed"),
  });

  const columns = React.useMemo<ColumnDef<SellerApplication>[]>(
    () => [
      {
        accessorKey: "businessName",
        header: "Business",
        cell: ({ row }) => (
          <button
            className="text-left hover:text-accent"
            onClick={() => setViewingApp(row.original)}
          >
            <div className="font-medium">{row.original.businessName}</div>
            <div className="text-[11px] text-muted-foreground">
              GSTIN: {row.original.gstin ?? "—"}
            </div>
          </button>
        ),
      },
      {
        accessorKey: "userEmail",
        header: "Applicant",
        cell: ({ row }) => (
          <div className="text-xs">
            <div className="font-medium">{row.original.userEmail}</div>
            <div className="text-muted-foreground">
              {row.original.userDisplayName ?? "—"}
            </div>
          </div>
        ),
      },
      {
        accessorKey: "contactPhone",
        header: "Phone",
        cell: ({ row }) => (
          <span className="font-mono text-xs">{row.original.contactPhone}</span>
        ),
      },
      {
        accessorKey: "submittedAt",
        header: "Submitted",
        cell: ({ row }) => (
          <span className="text-xs text-muted-foreground">
            {formatDate(row.original.submittedAt)}
          </span>
        ),
      },
      {
        accessorKey: "status",
        header: "Status",
        cell: ({ row }) => (
          <Badge variant={STATUS_VARIANT[row.original.status]}>{row.original.status}</Badge>
        ),
      },
      {
        id: "actions",
        header: "",
        enableSorting: false,
        cell: ({ row }) =>
          row.original.status === "PENDING" ? (
            <div className="inline-flex gap-2">
              <Button
                size="sm"
                variant="accent"
                onClick={() => approve.mutate(row.original.id)}
                disabled={approve.isPending}
              >
                Approve
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => setRejectingApp(row.original)}
              >
                Reject
              </Button>
            </div>
          ) : (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setViewingApp(row.original)}
            >
              View
            </Button>
          ),
      },
    ],
    [approve],
  );

  return (
    <div className="space-y-8">
      <header className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
            Operations
          </p>
          <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight">
            Seller applications
          </h1>
        </div>
        <div className="flex gap-2">
          {(["PENDING", "APPROVED", "REJECTED", "ALL"] as const).map((s) => (
            <Button
              key={s}
              size="sm"
              variant={statusFilter === s ? "accent" : "outline"}
              onClick={() => setStatusFilter(s)}
            >
              {s}
            </Button>
          ))}
        </div>
      </header>

      {isLoading ? (
        <Spinner />
      ) : (data?.content ?? []).length === 0 ? (
        <Card>
          <CardContent className="p-10 text-center text-sm text-muted-foreground">
            No {statusFilter === "ALL" ? "" : statusFilter.toLowerCase()} applications.
          </CardContent>
        </Card>
      ) : (
        <DataTable
          columns={columns}
          data={data?.content ?? []}
          searchColumn="businessName"
          searchPlaceholder="Search by business name…"
          pageSize={20}
          emptyState="No applications match your search."
        />
      )}

      <RejectDialog
        app={rejectingApp}
        onClose={() => setRejectingApp(null)}
        onConfirm={(reason) =>
          rejectingApp && reject.mutate({ id: rejectingApp.id, reason })
        }
        rejecting={reject.isPending}
      />
      <DetailDialog app={viewingApp} onClose={() => setViewingApp(null)} />
    </div>
  );
};

const RejectDialog: React.FC<{
  app: SellerApplication | null;
  onClose: () => void;
  onConfirm: (reason: string) => void;
  rejecting: boolean;
}> = ({ app, onClose, onConfirm, rejecting }) => {
  const [reason, setReason] = React.useState("");
  React.useEffect(() => setReason(""), [app]);
  if (!app) return null;
  return (
    <Dialog open={!!app} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reject application</DialogTitle>
          <DialogDescription>
            {app.businessName} · {app.userEmail}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2 py-2">
          <Label>Reason (sent to the applicant)</Label>
          <Textarea
            rows={4}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Explain what's missing or wrong…"
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

const DetailDialog: React.FC<{
  app: SellerApplication | null;
  onClose: () => void;
}> = ({ app, onClose }) => {
  if (!app) return null;
  const a = app.pickupAddress;
  return (
    <Dialog open={!!app} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{app.businessName}</DialogTitle>
          <DialogDescription>
            {app.userEmail} · submitted {formatDate(app.submittedAt)}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3 py-2 text-sm">
          <Row label="Status" value={<Badge variant={STATUS_VARIANT[app.status]}>{app.status}</Badge>} />
          <Row label="GSTIN" value={app.gstin ?? "—"} />
          <Row label="Contact phone" value={app.contactPhone} />
          <Row label="Bank acct (last 4)" value={app.bankAccountLast4 ?? "—"} />
          <div>
            <div className="text-xs text-muted-foreground">Pickup address</div>
            <div>
              {[a?.fullName, a?.phone].filter(Boolean).join(" · ")}
              <br />
              {a?.street}
              <br />
              {[a?.city, a?.state, a?.zipCode].filter(Boolean).join(" · ")}
              <br />
              {a?.country}
            </div>
          </div>
          {app.notes && (
            <div>
              <div className="text-xs text-muted-foreground">Notes</div>
              <div className="whitespace-pre-wrap">{app.notes}</div>
            </div>
          )}
          {app.rejectionReason && (
            <div>
              <div className="text-xs text-destructive">Rejection reason</div>
              <div className="whitespace-pre-wrap">{app.rejectionReason}</div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
};

const Row: React.FC<{ label: string; value: React.ReactNode }> = ({ label, value }) => (
  <div className="flex items-center justify-between">
    <span className="text-xs text-muted-foreground">{label}</span>
    <span className="text-right">{value}</span>
  </div>
);
