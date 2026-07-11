import { defineStore } from "pinia";
import { ref } from "vue";
import { conversationApi } from "@/api/conversations";
import { consumeStream } from "@/api/sse";
import type {
  Conversation,
  ConversationMessage,
  ConversationSummary,
  StreamEvent,
} from "@/api/types";
import { requestKey } from "@/utils/id";

export const useConversationStore = defineStore("conversations", () => {
  const items = ref<ConversationSummary[]>([]),
    current = ref<Conversation | null>(null),
    loading = ref(false),
    error = ref(""),
    streaming = ref(false),
    streamId = ref<string>(),
    lastEventId = ref(0);
  let controller: AbortController | undefined,
    generation = 0,
    loadGeneration = 0,
    activeConversationId: string | undefined,
    activeAssistantId: string | undefined;
  async function list() {
    loading.value = !items.value.length;
    error.value = "";
    try {
      items.value = (await conversationApi.list()).data;
    } catch (e) {
      error.value = message(e);
    } finally {
      loading.value = false;
    }
  }
  async function create(title?: string, tripId?: string) {
    return conversationApi.create({
      title: title || undefined,
      tripId: tripId || undefined,
    });
  }
  async function load(id: string) {
    const mine = ++loadGeneration;
    loading.value = true;
    error.value = "";
    current.value = null;
    try {
      const value = await conversationApi.get(id);
      if (mine === loadGeneration) current.value = value;
    } catch (e) {
      if (mine === loadGeneration) error.value = message(e);
    } finally {
      if (mine === loadGeneration) loading.value = false;
    }
  }

  async function send(content: string) {
    if (!current.value || streaming.value) return;
    const mine = ++generation,
      conversationId = current.value.id;
    activeConversationId = conversationId;
    const optimistic: ConversationMessage = {
      id: `local-${requestKey()}`,
      role: "USER",
      content,
      status: "COMPLETED",
      createdAt: new Date().toISOString(),
    };
    const assistant: ConversationMessage = {
      id: `local-${requestKey()}`,
      role: "ASSISTANT",
      content: "",
      status: "STREAMING",
      createdAt: new Date().toISOString(),
    };
    current.value.messages.push(optimistic, assistant);
    streaming.value = true;
    error.value = "";
    streamId.value = undefined;
    lastEventId.value = 0;
    controller = new AbortController();
    let terminal = false;
    const receive = (event: StreamEvent) => {
      terminal = apply(event, optimistic, assistant) || terminal;
    };
    try {
      await consumeStream(
        `/conversations/${conversationId}/messages:stream`,
        {
          method: "POST",
          headers: { "Idempotency-Key": requestKey() },
          body: JSON.stringify({ content }),
          signal: controller.signal,
        },
        receive,
        (response) => {
          const id = response.headers.get("X-Stream-Id");
          if (id) streamId.value = id;
        },
      );
      if (!terminal && streamId.value)
        terminal = await reconnect(conversationId, assistant.id, mine, receive);
    } catch (e) {
      if (mine === generation && !controller.signal.aborted && streamId.value)
        terminal = await reconnect(conversationId, assistant.id, mine, receive);
      if (!terminal && mine === generation && !controller.signal.aborted)
        error.value = message(e);
    } finally {
      if (mine === generation) {
        if (terminal) {
          await load(conversationId);
          streamId.value = undefined;
          activeConversationId = undefined;
          activeAssistantId = undefined;
        }
        streaming.value = false;
        controller = undefined;
      }
    }
  }

  async function reconnect(
    conversationId: string,
    assistantId: string,
    mine: number,
    receive: (event: StreamEvent) => void,
  ) {
    const deadline = Date.now() + 29_000;
    let delay = 500;
    while (Date.now() < deadline && mine === generation) {
      try {
        await visibleDelay(delay, mine, deadline);
      } catch {
        return false;
      }
      if (mine !== generation) return false;
      controller = new AbortController();
      let terminal = false;
      try {
        await consumeStream(
          `/conversations/${conversationId}/streams/${streamId.value}`,
          {
            method: "GET",
            headers: { "Last-Event-ID": String(lastEventId.value) },
            signal: controller.signal,
          },
          (event) => {
            if (event.type === "delta" && event.data.messageId !== assistantId)
              throw new Error("流消息不匹配");
            receive(event);
            if (
              event.type === "done" ||
              (event.type === "error" && event.data.final)
            )
              terminal = true;
          },
        );
        if (terminal) return true;
      } catch {
        if (mine !== generation || controller.signal.aborted) return false;
      }
      delay = Math.min(4_000, delay * 2);
    }
    return false;
  }

  function apply(
    event: StreamEvent,
    user: ConversationMessage,
    assistant: ConversationMessage,
  ) {
    if (event.id && event.id <= lastEventId.value) return false;
    if (streamId.value && event.data.streamId !== streamId.value)
      throw new Error("流标识不匹配");
    if (
      event.type === "delta" &&
      event.id !== undefined &&
      event.data.sequence !== event.id
    )
      throw new Error("流序号不匹配");
    if (
      (event.type === "delta" || event.type === "done") &&
      event.data.messageId !== assistant.id
    )
      throw new Error("流消息不匹配");
    if (event.id) lastEventId.value = event.id;
    if (event.type === "ack") {
      streamId.value = event.data.streamId;
      user.id = event.data.userMessageId;
      assistant.id = event.data.assistantMessageId;
      activeAssistantId = event.data.assistantMessageId;
    } else if (event.type === "delta") {
      assistant.content += event.data.content;
    } else if (event.type === "done") {
      assistant.status = "COMPLETED";
      return true;
    } else if (event.data.final) {
      assistant.status = "FAILED";
      assistant.errorCode = event.data.code;
      error.value = event.data.message;
      return true;
    } else error.value = event.data.message;
    return false;
  }
  async function visibleDelay(ms: number, mine: number, deadline: number) {
    let elapsed = 0,
      last = Date.now();
    while (elapsed < ms) {
      if (mine !== generation || Date.now() >= deadline)
        throw new DOMException("cancelled", "AbortError");
      await new Promise<void>((resolve, reject) => {
        const signal = controller?.signal;
        const finish = () => {
          signal?.removeEventListener("abort", abort);
          resolve();
        };
        const abort = () => {
          clearTimeout(timer);
          signal?.removeEventListener("abort", abort);
          reject(new DOMException("cancelled", "AbortError"));
        };
        const timer = window.setTimeout(finish, 100);
        signal?.addEventListener("abort", abort, { once: true });
      });
      const now = Date.now();
      if (document.visibilityState !== "hidden") elapsed += now - last;
      last = now;
    }
  }
  async function resume(conversationId: string) {
    if (!streamId.value || activeConversationId !== conversationId) return;
    const assistant =
        current.value?.messages.find((m) => m.id === activeAssistantId) ||
        current.value?.messages.find(
          (m) => m.role === "ASSISTANT" && m.status === "STREAMING",
        ),
      user = current.value?.messages.find((m) => m.role === "USER") ?? {
        id: "unknown",
        role: "USER",
        content: "",
        status: "COMPLETED",
        createdAt: "",
      };
    if (!assistant) return;
    const mine = ++generation;
    streaming.value = true;
    controller = new AbortController();
    let terminal = false;
    const receive = (event: StreamEvent) => {
      terminal = apply(event, user, assistant) || terminal;
    };
    terminal = await reconnect(conversationId, assistant.id, mine, receive);
    if (mine === generation) {
      if (terminal) {
        await load(conversationId);
        streamId.value = undefined;
        activeConversationId = undefined;
        activeAssistantId = undefined;
      }
      streaming.value = false;
      controller = undefined;
    }
  }
  async function cancel() {
    const conversationId = current.value?.id ?? activeConversationId,
      id = streamId.value;
    ++generation;
    controller?.abort();
    streaming.value = false;
    if (conversationId && id) await conversationApi.cancel(conversationId, id);
    streamId.value = undefined;
    activeConversationId = undefined;
    activeAssistantId = undefined;
    if (conversationId) await load(conversationId);
  }
  function disconnect() {
    ++generation;
    controller?.abort();
    controller = undefined;
    streaming.value = false;
  }
  return {
    items,
    current,
    loading,
    error,
    streaming,
    streamId,
    list,
    create,
    load,
    send,
    resume,
    cancel,
    disconnect,
  };
});
const message = (e: unknown) => (e instanceof Error ? e.message : "操作失败");
