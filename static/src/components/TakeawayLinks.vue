<script setup lang="ts">
import { computed } from "vue";
import type { CampusTakeawayEntry } from "../api/campusTakeaway";
import { mealSearchUrl } from "../api/takeawayIdeas";
import type { Restaurant } from "../api/types";
import Icon from "./Icon.vue";

const props = defineProps<{
  place: Restaurant;
  links: CampusTakeawayEntry[];
}>();
const meituan = computed(() =>
  props.links.find((link) => link.platform === "美团外卖"),
);
</script>

<template>
  <div v-if="place.source === 'amap'" class="card-actions">
    <a
      class="primary"
      :href="meituan?.url || mealSearchUrl(place.name)"
      target="_blank"
      rel="noopener noreferrer"
    >
      {{
        meituan
          ? meituan.destination === "item"
            ? "美团餐品"
            : "美团门店"
          : "美团搜店名"
      }}
      <Icon name="ArrowUpRight" :size="15" />
    </a>
  </div>
</template>

<style scoped>
.card-actions {
  display: grid;
  margin-top: auto;
}
.card-actions a {
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 5px;
  padding: 8px 4px;
  font-size: 12px;
  min-height: 44px;
}
</style>
