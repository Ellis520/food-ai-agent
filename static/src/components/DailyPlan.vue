<script setup lang="ts">
import { computed } from "vue";
import { useApp } from "../stores/app";
import { foods } from "../api/mock";
const props = defineProps<{ date: string; compact?: boolean }>();
const app = useApp();
const items = computed(() => app.plans[props.date] || []);
function logged(slot: string) {
  return app.meals.find((meal) => meal.planRef === `${props.date}:${slot}`);
}
const completed = computed(
  () => items.value.filter((item) => logged(item.slot)).length,
);
</script>

<template>
  <section
    class="panel daily-plan"
    :class="{ 'is-compact': compact }"
    aria-label="计划打卡"
  >
    <div class="plan-head">
      <h2 v-if="!compact">
        {{ date === app.today ? "今日" : "已保存的" }}计划打卡
      </h2>
      <span v-if="items.length" class="tag"
        >已记录 {{ completed }} / {{ items.length }} 餐</span
      >
    </div>
    <p v-if="!compact" class="small muted">
      按实际用餐确认，计划不会自动计入摄入。修改计划不会改动已经保存的饮食记录。
    </p>
    <div v-if="items.length" class="daily-plan-grid">
      <article
        v-for="item in items"
        :key="item.slot"
        class="daily-plan-meal"
        :aria-label="item.slot + '计划'"
      >
        <span class="slot-name">{{ item.slot }}</span>
        <div class="meal-copy">
          <h3>
            <span class="meal-symbol" aria-hidden="true">{{
              foods.find((food) => food.id === item.foodId)?.emoji || "🍽️"
            }}</span>
            {{
              item.dishName ||
              foods.find((food) => food.id === item.foodId)?.name
            }}
          </h3>
          <p class="small">
            计划 {{ item.grams }}g<span v-if="logged(item.slot)">
              · 已记录 {{ logged(item.slot)?.grams }}g</span
            >
          </p>
        </div>
        <RouterLink
          v-if="logged(item.slot)"
          :to="{ path: '/records', query: { date } }"
          class="text-btn"
          >查看记录</RouterLink
        >
        <RouterLink
          v-else-if="date <= app.today"
          :to="{ path: '/records', query: { planDate: date, slot: item.slot } }"
          class="secondary compact"
          :aria-label="'确认' + item.slot + '用餐'"
          >确认用餐</RouterLink
        >
        <span v-else class="small muted">到用餐当天再记录</span>
      </article>
    </div>
    <div v-else class="plan-start">
      <ol class="empty-meals">
        <li v-for="slot in ['早餐', '午餐', '晚餐']" :key="slot">
          <span class="slot-name">{{ slot }}</span>
          <span>未安排</span>
        </li>
      </ol>
      <RouterLink to="/plan" class="text-btn">去规划三餐</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.daily-plan {
  margin: 24px 0;
}
.daily-plan.is-compact {
  margin: 0;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
}
.plan-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.tag {
  font-size: 13px;
}
.daily-plan > .small {
  margin-top: 8px;
}
.daily-plan-grid,
.empty-meals {
  display: flex;
  flex-direction: column;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}
.empty-meals li,
.daily-plan-meal {
  border: 0;
  border-bottom: 1px solid var(--border);
  border-radius: 0;
  padding: 12px 0;
  display: grid;
  grid-template-columns: 4.5em minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  background: transparent;
}
.empty-meals li {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.slot-name {
  font-family: var(--sans);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
  color: #1a241c;
}
.empty-meals li > span:last-child {
  font-weight: 400;
  color: var(--muted);
}
.meal-symbol {
  font-size: 18px;
  margin-right: 4px;
}
.meal-copy h3 {
  font-size: 15px;
  font-weight: 600;
}
.meal-copy p {
  margin-top: 2px;
}
.daily-plan-meal > a,
.daily-plan-meal > span {
  justify-self: end;
}
.plan-start {
  display: block;
}
.plan-start .text-btn {
  margin-top: 14px;
}
@media (max-width: 720px) {
  .daily-plan-meal,
  .empty-meals li {
    grid-template-columns: 4.2em minmax(0, 1fr);
  }
  .daily-plan-meal > a,
  .daily-plan-meal > span:last-child {
    grid-column: 2;
    justify-self: start;
  }
}
</style>
