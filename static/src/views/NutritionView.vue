<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { foods } from "../api/mock";
import {
  nutrients,
  sampleEntry,
  summarizeNutrition,
  validateEntry,
  energyToKcal,
  recordNutrition,
  categoryReference,
  nutritionSourceLabel,
  withNutritionEstimate,
  type NutritionAnalysisEntry,
  type NutritionEstimate,
} from "../api/nutrition";
import type {
  NutritionEntry,
  NutritionRecipe,
  NutrientKey,
  NutrientValues,
} from "../api/types";
import { useApp } from "../stores/app";
import { isDemo, request } from "../api";
import PageHeader from "../components/PageHeader.vue";
import NutritionSummary from "../components/NutritionSummary.vue";
import Icon from "../components/Icon.vue";
const app = useApp(),
  tab = ref("搭配计算"),
  source = ref("示例目录"),
  foodId = ref("chicken"),
  sampleGrams = ref(300);
const items = ref<NutritionEntry[]>([]),
  recipeName = ref(""),
  activeId = ref(""),
  error = ref(""),
  pendingDelete = ref("");
const labelName = ref(""),
  labelGrams = ref(100),
  energyUnit = ref<"kcal" | "kJ">("kcal"),
  editingEntry = ref("");
const blankValues = () =>
  Object.fromEntries(nutrients.map((n) => [n.key, ""])) as Record<
    NutrientKey,
    string | number
  >;
const labelValues = ref(blankValues()),
  labelError = ref(""),
  selectedDate = ref(app.today),
  selectedSlot = ref("全部餐次");
const records = computed(() =>
  app.meals.filter(
    (meal) =>
      meal.date === selectedDate.value &&
      (selectedSlot.value === "全部餐次" || meal.slot === selectedSlot.value),
  ),
);
const recordItems = computed(() =>
  records.value.map((meal) =>
    recordNutrition(
      meal,
      foods.find((food) => food.id === meal.foodId),
    ),
  ),
);
const baseItems = computed(() =>
  tab.value === "搭配计算" ? items.value : recordItems.value,
);
const estimateCache = ref(new Map<string, NutritionEstimate>());
const estimateBusy = ref(false);
const attemptedEstimates = ref(new Map<string, Set<NutrientKey>>());
const estimateKey = (name: string) => name.trim().normalize("NFKC");
function unweighedReference(slot: string, name: string) {
  const reference = categoryReference(slot, name);
  return reference && !reference.grams ? reference : undefined;
}
function estimateName(item: NutritionAnalysisEntry, index: number) {
  const name = estimateKey(item.name);
  const meal = tab.value === "记录汇总" ? records.value[index] : undefined;
  const reference = meal && unweighedReference(meal.slot, name);
  return reference ? `${name}（${reference.example}）` : name;
}
const selectedItems = computed(() =>
  baseItems.value.map((item, index) =>
    withNutritionEstimate(
      item,
      estimateCache.value.get(estimateName(item, index)),
    ),
  ),
);
const missingEstimates = computed(() => {
  const missingByName = new Map<string, Set<NutrientKey>>();
  for (const [index, item] of selectedItems.value.entries()) {
    const name = estimateName(item, index);
    if (!name || name === "未知食物") continue;
    const missing = nutrients
      .filter(({ key }) => item.per100[key] === null)
      .map(({ key }) => key);
    if (missing.length)
      missingByName.set(
        name,
        new Set([...(missingByName.get(name) ?? []), ...missing]),
      );
  }
  return Array.from(missingByName, ([name, missing]) => ({
    name,
    missing: [...missing],
  }));
});
const pendingEstimates = computed(() =>
  missingEstimates.value
    .map(({ name, missing }) => ({
      name,
      missing: missing.filter(
        (key) => !attemptedEstimates.value.get(name)?.has(key),
      ),
    }))
    .filter((food) => food.missing.length),
);
function markAttempted(foods: typeof pendingEstimates.value) {
  for (const { name, missing } of foods) {
    attemptedEstimates.value.set(
      name,
      new Set([...(attemptedEstimates.value.get(name) ?? []), ...missing]),
    );
  }
}
async function fillNutrition() {
  const pending = pendingEstimates.value;
  if (estimateBusy.value || !pending.length) return;
  const foods = pending.slice(0, 20);
  estimateBusy.value = true;
  try {
    const estimates = await request<NutritionEstimate[]>(
      "/nutrition/estimate",
      { foods },
    );
    if (!Array.isArray(estimates) || estimates.length !== foods.length)
      throw new Error("千问返回格式异常");
    for (const [index, food] of foods.entries()) {
      const estimate = estimates[index];
      if (estimate && Object.keys(estimate).length) {
        estimateCache.value.set(food.name, {
          ...estimateCache.value.get(food.name),
          ...estimate,
        });
      }
    }
    markAttempted(foods);
  } catch {
    // Pause this batch and its queue after a service failure. Revisiting the page can try again.
    markAttempted(pending);
  } finally {
    estimateBusy.value = false;
  }
}
const calculationError = computed(() => {
  try {
    summarizeNutrition(items.value);
    return "";
  } catch (e) {
    return (e as Error).message;
  }
});
const canEstimate = computed(
  () => !isDemo && (tab.value === "记录汇总" || !calculationError.value),
);
const estimateStatus = computed(() => {
  if (!canEstimate.value || !missingEstimates.value.length) return "";
  if (pendingEstimates.value.length) return "正在用千问估算缺失项…";
  return "部分营养数据暂未补齐，仍显示为未知。";
});
watch(
  [pendingEstimates, estimateBusy, canEstimate],
  ([pending, busy, allowed], _previous, onCleanup) => {
    if (!allowed || busy || !pending.length) return;
    const timer = setTimeout(() => void fillNutrition(), 300);
    onCleanup(() => clearTimeout(timer));
  },
  { immediate: true },
);
const activeRecipe = computed(() =>
  app.nutritionRecipes.find((recipe) => recipe.id === activeId.value),
);
const dirty = computed(
  () =>
    items.value.length > 0 &&
    JSON.stringify({ name: recipeName.value, items: items.value }) !==
      JSON.stringify(
        activeRecipe.value
          ? { name: activeRecipe.value.name, items: activeRecipe.value.items }
          : null,
      ),
);
function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}
function addSample() {
  error.value = "";
  try {
    if (items.value.length >= 20) throw new Error("每份搭配最多20项食物");
    const entry = sampleEntry(
      foods.find((f) => f.id === foodId.value)!,
      sampleGrams.value,
    );
    validateEntry(entry);
    items.value.push(entry);
  } catch (e) {
    error.value = (e as Error).message;
  }
}
function clearLabel() {
  labelName.value = "";
  labelGrams.value = 100;
  labelValues.value = blankValues();
  editingEntry.value = "";
  labelError.value = "";
  energyUnit.value = "kcal";
}
function addLabel() {
  labelError.value = "";
  try {
    if (!editingEntry.value && items.value.length >= 20)
      throw new Error("每份搭配最多20项食物");
    const per100 = Object.fromEntries(
      nutrients.map((n) => [
        n.key,
        labelValues.value[n.key] === ""
          ? null
          : Number(labelValues.value[n.key]),
      ]),
    ) as NutrientValues;
    if (per100.kcal !== null)
      per100.kcal = energyToKcal(per100.kcal, energyUnit.value);
    const entry: NutritionEntry = {
      id: editingEntry.value || crypto.randomUUID(),
      name: labelName.value.trim(),
      grams: labelGrams.value,
      per100,
      source: "label",
    };
    validateEntry(entry);
    if (editingEntry.value)
      items.value = items.value.map((item) =>
        item.id === editingEntry.value ? entry : item,
      );
    else items.value.push(entry);
    clearLabel();
  } catch (e) {
    labelError.value = (e as Error).message;
  }
}
function editLabel(entry: NutritionEntry) {
  source.value = "自填营养表";
  editingEntry.value = entry.id;
  labelName.value = entry.name;
  labelGrams.value = entry.grams;
  energyUnit.value = "kcal";
  labelValues.value = Object.fromEntries(
    nutrients.map((n) => [n.key, entry.per100[n.key] ?? ""]),
  ) as Record<NutrientKey, string | number>;
}
async function save() {
  error.value = "";
  try {
    if (!recipeName.value.trim()) throw new Error("请先填写搭配名称");
    if (!items.value.length) throw new Error("请先添加食物");
    if (calculationError.value) throw new Error(calculationError.value);
    if (!activeId.value && app.nutritionRecipes.length >= 30)
      throw new Error("最多保存30份搭配，请先删除不需要的搭配");
    const recipe: NutritionRecipe = {
      id: activeId.value || crypto.randomUUID(),
      name: recipeName.value.trim(),
      items: clone(items.value),
    };
    app.nutritionRecipes = activeId.value
      ? app.nutritionRecipes.map((saved) =>
          saved.id === activeId.value ? recipe : saved,
        )
      : [...app.nutritionRecipes, recipe];
    activeId.value = recipe.id;
    recipeName.value = recipe.name;
    await app.sync();
    app.notify(
      app.storageError
        ? "搭配保留在当前页面，请查看保存错误"
        : "营养搭配已保存",
    );
  } catch (e) {
    error.value = (e as Error).message;
  }
}
function fresh() {
  if (
    dirty.value &&
    !window.confirm("当前搭配尚未保存，确认新建并放弃本次修改？")
  )
    return;
  items.value = [];
  recipeName.value = "";
  activeId.value = "";
  error.value = "";
  clearLabel();
}
function load(recipe: NutritionRecipe) {
  if (dirty.value && !window.confirm("当前搭配尚未保存，确认载入另一份搭配？"))
    return;
  items.value = clone(recipe.items);
  recipeName.value = recipe.name;
  activeId.value = recipe.id;
  error.value = "";
  tab.value = "搭配计算";
  clearLabel();
}
function removeRecipe(id: string) {
  app.nutritionRecipes = app.nutritionRecipes.filter(
    (recipe) => recipe.id !== id,
  );
  pendingDelete.value = "";
  if (activeId.value === id) {
    activeId.value = "";
    items.value = [];
    recipeName.value = "";
  }
  app.notify("已移除保存的搭配；饮食记录未改变");
}
function exportReport() {
  try {
    const summary = summarizeNutrition(
      selectedItems.value,
      tab.value === "记录汇总",
    );
    const lines = [
      "食刻AI · 营养计算清单",
      tab.value === "搭配计算"
        ? recipeName.value || "未命名搭配"
        : `${selectedDate.value} · ${selectedSlot.value}`,
      "自填数据为用户抄录的营养表；示例为人工样本，平台未核验。",
      "每项按每100g数值×实际克重÷100计算；空白为未知，不计作0。千问估算使用常见配方，实际值会随做法变化。",
      ...selectedItems.value.flatMap((item) => [
        `${item.name}｜${item.grams}g｜${nutritionSourceLabel(item.source)}`,
        ...nutrients.map(
          (n) =>
            `  每100g${n.label}：${item.per100[n.key]?.toFixed(2) ?? "未知"} ${n.unit}${item.aiKeys?.includes(n.key) ? "（千问估算）" : ""}`,
        ),
      ]),
      `总份量：${summary.totalGrams}g`,
      ...nutrients.map(
        (n) =>
          `${n.label}：${summary.values[n.key] ?? "未知"} ${n.unit}${summary.missing[n.key] ? `（${summary.missing[n.key]}项未知，仅已知部分）` : ""}`,
      ),
      "此清单不代表实际完整摄入、配料或过敏安全，不能用于医疗决策。",
    ];
    const url = URL.createObjectURL(
        new Blob([lines.join("\n")], { type: "text/plain;charset=utf-8" }),
      ),
      link = document.createElement("a");
    link.href = url;
    link.download = "foodmoment-nutrition.txt";
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (e) {
    app.notify((e as Error).message);
  }
}
</script>
<template>
  <PageHeader
    title="每一口，都可以更了解。"
    description="把一餐放在一起算，也能按包装上的营养表，记录自己的搭配。"
  />
  <div class="nutrition-toolbar">
    <div class="nutrition-tabs" aria-label="营养分析模式">
      <button
        v-for="name in ['搭配计算', '记录汇总']"
        :key="name"
        :class="{ active: tab === name }"
        :aria-pressed="tab === name"
        @click="tab = name"
      >
        {{ name }}
      </button>
    </div>
    <button
      class="secondary"
      :disabled="
        !selectedItems.length || (tab === '搭配计算' && !!calculationError)
      "
      @click="exportReport"
    >
      导出计算清单 ↗
    </button>
  </div>
  <p
    v-if="estimateStatus"
    class="small muted nutrition-estimate-status"
    role="status"
  >
    {{ estimateStatus }}
  </p>
  <div v-if="tab === '搭配计算'" class="nutrition-workspace">
    <section class="panel nutrition-add-panel">
      <h2>这一餐，加点什么？</h2>
      <div class="nutrition-source-tabs">
        <button
          v-for="name in ['示例目录', '自填营养表']"
          :key="name"
          :aria-pressed="source === name"
          :class="{ active: source === name }"
          @click="source = name"
        >
          {{ name }}
        </button>
      </div>
      <form
        v-if="source === '示例目录'"
        class="form-stack"
        @submit.prevent="addSample"
      >
        <label
          >示例食物<select v-model="foodId" aria-label="示例食物">
            <option v-for="food in foods" :key="food.id" :value="food.id">
              {{ food.name }}
            </option>
          </select></label
        ><label
          >添加份量（g）<input
            v-model.number="sampleGrams"
            type="number"
            min="0.1"
            max="2000"
            step="any"
            required
        /></label>
        <p class="source-note">
          此目录仅用于演示计算。不同餐厅、做法与食材比例会改变实际营养值。
        </p>
        <button class="secondary" :disabled="items.length >= 20">
          添加示例食物 ＋
        </button>
      </form>
      <form v-else class="form-stack" @submit.prevent="addLabel">
        <p class="source-note">
          请抄录每100g的营养表；每份或每100mL的数值不能直接当成每100g。未标注项目留空，已标明为0时再填写0。
        </p>
        <label
          >食物名称<input
            v-model="labelName"
            required
            maxlength="60"
            placeholder="例如：原味酸奶" /></label
        ><label
          >食用份量（g）<input
            v-model.number="labelGrams"
            type="number"
            min="0.1"
            max="2000"
            step="any"
            required /></label
        ><label
          >包装能量单位<select v-model="energyUnit" aria-label="包装能量单位">
            <option value="kcal">kcal（千卡）/100g</option>
            <option value="kJ">kJ（千焦）/100g</option>
          </select></label
        >
        <div class="nutrition-label-fields">
          <label v-for="n in nutrients" :key="n.key"
            >{{ n.label }}（{{
              n.key === "kcal" ? energyUnit : n.unit
            }}/100g）<input
              v-model="labelValues[n.key]"
              type="number"
              min="0"
              :max="n.key === 'kcal' && energyUnit === 'kJ' ? 4184 : n.max"
              step="any"
              :aria-label="'每100g' + n.label"
              placeholder="未标注留空"
          /></label>
        </div>
        <p v-if="energyUnit === 'kJ'" class="source-note">
          能量换算为 kcal 展示：kJ ÷ 4.184。
        </p>
        <p v-if="labelError" class="notice error" role="alert">
          {{ labelError }}
        </p>
        <div class="actions">
          <button class="secondary">
            {{ editingEntry ? "更新这项食物" : "添加自填食物" }}</button
          ><button
            v-if="editingEntry"
            type="button"
            class="text-btn"
            @click="clearLabel"
          >
            取消编辑
          </button>
        </div>
      </form>
    </section>
    <section class="nutrition-calculation">
      <div class="panel nutrition-recipe-editor">
        <div class="between">
          <h2>我的餐食搭配</h2>
          <button class="text-btn" @click="fresh">新建搭配</button>
        </div>
        <label
          >搭配名称<input
            v-model="recipeName"
            maxlength="60"
            placeholder="例如：周三午餐搭配"
        /></label>
        <p v-if="!items.length" class="empty-state muted">
          先添加一项食物，再试着搭配第二项。份量变化时，合计会同步更新。
        </p>
        <article v-for="entry in items" :key="entry.id" class="nutrition-entry">
          <div>
            <h3>{{ entry.name }}</h3>
            <span
              :class="['tag', { 'label-source': entry.source === 'label' }]"
              >{{ entry.source === "sample" ? "人工示例" : "自填营养表" }}</span
            ><button
              v-if="entry.source === 'label'"
              class="text-btn"
              @click="editLabel(entry)"
            >
              编辑营养值
            </button>
          </div>
          <label
            >份量（g）<input
              v-model.number="entry.grams"
              :aria-label="entry.name + '份量（g）'"
              type="number"
              min="0.1"
              max="2000"
              step="any" /></label
          ><button
            class="icon-btn"
            :aria-label="'移除' + entry.name"
            @click="items = items.filter((item) => item.id !== entry.id)"
          >
            <Icon name="Trash2" :size="17" />
          </button>
        </article>
        <p v-if="error" class="notice error" role="alert">{{ error }}</p>
        <div class="nutrition-save-row">
          <button
            class="primary"
            :disabled="!items.length || !!calculationError"
            @click="save"
          >
            {{ activeId ? "更新搭配" : "保存搭配" }}</button
          ><span class="small muted">{{
            dirty
              ? "当前搭配尚未保存"
              : activeId
                ? "当前搭配与已保存内容一致"
                : "最多20项食物"
          }}</span>
        </div>
        <p class="source-note">
          保存搭配不会增加用餐记录。{{
            isDemo
              ? "演示模式保存到本机浏览器。"
              : "保存到当前浏览器会话，可刷新恢复；跨设备不同步。"
          }}
        </p>
      </div>
      <NutritionSummary :items="selectedItems" />
      <section class="panel nutrition-saved">
        <h2>
          保存的搭配 <small>{{ app.nutritionRecipes.length }} / 30</small>
        </h2>
        <p v-if="!app.nutritionRecipes.length" class="muted small">
          还没有保存的搭配，给这一餐起个名字再保存吧。
        </p>
        <article
          v-for="recipe in app.nutritionRecipes"
          :key="recipe.id"
          class="saved-nutrition"
        >
          <div>
            <strong>{{ recipe.name }}</strong>
            <p>{{ recipe.items.length }}项食物</p>
          </div>
          <button
            class="text-btn"
            :aria-label="'载入' + recipe.name"
            @click="load(recipe)"
          >
            载入</button
          ><button
            class="text-btn"
            :aria-label="'删除搭配' + recipe.name"
            @click="pendingDelete = recipe.id"
          >
            删除
          </button>
          <div
            v-if="pendingDelete === recipe.id"
            class="notice delete-nutrition"
          >
            <p>确认删除这份保存的搭配？饮食记录不会改变。</p>
            <button class="text-btn" @click="removeRecipe(recipe.id)">
              确认删除搭配</button
            ><button class="text-btn" @click="pendingDelete = ''">取消</button>
          </div>
        </article>
      </section>
    </section>
  </div>
  <section v-else class="panel nutrition-records">
    <div class="nutrition-record-filters">
      <label
        >记录日期<input
          v-model="selectedDate"
          type="date"
          :max="app.today" /></label
      ><label
        >查看餐次<select v-model="selectedSlot" aria-label="查看餐次">
          <option>全部餐次</option>
          <option>早餐</option>
          <option>午餐</option>
          <option>晚餐</option>
          <option>加餐</option>
        </select></label
      ><span class="tag">{{ records.length }}条已保存记录</span>
    </div>
    <p class="source-note">
      只汇总已保存的记录，不代表一整天的完整摄入。目录餐食使用人工示例值。分类营养按实际记录的份量估算。整份参考需要补充重量基准或使用千问估算才可纳入总量。
    </p>
    <div v-if="!records.length" class="empty-state">
      <p>这一天的所选餐次还没有记录。</p>
      <RouterLink to="/records" class="text-btn">去记录这一餐 →</RouterLink>
    </div>
    <template v-else
      ><div class="nutrition-record-list">
        <article v-for="(record, index) in records" :key="record.id">
          <span>{{ record.slot }}</span
          ><strong>{{
            record.dishName ||
            foods.find((food) => food.id === record.foodId)?.name ||
            "未知食物"
          }}</strong
          ><span>{{ record.grams }}g</span
          ><span class="tag">{{
            selectedItems[index].aiKeys?.length
              ? "含千问估算"
              : recordItems[index].source === "unknown" &&
                  categoryReference(record.slot, recordItems[index].name)
                ? "仅有整份参考"
                : nutritionSourceLabel(recordItems[index].source)
          }}</span>
        </article>
      </div>
      <NutritionSummary :items="selectedItems" record-mode
    /></template>
  </section>
</template>
<style scoped>
.nutrition-estimate-status {
  margin: -12px 0 16px;
}
.nutrition-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 15px;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.nutrition-tabs,
.nutrition-source-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.nutrition-tabs button,
.nutrition-source-tabs button {
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #809171;
  padding: 9px 15px;
  font-size: 12px;
}
.nutrition-tabs {
  padding: 5px;
  background: #edf1e6;
  border-radius: 11px;
}
.nutrition-tabs button.active {
  background: #fffefb;
  color: #3f6b50;
  box-shadow: 0 2px 8px #455f4220;
}
.nutrition-source-tabs {
  margin: 18px 0;
}
.nutrition-source-tabs button {
  font-size: 11px;
  padding: 8px 12px;
  background: #f2f5eb;
}
.nutrition-source-tabs button.active {
  background: #3f6b50;
  color: white;
}
.nutrition-toolbar > .secondary {
  font-size: 11px;
}
.nutrition-workspace {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}
.nutrition-add-panel {
  position: sticky;
  top: 25px;
}
.nutrition-add-panel h2,
.nutrition-recipe-editor h2,
.nutrition-saved h2 {
  font-size: 16px;
}
.nutrition-label-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.nutrition-label-fields label {
  font-size: 10px;
}
.nutrition-calculation {
  min-width: 0;
}
.nutrition-recipe-editor {
  margin-bottom: 22px;
}
.nutrition-recipe-editor > .between {
  margin-bottom: 17px;
  gap: 12px;
}
.nutrition-recipe-editor > label {
  display: block;
  font-size: 11px;
  color: #7c8e6f;
}
.nutrition-recipe-editor > label input {
  margin-top: 7px;
}
.nutrition-entry {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 100px 28px;
  gap: 13px;
  align-items: center;
  border-top: 1px solid var(--border);
  padding: 17px 0;
}
.nutrition-entry:first-of-type {
  margin-top: 20px;
}
.nutrition-entry h3 {
  font-size: 14px;
  overflow-wrap: anywhere;
  margin-bottom: 7px;
}
.nutrition-entry .tag {
  font-size: 9px;
}
.nutrition-entry .label-source {
  background: #e9efe3;
}
.nutrition-entry .text-btn {
  margin-left: 9px;
  font-size: 10px;
}
.nutrition-entry label {
  font-size: 9px;
}
.nutrition-entry input {
  padding: 8px 10px;
  margin-top: 5px;
  font-size: 12px;
}
.nutrition-save-row {
  display: flex;
  align-items: center;
  gap: 15px;
  flex-wrap: wrap;
  margin-top: 15px;
}
.nutrition-save-row button {
  font-size: 12px;
}
.nutrition-recipe-editor > .source-note {
  margin-top: 14px;
}
.nutrition-saved {
  margin-top: 24px;
}
.nutrition-saved h2 small {
  font-size: 11px;
  color: #95a08b;
  margin-left: 10px;
}
.saved-nutrition {
  display: flex;
  align-items: center;
  gap: 13px;
  flex-wrap: wrap;
  padding: 16px 0;
  border-bottom: 1px solid var(--border);
}
.saved-nutrition > div:first-child {
  flex: 1;
  min-width: 0;
}
.saved-nutrition strong {
  font-size: 13px;
  overflow-wrap: anywhere;
}
.saved-nutrition p,
.saved-nutrition > button {
  font-size: 10px;
}
.delete-nutrition {
  flex-basis: 100%;
  margin: 0;
}
.delete-nutrition button {
  margin-right: 16px;
}
.nutrition-record-filters {
  display: flex;
  gap: 20px;
  align-items: end;
  flex-wrap: wrap;
  margin-bottom: 15px;
}
.nutrition-record-filters label {
  font-size: 11px;
  color: #7c8e6f;
}
.nutrition-record-filters input,
.nutrition-record-filters select {
  margin-top: 7px;
}
.nutrition-record-list {
  margin: 20px 0;
}
.nutrition-record-list article {
  display: flex;
  gap: 15px;
  flex-wrap: wrap;
  padding: 13px 0;
  border-bottom: 1px solid var(--border);
  font-size: 11px;
  align-items: center;
}
.nutrition-record-list strong {
  flex: 1;
  overflow-wrap: anywhere;
}
.nutrition-record-list .tag {
  font-size: 9px;
}
@media (max-width: 1100px) {
  .nutrition-workspace {
    grid-template-columns: 1fr;
  }
  .nutrition-add-panel {
    position: static;
  }
  .nutrition-add-panel > .form-stack {
    max-width: 650px;
  }
}
@media (max-width: 600px) {
  .nutrition-toolbar {
    gap: 12px;
    margin-bottom: 20px;
  }
  .nutrition-tabs button {
    font-size: 11px;
    padding: 8px 12px;
  }
  .nutrition-workspace {
    gap: 18px;
  }
  .nutrition-label-fields {
    gap: 10px;
  }
  .nutrition-entry {
    grid-template-columns: minmax(0, 1fr) 80px 24px;
    gap: 8px;
  }
  .nutrition-entry h3 {
    font-size: 12px;
  }
  .nutrition-entry .text-btn {
    display: block;
    margin: 7px 0 0;
  }
  .nutrition-entry .tag {
    font-size: 8px;
  }
  .nutrition-record-filters {
    gap: 12px;
  }
  .nutrition-record-filters label {
    min-width: 0;
    flex: 1;
  }
  .nutrition-record-filters > .tag {
    flex-basis: 100%;
  }
  .nutrition-save-row > .primary {
    width: 100%;
    justify-content: center;
  }
  .nutrition-record-list article {
    gap: 8px;
  }
  .nutrition-record-list strong {
    flex-basis: 70%;
  }
}
</style>
