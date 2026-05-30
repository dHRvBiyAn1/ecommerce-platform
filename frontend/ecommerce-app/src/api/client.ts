import axios, {
  AxiosError,
  AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from "axios";
import { useAuthStore } from "@/stores/auth";
import type { ErrorResponse } from "@/api/types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";

/**
 * Lightweight axios wrapper that:
 * - injects the access token from the auth store on every request
 * - on 401: attempts a single silent refresh against /auth/token, retries the
 *   original request once, then signs the user out if that fails too
 * - normalises backend error envelopes into the {@link ApiError} thrown to callers
 */
export class ApiError extends Error {
  status: number;
  code?: string;
  fieldErrors?: Record<string, string>;
  traceId?: string;

  constructor(message: string, status: number, body?: ErrorResponse) {
    super(message);
    this.status = status;
    this.code = body?.code;
    this.fieldErrors = body?.fieldErrors;
    this.traceId = body?.traceId;
  }
}

const client: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
    Accept: "application/json",
  },
});

client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshing: Promise<string | null> | null = null;
async function refreshAccessToken(): Promise<string | null> {
  if (refreshing) return refreshing;
  refreshing = (async () => {
    try {
      const params = new URLSearchParams({ grant_type: "refresh_token" });
      const { data } = await axios.post<{ data: { accessToken: string } }>(
        `${BASE_URL}/auth/token`,
        params.toString(),
        {
          withCredentials: true,
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
        },
      );
      const newToken = data?.data?.accessToken ?? null;
      if (newToken) useAuthStore.getState().setAccessToken(newToken);
      return newToken;
    } catch {
      return null;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

client.interceptors.response.use(
  (r) => r,
  async (err: AxiosError<ErrorResponse>) => {
    const original = err.config as AxiosRequestConfig & { _retry?: boolean };
    const status = err.response?.status ?? 0;

    if (
      (status === 401 || status === 403) &&
      original &&
      !original._retry
    ) {
      original._retry = true;

      // Try silently refreshing the token if it was an unauthorized error (401)
      if (status === 401 && !original.url?.includes("/auth/token")) {
        const fresh = await refreshAccessToken();
        if (fresh) {
          original.headers = {
            ...(original.headers ?? {}),
            Authorization: `Bearer ${fresh}`,
          };
          return client.request(original);
        }
      }

      // If refresh failed or it was a forbidden (403) error, clear local auth state
      useAuthStore.getState().clear();

      // If this is a public GET request, fallback to guest by stripping the invalid token and retrying
      if (
        original.method?.toUpperCase() === "GET" &&
        (original.url?.includes("/v1/products") || original.url?.includes("/v1/categories"))
      ) {
        if (original.headers) {
          delete original.headers.Authorization;
          delete original.headers.authorization;
        }
        return client.request(original);
      }
    }

    const body = err.response?.data;
    const msg = body?.message ?? err.message ?? "Request failed";
    throw new ApiError(msg, status, body);
  },
);

export default client;
