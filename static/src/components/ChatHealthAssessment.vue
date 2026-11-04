<script setup lang="ts">
import { computed } from "vue";
import Icon from "./Icon.vue";

const props = defineProps<{ text: string; clarify?: boolean }>();
const title = computed(() => (props.clarify ? "先了解您的情况" : "初步判断"));
// Render the existing health reply format, including replies already saved in chat.
const sections = computed(() => {
  const paragraphs = props.text
    .replace(/^初步判断\n/, "")
    .split(/\n\s*\n/)
    .filter(Boolean);
  const summary = paragraphs.shift();
  const question = paragraphs.pop();
  return { summary, notes: paragraphs, question };
});
</script>

<template>
  <section class="assessment" :aria-label="title">
    <header class="assessment-heading">
      <span class="assessment-icon"><Icon name="HeartPulse" :size="23" /></span>
      <div>
        <span class="assessment-label">健康咨询</span>
        <h3>{{ title }}</h3>
      </div>
    </header>
    <p class="assessment-summary">{{ sections.summary }}</p>
    <div v-if="sections.notes.length" class="assessment-notes">
      <p v-for="(note, index) in sections.notes" :key="index">{{ note }}</p>
    </div>
    <div v-if="sections.question" class="assessment-next">
      <Icon name="MessageCircleHeart" :size="19" />
      <div>
        <h4>{{ clarify ? "请您补充" : "接下来想确认" }}</h4>
        <p>{{ sections.question }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.assessment {
  margin-top: 7px;
  padding: clamp(15px, 2vw, 24px);
  border: 1px solid #b4cebf;
  border-left: 4px solid #2f7355;
  border-radius: 14px;
  background: #fcfefa;
  color: #203d30;
}
.assessment-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.assessment-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex: none;
  border-radius: 12px;
  background: #e2f0e7;
  color: #286146;
}
.assessment-label {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: var(--chop);
  letter-spacing: 0;
}
.assessment-heading h3 {
  margin: 0;
  font-size: clamp(21px, 2vw, 25px);
  font-weight: 750;
  line-height: 1.25;
  color: #173a2a;
}
.assessment p {
  margin: 0;
  color: #243e30;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.assessment-summary {
  font-size: 16px;
}
.assessment-notes {
  display: grid;
  gap: 8px;
  margin-top: 10px;
  font-size: 14px;
}
.assessment-next {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-top: 15px;
  padding: 12px 13px;
  border-radius: 10px;
  background: #eaf3ed;
  color: #295e46;
}
.assessment-next svg {
  margin-top: 2px;
}
.assessment-next h4 {
  margin: 0 0 3px;
  font-size: 13px;
  font-weight: 750;
}
.assessment-next p {
  font-size: 15px;
  font-weight: 650;
  line-height: 1.6;
}
</style>
