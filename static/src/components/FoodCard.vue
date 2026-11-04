<script setup lang="ts">
import type { Food } from "../api/types";
import FoodArt from "./FoodArt.vue";
import Icon from "./Icon.vue";
defineProps<{ food: Food; label?: string }>();
defineEmits<{ choose: [id: string] }>();
</script>
<template>
  <article class="food-card">
    <FoodArt
      :emoji="food.emoji"
      :variant="food.category === '热食' ? 'peach' : 'green'"
    />
    <div class="food-card-body">
      <span class="tag">{{ label || food.category }}</span>
      <h3>{{ food.name }}</h3>
      <p>示例份量 300g · {{ Math.round(food.kcal * 3) }} kcal</p>
      <div class="between">
        <strong>¥{{ food.price }} <small>/ 示例价</small></strong
        ><button
          class="round-btn"
          :aria-label="'选择' + food.name"
          @click="$emit('choose', food.id)"
        >
          <Icon name="Plus" :size="18" />
        </button>
      </div>
    </div>
  </article>
</template>
