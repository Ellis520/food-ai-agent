<script setup lang="ts">
defineProps<{ title: string; description?: string }>();
</script>
<template>
  <div class="page-heading">
    <div class="page-heading-main">
      <slot name="leading" />
      <div>
        <h1>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
    </div>
    <slot />
  </div>
</template>
