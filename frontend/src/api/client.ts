import type { ApiErrorBody, Envelope } from "./types";

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details: ApiErrorBody["error"]["details"] = [],
    public requestId?: string,
  ) {
    super(message);
  }
}
type Session = {
  accessToken: string | null;
  refreshToken: string | null;
  update: (access: string, refresh: string) => void;
  clear: () => void;
};
let session: Session | null = null;
let refreshInFlight: Promise<void> | null = null;
export function bindSession(value: Session) {
  session = value;
}

async function parseError(response: Response) {
  let body: ApiErrorBody | undefined;
  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    /* non-json upstream */
  }
  return new ApiError(
    response.status,
    body?.error.code ?? "NETWORK_ERROR",
    body?.error.message ?? `请求失败（${response.status}）`,
    body?.error.details,
    body?.meta?.requestId,
  );
}

export async function apiFetch(
  path: string,
  init: RequestInit = {},
  retry = true,
  timeoutMs = 15_000,
): Promise<Response> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type"))
    headers.set("Content-Type", "application/json");
  if (session?.accessToken)
    headers.set("Authorization", `Bearer ${session.accessToken}`);
  headers.set("X-Request-Id", crypto.randomUUID());
  const response = await timedFetch(
    `${import.meta.env.VITE_API_BASE_URL ?? "/api/v1"}${path}`,
    { ...init, headers },
    timeoutMs,
  );
  if (response.status === 401 && retry && session?.refreshToken) {
    await refresh();
    return apiFetch(path, init, false, timeoutMs);
  }
  if (!response.ok) throw await parseError(response);
  return response;
}
async function refresh() {
  if (!session?.refreshToken)
    throw new ApiError(401, "UNAUTHORIZED", "登录已失效");
  if (!refreshInFlight) {
    const headers = new Headers({
      "Content-Type": "application/json",
      "X-Request-Id": crypto.randomUUID(),
    });
    refreshInFlight = timedFetch(
      `${import.meta.env.VITE_API_BASE_URL ?? "/api/v1"}/auth/refresh`,
      {
        method: "POST",
        headers,
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      },
      15_000,
    )
      .then(async (response) => {
        if (!response.ok) throw await parseError(response);
        const result = (await response.json()) as Envelope<{
          accessToken: string;
          refreshToken: string;
        }>;
        session?.update(result.data.accessToken, result.data.refreshToken);
      })
      .catch((error) => {
        session?.clear();
        throw error;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

async function timedFetch(url: string, init: RequestInit, timeoutMs: number) {
  const timeout = new AbortController(),
    timer = window.setTimeout(() => timeout.abort(), timeoutMs),
    signal = init.signal
      ? AbortSignal.any([init.signal, timeout.signal])
      : timeout.signal;
  try {
    return await fetch(url, { ...init, signal });
  } catch (error) {
    if (init.signal?.aborted) throw error;
    throw new ApiError(
      0,
      timeout.signal.aborted ? "REQUEST_TIMEOUT" : "NETWORK_ERROR",
      timeout.signal.aborted
        ? "请求超时，请稍后重试"
        : "无法连接服务，请检查网络后重试",
    );
  } finally {
    window.clearTimeout(timer);
  }
}

export async function api<T>(
  path: string,
  init: RequestInit = {},
  retry = true,
): Promise<T> {
  const response = await apiFetch(path, init, retry);
  if (response.status === 204) return undefined as T;
  const result = (await response.json()) as Envelope<T>;
  return result.data;
}

export async function apiPage<T>(path: string, retry = true) {
  const response = await apiFetch(path, {}, retry);
  return response.json() as Promise<Envelope<T>>;
}
