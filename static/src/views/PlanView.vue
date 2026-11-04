<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { useRoute } from "vue-router";
import { useApp, dateKey } from "../stores/app";
import { api, isDemo } from "../api";
import { foods } from "../api/mock";
import type { PlanItem } from "../api/types";
import PageHeader from "../components/PageHeader.vue";
import Icon from "../components/Icon.vue";
import DailyPlan from "../components/DailyPlan.vue";
import PlanShops from "../components/PlanShops.vue";
import PlanPrice from "../components/PlanPrice.vue";
import CategoryNutrition from "../components/CategoryNutrition.vue";
import mealOptions from "../../../shared/meal-options.json";
import PlanPreference from "../components/PlanPreference.vue";
import {
  dietaryRestrictions,
  findDietConflicts,
  hasVegetarianPreference,
  tastes,
  cuisines,
} from "../api/dietPreferences";
const app = useApp(),
  budget = ref(app.profile.budget),
  day = ref(0),
  busy = ref(false),
  saving = ref(false),
  error = ref("");
const savedPreferences = app.profile.preference
  .split(/[、，,；;]+/)
  .map((value) => value.trim())
  .filter((value) => value && value !== "不限");
const taste = ref(
  app.profile.taste ??
    savedPreferences.filter((value) => tastes.includes(value)).join("、"),
);
const cuisine = ref(
  app.profile.cuisine ??
    savedPreferences.filter((value) => cuisines.includes(value)).join("、"),
);
const restrictions = ref(
  [
    hasVegetarianPreference(app.profile.preference) ? "素食" : "",
    app.profile.allergies,
  ]
    .filter((value) => value && value.trim() !== "不限")
    .join("、"),
);
const restrictionOptions = ["素食", "纯素", "奶蛋素", ...dietaryRestrictions];
const preference = computed(() =>
  hasVegetarianPreference(restrictions.value) ? "素食" : "不限",
);
const conflicts = computed(() =>
  findDietConflicts(
    taste.value,
    cuisine.value,
    restrictions.value,
    hasVegetarianPreference(app.profile.preference) ? "素食" : "",
    app.profile.allergies,
  ),
);
const dates = computed(() =>
  Array.from({ length: 7 }, (_, i) => {
    const d = new Date(app.today + "T12:00:00");
    d.setDate(d.getDate() + i);
    return {
      key: dateKey(d),
      label:
        i === 0 ? "今天" : d.toLocaleDateString("zh-CN", { weekday: "short" }),
      date: d.getDate(),
    };
  }),
);
const selected = computed(() => dates.value[day.value].key),
  items = ref<PlanItem[]>(copy(app.plans[app.today] || []));
function copy(plan: PlanItem[], target = budget.value) {
  const weights = plan.map(
    (item) => foods.find((food) => food.id === item.foodId)?.price ?? 12,
  );
  const weightTotal = weights.reduce((sum, weight) => sum + weight, 0);
  let allocated = 0;
  return plan.map((item, index) => {
    const ceiling =
      index === plan.length - 1
        ? target - allocated
        : Math.floor((target * weights[index]) / weightTotal);
    allocated += ceiling;
    return {
      ...item,
      priceMin: item.priceMin ?? Math.round(ceiling * 0.85),
      priceMax: item.priceMax ?? ceiling,
    };
  });
}
const dishesBySlot: Record<string, string[]> = mealOptions;
function setDish(item: PlanItem, name: string) {
  const food = foods.find((food) => food.name === name);
  item.foodId = food?.id ?? "";
  item.dishName = food ? "" : name;
}
function pickDish(item: PlanItem, name: string, event: MouseEvent) {
  const menu = (event.currentTarget as HTMLElement).closest("details");
  setDish(item, name);
  if (menu) {
    menu.open = false;
    menu.parentElement?.querySelector("input")?.focus();
  }
}
function closeDishMenu(event: FocusEvent) {
  const menu = event.currentTarget as HTMLDetailsElement;
  if (!menu.contains(event.relatedTarget as Node | null)) menu.open = false;
}
function setPrice(item: PlanItem, min?: number, max?: number) {
  item.priceMin = min;
  item.priceMax = max;
}
function dish(item: PlanItem) {
  return item.foodId
    ? (foods.find((food) => food.id === item.foodId)?.name ?? "")
    : (item.dishName?.trim() ?? "");
}
function emptyPlan() {
  items.value = copy([
    { slot: "早餐", foodId: "", dishName: "不限", grams: 250 },
    { slot: "午餐", foodId: "", dishName: "不限", grams: 300 },
    { slot: "晚餐", foodId: "", dishName: "不限", grams: 300 },
  ]);
}
const valid = computed(() =>
  items.value.every(
    (item) =>
      dish(item) &&
      Number.isFinite(item.grams) &&
      item.grams >= 10 &&
      item.grams <= 2000 &&
      Number.isFinite(item.priceMin) &&
      Number.isFinite(item.priceMax) &&
      item.priceMin! >= 0 &&
      item.priceMax! <= 1000 &&
      item.priceMin! <= item.priceMax!,
  ),
);
const money = (value: number) => Number(value.toFixed(2));
const minTotal = computed(() =>
  money(
    items.value.reduce((sum, item) => sum + (Number(item.priceMin) || 0), 0),
  ),
);
const maxTotal = computed(() =>
  money(
    items.value.reduce((sum, item) => sum + (Number(item.priceMax) || 0), 0),
  ),
);
const route = useRoute(),
  fromChat = app.messages.find(
    (message) => message.id === route.query.fromChat,
  );
if (fromChat?.planItems && fromChat.planDate) {
  const index = dates.value.findIndex((d) => d.key === fromChat.planDate);
  if (index >= 0) {
    day.value = index;
    items.value = copy(fromChat.planItems);
  } else error.value = "这份聊天计划已不在未来7天内，请重新生成。";
}
watch(
  () => app.today,
  (today) => {
    day.value = 0;
    items.value = copy(app.plans[today] || []);
    error.value = "";
  },
);
function choose(i: number) {
  if (busy.value) return;
  day.value = i;
  items.value = copy(app.plans[selected.value] || []);
}
async function generate() {
  if (conflicts.value.length) return;
  const date = selected.value,
    target = budget.value;
  busy.value = true;
  error.value = "";
  try {
    const result = await api.plan(
      target,
      preference.value,
      day.value,
      taste.value.trim(),
      cuisine.value.trim(),
      restrictions.value.trim(),
    );
    if (date !== selected.value) return;
    items.value = copy(result, target);
    if (isDemo && restrictions.value.trim())
      app.notify("请按填写的限制核对示例餐食配料");
  } catch (problem) {
    if (date === selected.value) error.value = (problem as Error).message;
  } finally {
    busy.value = false;
  }
}
const unsaved = computed(
  () =>
    JSON.stringify(items.value) !==
    JSON.stringify(copy(app.plans[selected.value] || [])),
);
async function save() {
  if (!valid.value || saving.value) return;
  const date = selected.value;
  saving.value = true;
  try {
    app.plans[date] = copy(items.value);
    await app.sync();
    if (!app.storageError) app.notify("已保存 " + date + " 的计划");
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <PageHeader
    title="饮食规划"
    description="按预算和口味，给一天留下早餐、午餐和晚餐。"
  />
  <section class="panel plan-settings" aria-labelledby="plan-settings-title">
    <header>
      <h2 id="plan-settings-title">三餐安排</h2>
    </header>
    <div class="plan-filters">
      <label
        >每日预算（元）<input
          v-model.number="budget"
          :disabled="busy"
          type="number"
          min="20"
          max="200" /></label
      ><PlanPreference
        v-model="taste"
        label="口味"
        :options="tastes"
        :disabled="busy"
        :max-length="60"
        multiple
      /><PlanPreference
        v-model="cuisine"
        label="菜系"
        :options="cuisines"
        :disabled="busy"
        :max-length="60"
        multiple
      /><PlanPreference
        v-model="restrictions"
        label="饮食限制"
        :options="restrictionOptions"
        :disabled="busy"
        multiple
      />
    </div>
    <div v-if="conflicts.length" class="notice error" role="alert">
      <strong>请调整冲突的饮食选项</strong>
      <ul>
        <li v-for="conflict in conflicts" :key="conflict">{{ conflict }}</li>
      </ul>
      <small>也会检查个人中心已保存的素食偏好和禁忌。</small>
    </div>
    <div class="plan-settings-footer">
      <button
        class="primary"
        :disabled="busy || budget < 20 || budget > 200 || conflicts.length > 0"
        @click="generate"
      >
        {{
          busy ? "正在生成…" : isDemo ? "生成示例计划" : "AI 生成饮食计划"
        }}</button
      ><button
        v-if="!items.length"
        class="secondary"
        :disabled="busy"
        @click="emptyPlan"
      >
        自行安排三餐</button
      ><button
        v-if="!items.length && app.plans[selected]?.length"
        class="secondary"
        :disabled="busy"
        @click="choose(day)"
      >
        查看已保存的计划
      </button>
      <p class="muted small">
        {{
          isDemo
            ? "按本地模板选择示例餐食，未调用千问。"
            : "按预算、口味、菜系和饮食限制，从每餐的可选餐食里挑选。"
        }}
        每餐价格区间按每日预算分配，可自行调整。过敏原请以商家确认的配料为准。
      </p>
    </div>
  </section>
  <div class="week-tabs">
    <button
      v-for="(d, i) in dates"
      :key="d.key"
      :class="{ active: day === i }"
      :disabled="busy"
      @click="choose(i)"
    >
      <span>{{ d.label }}</span
      ><strong>{{ d.date }}</strong
      ><small>{{ app.plans[d.key] ? "已保存" : "待安排" }}</small>
    </button>
  </div>
  <p v-if="error" class="notice error" role="alert">{{ error }}</p>
  <div v-if="!items.length" class="empty-state panel plan-welcome">
    <h2>这天还没有三餐</h2>
  </div>
  <section v-else class="plan-grid">
    <article v-for="(item, i) in items" :key="i" class="panel plan-card">
      <h2>{{ item.slot }}</h2>
      <span class="plan-emoji">{{
        foods.find((f) => f.id === item.foodId)?.emoji || "🍽️"
      }}</span>
      <div class="plan-fields">
        <div class="dish-field">
          <label
            >餐食<input
              :value="item.foodId ? dish(item) : item.dishName"
              maxlength="60"
              placeholder="选择或填写菜名"
              @input="setDish(item, ($event.target as HTMLInputElement).value)"
          /></label>
          <details class="dish-menu" @focusout="closeDishMenu">
            <summary aria-label="展开餐食选项">
              <Icon name="ChevronRight" :size="17" />
            </summary>
            <div class="dish-options">
              <button type="button" @click="pickDish(item, '', $event)">
                自行填写
              </button>
              <button
                v-for="name in ['不限', ...(dishesBySlot[item.slot] ?? [])]"
                :key="name"
                type="button"
                @click="pickDish(item, name, $event)"
              >
                {{ name }}
              </button>
            </div>
          </details>
        </div>
        <label
          >份量（g）<input
            type="number"
            min="10"
            max="2000"
            v-model.number="item.grams"
        /></label>
        <PlanPrice
          :min="item.priceMin"
          :max="item.priceMax"
          @change="(min, max) => setPrice(item, min, max)"
        />
      </div>
      <p v-if="item.foodId" class="muted">
        约
        {{
          Math.round(
            ((foods.find((f) => f.id === item.foodId)?.kcal || 0) *
              item.grams) /
              100,
          )
        }}
        kcal · 示例数值
      </p>
      <CategoryNutrition
        v-else
        :slot="item.slot"
        :dish="dish(item)"
        :grams="Number(item.grams)"
      />
      <PlanShops
        v-if="dish(item) !== '不限'"
        :dish="dish(item)"
        :price-min="item.priceMin"
        :price-max="item.priceMax"
      />
    </article>
  </section>
  <div v-if="items.length" class="save-bar panel">
    <div>
      <strong>预计 ¥{{ minTotal }}–{{ maxTotal }} / 天</strong>
      <p class="small muted">
        {{
          maxTotal > budget ? "预计上限超过每日预算" : "预计上限在每日预算内"
        }}
        · 计划不会自动变成饮食记录
      </p>
    </div>
    <button class="primary" :disabled="busy || saving || !valid" @click="save">
      {{ saving ? "正在保存…" : "保存这份计划" }}
      <Icon name="Check" :size="17" />
    </button>
  </div>
  <DailyPlan
    v-if="items.length && !unsaved && !saving && !app.storageError"
    :date="selected"
  />
</template>

<style scoped>
.plan-welcome {
  display: grid;
  place-items: center;
  min-height: clamp(140px, 22vw, 230px);
  background: #f0f4e9;
  border-color: #dce6d3;
}
.plan-welcome h2 {
  margin: 0;
  color: #365342;
  font-size: clamp(20px, 3vw, 29px);
  line-height: 1.5;
  text-align: center;
}
.plan-settings {
  display: grid;
  gap: 16px;
  margin-bottom: 20px;
}
.plan-settings h2 {
  margin: 4px 0 0;
  font-size: 21px;
}
.plan-filters {
  display: grid;
  grid-template-columns: minmax(0, 0.7fr) repeat(2, minmax(0, 1fr)) minmax(
      0,
      1.5fr
    );
  gap: 12px;
  align-items: end;
}
.plan-filters > label {
  display: grid;
  gap: 6px;
  min-width: 0;
}
.plan-filters > label input {
  width: 100%;
  min-height: 44px;
}
.plan-settings-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 18px;
  border-top: 1px solid var(--border);
  padding-top: 14px;
}
.plan-settings-footer button {
  min-height: 44px;
  white-space: nowrap;
}
.plan-settings-footer button svg {
  flex-shrink: 0;
}
.plan-settings-footer p {
  flex: 1 1 240px;
  line-height: 1.65;
}
@media (max-width: 720px) {
  .plan-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .plan-filters > *:last-child {
    grid-column: 1/-1;
  }
  .plan-settings-footer button {
    width: 100%;
    justify-content: center;
  }
}
.plan-fields {
  display: grid;
  grid-template-columns: minmax(180px, 1.5fr) minmax(108px, 0.6fr) minmax(220px, 1fr);
  gap: 12px 16px;
  align-items: end;
  min-width: 0;
}
.plan-fields label {
  display: grid;
  grid-column: auto;
  gap: 6px;
  min-width: 0;
}
.plan-fields :deep(input) {
  width: 100%;
  min-width: 0;
  min-height: 42px;
}
.dish-field {
  position: relative;
  min-width: 0;
}
.dish-field input {
  padding-right: 42px;
}
.dish-menu {
  position: absolute;
  right: 0;
  bottom: 0;
  z-index: 2;
}
.dish-menu summary {
  display: grid;
  place-items: center;
  width: 40px;
  height: 42px;
  cursor: pointer;
  list-style: none;
}
.dish-menu summary::-webkit-details-marker {
  display: none;
}
.dish-menu summary svg {
  transform: rotate(90deg);
}
.dish-options {
  position: absolute;
  right: 0;
  width: max-content;
  min-width: 180px;
  max-width: 75vw;
  max-height: 220px;
  overflow: auto;
  padding: 5px;
  background: #fffefa;
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: 0 8px 24px #253d3026;
}
.dish-options button {
  display: block;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 8px 12px;
  border-radius: 6px;
}
.dish-options button:hover,
.dish-options button:focus-visible {
  background: #e8efdf;
}
.dish-options button:first-child {
  font-weight: 700;
}
@media (max-width: 860px) {
  .plan-fields {
    grid-template-columns: 1fr;
  }
  .dish-field {
    grid-column: 1/-1;
  }
  .plan-fields :deep(input) {
    font-size: 16px;
  }
}
</style>
