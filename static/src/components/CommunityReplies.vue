<script setup lang="ts">
import { nextTick, onMounted, ref } from "vue";
import { community, communityTime, type Reply } from "../api/community";

const props = defineProps<{ postId: number; buddy: boolean }>();
const emit = defineEmits<{ change: [amount: number] }>();
const replies = ref<Reply[]>([]);
const cursor = ref<number | null>(null);
const draft = ref("");
const field = ref<HTMLTextAreaElement>();
const loading = ref(false);
const busy = ref(false);
const loaded = ref(false);
const error = ref("");
let submission: { text: string; id: string } | undefined;

async function load(older = false) {
  if (loading.value || busy.value) return;
  error.value = "";
  loading.value = true;
  try {
    const page = await community.replies(
      props.postId,
      older ? cursor.value : null,
    );
    replies.value = older ? [...page.items, ...replies.value] : page.items;
    cursor.value = page.next;
    loaded.value = true;
  } catch (problem) {
    error.value = (problem as Error).message;
  } finally {
    loading.value = false;
  }
}

async function join() {
  if (!draft.value.trim()) draft.value = "我想一起！还有位置吗？";
  await nextTick();
  field.value?.focus();
}

async function send() {
  const text = draft.value.trim();
  if (!text || busy.value || loading.value) return;
  busy.value = true;
  error.value = "";
  try {
    if (submission?.text !== text)
      submission = { text, id: crypto.randomUUID() };
    const reply = await community.reply(props.postId, text, submission.id);
    if (!replies.value.some((item) => item.id === reply.id)) {
      replies.value.push(reply);
      emit("change", 1);
    }
    draft.value = "";
    submission = undefined;
  } catch (problem) {
    error.value = (problem as Error).message;
  } finally {
    busy.value = false;
  }
}

async function remove(reply: Reply) {
  if (busy.value || loading.value || !window.confirm("删除这条留言？")) return;
  busy.value = true;
  error.value = "";
  try {
    await community.removeReply(props.postId, reply.id);
    replies.value = replies.value.filter((item) => item.id !== reply.id);
    emit("change", -1);
  } catch (problem) {
    error.value = (problem as Error).message;
  } finally {
    busy.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <section class="post-replies" aria-label="帖子留言">
    <button
      v-if="cursor"
      type="button"
      class="text-btn"
      :disabled="loading || busy"
      @click="load(true)"
    >
      加载更早的留言
    </button>
    <p v-if="loading" role="status" class="source-note">正在读取留言…</p>
    <p v-if="loaded && !replies.length" class="source-note">
      还没有留言，来聊聊吧。
    </p>
    <ul v-if="replies.length">
      <li v-for="reply in replies" :key="reply.id">
        <div class="reply-meta">
          <strong>{{ reply.author }}</strong
          ><time :datetime="reply.createdAt">{{
            communityTime(reply.createdAt)
          }}</time>
          <button
            v-if="reply.mine"
            class="text-btn"
            :disabled="busy || loading"
            aria-label="删除我的留言"
            @click="remove(reply)"
          >
            删除
          </button>
        </div>
        <p>{{ reply.text }}</p>
      </li>
    </ul>
    <p v-if="error" role="alert" class="notice error">{{ error }}</p>
    <button
      v-if="!loaded && !loading"
      type="button"
      class="secondary"
      @click="load()"
    >
      重试加载留言
    </button>
    <form v-if="loaded" @submit.prevent="send">
      <label
        >公开留言
        <textarea
          ref="field"
          v-model="draft"
          rows="2"
          maxlength="300"
          :readonly="busy"
          placeholder="写条留言，和同学聊聊…"
        />
      </label>
      <div class="reply-actions">
        <button
          v-if="buddy"
          type="button"
          class="secondary"
          :disabled="busy"
          @click="join"
        >
          我想一起
        </button>
        <button class="primary" :disabled="busy || loading || !draft.trim()">
          {{ busy ? "处理中…" : "发送留言" }}
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.post-replies {
  border-top: 1px solid var(--border);
  margin-top: 14px;
  padding-top: 12px;
}
.post-replies ul {
  list-style: none;
  margin: 0 0 14px;
  padding: 0;
}
.post-replies li {
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
.post-replies li p {
  margin: 5px 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.reply-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.reply-meta time {
  font-size: 13px;
  color: #657967;
}
.post-replies label {
  display: grid;
  gap: 7px;
  font-size: 13px;
}
.post-replies textarea {
  width: 100%;
  resize: vertical;
}
.reply-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  flex-wrap: wrap;
  margin-top: 10px;
}
</style>
