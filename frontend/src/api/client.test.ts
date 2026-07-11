import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, apiFetch, ApiError, bindSession } from "./client";
describe("API client", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });
  it("shares one refresh across concurrent 401 responses and retries once", async () => {
    const state = {
      accessToken: "old",
      refreshToken: "refresh-old",
      update: vi.fn((a: string, r: string) => {
        state.accessToken = a;
        state.refreshToken = r;
      }),
      clear: vi.fn(),
    };
    bindSession(state);
    let refreshes = 0,
      calls = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string | URL | Request) => {
        const url = String(input);
        if (url.includes("/auth/refresh")) {
          refreshes++;
          await Promise.resolve();
          return new Response(
            JSON.stringify({
              data: { accessToken: "new", refreshToken: "refresh-new" },
              meta: { requestId: "r" },
            }),
            { status: 200, headers: { "Content-Type": "application/json" } },
          );
        }
        calls++;
        if (calls <= 2)
          return new Response(
            JSON.stringify({
              error: { code: "UNAUTHORIZED", message: "expired" },
              meta: { requestId: "x" },
            }),
            { status: 401, headers: { "Content-Type": "application/json" } },
          );
        return new Response(
          JSON.stringify({ data: { ok: true }, meta: { requestId: "y" } }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        );
      }),
    );
    const [a, b] = await Promise.all([
      api<{ ok: boolean }>("/one"),
      api<{ ok: boolean }>("/two"),
    ]);
    expect(a.ok && b.ok).toBe(true);
    expect(refreshes).toBe(1);
    expect(state.update).toHaveBeenCalledWith("new", "refresh-new");
  });
  it("supports 204", async () => {
    bindSession({
      accessToken: null,
      refreshToken: null,
      update: vi.fn(),
      clear: vi.fn(),
    });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(null, { status: 204 })),
    );
    await expect(
      api("/resource", { method: "DELETE" }),
    ).resolves.toBeUndefined();
  });
  it("aborts an unresponsive request at the configured deadline", async () => {
    vi.useFakeTimers();
    bindSession({
      accessToken: null,
      refreshToken: null,
      update: vi.fn(),
      clear: vi.fn(),
    });
    vi.stubGlobal(
      "fetch",
      vi.fn(
        (_url, _init) =>
          new Promise((_resolve, reject) => {
            (_init?.signal as AbortSignal).addEventListener("abort", () =>
              reject(new DOMException("aborted", "AbortError")),
            );
          }),
      ),
    );
    const pending = apiFetch("/slow", {}, true, 50),
      assertion = expect(pending).rejects.toMatchObject({
        code: "REQUEST_TIMEOUT",
      } satisfies Partial<ApiError>);
    await vi.advanceTimersByTimeAsync(51);
    await assertion;
  });
  it("maps non-json upstream and immediate network failures", async () => {
    bindSession({
      accessToken: null,
      refreshToken: null,
      update: vi.fn(),
      clear: vi.fn(),
    });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("bad gateway", { status: 502 })),
    );
    await expect(apiFetch("/broken")).rejects.toMatchObject({
      status: 502,
      code: "NETWORK_ERROR",
      message: "请求失败（502）",
    });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new TypeError("offline");
      }),
    );
    await expect(apiFetch("/offline")).rejects.toMatchObject({
      status: 0,
      code: "NETWORK_ERROR",
      message: "无法连接服务，请检查网络后重试",
    });
  });
});
