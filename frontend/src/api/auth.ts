import { api } from "./client";
import type { AuthTokens, UserProfile } from "./types";
export const authApi = {
  register: (body: { email: string; password: string; displayName: string }) =>
    api<AuthTokens>("/auth/register", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  login: (body: { email: string; password: string }) =>
    api<AuthTokens>("/auth/login", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  me: () => api<UserProfile>("/users/me"),
  logout: (refreshToken: string) =>
    api<void>("/auth/logout", {
      method: "POST",
      body: JSON.stringify({ refreshToken }),
    }),
};
