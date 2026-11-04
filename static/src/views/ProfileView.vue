<script setup lang="ts">
import { ref } from "vue";
import { useApp } from "../stores/app";
import { isDemo } from "../api";
import { restaurants } from "../api/mock";
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
  <PageHeader
    title="每个人，都有自己的食刻。"
    description="让偏好被记住，让日常更合心意。"
  />
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
      <section class="panel">
        <h2>服务连接状态</h2>
        <p class="notice">{{ app.syncStatus }}</p>
        <button v-if="!isDemo" class="text-btn" @click="app.refreshServices()">
          刷新配置状态
        </button>
        <div class="service-row">
          <span>运行方式</span
          ><span class="tag">{{ isDemo ? "本地演示" : "API 对接" }}</span>
        </div>
        <div class="service-row">
          <span>千问 / 翻译</span
          ><span>{{
            isDemo
              ? "未连接 · 规则 / 词库"
              : app.services?.qwen
                ? "已配置 · 尚需真实调用验证"
                : "等待千问密钥"
          }}</span>
        </div>
        <div class="service-row">
          <span>高德地图 SDK</span
          ><span>{{
            isDemo
              ? "示意图"
              : app.services?.amap
                ? "POI 接口已配置"
                : "等待高德密钥"
          }}</span>
        </div>
        <div class="service-row">
          <span>FunASR 2-Pass</span
          ><span>{{
            isDemo
              ? "按钮占位"
              : app.services?.asr
                ? "服务地址已配置"
                : "等待服务地址"
          }}</span>
        </div>
        <p class="source-note">
          后端配置在
          backend/.env，修改后需重启。已配置仅代表存在配置值，不代表鉴权或额度验证成功。后端模式使用匿名浏览器会话保存至
          SQLite，无账号登录及跨设备同步。
        </p>
      </section>
      <section class="panel">
        <h2>我的收藏</h2>
        <p v-if="!app.favorites.length" class="muted">
          还没有收藏，去校园地图逛逛吧。
        </p>
        <div v-for="id in app.favorites" :key="id" class="service-row">
          <span>{{ restaurants.find((r) => r.id === id)?.name || id }}</span
          ><button class="text-btn" @click="app.toggleFavorite(id)">
            移除
          </button>
        </div>
        <RouterLink to="/map" class="text-btn">发现校园好味 →</RouterLink>
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
</style>
