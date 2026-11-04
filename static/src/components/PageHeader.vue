<script setup lang="ts">
defineProps<{ title: string; description?: string }>();
</script>
<template>
  <div class="page-heading">
    <span class="eyebrow day-mark">今日食刻</span>
    <div class="page-heading-main">
      <div class="page-heading-title">
        <slot name="leading" />
        <h1>{{ title }}</h1>
      </div>
      <p v-if="description">{{ description }}</p>
    </div>
    <slot />
  </div>
</template>
