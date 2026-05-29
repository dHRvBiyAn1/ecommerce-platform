import * as React from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Spinner } from "@/components/ui/spinner";
import { changePassword, me, updateProfile } from "@/api/auth";
import type { Address, UserProfile } from "@/api/types";
import { useAuthStore } from "@/stores/auth";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { User, Shield, MapPin, Activity, BadgeCheck, Phone, Mail } from "lucide-react";

// ---------------------------------------------------------------------------
// Schemas
// ---------------------------------------------------------------------------

const ProfileSchema = z.object({
  displayName: z.string().min(2).max(80),
  phone: z
    .string()
    .trim()
    .regex(/^\+?[0-9 ()-]{6,20}$/, "Enter a valid phone number")
    .or(z.literal("")),
  imageUrl: z
    .string()
    .trim()
    .url("Enter a valid image URL")
    .or(z.literal("")),
});

const AddressSchema = z.object({
  fullName: z.string().trim().min(2, "Required").max(120),
  phone: z
    .string()
    .trim()
    .regex(/^\+?[0-9 ()-]{6,20}$/, "Enter a valid phone number"),
  street: z.string().trim().min(3, "Required").max(200),
  city: z.string().trim().min(2, "Required").max(80),
  state: z.string().trim().min(2, "Required").max(80),
  zipCode: z.string().trim().min(3, "Required").max(20),
  country: z.string().trim().min(2, "Required").max(80),
});

const PasswordSchema = z
  .object({
    oldPassword: z.string().min(1, "Required"),
    newPassword: z
      .string()
      .min(10, "Min 10 characters")
      .regex(/[a-z]/, "Add a lowercase letter")
      .regex(/[A-Z]/, "Add an uppercase letter")
      .regex(/\d/, "Add a digit"),
    confirm: z.string().min(1),
  })
  .refine((v) => v.newPassword === v.confirm, {
    path: ["confirm"],
    message: "Passwords don't match",
  });

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------

export const ProfilePage: React.FC = () => {
  const qc = useQueryClient();
  const setUser = useAuthStore((s) => s.setUser);
  const profile = useQuery({ queryKey: ["me"], queryFn: me });
  const data = profile.data;

  const initials = data
    ? (data.displayName ?? data.email)
        .split(" ")
        .map((s) => s.charAt(0).toUpperCase())
        .slice(0, 2)
        .join("")
    : "?";

  return (
    <div className="container relative py-12">
      {/* Decorative ambient gradients */}
      <div className="absolute inset-0 -z-10 flex justify-center overflow-hidden">
        <div className="h-96 w-[600px] rounded-full bg-accent/5 blur-3xl" />
        <div className="ml-96 h-80 w-80 rounded-full bg-destructive/5 blur-3xl" />
      </div>

      <header className="mb-10 flex flex-col md:flex-row md:items-end md:justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Account settings</p>
          <h1 className="mt-2 font-display text-4xl font-semibold tracking-tight">Profile</h1>
        </div>
      </header>

      {profile.isLoading ? (
        <Spinner />
      ) : data ? (
        <div className="space-y-8">
          {/* Bento Header Grid */}
          <div className="grid gap-6 md:grid-cols-3">
            {/* Bento Block 1: User Profile Header (Glassmorphic) */}
            <div className="md:col-span-2 relative overflow-hidden rounded-2xl border border-white/10 bg-card/45 p-6 shadow-xl backdrop-blur-md flex flex-col justify-between sm:flex-row sm:items-center gap-6">
              <div className="absolute -right-20 -top-20 h-48 w-48 rounded-full bg-accent/10 blur-2xl" />
              <div className="flex items-center gap-6 min-w-0">
                {data.imageUrl ? (
                  <img
                    src={data.imageUrl}
                    alt={data.displayName ?? "Profile"}
                    className="h-20 w-20 rounded-full object-cover ring-2 ring-accent/30"
                  />
                ) : (
                  <div className="grid h-20 w-20 place-items-center rounded-full bg-accent/15 text-accent font-display text-2xl font-bold">
                    {initials}
                  </div>
                )}
                <div className="min-w-0">
                  <div className="inline-flex items-center gap-2">
                    <h2 className="font-display text-2xl font-bold tracking-tight truncate">
                      {data.displayName ?? "Dear Member"}
                    </h2>
                    {data.roles?.includes("ADMIN") && (
                      <span className="inline-flex items-center gap-1 rounded bg-accent/20 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-accent">
                        <BadgeCheck className="h-3.5 w-3.5" /> Staff
                      </span>
                    )}
                  </div>
                  <p className="text-sm text-muted-foreground flex items-center gap-2 mt-1 truncate">
                    <Mail className="h-3.5 w-3.5" /> {data.email}
                  </p>
                  {data.phone && (
                    <p className="text-sm text-muted-foreground flex items-center gap-2 mt-0.5 truncate">
                      <Phone className="h-3.5 w-3.5" /> {data.phone}
                    </p>
                  )}
                </div>
              </div>
            </div>

            {/* Bento Block 2: Quick Status Info */}
            <div className="rounded-2xl border border-white/10 bg-card/40 p-6 shadow-xl backdrop-blur-md flex flex-col justify-between">
              <div>
                <p className="text-xs uppercase tracking-widest text-muted-foreground font-mono">Platform Access</p>
                <div className="mt-3 space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">Authorized roles</span>
                    <span className="font-medium text-foreground">{data.roles?.join(", ") || "Member"}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">Auth method</span>
                    <span className="font-medium text-foreground">{data.hasPassword === false ? "Google SSO" : "Local Password"}</span>
                  </div>
                </div>
              </div>
              <div className="mt-4 pt-4 border-t border-white/5 flex items-center gap-2 text-xs text-muted-foreground">
                <Activity className="h-3.5 w-3.5 text-accent animate-pulse" />
                <span>Account status: Healthy</span>
              </div>
            </div>
          </div>

          {/* Bento Tabs Container */}
          <Tabs defaultValue="identity" className="w-full">
            <TabsList className="w-full justify-start border-b border-white/10 bg-transparent h-auto p-0 mb-8 rounded-none gap-8">
              <TabsTrigger
                value="identity"
                className="bg-transparent border-b-2 border-transparent rounded-none px-1 py-3 text-sm font-medium tracking-wide text-muted-foreground data-[state=active]:border-accent data-[state=active]:text-foreground data-[state=active]:bg-transparent shadow-none"
              >
                <User className="mr-2 h-4 w-4" /> Personal info
              </TabsTrigger>
              {data.hasPassword !== false && (
                <TabsTrigger
                  value="security"
                  className="bg-transparent border-b-2 border-transparent rounded-none px-1 py-3 text-sm font-medium tracking-wide text-muted-foreground data-[state=active]:border-accent data-[state=active]:text-foreground data-[state=active]:bg-transparent shadow-none"
                >
                  <Shield className="mr-2 h-4 w-4" /> Security
                </TabsTrigger>
              )}
              <TabsTrigger
                value="addresses"
                className="bg-transparent border-b-2 border-transparent rounded-none px-1 py-3 text-sm font-medium tracking-wide text-muted-foreground data-[state=active]:border-accent data-[state=active]:text-foreground data-[state=active]:bg-transparent shadow-none"
              >
                <MapPin className="mr-2 h-4 w-4" /> Address book
              </TabsTrigger>
            </TabsList>

            <TabsContent value="identity" className="mt-0 focus-visible:ring-0">
              <div className="max-w-2xl">
                <IdentityCard
                  data={data}
                  onSaved={(u) => {
                    setUser(u);
                    qc.setQueryData(["me"], u);
                  }}
                />
              </div>
            </TabsContent>

            {data.hasPassword !== false && (
              <TabsContent value="security" className="mt-0 focus-visible:ring-0">
                <div className="max-w-2xl">
                  <PasswordCard />
                </div>
              </TabsContent>
            )}

            <TabsContent value="addresses" className="mt-0 focus-visible:ring-0">
              <div className="grid gap-6 md:grid-cols-2">
                <AddressCard
                  title="Shipping address"
                  description="We'll ship every order here unless you choose another at checkout."
                  value={data.shippingAddress ?? null}
                  onSave={(addr) =>
                    updateProfile({ shippingAddress: addr }).then((u) => {
                      qc.setQueryData(["me"], u);
                      setUser(u);
                    })
                  }
                />

                <AddressCard
                  title="Billing address"
                  description="Used on invoices and receipts."
                  value={data.billingAddress ?? null}
                  copyFromValue={data.shippingAddress ?? null}
                  onSave={(addr) =>
                    updateProfile({ billingAddress: addr }).then((u) => {
                      qc.setQueryData(["me"], u);
                      setUser(u);
                    })
                  }
                />
              </div>
            </TabsContent>
          </Tabs>
        </div>
      ) : null}
    </div>
  );
};

// ---------------------------------------------------------------------------
// Identity card (display name, phone, image URL)
// ---------------------------------------------------------------------------

const IdentityCard: React.FC<{
  data: UserProfile;
  onSaved: (u: UserProfile) => void;
}> = ({ data, onSaved }) => {
  const form = useForm<z.infer<typeof ProfileSchema>>({
    resolver: zodResolver(ProfileSchema),
    values: {
      displayName: data.displayName ?? "",
      phone: data.phone ?? "",
      imageUrl: data.imageUrl ?? "",
    },
  });

  const mutation = useMutation({
    mutationFn: (v: z.infer<typeof ProfileSchema>) =>
      updateProfile({
        displayName: v.displayName,
        phone: v.phone || null,
        imageUrl: v.imageUrl || null,
      }),
    onSuccess: (u) => {
      toast.success("Profile updated");
      onSaved(u);
    },
    onError: (e: any) => toast.error(e?.message ?? "Update failed"),
  });

  const initials = (data.displayName ?? data.email)
    .split(" ")
    .map((s) => s.charAt(0).toUpperCase())
    .slice(0, 2)
    .join("");

  return (
    <Card className="backdrop-blur-md bg-card/45 border border-white/10 shadow-xl rounded-2xl transition-all duration-300 hover:border-accent/20">
      <CardHeader>
        <CardTitle>Identity</CardTitle>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
          className="space-y-4"
        >
          <div className="flex items-center gap-4">
            {data.imageUrl ? (
              // eslint-disable-next-line jsx-a11y/img-redundant-alt
              <img
                src={data.imageUrl}
                alt="Profile photo"
                className="h-16 w-16 rounded-full object-cover"
              />
            ) : (
              <div className="grid h-16 w-16 place-items-center rounded-full bg-secondary text-lg font-semibold">
                {initials || "?"}
              </div>
            )}
            <div className="flex-1 space-y-1.5">
              <Label htmlFor="imageUrl">Profile image URL</Label>
              <Input
                id="imageUrl"
                placeholder="https://…"
                {...form.register("imageUrl")}
              />
              {form.formState.errors.imageUrl && (
                <p className="text-xs text-destructive">
                  {form.formState.errors.imageUrl.message}
                </p>
              )}
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>Email</Label>
            <Input value={data.email} disabled />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="displayName">Display name</Label>
              <Input id="displayName" {...form.register("displayName")} />
              {form.formState.errors.displayName && (
                <p className="text-xs text-destructive">
                  {form.formState.errors.displayName.message}
                </p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="phone">Mobile number</Label>
              <Input
                id="phone"
                placeholder="+91 98765 43210"
                inputMode="tel"
                {...form.register("phone")}
              />
              {form.formState.errors.phone && (
                <p className="text-xs text-destructive">
                  {form.formState.errors.phone.message}
                </p>
              )}
            </div>
          </div>

          <Separator />
          <div className="text-xs text-muted-foreground">
            Roles: {data.roles?.join(", ") || "—"}
          </div>

          <Button variant="accent" type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? <Spinner /> : "Save"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
};

// ---------------------------------------------------------------------------
// Password card
// ---------------------------------------------------------------------------

const PasswordCard: React.FC = () => {
  const form = useForm<z.infer<typeof PasswordSchema>>({
    resolver: zodResolver(PasswordSchema),
    defaultValues: { oldPassword: "", newPassword: "", confirm: "" },
  });

  const mutation = useMutation({
    mutationFn: ({ oldPassword, newPassword }: { oldPassword: string; newPassword: string }) =>
      changePassword(oldPassword, newPassword),
    onSuccess: () => {
      form.reset({ oldPassword: "", newPassword: "", confirm: "" });
      toast.success("Password changed. You may be asked to sign in again next time.");
    },
    onError: (e: any) => toast.error(e?.message ?? "Could not change password"),
  });

  return (
    <Card className="backdrop-blur-md bg-card/45 border border-white/10 shadow-xl rounded-2xl transition-all duration-300 hover:border-accent/20">
      <CardHeader>
        <CardTitle>Change password</CardTitle>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={form.handleSubmit(({ oldPassword, newPassword }) =>
            mutation.mutate({ oldPassword, newPassword }),
          )}
          className="space-y-4"
        >
          <div className="space-y-1.5">
            <Label>Current password</Label>
            <Input type="password" {...form.register("oldPassword")} />
          </div>
          <div className="space-y-1.5">
            <Label>New password</Label>
            <Input type="password" {...form.register("newPassword")} />
            {form.formState.errors.newPassword && (
              <p className="text-xs text-destructive">
                {form.formState.errors.newPassword.message}
              </p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label>Confirm new password</Label>
            <Input type="password" {...form.register("confirm")} />
            {form.formState.errors.confirm && (
              <p className="text-xs text-destructive">
                {form.formState.errors.confirm.message}
              </p>
            )}
          </div>
          <Button variant="default" type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? <Spinner /> : "Update password"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
};

// ---------------------------------------------------------------------------
// Address card (used for shipping AND billing)
// ---------------------------------------------------------------------------

const AddressCard: React.FC<{
  title: string;
  description?: string;
  value: Address | null;
  copyFromValue?: Address | null;
  onSave: (addr: Address) => Promise<void>;
}> = ({ title, description, value, copyFromValue, onSave }) => {
  const form = useForm<z.infer<typeof AddressSchema>>({
    resolver: zodResolver(AddressSchema),
    values: {
      fullName: value?.fullName ?? "",
      phone: value?.phone ?? "",
      street: value?.street ?? "",
      city: value?.city ?? "",
      state: value?.state ?? "",
      zipCode: value?.zipCode ?? "",
      country: value?.country ?? "IN",
    },
  });

  const [saving, setSaving] = React.useState(false);

  const handleCopy = () => {
    if (!copyFromValue) return;
    form.reset({
      fullName: copyFromValue.fullName ?? "",
      phone: copyFromValue.phone ?? "",
      street: copyFromValue.street ?? "",
      city: copyFromValue.city ?? "",
      state: copyFromValue.state ?? "",
      zipCode: copyFromValue.zipCode ?? "",
      country: copyFromValue.country ?? "IN",
    });
  };

  return (
    <Card className="backdrop-blur-md bg-card/45 border border-white/10 shadow-xl rounded-2xl transition-all duration-300 hover:border-accent/20">
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        {description && (
          <p className="text-sm text-muted-foreground">{description}</p>
        )}
      </CardHeader>
      <CardContent>
        <form
          onSubmit={form.handleSubmit(async (v) => {
            setSaving(true);
            try {
              await onSave(v);
              toast.success(`${title} saved`);
            } catch (e: any) {
              toast.error(e?.message ?? "Save failed");
            } finally {
              setSaving(false);
            }
          })}
          className="grid gap-3 sm:grid-cols-2"
        >
          <Field label="Full name" error={form.formState.errors.fullName?.message} className="sm:col-span-2">
            <Input {...form.register("fullName")} />
          </Field>
          <Field label="Phone" error={form.formState.errors.phone?.message}>
            <Input inputMode="tel" {...form.register("phone")} />
          </Field>
          <Field label="ZIP / postal code" error={form.formState.errors.zipCode?.message}>
            <Input inputMode="numeric" {...form.register("zipCode")} />
          </Field>
          <Field label="Street" error={form.formState.errors.street?.message} className="sm:col-span-2">
            <Input {...form.register("street")} />
          </Field>
          <Field label="City" error={form.formState.errors.city?.message}>
            <Input {...form.register("city")} />
          </Field>
          <Field label="State" error={form.formState.errors.state?.message}>
            <Input {...form.register("state")} />
          </Field>
          <Field label="Country" error={form.formState.errors.country?.message} className="sm:col-span-2">
            <Input {...form.register("country")} />
          </Field>

          <div className="sm:col-span-2 flex items-center justify-between gap-2 pt-2">
            <Button type="submit" variant="accent" disabled={saving}>
              {saving ? <Spinner /> : "Save"}
            </Button>
            {copyFromValue &&
              !(copyFromValue.fullName == null && copyFromValue.street == null) && (
                <Button type="button" variant="ghost" size="sm" onClick={handleCopy}>
                  Same as shipping
                </Button>
              )}
          </div>
        </form>
      </CardContent>
    </Card>
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
