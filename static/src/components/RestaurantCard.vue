<script setup lang="ts">
import type { Restaurant } from "../api/types";
import Icon from "./Icon.vue";
import { useApp } from "../stores/app";
import RestaurantPhoto from "./RestaurantPhoto.vue";
defineProps<{ restaurant: Restaurant; selected?: boolean }>();
defineEmits<{
  select: [restaurant: Restaurant];
  navigate: [restaurant: Restaurant];
}>();
const app = useApp();
</script>
<template>
  <article class="restaurant-card" :class="{ selected }">
    <RestaurantPhoto :restaurant="restaurant" />
    <div class="restaurant-top">
      <div>
        <h3>{{ restaurant.name }}</h3>
        <p>
          <span class="rating">★ {{ restaurant.rating ?? "未提供" }}</span> ·
          {{
            restaurant.price === null
              ? "人均未提供"
              : "¥" + restaurant.price + " / 人"
          }}
          ·
          {{
            restaurant.distance === null
              ? "距离未提供"
              : restaurant.distance + "m"
          }}
        </p>
      </div>
      <button
        class="icon-btn"
        :aria-label="
          app.favorites.includes(restaurant.id) ? '取消收藏' : '收藏商家'
        "
        @click="app.toggleFavorite(restaurant.id)"
      >
        <Icon
          name="Heart"
          :class="{ filled: app.favorites.includes(restaurant.id) }"
        />
      </button>
    </div>
    <p>{{ restaurant.reason }}</p>
    <p class="muted small">{{ restaurant.address }} · {{ restaurant.hours }}</p>
    <div class="between">
      <span class="tag">{{
        restaurant.source === "demo" ? "虚构商家 · 演示" : "高德数据"
      }}</span>
      <div class="actions">
        <button class="text-btn" @click="$emit('select', restaurant)">
          查看详情</button
        ><button
          class="secondary compact"
          @click="$emit('navigate', restaurant)"
        >
          <Icon name="Navigation" :size="15" /> 去导航
        </button>
      </div>
    </div>
  </article>
</template>
