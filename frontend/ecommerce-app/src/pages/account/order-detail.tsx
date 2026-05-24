import * as React from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft } from "lucide-react";
import { getOrder } from "@/api/orders";
import { ProductArt } from "@/components/product-art";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { PageSpinner } from "@/components/ui/spinner";
import { Separator } from "@/components/ui/separator";
import { formatDate, formatMoney } from "@/lib/utils";

export const OrderDetailPage: React.FC = () => {
  const { id = "" } = useParams();
  const { data: order, isLoading } = useQuery({
    queryKey: ["order", id],
    queryFn: () => getOrder(id),
    enabled: Boolean(id),
  });

  if (isLoading || !order) return <PageSpinner />;

  return (
    <div className="container py-12">
      <Link
        to="/account/orders"
        className="inline-flex items-center gap-1 text-xs uppercase tracking-[0.18em] text-muted-foreground hover:text-accent"
      >
        <ChevronLeft className="h-3 w-3" /> Orders
      </Link>

      <header className="mt-4">
        <h1 className="font-display text-3xl font-semibold tracking-tight md:text-4xl">
          {order.orderNumber}
        </h1>
        <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <span>{formatDate(order.createdAt)}</span>
          <Badge variant="info">{order.status}</Badge>
          <Badge variant="outline">{order.paymentStatus ?? "—"}</Badge>
        </div>
      </header>

      <div className="mt-8 grid gap-8 lg:grid-cols-[1fr_22rem]">
        <Card>
          <CardContent className="p-0">
            <ul className="divide-y">
              {order.items.map((it) => (
                <li key={it.productId} className="flex items-center gap-4 p-5">
                  <ProductArt
                    seed={it.sku || it.productId}
                    ratio="square"
                    className="h-16 w-16"
                  />
                  <div className="flex-1 min-w-0">
                    <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                      SKU · {it.sku}
                    </p>
                    <p className="font-display text-base font-semibold tracking-tight">
                      {it.productName ?? it.sku}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      × {it.quantity} @ {formatMoney(it.unitPrice, order.currency)}
                    </p>
                  </div>
                  <span className="font-mono text-sm">
                    {formatMoney(it.totalPrice, order.currency)}
                  </span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>

        <Card className="h-fit">
          <CardContent className="space-y-3 p-6 text-sm">
            <h2 className="font-display text-lg font-semibold tracking-tight">Total</h2>
            <Row label="Subtotal" value={formatMoney(order.subtotal, order.currency)} />
            <Row label="Tax" value={formatMoney(order.taxAmount, order.currency)} />
            <Row label="Shipping" value={formatMoney(order.shippingCost, order.currency)} />
            {(order.discountAmount ?? 0) > 0 && (
              <Row label="Discount" value={`-${formatMoney(order.discountAmount, order.currency)}`} />
            )}
            <Separator />
            <Row label="Total" value={formatMoney(order.totalAmount, order.currency)} bold />

            {order.shippingAddress && (
              <>
                <Separator className="my-2" />
                <h3 className="font-display text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                  Shipping to
                </h3>
                <address className="not-italic text-muted-foreground">
                  <div>{order.shippingAddress.fullName}</div>
                  <div>{order.shippingAddress.street}</div>
                  <div>
                    {order.shippingAddress.city}, {order.shippingAddress.state}{" "}
                    {order.shippingAddress.zipCode}
                  </div>
                  <div>{order.shippingAddress.country}</div>
                  <div>{order.shippingAddress.phone}</div>
                </address>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

const Row: React.FC<{ label: string; value: React.ReactNode; bold?: boolean }> = ({
  label,
  value,
  bold,
}) => (
  <div
    className={`flex items-center justify-between ${bold ? "text-base font-semibold" : ""}`}
  >
    <span className="text-muted-foreground">{label}</span>
    <span className={bold ? "font-mono" : "font-mono text-muted-foreground"}>{value}</span>
  </div>
);
