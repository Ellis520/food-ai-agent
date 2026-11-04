<script setup lang="ts">
import { computed } from "vue";
import Icon from "./Icon.vue";
const props = defineProps<{
  label: string;
  options: string[];
  disabled: boolean;
  multiple?: boolean;
  maxLength?: number;
}>();
const value = defineModel<string>({ required: true });
const choices = computed(() => [
  "不限",
  ...props.options.filter((option) => option !== "不限"),
]);
function choose(option: string, event: MouseEvent) {
  if (!props.multiple || option === "不限") value.value = option;
  else if (option) {
    const entries = value.value
      .split(/[、，,；;]+/)
      .map((text) => text.trim())
      .filter((text) => text && text !== "不限");
    const next = [...new Set([...entries, option])].join("、");
    if (next.length <= (props.maxLength ?? 240)) value.value = next;
  }
  const menu = (event.currentTarget as HTMLElement).closest("details");
  if (menu) {
    menu.open = false;
    const input = menu.parentElement?.querySelector("input");
    input?.focus();
    if (props.multiple)
      input?.setSelectionRange(input.value.length, input.value.length);
  }
}
function closeMenu(event: FocusEvent) {
  const menu = event.currentTarget as HTMLDetailsElement;
  if (!menu.contains(event.relatedTarget as Node | null)) menu.open = false;
}
</script>

<template>
  <div class="preference-field">
    <label
      >{{ label
      }}<input
        v-model="value"
        :disabled="disabled"
        :maxlength="maxLength ?? (multiple ? 240 : 60)"
        :placeholder="
          multiple ? '可多选或填写，用顿号分隔' : '不限，可选择或填写'
        "
    /></label>
    <details v-if="!disabled" @focusout="closeMenu">
      <summary :aria-label="'展开' + label + '选项'">
        <Icon name="ChevronRight" :size="17" />
      </summary>
      <div class="preference-options">
        <button type="button" @click="choose('', $event)">自行填写</button>
        <button
          v-for="option in choices"
          :key="option"
          type="button"
          @click="choose(option, $event)"
        >
          {{ option }}
        </button>
      </div>
    </details>
  </div>
</template>

<style scoped>
.preference-field {
  position: relative;
  min-width: 0;
}
.preference-field label {
  display: grid;
  gap: 6px;
}
.preference-field input {
  width: 100%;
  padding-right: 40px;
  min-height: 44px;
}
details {
  position: absolute;
  right: 0;
  bottom: 0;
  z-index: 3;
}
summary {
  display: grid;
  place-items: center;
  width: 40px;
  height: 44px;
  cursor: pointer;
  list-style: none;
}
summary::-webkit-details-marker {
  display: none;
}
summary svg {
  transform: rotate(90deg);
}
.preference-options {
  position: absolute;
  right: 0;
  min-width: 160px;
  max-height: 230px;
  overflow: auto;
  padding: 5px;
  background: #fffefa;
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: 0 8px 24px #253d3026;
}
.preference-options button {
  display: block;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 8px 12px;
  border-radius: 6px;
}
.preference-options button:hover,
.preference-options button:focus-visible {
  background: #e8efdf;
}
.preference-options button:first-child {
  font-weight: 700;
}
@media (max-width: 720px) {
  .preference-field input {
    font-size: 16px;
  }
}
</style>
