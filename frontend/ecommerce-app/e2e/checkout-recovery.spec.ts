import { expect, test } from "@playwright/test";

test("retries a declined card with the same checkout attempt", async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  page.on("pageerror", (error) => consoleErrors.push(error.message));

  await page.route("https://fonts.googleapis.com/**", (route) => route.fulfill({ contentType: "text/css", body: "" }));
  await page.route("https://js.stripe.com/**", (route) => route.fulfill({
    contentType: "text/javascript",
    body: `
      window.Stripe = function () {
        var confirmations = 0;
        return {
          elements: function () {
            return { create: function () {
              var handlers = {};
              return {
                on: function (event, handler) { handlers[event] = handler; },
                mount: function (element) {
                  element.textContent = "Test card ready";
                  queueMicrotask(function () { if (handlers.ready) handlers.ready(); });
                },
                destroy: function () {}
              };
            } };
          },
          confirmCardPayment: async function () {
            confirmations += 1;
            if (confirmations === 1) return { error: { message: "Card declined" } };
            return { paymentIntent: { status: "succeeded" } };
          }
        };
      };
    `,
  }));
  await page.addInitScript(() => {
    localStorage.setItem("ecom-auth", JSON.stringify({
      state: {
        accessToken: "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ1c2VyLTEifQ.signature",
        user: { id: "user-1", email: "customer@example.com", displayName: "Ada Lovelace" },
      },
      version: 0,
    }));
    localStorage.setItem("ecom-cart-v2", JSON.stringify({
      state: {
        lines: [{ productId: "product-1", sku: "LAMP", name: "Lamp", price: 100, quantity: 1 }],
        pendingAttempt: null,
      },
      version: 0,
    }));
  });

  await page.goto("/checkout");
  await expect(page.getByLabel("Card details")).toContainText("Test card ready");
  await expect(page.getByText("UPI")).toHaveCount(0);
  await expect(page.getByText("Cash on delivery")).toHaveCount(0);
  await page.getByLabel("Phone").fill("1234567890");
  await page.getByLabel("Street").fill("1 Analytical Engine Way");
  await page.getByLabel("City").fill("London");
  await page.getByLabel("State").fill("London");
  await page.getByLabel("ZIP / Postal code").fill("SW1A");

  await page.getByRole("button", { name: "Place order" }).click();
  await expect(page.getByText("Card declined")).toBeVisible();
  await page.getByRole("button", { name: "Place order" }).click();
  await expect(page).toHaveURL(/\/order-success\/order-1$/);

  const response = await page.request.get("http://127.0.0.1:4178/_e2e/requests");
  const requests = await response.json();
  expect(requests.orderKeys).toHaveLength(2);
  expect(requests.paymentKeys).toHaveLength(2);
  expect(new Set([...requests.orderKeys, ...requests.paymentKeys]).size).toBe(1);
  expect(requests.orderKeys[0]).toMatch(/^[0-9a-f-]{36}$/);
  expect(consoleErrors).toEqual([]);
});
