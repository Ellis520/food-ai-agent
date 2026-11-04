<script setup lang="ts">
import { highestRatedFirst, walkingUrl } from "../api/restaurantActions";
import { ref, computed, onMounted, onBeforeUnmount, watch } from "vue";
import { useRoute } from "vue-router";
import { api, isDemo } from "../api";
import { useApp } from "../stores/app";
import type { Restaurant } from "../api/types";
import RestaurantCard from "../components/RestaurantCard.vue";
import RestaurantDetails from "../components/RestaurantDetails.vue";
import PageHeader from "../components/PageHeader.vue";
import Icon from "../components/Icon.vue";
import AmapView from "../components/AmapView.vue";

const route = useRoute();
const app = useApp();
const all = ref<Restaurant[]>([]);
const category = ref("全部");
const query = ref(
  typeof route.query.q === "string" ? route.query.q.slice(0, 60) : "",
);
const radius = ref(5000);
const sort = ref<"rating" | "distance" | "price">("rating");
const selected = ref<Restaurant>();
const error = ref("");
const loading = ref(true);
const detail = ref<Restaurant>();
const navigation = ref(false);
const page = ref(0);
const hasMore = ref(false);
let requestId = 0;
let timer: ReturnType<typeof setTimeout> | undefined;

function reset() {
  all.value = [];
  selected.value = undefined;
  detail.value = undefined;
  navigation.value = false;
  page.value = 0;
  hasMore.value = false;
}
async function load(append = false) {
  if (append && loading.value) return;
  clearTimeout(timer);
  const id = ++requestId;
  if (!append) reset();
  const nextPage = page.value + 1;
  loading.value = true;
  error.value = "";
  try {
    const result = await api.searchRestaurants(
      query.value.trim(),
      radius.value,
      nextPage,
    );
    if (id !== requestId) return;
    const unique = new Map(all.value.map((place) => [place.id, place]));
    const previousCount = unique.size;
    for (const place of result.restaurants) unique.set(place.id, place);
    all.value = [...unique.values()];
    page.value = nextPage;
    hasMore.value = result.hasMore && unique.size > previousCount;
    selected.value ??= filtered.value[0];
  } catch (problem) {
    if (id === requestId) error.value = (problem as Error).message;
  } finally {
    if (id === requestId) loading.value = false;
  }
}
watch(
  () => app.profile.campus,
  () => {
    requestId++;
    clearTimeout(timer);
    reset();
    void load();
  },
);
watch([query, radius], () => {
  requestId++;
  clearTimeout(timer);
  reset();
  loading.value = true;
  error.value = "";
  timer = setTimeout(() => void load(), 350);
});
onMounted(() => void load());
onBeforeUnmount(() => {
  requestId++;
  clearTimeout(timer);
});
const filtered = computed(() =>
  all.value
    .filter(
      (place) => category.value === "全部" || place.category === category.value,
    )
    .sort((a, b) => {
      if (sort.value === "rating") return highestRatedFirst(a, b);
      if (sort.value === "price")
        return (a.price ?? Infinity) - (b.price ?? Infinity);
      return (a.distance ?? Infinity) - (b.distance ?? Infinity);
    }),
);
function select(r: Restaurant) {
  selected.value = r;
  detail.value = r;
}
function navigate(r: Restaurant) {
  selected.value = r;
  navigation.value = true;
}
const mapUrl = computed(() =>
  selected.value ? walkingUrl(selected.value) : "",
);
</script>
<template>
  <PageHeader
    title="校园美食地图"
    :description="`按店名或菜名找${app.profile.campus}附近的店。`"
  />
  <form class="panel filter-panel" @submit.prevent="load()">
    <div class="filter-row">
      <label class="search-label"
        ><Icon name="Search" :size="18" /><input
          v-model="query"
          maxlength="60"
          placeholder="搜索店名或菜名，如麦当劳"
          aria-label="搜索商家"
      /></label>
      <button class="primary compact" type="submit">搜索</button>
    </div>
    <div class="filter-row">
      <select
        v-if="!isDemo"
        v-model.number="radius"
        aria-label="校园周边搜索半径"
      >
        <option :value="2000">校园 2 公里</option>
        <option :value="5000">校园 5 公里</option>
        <option :value="10000">校园 10 公里</option>
      </select>
      <div class="chips">
        <button
          v-for="c in isDemo
            ? ['全部', '轻食', '热食', '均衡']
            : ['全部', '餐饮']"
          :key="c"
          type="button"
          :class="{ active: category === c }"
          @click="category = c"
        >
          {{ c }}
        </button>
      </div>
      <select v-model="sort" aria-label="商家排序">
        <option value="rating">评分优先</option>
        <option value="distance">距离优先</option>
        <option value="price">价格优先</option>
      </select>
    </div>
  </form>
  <p v-if="error" role="alert" class="notice error">
    {{ error }} <button class="text-btn" @click="load(page > 0)">重试</button>
  </p>
  <div class="map-layout">
    <AmapView
      v-if="!isDemo"
      :restaurants="filtered"
      :selected="selected"
      @select="selected = $event"
    />
    <section v-else class="campus-map" aria-label="虚构校园示意图，非真实地图">
      <div class="map-grid"></div>
      <div class="map-river"></div>
      <div class="map-road road-one"></div>
      <div class="map-road road-two"></div>
      <div class="map-building b1">图书馆</div>
      <div class="map-building b2">教学楼</div>
      <div class="map-building b3">学生宿舍</div>
      <div class="map-park">🌳<br />中心草坪</div>
      <span class="map-caption">青禾大学 · 虚构校园示意图</span
      ><button
        v-for="r in filtered"
        :key="r.id"
        class="map-pin"
        :class="{ active: selected?.id === r.id }"
        :style="{ left: r.x + '%', top: r.y + '%' }"
        :aria-label="r.name"
        @click="selected = r"
      >
        <span>{{ r.emoji }}</span
        ><small>{{ r.name.split(" · ")[1] || r.name }}</small>
      </button>
      <div class="map-legend">
        <Icon name="Info" :size="16" />非高德地图 · 未获取定位 ·
        点位仅用于界面演示
      </div>
    </section>
    <div class="restaurant-list">
      <p v-if="loading" class="notice">正在载入商家…</p>
      <p v-else-if="!error && !filtered.length" class="empty-state panel">
        {{
          isDemo
            ? "演示商家中没有匹配结果。"
            : "当前范围内没有匹配的商家。您可以扩大搜索半径，或试试店名的另一种写法。"
        }}
      </p>
      <RestaurantCard
        v-for="r in filtered"
        :key="r.id"
        :restaurant="r"
        :selected="selected?.id === r.id"
        @select="select"
        @navigate="navigate"
      />
    </div>
    <div v-if="filtered.length || hasMore" class="map-results-footer">
      <p class="source-note">
        已显示 {{ filtered.length }} 家{{ isDemo ? "演示商家" : "高德商家"
        }}<span v-if="hasMore && sort === 'rating'">
          · 按已加载商家评分排序</span
        >
      </p>
      <button
        v-if="hasMore"
        type="button"
        class="secondary"
        :disabled="loading"
        @click="load(true)"
      >
        加载更多商家
      </button>
    </div>
  </div>
  <RestaurantDetails
    v-if="detail"
    :key="detail.id"
    :restaurant="detail"
    @close="detail = undefined"
  />
  <div
    v-if="navigation && selected"
    class="modal-backdrop"
    @click.self="navigation = false"
  >
    <section
      class="modal panel"
      role="dialog"
      aria-modal="true"
      aria-labelledby="nav-title"
    >
      <div class="between">
        <h2 id="nav-title">前往 {{ selected.name }}</h2>
        <button
          class="icon-btn"
          aria-label="关闭导航"
          @click="navigation = false"
        >
          <Icon name="X" />
        </button>
      </div>
      <p v-if="!mapUrl">
        此商家是演示数据或缺少有效坐标，无法提供真实路线。接入真实 POI
        后，这里会显示高德步行导航入口。
      </p>
      <template v-else
        ><p>将打开高德导航。当前页面未获取你的位置，起点由高德确认。</p>
        <a
          :href="mapUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="primary"
          >去导航</a
        ></template
      ><button class="secondary" @click="navigation = false">知道了</button>
    </section>
  </div>
</template>

<style scoped>
.map-results-footer {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
}

.map-results-tagline {
  flex: 0 0 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(8px, 2vw, 18px);
  margin-bottom: 8px;
  color: var(--green);
  font-size: clamp(20px, 3vw, 28px);
  font-weight: 750;
  letter-spacing: 0.06em;
  line-height: 1.4;
  text-align: center;
}

.map-results-tagline::before,
.map-results-tagline::after {
  content: "";
  flex: 0 1 36px;
  height: 1px;
  background: #c8ae75;
}
</style>
