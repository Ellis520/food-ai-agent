<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { isDemo } from "../api";
import {
  community,
  communityCategories,
  communityTime,
  type Post,
} from "../api/community";
import {
  campusStoreLinks,
  loadCampusTakeaway,
  type CampusTakeawayEntry,
} from "../api/campusTakeaway";
import { walkingUrl } from "../api/restaurantActions";
import type { Restaurant } from "../api/types";
import CommunityReplies from "./CommunityReplies.vue";
import Icon from "./Icon.vue";
import RestaurantPhoto from "./RestaurantPhoto.vue";
import TakeawayLinks from "./TakeawayLinks.vue";

const props = defineProps<{ restaurant: Restaurant }>();
defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();
const closeButton = ref<HTMLButtonElement>();
const showTakeaway = ref(false);
const catalog = ref<CampusTakeawayEntry[]>([]);
const links = computed(() =>
  campusStoreLinks(props.restaurant.name, catalog.value),
);
const mapUrl = computed(() => walkingUrl(props.restaurant));
const distance = computed(() => {
  const meters = props.restaurant.distance;
  if (meters === null) return "未提供";
  return meters >= 1000 ? (meters / 1000).toFixed(1) + " 公里" : meters + " 米";
});
const posts = ref<Post[]>([]);
const cursor = ref<number | null>(null);
const loading = ref(false);
const error = ref("");
const openReplies = ref<number | null>(null);
const canReadReviews = !isDemo && props.restaurant.source === "amap";

async function loadReviews() {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    const result = await community.storePosts(
      props.restaurant.name,
      cursor.value,
    );
    posts.value.push(...result.items);
    cursor.value = result.next;
  } catch (problem) {
    error.value = (problem as Error).message;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  dialog.value?.showModal();
  closeButton.value?.focus();
  if (canReadReviews) void loadReviews();
  if (props.restaurant.source === "amap") {
    void loadCampusTakeaway()
      .then((entries) => {
        catalog.value = entries;
      })
      .catch(() => {});
  }
});
</script>

<template>
  <dialog
    ref="dialog"
    class="store-details"
    aria-labelledby="store-title"
    @close="$emit('close')"
    @click.self="dialog?.close()"
  >
    <div class="store-content">
      <header class="store-heading">
        <div>
          <span class="tag">{{
            restaurant.source === "amap" ? "高德商家" : "演示商家"
          }}</span>
          <h2 id="store-title">{{ restaurant.name }}</h2>
        </div>
        <button
          ref="closeButton"
          class="icon-btn store-close"
          type="button"
          aria-label="关闭店铺详情"
          @click="dialog?.close()"
        >
          <Icon name="X" />
        </button>
      </header>

      <RestaurantPhoto :restaurant="restaurant" />
      <dl class="store-metrics">
        <div>
          <dt>{{ restaurant.source === "amap" ? "高德评分" : "示例评分" }}</dt>
          <dd>{{ restaurant.rating ?? "未提供" }}</dd>
        </div>
        <div>
          <dt>参考人均</dt>
          <dd>
            {{ restaurant.price === null ? "未提供" : "¥" + restaurant.price }}
          </dd>
        </div>
        <div>
          <dt>距搜索中心</dt>
          <dd>{{ distance }}</dd>
        </div>
      </dl>
      <dl class="store-facts">
        <div>
          <dt>地址</dt>
          <dd>{{ restaurant.address }}</dd>
        </div>
        <div>
          <dt>营业时间</dt>
          <dd>{{ restaurant.hours }}</dd>
        </div>
        <div v-if="restaurant.cuisine">
          <dt>餐饮类型</dt>
          <dd>{{ restaurant.cuisine.replace(/;/g, " · ") }}</dd>
        </div>
        <div v-if="restaurant.specialties">
          <dt>特色菜</dt>
          <dd>{{ restaurant.specialties }}</dd>
        </div>
        <div v-if="restaurant.phone">
          <dt>店铺电话</dt>
          <dd>{{ restaurant.phone.replace(/;/g, " / ") }}</dd>
        </div>
      </dl>
      <p v-if="restaurant.source === 'demo'" class="source-note">
        {{ restaurant.reason }}
      </p>
      <div v-else class="store-actions">
        <a
          v-if="mapUrl"
          :href="mapUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="primary"
        >
          <Icon name="Navigation" :size="16" /> 去导航
        </a>
        <p v-else class="source-note">暂无可用的导航坐标。</p>
        <button
          class="secondary"
          type="button"
          :aria-expanded="showTakeaway"
          aria-controls="store-takeaway"
          @click="showTakeaway = !showTakeaway"
        >
          <Icon name="Bike" :size="17" />
          {{ showTakeaway ? "收起外卖选项" : "点外卖" }}
        </button>
      </div>
      <section
        v-if="showTakeaway"
        id="store-takeaway"
        class="store-takeaway"
        aria-label="外卖平台选项"
      >
        <TakeawayLinks :place="restaurant" :links="links" />
      </section>
      <p v-if="restaurant.source === 'amap'" class="source-note store-source">
        店铺资料来自高德，营业信息以商家实际情况为准。
      </p>

      <section class="store-reviews" aria-labelledby="store-reviews-title">
        <h3 id="store-reviews-title">美食圈评价</h3>
        <p v-if="!canReadReviews" class="source-note">
          演示店铺暂无美食圈评价。
        </p>
        <p v-else-if="error" class="notice error" role="alert">
          {{ error }}
          <button class="text-btn" @click="loadReviews">重试</button>
        </p>
        <p v-else-if="loading" class="source-note" role="status">
          正在读取评价…
        </p>
        <p v-else-if="!posts.length" class="source-note">
          还没有匹配到这家店的评价。
        </p>
        <article v-for="post in posts" :key="post.id" class="store-review">
          <header>
            <strong>{{ post.author }}</strong>
            <time :datetime="post.createdAt">{{
              communityTime(post.createdAt)
            }}</time>
            <span class="tag">{{
              communityCategories.find(
                (category) => category.value === post.category,
              )?.label
            }}</span>
          </header>
          <p v-if="post.food" class="review-food">{{ post.food }}</p>
          <p class="review-text">{{ post.text }}</p>
          <div v-if="post.imageCount" class="review-photos">
            <img
              v-for="index in post.imageCount"
              :key="index"
              :src="community.image(post.id, index - 1)"
              :alt="'评价图片 ' + index"
              loading="lazy"
            />
          </div>
          <button
            class="text-btn"
            type="button"
            :aria-expanded="openReplies === post.id"
            @click="openReplies = openReplies === post.id ? null : post.id"
          >
            {{
              openReplies === post.id ? "收起留言" : "留言 · " + post.replyCount
            }}
          </button>
          <CommunityReplies
            v-if="openReplies === post.id"
            :post-id="post.id"
            :buddy="false"
            @change="post.replyCount += $event"
          />
        </article>
        <button
          v-if="cursor && !error"
          class="secondary compact"
          type="button"
          :disabled="loading"
          @click="loadReviews"
        >
          查看更多评价
        </button>
      </section>
    </div>
  </dialog>
</template>

<style scoped>
.store-details {
  width: min(640px, calc(100% - 24px));
  max-height: calc(100dvh - 24px);
  margin: auto;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: 20px;
  background: #fffefa;
  color: var(--ink, #253c2f);
  box-shadow: 0 18px 60px #163b2938;
  overscroll-behavior: contain;
}
.store-details::backdrop {
  background: #142b22a8;
}
.store-content {
  --restaurant-cover-height: clamp(150px, 36vw, 230px);
  padding: clamp(16px, 4vw, 28px);
  overflow-wrap: anywhere;
}
.store-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.store-heading h2 {
  font-size: clamp(21px, 4vw, 29px);
  line-height: 1.35;
  margin: 8px 0 0;
}
.store-close {
  flex-shrink: 0;
  min-width: 44px;
  min-height: 44px;
  justify-content: center;
}
.store-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  background: #edf2e9;
}
dt {
  color: #506651;
  font-size: 13px;
}
dd {
  margin: 0;
  font-size: 15px;
  color: #233d30;
}
.store-metrics dd {
  font-weight: 700;
}
.store-facts {
  display: grid;
  gap: 8px;
  margin: 16px 0;
}
.store-facts div {
  display: grid;
  grid-template-columns: 5em minmax(0, 1fr);
  gap: 8px;
}
.store-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.store-actions a,
.store-actions button {
  min-height: 44px;
}
.store-takeaway {
  padding: 12px;
  margin-top: 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
}
.store-reviews {
  border-top: 1px solid var(--border);
  margin-top: 22px;
  padding-top: 16px;
}
.store-reviews h3 {
  font-size: 19px;
  margin: 0 0 8px;
}
.store-reviews .source-note {
  font-size: 13px;
}
.store-review {
  padding: 14px;
  margin: 12px 0;
  border-radius: 10px;
  background: #f4f6ef;
}
.store-review header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
  font-size: 14px;
}
.store-review time {
  font-size: 12px;
  color: #526857;
}
.review-food {
  margin-top: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #315843;
}
.review-text {
  margin: 6px 0 8px;
  white-space: pre-wrap;
  line-height: 1.65;
  font-size: 14px;
  color: #2d4536;
}
.review-photos {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin: 8px 0;
}
.review-photos img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 6px;
}
.store-source {
  margin-top: 18px;
}
</style>
