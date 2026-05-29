import * as React from "react";
import { cn } from "@/lib/utils";

interface DecryptedTextProps {
  text: string;
  speed?: number;
  className?: string;
  delay?: number;
}

const CHARS = "ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789@#$%^&*()";

export const DecryptedText: React.FC<DecryptedTextProps> = ({
  text,
  speed = 50,
  className,
  delay = 0,
}) => {
  const [displayText, setDisplayText] = React.useState("");
  const [isAnimating, setIsAnimating] = React.useState(false);

  React.useEffect(() => {
    let timeout: NodeJS.Timeout;
    
    const startAnimation = () => {
      setIsAnimating(true);
      let iteration = 0;
      const interval = setInterval(() => {
        setDisplayText(
          text
            .split("")
            .map((char, index) => {
              if (index < iteration) {
                return text[index];
              }
              if (char === " ") return " ";
              return CHARS[Math.floor(Math.random() * CHARS.length)];
            })
            .join("")
        );

        if (iteration >= text.length) {
          clearInterval(interval);
          setIsAnimating(false);
        }

        iteration += 1 / 3;
      }, speed);

      return () => clearInterval(interval);
    };

    timeout = setTimeout(startAnimation, delay * 1000);
    return () => clearTimeout(timeout);
  }, [text, speed, delay]);

  return (
    <span className={cn("inline-block font-mono", className)}>
      {displayText || (isAnimating ? "" : text)}
    </span>
  );
};
