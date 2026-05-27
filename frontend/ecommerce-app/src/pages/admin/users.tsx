import * as React from "react";
import { Card, CardContent } from "@/components/ui/card";

/**
 * Admin Users management — minimal placeholder. Full assign-roles / activate
 * flows depend on auth-service admin endpoints; we'll wire the calls in the
 * next session, gated by the `admin:users:read|write` permissions.
 */
export const AdminUsersPage: React.FC = () => (
  <div className="space-y-8">
    <header>
      <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Operations</p>
      <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight">Users</h1>
    </header>
    <Card>
      <CardContent className="space-y-3 p-6 text-sm text-muted-foreground">
        <p>
          User management page is intentionally lightweight in this build. The
          backend has the full set of endpoints already (
          <code className="rounded bg-secondary px-1.5 py-0.5 font-mono text-xs">
            /api/admin/users
          </code>
          ,
          <code className="ml-1 rounded bg-secondary px-1.5 py-0.5 font-mono text-xs">
            PUT /api/admin/users/&#123;id&#125;/roles
          </code>
          , etc.); we'll wire the table in the next session along with
          permission-based action gating.
        </p>
        <p>
          For now, promote a customer to seller from a database shell or via{" "}
          <code className="rounded bg-secondary px-1.5 py-0.5 font-mono text-xs">
            curl -X PUT /api/admin/users/&lt;id&gt;/roles -d '{`{"roles":["ROLE_SELLER"]}`}'
          </code>
          .
        </p>
      </CardContent>
    </Card>
  </div>
);
