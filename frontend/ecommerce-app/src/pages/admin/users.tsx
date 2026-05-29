import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ColumnDef } from "@tanstack/react-table";
import { toast } from "sonner";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Spinner } from "@/components/ui/spinner";
import { DataTable } from "@/components/ui/data-table";
import {
  listAdminUsers,
  setUserActive,
  setUserRoles,
  listRoles,
} from "@/api/admin";
import type { UserProfile } from "@/api/types";
import { formatDate } from "@/lib/utils";

/**
 * Admin user management. The auth-service exposes the full set of endpoints
 * already (`/api/admin/users`, role assignment, activation toggle) — this
 * page wires them up with search, sort, and a roles-edit modal.
 */
export const AdminUsersPage: React.FC = () => {
  const qc = useQueryClient();
  const users = useQuery({
    queryKey: ["admin", "users"],
    queryFn: () => listAdminUsers(0, 200),
  });
  const roles = useQuery({ queryKey: ["admin", "roles"], queryFn: listRoles });

  const [editing, setEditing] = React.useState<UserProfile | null>(null);

  const toggleActive = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      setUserActive(id, active),
    onSuccess: (u) => {
      toast.success(`${u.email} ${u.active ? "activated" : "deactivated"}`);
      qc.invalidateQueries({ queryKey: ["admin", "users"] });
    },
    onError: (e: any) => toast.error(e?.message ?? "Failed"),
  });

  const updateRoles = useMutation({
    mutationFn: ({ id, roles }: { id: string; roles: string[] }) =>
      setUserRoles(id, roles),
    onSuccess: (u) => {
      toast.success(`Updated roles for ${u.email}`);
      qc.invalidateQueries({ queryKey: ["admin", "users"] });
      setEditing(null);
    },
    onError: (e: any) => toast.error(e?.message ?? "Failed"),
  });

  const columns = React.useMemo<ColumnDef<UserProfile>[]>(
    () => [
      {
        accessorKey: "email",
        header: "Email",
        cell: ({ row }) => (
          <div>
            <div className="text-sm font-medium">{row.original.email}</div>
            <div className="text-[11px] text-muted-foreground">
              {row.original.id.slice(0, 8)}…
            </div>
          </div>
        ),
      },
      {
        accessorKey: "displayName",
        header: "Name",
        cell: ({ row }) => row.original.displayName ?? "—",
      },
      {
        id: "roles",
        header: "Roles",
        accessorFn: (u) => (u.roles ?? []).join(", "),
        cell: ({ row }) => (
          <div className="flex flex-wrap gap-1">
            {(row.original.roles ?? []).map((r) => (
              <Badge key={r} variant="outline" className="text-[10px]">
                {r.replace(/^ROLE_/, "")}
              </Badge>
            ))}
          </div>
        ),
      },
      {
        accessorKey: "active",
        header: "Status",
        cell: ({ row }) => (
          <Badge variant={row.original.active ? "success" : "secondary"}>
            {row.original.active ? "Active" : "Disabled"}
          </Badge>
        ),
      },
      {
        accessorKey: "createdAt",
        header: "Joined",
        cell: ({ row }) =>
          row.original.createdAt ? (
            <span className="text-xs text-muted-foreground">
              {formatDate(row.original.createdAt)}
            </span>
          ) : (
            "—"
          ),
      },
      {
        id: "actions",
        header: "",
        enableSorting: false,
        cell: ({ row }) => {
          const u = row.original;
          return (
            <div className="inline-flex items-center gap-2">
              <Button size="sm" variant="outline" onClick={() => setEditing(u)}>
                Edit roles
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() =>
                  toggleActive.mutate({ id: u.id, active: !u.active })
                }
                disabled={toggleActive.isPending}
              >
                {u.active ? "Disable" : "Enable"}
              </Button>
            </div>
          );
        },
      },
    ],
    [toggleActive],
  );

  return (
    <div className="space-y-8">
      <header>
        <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Operations</p>
        <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight">Users</h1>
      </header>

      {users.isLoading ? (
        <Spinner />
      ) : (users.data?.content ?? []).length === 0 ? (
        <Card>
          <CardContent className="p-10 text-center text-sm text-muted-foreground">
            No users yet.
          </CardContent>
        </Card>
      ) : (
        <DataTable
          columns={columns}
          data={users.data?.content ?? []}
          searchColumn="email"
          searchPlaceholder="Search by email…"
          pageSize={20}
          emptyState="No users match your search."
        />
      )}

      <RolesDialog
        user={editing}
        availableRoles={(roles.data ?? []).map((r) => r.name)}
        onClose={() => setEditing(null)}
        onSave={(roles) =>
          editing && updateRoles.mutate({ id: editing.id, roles })
        }
        saving={updateRoles.isPending}
      />
    </div>
  );
};

const RolesDialog: React.FC<{
  user: UserProfile | null;
  availableRoles: string[];
  onClose: () => void;
  onSave: (roles: string[]) => void;
  saving: boolean;
}> = ({ user, availableRoles, onClose, onSave, saving }) => {
  const [selected, setSelected] = React.useState<Set<string>>(new Set());

  React.useEffect(() => {
    setSelected(new Set(user?.roles ?? []));
  }, [user]);

  if (!user) return null;

  return (
    <Dialog open={!!user} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit roles · {user.email}</DialogTitle>
          <DialogDescription>
            Pick which roles this user has. Permissions are derived from roles.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2 py-2">
          {availableRoles.length === 0 ? (
            <p className="text-sm text-muted-foreground">No roles available.</p>
          ) : (
            availableRoles.map((r) => {
              const checked = selected.has(r);
              return (
                <label
                  key={r}
                  className="flex cursor-pointer items-center gap-3 rounded-md border p-3 hover:bg-secondary/40"
                >
                  <input
                    type="checkbox"
                    className="h-4 w-4"
                    checked={checked}
                    onChange={(e) => {
                      const next = new Set(selected);
                      if (e.target.checked) next.add(r);
                      else next.delete(r);
                      setSelected(next);
                    }}
                  />
                  <span className="text-sm font-medium">
                    {r.replace(/^ROLE_/, "")}
                  </span>
                  <span className="ml-auto text-[11px] text-muted-foreground">{r}</span>
                </label>
              );
            })
          )}
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button
            variant="accent"
            onClick={() => onSave(Array.from(selected))}
            disabled={saving || selected.size === 0}
          >
            {saving ? <Spinner /> : "Save"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
};
