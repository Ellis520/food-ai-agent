<script setup lang="ts">
const props = defineProps<{ min?: number; max?: number }>();
const emit = defineEmits<{ change: [min?: number, max?: number] }>();
function update(event: Event, side: "min" | "max") {
  const input = event.target as HTMLInputElement;
  const value = Number.isFinite(input.valueAsNumber)
    ? input.valueAsNumber
    : undefined;
  emit(
    "change",
    side === "min" ? value : props.min,
    side === "max" ? value : props.max,
  );
}
</script>

<template>
  <fieldset class="plan-price">
    <legend>预计价格区间（元）</legend>
    <div class="range-box">
      <input
        :value="min"
        aria-label="预计最低价（元）"
        placeholder="最低"
        type="number"
        inputmode="decimal"
        min="0"
        max="1000"
        step="0.01"
        @input="update($event, 'min')"
      />
      <span aria-hidden="true">–</span>
      <input
        :value="max"
        aria-label="预计最高价（元）"
        placeholder="最高"
        type="number"
        inputmode="decimal"
        min="0"
        max="1000"
        step="0.01"
        @input="update($event, 'max')"
      />
    </div>
    <small
      v-if="min !== undefined && max !== undefined && min > max"
      class="error"
      >最低价应小于或等于最高价。</small
    >
  </fieldset>
</template>

<style scoped>
.plan-price {
  border: 0;
  padding: 0;
  margin: 0;
  min-width: 0;
  color: var(--muted);
}
legend {
  padding: 0;
  margin-bottom: 6px;
  font-size: inherit;
}
.range-box {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: #fffefa;
}
.range-box input {
  border: 0;
  background: transparent;
  padding-inline: 8px;
  text-align: center;
  appearance: textfield;
}
.range-box input::-webkit-inner-spin-button,
.range-box input::-webkit-outer-spin-button {
  appearance: none;
}
.range-box span {
  color: #657963;
}
</style>
