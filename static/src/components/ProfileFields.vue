<script setup lang="ts">
import type { Profile } from "../api/types";
import {
  dietaryRestrictions,
  eatingStyles,
  tastes,
  cuisines,
} from "../api/dietPreferences";
import PlanPreference from "./PlanPreference.vue";

defineProps<{ showGoal?: boolean }>();
const draft = defineModel<Profile>({ required: true });
function getPreference(field: "taste" | "cuisine", options: string[]) {
  return (
    draft.value[field] ??
    draft.value.preference
      .split(/[、，,；;]+/)
      .map((value) => value.trim())
      .filter((value) => value !== "不限" && options.includes(value))
      .join("、")
  );
}
</script>

<template>
  <div class="two-col profile-fields-row">
    <label
      >昵称<input
        v-model.trim="draft.name"
        required
        maxlength="20"
        placeholder="您希望大家怎么称呼您？"
    /></label>
    <label
      >校园<input
        v-model.trim="draft.campus"
        required
        maxlength="40"
        placeholder="例如：中山大学南校区"
    /></label>
  </div>
  <div class="two-col profile-fields-row">
    <label
      >每日预算（元）<input
        v-model.number="draft.budget"
        type="number"
        inputmode="decimal"
        min="20"
        max="200"
        step="any"
        required
    /></label>
    <label
      >每日热量目标（kcal）<input
        v-model.number="draft.kcal"
        type="number"
        inputmode="numeric"
        min="500"
        max="5000"
        required
    /></label>
  </div>
  <label v-if="showGoal"
    >饮食目标<select v-model="draft.goal">
      <option>均衡饮食</option>
      <option>规律三餐</option>
      <option>控制预算</option>
    </select></label
  >
  <div class="two-col profile-fields-row">
    <PlanPreference
      :model-value="getPreference('taste', tastes)"
      @update:model-value="draft.taste = $event"
      label="口味"
      :options="tastes"
      :disabled="false"
      :max-length="60"
      multiple
    />
    <PlanPreference
      :model-value="getPreference('cuisine', cuisines)"
      @update:model-value="draft.cuisine = $event"
      label="菜系"
      :options="cuisines"
      :disabled="false"
      :max-length="60"
      multiple
    />
  </div>
  <PlanPreference
    v-model="draft.preference"
    label="饮食偏好"
    :options="eatingStyles"
    :disabled="false"
    :max-length="200"
    multiple
  />
  <PlanPreference
    v-model="draft.allergies"
    label="过敏原 / 饮食禁忌（选填）"
    :options="dietaryRestrictions"
    :disabled="false"
    :max-length="200"
    multiple
  />
</template>

<style scoped>
label {
  display: grid;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
  font-weight: 650;
}
input,
select {
  width: 100%;
  min-width: 0;
  min-height: 44px;
}
@media (max-width: 480px) {
  .profile-fields-row {
    grid-template-columns: minmax(0, 1fr);
  }
  input,
  select {
    font-size: 16px;
  }
}
</style>
