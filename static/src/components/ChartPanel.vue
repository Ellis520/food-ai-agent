<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from "vue";
import * as echarts from "echarts/core";
import { BarChart, LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
echarts.use([
  BarChart,
  LineChart,
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
let chart: echarts.ECharts | undefined;
let observer: ResizeObserver;
function draw() {
  chart?.setOption({
    tooltip: {
      trigger: "axis",
      valueFormatter: (v: unknown) => `${v} ${props.unit || ""}`,
    },
    grid: { left: 48, right: 20, top: 20, bottom: 35 },
    xAxis: {
      type: "category",
      data: props.labels,
      axisLine: { lineStyle: { color: "#e4e9e3" } },
      axisTick: { show: false },
      axisLabel: { color: "#7b877d" },
    },
    yAxis: {
      type: "value",
      splitLine: { lineStyle: { color: "#eef1ec" } },
      axisLabel: { color: "#7b877d" },
    },
    series: [
      {
        data: props.values,
        type: props.type || "bar",
        smooth: true,
        barMaxWidth: 30,
        itemStyle: { color: "#629777", borderRadius: [6, 6, 0, 0] },
        lineStyle: { color: "#629777", width: 3 },
        areaStyle: { color: "#e9f1e6" },
      },
    ],
  });
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
    role="img"
    :aria-label="
      labels.map((l, i) => l + ': ' + values[i] + (unit || '')).join('；')
    "
  ></div>
</template>
