<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from "vue";
import * as echarts from "echarts/core";
import { BarChart, LineChart } from "echarts/charts";
import {
  DataZoomComponent,
  GridComponent,
  TooltipComponent,
} from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
echarts.use([
  BarChart,
  LineChart,
  DataZoomComponent,
  GridComponent,
  TooltipComponent,
  CanvasRenderer,
]);
const props = defineProps<{
  labels: string[];
  values: number[];
  unit?: string;
  type?: "bar" | "line";
}>();
const el = ref<HTMLDivElement>();
const canSlide = ref(false);
const visibleDays = 7;
let chart: echarts.ECharts | undefined;
let observer: ResizeObserver;
function draw() {
  const count = props.labels.length;
  const windowSize = Math.min(visibleDays, count);
  canSlide.value = count > windowSize;
  const span = count ? (windowSize / count) * 100 : 100;
  chart?.setOption(
    {
      tooltip: {
        trigger: "axis",
        valueFormatter: (v: unknown) => `${v} ${props.unit || ""}`,
      },
      grid: { left: 2, right: 6, top: 12, bottom: 0, containLabel: true },
      dataZoom: canSlide.value
        ? [
            {
              type: "inside",
              start: 100 - span,
              end: 100,
              minSpan: span,
              maxSpan: span,
              zoomLock: true,
              filterMode: "none",
              moveOnMouseMove: true,
              moveOnMouseWheel: true,
              zoomOnMouseWheel: false,
              preventDefaultMouseMove: true,
            },
          ]
        : [],
      xAxis: {
        type: "category",
        data: props.labels,
        axisLine: { lineStyle: { color: "#e4e9e3" } },
        axisTick: { show: false },
        axisLabel: {
          color: "#7b877d",
          fontSize: 11,
          interval: 0,
          margin: 8,
        },
      },
      yAxis: {
        type: "value",
        splitLine: { lineStyle: { color: "#eef1ec" } },
        axisLabel: { color: "#7b877d", fontSize: 11, margin: 4 },
      },
      series: [
        {
          data: props.values,
          type: props.type || "bar",
          smooth: true,
          barMaxWidth: 36,
          itemStyle: { color: "#629777", borderRadius: [6, 6, 0, 0] },
          lineStyle: { color: "#629777", width: 3 },
          areaStyle: { color: "#e9f1e6" },
        },
      ],
    },
    { replaceMerge: ["dataZoom"] },
  );
}
onMounted(() => {
  chart = echarts.init(el.value!);
  draw();
  observer = new ResizeObserver(() => chart?.resize());
  observer.observe(el.value!);
});
watch(() => [props.values, props.labels], draw, { deep: true });
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>
<template>
  <div
    ref="el"
    class="chart"
    :class="{ slidable: canSlide }"
    role="img"
    :aria-label="
      (canSlide ? '可向左滑动查看更早日期，不能滑过最近一天。' : '') +
      labels.map((l, i) => l + ': ' + values[i] + (unit || '')).join('；')
    "
  ></div>
  <p v-if="canSlide" class="muted small chart-hint">
    向左滑动查看更早的日期，不能滑过最近一天。
  </p>
</template>
