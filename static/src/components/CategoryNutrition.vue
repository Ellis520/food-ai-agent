<script setup lang="ts">
import { computed } from "vue";
import { categoryReference, nutrients } from "../api/nutrition";

const props = defineProps<{ slot: string; dish: string; grams: number }>();
const reference = computed(() => categoryReference(props.slot, props.dish));
const ratio = computed(() => {
  const base = reference.value?.grams;
  if (!base) return 1;
  return Number.isFinite(props.grams) && props.grams > 0 && props.grams <= 2000
    ? props.grams / base
    : null;
});
const values = computed(() => {
  const ref = reference.value,
    scale = ratio.value;
  if (!ref || scale === null) return [];
  return nutrients.map((nutrient) => ({
    ...nutrient,
    value: Number((ref.values[nutrient.key] * scale).toFixed(1)),
  }));
});
</script>

<template>
  <details v-if="reference && values.length" class="category-nutrition">
    <summary>
      <strong>约 {{ values[0].value }} kcal</strong>
      <span>{{
        reference.grams ? `${grams} g · 分类估算` : "整份参考 · 分类估算"
      }}</span>
    </summary>
    <dl>
      <div v-for="nutrient in values" :key="nutrient.key">
        <dt>{{ nutrient.label }}</dt>
        <dd>{{ nutrient.value }} {{ nutrient.unit }}</dd>
      </div>
    </dl>
    <p>
      参考餐食为{{ reference.example
      }}{{
        reference.grams
          ? `，以 ${reference.grams} g 为基准换算。`
          : "。缺少参考重量，暂无法按克数换算。"
      }}实际营养会随食材、配方和调味变化。
    </p>
  </details>
  <p v-else class="muted">
    {{
      reference
        ? "请输入有效份量以换算营养"
        : dish === "不限" || !dish
          ? "餐食待定，可稍后修改"
          : "此菜暂无营养数据"
    }}
  </p>
</template>

<style scoped>
.category-nutrition {
  grid-column: 2/-1;
  min-width: 0;
  background: #f1f4ea;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 10px 14px;
}
.category-nutrition summary {
  cursor: pointer;
  color: #3a5c47;
  font-size: 13px;
}
.category-nutrition summary strong {
  margin-left: 4px;
}
.category-nutrition summary span {
  margin-left: 10px;
  font-size: 12px;
}
.category-nutrition dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 16px;
  margin: 14px 0 10px;
}
.category-nutrition dt,
.category-nutrition p {
  font-size: 12px;
  color: #596d57;
}
.category-nutrition dd {
  margin: 0;
  font-size: 14px;
  font-weight: 650;
}
@media (max-width: 720px) {
  .category-nutrition {
    grid-column: 1/-1;
  }
  .category-nutrition dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
