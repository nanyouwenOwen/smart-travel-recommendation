<script setup lang="ts">
import { onMounted } from "vue";
import { useTripStore } from "@/stores/trips";
import StatusBadge from "@/components/StatusBadge.vue";
import ViewState from "@/components/ViewState.vue";
const store = useTripStore();
onMounted(() => store.list());
</script>
<template>
  <section class="page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">MY JOURNEYS</p>
        <h1 tabindex="-1">我的行程</h1>
      </div>
      <RouterLink class="button" to="/trips/new">＋ 新建规划</RouterLink>
    </div>
    <p
      v-if="store.error && store.items.length"
      class="banner error"
      role="alert"
    >
      {{ store.error }}
      <button class="text-button" @click="store.list()">重试</button>
    </p>
    <ViewState
      :loading="store.loading"
      :error="!store.items.length ? store.error : ''"
      :empty="!store.items.length"
      empty-text="还没有行程，从一个想去的地方开始吧。"
      @retry="store.list()"
      ><template #action
        ><RouterLink class="button" to="/trips/new"
          >创建第一段旅程</RouterLink
        ></template
      >
      <div class="card-grid">
        <RouterLink
          v-for="trip in store.items"
          :key="trip.id"
          class="trip-card"
          :to="`/trips/${trip.id}`"
          ><div>
            <p class="date">{{ trip.startDate }} · {{ trip.days }} 天</p>
            <h2>{{ trip.destination }}</h2>
          </div>
          <StatusBadge :status="trip.status" />
          <p v-if="trip.currentVersion" class="muted">
            当前版本 v{{ trip.currentVersion }}
          </p></RouterLink
        >
      </div>
      <button
        v-if="store.hasMore"
        class="secondary load-more"
        @click="store.list(false)"
      >
        加载更多
      </button></ViewState
    >
  </section>
</template>
