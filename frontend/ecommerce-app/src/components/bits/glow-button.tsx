import * as React from "react";
import { cn } from "@/lib/utils";
import { Button, ButtonProps } from "@/components/ui/button";

interface GlowButtonProps extends ButtonProps {
  glowColor?: string;
}

export const GlowButton = React.forwardRef<HTMLButtonElement, GlowButtonProps>(
  ({ className, glowColor = "hsl(var(--accent))", children, ...props }, ref) => {
    return (
      <div className="group relative inline-block">
        <div
          className="absolute -inset-2 rounded-xl opacity-0 blur-2xl transition-all duration-500 group-hover:opacity-40 group-hover:duration-300 pointer-events-none"
          style={{
            backgroundColor: glowColor,
          }}
        />
        <Button ref={ref} className={cn("relative z-10", className)} {...props}>
          {children}
        </Button>
      </div>
    );
  }
);

GlowButton.displayName = "GlowButton";
