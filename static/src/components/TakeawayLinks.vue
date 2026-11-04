<script setup lang="ts">
import { computed, ref } from "vue";
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
const status = ref("");
const manualCopy = ref(false);

async function copyName() {
  try {
    await navigator.clipboard.writeText(props.place.name);
    manualCopy.value = false;
    status.value = "已复制店名，可到美团外卖粘贴搜索。";
  } catch {
    manualCopy.value = true;
    status.value = "请选中下方店名，手动复制。";
  }
}
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
    <button class="secondary" type="button" @click="copyName">
      <Icon name="Copy" :size="15" /> 复制店名
    </button>
    <p v-if="status" role="status" class="note">{{ status }}</p>
    <input
      v-if="manualCopy"
      :value="place.name"
      readonly
      aria-label="手动复制店名"
      @focus="($event.target as HTMLInputElement).select()"
    />
  </div>
</template>

<style scoped>
.card-actions {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
  gap: 8px;
  margin-top: auto;
}
.card-actions a,
.card-actions button {
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 5px;
  padding: 8px 4px;
  font-size: 12px;
  min-height: 44px;
}
.note,
input {
  grid-column: 1/-1;
}
.note {
  font-size: 11px;
  color: #526857;
}
input {
  width: 100%;
  min-width: 0;
}
</style>
