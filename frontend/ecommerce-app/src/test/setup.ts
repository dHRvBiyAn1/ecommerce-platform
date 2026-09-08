import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";
import { server } from "./server";

const entries = new Map<string, string>();
const memoryStorage: Storage = {
  get length() {
    return entries.size;
  },
  clear() {
    entries.clear();
  },
  getItem(key) {
    return entries.get(key) ?? null;
  },
  key(index) {
    return [...entries.keys()][index] ?? null;
  },
  removeItem(key) {
    entries.delete(key);
  },
  setItem(key, value) {
    entries.set(key, String(value));
  },
};

Object.defineProperty(globalThis, "localStorage", {
  configurable: true,
  value: memoryStorage,
});

Object.defineProperty(window, "matchMedia", {
  configurable: true,
  value: () => ({
    matches: false,
    media: "",
    onchange: null,
    addEventListener() {},
    removeEventListener() {},
    addListener() {},
    removeListener() {},
    dispatchEvent() {
      return false;
    },
  }),
});

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

afterEach(() => {
  cleanup();
  memoryStorage.clear();
  server.resetHandlers();
});

afterAll(() => server.close());
