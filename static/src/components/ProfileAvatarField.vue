<script setup lang="ts">
import { ref } from "vue";
import { imagePreview } from "../api/imagePreview";
import Icon from "./Icon.vue";

defineProps<{ nickname: string }>();
const avatar = defineModel<string | null>();
const preparing = defineModel<boolean>("preparing", { required: true });
const input = ref<HTMLInputElement>();
const error = ref("");

async function choosePhoto(event: Event) {
  const field = event.target as HTMLInputElement;
  const photo = field.files?.[0];
  field.value = "";
  if (!photo) return;
  error.value = "";
  preparing.value = true;
  try {
    avatar.value = await imagePreview(photo, 256, 100_000);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    preparing.value = false;
  }
}

function resetAvatar() {
  avatar.value = null;
  error.value = "";
}
</script>

<template>
  <div class="avatar-field">
    <span class="avatar avatar-preview"
      ><img v-if="avatar" :src="avatar" alt="头像预览" /><template v-else
        >食</template
      ></span
    >
    <div class="avatar-controls">
      <strong>{{ nickname.trim() || "您的昵称" }}</strong>
      <div class="actions">
        <button
          type="button"
          class="secondary compact"
          :disabled="preparing"
          @click="input?.click()"
        >
          <Icon name="Image" :size="16" />{{
            preparing ? "正在处理…" : avatar ? "更换头像" : "上传头像"
          }}
        </button>
        <button
          v-if="avatar"
          type="button"
          class="text-btn"
          :disabled="preparing"
          @click="resetAvatar"
        >
          恢复默认
        </button>
      </div>
      <input
        ref="input"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        hidden
        aria-label="选择头像照片"
        @change="choosePhoto"
      />
    </div>
    <p v-if="error" role="alert" class="notice error">{{ error }}</p>
  </div>
</template>

<style scoped>
.avatar-field {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px 16px;
  align-items: center;
  padding: 14px;
  background: #f2f5eb;
  border: 1px solid var(--border);
  border-radius: 12px;
}
.avatar-preview {
  width: 60px;
  height: 60px;
  font-size: 23px;
}
.avatar-controls strong {
  font-size: 15px;
  overflow-wrap: anywhere;
}
.avatar-controls .actions {
  margin-top: 8px;
  flex-wrap: wrap;
  gap: 6px 12px;
}
.avatar-field > .error {
  grid-column: 1/-1;
  margin: 0;
}
@media (max-width: 400px) {
  .avatar-field {
    padding: 12px;
    column-gap: 12px;
  }
  .avatar-preview {
    width: 48px;
    height: 48px;
    font-size: 19px;
  }
}
</style>
