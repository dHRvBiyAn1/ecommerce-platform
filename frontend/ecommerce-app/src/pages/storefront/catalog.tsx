import * as React from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { SlidersHorizontal } from "lucide-react";
import {
  listCategories,
  listProducts,
  productByPrice,
  productsByCategory,
  searchProducts,
} from "@/api/products";
import { ProductArt } from "@/components/product-art";
import { Skeleton } from "@/components/ui/spinner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetBody,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Seo } from "@/components/seo";
import { formatMoney } from "@/lib/utils";

const SORT_OPTIONS: { value: string; label: string }[] = [
  { value: "createdAt,desc", label: "Newest" },
  { value: "price,asc", label: "Price · low to high" },
  { value: "price,desc", label: "Price · high to low" },
  { value: "name,asc", label: "Alphabetical" },
];

export const CatalogPage: React.FC = () => {
  const { id: routeCategoryId } = useParams();
  const [params, setParams] = useSearchParams();
  const [filterOpen, setFilterOpen] = React.useState(false);

  const keyword = params.get("keyword") ?? "";
  const minPrice = params.get("minPrice");
  const maxPrice = params.get("maxPrice");
  const sort = params.get("sort") ?? "createdAt,desc";
  const page = Number(params.get("page") ?? 0);
  const size = 12;

  const queryKey: any[] = [
    "products",
    { routeCategoryId, keyword, minPrice, maxPrice, sort, page },
  ];
  const { data, isFetching } = useQuery({
    queryKey,
    queryFn: async () => {
      if (keyword) return searchProducts(keyword, { page, size });
      if (routeCategoryId) return productsByCategory(routeCategoryId, { page, size });
      if (minPrice && maxPrice)
        return productByPrice(Number(minPrice), Number(maxPrice), { page, size });
      return listProducts({ page, size, sort });
    },
  });

  const categories = useQuery({ queryKey: ["categories"], queryFn: listCategories });

  function patchParams(patch: Record<string, string | null>) {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      if (v === null || v === "") next.delete(k);
      else next.set(k, v);
    }
    if (!("page" in patch)) next.delete("page");
    setParams(next, { replace: true });
  }

  const products = data?.content ?? [];

  const Filters = (
    <div className="space-y-8">
      <FilterSection title="Search">
        <SearchBox initial={keyword} onSubmit={(v) => {
          patchParams({ keyword: v || null });
          setFilterOpen(false);
        }} />
      </FilterSection>

      <FilterSection title="Categories">
        <ul className="space-y-1.5 text-sm">
          <li>
            <Link
              to="/products"
              onClick={() => setFilterOpen(false)}
              className={`block rounded-md px-2 py-1.5 hover:bg-secondary ${
                !routeCategoryId ? "bg-secondary font-medium" : ""
              }`}
            >
              All
            </Link>
          </li>
          {(categories.data ?? []).map((c) => (
            <li key={c.id}>
              <Link
                to={`/categories/${c.id}`}
                onClick={() => setFilterOpen(false)}
                className={`block rounded-md px-2 py-1.5 hover:bg-secondary ${
                  routeCategoryId === c.id ? "bg-secondary font-medium" : ""
                }`}
              >
                {c.name}
              </Link>
            </li>
          ))}
        </ul>
      </FilterSection>

      <FilterSection title="Price">
        <PriceFilter
          min={minPrice ? Number(minPrice) : undefined}
          max={maxPrice ? Number(maxPrice) : undefined}
          onApply={(min, max) => {
            patchParams({
              minPrice: min != null ? String(min) : null,
              maxPrice: max != null ? String(max) : null,
            });
            setFilterOpen(false);
          }}
        />
      </FilterSection>
    </div>
  );

  return (
    <div className="container py-8 sm:py-12">
      <Seo
        title={keyword ? `Search · ${keyword}` : "Catalog"}
        description={
          keyword
            ? `Search results for "${keyword}".`
            : "Browse the full catalog — newly listed first."
        }
      />

      <header className="mb-6 flex flex-wrap items-end justify-between gap-4 sm:mb-10">
        <div>
          <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
            Catalog
          </p>
          <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight sm:text-4xl md:text-5xl">
            {keyword
              ? `Results for “${keyword}”`
              : routeCategoryId
              ? "Category"
              : "All products"}
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            {data?.totalElements ?? 0} items
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 sm:gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setFilterOpen(true)}
            className="lg:hidden"
          >
            <SlidersHorizontal className="h-4 w-4" /> Filters
          </Button>
          <Select
            value={sort}
            onValueChange={(v) => patchParams({ sort: v, keyword: keyword || null })}
          >
            <SelectTrigger className="w-40 sm:w-48">
              <SelectValue placeholder="Sort" />
            </SelectTrigger>
            <SelectContent>
              {SORT_OPTIONS.map((o) => (
                <SelectItem key={o.value} value={o.value}>
                  {o.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </header>

      <div className="grid gap-10 lg:grid-cols-[16rem_1fr]">
        <aside className="hidden lg:block" aria-label="Filters">
          {Filters}
        </aside>

        {/* Mobile filter sheet */}
        <Sheet open={filterOpen} onOpenChange={setFilterOpen}>
          <SheetContent side="left" aria-label="Filters">
            <SheetHeader>
              <SheetTitle>Filters</SheetTitle>
            </SheetHeader>
            <SheetBody>{Filters}</SheetBody>
          </SheetContent>
        </Sheet>

        <section aria-busy={isFetching}>
          {isFetching && products.length === 0 ? (
            <div className="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3">
              {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="space-y-3">
                  <Skeleton className="aspect-[3/4] w-full" />
                  <Skeleton className="h-4 w-2/3" />
                  <Skeleton className="h-4 w-1/3" />
                </div>
              ))}
            </div>
          ) : products.length === 0 ? (
            <Card>
              <CardContent className="p-10 text-center">
                <p className="font-display text-lg">Nothing matches that yet.</p>
                <p className="mt-2 text-sm text-muted-foreground">
                  Try a different keyword or clear the filters.
                </p>
                <Button asChild variant="outline" className="mt-4">
                  <Link to="/products">Reset</Link>
                </Button>
              </CardContent>
            </Card>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3">
                {products.map((p, i) => (
                  <motion.div
                    key={p.id}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.3, delay: Math.min(i * 0.03, 0.3) }}
                  >
                    <Link to={`/products/${p.id}`} className="group block">
                      <ProductArt
                        seed={p.sku || p.id}
                        ratio="portrait"
                        label={String(i + 1).padStart(2, "0")}
                      />
                      <div className="mt-3 flex items-start justify-between gap-3 sm:mt-4">
                        <div className="min-w-0">
                          <p className="text-[10px] uppercase tracking-[0.18em] text-muted-foreground sm:text-xs">
                            {p.sku}
                          </p>
                          <h3 className="mt-1 truncate font-display text-sm font-semibold tracking-tight sm:text-base">
                            {p.name}
                          </h3>
                        </div>
                        <span className="shrink-0 font-mono text-xs sm:text-sm">
                          {formatMoney(p.price)}
                        </span>
                      </div>
                      <div className="mt-2 flex items-center gap-2">
                        {p.stockQuantity === 0 ? (
                          <Badge variant="destructive">Sold out</Badge>
                        ) : p.stockQuantity < 5 ? (
                          <Badge variant="warning">Only {p.stockQuantity} left</Badge>
                        ) : null}
                      </div>
                    </Link>
                  </motion.div>
                ))}
              </div>

              <Pagination
                page={page}
                totalPages={data?.totalPages ?? 1}
                onChange={(p) => patchParams({ page: p > 0 ? String(p) : null })}
              />
            </>
          )}
        </section>
      </div>
    </div>
  );
};

const FilterSection: React.FC<{ title: string; children: React.ReactNode }> = ({
  title,
  children,
}) => (
  <section>
    <h3 className="mb-3 font-display text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
      {title}
    </h3>
    {children}
  </section>
);

const SearchBox: React.FC<{
  initial: string;
  onSubmit: (v: string) => void;
}> = ({ initial, onSubmit }) => {
  const [value, setValue] = React.useState(initial);
  React.useEffect(() => setValue(initial), [initial]);
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(value.trim());
      }}
      className="flex flex-col gap-2"
      role="search"
    >
      <label htmlFor="cat-search" className="sr-only">Search catalog</label>
      <Input
        id="cat-search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Find…"
      />
      <Button type="submit" variant="default" size="sm">
        Apply
      </Button>
    </form>
  );
};

const PriceFilter: React.FC<{
  min?: number;
  max?: number;
  onApply: (min?: number, max?: number) => void;
}> = ({ min, max, onApply }) => {
  const [lo, setLo] = React.useState<string>(min != null ? String(min) : "");
  const [hi, setHi] = React.useState<string>(max != null ? String(max) : "");
  return (
    <div className="space-y-2">
      <div className="grid grid-cols-2 gap-2">
        <label className="sr-only" htmlFor="price-min">Minimum price</label>
        <Input
          id="price-min"
          inputMode="numeric"
          value={lo}
          onChange={(e) => setLo(e.target.value)}
          placeholder="Min"
        />
        <label className="sr-only" htmlFor="price-max">Maximum price</label>
        <Input
          id="price-max"
          inputMode="numeric"
          value={hi}
          onChange={(e) => setHi(e.target.value)}
          placeholder="Max"
        />
      </div>
      <Button
        size="sm"
        variant="outline"
        className="w-full"
        onClick={() =>
          onApply(lo ? Number(lo) : undefined, hi ? Number(hi) : undefined)
        }
      >
        Apply
      </Button>
    </div>
  );
};

const Pagination: React.FC<{
  page: number;
  totalPages: number;
  onChange: (p: number) => void;
}> = ({ page, totalPages, onChange }) => {
  if (totalPages <= 1) return null;
  return (
    <nav className="mt-12 flex items-center justify-between" aria-label="Pagination">
      <Button
        variant="ghost"
        disabled={page === 0}
        onClick={() => onChange(Math.max(0, page - 1))}
      >
        ← Previous
      </Button>
      <span className="text-sm text-muted-foreground">
        Page {page + 1} of {totalPages}
      </span>
      <Button
        variant="ghost"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Next →
      </Button>
    </nav>
  );
};
