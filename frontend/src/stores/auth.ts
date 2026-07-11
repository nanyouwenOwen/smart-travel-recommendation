import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { authApi } from "@/api/auth";
import { bindSession } from "@/api/client";
import type { AuthTokens, UserProfile } from "@/api/types";
const STORAGE = "travel-assistant.refresh-token";
export const useAuthStore = defineStore("auth", () => {
  const accessToken = ref<string | null>(null),
    refreshToken = ref<string | null>(sessionStorage.getItem(STORAGE)),
    user = ref<UserProfile | null>(null),
    initialized = ref(false);
  function update(access: string, refresh: string) {
    accessToken.value = access;
    refreshToken.value = refresh;
    sessionStorage.setItem(STORAGE, refresh);
  }
  function clearSession() {
    const wasAuthenticated =
      !!accessToken.value || !!refreshToken.value || !!user.value;
    accessToken.value = null;
    refreshToken.value = null;
    user.value = null;
    sessionStorage.removeItem(STORAGE);
    if (wasAuthenticated) window.dispatchEvent(new CustomEvent("auth:expired"));
  }
  bindSession({
    get accessToken() {
      return accessToken.value;
    },
    get refreshToken() {
      return refreshToken.value;
    },
    update,
    clear: clearSession,
  });
  async function accept(result: AuthTokens) {
    update(result.accessToken, result.refreshToken);
    user.value = await authApi.me();
  }
  async function login(email: string, password: string) {
    await accept(await authApi.login({ email, password }));
  }
  async function register(
    email: string,
    password: string,
    displayName: string,
  ) {
    await accept(await authApi.register({ email, password, displayName }));
  }
  async function initialize() {
    try {
      if (refreshToken.value) user.value = await authApi.me();
    } catch {
      clearSession();
    } finally {
      initialized.value = true;
    }
  }
  async function logout() {
    const token = refreshToken.value;
    clearSession();
    if (token)
      try {
        await authApi.logout(token);
      } catch {
        /* local logout remains authoritative */
      }
  }
  return {
    accessToken,
    refreshToken,
    user,
    initialized,
    authenticated: computed(() => !!user.value),
    initialize,
    login,
    register,
    logout,
    clearSession,
  };
});
