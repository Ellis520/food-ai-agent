<script setup lang="ts">
import { computed } from "vue";
import type { ChatMeal } from "../api/types";
import { mealSearchUrl } from "../api/takeawayIdeas";
import Icon from "./Icon.vue";

const props = defineProps<{
  meals?: ChatMeal[];
  selected?: ChatMeal;
  disabled: boolean;
}>();
const choices = computed(() =>
  Array.from(
    new Map(
      props.meals?.map((meal) => [meal.slot + "-" + meal.option, meal]),
    ).values(),
  ),
);
const emit = defineEmits<{
  choose: [meal: ChatMeal];
  findShops: [meal: ChatMeal];
}>();
</script>

<template>
  <section
    v-if="selected"
    class="meal-choice structured-card"
    aria-label="餐食下一步"
  >
    <div class="meal-choice-title">
      <span class="meal-choice-icon"><Icon name="Utensils" :size="19" /></span>
      <div>
        <span class="meal-kicker">{{ selected.slot }}</span>
        <h3>{{ selected.option }}</h3>
      </div>
    </div>
    <p>需要我帮您查找校园附近的店铺吗？您也可以去美团外卖搜索这道餐食。</p>
    <div class="meal-actions">
      <button
        type="button"
        class="primary compact"
        :disabled="disabled"
        @click="emit('findShops', selected)"
      >
        <Icon name="Search" :size="16" /> 找店铺
      </button>
      <a
        class="secondary compact"
        :href="mealSearchUrl(selected.option)"
        target="_blank"
        rel="noopener noreferrer"
        ><Icon name="Bike" :size="16" /> 点外卖</a
      >
    </div>
  </section>
  <section
    v-else-if="choices.length"
    class="meal-recommendations structured-card"
    aria-label="餐食推荐"
  >
    <h3>餐食推荐</h3>
    <ul>
      <li v-for="meal in choices" :key="meal.slot + '-' + meal.option">
        <div class="meal-copy">
          <span class="meal-kicker">{{ meal.slot }}</span>
          <h4>{{ meal.option }}</h4>
          <p v-if="meal.name === meal.option && meal.detail">
            {{ meal.detail }}
          </p>
        </div>
        <button
          type="button"
          class="secondary compact meal-pick"
          :aria-label="'选择' + meal.option"
          :disabled="disabled"
          @click="emit('choose', meal)"
        >
          选这个
        </button>
      </li>
    </ul>
    <slot />
  </section>
</template>

<style scoped>
.meal-recommendations,
.meal-choice {
  width: min(100%, 590px);
  background: var(--sheet);
  border-color: var(--border);
  overflow-wrap: anywhere;
}
.meal-recommendations ul {
  list-style: none;
  padding: 0;
  margin: 10px 0 0;
  display: grid;
  gap: 8px;
}
.meal-recommendations li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 14px 0;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--border);
  border-radius: 0;
}
.meal-copy h4,
.meal-choice-title h3 {
  margin: 2px 0 0;
  font-family: var(--sans);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.45;
  color: #1a241c;
}
.meal-copy p,
.meal-choice p {
  margin: 5px 0 0;
  line-height: 1.65;
  font-size: 14px;
  color: #5c6b60;
}
.meal-kicker {
  font-family: var(--sans);
  font-size: 14px;
  color: var(--chop);
  font-weight: 700;
}
.meal-pick,
.meal-actions > * {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  white-space: nowrap;
  min-height: 36px;
  font-size: 15px;
}
.meal-choice-title,
.meal-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.meal-choice-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 6px;
  background: transparent;
  border: 1px solid var(--border);
  color: #385742;
}
.meal-actions {
  margin-top: 12px;
  flex-wrap: wrap;
}
@media (max-width: 420px) {
  .meal-recommendations li {
    gap: 8px;
    padding: 9px;
  }
  .meal-pick {
    min-height: 42px;
    padding: 7px 8px;
  }
  .meal-actions > * {
    flex: 1;
    min-height: 42px;
  }
}
</style>
