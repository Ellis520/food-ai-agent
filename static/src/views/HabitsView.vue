<script setup lang="ts">
import {
  computed,
  defineAsyncComponent,
  h,
  ref,
  watch,
  onBeforeUnmount,
} from "vue";
import { useApp } from "../stores/app";
import { api, isDemo } from "../api";
import { summarizeHabits } from "../api/habits";
import type { AnalysisDays, HabitAnalysis } from "../api/types";
import PageHeader from "../components/PageHeader.vue";
import HabitReport from "../components/HabitReport.vue";
import Icon from "../components/Icon.vue";
const ChartPanel = defineAsyncComponent({
  loader: () => import("../components/ChartPanel.vue"),
  onError(_error, retry, fail, attempts) {
    if (attempts <= 3) setTimeout(() => retry(), 400 * attempts);
    else fail();
  },
  errorComponent: () =>
    h(
      "p",
      { class: "notice", role: "status" },
      "图表暂时无法加载，仍可查看本页的文字统计。",
    ),
});
const app = useApp(),
  days = ref<AnalysisDays>(7),
  busy = ref(false),
  error = ref(""),
  report = ref<HabitAnalysis>(),
  reportFingerprint = ref("");
const summary = computed(() =>
    summarizeHabits(app.meals, days.value, app.today),
  ),
  stats = computed(() => summary.value.statistics);
const weekly = computed(() =>
  app.meals.filter(
    (meal) =>
      meal.date >= summary.value.period.start &&
      meal.date <= summary.value.period.end,
  ),
);
const fingerprint = computed(() =>
  JSON.stringify([
    days.value,
    weekly.value,
    app.profile.goal,
    app.profile.budget,
    app.profile.preference,
  ]),
);
const stale = computed(
  () => !!report.value && reportFingerprint.value !== fingerprint.value,
);
let requestId = 0;
watch(days, () => {
  report.value = undefined;
  error.value = "";
});
onBeforeUnmount(() => {
  requestId++;
});
async function analyze() {
  if (busy.value || !stats.value.recordCount) return;
  const id = ++requestId;
  busy.value = true;
  error.value = "";
  try {
    await app.sync();
    if (id !== requestId) return;
    if (app.storageError)
      throw new Error(
        "记录尚未成功保存，请先处理页面顶部的保存提示，再生成回顾。",
      );
    const before = fingerprint.value;
    const result = await api.analyze(
      days.value,
      app.revision,
      app.meals,
      app.today,
    );
    if (id !== requestId) return;
    if (before !== fingerprint.value)
      throw new Error("生成期间记录或偏好发生变化，请重新生成。");
    report.value = result;
    reportFingerprint.value = before;
  } catch (e) {
    if (id === requestId) error.value = (e as Error).message;
  } finally {
    if (id === requestId) busy.value = false;
  }
}
</script>

<template>
  <PageHeader
    title="饮食习惯"
    description="只统计已经记下的餐。没记的日期不算没吃。"
  />
  <section class="panel habit-range">
    <label
      >回顾范围<select
        v-model.number="days"
        aria-label="回顾范围"
        :disabled="busy"
      >
        <option :value="7">近 7 天</option>
        <option :value="30">近 30 天</option>
      </select></label
    >
    <p class="small muted">
      {{ summary.period.start }} — {{ summary.period.end }} ·
      截至今天，仅统计已确认的饮食记录。
    </p>
  </section>
  <div class="stats-grid">
    <article class="panel metric">
      <p>近 {{ days }} 天已记录</p>
      <strong
        >{{ stats.recordedDays }} <small>/ {{ days }} 天</small></strong
      >
    </article>
    <article class="panel metric">
      <p>早餐记录覆盖</p>
      <strong>{{ stats.breakfastDays }} <small>天</small></strong>
    </article>
    <article class="panel metric">
      <p>已记录花费</p>
      <strong>¥{{ stats.totalCost.toFixed(2) }}</strong>
    </article>
  </div>
  <div class="analysis-grid">
    <section class="panel">
      <h2>这段时间的饮食花费</h2>
      <p class="muted small">
        {{ summary.period.start }} — {{ summary.period.end }} ·
        空白记录不等于未用餐
      </p>
      <ChartPanel
        :labels="stats.daily.map((day) => day.date.slice(5))"
        :values="stats.daily.map((day) => day.totalCost)"
        unit="元"
      />
    </section>
    <aside class="panel insight">
      <h2>已记下的</h2>
      <p v-if="!stats.recordCount">
        这段时间暂无记录。记录几餐后，回来看看变化吧。
      </p>
      <p v-else>
        你记录了 {{ stats.recordCount }} 餐，覆盖
        {{ stats.recordedDays }} 天。其中
        {{ stats.breakfastDays }} 天有早餐记录。未记录的日期无法判断是否用餐。
      </p>
      <p>
        这是已记下内容的统计。记录还少也值得看看，之后记得多一些，回顾会更贴近您的日常。
      </p>
      <RouterLink to="/records" class="primary">再记录一餐</RouterLink>
    </aside>
  </div>
  <section class="panel">
    <h2>餐次记录分布</h2>
    <div class="meal-distribution">
      <div v-for="(count, slot) in stats.mealCounts" :key="slot">
        <span>{{ slot }}</span
        ><strong>{{ count }} 餐</strong>
      </div>
    </div>
    <p class="source-note">
      以上为记录统计，不调用
      AI。演示模式的初始两条记录为示例，不据此推断健康状况。
    </p>
  </section>
  <section
    class="panel ai-habit-panel"
    aria-label="AI 饮食回顾"
    :aria-busy="busy"
  >
    <div class="ai-habit-heading">
      <div>
        <h2>{{ isDemo ? "饮食记录回顾" : "回顾这段记录" }}</h2>
        <p>把统计变成易读的观察，再给下一餐一个小行动。</p>
      </div>
      <button
        class="primary"
        type="button"
        :disabled="busy || !stats.recordCount"
        @click="analyze"
      >
        {{
          busy
            ? "正在生成回顾…"
            : report
              ? "重新生成回顾"
              : isDemo
                ? "生成演示回顾"
                : "生成 AI 回顾"
        }}
      </button>
    </div>
    <p v-if="!stats.recordCount" class="notice">
      所选范围暂无记录，先记下实际吃过的一餐，再来生成回顾。
    </p>
    <p v-else-if="stats.recordedDays < 3" class="notice small">
      记录还不多。回顾会按您已经记下的这几餐来写，并轻轻说说下一步。
    </p>
    <p class="source-note">
      {{
        isDemo
          ? "固定规则演示，未调用千问。"
          : "点击后调用千问，发送所选范围的汇总统计及饮食目标、预算和偏好；不发送姓名或原始逐条记录。服务失败会明确提示。"
      }}
      回顾不会修改饮食记录。
    </p>
    <p v-if="error" role="alert" class="notice error">{{ error }}</p>
    <HabitReport v-if="report" :report="report" :stale="stale" />
  </section>
</template>

<style scoped>
.habit-range {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 14px;
  margin-bottom: 20px;
}
.habit-range label {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  max-width: 280px;
  font-size: 14px;
  font-weight: 700;
  color: #1a241c;
}
.habit-range select {
  width: 100%;
  min-width: 0;
}
.ai-habit-panel {
  margin-top: 24px;
}
.ai-habit-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}
.ai-habit-heading p {
  font-size: 14px;
  margin-top: 7px;
}
.ai-habit-panel > .source-note {
  margin-top: 18px;
}
.ai-habit-panel > .notice {
  margin-top: 20px;
}
@media (max-width: 600px) {
  .habit-range {
    gap: 12px;
  }
  .habit-range p {
    font-size: 10px;
  }
  .ai-habit-heading > .primary {
    width: 100%;
  }
  .ai-habit-heading h2 {
    font-size: 17px;
  }
}
</style>
