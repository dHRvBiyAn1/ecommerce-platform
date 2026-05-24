import * as React from "react";
import { cn } from "@/lib/utils";
import { gradientFromSeed } from "@/lib/utils";

interface ProductArtProps extends React.HTMLAttributes<HTMLDivElement> {
  seed: string;
  ratio?: "square" | "portrait" | "wide";
  label?: string;
}

/**
 * Deterministic abstract gradient art used in place of product photos while
 * the backend's image pipeline is intentionally null. The seed (typically the
 * SKU or product id) maps to a stable hue/angle pair so each product has its
 * own recognizable "thumbnail" without needing assets.
 */
export const ProductArt: React.FC<ProductArtProps> = ({
  seed,
  ratio = "square",
  label,
  className,
  ...rest
}) => {
  const { from, to, angle } = gradientFromSeed(seed);
  const ratioClass =
    ratio === "portrait"
      ? "aspect-[3/4]"
      : ratio === "wide"
      ? "aspect-[16/10]"
      : "aspect-square";

  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-xl bg-grain ring-1 ring-black/5",
        ratioClass,
        className,
      )}
      style={{
        backgroundImage: `linear-gradient(${angle}deg, ${from}, ${to}), url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"><filter id="n"><feTurbulence type="fractalNoise" baseFrequency=".85" numOctaves="2" stitchTiles="stitch"/><feColorMatrix values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.06 0"/></filter><rect width="100%" height="100%" filter="url(%23n)"/></svg>')`,
        backgroundBlendMode: "soft-light",
      }}
      {...rest}
    >
      {/* Subtle decorative geometry: an off-center oval frosted shape */}
      <div
        aria-hidden
        className="pointer-events-none absolute -right-16 -top-16 h-48 w-48 rounded-full bg-white/15 blur-2xl"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute bottom-6 left-6 right-6 flex items-end justify-between text-[10px] uppercase tracking-[0.18em] text-white/80 mix-blend-overlay"
      >
        <span className="font-mono">SKU · {seed.slice(-6).toUpperCase()}</span>
        {label && <span className="font-display font-medium">{label}</span>}
      </div>
    </div>
  );
};
