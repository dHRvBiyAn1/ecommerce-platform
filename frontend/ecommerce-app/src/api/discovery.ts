import client from "@/api/client";

export interface AuthProvider {
  id: string;
  label: string;
  authorizationUrl: string;
}

export interface AuthDiscovery {
  password: boolean;
  oauth2: boolean;
  providers: AuthProvider[];
}

/**
 * Discovers which auth methods this backend has configured. Used by the login
 * page to render only the social buttons that actually work. If the call fails
 * (network, 5xx) we degrade gracefully to "password only" so the form is never
 * broken.
 */
export async function getAuthProviders(): Promise<AuthDiscovery> {
  try {
    const { data } = await client.get<AuthDiscovery>("/auth/providers");
    return data;
  } catch {
    return { password: true, oauth2: false, providers: [] };
  }
}
