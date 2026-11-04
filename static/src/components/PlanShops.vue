<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { api, isDemo } from "../api";
import {
  campusStoreLinks,
  loadCampusTakeaway,
  type CampusTakeawayEntry,
} from "../api/campusTakeaway";
import { walkingUrl } from "../api/restaurantActions";
import type { Restaurant } from "../api/types";
import Icon from "./Icon.vue";
import TakeawayLinks from "./TakeawayLinks.vue";

const props = defineProps<{
  dish: string;
  priceMin?: number;
  priceMax?: number;
}>();
const shops = ref<Restaurant[]>([]),
  catalog = ref<CampusTakeawayEntry[]>([]);
const visible = ref(0),
  page = ref(0),
  more = ref(true),
  busy = ref(false),
  error = ref(""),
  searched = ref(false);
const order = ref("");
const budget = computed(() => {
  const { priceMin: min, priceMax: max } = props;
  if (
    typeof min !== "number" ||
    typeof max !== "number" ||
    !Number.isFinite(min) ||
    !Number.isFinite(max) ||
    min < 0 ||
    min > max ||
    max > 1000
  )
    return;
  return { min, max };
});
function dishKey(value: string) {
  return value
    .normalize("NFKC")
    .toLowerCase()
    .replace(/[\s\p{P}]+/gu, "");
}
const related = computed(() => {
  const dish = dishKey(props.dish);
  if (!dish) return [];
  return shops.value.flatMap((shop) => {
    const matchedName = dishKey(shop.name).includes(dish);
    if (!matchedName && !dishKey(shop.specialties ?? "").includes(dish))
      return [];
    return [{ shop, matchedName }];
  });
});
const priced = computed(() =>
  related.value.flatMap(({ shop, matchedName }) => {
    const price = shop.price;
    return typeof price === "number" && Number.isFinite(price) && price > 0
      ? [{ shop, matchedName, price }]
      : [];
  }),
);
const matches = computed(() => {
  const range = budget.value;
  if (!range) return [];
  return priced.value
    .filter(({ price }) => price <= range.max * 2)
    .map(({ shop, price, matchedName }) => {
      const gap = Math.max(0, range.min - price, price - range.max);
      return { shop, price, gap, matchedName };
    })
    .sort(
      (a, b) =>
        a.gap - b.gap ||
        (a.shop.distance ?? Infinity) - (b.shop.distance ?? Infinity),
    );
});
const cheapest = computed(() =>
  priced.value.reduce((lowest, item) => Math.min(lowest, item.price), Infinity),
);
const canLoad = computed(
  () => visible.value < matches.value.length || more.value,
);
let generation = 0;
watch(
  () => props.dish,
  () => {
    generation++;
    shops.value = [];
    visible.value = 0;
    page.value = 0;
    more.value = true;
    busy.value = false;
    error.value = "";
    searched.value = false;
    order.value = "";
  },
);
async function load() {
  if (busy.value || !props.dish || !budget.value) return;
  const id = generation;
  busy.value = true;
  error.value = "";
  try {
    const target = Math.min(visible.value, matches.value.length) + 3;
    for (
      let attempts = 0;
      matches.value.length < target && more.value && page.value < 10;
      attempts++
    ) {
      if (attempts >= 3 && matches.value.length > 0) break;
      const result = await api.searchRestaurants(
        props.dish,
        5000,
        page.value + 1,
      );
      if (id !== generation) return;
      const known = new Set(shops.value.map((shop) => shop.id));
      for (const shop of result.restaurants) {
        if (!known.has(shop.id)) {
          shops.value.push(shop);
          known.add(shop.id);
        }
      }
      page.value++;
      more.value = result.hasMore && page.value < 10;
    }
    visible.value = target;
    searched.value = true;
  } catch (problem) {
    if (id === generation) error.value = (problem as Error).message;
  } finally {
    if (id === generation) busy.value = false;
  }
}
async function chooseOrder(shop: Restaurant) {
  order.value = order.value === shop.id ? "" : shop.id;
  if (order.value && !catalog.value.length) {
    try {
      catalog.value = await loadCampusTakeaway();
    } catch {
      /* Search links remain available. */
    }
  }
}
</script>

<template>
  <section class="plan-shops" aria-label="相关店铺">
    <h3>相关店铺</h3>
    <p v-if="isDemo" class="small muted">
      连接高德后可按菜名查询校园附近店铺。
    </p>
    <template v-else>
      <p v-if="budget" class="small muted">
        按店名或高德特色菜匹配。优先展示人均 ¥{{ budget.min }}–{{
          budget.max
        }}
        的店铺，也接受更低价格，最高推荐到 ¥{{
          budget.max * 2
        }}。供应和实际菜价请在平台确认。
      </p>
      <p v-else class="small muted">请先填写有效的每餐预计价格区间。</p>
      <article
        v-for="{ shop, price, gap, matchedName } in matches.slice(0, visible)"
        :key="shop.id"
        class="plan-shop"
      >
        <strong>{{ shop.name }}</strong>
        <p class="small">
          {{ shop.address }} ·
          {{ shop.distance === null ? "距离未提供" : shop.distance + " 米" }} ·
          参考人均 ¥{{ price }}
        </p>
        <span class="tag">{{
          gap
            ? "比区间" +
              (price > budget!.max ? "高" : "低") +
              " ¥" +
              Number(gap.toFixed(2))
            : "符合本餐预算"
        }}</span>
        <p class="small muted">
          {{
            matchedName
              ? "匹配依据：店名包含「" + dish + "」"
              : "匹配依据：高德特色菜包含「" + dish + "」"
          }}
        </p>
        <div class="actions">
          <a
            v-if="walkingUrl(shop)"
            class="secondary compact"
            :href="walkingUrl(shop)"
            target="_blank"
            rel="noopener noreferrer"
            >去导航</a
          >
          <button
            type="button"
            class="secondary compact"
            :aria-expanded="order === shop.id"
            @click="chooseOrder(shop)"
          >
            点外卖
          </button>
        </div>
        <TakeawayLinks
          v-if="order === shop.id"
          :place="shop"
          :links="campusStoreLinks(shop.name, catalog)"
        />
      </article>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p>
      <div
        v-if="searched && budget && !matches.length && !busy && !more && !error"
        class="shop-empty"
        role="status"
      >
        <span class="shop-empty-icon"><Icon name="Search" :size="24" /></span>
        <h4>抱歉，目前没有找到您想要的</h4>
        <p v-if="Number.isFinite(cheapest)">
          相关店铺最低参考人均 ¥{{ cheapest }}，高于本餐推荐上限 ¥{{
            budget.max * 2
          }}。
        </p>
        <p v-if="related.length > priced.length">
          有 {{ related.length - priced.length }} 家相关店铺未提供参考人均。
        </p>
        <p>
          {{
            related.length
              ? "您可以调整价格区间，或换一道菜试试。"
              : "您可以试试更常见的菜名。"
          }}
        </p>
      </div>
      <button
        v-if="canLoad"
        type="button"
        class="secondary compact"
        :disabled="busy || !dish || !budget"
        @click="load"
      >
        {{
          busy
            ? "查找中…"
            : searched
              ? matches.length
                ? "再加载三家"
                : "继续查找"
              : "查找附近店铺"
        }}
      </button>
    </template>
  </section>
</template>

<style scoped>
.plan-shops {
  grid-column: 1/-1;
  display: grid;
  gap: 10px;
  border-top: 1px solid var(--border);
  padding-top: 14px;
  min-width: 0;
}
.plan-shops > button {
  justify-self: start;
}
.plan-shop {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 10px;
  background: #f2f5ec;
  overflow-wrap: anywhere;
}
.plan-shop .actions {
  flex-wrap: wrap;
}
.plan-shop .tag {
  justify-self: start;
}
.shop-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: clamp(20px, 4vw, 34px);
  border: 1px dashed #b7c5ab;
  border-radius: 14px;
  background: #f1f5eb;
  text-align: center;
  color: #35513f;
}
.shop-empty-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #e0ead8;
}
.shop-empty h4 {
  margin: 3px 0;
  font-size: clamp(17px, 3vw, 23px);
  line-height: 1.4;
}
.plan-shops .shop-empty p {
  font-size: 13px;
  line-height: 1.55;
}
</style>
