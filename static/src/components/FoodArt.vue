<script setup lang="ts">
withDefaults(defineProps<{ emoji?: string; variant?: string }>(), {
  emoji: "🥗",
  variant: "green",
});
</script>
<template>
  <div class="food-art" :class="variant" aria-hidden="true">
    <span class="art-ring"></span><span class="art-sprinkle one">✦</span
    ><span class="art-sprinkle two">✧</span
    ><span class="food-emoji">{{ emoji }}</span
    ><span class="art-dot"></span>
  </div>
</template>
