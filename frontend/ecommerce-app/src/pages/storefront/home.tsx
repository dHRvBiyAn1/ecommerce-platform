import * as React from "react";
import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, ArrowUpRight, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ProductArt } from "@/components/product-art";
import { listCategories, listProducts } from "@/api/products";
import { Spinner } from "@/components/ui/spinner";
import { formatMoney } from "@/lib/utils";

export const HomePage: React.FC = () => {
  const products = useQuery({
    queryKey: ["products", "featured"],
    queryFn: () => listProducts({ size: 8, sort: "createdAt,desc" }),
  });
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: listCategories,
  });

  return (
    <div className="space-y-24 pb-24">
      {/* Hero — editorial split */}
      <section className="container relative overflow-hidden pt-12">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
          className="grid items-center gap-10 lg:grid-cols-12"
        >
          <div className="lg:col-span-7">
            <span className="inline-flex items-center gap-2 rounded-full bg-secondary px-3 py-1 text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
              <Sparkles className="h-3.5 w-3.5 text-accent" />
              Vol. 01 · The arrival edition
            </span>
            <h1 className="mt-6 font-display text-5xl font-semibold leading-[1.02] tracking-tight md:text-7xl">
              Goods that <em className="font-display not-italic text-accent">linger</em>.<br />
              Stories that <span className="underline decoration-accent decoration-2 underline-offset-8">stay</span>.
            </h1>
            <p className="mt-6 max-w-xl text-balance text-lg text-muted-foreground">
              An editorial marketplace for makers, sellers, and buyers who care about the
              difference between a product and a thing worth keeping.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Button asChild size="lg" variant="accent">
                <Link to="/products">
                  Shop the collection <ArrowRight className="h-4 w-4" />
                </Link>
              </Button>
              <Button asChild size="lg" variant="outline">
                <Link to="/register">
                  Become a seller <ArrowUpRight className="h-4 w-4" />
                </Link>
              </Button>
            </div>
          </div>

          {/* Asymmetric tile collage instead of a hero photo */}
          <div className="lg:col-span-5">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-3">
                <ProductArt seed="A1" ratio="square" label="No. 01" />
                <ProductArt seed="A22-tall" ratio="portrait" label="No. 02" />
              </div>
              <div className="mt-12 space-y-3">
                <ProductArt seed="B7" ratio="portrait" label="No. 03" />
                <ProductArt seed="C4" ratio="square" label="No. 04" />
              </div>
            </div>
          </div>
        </motion.div>
      </section>

      {/* Categories strip */}
      <section className="container">
        <SectionHeading
          eyebrow="Browse"
          title="By interest"
          link={{ to: "/products", label: "All products" }}
        />
        {categories.isLoading ? (
          <Spinner label="Loading categories…" />
        ) : (
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4 lg:grid-cols-6">
            {(categories.data ?? []).slice(0, 12).map((c, i) => (
              <Link
                key={c.id}
                to={`/categories/${c.id}`}
                className="group relative overflow-hidden rounded-xl border bg-card p-4 transition-all hover:border-accent hover:shadow-lg"
              >
                <ProductArt seed={c.id} ratio="square" label={String(i + 1).padStart(2, "0")} />
                <div className="mt-3 flex items-center justify-between">
                  <span className="font-display text-sm font-semibold tracking-tight">
                    {c.name}
                  </span>
                  <ArrowUpRight className="h-4 w-4 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-accent" />
                </div>
              </Link>
            ))}
            {(categories.data ?? []).length === 0 && (
              <Card className="col-span-full">
                <CardContent className="p-6 text-sm text-muted-foreground">
                  No categories yet. Once an admin creates them they will appear here.
                </CardContent>
              </Card>
            )}
          </div>
        )}
      </section>

      {/* Featured products */}
      <section className="container">
        <SectionHeading
          eyebrow="In rotation"
          title="Newly listed"
          link={{ to: "/products?sort=createdAt,desc", label: "See all" }}
        />
        {products.isLoading ? (
          <Spinner label="Loading products…" />
        ) : (
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {(products.data?.content ?? []).map((p, i) => (
              <motion.div
                key={p.id}
                initial={{ opacity: 0, y: 12 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: "-50px" }}
                transition={{
                  duration: 0.5,
                  ease: [0.22, 1, 0.36, 1],
                  delay: i * 0.05,
                }}
              >
                <Link
                  to={`/products/${p.id}`}
                  className="group block"
                >
                  <ProductArt
                    seed={p.sku || p.id}
                    ratio="portrait"
                    label={`No. ${String(i + 1).padStart(2, "0")}`}
                    className="transition-transform duration-500 group-hover:scale-[1.01]"
                  />
                  <div className="mt-4 flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                        {p.sku}
                      </p>
                      <h3 className="mt-1 truncate font-display text-base font-semibold tracking-tight">
                        {p.name}
                      </h3>
                    </div>
                    <span className="shrink-0 font-mono text-sm">
                      {formatMoney(p.price)}
                    </span>
                  </div>
                </Link>
              </motion.div>
            ))}
            {(products.data?.content ?? []).length === 0 && (
              <Card className="col-span-full">
                <CardContent className="p-10 text-center">
                  <p className="text-sm text-muted-foreground">
                    No products yet. Sellers, the front page is yours to claim.
                  </p>
                  <Button asChild variant="accent" size="sm" className="mt-4">
                    <Link to="/admin/products/new">List your first product</Link>
                  </Button>
                </CardContent>
              </Card>
            )}
          </div>
        )}
      </section>

      {/* Editorial split — value prop card */}
      <section className="container">
        <div className="grid items-center gap-10 rounded-3xl border bg-primary p-10 text-primary-foreground md:grid-cols-2 md:p-16">
          <div>
            <p className="text-xs uppercase tracking-[0.18em] text-primary-foreground/55">
              For sellers
            </p>
            <h2 className="mt-3 font-display text-4xl font-semibold leading-tight tracking-tight md:text-5xl">
              Sell to people who notice.
            </h2>
            <p className="mt-4 max-w-md text-primary-foreground/70">
              Listings go live in minutes. Stripe, GST and shipping on us. We
              don't take a cut for being clever.
            </p>
            <Button asChild variant="accent" className="mt-6">
              <Link to="/register">
                Open a seller account <ArrowRight className="h-4 w-4" />
              </Link>
            </Button>
          </div>
          <div className="relative">
            <div
              className="aspect-[5/4] w-full rounded-2xl bg-grain ring-1 ring-white/10"
              style={{
                backgroundImage:
                  "linear-gradient(135deg, hsl(var(--accent) / 0.85), hsl(220 80% 30%))",
                backgroundBlendMode: "soft-light",
              }}
            />
          </div>
        </div>
      </section>
    </div>
  );
};

const SectionHeading: React.FC<{
  eyebrow: string;
  title: string;
  link?: { to: string; label: string };
}> = ({ eyebrow, title, link }) => (
  <div className="mb-8 flex items-end justify-between gap-4">
    <div>
      <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
        {eyebrow}
      </p>
      <h2 className="mt-2 font-display text-3xl font-semibold tracking-tight md:text-4xl">
        {title}
      </h2>
    </div>
    {link && (
      <Link
        to={link.to}
        className="inline-flex items-center gap-1 text-sm font-medium text-muted-foreground transition-colors hover:text-accent"
      >
        {link.label} <ArrowRight className="h-4 w-4" />
      </Link>
    )}
  </div>
);
