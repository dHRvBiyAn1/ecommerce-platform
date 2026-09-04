import * as React from "react";
import { cn } from "@/lib/utils";

interface BorderGlowProps extends React.HTMLAttributes<HTMLDivElement> {
  glowColor?: string;
  size?: number;
}

export const BorderGlow = ({
  glowColor = "hsl(var(--accent))",
  size = 1,
  className,
  children,
  style,
  ...props
}: BorderGlowProps) => {
  return (
    <div
      className={cn("group relative rounded-xl p-[1px] overflow-hidden bg-border/50 transition-colors hover:bg-transparent", className)}
      style={{ padding: size, ...style }}
      {...props}
    >
      <div
        className="absolute inset-0 z-0 opacity-0 transition-opacity duration-500 group-hover:opacity-100"
        style={{
          background: `conic-gradient(from 0deg at 50% 50%, transparent 0%, ${glowColor} 25%, transparent 50%, ${glowColor} 75%, transparent 100%)`,
          animation: "slow-spin 4s linear infinite",
        }}
      />
      <div className="relative z-10 rounded-[calc(var(--radius)-1px)] bg-card h-full w-full">
        {children}
      </div>
    </div>
  );
};
