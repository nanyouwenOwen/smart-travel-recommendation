import { describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useTripStore } from "./trips";
import { useConversationStore } from "./conversations";
import { tripApi } from "@/api/trips";
import { conversationApi } from "@/api/conversations";
vi.mock("@/api/trips", () => ({
  tripApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    adjust: vi.fn(),
    versions: vi.fn(),
    version: vi.fn(),
    restore: vi.fn(),
    remove: vi.fn(),
  },
}));
vi.mock("@/api/conversations", () => ({
  conversationApi: {
    list: vi.fn(),
    create: vi.fn(),
    get: vi.fn(),
    messages: vi.fn(),
    cancel: vi.fn(),
  },
}));
vi.mock("@/api/sse", () => ({ consumeStream: vi.fn() }));
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => (resolve = done));
  return { promise, resolve };
}
describe("route request generations", () => {
  it("does not let an old trip response overwrite a newer route", async () => {
    setActivePinia(createPinia());
    const old = deferred<any>(),
      fresh = deferred<any>();
    vi.mocked(tripApi.get)
      .mockReturnValueOnce(old.promise)
      .mockReturnValueOnce(fresh.promise);
    const store = useTripStore(),
      one = store.load("old"),
      two = store.load("new");
    fresh.resolve({ id: "new", status: "READY", itinerary: [] });
    await two;
    old.resolve({ id: "old", status: "READY", itinerary: [] });
    await one;
    expect(store.current?.id).toBe("new");
  });
  it("does not let an old conversation response overwrite a newer route", async () => {
    setActivePinia(createPinia());
    const old = deferred<any>(),
      fresh = deferred<any>();
    vi.mocked(conversationApi.get)
      .mockReturnValueOnce(old.promise)
      .mockReturnValueOnce(fresh.promise);
    const store = useConversationStore(),
      one = store.load("old"),
      two = store.load("new");
    fresh.resolve({ id: "new", messages: [] });
    await two;
    old.resolve({ id: "old", messages: [] });
    await one;
    expect(store.current?.id).toBe("new");
  });
});
