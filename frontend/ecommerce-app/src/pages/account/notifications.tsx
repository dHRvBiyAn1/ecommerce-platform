import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, Check } from "lucide-react";
import { listMyNotifications, markRead, unreadCount } from "@/api/notifications";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { formatDate } from "@/lib/utils";

export const NotificationsPage: React.FC = () => {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ["notifications"],
    queryFn: () => listMyNotifications(0, 50),
  });
  const { data: unread } = useQuery({
    queryKey: ["notifications-unread"],
    queryFn: unreadCount,
  });

  const markReadMutation = useMutation({
    mutationFn: markRead,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["notifications"] });
      qc.invalidateQueries({ queryKey: ["notifications-unread"] });
    },
  });

  return (
    <div className="container py-12">
      <header className="mb-8 flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Account</p>
          <h1 className="mt-2 font-display text-4xl font-semibold tracking-tight">
            Notifications
          </h1>
        </div>
        {(unread ?? 0) > 0 && (
          <Badge variant="accent">{unread} unread</Badge>
        )}
      </header>

      {isLoading ? (
        <Spinner label="Loading…" />
      ) : (data?.content?.length ?? 0) === 0 ? (
        <Card>
          <CardContent className="p-10 text-center">
            <Bell className="mx-auto h-8 w-8 text-muted-foreground" />
            <p className="mt-4 font-display text-lg">All caught up.</p>
          </CardContent>
        </Card>
      ) : (
        <ul className="space-y-3">
          {(data?.content ?? []).map((n) => (
            <li key={n.id}>
              <Card>
                <CardContent className="flex items-start gap-3 p-4">
                  <div
                    className={`mt-1 grid h-8 w-8 place-items-center rounded-full ${
                      n.status === "READ" ? "bg-secondary" : "bg-accent/15 text-accent"
                    }`}
                  >
                    <Bell className="h-4 w-4" />
                  </div>
                  <div className="flex-1">
                    <div className="flex flex-wrap items-baseline justify-between gap-2">
                      <h3 className="font-display text-base font-semibold tracking-tight">
                        {n.subject}
                      </h3>
                      <span className="text-xs text-muted-foreground">
                        {formatDate(n.createdAt)}
                      </span>
                    </div>
                    <p
                      className="mt-1 text-sm text-muted-foreground"
                      // The backend body is rendered HTML for emails. Stripped via DOMPurify in real prod.
                      dangerouslySetInnerHTML={{ __html: n.body }}
                    />
                    <div className="mt-2 flex items-center gap-2">
                      <Badge variant="outline">{n.category}</Badge>
                      <Badge variant={n.status === "READ" ? "secondary" : "info"}>
                        {n.status}
                      </Badge>
                    </div>
                  </div>
                  {n.status !== "READ" && (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => markReadMutation.mutate(n.id)}
                    >
                      <Check className="h-4 w-4" /> Mark read
                    </Button>
                  )}
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};
