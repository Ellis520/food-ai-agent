<script setup lang="ts">
import { computed, ref } from "vue";
import type { NutrientKey } from "../api/types";
import {
  nutritionSourceLabel,
  nutrients,
  summarizeNutrition,
  nutritionBreakdown,
  type NutritionAnalysisEntry,
} from "../api/nutrition";
const props = defineProps<{
  items: NutritionAnalysisEntry[];
  recordMode?: boolean;
}>();
const calculation = computed(() => {
  try {
    return {
      summary: summarizeNutrition(props.items, props.recordMode),
      error: "",
    };
  } catch (e) {
    return { summary: null, error: (e as Error).message };
  }
});
const metric = ref<NutrientKey>("kcal"),
  metricInfo = computed(() => nutrients.find((n) => n.key === metric.value)!);
const rows = computed(() =>
  calculation.value.error
    ? []
    : nutritionBreakdown(props.items, metric.value, props.recordMode),
);
</script>
<template>
  <div v-if="calculation.error" class="notice error" role="alert">
    {{ calculation.error }}
  </div>
  <template v-else-if="calculation.summary">
    <div class="nutrition-total-line">
      <strong>合计 {{ calculation.summary.totalGrams }}g</strong
      ><span
        >{{ items.length }}项食物 · {{ calculation.summary.labelCount }}项自填 /
        {{ calculation.summary.sampleCount }}项示例<span
          v-if="calculation.summary.estimateCount"
        >
          / {{ calculation.summary.estimateCount }}项分类估算</span
        ><span v-if="calculation.summary.unknownCount">
          / {{ calculation.summary.unknownCount }}项营养未知</span
        ></span
      >
    </div>
    <div class="nutrient-grid">
      <article
        v-for="n in nutrients"
        :key="n.key"
        class="panel nutrient"
        :data-nutrient="n.key"
      >
        <p>{{ n.label }}</p>
        <strong
          >{{ calculation.summary.values[n.key] ?? "—" }}
          <small>{{ n.unit }}</small></strong
        ><span class="small muted">{{
          !items.length
            ? "添加食物后计算"
            : calculation.summary.values[n.key] === null
              ? "未提供数值"
              : calculation.summary.missing[n.key]
                ? `仅已知部分 · ${calculation.summary.missing[n.key]}项未知`
                : "所选食物合计"
        }}</span
        ><span
          v-if="calculation.summary.aiMetrics.includes(n.key)"
          class="small muted"
          >含千问估算</span
        >
      </article>
    </div>
    <section
      v-if="items.length"
      class="panel nutrition-breakdown"
      aria-label="食物营养贡献明细"
    >
      <div class="breakdown-header">
        <div>
          <h2>这些数值，来自哪里？</h2>
          <p>
            {{
              calculation.summary.missing[metric]
                ? "比例按有数值的项目计算，剩余未知项不计入。"
                : "按这一餐各项食物的数值比较。"
            }}
          </p>
        </div>
        <label
          >分项指标<select v-model="metric" aria-label="分项指标">
            <option v-for="n in nutrients" :key="n.key" :value="n.key">
              {{ n.label }}
            </option>
          </select></label
        >
      </div>
      <article
        v-for="row in rows"
        :key="row.id"
        class="contribution-row"
        :data-contribution="row.id"
      >
        <div>
          <strong>{{ row.name }}</strong
          ><span
            >{{ row.grams }}g ·
            {{
              row.aiEstimated
                ? "千问估算"
                : row.value === null
                  ? "营养数据未提供"
                  : nutritionSourceLabel(row.source)
            }}</span
          >
        </div>
        <p>
          {{ row.value ?? "未知"
          }}{{ row.value === null ? "" : " " + metricInfo.unit
          }}<small v-if="row.share !== null"
            >{{ row.share }}%{{
              calculation.summary.missing[metric] ? "（已知部分）" : ""
            }}</small
          >
        </p>
        <div class="contribution-track" aria-hidden="true">
          <span :style="{ width: (row.share ?? 0) + '%' }"></span>
        </div>
      </article>
      <details class="calculation-details">
        <summary>查看每100g与份量换算明细</summary>
        <div class="calculation-table">
          <table>
            <thead>
              <tr>
                <th>食物</th>
                <th>每100g</th>
                <th>份量</th>
                <th>{{ metricInfo.label }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>{{ row.name }}</td>
                <td>
                  {{
                    row.per100 === null
                      ? "未知"
                      : Number(row.per100.toFixed(2))
                  }}{{ row.per100 === null ? "" : " " + metricInfo.unit }}
                </td>
                <td>{{ row.grams }}g</td>
                <td>
                  {{ row.value ?? "未知"
                  }}{{ row.value === null ? "" : " " + metricInfo.unit }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p>
          单项＝每100g数值×份量÷100。分项分别显示一位小数；合计先汇总原始值，再保留一位小数。
        </p>
      </details>
    </section>
  </template>
</template>
<style scoped>
.nutrition-total-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.nutrition-total-line strong {
  font-family: var(--sans);
  color: #1a241c;
  font-size: 18px;
  font-weight: 700;
}
.nutrition-total-line span {
  font-size: 14px;
  color: var(--muted);
}
.nutrient {
  padding: 20px 17px;
}
.nutrient strong {
  font-family: var(--sans);
  font-size: 32px;
  font-weight: 700;
  overflow-wrap: anywhere;
}
.nutrient strong small {
  font-size: 12px;
}
.nutrient > span {
  display: block;
  font-size: 14px;
  line-height: 1.7;
}
@media (max-width: 600px) {
  .nutrient {
    padding: 15px 12px;
  }
  .nutrient strong {
    font-size: 25px;
  }
}
.nutrition-breakdown {
  margin-top: 24px;
}
.breakdown-header {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.breakdown-header h2 {
  font-size: 22px;
}
.breakdown-header p {
  font-size: 14px;
  margin-top: 7px;
}
.breakdown-header label {
  font-size: 14px;
  color: #5c6b60;
}
.breakdown-header select {
  font-size: 15px;
  margin-top: 5px;
  padding: 8px 10px;
  min-width: 140px;
}
.contribution-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  padding: 13px 0;
  border-bottom: 1px solid var(--border);
}
.contribution-row strong {
  font-family: var(--sans);
  font-size: 16px;
  font-weight: 700;
  overflow-wrap: anywhere;
}
.contribution-row div > span {
  display: block;
  font-size: 14px;
  color: var(--muted);
  margin-top: 4px;
}
.contribution-row p {
  font-family: var(--sans);
  font-size: 16px;
  font-weight: 700;
  text-align: right;
  color: #1a241c;
}
.contribution-row small {
  display: block;
  font-size: 13px;
  font-weight: 400;
  margin-top: 5px;
}
.contribution-track {
  grid-column: 1/-1;
  background: #edf1e7;
  border-radius: 4px;
  height: 5px;
  overflow: hidden;
}
.contribution-track > span {
  height: 100%;
  background: #9bad78;
  border-radius: 4px;
  margin: 0 !important;
}
.calculation-details {
  margin-top: 18px;
  font-size: 14px;
  color: #5c6b60;
}
.calculation-details summary {
  cursor: pointer;
}
.calculation-table {
  overflow-x: auto;
  margin-top: 13px;
}
.calculation-table table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
  table-layout: fixed;
}
.calculation-table th,
.calculation-table td {
  padding: 9px 4px;
  text-align: left;
  border-bottom: 1px solid var(--border);
  overflow-wrap: anywhere;
}
.calculation-table th:first-child {
  width: 35%;
}
.calculation-details > p {
  font-size: 14px;
  margin-top: 12px;
  line-height: 1.8;
}
</style>
