import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import process from "node:process";
import { fileURLToPath, URL } from "node:url";
import { build } from "vite";

const appDirectory = fileURLToPath(new URL("..", import.meta.url));
const distDirectory = join(appDirectory, "dist");
const requests = { orderKeys: [], paymentKeys: [] };
const order = {
  id: "order-1",
  orderNumber: "ORD-1",
  userId: "user-1",
  userEmail: "customer@example.com",
  status: "CONFIRMED",
  items: [{ productId: "product-1", sku: "LAMP", productName: "Lamp", quantity: 1, unitPrice: 100, totalPrice: 100 }],
  subtotal: 100,
  taxAmount: 18,
  shippingCost: 49,
  discountAmount: 0,
  totalAmount: 167,
  currency: "INR",
  paymentId: "payment-1",
  paymentMethod: "card",
  paymentStatus: "COMPLETED",
  createdAt: "2026-09-20T00:00:00Z",
  updatedAt: "2026-09-20T00:00:00Z",
};
const payment = {
  id: "payment-1",
  paymentReference: "pay-1",
  orderId: "order-1",
  userId: "user-1",
  userEmail: "customer@example.com",
  status: "COMPLETED",
  paymentMethod: "card",
  amount: 167,
  currency: "INR",
  createdAt: "2026-09-20T00:00:00Z",
  updatedAt: "2026-09-20T00:00:00Z",
};
const cart = {
  id: "cart-1",
  items: [{ productId: "product-1", sku: "LAMP", productName: "Lamp", unitPrice: 100, quantity: 1 }],
  appliedCouponCode: null,
  appliedDiscountAmount: 0,
};

process.env.VITE_STRIPE_PUBLISHABLE_KEY = "pk_test_isolated";
process.env.VITE_API_BASE_URL = "/api";
process.chdir(appDirectory);
await build({ root: appDirectory, logLevel: "warn" });

function json(response, body, statusCode = 200) {
  response.writeHead(statusCode, { "content-type": "application/json" });
  response.end(JSON.stringify(body));
}

const types = {
  ".css": "text/css",
  ".html": "text/html",
  ".js": "text/javascript",
  ".json": "application/json",
  ".map": "application/json",
  ".svg": "image/svg+xml",
  ".webmanifest": "application/manifest+json",
};

const server = createServer(async (request, response) => {
  const url = new URL(request.url ?? "/", "http://127.0.0.1");
  if (url.pathname === "/_e2e/requests") return json(response, requests);
  if (url.pathname === "/api/v1/cart" && request.method === "GET") return json(response, cart);
  if (url.pathname === "/api/v1/cart" && request.method === "DELETE") return json(response, { ...cart, items: [] });
  if (url.pathname === "/api/v1/orders" && request.method === "POST") {
    requests.orderKeys.push(request.headers["x-idempotency-key"] ?? "");
    return json(response, { status: 201, message: "created", data: order }, 201);
  }
  if (url.pathname === "/api/v1/payments" && request.method === "POST") {
    requests.paymentKeys.push(request.headers["x-idempotency-key"] ?? "");
    return json(response, {
      status: 201,
      message: "created",
      data: { payment, clientSecret: requests.paymentKeys.length === 1 ? "pi_test_secret" : null },
    }, 201);
  }
  if (url.pathname === "/api/v1/orders/order-1") return json(response, { status: 200, message: "ok", data: order });
  if (url.pathname === "/api/v1/payments/payment-1") return json(response, { status: 200, message: "ok", data: payment });

  const requested = normalize(decodeURIComponent(url.pathname)).replace(/^(\.\.(\/|\\|$))+/, "");
  let filePath = join(distDirectory, requested === "/" ? "index.html" : requested);
  try {
    if (!(await stat(filePath)).isFile()) filePath = join(distDirectory, "index.html");
  } catch {
    filePath = join(distDirectory, "index.html");
  }
  response.writeHead(200, { "content-type": types[extname(filePath)] ?? "application/octet-stream" });
  response.end(await readFile(filePath));
});

server.listen(4178, "127.0.0.1");

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
