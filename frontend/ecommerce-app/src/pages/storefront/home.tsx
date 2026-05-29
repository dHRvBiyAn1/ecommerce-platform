import * as React from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, ArrowUpRight, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ProductArt } from "@/components/product-art";
import { listCategories, listProducts } from "@/api/products";
import { formatMoney } from "@/lib/utils";
import { ScrollAnimation } from "@/components/ui/scroll-animation";
import { CategorySkeleton } from "@/components/skeletons/category-skeleton";
import { ProductGridSkeleton } from "@/components/skeletons/product-card-skeleton";
import { DotField } from "@/components/bits/dot-field";
import { DecryptedText } from "@/components/bits/decrypted-text";
import { GlowButton } from "@/components/bits/glow-button";
import { AnimatedGradient } from "@/components/bits/animated-gradient";
import { BorderGlow } from "@/components/bits/border-glow";
import { TextType } from "@/components/bits/text-type";

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
      <section className="relative overflow-hidden pt-12">
        <DotField color="hsl(var(--accent))" gap={16} radius={140} />
        <div className="container relative">
          <ScrollAnimation type="slide-up">
            <div className="grid items-center gap-10 lg:grid-cols-12">
            <div className="lg:col-span-7">
              <span className="inline-flex items-center gap-2 rounded-full bg-secondary px-3 py-1 text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
                <Sparkles className="h-3.5 w-3.5 text-accent" />
                <DecryptedText text="Vol. 01 · The arrival edition" speed={80} />
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
                <GlowButton asChild size="lg" variant="accent">
                  <Link to="/products">
                    Shop the collection <ArrowRight className="h-4 w-4" />
                  </Link>
                </GlowButton>
                <Button asChild size="lg" variant="outline" className="backdrop-blur-sm">
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
                  <ProductArt seed="A1" ratio="square" label="No. 01" className="ring-1 ring-border" />
                  <ProductArt seed="A22-tall" ratio="portrait" label="No. 02" className="ring-1 ring-border" />
                </div>
                <div className="mt-12 space-y-3">
                  <ProductArt seed="B7" ratio="portrait" label="No. 03" className="ring-1 ring-border" />
                  <ProductArt seed="C4" ratio="square" label="No. 04" className="ring-1 ring-border" />
                </div>
              </div>
            </div>
          </div>
        </ScrollAnimation>
      </div>
    </section>

      {/* Categories strip */}
      <section className="container">
        <ScrollAnimation type="fade">
          <SectionHeading
            eyebrow="Browse"
            title="By interest"
            link={{ to: "/products", label: "All products" }}
          />
        </ScrollAnimation>
        
        {categories.isLoading ? (
          <CategorySkeleton />
        ) : (
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4 lg:grid-cols-6">
            {(categories.data ?? []).slice(0, 12).map((c, i) => (
              <ScrollAnimation key={c.id} type="scale" delay={i * 0.05}>
                <BorderGlow className="rounded-xl h-full">
                  <Link
                    to={`/categories/${c.id}`}
                    className="group block relative overflow-hidden rounded-[calc(var(--radius)-1px)] bg-card p-4 h-full transition-all active:scale-[0.98]"
                  >
                    <ProductArt seed={c.id} ratio="square" label={String(i + 1).padStart(2, "0")} />
                    <div className="mt-3 flex items-center justify-between">
                      <span className="font-display text-sm font-semibold tracking-tight">
                        {c.name}
                      </span>
                      <ArrowUpRight className="h-4 w-4 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-accent" />
                    </div>
                  </Link>
                </BorderGlow>
              </ScrollAnimation>
            ))}
            {(categories.data ?? []).length === 0 && (
              <Card className="col-span-full border-dashed">
                <CardContent className="p-6 text-sm text-muted-foreground">
                  <TextType text="No categories yet. Once an admin creates them they will appear here." speed={30} cursor={false} />
                </CardContent>
              </Card>
            )}
          </div>
        )}
      </section>

      {/* Featured products */}
      <section className="container">
        <ScrollAnimation type="fade">
          <SectionHeading
            eyebrow="In rotation"
            title="Newly listed"
            link={{ to: "/products?sort=createdAt,desc", label: "See all" }}
          />
        </ScrollAnimation>
        
        {products.isLoading ? (
          <ProductGridSkeleton />
        ) : (
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {(products.data?.content ?? []).map((p, i) => (
              <ScrollAnimation key={p.id} type="slide-up" delay={i * 0.05}>
                <BorderGlow className="rounded-2xl">
                  <Link
                    to={`/products/${p.id}`}
                    className="group block p-2"
                  >
                    <div className="relative overflow-hidden rounded-xl">
                      <ProductArt
                        seed={p.sku || p.id}
                        ratio="portrait"
                        label={`No. ${String(i + 1).padStart(2, "0")}`}
                        className="transition-transform duration-700 ease-out group-hover:scale-110"
                      />
                      <div className="absolute inset-0 bg-black/0 transition-colors duration-500 group-hover:bg-black/10" />
                    </div>
                    <div className="mt-4 flex items-start justify-between gap-3 px-1">
                      <div className="min-w-0">
                        <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground font-mono">
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
                </BorderGlow>
              </ScrollAnimation>
            ))}
            {(products.data?.content ?? []).length === 0 && (
              <Card className="col-span-full border-dashed">
                <CardContent className="p-10 text-center">
                  <p className="text-sm text-muted-foreground">
                    <TextType text="No products yet. Sellers, the front page is yours to claim." speed={30} />
                  </p>
                  <GlowButton asChild variant="accent" size="sm" className="mt-4">
                    <Link to="/admin/products/new">List your first product</Link>
                  </GlowButton>
                </CardContent>
              </Card>
            )}
          </div>
        )}
      </section>

      {/* Editorial split — value prop card */}
      <section className="container">
        <ScrollAnimation type="fade" viewport={{ amount: 0.3 }}>
          <div className="relative grid items-center gap-10 overflow-hidden rounded-3xl border bg-card p-10 text-card-foreground md:grid-cols-2 md:p-16">
            <AnimatedGradient className="opacity-10 dark:opacity-20" />
            <div className="relative z-10">
              <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                For sellers
              </p>
              <h2 className="mt-3 font-display text-4xl font-semibold leading-tight tracking-tight md:text-5xl">
                Sell to people who notice.
              </h2>
              <p className="mt-4 max-w-md text-muted-foreground text-lg text-balance">
                Listings go live in minutes. Stripe, GST and shipping on us. We
                don't take a cut for being clever.
              </p>
              <GlowButton asChild variant="accent" size="lg" className="mt-8 shadow-xl shadow-accent/10">
                <Link to="/register">
                  Open a seller account <ArrowRight className="h-4 w-4" />
                </Link>
              </GlowButton>
            </div>
            <div className="relative">
              <div
                className="aspect-[5/4] w-full rounded-2xl bg-grain shadow-2xl ring-1 ring-white/10"
                style={{
                  backgroundImage:
                    "linear-gradient(135deg, hsl(var(--accent) / 0.85), hsl(220 80% 30%))",
                  backgroundBlendMode: "soft-light",
                }}
              />
            </div>
          </div>
        </ScrollAnimation>
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
