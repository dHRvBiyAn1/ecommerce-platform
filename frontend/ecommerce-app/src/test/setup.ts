import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

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

afterEach(() => {
  cleanup();
  memoryStorage.clear();
});
