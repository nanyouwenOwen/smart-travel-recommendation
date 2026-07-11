import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useConversationStore } from "./conversations";
import { conversationApi } from "@/api/conversations";
import { consumeStream } from "@/api/sse";
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
vi.mock("@/utils/id", () => ({
  requestKey: vi.fn(() => `key-${Math.random()}`),
}));
const conversation = {
  id: "c1",
  title: "咨询",
  createdAt: "now",
  updatedAt: "now",
  messages: [],
};
describe("conversation store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    vi.useRealTimers();
  });
  it("renders ordered SSE deltas and reloads final server state", async () => {
    vi.mocked(conversationApi.get).mockResolvedValue(conversation);
    vi.mocked(consumeStream).mockImplementation(async (_p, _i, on) => {
      on({
        id: 1,
        type: "ack",
        data: {
          streamId: "s1",
          userMessageId: "u1",
          assistantMessageId: "a1",
          eventId: 1,
        },
      });
      on({
        id: 2,
        type: "delta",
        data: { streamId: "s1", messageId: "a1", sequence: 2, content: "你" },
      });
      on({
        id: 2,
        type: "delta",
        data: { streamId: "s1", messageId: "a1", sequence: 2, content: "重复" },
      });
      on({
        id: 3,
        type: "delta",
        data: { streamId: "s1", messageId: "a1", sequence: 3, content: "好" },
      });
      on({
        id: 4,
        type: "done",
        data: {
          streamId: "s1",
          messageId: "a1",
          status: "COMPLETED",
          usage: {},
          replayed: false,
        },
      });
    });
    const store = useConversationStore();
    await store.load("c1");
    await store.send("问题");
    expect(consumeStream).toHaveBeenCalledOnce();
    expect(conversationApi.get).toHaveBeenCalledTimes(2);
    expect(store.streaming).toBe(false);
  });
  it("cancels the active server stream", async () => {
    vi.mocked(conversationApi.get).mockResolvedValue(conversation);
    vi.mocked(consumeStream).mockImplementation(async (_p, _i, on) => {
      on({
        id: 1,
        type: "ack",
        data: {
          streamId: "s1",
          userMessageId: "u1",
          assistantMessageId: "a1",
          eventId: 1,
        },
      });
      await new Promise(() => {});
    });
    const store = useConversationStore();
    await store.load("c1");
    void store.send("问题");
    await vi.waitFor(() => expect(store.streamId).toBe("s1"));
    vi.mocked(conversationApi.get).mockResolvedValue(conversation);
    await store.cancel();
    expect(conversationApi.cancel).toHaveBeenCalledWith("c1", "s1");
  });
  it("does not reconnect after cancellation during retry backoff", async () => {
    vi.useFakeTimers();
    vi.mocked(conversationApi.get).mockResolvedValue(conversation);
    vi.mocked(consumeStream).mockImplementationOnce(async (_p, _i, on) => {
      on({
        id: 1,
        type: "ack",
        data: {
          streamId: "s1",
          userMessageId: "u1",
          assistantMessageId: "a1",
          eventId: 1,
        },
      });
      throw new Error("disconnect");
    });
    const store = useConversationStore();
    await store.load("c1");
    const sending = store.send("问题");
    await vi.advanceTimersByTimeAsync(100);
    await store.cancel();
    await vi.advanceTimersByTimeAsync(10_000);
    await sending;
    expect(consumeStream).toHaveBeenCalledTimes(1);
  });
  it("lists, creates and disconnects without retaining stale state", async () => {
    vi.mocked(conversationApi.list).mockResolvedValue({
      data: [{ id: "c1", title: "咨询", updatedAt: "now" }],
      meta: { requestId: "r", hasMore: false },
    });
    vi.mocked(conversationApi.create).mockResolvedValue(conversation);
    const store = useConversationStore();
    await store.list();
    expect((await store.create("咨询")).id).toBe("c1");
    store.disconnect();
    expect(store.streaming).toBe(false);
  });
});
