<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { api, isDemo } from "../api";
import { restaurants } from "../api/mock";
import { walkingUrl } from "../api/restaurantActions";
import type { Restaurant } from "../api/types";
import { useApp } from "../stores/app";
import Icon from "../components/Icon.vue";
import PageHeader from "../components/PageHeader.vue";
import RestaurantCard from "../components/RestaurantCard.vue";
import RestaurantDetails from "../components/RestaurantDetails.vue";

const app = useApp();
const router = useRouter();
function goBack() {
  if (window.history.state?.back) router.back();
  else void router.push("/profile");
}
const places = ref<Restaurant[]>([]);
const missing = ref(0);
const loading = ref(true);
const error = ref("");
const detail = ref<Restaurant>();
const navigation = ref<Restaurant>();

async function load() {
  loading.value = true;
  error.value = "";
  const ids = app.favorites;
  if (!ids.length) {
    places.value = [];
    missing.value = 0;
    loading.value = false;
    return;
  }
  if (isDemo) {
    const known = new Map(restaurants.map((place) => [place.id, place]));
    places.value = ids.flatMap((id) => {
      const place = known.get(id);
      return place ? [place] : [];
    });
    missing.value = ids.length - places.value.length;
    loading.value = false;
    return;
  }
  try {
    const result = await api.favoriteRestaurants();
    places.value = result.restaurants.filter((place) =>
      app.favorites.includes(place.id),
    );
    missing.value = result.missing;
  } catch (problem) {
    error.value = (problem as Error).message;
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load());
watch(
  () => app.favorites.join("\0"),
  () => {
    places.value = places.value.filter((place) =>
      app.favorites.includes(place.id),
    );
  },
);
</script>

<template>
  <PageHeader title="重温美好时刻" description="收藏过的店铺都在这里。">
    <button type="button" class="secondary" @click="goBack">
      返回上一页
    </button>
  </PageHeader>
  <p v-if="loading" class="notice" role="status">正在读取收藏的店铺…</p>
  <p v-else-if="error" class="notice error" role="alert">{{ error }}</p>
  <p v-else-if="!places.length" class="empty-state panel">
    还没有可以打开的收藏店铺。
    <RouterLink to="/map" class="text-btn">去校园地图</RouterLink>
  </p>
  <div v-else class="restaurant-list">
    <p v-if="missing" class="notice">
      有 {{ missing }} 家店铺暂时读不到，其余收藏已列出。
    </p>
    <RestaurantCard
      v-for="place in places"
      :key="place.id"
      :restaurant="place"
      @select="detail = $event"
      @navigate="navigation = $event"
    />
  </div>
  <RestaurantDetails
    v-if="detail"
    :key="detail.id"
    :restaurant="detail"
    @close="detail = undefined"
  />
  <div
    v-if="navigation"
    class="modal-backdrop"
    @click.self="navigation = undefined"
  >
    <section
      class="modal panel"
      role="dialog"
      aria-modal="true"
      aria-labelledby="favorite-nav-title"
    >
      <div class="between">
        <h2 id="favorite-nav-title">前往 {{ navigation.name }}</h2>
        <button
          class="icon-btn"
          aria-label="关闭导航"
          @click="navigation = undefined"
        >
          <Icon name="X" />
        </button>
      </div>
      <p v-if="!walkingUrl(navigation)">
        此商家缺少有效坐标，暂时无法打开导航。
      </p>
      <template v-else>
        <p>将打开高德导航。当前页面未获取你的位置，起点由高德确认。</p>
        <a
          :href="walkingUrl(navigation)"
          target="_blank"
          rel="noopener noreferrer"
          class="primary"
          >去导航</a
        >
      </template>
      <button class="secondary" @click="navigation = undefined">知道了</button>
    </section>
  </div>
</template>

<style scoped>
.restaurant-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
