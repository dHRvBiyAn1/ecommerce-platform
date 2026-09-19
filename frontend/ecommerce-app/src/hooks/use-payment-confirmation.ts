import { useMutation } from "@tanstack/react-query";
import * as React from "react";
import { loadStripe, type Stripe, type StripeCardElement } from "@stripe/stripe-js";

export type PaymentConfirmationAdapter = (clientSecret: string, cardElement: StripeCardElement) => Promise<void>;

let stripePromise: Promise<Stripe | null> | null = null;
let stripeKey: string | null = null;

async function getStripe() {
  const publishableKey = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY;
  if (!publishableKey) return null;
  if (!stripePromise || stripeKey !== publishableKey) {
    stripeKey = publishableKey;
    stripePromise = loadStripe(publishableKey);
  }
  return stripePromise;
}

export const confirmPayment: PaymentConfirmationAdapter = async (clientSecret, cardElement) => {
  if (!clientSecret) throw new Error("Payment confirmation is unavailable");
  if (!cardElement) throw new Error("Card details are not ready");
  const stripe = await getStripe();
  if (!stripe) throw new Error("Stripe is not configured");

  const result = await stripe.confirmCardPayment(clientSecret, {
    payment_method: { card: cardElement },
  });
  if (result.error) throw new Error(result.error.message ?? "Payment confirmation failed");
  if (result.paymentIntent?.status !== "succeeded") {
    throw new Error("Payment confirmation is incomplete");
  }
};

export function usePaymentConfirmation(cardElement: StripeCardElement | null) {
  return useMutation({
    mutationFn: ({ clientSecret }: { clientSecret: string }) => {
      if (!cardElement) throw new Error("Card details are not ready");
      return confirmPayment(clientSecret, cardElement);
    },
  });
}

export function useStripeCardElement() {
  const containerRef = React.useRef<HTMLDivElement>(null);
  const [cardElement, setCardElement] = React.useState<StripeCardElement | null>(null);
  const [ready, setReady] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let active = true;
    let element: StripeCardElement | null = null;

    void getStripe().then((stripe) => {
      if (!active) return;
      if (!stripe || !containerRef.current) {
        setError("Card details are unavailable");
        return;
      }

      element = stripe.elements().create("card");
      element.on("ready", () => active && setReady(true));
      element.on("change", (event) => active && setError(event.error?.message ?? null));
      element.mount(containerRef.current);
      setCardElement(element);
    }).catch(() => {
      if (active) setError("Card details are unavailable");
    });

    return () => {
      active = false;
      element?.destroy();
    };
  }, []);

  return { containerRef, cardElement, ready, error };
}
