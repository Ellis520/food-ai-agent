<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import {
  community,
  communityCategories,
  type Post,
  type PostDraft,
} from "../api/community";
import { useApp } from "../stores/app";
import Icon from "./Icon.vue";
import { imagePreview } from "../api/imagePreview";

const emit = defineEmits<{ published: [post: Post]; close: [] }>();
const app = useApp();
const draft = reactive<PostDraft>({
  category: "share",
  text: "",
  food: "",
  place: "",
  meetup: "",
});
const pictures = ref<string[]>([]);
const busy = ref(false);
const preparing = ref(false);
const error = ref("");
let submission: { key: string; id: string } | undefined;
const isReview = computed(
  () => draft.category === "recommend" || draft.category === "avoid",
);
const valid = computed(
  () =>
    draft.text.trim() &&
    (!isReview.value || draft.food.trim() || draft.place.trim()) &&
    (draft.category !== "buddy" || (draft.place.trim() && draft.meetup.trim())),
);

async function addPictures(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = "";
  if (!files.length) return;
  error.value = "";
  if (files.length + pictures.value.length > 3) {
    error.value = "每条动态最多添加 3 张图片。";
    return;
  }
  preparing.value = true;
  try {
    const previews = [];
    for (const file of files) previews.push(await imagePreview(file));
    pictures.value.push(...previews);
  } catch (problem) {
    error.value =
      problem instanceof Error ? problem.message : "图片处理失败，请换一张。";
  } finally {
    preparing.value = false;
  }
}

async function publish() {
  if (!valid.value || busy.value || preparing.value) return;
  busy.value = true;
  error.value = "";
  try {
    const images = pictures.value.map((picture) => picture.split(",")[1]);
    const key = JSON.stringify([draft, images]);
    if (submission?.key !== key) submission = { key, id: crypto.randomUUID() };
    const post = await community.publish(draft, images, submission.id);
    emit("published", post);
  } catch (problem) {
    error.value = (problem as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <form
    class="panel community-composer"
    aria-label="发布动态"
    @submit.prevent="publish"
  >
    <div class="between">
      <div>
        <h2>分享你的食刻</h2>
        <p class="source-note">
          以「{{ app.profile.name }}」发布 · 同一美食圈的用户可见
        </p>
      </div>
      <button
        type="button"
        class="icon-btn"
        aria-label="关闭发帖"
        :disabled="busy || preparing"
        @click="emit('close')"
      >
        <Icon name="X" />
      </button>
    </div>
    <fieldset :disabled="busy || preparing">
      <label class="compose-field"
        >动态类型
        <select v-model="draft.category">
          <option
            v-for="category in communityCategories"
            :key="category.value"
            :value="category.value"
          >
            {{ category.label }}
          </option>
        </select>
      </label>
      <div class="compose-fields">
        <label class="compose-field"
          >菜名
          <input
            v-model="draft.food"
            maxlength="60"
            placeholder="如叉烧饭（选填）"
        /></label>
        <label class="compose-field"
          >{{ draft.category === "buddy" ? "见面地点" : "店名或地点" }}
          <input
            v-model="draft.place"
            maxlength="80"
            :required="draft.category === 'buddy'"
            :placeholder="
              draft.category === 'buddy' ? '如东区食堂门口' : '如东区食堂二楼'
            "
          />
        </label>
        <label v-if="draft.category === 'buddy'" class="compose-field"
          >约饭时间
          <input
            v-model="draft.meetup"
            maxlength="60"
            placeholder="如 11 月 6 日 12:15"
            required
          />
        </label>
      </div>
      <p v-if="isReview" class="source-note">
        填写菜名或店名，方便大家搜到你的体验。
      </p>
      <label class="compose-field"
        >动态内容
        <textarea
          v-model="draft.text"
          rows="4"
          maxlength="600"
          required
          :placeholder="
            draft.category === 'buddy'
              ? '想吃什么？欢迎什么样的饭搭子？'
              : '写写今天吃了什么、口味如何，或者值得留意的地方。'
          "
        />
      </label>
      <div v-if="pictures.length" class="compose-pictures">
        <div v-for="(picture, index) in pictures" :key="index">
          <img :src="picture" :alt="`待上传图片 ${index + 1}`" />
          <button
            type="button"
            class="icon-btn"
            :aria-label="`移除图片 ${index + 1}`"
            @click="pictures.splice(index, 1)"
          >
            <Icon name="X" :size="16" />
          </button>
        </div>
      </div>
      <div class="compose-footer">
        <label class="picture-picker">
          <Icon name="Image" :size="18" /> 添加图片（{{ pictures.length }}/3）
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            :disabled="pictures.length >= 3"
            @change="addPictures"
          />
        </label>
        <button class="primary" :disabled="!valid">
          <Icon name="Send" :size="17" />{{ busy ? "发布中…" : "发布动态" }}
        </button>
      </div>
    </fieldset>
    <p v-if="preparing" role="status" class="source-note">正在处理图片…</p>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p class="source-note">
      分享亲身体验即可。约饭请用公共地点，留言里请勿填写手机号等私人信息。
    </p>
  </form>
</template>

<style scoped>
.community-composer {
  padding: 22px;
}
.community-composer h2 {
  margin: 0;
}
.community-composer fieldset {
  border: 0;
  padding: 0;
  margin: 14px 0 0;
  min-width: 0;
}
.compose-fields {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.compose-fields > * {
  flex: 1 1 160px;
}
.compose-field {
  display: grid;
  gap: 6px;
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 650;
}
.compose-field textarea,
.compose-field input,
.compose-field select {
  width: 100%;
  font-weight: 400;
}
.compose-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.picture-picker {
  display: flex;
  gap: 7px;
  align-items: center;
  position: relative;
  font-size: 13px;
  color: #325e48;
  cursor: pointer;
  padding: 10px 0;
}
.picture-picker input {
  position: absolute;
  inset: 0;
  opacity: 0;
  width: 100%;
  cursor: pointer;
}
.picture-picker:focus-within {
  outline: 2px solid #c19855;
  outline-offset: 3px;
}
.compose-pictures {
  display: flex;
  gap: 9px;
  margin-bottom: 14px;
}
.compose-pictures div {
  width: min(29%, 110px);
  aspect-ratio: 1;
  position: relative;
}
.compose-pictures img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 10px;
}
.compose-pictures button {
  position: absolute;
  top: 4px;
  right: 4px;
  background: white;
  width: 30px;
  height: 30px;
}
@media (max-width: 520px) {
  .community-composer {
    padding: 16px;
  }
  .compose-fields {
    gap: 0 10px;
  }
  .compose-fields > * {
    flex-basis: 130px;
  }
}
</style>
