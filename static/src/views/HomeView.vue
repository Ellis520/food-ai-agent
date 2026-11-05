<script setup lang="ts">
import { computed } from "vue";
import { useApp } from "../stores/app";
import Icon from "../components/Icon.vue";
const app = useApp();
const greetingDate = computed(() => {
  const date = new Date(`${app.today}T12:00:00`);
  const week = "日一二三四五六"[date.getDay()];
  return `${date.getMonth() + 1}月${date.getDate()}日星期${week}`;
});
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
  <header class="home-greeting">
    <div>
      <span class="eyebrow">今日食刻</span>
      <h1>嗨，{{ app.profile.name }}</h1>
      <p class="greeting-line">今天也要好好吃饭，让生活多一点好滋味。</p>
    </div>
    <p class="date-label">
      <Icon name="CalendarDays" :size="16" />
      {{ greetingDate }}
    </p>
  </header>
  <section class="hero">
    <div class="hero-copy">
      <span class="hero-kicker"><span></span>你的校园饮食好搭子</span>
      <h2>每一餐的小事，<br />都值得用心一点。</h2>
      <p>
        不知道今天吃什么？让食刻陪你一起，<br />发现好吃的、适合你的、刚刚好的那一餐。
      </p>
      <RouterLink to="/chat" class="primary"
        >和食刻聊聊 <Icon name="ArrowUpRight" :size="18"
      /></RouterLink>
      <div class="hero-foot">
        <span>✦</span> 从「今天吃什么」开始，照顾好自己
      </div>
    </div>
    <div class="hero-visual">
      <div class="orbit-label top">🥑 新鲜灵感，每天一点</div>
      <span class="brand-mark hero-mark" aria-hidden="true"
        ><Icon name="Sprout" :size="92"
      /></span>
      <div class="orbit-label bottom">
        <span class="status-dot"></span> 今日份的元气，已就位
      </div>
    </div>
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
