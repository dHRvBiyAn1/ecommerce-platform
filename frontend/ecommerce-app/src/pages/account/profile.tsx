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

  return (
    <div className="container py-12">
      <header className="mb-8">
        <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Account</p>
        <h1 className="mt-2 font-display text-4xl font-semibold tracking-tight">Profile</h1>
      </header>

      {profile.isLoading ? (
        <Spinner />
      ) : data ? (
        <div className="grid gap-6 lg:grid-cols-2">
          <IdentityCard
            data={data}
            onSaved={(u) => {
              setUser(u);
              qc.setQueryData(["me"], u);
            }}
          />

          {/*
            Hide the change-password card for users without a LOCAL credential
            (e.g. signed in via Google). They have no password to change.
          */}
          {data.hasPassword !== false && <PasswordCard />}

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
    <Card>
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
    <Card>
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
    <Card>
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
