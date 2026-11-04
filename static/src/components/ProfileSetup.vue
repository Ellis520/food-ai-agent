<script setup lang="ts">
import { ref } from "vue";
import { useApp } from "../stores/app";
import Icon from "./Icon.vue";
import ProfileFields from "./ProfileFields.vue";
import ProfileAvatarField from "./ProfileAvatarField.vue";

const props = defineProps<{ revisiting: boolean }>();
const emit = defineEmits<{ saved: []; cancel: [] }>();
const app = useApp();
const draft = ref({ ...app.profile });
if (!props.revisiting && draft.value.name === "小食同学") draft.value.name = "";
const saving = ref(false),
  preparingAvatar = ref(false);
const validation = ref("");

async function submit() {
  if (saving.value || preparingAvatar.value) return;
  validation.value = "";
  if (!draft.value.name || !draft.value.campus) {
    validation.value = "请填写您的昵称和校园。";
    return;
  }
  saving.value = true;
  try {
    if (await app.saveProfile(draft.value)) {
      app.notify("资料已保存到个人中心");
      emit("saved");
    }
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <main id="main" class="profile-setup">
    <section class="setup-intro" aria-labelledby="setup-title">
      <div class="setup-brand">
        <span class="brand-mark setup-logo" aria-hidden="true"
          ><Icon name="Sprout" :size="120"
        /></span>
        <h1 id="setup-title">欢迎来到食刻</h1>
      </div>
    </section>
    <form class="panel setup-form" @submit.prevent="submit">
      <fieldset class="form-stack" :disabled="saving">
        <legend>美好的一天有食刻陪伴</legend>
        <ProfileAvatarField
          :nickname="draft.name"
          v-model="draft.avatar"
          v-model:preparing="preparingAvatar"
        />
        <ProfileFields v-model="draft" />
      </fieldset>
      <p class="setup-note">
        之后可在个人中心修改。您的昵称会显示在美食圈动态中，热量目标由您自行填写。
      </p>
      <p
        v-if="validation || app.storageError"
        class="notice error"
        role="alert"
      >
        {{ validation || app.storageError }}
      </p>
      <button
        class="primary"
        type="submit"
        :disabled="saving || preparingAvatar"
      >
        {{ saving ? "正在保存…" : "保存并进入" }}
      </button>
      <button
        v-if="revisiting"
        class="text-btn"
        type="button"
        :disabled="saving"
        @click="emit('cancel')"
      >
        返回
      </button>
    </form>
  </main>
</template>

<style scoped>
.profile-setup {
  width: min(100%, 920px);
  min-height: 100dvh;
  margin: auto;
  padding: clamp(20px, 5vw, 52px);
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.4fr);
  gap: clamp(18px, 4vw, 44px);
  align-items: center;
}
.setup-intro {
  padding: clamp(18px, 3vw, 32px);
  border-radius: 8px;
  background: transparent;
  border-top: 1px solid var(--border);
}
.setup-brand {
  display: inline-grid;
  grid-template-columns: max-content;
  justify-items: stretch;
}
.setup-logo {
  width: 100%;
  height: auto;
  aspect-ratio: 1;
  border-radius: 22%;
  margin-bottom: 16px;
}
.setup-logo :deep(svg) {
  width: 58%;
  height: 58%;
}
.setup-intro h1 {
  font-family: var(--sans);
  font-size: clamp(32px, 4vw, 44px);
  font-weight: 700;
  line-height: 1.25;
  color: var(--ink);
  margin: 0;
  white-space: nowrap;
}
.setup-form {
  display: grid;
  gap: 14px;
  min-width: 0;
}
.setup-form fieldset {
  border: 0;
  padding: 0;
  margin: 0;
  min-width: 0;
}
.setup-form legend {
  font-size: clamp(18px, 3vw, 23px);
  font-weight: 750;
  line-height: 1.4;
  color: #263d30;
  margin-bottom: 16px;
  padding: 0;
}
.setup-form button {
  min-height: 44px;
  justify-content: center;
}
.setup-note {
  font-size: 12px;
  line-height: 1.7;
  color: #526255;
}
@media (max-width: 720px) {
  .profile-setup {
    grid-template-columns: minmax(0, 1fr);
    align-content: start;
  }
  .setup-intro h1 {
    font-size: 32px;
  }
}
</style>
