import * as React from "react";
import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Spinner, PageSpinner } from "@/components/ui/spinner";
import { applyAsSeller, getMyApplication } from "@/api/seller";

const Schema = z.object({
  businessName: z.string().trim().min(2).max(180),
  gstin: z
    .string()
    .trim()
    .regex(/^[0-9A-Z]{15}$|^$/, "GSTIN must be 15 alphanumeric characters")
    .optional()
    .or(z.literal("")),
  contactPhone: z
    .string()
    .trim()
    .regex(/^\+?[0-9 ()-]{6,20}$/, "Invalid phone number"),
  pickup: z.object({
    fullName: z.string().trim().min(2).max(120),
    phone: z.string().trim().regex(/^\+?[0-9 ()-]{6,20}$/, "Invalid phone"),
    street: z.string().trim().min(3).max(200),
    city: z.string().trim().min(2).max(80),
    state: z.string().trim().min(2).max(80),
    zipCode: z.string().trim().min(3).max(20),
    country: z.string().trim().min(2).max(80),
  }),
  bankAccountLast4: z
    .string()
    .trim()
    .regex(/^[0-9]{4}$|^$/, "Last-4 must be 4 digits")
    .optional()
    .or(z.literal("")),
  notes: z.string().trim().max(4000).optional().or(z.literal("")),
});
type Values = z.infer<typeof Schema>;

const STATUS_VARIANT = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "destructive",
} as const;

/**
 * Customer-facing "apply to become a seller" page. Shows the current
 * application status if one exists, plus the form for new applications
 * (also reused after a rejection — submitting overwrites the rejected
 * application with a fresh PENDING one).
 */
export const BecomeSellerPage: React.FC = () => {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ["seller-application", "me"],
    queryFn: getMyApplication,
  });

  const form = useForm<Values>({
    resolver: zodResolver(Schema),
    defaultValues: {
      businessName: "",
      gstin: "",
      contactPhone: "",
      pickup: { fullName: "", phone: "", street: "", city: "", state: "", zipCode: "", country: "IN" },
      bankAccountLast4: "",
      notes: "",
    },
  });

  const apply = useMutation({
    mutationFn: (v: Values) =>
      applyAsSeller({
        businessName: v.businessName,
        gstin: v.gstin || undefined,
        contactPhone: v.contactPhone,
        pickupAddress: v.pickup,
        bankAccountLast4: v.bankAccountLast4 || undefined,
        notes: v.notes || undefined,
      }),
    onSuccess: () => {
      toast.success("Application submitted! An admin will review it shortly.");
      qc.invalidateQueries({ queryKey: ["seller-application", "me"] });
    },
    onError: (e: any) => toast.error(e?.message ?? "Submission failed"),
  });

  if (isLoading) return <PageSpinner />;

  const showForm = !data || data.status === "REJECTED";

  return (
    <div className="container max-w-3xl py-12">
      <header className="mb-8">
        <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Account</p>
        <h1 className="mt-2 font-display text-4xl font-semibold tracking-tight">
          Become a seller
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          List your products on the platform. Applications are reviewed by an admin
          and typically take 1-2 business days.
        </p>
      </header>

      {data && (
        <Card className="mb-6">
          <CardHeader className="flex flex-row items-center justify-between gap-2">
            <CardTitle>Your application</CardTitle>
            <Badge variant={STATUS_VARIANT[data.status]}>{data.status}</Badge>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div>
              <span className="text-muted-foreground">Business name:</span>{" "}
              <span className="font-medium">{data.businessName}</span>
            </div>
            <div>
              <span className="text-muted-foreground">Submitted:</span>{" "}
              <span>{new Date(data.submittedAt).toLocaleString()}</span>
            </div>
            {data.status === "REJECTED" && data.rejectionReason && (
              <div className="rounded border border-destructive/30 bg-destructive/5 p-3">
                <div className="text-xs font-semibold uppercase tracking-wide text-destructive">
                  Reason for rejection
                </div>
                <div className="mt-1">{data.rejectionReason}</div>
                <div className="mt-2 text-xs text-muted-foreground">
                  You can submit a new application below addressing this feedback.
                </div>
              </div>
            )}
            {data.status === "APPROVED" && (
              <div className="rounded border border-emerald-500/30 bg-emerald-500/5 p-3 text-sm">
                Welcome! You can now{" "}
                <Link to="/admin/products/new" className="underline">
                  list your first product
                </Link>
                .
              </div>
            )}
            {data.status === "PENDING" && (
              <div className="text-sm text-muted-foreground">
                You'll get an email once your application is reviewed.
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {showForm && (
        <Card>
          <CardHeader>
            <CardTitle>{data ? "Re-apply" : "Apply"}</CardTitle>
          </CardHeader>
          <CardContent>
            <form
              onSubmit={form.handleSubmit((v) => apply.mutate(v))}
              className="grid gap-4 sm:grid-cols-2"
            >
              <Field
                label="Business name"
                error={form.formState.errors.businessName?.message}
                className="sm:col-span-2"
              >
                <Input {...form.register("businessName")} placeholder="Heirloom Goods Pvt Ltd" />
              </Field>

              <Field
                label="GSTIN (optional)"
                error={form.formState.errors.gstin?.message}
              >
                <Input {...form.register("gstin")} placeholder="22AAAAA0000A1Z5" maxLength={15} />
              </Field>

              <Field
                label="Contact phone"
                error={form.formState.errors.contactPhone?.message}
              >
                <Input inputMode="tel" {...form.register("contactPhone")} placeholder="+91 98765 43210" />
              </Field>

              <div className="sm:col-span-2 mt-2">
                <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  Pickup address
                </p>
              </div>

              <Field label="Full name" error={form.formState.errors.pickup?.fullName?.message} className="sm:col-span-2">
                <Input {...form.register("pickup.fullName")} />
              </Field>
              <Field label="Phone" error={form.formState.errors.pickup?.phone?.message}>
                <Input inputMode="tel" {...form.register("pickup.phone")} />
              </Field>
              <Field label="ZIP / postal code" error={form.formState.errors.pickup?.zipCode?.message}>
                <Input inputMode="numeric" {...form.register("pickup.zipCode")} />
              </Field>
              <Field label="Street" error={form.formState.errors.pickup?.street?.message} className="sm:col-span-2">
                <Input {...form.register("pickup.street")} />
              </Field>
              <Field label="City" error={form.formState.errors.pickup?.city?.message}>
                <Input {...form.register("pickup.city")} />
              </Field>
              <Field label="State" error={form.formState.errors.pickup?.state?.message}>
                <Input {...form.register("pickup.state")} />
              </Field>
              <Field label="Country" error={form.formState.errors.pickup?.country?.message} className="sm:col-span-2">
                <Input {...form.register("pickup.country")} />
              </Field>

              <Field
                label="Bank account last 4 (optional)"
                error={form.formState.errors.bankAccountLast4?.message}
              >
                <Input
                  inputMode="numeric"
                  maxLength={4}
                  {...form.register("bankAccountLast4")}
                  placeholder="1234"
                />
              </Field>

              <Field
                label="Anything you'd like the reviewer to know?"
                error={form.formState.errors.notes?.message}
                className="sm:col-span-2"
              >
                <Textarea rows={4} {...form.register("notes")} />
              </Field>

              <div className="sm:col-span-2 flex items-center justify-between gap-2 pt-2">
                <Button type="button" variant="ghost" onClick={() => navigate(-1)}>
                  Cancel
                </Button>
                <Button type="submit" variant="accent" disabled={apply.isPending}>
                  {apply.isPending ? <Spinner /> : "Submit application"}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}
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
