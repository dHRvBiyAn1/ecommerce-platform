import * as React from "react";
import { useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Card, CardContent } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Spinner } from "@/components/ui/spinner";
import { ProductArt } from "@/components/product-art";
import { useCart } from "@/stores/cart";
import { useAuthStore } from "@/stores/auth";
import { createOrder } from "@/api/orders";
import { createPayment } from "@/api/payments";
import { formatMoney } from "@/lib/utils";

const Schema = z.object({
  fullName: z.string().min(2),
  phone: z.string().min(7),
  street: z.string().min(3),
  city: z.string().min(2),
  state: z.string().min(2),
  zipCode: z.string().min(3),
  country: z.string().min(2),
  paymentMethod: z.enum(["card", "upi", "cod"]),
  notes: z.string().optional(),
});

type FormValues = z.infer<typeof Schema>;

const FREE_SHIPPING = 499;
const SHIPPING_COST = 49;
const TAX_RATE = 0.18;

export const CheckoutPage: React.FC = () => {
  const navigate = useNavigate();
  const { lines, subtotal, clear } = useCart();
  const user = useAuthStore((s) => s.user);

  const sub = subtotal();
  const tax = sub * TAX_RATE;
  const shipping = sub >= FREE_SHIPPING ? 0 : SHIPPING_COST;
  const total = sub + tax + shipping;

  const idempotencyKey = React.useMemo(() => crypto.randomUUID(), []);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(Schema),
    defaultValues: {
      fullName: user?.displayName ?? "",
      phone: "",
      street: "",
      city: "",
      state: "",
      zipCode: "",
      country: "IN",
      paymentMethod: "card",
      notes: "",
    },
  });

  const place = useMutation({
    mutationFn: async (values: FormValues) => {
      if (lines.length === 0) throw new Error("Your bag is empty");
      const order = await createOrder(
        {
          items: lines.map((l) => ({ productId: l.productId, quantity: l.quantity })),
          shippingAddress: {
            fullName: values.fullName,
            phone: values.phone,
            street: values.street,
            city: values.city,
            state: values.state,
            zipCode: values.zipCode,
            country: values.country,
          },
          billingAddress: {
            fullName: values.fullName,
            phone: values.phone,
            street: values.street,
            city: values.city,
            state: values.state,
            zipCode: values.zipCode,
            country: values.country,
          },
          paymentMethod: values.paymentMethod,
          notes: values.notes,
        },
        idempotencyKey,
      );
      // Kick off payment intent (sandbox or stripe). Backend will emit the saga
      // events; OrderSuccess polls for status convergence.
      try {
        await createPayment(
          {
            orderId: order.id,
            orderNumber: order.orderNumber,
            paymentMethod: values.paymentMethod,
            amount: order.totalAmount,
            currency: order.currency,
            description: `Order ${order.orderNumber}`,
          },
          idempotencyKey,
        );
      } catch (e: any) {
        // Don't block the success page; the order is created either way.
        console.warn("Payment init failed", e?.message);
      }
      return order;
    },
    onSuccess: (order) => {
      clear();
      toast.success("Order placed");
      navigate(`/order-success/${order.id}`, { replace: true });
    },
    onError: (err: any) => {
      toast.error(err?.message ?? "Failed to place order");
    },
  });

  return (
    <div className="container py-12">
      <header className="mb-10">
        <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Checkout</p>
        <h1 className="mt-2 font-display text-4xl font-semibold tracking-tight">
          Almost yours
        </h1>
      </header>

      <form
        onSubmit={handleSubmit((v) => place.mutate(v))}
        className="grid gap-10 lg:grid-cols-[1fr_22rem]"
      >
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          className="space-y-10"
        >
          <Section title="Shipping address" eyebrow="01">
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Full name" error={errors.fullName?.message} className="sm:col-span-2">
                <Input {...register("fullName")} />
              </Field>
              <Field label="Phone" error={errors.phone?.message}>
                <Input {...register("phone")} />
              </Field>
              <Field label="Country" error={errors.country?.message}>
                <Input {...register("country")} />
              </Field>
              <Field label="Street" error={errors.street?.message} className="sm:col-span-2">
                <Input {...register("street")} />
              </Field>
              <Field label="City" error={errors.city?.message}>
                <Input {...register("city")} />
              </Field>
              <Field label="State" error={errors.state?.message}>
                <Input {...register("state")} />
              </Field>
              <Field label="ZIP / Postal code" error={errors.zipCode?.message}>
                <Input {...register("zipCode")} />
              </Field>
            </div>
          </Section>

          <Section title="Payment" eyebrow="02">
            <Field label="Method">
              <Select
                defaultValue="card"
                onValueChange={(v) => setValue("paymentMethod", v as FormValues["paymentMethod"])}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="card">Card (Stripe)</SelectItem>
                  <SelectItem value="upi">UPI</SelectItem>
                  <SelectItem value="cod">Cash on delivery</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <p className="mt-3 text-xs text-muted-foreground">
              Payment is processed by our secure gateway. We never see your card number.
            </p>
          </Section>

          <Section title="Notes (optional)" eyebrow="03">
            <Textarea {...register("notes")} placeholder="Anything for the seller?" />
          </Section>
        </motion.div>

        <Card className="h-fit lg:sticky lg:top-24">
          <CardContent className="space-y-4 p-6">
            <h2 className="font-display text-lg font-semibold tracking-tight">Summary</h2>
            <ul className="space-y-3 max-h-72 overflow-auto scrollbar-thin pr-1">
              {lines.map((l) => (
                <li key={l.productId} className="flex items-center gap-3">
                  <ProductArt seed={l.sku || l.productId} ratio="square" className="h-12 w-12" />
                  <div className="flex-1 min-w-0">
                    <div className="truncate text-sm font-medium">{l.name}</div>
                    <div className="text-xs text-muted-foreground">× {l.quantity}</div>
                  </div>
                  <span className="font-mono text-sm">{formatMoney(l.price * l.quantity)}</span>
                </li>
              ))}
            </ul>
            <Separator />
            <Row label="Subtotal" value={formatMoney(sub)} />
            <Row label="GST (18%)" value={formatMoney(tax)} />
            <Row
              label="Shipping"
              value={shipping === 0 ? <span className="text-emerald-600">Free</span> : formatMoney(shipping)}
            />
            <Separator />
            <Row label="Total" value={formatMoney(total)} bold />
            <Button type="submit" variant="accent" size="lg" className="w-full" disabled={isSubmitting || place.isPending}>
              {place.isPending ? <Spinner /> : "Place order"}
            </Button>
            <p className="text-center text-xs text-muted-foreground">
              By placing your order you agree to our terms.
            </p>
          </CardContent>
        </Card>
      </form>
    </div>
  );
};

const Section: React.FC<{ title: string; eyebrow: string; children: React.ReactNode }> = ({
  title,
  eyebrow,
  children,
}) => (
  <section>
    <p className="font-mono text-xs uppercase tracking-[0.18em] text-muted-foreground">{eyebrow}</p>
    <h2 className="mt-1 font-display text-2xl font-semibold tracking-tight">{title}</h2>
    <Separator className="my-4" />
    {children}
  </section>
);

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

const Row: React.FC<{ label: string; value: React.ReactNode; bold?: boolean }> = ({
  label,
  value,
  bold,
}) => (
  <div className={`flex items-center justify-between text-sm ${bold ? "text-base font-semibold" : ""}`}>
    <span className="text-muted-foreground">{label}</span>
    <span className={bold ? "font-mono" : "font-mono text-muted-foreground"}>{value}</span>
  </div>
);
