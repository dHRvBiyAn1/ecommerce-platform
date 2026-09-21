import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAuthStore } from "@/stores/auth";

function token(payload: Record<string, unknown>) {
  return `eyJhbGciOiJub25lIn0.${btoa(JSON.stringify(payload))}.signature`;
}

describe("auth store", () => {
  beforeEach(() => {
    useAuthStore.getState().clear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("derives authentication, roles, and authorities from the access token", () => {
    useAuthStore.getState().setAccessToken(token({
      sub: "user-1",
      roles: ["ROLE_SELLER"],
      permissions: ["products:write"],
      exp: Math.floor(Date.now() / 1000) + 60,
    }));

    expect(useAuthStore.getState().isAuthenticated()).toBe(true);
    expect(useAuthStore.getState().hasRole("SELLER")).toBe(true);
    expect(useAuthStore.getState().hasRole("ROLE_SELLER")).toBe(true);
    expect(useAuthStore.getState().hasAuthority("products:write")).toBe(true);
    expect(useAuthStore.getState().isSeller()).toBe(true);
    expect(useAuthStore.getState().isAdmin()).toBe(false);
  });

  it("rejects missing, malformed, and expired tokens", () => {
    vi.setSystemTime(new Date("2026-09-20T12:00:00Z"));

    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    useAuthStore.getState().setAccessToken("not-a-jwt");
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    useAuthStore.getState().setAccessToken(token({ sub: "user-1", exp: 1 }));
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
  });

  it("treats admins as sellers and clears identity state", () => {
    useAuthStore.getState().setAccessToken(token({ sub: "admin-1", roles: ["ROLE_ADMIN"] }));
    useAuthStore.getState().setUser({ id: "admin-1", email: "admin@example.com" } as never);

    expect(useAuthStore.getState().isAdmin()).toBe(true);
    expect(useAuthStore.getState().isSeller()).toBe(true);

    useAuthStore.getState().clear();
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
  });
});
