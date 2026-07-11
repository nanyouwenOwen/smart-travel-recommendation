<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useTripStore } from "@/stores/trips";
import { realtimeApi } from "@/api/realtime";
import type { PlaceSnapshot, WeatherSnapshot } from "@/api/types";
import ViewState from "@/components/ViewState.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
import BudgetSummary from "@/components/trips/BudgetSummary.vue";
import ItineraryTimeline from "@/components/trips/ItineraryTimeline.vue";
import TripVersionPanel from "@/components/trips/TripVersionPanel.vue";
import DestinationMap from "@/components/realtime/DestinationMap.vue";
import WeatherPanel from "@/components/realtime/WeatherPanel.vue";
import NearbyPlacesPanel from "@/components/realtime/NearbyPlacesPanel.vue";
const route = useRoute(),
  router = useRouter(),
  store = useTripStore(),
  id = String(route.params.tripId),
  dialog = ref<InstanceType<typeof ConfirmDialog>>(),
  weather = ref<WeatherSnapshot>(),
  places = ref<PlaceSnapshot>(),
  realtimeLoading = ref(false),
  realtimeError = ref(""),
  weatherError = ref(""),
  placesError = ref("");
onMounted(async () => {
  await store.load(id);
  if (store.current?.status === "READY") {
    await store.loadVersions();
    if (store.current.destinationLocation) await loadRealtime();
  }
});
onBeforeUnmount(store.stopPolling);
async function loadRealtime() {
  realtimeLoading.value = true;
  realtimeError.value = "";
  weatherError.value = "";
  placesError.value = "";
  try {
    const [w, p] = await Promise.allSettled([
      realtimeApi.weather(id),
      realtimeApi.places(id),
    ]);
    if (w.status === "fulfilled") weather.value = w.value;
    else weatherError.value = "天气数据暂不可用";
    if (p.status === "fulfilled") places.value = p.value;
    else placesError.value = "景点数据暂不可用";
    if (w.status === "rejected" || p.status === "rejected")
      realtimeError.value =
        [weatherError.value, placesError.value].filter(Boolean).join("；") +
        "，核心行程不受影响。";
  } finally {
    realtimeLoading.value = false;
  }
}
async function remove() {
  if (await dialog.value?.confirm("确定删除这个行程？此操作会从列表隐藏。")) {
    await store.remove();
    await router.push("/trips");
  }
}
</script>
<template>
  <section class="page">
    <ViewState
      :loading="store.loading && !store.current"
      :error="store.error"
      :empty="!store.current"
      @retry="store.load(id)"
      ><template v-if="store.current"
        ><div class="trip-hero">
          <div>
            <RouterLink to="/trips" class="back">← 返回行程</RouterLink>
            <p class="eyebrow">
              {{ store.current.startDate }} · {{ store.current.days }} DAYS
            </p>
            <h1 tabindex="-1">{{ store.current.destination }}</h1>
            <p>
              {{ store.current.travelers }} 人 · {{ store.current.timezone }}
              <span v-if="store.current.currentVersion"
                >· v{{ store.current.currentVersion }}</span
              >
            </p>
          </div>
          <StatusBadge :status="store.current.status" />
        </div>
        <div v-if="store.current.status === 'GENERATING'" class="generation">
          <div class="spinner"></div>
          <h2>AI 正在编排行程</h2>
        </div>
        <div v-else-if="store.current.status === 'FAILED'" class="banner error">
          <h2>生成未完成</h2>
          <p>错误代码：{{ store.current.failureCode }}</p>
        </div>
        <template v-else-if="store.current.status === 'READY'"
          ><ul v-if="store.current.warnings?.length" class="warnings">
            <li v-for="warning in store.current.warnings" :key="warning">
              {{ warning }}
            </li>
          </ul>
          <div v-if="store.current.destinationLocation" class="realtime-grid">
            <DestinationMap
              :location="store.current.destinationLocation"
              :places="places?.places ?? []"
            /><WeatherPanel
              v-if="weather"
              :snapshot="weather"
            /><NearbyPlacesPanel v-if="places" :snapshot="places" />
            <p v-if="realtimeLoading" class="panel" role="status">
              正在获取实时旅游数据…
            </p>
            <p v-if="realtimeError" class="banner warning">
              {{ realtimeError }}
              <button class="text-button" @click="loadRealtime">重试</button>
            </p>
          </div>
          <div v-else class="banner warning">
            此行程未绑定明确地点，天气、地图和附近景点暂不可用。新建行程时可通过“搜索地点”选择。
          </div>
          <div class="detail-grid">
            <ItineraryTimeline :days="store.current.itinerary" />
            <aside>
              <BudgetSummary :trip="store.current" /><TripVersionPanel />
            </aside></div></template
        ><button class="danger text-button" @click="remove">删除行程</button
        ><ConfirmDialog ref="dialog" /></template
    ></ViewState>
  </section>
</template>
