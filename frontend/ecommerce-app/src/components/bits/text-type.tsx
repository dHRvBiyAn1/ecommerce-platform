import * as React from "react";
import { cn } from "@/lib/utils";

interface TextTypeProps {
  text: string;
  speed?: number;
  delay?: number;
  className?: string;
  cursor?: boolean;
}

export const TextType: React.FC<TextTypeProps> = ({
  text,
  speed = 50,
  delay = 0,
  className,
  cursor = true,
}) => {
  const [displayText, setDisplayText] = React.useState("");

  React.useEffect(() => {
    let timeout: NodeJS.Timeout;
    let index = 0;

    const startTyping = () => {
      const interval = setInterval(() => {
        setDisplayText(text.slice(0, index + 1));
        index++;
        if (index >= text.length) {
          clearInterval(interval);
        }
      }, speed);
      return () => clearInterval(interval);
    };

    timeout = setTimeout(startTyping, delay * 1000);
    return () => clearTimeout(timeout);
  }, [text, speed, delay]);

  return (
    <span className={cn("inline-block", className)}>
      {displayText}
      {cursor && displayText.length < text.length && (
        <span className="ml-0.5 inline-block w-1.5 h-4 bg-current animate-pulse align-middle" />
      )}
    </span>
  );
};
