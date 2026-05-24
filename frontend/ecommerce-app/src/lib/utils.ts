import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** Format a number as a localized currency string. Defaults to INR. */
export function formatMoney(
  amount: number | string | undefined | null,
  currency = "INR",
  locale = "en-IN",
): string {
  if (amount === null || amount === undefined) return "—";
  const value = typeof amount === "string" ? Number.parseFloat(amount) : amount;
  if (Number.isNaN(value)) return "—";
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(value);
}

/** Format a date or ISO string. */
export function formatDate(input: string | Date | null | undefined): string {
  if (!input) return "—";
  const d = typeof input === "string" ? new Date(input) : input;
  return d.toLocaleString("en-IN", {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

/** Pretty bytes / count compactor. */
export function compact(n: number): string {
  if (n < 1000) return String(n);
  return new Intl.NumberFormat("en", { notation: "compact" }).format(n);
}

/** Deterministic hash → HSL gradient pair for SKU-derived placeholder art. */
export function gradientFromSeed(seed: string): { from: string; to: string; angle: number } {
  let h = 0;
  for (let i = 0; i < seed.length; i += 1) {
    h = (h * 31 + seed.charCodeAt(i)) | 0;
  }
  const a = Math.abs(h);
  const hue1 = a % 360;
  const hue2 = (hue1 + 60 + (a % 90)) % 360;
  const angle = (a >> 3) % 360;
  return {
    from: `hsl(${hue1} 70% 75%)`,
    to: `hsl(${hue2} 60% 35%)`,
    angle,
  };
}

/** Initials for avatars from a name string. */
export function initials(name?: string | null): string {
  if (!name) return "·";
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase())
    .join("");
}
