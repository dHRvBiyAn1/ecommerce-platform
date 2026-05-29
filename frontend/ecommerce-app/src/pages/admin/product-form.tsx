import * as React from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { ProductArt } from "@/components/product-art";
import {
  createProduct,
  getProduct,
  listCategories,
  updateProduct,
} from "@/api/products";

const Schema = z.object({
  sku: z.string().min(2),
  name: z.string().min(2),
  description: z.string().min(10),
  categoryId: z.string().min(1),
  price: z.coerce.number().positive(),
  stockQuantity: z.coerce.number().int().nonnegative(),
  imageUrl: z.string().url().or(z.literal("")).optional(),
});

type Values = z.infer<typeof Schema>;

export const AdminProductFormPage: React.FC = () => {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { id } = useParams();
  const isEdit = Boolean(id);

  const categories = useQuery({ queryKey: ["categories"], queryFn: listCategories });
  const existing = useQuery({
    queryKey: ["product", id],
    queryFn: () => getProduct(id!),
    enabled: isEdit,
  });

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<Values>({
    resolver: zodResolver(Schema),
    defaultValues: {
      sku: "",
      name: "",
      description: "",
      categoryId: "",
      price: 0,
      stockQuantity: 0,
      imageUrl: "",
    },
  });

  // Hydrate edit form
  React.useEffect(() => {
    if (existing.data) {
      reset({
        sku: existing.data.sku,
        name: existing.data.name,
        description: existing.data.description,
        categoryId: existing.data.categoryId,
        price: existing.data.price,
        stockQuantity: existing.data.stockQuantity,
        imageUrl: existing.data.imageUrls?.[0] ?? "",
      });
    }
  }, [existing.data, reset]);

  const create = useMutation({
    mutationFn: (v: Values) => {
      const { imageUrl, ...rest } = v;
      return createProduct({
        ...rest,
        imageUrls: imageUrl ? [imageUrl] : [],
      });
    },
    onSuccess: () => {
      toast.success("Product created");
      qc.invalidateQueries({ queryKey: ["admin", "my-products"] });
      // Sellers and admins both land on the list after creating, so they
      // can immediately see the new row in context.
      navigate("/admin/products", { replace: true });
    },
    onError: (e: any) => toast.error(e?.message ?? "Failed"),
  });

  const update = useMutation({
    mutationFn: (v: Values) => {
      const { imageUrl, ...rest } = v;
      return updateProduct(id!, {
        ...rest,
        imageUrls: imageUrl ? [imageUrl] : [],
      });
    },
    onSuccess: () => {
      toast.success("Product updated");
      qc.invalidateQueries({ queryKey: ["admin", "my-products"] });
      navigate("/admin/products", { replace: true });
    },
    onError: (e: any) => toast.error(e?.message ?? "Failed"),
  });

  const sku = watch("sku");
  const name = watch("name");
  const imageUrl = watch("imageUrl");

  return (
    <div className="space-y-8">
      <Link
        to="/admin/products"
        className="inline-flex items-center gap-1 text-xs uppercase tracking-[0.18em] text-muted-foreground hover:text-accent"
      >
        <ChevronLeft className="h-3 w-3" /> Products
      </Link>

      <header>
        <h1 className="font-display text-3xl font-semibold tracking-tight">
          {isEdit ? "Edit product" : "New product"}
        </h1>
      </header>

      <form
        onSubmit={handleSubmit((v) => (isEdit ? update.mutate(v) : create.mutate(v)))}
        className="grid gap-8 lg:grid-cols-[1fr_22rem]"
      >
        <Card>
          <CardContent className="space-y-5 p-6">
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="SKU" error={errors.sku?.message}>
                <Input {...register("sku")} placeholder="ABC-001" />
              </Field>
              <Field label="Category" error={errors.categoryId?.message}>
                <Select
                  value={watch("categoryId") || undefined}
                  onValueChange={(v) => setValue("categoryId", v)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Choose…" />
                  </SelectTrigger>
                  <SelectContent>
                    {(categories.data ?? []).map((c) => (
                      <SelectItem key={c.id} value={c.id}>
                        {c.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field label="Name" error={errors.name?.message} className="sm:col-span-2">
                <Input {...register("name")} placeholder="Linen tote, oat" />
              </Field>
              <Field label="Image URL" error={errors.imageUrl?.message} className="sm:col-span-2">
                <Input {...register("imageUrl")} placeholder="https://images.unsplash.com/photo-..." />
              </Field>
              <Field label="Price (INR)" error={errors.price?.message}>
                <Input type="number" step="0.01" {...register("price")} />
              </Field>
              <Field label="Stock" error={errors.stockQuantity?.message}>
                <Input type="number" {...register("stockQuantity")} />
              </Field>
              <Field
                label="Description"
                error={errors.description?.message}
                className="sm:col-span-2"
              >
                <Textarea
                  rows={6}
                  {...register("description")}
                  placeholder="What makes this object worth keeping?"
                />
              </Field>
            </div>

            <div className="flex items-center gap-2 pt-2">
              <Button type="submit" variant="accent" disabled={isSubmitting}>
                {isSubmitting ? <Spinner /> : isEdit ? "Save changes" : "Create"}
              </Button>
              <Button asChild variant="outline" type="button">
                <Link to="/admin/products">Cancel</Link>
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card className="h-fit">
          <CardContent className="p-6">
            <h2 className="mb-4 font-display text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">
              Preview
            </h2>
            <ProductArt
              seed={sku || "preview"}
              ratio="portrait"
              label={name || "Untitled"}
              imageUrl={imageUrl}
            />
            <div className="mt-4 space-y-1">
              <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                SKU · {sku || "—"}
              </p>
              <p className="font-display text-base font-semibold tracking-tight">
                {name || "Untitled product"}
              </p>
              <p className="text-xs text-muted-foreground">
                Live previewing abstract gradient identity or custom photo if specified.
              </p>
            </div>
          </CardContent>
        </Card>
      </form>
    </div>
  );
};

const Field: React.FC<{
  label: string;
  error?: string;
  className?: string;
  children: React.ReactNode;
}> = ({ label, error, className, children }) => (
  <div className={`space-y-1.5 ${className ?? ""}`}>
    <Label>{label}</Label>
    {children}
    {error && <p className="text-xs text-destructive">{error}</p>}
  </div>
);
