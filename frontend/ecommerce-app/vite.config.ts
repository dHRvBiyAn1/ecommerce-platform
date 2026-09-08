import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";

const API_TARGET = process.env.VITE_API_PROXY_TARGET ?? "http://api-gateway:8080";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/v1": { target: API_TARGET, changeOrigin: true },
      "/api": { target: API_TARGET, changeOrigin: true },
      "/.well-known": { target: API_TARGET, changeOrigin: true },
      // Only proxy the Spring Security OAuth2 endpoints, NOT /oauth2/redirect
      // which is a frontend SPA route handled by React Router.
      "/oauth2/authorization": { target: API_TARGET, changeOrigin: true },
      "/login/oauth2": { target: API_TARGET, changeOrigin: true },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    exclude: ["**/node_modules/**", "**/dist/**", "scripts/**"],
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "lcov", "json"],
      all: true,
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.test.{ts,tsx}", "src/test/**"],
    },
  },
  build: {
    sourcemap: true,
    target: "es2022",
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        // Split heavy vendor groups so the entry chunk stays small. Anything
        // not matched falls into the main bundle.
        manualChunks(id) {
          if (!id.includes("node_modules")) return undefined;
          if (id.includes("react-router")) return "vendor-router";
          if (id.includes("@tanstack")) return "vendor-query";
          if (id.includes("framer-motion")) return "vendor-motion";
          if (id.includes("@radix-ui")) return "vendor-radix";
          if (id.includes("lucide-react")) return "vendor-icons";
          if (id.includes("zod") || id.includes("react-hook-form") || id.includes("@hookform")) {
            return "vendor-forms";
          }
          return "vendor";
        },
      },
    },
  },
});
