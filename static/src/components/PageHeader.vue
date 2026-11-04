<script setup lang="ts">
defineProps<{ eyebrow?: string; title: string; description: string }>();
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">{{ eyebrow || "YOUR CAMPUS, YOUR MOMENT" }}</span>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <slot />
  </div>
</template>
