<script setup lang="ts">
import { computed, ref } from "vue";
import { api, isDemo } from "../api";
import {
  campusStoreLinks,
  loadCampusTakeaway,
  type CampusTakeawayEntry,
} from "../api/campusTakeaway";
import { highestRatedFirst } from "../api/restaurantActions";
import { requestedTakeawaySort, takeawayKeywords } from "../api/takeawaySearch";
import type { Restaurant } from "../api/types";
import CampusTakeawayCard from "../components/CampusTakeawayCard.vue";
import Icon from "../components/Icon.vue";
import PageHeader from "../components/PageHeader.vue";

type Sort = "price" | "rating" | "distance";
type Search = { keyword: string; page: number; hasMore: boolean };
const input = ref("");
const budget = ref<number | "">("");
const sort = ref<Sort>("rating");
const busy = ref(false);
const error = ref("");
const submitted = ref("");
const places = ref<Restaurant[]>([]);
const searches = ref<Search[]>([]);
const catalog = ref<CampusTakeawayEntry[]>([]);
const moreAvailable = computed(() =>
  searches.value.some((search) => search.hasMore),
);
const visible = computed(() =>
  places.value
    .filter(
      (place) =>
        budget.value === "" ||
        (place.price != null && place.price <= budget.value),
    )
    .sort((a, b) => {
      if (sort.value === "rating") return highestRatedFirst(a, b);
      if (sort.value === "price")
        return (
          (a.price ?? Infinity) - (b.price ?? Infinity) ||
          highestRatedFirst(a, b)
        );
      return (
        (a.distance ?? Infinity) - (b.distance ?? Infinity) ||
        highestRatedFirst(a, b)
      );
    }),
);

async function search(value = input.value) {
  if (busy.value || !value.trim()) return;
  input.value = value;
  submitted.value = value.trim();
  const amount = submitted.value.match(/(\d+(?:\.\d+)?)\s*(?:元|块)/)?.[1];
  if (amount && Number(amount) > 0) budget.value = Number(amount);
  sort.value = requestedTakeawaySort(submitted.value) ?? sort.value;
  places.value = [];
  searches.value = takeawayKeywords(submitted.value).map((keyword) => ({
    keyword,
    page: 1,
    hasMore: true,
  }));
  await loadMore();
}

async function loadMore() {
  if (busy.value) return;
  const active = searches.value.filter((search) => search.hasMore);
  if (!active.length) return;
  busy.value = true;
  error.value = "";
  try {
    const requests = Promise.allSettled(
      active.map((search) =>
        api.searchRestaurants(search.keyword, 5000, search.page),
      ),
    );
    const catalogRequest =
      catalog.value.length || isDemo
        ? Promise.resolve(catalog.value)
        : loadCampusTakeaway().catch(() => []);
    const [results, links] = await Promise.all([requests, catalogRequest]);
    catalog.value = links;
    const byId = new Map(places.value.map((place) => [place.id, place]));
    for (const [index, result] of results.entries()) {
      if (result.status === "rejected") {
        error.value =
          result.reason instanceof Error
            ? result.reason.message
            : "商家查询失败，请重试。";
        continue;
      }
      const query = active[index];
      query.page++;
      query.hasMore = result.value.hasMore;
      for (const place of result.value.restaurants) byId.set(place.id, place);
    }
    places.value = [...byId.values()];
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <PageHeader
    title="想吃什么，让食刻陪你挑。"
    description="输入想吃的口味、菜名或店名，看看校园附近有哪些选择。"
  />
  <section class="panel takeaway-conversation" aria-label="外卖推荐">
    <div class="takeaway-assistant">
      <span class="brand-mark"><Icon name="Sparkles" /></span>
      <div>
        <strong>食刻外卖助手</strong>
        <p>
          {{
            isDemo ? "演示商家 · 未调用高德" : "高德商家 · 中山大学广州南校区"
          }}
        </p>
      </div>
    </div>
    <form class="takeaway-request" @submit.prevent="search()">
      <label
        >今天想吃什么？<textarea
          v-model="input"
          aria-label="外卖需求"
          rows="2"
          maxlength="600"
          placeholder="例如：想喝珍珠奶茶，20 元以内"
          :disabled="busy"
          required
        ></textarea>
      </label>
      <div class="prompts">
        <button
          v-for="prompt in [
            '想吃炸鸡汉堡',
            '附近的牛肉面',
            '想喝珍珠奶茶',
            '推荐一些快餐',
          ]"
          :key="prompt"
          class="secondary compact"
          type="button"
          :disabled="busy"
          @click="search(prompt)"
        >
          {{ prompt }}
        </button>
      </div>
      <div class="controls">
        <label
          >推荐因子<select v-model="sort" aria-label="推荐因子">
            <option value="rating">评分优先</option>
            <option value="price">价格优先</option>
            <option value="distance">距离优先</option>
          </select></label
        >
        <label
          >人均上限（元）<input
            v-model.number="budget"
            type="number"
            min="1"
            step="any"
            placeholder="不限"
        /></label>
        <button class="primary" :disabled="busy || !input.trim()">
          <Icon name="Search" :size="17" />
          {{ busy ? "正在找商家…" : "给我一些推荐" }}
        </button>
      </div>
    </form>
    <p class="source-note">
      {{
        isDemo
          ? "演示数据仅用于体验筛选和排序。"
          : "人均消费、评分和距校园的距离来自高德。"
      }}设定人均上限后，仅展示有价格数据且符合上限的商家。实际菜单、下单价格和配送范围以外卖平台为准。
    </p>
  </section>
  <p v-if="error" class="notice error" role="alert">
    {{ error }}
    <button class="text-btn" :disabled="busy" @click="loadMore()">
      重试查询
    </button>
  </p>
  <section
    v-if="submitted"
    class="takeaway-results"
    aria-label="外卖商家推荐"
    :aria-busy="busy"
  >
    <div class="section-heading">
      <div>
        <h2>校园周边商家推荐</h2>
        <p class="small muted">
          已查询
          {{
            searches
              .map((search) => search.keyword || "校园附近餐饮")
              .join("、")
          }}
          · 显示 {{ visible.length }} 家
        </p>
      </div>
      <span class="tag">{{ isDemo ? "演示数据" : "高德数据" }}</span>
    </div>
    <div v-if="visible.length" class="takeaway-grid">
      <CampusTakeawayCard
        v-for="place in visible"
        :key="place.id"
        :place="place"
        :links="campusStoreLinks(place.name, catalog)"
      />
    </div>
    <p v-else-if="!busy && !error" class="panel empty-state">
      {{
        places.length
          ? "已加载的商家中暂无符合人均上限的结果，可以清空上限或加载更多。"
          : "暂未找到匹配的商家，可以试试更具体的店名或换个菜名。"
      }}
    </p>
    <div v-if="moreAvailable && !error" class="more">
      <button class="secondary" :disabled="busy" @click="loadMore()">
        加载更多商家
      </button>
    </div>
  </section>
</template>

<style scoped>
.takeaway-conversation {
  max-width: 1080px;
  margin: auto;
}
.takeaway-assistant {
  display: flex;
  align-items: center;
  gap: 12px;
}
.takeaway-assistant p,
.takeaway-request label {
  font-size: 12px;
  color: #526857;
}
.takeaway-request {
  margin: 20px 0 14px;
}
.takeaway-request textarea,
.takeaway-request select,
.takeaway-request input {
  width: 100%;
  margin-top: 5px;
}
.prompts,
.controls {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
  align-items: end;
}
.controls label {
  flex: 1 1 130px;
}
.controls > .primary {
  min-height: 40px;
  justify-content: center;
}
.takeaway-results {
  margin-top: 22px;
}
.takeaway-results .section-heading {
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.takeaway-results .section-heading p {
  margin-top: 4px;
  overflow-wrap: anywhere;
}
.takeaway-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.more {
  text-align: center;
  margin-top: 18px;
}
@media (max-width: 1100px) {
  .takeaway-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .takeaway-grid {
    grid-template-columns: 1fr;
  }
  .controls > .primary {
    width: 100%;
  }
}
</style>
