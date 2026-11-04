<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from "vue";
import type { Restaurant } from "../api/types";
declare global {
  interface Window {
    AMap: any;
    _AMapSecurityConfig: { serviceHost: string };
  }
}
const props = defineProps<{
    restaurants: Restaurant[];
    selected?: Restaurant;
  }>(),
  emit = defineEmits<{ select: [restaurant: Restaurant] }>();
const container = ref<HTMLElement>(),
  error = ref(""),
  loading = ref(true),
  ready = ref(false);
let map: any,
  disposed = false,
  observer: ResizeObserver | undefined,
  loadTimer: ReturnType<typeof setTimeout>;
function draw() {
  if (!map) return;
  map.clearMap();
  const markers = props.restaurants
    .filter(
      (r) =>
        r.source === "amap" && Number.isFinite(r.lng) && Number.isFinite(r.lat),
    )
    .map((r) => {
      const marker = new window.AMap.Marker({
        position: [r.lng, r.lat],
        title: r.name,
      });
      marker.on("click", () => emit("select", r));
      return marker;
    });
  map.add(markers);
  if (markers.length) map.setFitView(markers);
}
onMounted(async () => {
  const key = import.meta.env.VITE_AMAP_JS_KEY;
  if (!key) {
    error.value =
      "地图画布未配置：请填写前端高德 JS Key。真实商家列表仍可由后端提供。";
    loading.value = false;
    return;
  }
  try {
    if (!window.AMap) {
      window._AMapSecurityConfig = {
        serviceHost: location.origin + "/api/amap/_AMapService",
      };
      await new Promise<void>((resolve, reject) => {
        const script = document.createElement("script");
        const timer = setTimeout(
          () => reject(new Error("高德 SDK 加载超时，请检查网络及 Key 权限")),
          15000,
        );
        script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}`;
        script.onload = () => {
          clearTimeout(timer);
          resolve();
        };
        script.onerror = () => {
          clearTimeout(timer);
          reject(new Error("高德 SDK 加载失败"));
        };
        document.head.appendChild(script);
      });
    }
    if (disposed) return;
    map = new window.AMap.Map(container.value, { zoom: 15 });
    map.on("complete", () => {
      ready.value = true;
      loading.value = false;
      clearTimeout(loadTimer);
    });
    map.on("error", () => {
      error.value = "高德地图加载异常，请检查 JS Key、安全密钥及域名设置。";
      loading.value = false;
    });
    observer = new ResizeObserver(() => {
      map?.resize();
      if (props.restaurants.length) map?.setFitView();
    });
    observer.observe(container.value!);
    loadTimer = setTimeout(() => {
      if (!ready.value) {
        error.value = "地图底图加载超时，请检查网络与高德服务配置。";
        loading.value = false;
      }
    }, 25000);
    draw();
  } catch (e) {
    error.value = (e as Error).message;
    loading.value = false;
  }
});
watch(() => props.restaurants, draw, { deep: true });
watch(
  () => props.selected,
  (r) => {
    if (map && r?.lng && r?.lat) map.panTo([r.lng, r.lat]);
  },
);
onBeforeUnmount(() => {
  disposed = true;
  clearTimeout(loadTimer);
  observer?.disconnect();
  map?.destroy();
});
</script>
<template>
  <section class="real-map panel" :data-map-ready="ready">
    <div ref="container" class="real-map-container"></div>
    <p v-if="loading" class="notice map-state">正在加载高德地图…</p>
    <p v-if="error" role="alert" class="notice error map-state">{{ error }}</p>
    <span class="map-legend"
      >高德地图 · 点位来源：真实 POI · 未请求个人定位</span
    >
  </section>
</template>
<style scoped>
.real-map {
  height: 540px;
  position: sticky;
  top: 22px;
  padding: 0;
  overflow: hidden;
  align-self: start;
}
.real-map-container {
  position: absolute;
  inset: 0;
}
.map-state {
  position: relative;
  z-index: 2;
  margin: 20px;
}
@media (max-width: 1200px) {
  .real-map {
    height: 390px;
    position: relative;
    top: 0;
  }
}
</style>
