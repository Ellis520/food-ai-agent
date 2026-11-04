<script setup lang="ts">
import type { HabitAnalysis } from "../api/types";
import Icon from "./Icon.vue";
const props = defineProps<{ report: HabitAnalysis; stale: boolean }>();
function evidence(kind: string) {
  const s = props.report.statistics;
  if (kind === "coverage")
    return `记录覆盖 ${s.recordedDays} / ${props.report.period.days} 天，共 ${s.recordCount} 餐`;
  if (kind === "breakfast") return `其中 ${s.breakfastDays} 天有早餐记录`;
  if (kind === "spending")
    return `已记录花费 ¥${s.totalCost.toFixed(2)}，已记录日均 ¥${s.averageRecordedDayCost.toFixed(2)}`;
  return Object.entries(s.mealCounts)
    .map(([slot, count]) => `${slot} ${count} 餐`)
    .join(" · ");
}
function download() {
  const r = props.report;
  const text = [
    "食刻AI · 饮食记录回顾",
    `${r.period.start} — ${r.period.end}`,
    `来源：${r.provider === "qwen" ? "千问真实生成 · 统计来自已保存记录" : "固定规则演示 · 未调用AI"}`,
    `生成时间：${new Date(r.generatedAt).toLocaleString("zh-CN")}`,
    props.stale ? "注意：记录或偏好已变动，以下为生成时的数据快照。" : "",
    "",
    "统计依据",
    evidence("coverage"),
    evidence("breakfast"),
    evidence("spending"),
    evidence("distribution"),
    "",
    r.provider === "qwen" ? "AI 回顾" : "演示回顾",
    r.summary,
    ...r.observations.flatMap((item) => [
      "",
      item.title,
      item.detail,
      `依据：${evidence(item.evidence)}`,
    ]),
    "",
    "下一步小行动",
    ...r.suggestions.map(
      (item, index) => `${index + 1}. ${item.title}：${item.action}`,
    ),
    "",
    r.dataNote,
  ].join("\n");
  const url = URL.createObjectURL(
      new Blob(["\uFEFF" + text], { type: "text/plain;charset=utf-8" }),
    ),
    link = document.createElement("a");
  link.href = url;
  link.download = `foodmoment-review-${r.period.start}-${r.period.end}.txt`;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
</script>

<template>
  <article class="habit-report" aria-label="饮食回顾报告">
    <div class="report-heading">
      <div>
        <span class="tag">{{
          report.provider === "qwen" ? "千问真实生成" : "规则演示 · 未调用 AI"
        }}</span>
        <p class="small muted">
          {{ report.period.start }} — {{ report.period.end }} ·
          {{ new Date(report.generatedAt).toLocaleString("zh-CN") }}
        </p>
      </div>
      <button type="button" class="secondary compact" @click="download">
        下载回顾
      </button>
    </div>
    <p v-if="stale" role="status" class="notice">
      记录或偏好已变动。这是生成时的回顾，请重新生成以反映最新数据。
    </p>
    <p class="report-summary">{{ report.summary }}</p>
    <div class="report-observations">
      <section
        v-for="(item, index) in report.observations"
        :key="index"
        class="observation"
      >
        <span class="eyebrow">OBSERVATION {{ index + 1 }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.detail }}</p>
        <div class="evidence">
          <Icon name="ChartNoAxesCombined" :size="14" /><span
            >统计依据：{{ evidence(item.evidence) }}</span
          >
        </div>
      </section>
    </div>
    <section class="next-actions">
      <h3>下一步，小步开始</h3>
      <ol>
        <li v-for="(item, index) in report.suggestions" :key="index">
          <strong>{{ item.title }}</strong>
          <p>{{ item.action }}</p>
        </li>
      </ol>
    </section>
    <p class="source-note">
      {{ report.dataNote }} 本次回顾仅保留在当前页面，可下载保存。
    </p>
  </article>
</template>

<style scoped>
.habit-report {
  border-top: 1px solid var(--border);
  margin-top: 22px;
  padding-top: 22px;
}
.report-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}
.report-heading p {
  margin-top: 8px;
}
.report-summary {
  font-size: 14px;
  color: var(--green);
  line-height: 1.9;
  margin: 22px 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.report-observations {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 14px;
}
.observation {
  padding: 19px;
  background: #f5f7ee;
  border-radius: 12px;
  min-width: 0;
}
.observation > p {
  font-size: 12px;
  margin: 10px 0;
  line-height: 1.9;
  overflow-wrap: anywhere;
}
.evidence {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  font-size: 10px;
  color: #6d815e;
  padding-top: 12px;
  border-top: 1px solid #e1e8d6;
}
.evidence svg {
  margin-top: 3px;
}
.next-actions {
  margin-top: 24px;
}
.next-actions ol {
  padding-left: 22px;
  display: grid;
  gap: 15px;
  margin-bottom: 22px;
}
.next-actions strong {
  font-size: 13px;
}
.next-actions p {
  font-size: 12px;
  margin-top: 4px;
  overflow-wrap: anywhere;
}
.habit-report > .source-note {
  margin-top: 18px;
}
@media (max-width: 600px) {
  .report-observations {
    grid-template-columns: 1fr;
  }
  .report-heading p {
    font-size: 10px;
  }
  .report-summary {
    font-size: 13px;
  }
  .observation {
    padding: 16px;
  }
}
</style>
