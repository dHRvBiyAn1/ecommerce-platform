import { create } from "zustand";
import { persist } from "zustand/middleware";
import { jwtDecode } from "jwt-decode";
import type { UserProfile } from "@/api/types";

interface JwtPayload {
  sub: string;
  email?: string;
  roles?: string[];
  permissions?: string[];
  exp?: number;
}

export interface AuthState {
  accessToken: string | null;
  user: UserProfile | null;

  setAccessToken: (token: string | null) => void;
  setUser: (user: UserProfile | null) => void;
  clear: () => void;

  hasRole: (role: string) => boolean;
  hasAuthority: (authority: string) => boolean;
  isAdmin: () => boolean;
  isSeller: () => boolean;
  isAuthenticated: () => boolean;
}

function decode(token: string | null): JwtPayload | null {
  if (!token) return null;
  try {
    return jwtDecode<JwtPayload>(token);
  } catch {
    return null;
  }
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      user: null,

      setAccessToken: (token) => set({ accessToken: token }),
      setUser: (user) => set({ user }),
      clear: () => set({ accessToken: null, user: null }),

      hasRole(role) {
        const claims = decode(get().accessToken);
        const want = role.startsWith("ROLE_") ? role : `ROLE_${role}`;
        return Boolean(claims?.roles?.includes(want));
      },
      hasAuthority(auth) {
        const claims = decode(get().accessToken);
        return Boolean(claims?.permissions?.includes(auth));
      },
      isAdmin() {
        return get().hasRole("ADMIN");
      },
      isSeller() {
        return get().hasRole("SELLER") || get().isAdmin();
      },
      isAuthenticated() {
        const claims = decode(get().accessToken);
        if (!claims) return false;
        if (claims.exp && claims.exp * 1000 < Date.now()) return false;
        return true;
      },
    }),
    {
      name: "ecom-auth",
      partialize: (s) => ({ accessToken: s.accessToken, user: s.user }),
    },
  ),
);
