import * as React from "react";
import { Moon, Sun } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useTheme } from "@/components/theme-provider";

export const ThemeToggle: React.FC<{ className?: string }> = ({ className }) => {
  const { resolved, toggle } = useTheme();
  const isDark = resolved === "dark";
  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={toggle}
      aria-label={isDark ? "Switch to light theme" : "Switch to dark theme"}
      title={isDark ? "Light mode" : "Dark mode"}
      className={className}
    >
      <Sun className={`h-5 w-5 transition-all ${isDark ? "scale-0 -rotate-90" : "scale-100 rotate-0"}`} />
      <Moon
        className={`absolute h-5 w-5 transition-all ${isDark ? "scale-100 rotate-0" : "scale-0 rotate-90"}`}
      />
    </Button>
  );
};
