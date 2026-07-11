import { api, apiPage } from "./client";
import type {
  Conversation,
  ConversationMessage,
  ConversationSummary,
} from "./types";
const enc = encodeURIComponent;
export const conversationApi = {
  list: (cursor?: string) =>
    apiPage<ConversationSummary[]>(
      `/conversations?limit=30${cursor ? `&cursor=${enc(cursor)}` : ""}`,
    ),
  create: (body: { title?: string; tripId?: string }) =>
    api<Conversation>("/conversations", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  get: (id: string) => api<Conversation>(`/conversations/${enc(id)}`),
  messages: (id: string, cursor?: string) =>
    apiPage<ConversationMessage[]>(
      `/conversations/${enc(id)}/messages?limit=100${cursor ? `&cursor=${enc(cursor)}` : ""}`,
    ),
  cancel: (conversation: string, stream: string) =>
    api<void>(`/conversations/${enc(conversation)}/streams/${enc(stream)}`, {
      method: "DELETE",
    }),
};
