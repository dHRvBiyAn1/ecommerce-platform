import { motion, useReducedMotion, Variants } from "framer-motion";
import { ReactNode } from "react";

interface ScrollAnimationProps {
  children: ReactNode;
  variants?: Variants;
  initial?: string | boolean;
  whileInView?: string;
  viewport?: {
    once?: boolean;
    amount?: number | "some" | "all";
    margin?: string;
  };
  transition?: {
    duration?: number;
    delay?: number;
    ease?: string | number[];
  };
  className?: string;
  type?: "fade" | "slide-up" | "slide-down" | "slide-left" | "slide-right" | "scale";
  delay?: number;
}

const defaultVariants: Record<string, Variants> = {
  fade: {
    hidden: { opacity: 0 },
    visible: { opacity: 1 },
  },
  "slide-up": {
    hidden: { opacity: 0, y: 20 },
    visible: { opacity: 1, y: 0 },
  },
  "slide-down": {
    hidden: { opacity: 0, y: -20 },
    visible: { opacity: 1, y: 0 },
  },
  "slide-left": {
    hidden: { opacity: 0, x: 20 },
    visible: { opacity: 1, x: 0 },
  },
  "slide-right": {
    hidden: { opacity: 0, x: -20 },
    visible: { opacity: 1, x: 0 },
  },
  scale: {
    hidden: { opacity: 0, scale: 0.95 },
    visible: { opacity: 1, scale: 1 },
  },
};

export function ScrollAnimation({
  children,
  variants,
  initial = "hidden",
  whileInView = "visible",
  viewport = { once: true, amount: 0.2 },
  transition,
  className,
  type = "fade",
  delay = 0,
}: ScrollAnimationProps) {
  const shouldReduceMotion = useReducedMotion();

  // If user prefers reduced motion, we disable animations but keep the content visible
  if (shouldReduceMotion) {
    return <div className={className}>{children}</div>;
  }

  const selectedVariants = variants || defaultVariants[type];

  return (
    <motion.div
      initial={initial}
      whileInView={whileInView}
      viewport={viewport}
      variants={selectedVariants}
      transition={{
        duration: 0.5,
        delay,
        ease: [0.21, 0.47, 0.32, 0.98],
        ...transition,
      }}
      className={className}
    >
      {children}
    </motion.div>
  );
}
