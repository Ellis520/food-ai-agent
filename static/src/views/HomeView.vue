<script setup lang="ts">
import { computed } from "vue";
import { useApp } from "../stores/app";
import DailyPlan from "../components/DailyPlan.vue";
const app = useApp();
const date = computed(() =>
  new Date().toLocaleDateString("zh-CN", {
    month: "long",
    day: "numeric",
    weekday: "long",
  }),
);
const shortcuts = [
  { name: "饮食规划", desc: "排出早餐、午餐和晚餐", path: "/plan" },
  { name: "饮食记录", desc: "把吃过的写进今天", path: "/records" },
  { name: "饮食习惯", desc: "看最近记下的餐规律不规律", path: "/habits" },
  { name: "校园美食地图", desc: "找食堂和附近的店", path: "/map" },
  { name: "外卖助手", desc: "按想吃的口味找可点的外卖", path: "/takeaway" },
  { name: "美食圈", desc: "看看同学们在吃什么", path: "/community" },
  { name: "营养分析", desc: "看今天的摄入是否均衡", path: "/nutrition" },
  { name: "饮食小贴士", desc: "问饮食安全和附近的医院药店", path: "/language" },
];
</script>
<template>
  <section class="hero">
    <div class="hero-copy">
      <p class="menu-date">{{ date }}</p>
      <h1>今天的食单</h1>
      <p>{{ app.profile.name }}，先看三餐，再决定要不要记一笔。</p>
      <RouterLink to="/chat" class="primary">和食刻聊聊</RouterLink>
    </div>
    <DailyPlan :date="app.today" compact />
  </section>
  <section class="stats-grid" aria-label="今日饮食">
    <article class="stat">
      <p>
        {{ app.caloriesIncomplete ? "今日摄入（已知部分）" : "今日摄入" }}
      </p>
      <strong>{{ app.calories.toLocaleString() }} <small>kcal</small></strong>
      <span class="small muted">参考目标 {{ app.profile.kcal }} kcal</span>
    </article>
    <article class="stat">
      <p>已记录餐次</p>
      <strong>{{ app.todayMeals.length }} <small>餐</small></strong>
      <span class="small muted">只算已经确认的</span>
    </article>
    <article class="stat">
      <p>今日饮食支出</p>
      <strong
        >¥{{ app.spent.toFixed(0) }}
        <small>/ ¥{{ app.profile.budget }}</small></strong
      >
      <span class="small muted">{{
        app.spent > app.profile.budget
          ? "已超出日预算"
          : "预算还剩 ¥" + (app.profile.budget - app.spent).toFixed(0)
      }}</span>
    </article>
  </section>
  <div class="section-heading">
    <h2>食刻服务</h2>
  </div>
  <section class="shortcut-grid">
    <RouterLink
      v-for="item in shortcuts"
      :key="item.path"
      :to="item.path"
      class="shortcut"
    >
      <h3>{{ item.name }}</h3>
      <p>{{ item.desc }}</p>
    </RouterLink>
  </section>
</template>
