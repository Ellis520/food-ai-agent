<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { useApp } from "../stores/app";
import { foods } from "../api/mock";
import Icon from "../components/Icon.vue";
import FoodArt from "../components/FoodArt.vue";
import FoodCard from "../components/FoodCard.vue";
import DailyPlan from "../components/DailyPlan.vue";
const app = useApp(),
  router = useRouter();
const date = computed(() =>
  new Date().toLocaleDateString("zh-CN", {
    month: "long",
    day: "numeric",
    weekday: "long",
  }),
);
const shortcuts = [
  {
    name: "饮食规划",
    desc: "把三餐安排得明明白白",
    icon: "CalendarDays",
    path: "/plan",
    color: "sage",
  },
  {
    name: "记录这一餐",
    desc: "记下今天的小小满足",
    icon: "NotebookPen",
    path: "/records",
    color: "peach",
  },
  {
    name: "校园美食地图",
    desc: "发现校园里的好味道",
    icon: "MapPinned",
    path: "/map",
    color: "yellow",
  },
  {
    name: "营养分析",
    desc: "吃得开心，也吃得均衡",
    icon: "Leaf",
    path: "/nutrition",
    color: "lavender",
  },
];
</script>
<template>
  <div class="home-greeting">
    <div>
      <span class="eyebrow">A FRESH START, EVERY DAY</span>
      <h1>嗨，{{ app.profile.name }} <span class="wave">✳</span></h1>
      <p>今天也要好好吃饭，让生活多一点好滋味。</p>
    </div>
    <span class="date-label"
      ><Icon name="CalendarDays" :size="16" />{{ date }}</span
    >
  </div>
  <section class="hero">
    <div class="hero-copy">
      <span class="hero-kicker"><span></span> 你的校园饮食好搭子</span>
      <h2>每一餐的小事，<br />都值得<span>用心一点。</span></h2>
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
      <FoodArt emoji="🥗" />
      <div class="orbit-label bottom">
        <span class="status-dot"></span> 今日份的元气，已就位
      </div>
      <span class="hero-spark">✧</span>
    </div>
  </section>
  <div class="section-heading">
    <h2>今日饮食小报 <span>TODAY AT A GLANCE</span></h2>
    <RouterLink to="/records" class="text-btn"
      >查看记录 <Icon name="ArrowRight" :size="15"
    /></RouterLink>
  </div>
  <section class="stats-grid">
    <article class="stat">
      <span class="stat-icon sage"><Icon name="Flame" /></span>
      <div>
        <p>
          {{ app.caloriesIncomplete ? "今日摄入（已知部分）" : "今日摄入" }}
        </p>
        <strong>{{ app.calories.toLocaleString() }} <small>kcal</small></strong
        ><span class="small muted">参考目标 {{ app.profile.kcal }} kcal</span>
      </div>
      <div class="tiny-progress">
        <i
          :style="{
            height:
              Math.min(100, (app.calories / app.profile.kcal) * 100) + '%',
          }"
        ></i>
      </div>
    </article>
    <article class="stat">
      <span class="stat-icon peach"><Icon name="Utensils" /></span>
      <div>
        <p>已记录餐次</p>
        <strong>{{ app.todayMeals.length }} <small>餐</small></strong
        ><span class="small muted">每一餐，都值得被记住</span>
      </div>
    </article>
    <article class="stat">
      <span class="stat-icon yellow"><Icon name="Wallet" /></span>
      <div>
        <p>今日饮食支出</p>
        <strong
          >¥{{ app.spent.toFixed(0) }}
          <small>/ ¥{{ app.profile.budget }}</small></strong
        ><span class="small muted">{{
          app.spent > app.profile.budget
            ? "已超出日预算"
            : "预算还剩 ¥" + (app.profile.budget - app.spent).toFixed(0)
        }}</span>
      </div>
    </article>
  </section>
  <DailyPlan :date="app.today" />
  <div class="section-heading">
    <h2>你的饮食工具箱 <span>MADE FOR YOUR DAY</span></h2>
    <span class="muted small">好好生活，从这里开始</span>
  </div>
  <section class="shortcut-grid">
    <RouterLink
      v-for="s in shortcuts"
      :key="s.path"
      :to="s.path"
      class="shortcut"
      ><span class="stat-icon" :class="s.color"
        ><Icon :name="s.icon" :size="24"
      /></span>
      <h3>{{ s.name }} <Icon name="ArrowUpRight" :size="16" /></h3>
      <p>{{ s.desc }}</p></RouterLink
    >
  </section>
  <div class="home-bottom">
    <section>
      <div class="section-heading">
        <h2>这一餐，试试这些？</h2>
        <RouterLink to="/plan" class="text-btn"
          >更多灵感 <Icon name="ArrowRight" :size="15"
        /></RouterLink>
      </div>
      <div class="food-grid">
        <FoodCard
          v-for="food in foods.slice(1, 3)"
          :key="food.id"
          :food="food"
          @choose="router.push('/records?food=' + $event)"
        />
      </div>
      <p class="source-note">餐食、价格及营养均为示例数据，非实时推荐。</p>
    </section>
    <aside class="daily-note">
      <span class="eyebrow">A LITTLE REMINDER</span
      ><span class="note-illustration">🍊</span>
      <h2>忙碌的课表里，<br />别忘了给自己留一餐。</h2>
      <p>吃饭不只是补充能量，<br />也是认真对待自己的小仪式。</p>
      <RouterLink to="/plan" class="text-btn"
        >安排我的下一餐 <Icon name="ArrowRight" :size="17" /></RouterLink
      ><span class="note-decoration">✳</span>
    </aside>
  </div>
</template>
