<script setup lang="ts">
import type { CampusTakeawayEntry } from "../api/campusTakeaway";
import type { Restaurant } from "../api/types";
import TakeawayLinks from "./TakeawayLinks.vue";
import RestaurantPhoto from "./RestaurantPhoto.vue";

defineProps<{ place: Restaurant; links: CampusTakeawayEntry[] }>();
</script>

<template>
  <article class="campus-takeaway-card">
    <RestaurantPhoto :restaurant="place" />
    <span class="tag">{{
      place.source === "amap" ? "高德商家" : "演示商家"
    }}</span>
    <h3>{{ place.name }}</h3>
    <p class="address">{{ place.address }}</p>
    <dl class="metrics">
      <div>
        <dt>参考人均</dt>
        <dd>{{ place.price == null ? "未提供" : "¥" + place.price }}</dd>
      </div>
      <div>
        <dt>评分</dt>
        <dd>{{ place.rating == null ? "未提供" : place.rating + " 分" }}</dd>
      </div>
      <div>
        <dt>距校园</dt>
        <dd>
          {{
            place.distance == null
              ? "未提供"
              : place.distance >= 1000
                ? (place.distance / 1000).toFixed(1) + " 公里"
                : place.distance + " 米"
          }}
        </dd>
      </div>
    </dl>
    <TakeawayLinks :place="place" :links="links" />
  </article>
</template>

<style scoped>
.campus-takeaway-card {
  --restaurant-cover-height: clamp(150px, 17vw, 190px);
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: clamp(16px, 2vw, 22px);
  border: 1px solid var(--border);
  border-radius: 16px;
  background: #fffefb;
  overflow-wrap: anywhere;
}
.tag {
  align-self: flex-start;
}
h3 {
  font-size: clamp(18px, 2vw, 21px);
  line-height: 1.4;
  margin: 11px 0 5px;
  color: #253e30;
}
.address {
  font-size: 13px;
  color: #526857;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin: 15px 0;
  padding: 11px 9px;
  border-radius: 9px;
  background: #f0f3ea;
}
dt {
  font-size: 11px;
  color: #586c5b;
}
dd {
  font-size: 13px;
  font-weight: 700;
  margin: 0;
  color: #263f32;
}
</style>
