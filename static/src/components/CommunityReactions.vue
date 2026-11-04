<script setup lang="ts">
import { ref } from "vue";
import { Heart, Star } from "lucide-vue-next";
import { community, type Post } from "../api/community";

const props = defineProps<{ post: Post }>();
const emit = defineEmits<{ unsaved: [] }>();
const busy = ref(false);
const error = ref("");

async function react(kind: "like" | "save") {
  if (busy.value) return;
  const next = kind === "like" ? !props.post.liked : !props.post.saved;
  const previous = {
    liked: props.post.liked,
    saved: props.post.saved,
    likeCount: props.post.likeCount,
  };
  if (kind === "like") {
    props.post.liked = next;
    props.post.likeCount = Math.max(0, props.post.likeCount + (next ? 1 : -1));
  } else props.post.saved = next;
  busy.value = true;
  error.value = "";
  try {
    const result =
      kind === "like"
        ? await community.like(props.post.id, next)
        : await community.save(props.post.id, next);
    props.post.liked = result.liked;
    props.post.saved = result.saved;
    props.post.likeCount = result.likeCount;
    if (kind === "save" && !result.saved) emit("unsaved");
  } catch (problem) {
    props.post.liked = previous.liked;
    props.post.saved = previous.saved;
    props.post.likeCount = previous.likeCount;
    error.value = (problem as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="reactions">
    <button
      type="button"
      class="react"
      :class="{ on: post.liked }"
      :aria-pressed="post.liked"
      :aria-label="post.liked ? '取消点赞' : '点赞'"
      :disabled="busy"
      @click="react('like')"
    >
      <Heart :size="22" :stroke-width="1.6" :fill="post.liked ? 'currentColor' : 'none'" />
      <span>{{ post.likeCount }}</span>
    </button>
    <button
      type="button"
      class="react star"
      :class="{ on: post.saved }"
      :aria-pressed="post.saved"
      :aria-label="post.saved ? '取消收藏' : '收藏'"
      :disabled="busy"
      @click="react('save')"
    >
      <Star :size="22" :stroke-width="1.6" :fill="post.saved ? 'currentColor' : 'none'" />
    </button>
    <p v-if="error" class="react-error" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.reactions {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.react {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: #243027;
  padding: 2px;
  font-size: 15px;
  font-weight: 500;
  line-height: 1;
}
.react :deep(svg) {
  display: block;
}
.react.on {
  color: #c23b3b;
}
.react.star.on {
  color: #c4962a;
}
.react-error {
  margin: 0;
  color: var(--chop, #9c3b2e);
  font-size: 12px;
}
</style>
