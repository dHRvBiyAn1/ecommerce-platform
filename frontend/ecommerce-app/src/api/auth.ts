import client from "@/api/client";
import type {
  ApiResponse,
  RegistrationRequest,
  TokenResponse,
  UserProfile,
} from "@/api/types";

export async function login(email: string, password: string): Promise<TokenResponse> {
  const params = new URLSearchParams({
    grant_type: "password",
    email,
    password,
  });
  const { data } = await client.post<ApiResponse<TokenResponse>>(
    "/auth/token",
    params.toString(),
    { headers: { "Content-Type": "application/x-www-form-urlencoded" } },
  );
  return data.data;
}

export async function register(req: RegistrationRequest): Promise<UserProfile> {
  const { data } = await client.post<ApiResponse<UserProfile>>(
    "/auth/register",
    req,
  );
  return data.data;
}

export async function logout(): Promise<void> {
  await client.post("/auth/logout");
}

export async function changePassword(
  oldPassword: string,
  newPassword: string,
): Promise<void> {
  await client.post("/auth/change-password", { oldPassword, newPassword });
}

export async function me(): Promise<UserProfile> {
  const { data } = await client.get<ApiResponse<UserProfile>>("/user/profile");
  return data.data;
}

export async function updateProfile(
  patch: Partial<Pick<UserProfile, "displayName" | "imageUrl">>,
): Promise<UserProfile> {
  const { data } = await client.put<ApiResponse<UserProfile>>(
    "/user/profile",
    patch,
  );
  return data.data;
}
