import * as React from "react";
import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { ArrowRight, CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { login, me, register as registerApi } from "@/api/auth";
import { useAuthStore } from "@/stores/auth";

const Schema = z.object({
  displayName: z.string().min(2, "Min 2 characters").max(80),
  email: z.string().email("Enter a valid email"),
  password: z
    .string()
    .min(10, "Min 10 characters")
    .regex(/[a-z]/, "Add a lowercase letter")
    .regex(/[A-Z]/, "Add an uppercase letter")
    .regex(/\d/, "Add a digit"),
});

type FormValues = z.infer<typeof Schema>;

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const setAccessToken = useAuthStore((s) => s.setAccessToken);
  const setUser = useAuthStore((s) => s.setUser);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(Schema),
    mode: "onBlur",
    defaultValues: { displayName: "", email: "", password: "" },
  });

  const password = watch("password");
  const checks = [
    { ok: password?.length >= 10, label: "10+ characters" },
    { ok: /[a-z]/.test(password ?? ""), label: "lowercase" },
    { ok: /[A-Z]/.test(password ?? ""), label: "uppercase" },
    { ok: /\d/.test(password ?? ""), label: "digit" },
  ];

  async function onSubmit(values: FormValues) {
    try {
      await registerApi(values);
      // Auto sign-in
      const tokens = await login(values.email, values.password);
      setAccessToken(tokens.accessToken);
      const profile = await me();
      setUser(profile);
      toast.success("Welcome aboard.");
      navigate("/", { replace: true });
    } catch (err: any) {
      toast.error(err?.message ?? "Registration failed");
    }
  }

  return (
    <div className="space-y-8 animate-fade-in">
      <div>
        <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
          Create your account
        </p>
        <h1 className="mt-2 font-display text-4xl font-semibold tracking-tight">
          Join us
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Already have one?{" "}
          <Link to="/login" className="font-medium underline-offset-4 hover:underline">
            Sign in
          </Link>
        </p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
        <div className="space-y-1.5">
          <Label htmlFor="displayName">Display name</Label>
          <Input
            id="displayName"
            autoComplete="name"
            placeholder="What should we call you?"
            {...register("displayName")}
            aria-invalid={!!errors.displayName}
          />
          {errors.displayName && (
            <p className="text-xs text-destructive">{errors.displayName.message}</p>
          )}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            {...register("email")}
            aria-invalid={!!errors.email}
          />
          {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="password">Password</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            placeholder="••••••••••"
            {...register("password")}
            aria-invalid={!!errors.password}
          />
          <ul className="grid grid-cols-2 gap-1 pt-1 text-xs text-muted-foreground">
            {checks.map((c) => (
              <li
                key={c.label}
                className={`inline-flex items-center gap-1.5 ${c.ok ? "text-emerald-600" : ""}`}
              >
                <CheckCircle2 className={`h-3.5 w-3.5 ${c.ok ? "opacity-100" : "opacity-30"}`} />
                {c.label}
              </li>
            ))}
          </ul>
        </div>

        <Button
          type="submit"
          variant="accent"
          size="lg"
          disabled={isSubmitting}
          className="w-full"
        >
          {isSubmitting ? <Spinner /> : (
            <>
              Create account <ArrowRight className="h-4 w-4" />
            </>
          )}
        </Button>

        <p className="text-center text-xs text-muted-foreground">
          By creating an account you agree to the editorial-tier terms of service.
        </p>
      </form>
    </div>
  );
};
