import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { server } from "@/test/server";

afterEach(() => {
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe("API base URL", () => {
  it.each([undefined, "", "   "])(
    "uses the gateway API prefix when the build argument is %j",
    async (baseUrl) => {
      vi.stubEnv("VITE_API_BASE_URL", baseUrl);
      vi.resetModules();
      server.use(
        http.get("*/api/v1/categories", () =>
          HttpResponse.json([{ id: "category-1", name: "Home" }])),
        http.get("*/v1/categories", () => new HttpResponse(null, { status: 404 })),
      );

      const { listCategories } = await import("@/api/products");
      await expect(listCategories()).resolves.toEqual([{ id: "category-1", name: "Home" }]);
    },
  );

  it("preserves a configured API origin and prefix", async () => {
    vi.stubEnv("VITE_API_BASE_URL", "https://gateway.example.test/api");
    vi.resetModules();
    server.use(http.get("https://gateway.example.test/api/v1/categories", () =>
      HttpResponse.json([{ id: "category-2", name: "Clothing" }])));

    const { listCategories } = await import("@/api/products");
    await expect(listCategories()).resolves.toEqual([{ id: "category-2", name: "Clothing" }]);
  });
});
