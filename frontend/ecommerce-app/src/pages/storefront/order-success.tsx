import * as React from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { CheckCircle2 } from "lucide-react";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ProductArt } from "@/components/product-art";
import { Spinner } from "@/components/ui/spinner";
import { Badge } from "@/components/ui/badge";
import { getOrder } from "@/api/orders";
import { formatMoney } from "@/lib/utils";

export const OrderSuccessPage: React.FC = () => {
  const { id = "" } = useParams();

  // Poll briefly so users see the saga result if Stripe / sandbox webhook fires fast.
  const { data: order, isLoading } = useQuery({
    queryKey: ["order", id],
    queryFn: () => getOrder(id),
    refetchInterval: (q) => {
      const o = q.state.data;
      if (!o) return 2000;
      if (o.status === "PENDING" || o.paymentStatus === "PENDING") return 2000;
      return false;
    },
    enabled: Boolean(id),
  });

  if (isLoading || !order) {
    return (
      <div className="container py-24 text-center">
        <Spinner label="Confirming your order…" />
      </div>
    );
  }

  return (
    <div className="container py-16">
      <motion.div
        initial={{ scale: 0.9, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ duration: 0.4 }}
        className="mx-auto grid h-16 w-16 place-items-center rounded-full bg-emerald-500/10 text-emerald-600"
      >
        <CheckCircle2 className="h-8 w-8" />
      </motion.div>

      <h1 className="mt-6 text-center font-display text-4xl font-semibold tracking-tight md:text-5xl">
        Thank you. Your order's on its way.
      </h1>
      <p className="mt-3 text-center text-sm text-muted-foreground">
        Order <span className="font-mono">{order.orderNumber}</span>
      </p>

      <Card className="mx-auto mt-10 max-w-2xl">
        <CardContent className="space-y-4 p-6">
          <div className="flex items-center justify-between">
            <Badge variant={order.status === "CONFIRMED" ? "success" : "info"}>
              {order.status}
            </Badge>
            <Badge variant={order.paymentStatus === "COMPLETED" ? "success" : "warning"}>
              Payment · {order.paymentStatus}
            </Badge>
          </div>

          <ul className="divide-y rounded-md border">
            {order.items.map((it) => (
              <li key={it.productId} className="flex items-center gap-3 p-3">
                <ProductArt seed={it.sku || it.productId} ratio="square" className="h-12 w-12" />
                <div className="flex-1 min-w-0">
                  <div className="truncate text-sm font-medium">
                    {it.productName ?? it.sku}
                  </div>
                  <div className="text-xs text-muted-foreground">× {it.quantity}</div>
                </div>
                <span className="font-mono text-sm">{formatMoney(it.totalPrice)}</span>
              </li>
            ))}
          </ul>

          <div className="grid grid-cols-2 gap-4 pt-2 text-sm">
            <div className="flex items-center justify-between sm:col-span-2">
              <span className="text-muted-foreground">Subtotal</span>
              <span className="font-mono">{formatMoney(order.subtotal)}</span>
            </div>
            <div className="flex items-center justify-between sm:col-span-2">
              <span className="text-muted-foreground">Tax</span>
              <span className="font-mono">{formatMoney(order.taxAmount)}</span>
            </div>
            <div className="flex items-center justify-between sm:col-span-2">
              <span className="text-muted-foreground">Shipping</span>
              <span className="font-mono">{formatMoney(order.shippingCost)}</span>
            </div>
            <div className="flex items-center justify-between border-t pt-3 text-base font-semibold sm:col-span-2">
              <span>Total</span>
              <span className="font-mono">{formatMoney(order.totalAmount, order.currency)}</span>
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="mt-8 flex justify-center gap-3">
        <Button asChild variant="accent">
          <Link to="/account/orders">View my orders</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/products">Keep shopping</Link>
        </Button>
      </div>
    </div>
  );
};
