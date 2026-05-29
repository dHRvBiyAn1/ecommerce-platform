import * as React from "react";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

export const Spinner: React.FC<{ className?: string; label?: string }> = ({
  className,
  label,
}) => (
  <div className={cn("inline-flex items-center gap-2 text-sm text-muted-foreground", className)}>
    <Loader2 className="h-4 w-4 animate-spin" />
    {label && <span>{label}</span>}
  </div>
);

export const PageSpinner: React.FC<{ label?: string }> = ({ label = "Loading…" }) => (
  <div className="flex min-h-[40vh] items-center justify-center">
    <Spinner label={label} />
  </div>
);
