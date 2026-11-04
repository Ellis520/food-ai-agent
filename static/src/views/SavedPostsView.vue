<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ChevronLeft } from "lucide-vue-next";
import { isDemo } from "../api";
import {
  community,
  communityCategories,
  communityTime,
  type Post,
} from "../api/community";
import CommunityReactions from "../components/CommunityReactions.vue";
import CommunityReplies from "../components/CommunityReplies.vue";
import Icon from "../components/Icon.vue";
import PageHeader from "../components/PageHeader.vue";

const router = useRouter();
const posts = ref<Post[]>([]);
const loading = ref(true);
const error = ref("");
const openReplies = ref<number | null>(null);

async function load() {
  if (isDemo) {
    posts.value = [];
    loading.value = false;
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    posts.value = (await community.saved()).items;
  } catch (problem) {
    error.value = (problem as Error).message;
  } finally {
    loading.value = false;
  }
}

function drop(id: number) {
  posts.value = posts.value.filter((post) => post.id !== id);
  if (openReplies.value === id) openReplies.value = null;
}

function back() {
  if (window.history.state?.back) router.back();
  else void router.push("/profile");
}

onMounted(() => void load());
</script>

<template>
  <PageHeader title="重温宝藏动态" description="收藏过的动态都在这里。">
    <template #leading>
      <button type="button" class="back-chevron" aria-label="返回上一页" @click="back">
        <ChevronLeft :size="46" :stroke-width="2.25" />
      </button>
    </template>
    <RouterLink to="/profile" class="text-btn">返回个人中心</RouterLink>
  </PageHeader>
  <p v-if="loading" class="notice" role="status">正在读取收藏的动态…</p>
  <p v-else-if="error" class="notice error" role="alert">
    {{ error }}
    <button class="text-btn" type="button" @click="load">重新加载</button>
  </p>
  <p v-else-if="!posts.length" class="empty-state panel">
    还没有收藏的动态。
    <RouterLink to="/community" class="text-btn">去美食圈</RouterLink>
  </p>
  <div v-else class="saved-feed">
    <article v-for="post in posts" :key="post.id" class="panel saved-post">
      <div class="post-top">
        <span class="avatar" aria-hidden="true"
          ><img v-if="post.avatar" :src="post.avatar" alt="" /><template
            v-else
            >{{ Array.from(post.author)[0] }}</template
          ></span
        >
        <div class="post-author">
          <strong>{{ post.author }}</strong
          ><time :datetime="post.createdAt">{{
            communityTime(post.createdAt)
          }}</time>
        </div>
        <span class="post-tag" :class="post.category">{{
          communityCategories.find((option) => option.value === post.category)
            ?.label
        }}</span>
      </div>
      <h2 v-if="post.food">{{ post.food }}</h2>
      <div v-if="post.place || post.meetup" class="post-details">
        <span v-if="post.place"
          ><Icon name="MapPinned" :size="16" /> {{ post.place }}</span
        >
        <span v-if="post.meetup"
          ><Icon name="CalendarDays" :size="16" /> {{ post.meetup }}</span
        >
      </div>
      <p class="post-text">{{ post.text }}</p>
      <div
        v-if="post.imageCount"
        class="post-pictures"
        :class="{ 'single-image': post.imageCount === 1 }"
      >
        <a
          v-for="number in post.imageCount"
          :key="number"
          :href="community.image(post.id, number - 1)"
          target="_blank"
          rel="noopener"
          :aria-label="`查看动态图片 ${number}`"
        >
          <img
            :src="community.image(post.id, number - 1)"
            :alt="`动态图片 ${number}`"
            loading="lazy"
          />
        </a>
      </div>
      <div class="post-actions">
        <CommunityReactions :post="post" @unsaved="drop(post.id)" />
        <button
          class="secondary"
          type="button"
          :aria-expanded="openReplies === post.id"
          @click="openReplies = openReplies === post.id ? null : post.id"
        >
          <Icon name="MessageCircleHeart" :size="17" />
          {{ openReplies === post.id ? "收起留言" : "查看留言" }}
          · {{ post.replyCount }}
        </button>
      </div>
      <CommunityReplies
        v-if="openReplies === post.id"
        :post-id="post.id"
        :buddy="post.category === 'buddy'"
        @change="post.replyCount = Math.max(0, post.replyCount + $event)"
      />
    </article>
  </div>
</template>

<style scoped>
.back-chevron {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  align-self: flex-start;
  width: 50px;
  height: 50px;
  margin-left: -8px;
  border: 0;
  background: transparent;
  color: #1c1c1c;
  padding: 0;
  flex: none;
}
.saved-feed {
  max-width: 760px;
}
.saved-post {
  padding: 20px 24px;
  margin-bottom: 16px;
}
.post-top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.post-top > .avatar {
  flex: none;
  background: #e8efdf;
  color: #385a40;
}
.post-author {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  overflow-wrap: anywhere;
}
.post-author time {
  font-size: 11px;
  color: #657967;
}
.post-tag {
  border-radius: 4px;
  padding: 3px 8px;
  font-size: 13px;
  font-weight: 700;
  border: 1px solid var(--border);
  color: #1a241c;
}
.post-tag.recommend {
  background: #e5f1dc;
  color: #315829;
}
.post-tag.avoid {
  background: #fff0de;
  color: #844819;
}
.post-tag.buddy {
  background: #e8effc;
  color: #304f84;
}
.saved-post h2 {
  font-size: 19px;
  margin: 16px 0 6px;
  overflow-wrap: anywhere;
}
.post-details {
  display: flex;
  align-items: center;
  gap: 10px 18px;
  flex-wrap: wrap;
  margin: 10px 0;
  color: #49674d;
  font-size: 13px;
}
.post-details span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  overflow-wrap: anywhere;
  min-width: 0;
}
.post-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.75;
  margin: 12px 0;
}
.post-pictures {
  display: flex;
  gap: 10px;
  margin: 12px 0;
}
.post-pictures a {
  width: calc((100% - 20px) / 3);
  max-width: 210px;
  aspect-ratio: 1;
  display: block;
}
.post-pictures.single-image a {
  width: min(100%, 380px);
  max-width: none;
  aspect-ratio: 4/3;
}
.post-pictures img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 12px;
  background: #edf0e9;
}
.post-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}
.post-actions .secondary {
  margin-left: auto;
}
</style>
