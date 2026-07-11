<script setup lang="ts">
import type { ConversationMessage } from "@/api/types";
import StatusBadge from "@/components/StatusBadge.vue";
import SourceList from "@/components/realtime/SourceList.vue";
defineProps<{ messages: ConversationMessage[] }>();
</script>
<template>
  <div class="messages" role="log" aria-label="对话消息">
    <article
      v-for="message in messages"
      :key="message.id"
      class="message"
      :class="message.role.toLowerCase()"
    >
      <div class="avatar">{{ message.role === "USER" ? "你" : "AI" }}</div>
      <div class="bubble">
        <div class="message-meta">
          <b>{{ message.role === "USER" ? "你" : "旅途智囊" }}</b
          ><StatusBadge
            v-if="message.status !== 'COMPLETED'"
            :status="message.status"
          />
        </div>
        <p>
          {{
            message.content ||
            (message.status === "STREAMING" ? "正在思考…" : "暂无内容")
          }}
        </p>
        <small v-if="message.errorCode" class="form-error">{{
          message.errorCode
        }}</small
        ><SourceList
          v-if="message.role === 'ASSISTANT' && message.sources?.length"
          :sources="message.sources"
        /><small
          v-else-if="
            message.role === 'ASSISTANT' && message.status === 'COMPLETED'
          "
          class="fine"
          >一般 AI 建议，未使用实时数据，请在出行前核验。</small
        >
      </div>
    </article>
    <p class="sr-only" aria-live="polite">
      {{
        messages[messages.length - 1]?.status === "COMPLETED"
          ? "AI 回答已完成"
          : ""
      }}
    </p>
  </div>
</template>
