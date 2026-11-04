<script setup lang="ts">
import { ref, computed, watch, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useApp } from "../stores/app";
import { isDemo } from "../api";
import { foods } from "../api/mock";
import { categoryReference, recordNutrition } from "../api/nutrition";
import type { Meal } from "../api/types";
import PageHeader from "../components/PageHeader.vue";
import Icon from "../components/Icon.vue";
const app = useApp(),
  route = useRoute(),
  router = useRouter();
const date = ref(app.today),
  slot = ref("晚餐"),
  foodId = ref("salad"),
  grams = ref(300),
  cost = ref<number | "">(16),
  dishName = ref("");
const editingId = ref(""),
  planRef = ref<string>(),
  error = ref(""),
  planProblem = ref(false);
const form = ref<HTMLFormElement>();
const records = computed(() =>
  app.meals.filter((meal) => meal.date === date.value),
);
const duplicate = computed(
  () =>
    !!planRef.value &&
    app.meals.some(
      (meal) => meal.planRef === planRef.value && meal.id !== editingId.value,
    ),
);
const sourceChanged = computed(
  () => !!planRef.value && planRef.value !== `${date.value}:${slot.value}`,
);
function validDate(value: string) {
  return (
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    !Number.isNaN(Date.parse(value)) &&
    new Date(value).toISOString().slice(0, 10) === value
  );
}
function loadRoute() {
  editingId.value = "";
  dishName.value = "";
  planRef.value = undefined;
  error.value = "";
  planProblem.value = false;
  const queryDate =
    typeof route.query.date === "string" ? route.query.date : app.today;
  date.value = validDate(queryDate) ? queryDate : app.today;
  slot.value = "晚餐";
  foodId.value = foods.some((food) => food.id === route.query.food)
    ? String(route.query.food)
    : "salad";
  grams.value = 300;
  cost.value = foods.find((food) => food.id === foodId.value)?.price || 0;
  if (route.query.planDate !== undefined) {
    const plannedDate = String(route.query.planDate),
      plannedSlot = String(route.query.slot);
    const meal = app.plans[plannedDate]?.find(
      (item) => item.slot === plannedSlot,
    );
    if (!validDate(plannedDate) || plannedDate > app.today || !meal) {
      error.value = "这份已保存计划不存在或尚未到用餐日期，请返回规划页检查。";
      planProblem.value = true;
      return;
    }
    date.value = plannedDate;
    slot.value = meal.slot;
    foodId.value = meal.foodId;
    grams.value = meal.grams;
    dishName.value = meal.dishName === "不限" ? "" : (meal.dishName ?? "");
    cost.value = foods.find((food) => food.id === meal.foodId)?.price ?? "";
    planRef.value = `${plannedDate}:${meal.slot}`;
  }
}
watch(() => route.fullPath, loadRoute, { immediate: true });
function edit(meal: Meal) {
  editingId.value = meal.id;
  date.value = meal.date;
  slot.value = meal.slot;
  foodId.value = meal.foodId;
  grams.value = meal.grams;
  cost.value = meal.cost;
  dishName.value = meal.dishName ?? "";
  planRef.value = meal.planRef;
  planProblem.value = false;
  error.value = "";
  void nextTick(() => {
    form.value?.scrollIntoView({ behavior: "smooth", block: "start" });
    form.value?.querySelector("input")?.focus({ preventScroll: true });
  });
}
function reset() {
  if (Object.keys(route.query).length) void router.replace("/records");
  else loadRoute();
}
function save() {
  error.value = "";
  if (planProblem.value) return;
  if (!validDate(date.value) || date.value > app.today) {
    error.value = "请选择今天或过去的实际用餐日期。";
    return;
  }
  const name = dishName.value.trim();
  if (
    (foodId.value
      ? !foods.some((food) => food.id === foodId.value)
      : !name || name === "不限" || name.length > 60) ||
    !["早餐", "午餐", "晚餐", "加餐"].includes(slot.value) ||
    !Number.isFinite(grams.value) ||
    grams.value < 1 ||
    grams.value > 2000 ||
    typeof cost.value !== "number" ||
    !Number.isFinite(cost.value) ||
    cost.value < 0 ||
    cost.value > 1000
  ) {
    error.value = "请检查餐食、份量（1–2000g）和实际花费（0–1000元）。";
    return;
  }
  const reference = sourceChanged.value ? undefined : planRef.value;
  if (
    reference &&
    app.meals.some(
      (meal) => meal.planRef === reference && meal.id !== editingId.value,
    )
  ) {
    error.value = "这一餐已经记录过了，请编辑现有记录。";
    return;
  }
  const id = editingId.value || crypto.randomUUID();
  const meal: Meal = {
    id,
    date: date.value,
    slot: slot.value,
    foodId: foodId.value,
    grams: grams.value,
    cost: cost.value,
    ...(!foodId.value ? { dishName: name } : {}),
    ...(reference ? { planRef: reference } : {}),
  };
  const index = app.meals.findIndex((item) => item.id === id);
  if (editingId.value && index < 0) {
    error.value = "原记录已不存在，请取消编辑后重新添加。";
    return;
  }
  if (index >= 0) app.meals.splice(index, 1, meal);
  else app.meals.push(meal);
  app.notify(
    editingId.value
      ? "记录已修改，请留意保存状态"
      : "这一餐已记录，请留意保存状态",
  );
  if (reference) {
    planRef.value = reference;
    editingId.value = "";
  } else {
    editingId.value = "";
    planRef.value = undefined;
  }
}
function calorieLabel(meal: Meal) {
  const entry = recordNutrition(
    meal,
    foods.find((food) => food.id === meal.foodId),
  );
  if (entry.per100.kcal !== null) {
    const kcal = Math.round((entry.per100.kcal * entry.grams) / 100);
    return `约 ${kcal} kcal${entry.source === "estimate" ? "（分类估算）" : ""}`;
  }
  return categoryReference(meal.slot, entry.name)
    ? "仅有整份营养参考"
    : "营养数据未提供";
}
function remove(id: string) {
  app.meals = app.meals.filter((meal) => meal.id !== id);
  if (editingId.value === id) reset();
  app.notify("记录已删除，可重新确认这餐计划");
}
</script>

<template>
  <PageHeader
    title="记录一餐，也记录生活。"
    description="不用追求完美，从认真记下今天吃了什么开始。"
  />
  <section class="records-layout">
    <form ref="form" class="panel form-stack" @submit.prevent="save">
      <h2>
        {{
          editingId
            ? "修改这一餐"
            : planRef
              ? "确认实际用餐"
              : "这一餐，吃了什么？"
        }}
      </h2>
      <p v-if="planRef" class="notice small">
        已带入保存的计划。请核对实际吃的食物、份量与花费；示例价格可修改。修改日期或餐次会解除计划关联。
      </p>
      <p v-if="sourceChanged" class="notice small">
        本次保存将作为独立记录，不会标记原计划已用餐。
      </p>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p>
      <p v-if="duplicate && !sourceChanged" class="notice" role="status">
        这一餐已经记录过了，可在记录列表中编辑，避免重复计算。
      </p>
      <label
        >日期<input v-model="date" type="date" :max="app.today" required
      /></label>
      <label
        >餐次<select v-model="slot" aria-label="餐次">
          <option>早餐</option>
          <option>午餐</option>
          <option>晚餐</option>
          <option>加餐</option>
        </select></label
      >
      <label
        >餐食<select
          v-model="foodId"
          aria-label="餐食"
          @change="cost = foods.find((food) => food.id === foodId)?.price ?? ''"
        >
          <option value="">自行填写菜名</option>
          <option v-for="food in foods" :key="food.id" :value="food.id">
            {{ food.name }}
          </option>
        </select></label
      >
      <label v-if="!foodId"
        >实际菜名<input
          v-model.trim="dishName"
          maxlength="60"
          required
          placeholder="填写实际吃的餐食"
      /></label>
      <div class="two-col">
        <label
          >食用份量（g）<input
            v-model.number="grams"
            type="number"
            min="1"
            max="2000"
            required /></label
        ><label
          >花费（元）<input
            v-model.number="cost"
            type="number"
            min="0"
            max="1000"
            step="0.1"
            required
        /></label>
      </div>
      <button
        class="primary"
        :disabled="planProblem || (duplicate && !sourceChanged)"
      >
        <Icon name="Plus" :size="18" />{{
          editingId ? "保存修改" : "记下这一餐"
        }}
      </button>
      <button
        v-if="editingId || planRef || planProblem"
        type="button"
        class="text-btn"
        @click="reset"
      >
        {{ editingId ? "取消编辑" : "改为手动记录" }}
      </button>
      <p class="source-note">
        营养数据使用目录示例或按份量换算的分类估算。{{
          isDemo
            ? "初始两条记录为演示记录，数据保存在当前浏览器。"
            : "仅统计你确认的记录，数据保存至 SQLite。"
        }}
      </p>
    </form>
    <section class="panel">
      <div class="section-heading">
        <h2>{{ date }} 的小满足</h2>
        <span class="tag">{{ records.length }} 条记录</span>
      </div>
      <div v-if="!records.length" class="empty-state">
        <span>🍽️</span>
        <h3>这天还没有记录</h3>
        <p>记下实际吃过的一餐吧。</p>
      </div>
      <article
        v-for="record in records"
        :key="record.id"
        class="record-row"
        :class="{ editing: editingId === record.id }"
      >
        <span class="restaurant-emoji">{{
          foods.find((food) => food.id === record.foodId)?.emoji || "🍽️"
        }}</span>
        <div class="record-detail">
          <span class="tag"
            >{{ record.slot }}{{ record.planRef ? " · 计划打卡" : "" }}</span
          >
          <h3>
            {{
              record.dishName ||
              foods.find((food) => food.id === record.foodId)?.name
            }}
          </h3>
          <p>
            {{ record.grams }}g · {{ calorieLabel(record) }} · ¥{{
              record.cost
            }}
          </p>
        </div>
        <div class="record-actions">
          <button
            type="button"
            class="text-btn"
            aria-label="编辑记录"
            @click="edit(record)"
          >
            编辑</button
          ><button
            type="button"
            class="icon-btn"
            aria-label="删除记录"
            @click="remove(record.id)"
          >
            <Icon name="Trash2" :size="18" />
          </button>
        </div>
      </article>
    </section>
  </section>
</template>

<style scoped>
.record-detail {
  flex: 1;
  min-width: 0;
}
.record-actions {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-left: auto;
}
.record-actions .text-btn {
  font-size: 11px;
}
.record-row.editing {
  background: #f0f5ea;
  border-radius: 10px;
}
.record-row {
  flex-wrap: wrap;
}
.record-detail h3 {
  overflow-wrap: anywhere;
}
@media (max-width: 600px) {
  .record-actions {
    gap: 0;
  }
  .record-actions .icon-btn {
    padding: 7px;
  }
  .record-detail {
    min-width: 120px;
  }
}
</style>
