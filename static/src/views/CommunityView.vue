<script setup lang="ts">
import { nextTick, onMounted, ref } from "vue";
import { isDemo } from "../api";
import {
  community,
  communityCategories,
  communityTime,
  type Category,
  type Post,
} from "../api/community";
import CommunityComposer from "../components/CommunityComposer.vue";
import CommunityReplies from "../components/CommunityReplies.vue";
import Icon from "../components/Icon.vue";
import PageHeader from "../components/PageHeader.vue";

const composing = ref(false);
const composer = ref<HTMLElement>();
const category = ref<Category | "">("");
const searchDraft = ref("");
const search = ref("");
const posts = ref<Post[]>([]);
const cursor = ref<number | null>(null);
const loading = ref(false);
const error = ref("");
const feedback = ref("");
const openReplies = ref<number | null>(null);
const removing = ref<number | null>(null);
const removeError = ref("");
let requestVersion = 0;

async function openComposer() {
  composing.value = true;
  await nextTick();
  composer.value?.scrollIntoView({ block: "start" });
  composer.value?.querySelector("select")?.focus({ preventScroll: true });
}

async function load(append = false) {
  if (isDemo || (append && (loading.value || !cursor.value))) return;
  const version = ++requestVersion;
  const before = append ? cursor.value : null;
  if (!append) {
    posts.value = [];
    cursor.value = null;
    openReplies.value = null;
  }
  loading.value = true;
  error.value = "";
  try {
    const page = await community.posts(category.value, search.value, before);
    if (version !== requestVersion) return;
    posts.value = append ? [...posts.value, ...page.items] : page.items;
    cursor.value = page.next;
  } catch (problem) {
    if (version === requestVersion) error.value = (problem as Error).message;
  } finally {
    if (version === requestVersion) loading.value = false;
  }
}

function applySearch() {
  search.value = searchDraft.value.trim();
  void load();
}

function chooseCategory(value: Category | "") {
  category.value = value;
  applySearch();
}

function published(post: Post) {
  composing.value = false;
  feedback.value = "动态已发布。";
  category.value = "";
  searchDraft.value = "";
  search.value = "";
  void load();
  posts.value = [post];
}

async function remove(post: Post) {
  if (
    removing.value !== null ||
    !window.confirm("删除这条动态和它的图片、留言？")
  )
    return;
  removing.value = post.id;
  removeError.value = "";
  try {
    await community.remove(post.id);
    posts.value = posts.value.filter((item) => item.id !== post.id);
    feedback.value = "动态已删除。";
  } catch (problem) {
    removeError.value = (problem as Error).message;
  } finally {
    removing.value = null;
  }
}

onMounted(() => void load());
</script>

<template>
  <div class="community-page">
    <PageHeader
      eyebrow="CAMPUS FOOD CLUB"
      title="美食圈"
      description="晒晒今天吃了什么，查查同学怎么说，约个饭搭子一起吃。"
    >
      <button
        v-if="!isDemo"
        class="primary"
        :disabled="composing"
        @click="openComposer"
      >
        <Icon name="Plus" :size="19" /> 写动态
      </button>
    </PageHeader>
    <div v-if="isDemo" class="panel empty-state">
      <Icon name="MessageCircleHeart" :size="30" />
      <h2>连接后端即可使用美食圈</h2>
      <p>
        当前为前端演示。连接同一食刻 AI 服务的用户可以共享动态、图片和留言。
      </p>
    </div>
    <template v-else>
      <div v-if="composing" ref="composer" class="composer-wrap">
        <CommunityComposer @published="published" @close="composing = false" />
      </div>
      <section class="panel community-tools" aria-label="搜索和筛选动态">
        <form class="community-search" @submit.prevent="applySearch">
          <input
            v-model="searchDraft"
            aria-label="搜索菜名、店名或帖子内容"
            placeholder="搜索菜名、店名或帖子内容"
            maxlength="80"
            type="search"
          />
          <button class="primary">
            <Icon name="Search" :size="17" /> 搜索
          </button>
        </form>
        <div class="community-tabs" role="group" aria-label="动态分类">
          <button
            class="chip"
            :aria-pressed="category === ''"
            @click="chooseCategory('')"
          >
            全部动态
          </button>
          <button
            v-for="option in communityCategories"
            :key="option.value"
            class="chip"
            :aria-pressed="category === option.value"
            @click="chooseCategory(option.value)"
          >
            <Icon :name="option.icon" :size="16" /> {{ option.label }}
          </button>
        </div>
      </section>
      <p v-if="feedback" class="community-feedback" role="status">
        {{ feedback }}
      </p>
      <p v-if="search" class="source-note">
        正在查看「{{ search }}」的搜索结果
      </p>
      <div v-if="error" role="alert" class="notice error">
        {{ error }}
        <button
          class="text-btn"
          @click="load(posts.length > 0 && cursor !== null)"
        >
          重新加载
        </button>
      </div>
      <p v-if="removeError" role="alert" class="notice error">
        {{ removeError }}
      </p>
      <p v-if="loading" role="status" class="source-note">正在读取动态…</p>
      <section
        v-if="!posts.length && !loading && !error"
        class="panel empty-state community-empty"
      >
        <Icon name="Utensils" :size="32" />
        <h2>
          {{
            search || category ? "没有找到符合条件的动态" : "美食圈等你来开张"
          }}
        </h2>
        <p>
          {{
            search || category
              ? "试试换一个菜名或店名，或者切换到全部动态。"
              : "分享你的一餐，推荐爱吃的菜，或者发帖约饭。"
          }}
        </p>
        <button
          v-if="!search && !category && !composing"
          class="secondary"
          @click="openComposer"
        >
          发布第一条动态
        </button>
      </section>
      <article
        v-for="post in posts"
        :key="post.id"
        class="panel community-post"
      >
        <div class="post-top">
          <span class="avatar" aria-hidden="true">{{
            Array.from(post.author)[0]
          }}</span>
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
          <button
            class="secondary"
            :aria-expanded="openReplies === post.id"
            @click="openReplies = openReplies === post.id ? null : post.id"
          >
            <Icon name="MessageCircleHeart" :size="17" />
            {{
              openReplies === post.id
                ? "收起留言"
                : post.category === "buddy"
                  ? "留言约饭"
                  : "查看留言"
            }}
            · {{ post.replyCount }}
          </button>
          <button
            v-if="post.mine"
            class="text-btn"
            aria-label="删除我的动态"
            :disabled="removing !== null"
            @click="remove(post)"
          >
            删除
          </button>
        </div>
        <CommunityReplies
          v-if="openReplies === post.id"
          :post-id="post.id"
          :buddy="post.category === 'buddy'"
          @change="post.replyCount = Math.max(0, post.replyCount + $event)"
        />
      </article>
      <button
        v-if="cursor"
        class="secondary more-posts"
        :disabled="loading"
        @click="load(true)"
      >
        加载更多动态
      </button>
    </template>
  </div>
</template>

<style scoped>
.community-page {
  max-width: 1020px;
  margin: auto;
}
.composer-wrap {
  scroll-margin-top: 82px;
  margin-bottom: 18px;
}
.community-tools,
.community-post {
  padding: 20px 24px;
  margin-bottom: 16px;
}
.community-search {
  display: flex;
  gap: 10px;
}
.community-search input {
  flex: 1;
  min-width: 0;
}
.community-search button {
  flex: none;
}
.community-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
}
.community-tabs button {
  display: flex;
  align-items: center;
  gap: 5px;
}
.community-tabs [aria-pressed="true"] {
  background: #285d48;
  color: white;
  border-color: #285d48;
}
.community-feedback {
  font-size: 13px;
  color: #2f6b4b;
}
.community-empty svg {
  color: #5c7c63;
}
.community-empty button {
  margin: auto;
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
  border-radius: 20px;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 650;
  background: #edf1e7;
  color: #435f42;
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
.community-post h2 {
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
.post-details svg {
  flex: none;
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
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}
.post-actions button {
  font-size: 13px;
}
.more-posts {
  margin: 14px auto;
  display: flex;
}
@media (max-width: 520px) {
  .community-tools,
  .community-post {
    padding: 15px;
  }
  .community-tabs {
    gap: 6px;
  }
  .community-tabs button {
    font-size: 12px;
  }
  .community-search button {
    padding-inline: 12px;
  }
}
</style>
