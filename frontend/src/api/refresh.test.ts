import { describe, expect, it, vi } from "vitest";
import { api, bindSession } from "./client";

describe("refresh deadline", () => {
  it("times out one shared refresh and clears the session", async () => {
    vi.useFakeTimers();
    const state = {
      accessToken: "old",
      refreshToken: "refresh",
      update: vi.fn(),
      clear: vi.fn(),
    };
    bindSession(state);
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
        if (!String(input).includes("/auth/refresh"))
          return new Response(
            JSON.stringify({
              error: { code: "UNAUTHORIZED", message: "expired" },
            }),
            { status: 401, headers: { "Content-Type": "application/json" } },
          );
        return new Promise<Response>((_resolve, reject) =>
          (init?.signal as AbortSignal).addEventListener("abort", () =>
            reject(new DOMException("aborted", "AbortError")),
          ),
        );
      }),
    );
    const pending = api("/protected"),
      assertion = expect(pending).rejects.toMatchObject({
        code: "REQUEST_TIMEOUT",
      });
    await vi.advanceTimersByTimeAsync(15_001);
    await assertion;
    expect(state.clear).toHaveBeenCalledOnce();
    vi.useRealTimers();
  });
});
