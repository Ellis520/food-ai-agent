<script setup lang="ts">
import { computed } from "vue";
import { useApp } from "../stores/app";
import { foods } from "../api/mock";
import Icon from "./Icon.vue";
const props = defineProps<{ date: string }>();
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
  <section class="panel daily-plan" aria-label="计划打卡">
    <div class="between">
      <h2>{{ date === app.today ? "今日" : "已保存的" }}计划打卡</h2>
      <span v-if="items.length" class="tag"
        >已记录 {{ completed }} / {{ items.length }} 餐</span
      >
    </div>
    <p class="small muted">
      按实际用餐确认，计划不会自动计入摄入。修改计划不会改动已经保存的饮食记录。
    </p>
    <div v-if="items.length" class="daily-plan-grid">
      <article
        v-for="item in items"
        :key="item.slot"
        class="daily-plan-meal"
        :aria-label="item.slot + '计划'"
      >
        <span class="meal-symbol" aria-hidden="true">{{
          foods.find((food) => food.id === item.foodId)?.emoji || "🍽️"
        }}</span>
        <div class="meal-copy">
          <span class="small muted">{{ item.slot }}</span>
          <h3>
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
          ><Icon name="CircleCheck" :size="16" /> 查看记录</RouterLink
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
      <p>还没有保存这天的计划，先给三餐留一点期待。</p>
      <RouterLink to="/plan" class="text-btn">去规划三餐 →</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.daily-plan {
  margin: 24px 0;
}
.daily-plan > .between {
  gap: 12px;
  flex-wrap: wrap;
}
.daily-plan > .small {
  margin-top: 8px;
}
.daily-plan-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}
.daily-plan-meal {
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.meal-symbol {
  font-size: 32px;
}
.meal-copy {
  flex: 1;
  min-width: 120px;
}
.meal-copy h3 {
  font-size: 13px;
}
.daily-plan-meal > a {
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.plan-start {
  padding-top: 16px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.plan-start p {
  font-size: 12px;
}
@media (max-width: 1000px) {
  .daily-plan-grid {
    grid-template-columns: 1fr;
  }
  .daily-plan-meal {
    flex-wrap: nowrap;
  }
}
@media (max-width: 600px) {
  .daily-plan-meal {
    padding: 12px;
    gap: 9px;
    flex-wrap: wrap;
  }
  .meal-copy {
    min-width: 140px;
  }
  .daily-plan-meal > a {
    margin-left: auto;
  }
  .daily-plan > .between h2 {
    font-size: 16px;
  }
}
</style>
