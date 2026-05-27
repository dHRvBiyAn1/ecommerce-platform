import * as React from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { ChevronLeft, Heart, ShieldCheck, ShoppingBag, Truck } from "lucide-react";
import { getProduct } from "@/api/products";
import { ProductArt } from "@/components/product-art";
import { PageSpinner } from "@/components/ui/spinner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Separator } from "@/components/ui/separator";
import { Seo } from "@/components/seo";
import { useCart } from "@/stores/cart";
import { formatMoney } from "@/lib/utils";
import { toast } from "sonner";

export const ProductDetailPage: React.FC = () => {
  const { id = "" } = useParams();
  const { data: product, isLoading, isError } = useQuery({
    queryKey: ["product", id],
    queryFn: () => getProduct(id),
    enabled: Boolean(id),
  });

  const add = useCart((s) => s.add);
  const [qty, setQty] = React.useState(1);

  if (isLoading) return <PageSpinner />;
  if (isError || !product) {
    return (
      <div className="container py-24 text-center">
        <Seo title="Product not found" noindex />
        <p className="font-display text-2xl">This product is no longer available.</p>
        <Button asChild variant="outline" className="mt-6">
          <Link to="/products">Back to catalog</Link>
        </Button>
      </div>
    );
  }

  const inStock = product.stockQuantity > 0;

  function addToCart() {
    add(product!, qty);
    toast.success(`${product!.name} added to cart`, {
      action: { label: "View cart", onClick: () => (window.location.href = "/cart") },
    });
  }

  return (
    <div className="container pb-28 pt-6 sm:py-12 md:pb-16">
      <Seo title={product.name} description={product.description.slice(0, 155)} />

      <Link
        to="/products"
        className="inline-flex items-center gap-1 text-xs uppercase tracking-[0.18em] text-muted-foreground hover:text-accent"
      >
        <ChevronLeft className="h-3 w-3" /> Back to catalog
      </Link>

      <div className="mt-4 grid gap-8 sm:mt-6 lg:grid-cols-2 lg:gap-12">
        {/* Visual: stack of art tiles for editorial feel */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="grid grid-cols-2 gap-3"
        >
          <ProductArt
            seed={product.sku}
            ratio="portrait"
            className="col-span-2"
            label={product.name}
          />
          <ProductArt seed={`${product.sku}-a`} ratio="square" />
          <ProductArt seed={`${product.sku}-b`} ratio="square" />
        </motion.div>

        <div>
          <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
            SKU · {product.sku}
          </p>
          <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight sm:text-4xl md:text-5xl">
            {product.name}
          </h1>
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <span className="font-mono text-2xl">{formatMoney(product.price)}</span>
            {!inStock ? (
              <Badge variant="destructive">Sold out</Badge>
            ) : product.stockQuantity < 5 ? (
              <Badge variant="warning">Only {product.stockQuantity} left</Badge>
            ) : (
              <Badge variant="success">In stock</Badge>
            )}
          </div>

          <p className="mt-6 max-w-prose text-balance text-muted-foreground">
            {product.description}
          </p>

          {/* Desktop add-to-cart inline */}
          <div className="mt-8 hidden flex-wrap items-center gap-3 md:flex">
            <QtyStepper qty={qty} setQty={setQty} max={product.stockQuantity} />
            <Button
              variant="accent"
              size="lg"
              className="flex-1 min-w-[12rem]"
              disabled={!inStock}
              onClick={addToCart}
            >
              <ShoppingBag className="h-4 w-4" />
              {inStock ? "Add to cart" : "Sold out"}
            </Button>
            <Button size="lg" variant="outline" aria-label="Save to wishlist">
              <Heart className="h-4 w-4" />
            </Button>
          </div>

          <div className="mt-6 grid gap-2 text-sm text-muted-foreground sm:grid-cols-2 md:mt-8">
            <Pill icon={Truck} label="Free shipping over ₹499" />
            <Pill icon={ShieldCheck} label="30-day returns" />
          </div>

          <Separator className="my-8 md:my-10" />

          <Tabs defaultValue="details">
            <TabsList>
              <TabsTrigger value="details">Details</TabsTrigger>
              <TabsTrigger value="shipping">Shipping</TabsTrigger>
              <TabsTrigger value="returns">Returns</TabsTrigger>
            </TabsList>
            <TabsContent value="details" className="prose prose-sm max-w-none">
              <p>{product.description}</p>
              <ul className="mt-3 list-disc pl-5 text-sm text-muted-foreground">
                <li>
                  Seller id: <span className="font-mono">{product.sellerId}</span>
                </li>
                <li>
                  Category: <span className="font-mono">{product.categoryId}</span>
                </li>
              </ul>
            </TabsContent>
            <TabsContent value="shipping" className="text-sm text-muted-foreground">
              Standard shipping in 3–5 business days. Free over ₹499. We carbon-offset all parcels.
            </TabsContent>
            <TabsContent value="returns" className="text-sm text-muted-foreground">
              30-day returns. Original packaging encouraged but not required.
            </TabsContent>
          </Tabs>
        </div>
      </div>

      {/* Sticky mobile bottom bar */}
      <div className="fixed inset-x-0 bottom-14 z-30 border-t bg-background/95 px-4 py-3 backdrop-blur supports-[backdrop-filter]:bg-background/85 md:hidden pb-[max(0.75rem,env(safe-area-inset-bottom))]">
        <div className="flex items-center gap-3">
          <div className="min-w-0">
            <p className="truncate font-display text-sm font-semibold tracking-tight">
              {product.name}
            </p>
            <p className="font-mono text-xs">{formatMoney(product.price)}</p>
          </div>
          <div className="ml-auto flex items-center gap-2">
            <QtyStepper qty={qty} setQty={setQty} max={product.stockQuantity} compact />
            <Button
              variant="accent"
              size="default"
              disabled={!inStock}
              onClick={addToCart}
              className="px-4"
            >
              <ShoppingBag className="h-4 w-4" /> Add
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};

const QtyStepper: React.FC<{
  qty: number;
  setQty: (n: number) => void;
  max: number;
  compact?: boolean;
}> = ({ qty, setQty, max, compact }) => (
  <div
    className={`inline-flex items-center rounded-md border ${compact ? "" : ""}`}
    role="group"
    aria-label="Quantity"
  >
    <button
      type="button"
      className={`text-lg disabled:opacity-50 ${compact ? "px-2 py-1" : "px-3 py-2"}`}
      disabled={qty <= 1}
      onClick={() => setQty(Math.max(1, qty - 1))}
      aria-label="Decrement quantity"
    >
      −
    </button>
    <span
      className={`min-w-[2ch] text-center font-mono ${compact ? "px-1 text-sm" : "px-2 text-sm"}`}
      aria-live="polite"
    >
      {qty}
    </span>
    <button
      type="button"
      className={`text-lg disabled:opacity-50 ${compact ? "px-2 py-1" : "px-3 py-2"}`}
      disabled={qty >= max}
      onClick={() => setQty(Math.min(max, qty + 1))}
      aria-label="Increment quantity"
    >
      +
    </button>
  </div>
);

const Pill: React.FC<{ icon: React.ComponentType<any>; label: string }> = ({
  icon: Icon,
  label,
}) => (
  <div className="inline-flex items-center gap-2 rounded-md border bg-secondary px-3 py-2">
    <Icon className="h-4 w-4 text-accent" />
    {label}
  </div>
);
