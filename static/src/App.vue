<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { pages } from "./router";
import { useApp } from "./stores/app";
import { isDemo } from "./api";
import Icon from "./components/Icon.vue";
import ProfileSetup from "./components/ProfileSetup.vue";
const route = useRoute(),
  router = useRouter(),
  app = useApp(),
  menu = ref(false);
const openSetup = computed(() => route.query.setup === "1");
function closeSetup() {
  if (!openSetup.value) return;
  const query = { ...route.query };
  delete query.setup;
  void router.replace({ path: route.path, query, hash: route.hash });
}
void app.initialize();
watch(
  () => route.path,
  () => {
    menu.value = false;
    document.title = `${String(route.meta.title || "食刻AI")} · 食刻AI`;
  },
);
</script>
<template>
  <ProfileSetup
    v-if="app.ready && (app.needsProfileSetup || openSetup)"
    :revisiting="openSetup && !app.needsProfileSetup"
    @saved="closeSetup"
    @cancel="closeSetup"
  /><template v-else
    ><a href="#main" class="skip-link">跳转到主要内容</a>
    <div v-if="menu" class="menu-overlay" @click="menu = false"></div>
    <aside class="sidebar" :class="{ open: menu }">
      <RouterLink to="/" class="brand"
        ><span class="brand-mark"><Icon name="Sprout" :size="22" /></span
        ><span>食刻<small>校园食单</small></span></RouterLink
      >
      <nav aria-label="主导航">
        <template v-for="group in ['日常食刻', '探索更多', '我的']" :key="group"
          ><span class="nav-group">{{ group }}</span
          ><RouterLink
            v-for="page in pages.filter((p) => p.group === group)"
            :key="page.path"
            :to="page.path"
            class="nav-link"
            @click="menu = false"
            :class="{ active: route.path === page.path }"
            ><Icon :name="page.icon" /><span>{{ page.name }}</span
            ><span
              v-if="page.path === '/chat'"
              class="mini-ai"
              aria-hidden="true"
              >AI</span
            ></RouterLink
          ></template
        >
      </nav>
      <RouterLink to="/profile" class="sidebar-profile"
        ><span class="avatar"
          ><img
            v-if="app.profile.avatar"
            :src="app.profile.avatar"
            alt=""
          /><template v-else>食</template></span
        ><span
          ><strong>{{ app.profile.name }}</strong
          ><small>个人中心</small></span
        ><Icon name="ChevronRight" :size="16"
      /></RouterLink>
    </aside>
    <div class="app-body">
      <header class="topbar">
        <div class="topbar-left">
          <button
            class="icon-btn mobile-menu"
            aria-label="打开导航"
            @click="menu = !menu"
          >
            <Icon name="Menu" /></button
          ><span class="muted">我的食刻</span
          ><Icon name="ChevronRight" :size="14" /><strong>{{
            route.meta.title
          }}</strong>
        </div>
        <div class="topbar-right">
          <span class="mode-dot"></span
          ><span>{{ isDemo ? "演示模式" : "API 对接模式" }}</span
          ><span class="topbar-divider"></span
          ><RouterLink
            to="/profile"
            class="avatar small-avatar"
            aria-label="个人中心"
            ><img
              v-if="app.profile.avatar"
              :src="app.profile.avatar"
              alt=""
            /><template v-else>食</template></RouterLink
          >
        </div>
      </header>
      <main id="main" tabindex="-1">
        <div class="demo-strip">
          <Icon name="Info" :size="15" /><span>{{
            isDemo
              ? "当前为前端演示：AI 为规则回复，地图与商家为示例，语音尚未接入。"
              : "后端模式：记录保存到 SQLite；AI、地图与语音按配置连接，未配置时明确提示。"
          }}</span
          ><RouterLink to="/profile"
            >服务配置</RouterLink>
          >
        </div>
        <div v-if="app.storageError" role="alert" class="notice error">
          {{ app.storageError }}
          <button class="text-btn" @click="app.sync()">重试保存</button>
        </div>
        <div v-if="!app.ready" class="panel empty-state">
          <h2>
            {{ app.connectionError ? "后端暂时无法连接" : "正在读取你的食刻…" }}
          </h2>
          <p>{{ app.connectionError }}</p>
          <button
            v-if="app.connectionError"
            class="secondary"
            @click="app.initialize()"
          >
            重新连接
          </button>
        </div>
        <RouterView v-else />
      </main>
      <footer>食刻 · 校园饮食</footer>
    </div></template
  ><Transition name="toast"
    ><div v-if="app.toast" role="status" class="toast">
      <Icon name="CircleCheck" :size="19" />{{ app.toast }}
    </div></Transition
  >
</template>
