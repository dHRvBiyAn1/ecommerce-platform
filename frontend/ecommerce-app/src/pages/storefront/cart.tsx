import * as React from "react";
import { Link, useNavigate } from "react-router-dom";
import { Minus, Plus, ShoppingBag, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Input } from "@/components/ui/input";
import { ProductArt } from "@/components/product-art";
import { useCart } from "@/stores/cart";
import { useAuthStore } from "@/stores/auth";
import { formatMoney } from "@/lib/utils";
import { toast } from "sonner";

const FREE_SHIPPING = 499;
const SHIPPING_COST = 49;
const TAX_RATE = 0.18;

export const CartPage: React.FC = () => {
  const navigate = useNavigate();
  const { lines, setQuantity, remove, clear, couponCode, discountAmount, applyCoupon, removeCoupon } = useCart();
  const isAuthed = useAuthStore((s) => s.isAuthenticated());
  const [couponInput, setCouponInput] = React.useState("");
  const [applying, setApplying] = React.useState(false);

  const subtotal = lines.reduce((s, l) => s + l.price * l.quantity, 0);
  const tax = subtotal * TAX_RATE;
  const shipping = subtotal >= FREE_SHIPPING ? 0 : SHIPPING_COST;
  const total = Math.max(0, subtotal + tax + shipping - (discountAmount || 0));

  const handleApplyCoupon = async () => {
    if (!couponInput.trim()) return;
    setApplying(true);
    try {
      await applyCoupon(couponInput);
      toast.success("Coupon applied");
    } catch (err: any) {
      toast.error(err?.message ?? "Failed to apply coupon");
    } finally {
      setApplying(false);
    }
  };

  const handleRemoveCoupon = async () => {
    setApplying(true);
    try {
      await removeCoupon();
      setCouponInput("");
      toast.success("Coupon removed");
    } catch (err: any) {
      toast.error(err?.message ?? "Failed to remove coupon");
    } finally {
      setApplying(false);
    }
  };

  if (lines.length === 0) {
    return (
      <div className="container py-24 text-center">
        <ShoppingBag className="mx-auto h-12 w-12 text-muted-foreground/50" />
        <h1 className="mt-6 font-display text-3xl font-semibold tracking-tight">
          Your bag is empty
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Choose something worth keeping.
        </p>
        <Button asChild variant="accent" className="mt-6">
          <Link to="/products">Browse products</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="container py-12">
      <header className="mb-10">
        <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Bag</p>
        <h1 className="mt-2 font-display text-4xl font-semibold tracking-tight">
          Review your selection
        </h1>
      </header>

      <div className="grid gap-10 lg:grid-cols-[1fr_22rem]">
        <ul className="divide-y rounded-xl border">
          {lines.map((l) => (
            <li key={l.productId} className="flex gap-4 p-4 sm:p-6">
              <Link to={`/products/${l.productId}`} className="shrink-0">
                <ProductArt seed={l.sku || l.productId} ratio="square" className="h-24 w-24 sm:h-28 sm:w-28" />
              </Link>
              <div className="flex flex-1 flex-col gap-2">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                      SKU · {l.sku}
                    </p>
                    <Link to={`/products/${l.productId}`} className="font-display text-base font-semibold tracking-tight hover:text-accent">
                      {l.name}
                    </Link>
                  </div>
                  <button
                    type="button"
                    onClick={async () => {
                      try {
                        await remove(l.productId);
                      } catch (err: any) {
                        toast.error(err?.message ?? "Failed to remove item");
                      }
                    }}
                    className="rounded p-2 text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive"
                    aria-label="Remove"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
                <div className="mt-auto flex items-center justify-between">
                  <div className="inline-flex items-center rounded-md border">
                    <button
                      type="button"
                      onClick={async () => {
                        try {
                          await setQuantity(l.productId, l.quantity - 1);
                        } catch (err: any) {
                          toast.error(err?.message ?? "Failed to update quantity");
                        }
                      }}
                      className="grid h-8 w-8 place-items-center"
                      aria-label="Decrement"
                    >
                      <Minus className="h-4 w-4" />
                    </button>
                    <span className="min-w-[3ch] px-2 text-center font-mono text-sm">
                      {l.quantity}
                    </span>
                    <button
                      type="button"
                      onClick={async () => {
                        try {
                          await setQuantity(l.productId, l.quantity + 1);
                        } catch (err: any) {
                          toast.error(err?.message ?? "Failed to update quantity");
                        }
                      }}
                      className="grid h-8 w-8 place-items-center"
                      aria-label="Increment"
                    >
                      <Plus className="h-4 w-4" />
                    </button>
                  </div>
                  <span className="font-mono text-sm">
                    {formatMoney(l.price * l.quantity)}
                  </span>
                </div>
              </div>
            </li>
          ))}
        </ul>

        <Card className="h-fit lg:sticky lg:top-24">
          <CardContent className="space-y-4 p-6">
            <h2 className="font-display text-lg font-semibold tracking-tight">Order summary</h2>
            <Row label="Subtotal" value={formatMoney(subtotal)} />
            <Row label="GST (18%)" value={formatMoney(tax)} />
            <Row
              label="Shipping"
              value={shipping === 0 ? <span className="text-emerald-600">Free</span> : formatMoney(shipping)}
            />
            {discountAmount > 0 && (
              <Row label="Discount" value={<span className="text-emerald-600">-{formatMoney(discountAmount)}</span>} />
            )}
            <Separator />
            {isAuthed && (
              <>
                <div className="flex gap-2">
                  <Input
                    value={couponCode || couponInput}
                    onChange={(e) => setCouponInput(e.target.value)}
                    disabled={!!couponCode || applying}
                    placeholder="Coupon code"
                  />
                  {couponCode ? (
                    <Button type="button" variant="outline" onClick={handleRemoveCoupon} disabled={applying}>
                      Remove
                    </Button>
                  ) : (
                    <Button type="button" variant="outline" onClick={handleApplyCoupon} disabled={applying || !couponInput.trim()}>
                      Apply
                    </Button>
                  )}
                </div>
                <Separator />
              </>
            )}
            <Row label="Total" value={formatMoney(total)} bold />
            <Button
              variant="accent"
              size="lg"
              className="w-full"
              onClick={() => (isAuthed ? navigate("/checkout") : navigate("/login", { state: { from: "/checkout" } }))}
            >
              {isAuthed ? "Checkout" : "Sign in to checkout"}
            </Button>
            <button
              type="button"
              onClick={clear}
              className="w-full text-xs text-muted-foreground underline-offset-4 hover:text-destructive hover:underline"
            >
              Clear bag
            </button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

const Row: React.FC<{ label: string; value: React.ReactNode; bold?: boolean }> = ({
  label,
  value,
  bold,
}) => (
  <div className={`flex items-center justify-between text-sm ${bold ? "text-base font-semibold" : ""}`}>
    <span className="text-muted-foreground">{label}</span>
    <span className={bold ? "font-mono" : "font-mono text-muted-foreground"}>{value}</span>
  </div>
);
