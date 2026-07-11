<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import type { LocationReference, NearbyPlace } from "@/api/types";
const props = defineProps<{
    location: LocationReference;
    places: NearbyPlace[];
  }>(),
  element = ref<HTMLElement>(),
  failed = ref(false);
let map: L.Map | undefined, markers: L.LayerGroup | undefined;
function renderPlaces() {
  if (!map) return;
  markers?.clearLayers();
  markers = L.layerGroup().addTo(map);
  for (const place of props.places)
    L.circleMarker([place.latitude, place.longitude], {
      radius: 5,
      color: "#bd6638",
    })
      .bindPopup(place.name)
      .addTo(markers);
}
onMounted(() => {
  try {
    if (!element.value) return;
    map = L.map(element.value, { scrollWheelZoom: false }).setView(
      [props.location.latitude, props.location.longitude],
      13,
    );
    const tiles = L.tileLayer(
      import.meta.env.VITE_MAP_TILE_URL ||
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
      {
        maxZoom: 19,
        attribution:
          '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      },
    );
    tiles.on("tileerror", () => {
      failed.value = true;
    });
    tiles.addTo(map);
    L.circleMarker([props.location.latitude, props.location.longitude], {
      radius: 8,
    })
      .bindPopup(props.location.displayName)
      .addTo(map);
    renderPlaces();
  } catch {
    failed.value = true;
  }
});
watch(() => props.places, renderPlaces, { deep: true });
onBeforeUnmount(() => map?.remove());
</script>
<template>
  <section class="panel realtime-panel">
    <h2>目的地地图</h2>
    <p v-if="failed" class="banner warning">
      地图瓦片暂时无法加载，仍可查看地点文字列表。
    </p>
    <div
      ref="element"
      class="destination-map"
      :aria-label="`${location.displayName} 地图`"
    ></div>
    <p class="fine">
      地图数据 ©
      <a
        href="https://www.openstreetmap.org/copyright"
        target="_blank"
        rel="noopener noreferrer"
        >OpenStreetMap contributors</a
      >
    </p>
  </section>
</template>
