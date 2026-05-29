import * as React from "react";
import { cn } from "@/lib/utils";

interface DotFieldProps extends React.HTMLAttributes<HTMLDivElement> {
  dotSize?: number;
  gap?: number;
  color?: string;
  maxOpacity?: number;
  minOpacity?: number;
  radius?: number;
}

export const DotField = ({
  dotSize = 1.5,
  gap = 25,
  color = "currentColor",
  maxOpacity = 0.6,
  minOpacity = 0.15,
  radius = 250,
  className,
  ...props
}: DotFieldProps) => {
  const containerRef = React.useRef<HTMLDivElement>(null);
  const [mousePos, setMousePos] = React.useState({ x: 0, y: 0 });
  const [isVisible, setIsVisible] = React.useState(false);

  React.useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!containerRef.current) return;
      const rect = containerRef.current.getBoundingClientRect();
      setMousePos({
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      });
      setIsVisible(true);
    };

    window.addEventListener("mousemove", handleMouseMove);
    return () => window.removeEventListener("mousemove", handleMouseMove);
  }, []);

  return (
    <div
      ref={containerRef}
      className={cn("absolute inset-0 -z-10 h-full w-full overflow-hidden", className)}
      {...props}
    >
      <div
        className="absolute inset-0"
        style={{
          backgroundImage: `radial-gradient(${color} ${dotSize}px, transparent ${dotSize}px)`,
          backgroundSize: `${gap}px ${gap}px`,
          opacity: minOpacity,
        }}
      />
      <div
        className="absolute inset-0 transition-opacity duration-500"
        style={{
          backgroundImage: `radial-gradient(${color} ${dotSize}px, transparent ${dotSize}px)`,
          backgroundSize: `${gap}px ${gap}px`,
          WebkitMaskImage: isVisible 
            ? `radial-gradient(${radius}px circle at ${mousePos.x}px ${mousePos.y}px, black 0%, transparent 100%)`
            : "none",
          maskImage: isVisible
            ? `radial-gradient(${radius}px circle at ${mousePos.x}px ${mousePos.y}px, black 0%, transparent 100%)`
            : "none",
          opacity: maxOpacity,
        }}
      />
    </div>
  );
};
