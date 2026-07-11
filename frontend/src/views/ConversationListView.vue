<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useConversationStore } from "@/stores/conversations";
import { useTripStore } from "@/stores/trips";
import ViewState from "@/components/ViewState.vue";
const conversations = useConversationStore(),
  trips = useTripStore(),
  router = useRouter(),
  title = ref(""),
  tripId = ref(""),
  busy = ref(false),
  error = ref("");
onMounted(() => Promise.all([conversations.list(), trips.list()]));
async function create() {
  busy.value = true;
  error.value = "";
  try {
    const c = await conversations.create(title.value, tripId.value);
    await router.push(`/conversations/${c.id}`);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "创建失败";
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">TRAVEL CONCIERGE</p>
        <h1 tabindex="-1">AI 旅游咨询</h1>
        <p class="lead">围绕已有行程继续提问，或开启一段独立对话。</p>
      </div>
    </div>
    <p
      v-if="conversations.error && conversations.items.length"
      class="banner error"
      role="alert"
    >
      {{ conversations.error }}
      <button class="text-button" @click="conversations.list()">重试</button>
    </p>
    <div class="conversation-layout">
      <form class="panel create-conversation" @submit.prevent="create">
        <h2>开始新会话</h2>
        <label
          >标题（可选）<input
            v-model.trim="title"
            maxlength="100"
            placeholder="例如：京都交通问题" /></label
        ><label
          >关联行程（可选）<select v-model="tripId">
            <option value="">不关联行程</option>
            <option
              v-for="trip in trips.items.filter((x) => x.status === 'READY')"
              :key="trip.id"
              :value="trip.id"
            >
              {{ trip.destination }} · {{ trip.startDate }}
            </option>
          </select></label
        >
        <p v-if="error" class="form-error">{{ error }}</p>
        <button :disabled="busy">
          {{ busy ? "创建中…" : "创建并开始提问" }}
        </button>
      </form>
      <ViewState
        :loading="conversations.loading"
        :error="!conversations.items.length ? conversations.error : ''"
        :empty="!conversations.items.length"
        empty-text="暂无历史会话。"
        @retry="conversations.list()"
        ><div class="conversation-list">
          <RouterLink
            v-for="item in conversations.items"
            :key="item.id"
            :to="`/conversations/${item.id}`"
            ><div>
              <h2>{{ item.title || "未命名会话" }}</h2>
              <p>{{ new Date(item.updatedAt).toLocaleString() }}</p>
            </div>
            <span>继续 →</span></RouterLink
          >
        </div></ViewState
      >
    </div>
  </section>
</template>
