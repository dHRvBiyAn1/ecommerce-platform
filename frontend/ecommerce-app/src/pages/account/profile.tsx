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
import { useAuthStore } from "@/stores/auth";

const ProfileSchema = z.object({
  displayName: z.string().min(2).max(80),
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

export const ProfilePage: React.FC = () => {
  const qc = useQueryClient();
  const setUser = useAuthStore((s) => s.setUser);
  const profile = useQuery({ queryKey: ["me"], queryFn: me });

  const profileForm = useForm<z.infer<typeof ProfileSchema>>({
    resolver: zodResolver(ProfileSchema),
    values: { displayName: profile.data?.displayName ?? "" },
  });

  const passwordForm = useForm<z.infer<typeof PasswordSchema>>({
    resolver: zodResolver(PasswordSchema),
    defaultValues: { oldPassword: "", newPassword: "", confirm: "" },
  });

  const profileMutation = useMutation({
    mutationFn: updateProfile,
    onSuccess: (u) => {
      setUser(u);
      qc.setQueryData(["me"], u);
      toast.success("Profile updated");
    },
    onError: (e: any) => toast.error(e?.message ?? "Update failed"),
  });

  const passwordMutation = useMutation({
    mutationFn: ({ oldPassword, newPassword }: { oldPassword: string; newPassword: string }) =>
      changePassword(oldPassword, newPassword),
    onSuccess: () => {
      passwordForm.reset({ oldPassword: "", newPassword: "", confirm: "" });
      toast.success("Password changed. You may be asked to sign in again next time.");
    },
    onError: (e: any) => toast.error(e?.message ?? "Could not change password"),
  });

  return (
    <div className="container py-12">
      <header className="mb-8">
        <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Account</p>
        <h1 className="mt-2 font-display text-4xl font-semibold tracking-tight">Profile</h1>
      </header>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Identity</CardTitle>
          </CardHeader>
          <CardContent>
            {profile.isLoading ? (
              <Spinner />
            ) : (
              <form
                onSubmit={profileForm.handleSubmit((v) => profileMutation.mutate(v))}
                className="space-y-4"
              >
                <div className="space-y-1.5">
                  <Label>Email</Label>
                  <Input value={profile.data?.email ?? ""} disabled />
                </div>
                <div className="space-y-1.5">
                  <Label>Display name</Label>
                  <Input {...profileForm.register("displayName")} />
                  {profileForm.formState.errors.displayName && (
                    <p className="text-xs text-destructive">
                      {profileForm.formState.errors.displayName.message}
                    </p>
                  )}
                </div>
                <Separator />
                <div className="text-xs text-muted-foreground">
                  Roles: {profile.data?.roles?.join(", ") || "—"}
                </div>
                <Button
                  variant="accent"
                  type="submit"
                  disabled={profileMutation.isPending}
                >
                  {profileMutation.isPending ? <Spinner /> : "Save"}
                </Button>
              </form>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Change password</CardTitle>
          </CardHeader>
          <CardContent>
            <form
              onSubmit={passwordForm.handleSubmit(({ oldPassword, newPassword }) =>
                passwordMutation.mutate({ oldPassword, newPassword }),
              )}
              className="space-y-4"
            >
              <div className="space-y-1.5">
                <Label>Current password</Label>
                <Input type="password" {...passwordForm.register("oldPassword")} />
              </div>
              <div className="space-y-1.5">
                <Label>New password</Label>
                <Input type="password" {...passwordForm.register("newPassword")} />
                {passwordForm.formState.errors.newPassword && (
                  <p className="text-xs text-destructive">
                    {passwordForm.formState.errors.newPassword.message}
                  </p>
                )}
              </div>
              <div className="space-y-1.5">
                <Label>Confirm new password</Label>
                <Input type="password" {...passwordForm.register("confirm")} />
                {passwordForm.formState.errors.confirm && (
                  <p className="text-xs text-destructive">
                    {passwordForm.formState.errors.confirm.message}
                  </p>
                )}
              </div>
              <Button variant="default" type="submit" disabled={passwordMutation.isPending}>
                {passwordMutation.isPending ? <Spinner /> : "Update password"}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
