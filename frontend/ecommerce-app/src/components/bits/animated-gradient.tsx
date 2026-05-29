import * as React from "react";
import { cn } from "@/lib/utils";

interface AnimatedGradientProps extends React.HTMLAttributes<HTMLDivElement> {
  colors?: string[];
  speed?: number;
}

export const AnimatedGradient = ({
  colors = ["#DB2777", "#F472B6", "#CA8A04", "#2563EB"],
  speed = 10,
  className,
  ...props
}: AnimatedGradientProps) => {
  return (
    <div
      className={cn("absolute inset-0 -z-10 overflow-hidden", className)}
      {...props}
    >
      <div
        className="absolute inset-[-100%] opacity-30 blur-[100px] animate-slow-spin"
        style={{
          background: `conic-gradient(from 0deg, ${colors.join(", ")})`,
          animationDuration: `${speed}s`,
        }}
      />
    </div>
  );
};
