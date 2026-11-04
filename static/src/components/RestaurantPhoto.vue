<script setup lang="ts">
import { ref, watch } from "vue";
import type { Restaurant } from "../api/types";
import Icon from "./Icon.vue";

const props = defineProps<{
  restaurant: Pick<Restaurant, "name" | "photoUrl">;
}>();
const photoFailed = ref(false);
watch(
  () => props.restaurant.photoUrl,
  () => (photoFailed.value = false),
);
</script>

<template>
  <figure class="restaurant-cover">
    <template v-if="restaurant.photoUrl && !photoFailed">
      <img
        :src="restaurant.photoUrl"
        :alt="restaurant.name + '的商家照片'"
        loading="lazy"
        decoding="async"
        referrerpolicy="no-referrer"
        @error="photoFailed = true"
      />
      <figcaption>商家图片 · 高德</figcaption>
    </template>
    <div v-else class="restaurant-photo-empty">
      <Icon name="Image" :size="28" />
      <span>{{ photoFailed ? "照片暂时无法加载" : "暂无店铺照片" }}</span>
    </div>
  </figure>
</template>

<style scoped>
.restaurant-cover {
  height: var(--restaurant-cover-height, 152px);
  margin: 0 0 17px;
  position: relative;
  overflow: hidden;
  border-radius: 9px;
  background: #eef2e8;
}
.restaurant-cover img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}
.restaurant-cover figcaption {
  position: absolute;
  left: 10px;
  bottom: 9px;
  font-size: 9px;
  line-height: 1.5;
  background: #fffef0e8;
  color: #64735b;
  padding: 3px 7px;
  border-radius: 5px;
}
.restaurant-photo-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 7px;
  color: #a8b49c;
  background: linear-gradient(120deg, #edf1e4, #f6f6ed);
  font-size: 10px;
}
@media (max-width: 720px) {
  .restaurant-cover {
    height: var(--restaurant-cover-height, 155px);
  }
}
</style>
