<script setup lang="ts">
import { computed } from "vue";
import type {
  ChatPlace,
  ChatRestaurantSearch,
  RestaurantMode,
} from "../api/types";
import { mealSearchUrl } from "../api/takeawayIdeas";
import { walkingUrl } from "../api/restaurantActions";
import Icon from "./Icon.vue";
import TakeawaySearchHelp from "./TakeawaySearchHelp.vue";

const props = defineProps<{
  search: ChatRestaurantSearch;
  mode?: RestaurantMode;
  disabled: boolean;
}>();
const emit = defineEmits<{
  choose: [mode: RestaurantMode, place: ChatPlace];
}>();
const hospital = computed(() => props.search.category === "hospital");
const pharmacy = computed(() => props.search.category === "pharmacy");
const mode = computed(() => (hospital.value ? "dine-in" : props.mode));
const title = computed(() => {
  if (hospital.value) return "附近医院";
  if (pharmacy.value)
    return mode.value === "takeaway"
      ? "药店外卖搜索"
      : mode.value === "dine-in"
        ? "药店导航"
        : "附近药店";
  return mode.value === "takeaway"
    ? "美团外卖"
    : mode.value === "dine-in"
      ? "到店导航"
      : "校园好味探索";
});
const region = computed(() => {
  if (hospital.value) return "医院导航卡片";
  if (pharmacy.value) return title.value;
  return mode.value === "takeaway"
    ? "美团外卖卡片"
    : mode.value === "dine-in"
      ? "到店导航卡片"
      : "聊天找店结果";
});
</script>

<template>
  <section class="structured-card restaurant-cards" :aria-label="region">
    <span class="tag">{{
      search.places[0].source === "amap" ? "高德地点数据" : "演示商家"
    }}</span>
    <h3>{{ title }}</h3>
    <p v-if="!mode" class="restaurant-question">
      {{
        pharmacy
          ? "请问您打算到店买药，还是点外卖配送？您可以直接回复，或选择下面的一家药店。"
          : "请问您打算点外卖，还是到店吃？您可以直接回复，或在下面选一家店。"
      }}
    </p>
    <p v-if="search.keyword && !hospital && !pharmacy">
      搜索关键词：{{ search.keyword }}
    </p>
    <article
      v-for="(place, index) in search.places"
      :key="place.id"
      class="chat-place"
    >
      <h4>
        {{ search.places.length > 1 ? index + 1 + " · " : "" }}{{ place.name }}
      </h4>
      <p>{{ place.address }}</p>
      <div class="restaurant-meta">
        <span v-if="place.distance != null"
          >距{{ search.origin === "location" ? "当前位置" : "校园搜索中心" }}约
          {{ Math.round(place.distance) }} 米</span
        >
        <span v-if="place.rating != null && !hospital && !pharmacy"
          >评分 {{ place.rating }}</span
        >
        <span v-if="place.price != null && !hospital && !pharmacy"
          >人均 ¥{{ place.price }}</span
        >
      </div>
      <div v-if="!mode" class="restaurant-actions">
        <button
          type="button"
          class="primary"
          :disabled="disabled"
          @click="emit('choose', 'takeaway', place)"
        >
          <Icon name="Bike" :size="17" /> 点外卖
        </button>
        <button
          type="button"
          class="secondary"
          :disabled="disabled"
          @click="emit('choose', 'dine-in', place)"
        >
          <Icon name="MapPinned" :size="17" />
          {{ pharmacy ? "到店买" : "到店吃" }}
        </button>
      </div>
      <p v-else-if="place.source === 'demo'" class="source-note">
        这是演示商家。连接高德后可以打开真实商家的美团搜索或高德导航。
      </p>
      <template v-else-if="mode === 'takeaway'">
        <a
          class="primary restaurant-link"
          :href="mealSearchUrl(place.name)"
          target="_blank"
          rel="noopener noreferrer"
          ><Icon name="ArrowUpRight" :size="17" />
          {{ pharmacy ? "在美团搜索这家药店" : "在美团外卖搜这家店" }}</a
        >
        <p class="source-note">
          {{
            pharmacy
              ? "按药店名搜索。可在美团 App 的「看病买药」频道确认药师服务、药品和配送范围。"
              : "用这家分店的完整店名搜索。是否可配送、菜品是否在售，请在美团确认。"
          }}
        </p>
        <TakeawaySearchHelp
          :keyword="place.name"
          term-kind="店名"
          :channel="pharmacy ? '看病买药' : '外卖'"
        />
      </template>
      <template v-else>
        <template v-if="walkingUrl(place)">
          <a
            class="primary restaurant-link"
            :href="walkingUrl(place)"
            target="_blank"
            rel="noopener noreferrer"
            ><Icon name="Navigation" :size="17" /> 去导航</a
          >
          <p class="source-note">打开后可在高德确认出发点。</p>
        </template>
        <p v-else class="source-note">
          该地点暂缺有效坐标，可以按上面的地址在高德地图搜索。
        </p>
      </template>
    </article>
    <RouterLink
      v-if="!hospital && !pharmacy && !mode"
      :to="{ path: '/map', query: search.keyword ? { q: search.keyword } : {} }"
      class="text-btn more-restaurants"
      >在校园美食地图查看更多</RouterLink
    >
  </section>
</template>

<style scoped>
.restaurant-cards {
  max-width: 600px;
}
.restaurant-question {
  font-weight: 650;
  color: #315642;
}
.chat-place {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 16px;
  margin-top: 10px;
  overflow-wrap: anywhere;
}
.chat-place h4 {
  margin: 0 0 6px;
  font-family: var(--sans);
  font-size: 18px;
  font-weight: 700;
  color: #1a241c;
}
.chat-place p {
  margin: 5px 0;
  color: #546c53;
}
.restaurant-meta {
  display: flex;
  gap: 4px 12px;
  flex-wrap: wrap;
  color: #546c53;
  font-size: 14px;
}
.restaurant-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.restaurant-actions button,
.restaurant-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
}
.restaurant-link {
  margin: 10px 0 4px;
}
</style>
