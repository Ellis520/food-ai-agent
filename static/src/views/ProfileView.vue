<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useApp } from "../stores/app";
import { isDemo } from "../api";
import { community, type Post } from "../api/community";
import PageHeader from "../components/PageHeader.vue";
import Icon from "../components/Icon.vue";
import ProfileFields from "../components/ProfileFields.vue";
import ProfileAvatarField from "../components/ProfileAvatarField.vue";
const app = useApp(),
  draft = ref({ ...app.profile }),
  confirmClear = ref(false);
const saving = ref(false),
  validation = ref("");
const preparingAvatar = ref(false),
  savingAvatar = ref(false),
  avatarSaveError = ref("");
const savedPosts = ref<Post[]>([]);
const savedLoading = ref(false);
const savedError = ref("");

async function loadSaved() {
  if (isDemo) return;
  savedLoading.value = true;
  savedError.value = "";
  try {
    savedPosts.value = (await community.saved()).items;
  } catch (problem) {
    savedError.value = (problem as Error).message;
  } finally {
    savedLoading.value = false;
  }
}

onMounted(() => void loadSaved());
async function saveAvatar(avatar: string | null | undefined) {
  draft.value.avatar = avatar;
  avatarSaveError.value = "";
  savingAvatar.value = true;
  try {
    if (await app.saveProfile({ ...app.profile, avatar }))
      app.notify(avatar ? "头像已保存" : "已恢复默认头像");
    else avatarSaveError.value = app.storageError || "头像暂未保存，请重试。";
  } finally {
    savingAvatar.value = false;
  }
}
async function save() {
  if (saving.value || preparingAvatar.value || savingAvatar.value) return;
  validation.value = "";
  if (!draft.value.name || !draft.value.campus) {
    validation.value = "请填写您的昵称和校园。";
    return;
  }
  saving.value = true;
  try {
    if (await app.saveProfile(draft.value)) {
      avatarSaveError.value = "";
      app.notify("个人资料已更新");
    }
  } finally {
    saving.value = false;
  }
}
function download() {
  const blob = new Blob(
    [
      JSON.stringify(
        {
          profile: app.profile,
          meals: app.meals,
          plans: app.plans,
          favorites: app.favorites,
          messages: app.messages,
          nutritionRecipes: app.nutritionRecipes,
        },
        null,
        2,
      ),
    ],
    { type: "application/json" },
  );
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "foodmoment-local-data.json";
  a.click();
  URL.revokeObjectURL(url);
}
</script>
<template>
  <PageHeader title="个人中心" />
  <div class="profile-layout">
    <form class="panel form-stack" @submit.prevent="save">
      <div class="between">
        <h2>我的基本资料</h2>
        <RouterLink to="/profile?setup=1" class="text-btn"
          >打开填写页</RouterLink
        >
      </div>
      <fieldset
        class="form-stack profile-edit-fields"
        :disabled="saving || savingAvatar"
      >
        <ProfileAvatarField
          :nickname="draft.name"
          :model-value="draft.avatar"
          v-model:preparing="preparingAvatar"
          @update:model-value="saveAvatar"
        />
        <p v-if="savingAvatar" class="source-note" role="status">
          正在保存头像…
        </p>
        <p v-if="avatarSaveError" class="notice error" role="alert">
          {{ avatarSaveError }}
        </p>
        <ProfileFields v-model="draft" show-goal />
      </fieldset>
      <p class="source-note">
        演示模板不会自动排除过敏原。每日热量目标由您自行填写。
      </p>
      <p v-if="validation" role="alert" class="notice error">
        {{ validation }}
      </p>
      <button
        class="primary"
        :disabled="saving || preparingAvatar || savingAvatar"
      >
        {{ saving ? "正在保存…" : "保存资料" }} <Icon name="Check" :size="17" />
      </button>
    </form>
    <aside class="form-stack">
      <section class="panel favorite-panel">
        <h2>我的收藏</h2>
        <div class="favorite-part">
          <div class="favorite-heading">
            <div class="favorite-copy">
              <h3>宝藏店铺</h3>
              <p v-if="!app.favorites.length" class="muted">
                还没有收藏的店铺，去校园地图逛逛吧。
              </p>
              <p v-else class="muted">
                已收藏 {{ app.favorites.length }} 家店铺。
              </p>
            </div>
            <RouterLink
              v-if="app.favorites.length"
              to="/favorites"
              class="secondary revisit-btn"
              >点击重温</RouterLink
            >
          </div>
          <RouterLink to="/map" class="text-btn">去校园地图</RouterLink>
        </div>
        <div class="favorite-part">
          <div class="favorite-heading">
            <div class="favorite-copy">
              <h3>宝藏动态</h3>
              <p v-if="savedLoading" class="source-note" role="status">
                正在读取收藏的动态…
              </p>
              <p v-else-if="savedError" class="notice error" role="alert">
                {{ savedError }}
                <button class="text-btn" type="button" @click="loadSaved">
                  重新加载
                </button>
              </p>
              <p v-else-if="!savedPosts.length" class="muted">
                还没有收藏的动态，去美食圈逛逛吧。
              </p>
              <p v-else class="muted">已收藏 {{ savedPosts.length }} 条动态。</p>
            </div>
            <RouterLink
              v-if="savedPosts.length"
              to="/favorites/posts"
              class="secondary revisit-btn"
              >点击重温</RouterLink
            >
          </div>
          <RouterLink to="/community" class="text-btn">去美食圈</RouterLink>
        </div>
      </section>
      <section class="panel">
        <h2>{{ isDemo ? "本地数据管理" : "当前会话数据管理" }}</h2>
        <p class="muted small">
          下载或清空当前会话的记录、计划、收藏、对话与营养搭配；后端模式同步至数据库。
        </p>
        <div class="actions">
          <button class="secondary" @click="download">导出数据</button
          ><button class="secondary danger" @click="confirmClear = true">
            清空数据
          </button>
        </div>
        <div v-if="confirmClear" class="notice">
          <p>确认清空记录、计划、收藏、聊天和营养搭配？个人偏好将保留。</p>
          <button
            class="secondary danger"
            @click="
              app.clearData();
              confirmClear = false;
            "
          >
            确认清空
          </button>
          <button class="text-btn" @click="confirmClear = false">取消</button>
        </div>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.profile-edit-fields {
  border: 0;
  margin: 0;
  padding: 0;
  min-width: 0;
}
.favorite-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.favorite-part {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
.favorite-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}
.favorite-copy {
  display: flex;
  flex-direction: column;
}
.favorite-copy h3,
.favorite-copy p {
  margin: 0;
  line-height: 1.25;
}
.favorite-heading .revisit-btn.secondary {
  min-width: 168px;
  padding: 12px 28px;
  font-size: 16px;
  line-height: 1.4;
}
.favorite-part + .favorite-part {
  padding-top: 16px;
  border-top: 1px solid #edf0e6;
}
.favorite-part h3 {
  font-size: 16px;
}
.revisit-btn.secondary {
  background: #314d3b;
  border-color: #314d3b;
  color: #fffef8;
}
.revisit-btn.secondary:hover {
  background: #283e30;
  color: #fffef8;
}
.favorite-part .service-row {
  width: 100%;
}
</style>
