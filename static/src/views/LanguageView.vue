<script setup lang="ts">
import { ref, computed, nextTick, onBeforeUnmount } from "vue";
import { api, isDemo } from "../api";
import {
  safetyTips,
  safetySources,
  safetyReviewedAt,
  type SafetyReply,
  type SafetyTurn,
} from "../api/foodSafety";
import { placeActionReply, restaurantFollowUp } from "../api/restaurantActions";
import type {
  ChatHealthOptions,
  ChatPlace,
  HealthMessage,
  Message,
  RestaurantMode,
} from "../api/types";
import ChatHealthActions from "../components/ChatHealthActions.vue";
import ChatHealthAssessment from "../components/ChatHealthAssessment.vue";
import ChatRestaurantCards from "../components/ChatRestaurantCards.vue";
import PageHeader from "../components/PageHeader.vue";
import Icon from "../components/Icon.vue";
import VoiceInput from "../components/VoiceInput.vue";

const category = ref("全部"),
  draft = ref(""),
  busy = ref(false),
  error = ref(""),
  voiceBusy = ref(false),
  voiceOriginal = ref("");
const categories = ["全部", ...new Set(safetyTips.map((tip) => tip.category))];
const visibleTips = computed(() =>
  safetyTips.filter(
    (tip) => category.value === "全部" || tip.category === category.value,
  ),
);
interface Exchange {
  id: string;
  question: string;
  reply: SafetyReply | HealthMessage;
}
const exchanges = ref<Exchange[]>([]),
  answerAnchor = ref<HTMLElement>(),
  composer = ref<HTMLTextAreaElement>();
const describingSymptoms = ref(false);
let lastRequest: { question: string; options?: ChatHealthOptions } | undefined;
let generation = 0;
function isHealth(reply: Message | SafetyReply): reply is HealthMessage {
  return (
    reply.kind === "health" &&
    reply.role === "assistant" &&
    Boolean(reply.healthStatus)
  );
}
function voiceStart() {
  voiceOriginal.value = draft.value;
  error.value = "";
}
function voiceDraft(text: string) {
  draft.value = (voiceOriginal.value + text).slice(0, 1600);
}
async function choose(question: string) {
  draft.value = question;
  await nextTick();
  composer.value?.focus();
  composer.value?.scrollIntoView({ behavior: "smooth", block: "center" });
}
function describe() {
  describingSymptoms.value = true;
  composer.value?.focus();
}
async function append(question: string, reply: Exchange["reply"]) {
  exchanges.value = [
    ...exchanges.value,
    { id: crypto.randomUUID(), question, reply },
  ].slice(-12);
  await nextTick();
  answerAnchor.value?.scrollIntoView({ behavior: "smooth", block: "start" });
}
function choosePlace(
  current: Exchange["reply"],
  mode: RestaurantMode,
  place: ChatPlace,
) {
  if (
    busy.value ||
    voiceBusy.value ||
    !isHealth(current) ||
    !current.restaurantSearch
  )
    return;
  const reply = placeActionReply(current.restaurantSearch, mode, place);
  if (isHealth(reply)) {
    error.value = "";
    const label = mode === "takeaway" ? "点外卖" : "到店买药";
    void append(`${label} · ${place.name}`, reply);
  }
}
function lookup(options: ChatHealthOptions) {
  const subject = options.healthLookup === "hospital" ? "医院" : "药店";
  return send(`查附近${subject}`, options);
}
function retry() {
  if (lastRequest) return send(lastRequest.question, lastRequest.options);
}
async function send(question = draft.value, options?: ChatHealthOptions) {
  if (busy.value || voiceBusy.value || !question.trim()) return;
  const text = question.trim(),
    id = ++generation;
  busy.value = true;
  error.value = "";
  lastRequest = { question: text, options };
  const safetyHistory: SafetyTurn[] = [];
  const healthHistory: Message[] = [];
  let collectingHealth = true;
  for (const item of exchanges.value.slice(-4).reverse()) {
    if (isHealth(item.reply)) {
      if (collectingHealth)
        healthHistory.unshift(
          { id: item.id, role: "user", text: item.question },
          item.reply,
        );
    } else {
      collectingHealth = false;
      safetyHistory.unshift(
        { role: "user", text: item.question },
        { role: "assistant", text: item.reply.answer },
      );
    }
  }
  try {
    const latest = healthHistory.at(-1)?.restaurantSearch;
    const followUp =
      !options &&
      latest?.category === "pharmacy" &&
      restaurantFollowUp(text, latest);
    const action =
      latest && followUp
        ? placeActionReply(latest, followUp.mode, followUp.place)
        : undefined;
    const reply =
      action && isHealth(action)
        ? action
        : await api.safetyAsk(text, safetyHistory, healthHistory, options);
    if (id !== generation) return;
    draft.value = "";
    describingSymptoms.value = false;
    lastRequest = undefined;
    await append(text, reply);
  } catch (e) {
    if (id === generation) error.value = (e as Error).message;
  } finally {
    if (id === generation) busy.value = false;
  }
}
function clear() {
  generation++;
  exchanges.value = [];
  error.value = "";
  draft.value = "";
  lastRequest = undefined;
  describingSymptoms.value = false;
}
onBeforeUnmount(() => {
  generation++;
});
const providerLabel = (reply: SafetyReply) =>
  reply.provider === "qwen"
    ? "千问 · 预置资料辅助"
    : reply.provider === "demo"
      ? "规则演示 · 未调用千问"
      : "本地危险症状提醒 · 未调用千问";
const kindLabel = (reply: SafetyReply) =>
  reply.kind === "urgent"
    ? "危险症状需立即求助"
    : reply.kind === "professional"
      ? "建议咨询医生或药师"
      : "日常食品安全科普";

function answerParagraphs(text: string) {
  return text
    .trim()
    .split(/\n+/)
    .flatMap((line) => {
      const paragraphs: string[] = [];
      let paragraph = "";
      for (const sentence of line.split(/(?<=[。！？])/)) {
        if (paragraph && (paragraph + sentence).length > 90) {
          paragraphs.push(paragraph);
          paragraph = "";
        }
        paragraph += sentence;
      }
      if (paragraph.trim()) paragraphs.push(paragraph.trim());
      return paragraphs;
    });
}
</script>

<template>
  <PageHeader
    eyebrow="A LITTLE CARE, EVERY DAY"
    title="饮食小贴士，安心每一餐。"
    description="您可以打字或说话，咨询饮食安全、身体不适，或查找附近的医院和药店。"
  />
  <div class="safety-layout">
    <section class="safety-library" aria-label="饮食安全提示">
      <div class="between safety-heading">
        <h2>日常安心指南</h2>
        <span class="tag">{{ safetyTips.length }} 条精选</span>
      </div>
      <div class="safety-filters" role="group" aria-label="提示分类">
        <button
          v-for="item in categories"
          :key="item"
          class="chip"
          :class="{ active: category === item }"
          :aria-pressed="category === item"
          @click="category = item"
        >
          {{ item }}
        </button>
      </div>
      <article
        v-for="tip in visibleTips"
        :key="tip.id"
        class="panel safety-tip"
      >
        <div class="safety-tip-top">
          <span class="safety-tip-icon"
            ><Icon :name="tip.icon" :size="23" /></span
          ><span class="eyebrow">{{ tip.category }}</span>
        </div>
        <h3>{{ tip.title }}</h3>
        <p>{{ tip.summary }}</p>
        <div class="safety-tip-footer">
          <a
            v-for="source in safetySources.filter((source) =>
              tip.sourceIds.includes(source.id),
            )"
            :key="source.id"
            :href="source.url"
            target="_blank"
            rel="noopener noreferrer"
            >查看权威资料 ↗</a
          ><button
            class="text-btn"
            :disabled="busy || voiceBusy"
            @click="choose(tip.question)"
          >
            问一问 <Icon name="ArrowUpRight" :size="16" />
          </button>
        </div>
      </article>
      <p class="source-note">
        提示为人工整理的科普摘要，资料核对日期
        {{
          safetyReviewedAt
        }}。不是实时检索；具体药品以说明书及医生、药师指导为准。
      </p>
    </section>
    <section class="safety-chat panel" aria-label="饮食安全问答">
      <div class="between">
        <div>
          <span class="eyebrow">ASK FOODMOMENT</span>
          <h2>有疑问，先问清楚。</h2>
        </div>
        <span class="safety-chat-icon"
          ><Icon name="MessageCircleHeart" :size="26"
        /></span>
      </div>
      <p class="source-note">
        {{
          isDemo
            ? "当前为规则演示，连接后端后可使用完整问答与地点查询。"
            : "食品安全问答参考预置资料。症状判断与就医、用药提示来自本地规则，医院和药店由高德查询。"
        }}
      </p>
      <div class="safety-boundary notice">
        <strong>先确认安全，再继续聊</strong>
        <p>
          若现在出现呼吸困难、喉咙肿胀、晕厥等危险症状，请立即拨打中国大陆
          <a href="tel:120">120</a> 或当地急救电话。一般问答不能替代诊疗。
        </p>
      </div>
      <div v-if="!exchanges.length" class="safety-empty">
        <Icon name="Sprout" :size="34" />
        <h3>您今天想了解什么？</h3>
        <div class="safety-shortcuts">
          <button
            v-for="question in [
              '外卖没吃完怎么放？',
              '我身体不舒服',
              '查附近医院',
              '查附近药店',
            ]"
            :key="question"
            type="button"
            class="secondary"
            :disabled="busy || voiceBusy"
            @click="choose(question)"
          >
            {{ question }}
          </button>
        </div>
      </div>
      <div
        v-for="(item, index) in exchanges"
        :key="item.id"
        :ref="
          (el) => {
            if (index === exchanges.length - 1)
              answerAnchor = el as HTMLElement;
          }
        "
        class="safety-exchange"
      >
        <p class="safety-question"><span>您的问题</span>{{ item.question }}</p>
        <template v-if="isHealth(item.reply)">
          <ChatHealthAssessment
            v-if="
              item.reply.healthStatus === 'clarify' ||
              item.reply.text.startsWith('初步判断\n')
            "
            :text="item.reply.text"
            :clarify="item.reply.healthStatus === 'clarify'"
          />
          <p v-else class="health-reply-text">{{ item.reply.text }}</p>
          <ChatRestaurantCards
            v-if="item.reply.restaurantSearch"
            :search="item.reply.restaurantSearch"
            :mode="item.reply.restaurantMode"
            :disabled="busy || voiceBusy"
            @choose="(mode, place) => choosePlace(item.reply, mode, place)"
          />
          <ChatHealthActions
            v-else-if="index === exchanges.length - 1"
            :status="item.reply.healthStatus"
            :topic="item.reply.healthTopic"
            :disabled="busy || voiceBusy"
            :demo="isDemo"
            @lookup="lookup"
            @describe="describe"
          />
        </template>
        <article
          v-else
          class="safety-answer"
          :class="item.reply.kind"
          aria-label="安全问答回复"
        >
          <header class="safety-answer-header">
            <span class="safety-answer-icon"
              ><Icon
                :name="
                  item.reply.kind === 'urgent' ? 'TriangleAlert' : 'ShieldCheck'
                "
                :size="22"
            /></span>
            <div>
              <span class="safety-answer-eyebrow">饮食安全问答</span>
              <h3>{{ kindLabel(item.reply) }}</h3>
            </div>
          </header>
          <div class="safety-answer-copy">
            <p
              v-for="(paragraph, part) in answerParagraphs(item.reply.answer)"
              :key="part"
            >
              {{ paragraph }}
            </p>
          </div>
          <section
            v-if="item.reply.steps.length"
            class="safety-steps"
            aria-label="可以这样做"
          >
            <h4>您可以这样做</h4>
            <ol role="list">
              <li v-for="(step, stepIndex) in item.reply.steps" :key="step">
                <span class="safety-step-number" aria-hidden="true">{{
                  stepIndex + 1
                }}</span
                ><span>{{ step }}</span>
              </li>
            </ol>
          </section>
          <div v-if="item.reply.followUp" class="safety-followup">
            <Icon name="MessageCircleHeart" :size="18" />
            <div>
              <strong>{{
                /[？?]/.test(item.reply.followUp)
                  ? "接下来想确认"
                  : "下一步提醒"
              }}</strong>
              <p>{{ item.reply.followUp }}</p>
            </div>
          </div>
          <div class="safety-answer-footer">
            <div class="safety-sources">
              <strong>参考资料</strong>
              <a
                v-for="source in item.reply.sources"
                :key="source.id"
                :href="source.url"
                target="_blank"
                rel="noopener noreferrer"
                >{{ source.title }} ↗</a
              >
            </div>
            <p class="source-note">
              {{ providerLabel(item.reply) }} · 资料核对
              {{ item.reply.reviewedAt }}
            </p>
            <p class="source-note">{{ item.reply.note }}</p>
          </div>
        </article>
      </div>
      <div v-if="busy" role="status" class="notice">正在处理您的问题…</div>
      <div v-if="error" role="alert" class="error notice">
        {{ error
        }}<button class="text-btn" :disabled="busy || voiceBusy" @click="retry">
          重试这次问题
        </button>
      </div>
      <form class="safety-composer" @submit.prevent="send()">
        <label for="safety-question">您的饮食安全或健康问题</label>
        <textarea
          id="safety-question"
          ref="composer"
          v-model="draft"
          aria-label="饮食安全问题"
          rows="4"
          maxlength="1600"
          :readonly="busy || voiceBusy"
          :placeholder="
            describingSymptoms
              ? '您可以补充什么时候开始、哪里不舒服、严重程度或实测体温。'
              : '您可以描述食物保存条件、饮食疑问或身体不适。'
          "
        ></textarea>
        <div class="between safety-compose-actions">
          <VoiceInput
            :disabled="busy"
            label="按住语音提问"
            @start="voiceStart"
            @draft="voiceDraft"
            @transcript="voiceDraft"
            @busy="voiceBusy = $event"
            @cancel="draft = voiceOriginal"
          /><button
            type="submit"
            class="primary"
            :disabled="busy || voiceBusy || !draft.trim()"
          >
            <Icon name="Send" :size="17" />{{ busy ? "正在回答…" : "发送问题" }}
          </button>
        </div>
      </form>
      <p class="source-note">
        语音会逐步填入上方文字，停止并校正后，您可以修改再发送。健康对话与食品安全问答分开处理。本页问答在离开页面时清空。
      </p>
      <button
        v-if="exchanges.length"
        class="text-btn safety-clear"
        :disabled="busy || voiceBusy"
        @click="clear"
      >
        清空本页问答
      </button>
    </section>
  </div>
</template>

<style scoped>
.safety-shortcuts {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}
.safety-shortcuts button {
  font-size: 12px;
  padding: 7px 10px;
}
.health-reply-text {
  border: 1px solid #e1e7db;
  border-radius: 12px;
  background: #f7faf6;
  color: #304d3e;
  margin-top: 10px;
  padding: 14px 16px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.safety-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  gap: 28px;
  align-items: start;
}
.safety-heading {
  margin-bottom: 18px;
}
.safety-heading h2 {
  margin: 0;
}
.safety-filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.safety-filters .chip.active {
  background: var(--green, #3d604b);
  color: #fff;
}
.safety-tip {
  margin-bottom: 16px;
  padding: 24px;
}
.safety-tip-top {
  display: flex;
  gap: 12px;
  align-items: center;
}
.safety-tip-icon,
.safety-chat-icon {
  display: grid;
  place-items: center;
  background: #eaf1df;
  color: #3d604b;
  border-radius: 14px;
  padding: 12px;
}
.safety-tip h3 {
  font-size: 19px;
  margin: 16px 0 10px;
}
.safety-tip p {
  line-height: 1.85;
  margin: 0;
  color: var(--muted, #7b8777);
}
.safety-tip-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 18px;
}
.safety-tip-footer a {
  font-size: 12px;
  color: #4d7059;
}
.safety-tip-footer button {
  display: flex;
  align-items: center;
  gap: 4px;
}
.safety-chat {
  padding: 28px;
  min-width: 0;
}
.safety-chat h2 {
  font-size: 22px;
  margin: 8px 0;
}
.safety-boundary {
  font-size: 13px;
  line-height: 1.8;
  background: #fff8e8;
  border-color: #eee0bb;
}
.safety-boundary p {
  margin: 8px 0 0;
}
.safety-boundary a {
  font-weight: 700;
  color: #925924;
}
.safety-empty {
  text-align: center;
  padding: 40px 10px 28px;
  color: #6f866a;
}
.safety-empty svg {
  margin: auto;
}
.safety-empty h3 {
  font-size: 17px;
  margin: 12px 0;
}
.safety-empty p {
  font-size: 14px;
  line-height: 1.9;
}
.safety-exchange {
  scroll-margin-top: 20px;
  margin: 24px 0;
}
.safety-question span {
  display: block;
  font-size: 11px;
  color: #73856a;
  margin-bottom: 4px;
}
.safety-composer {
  display: grid;
  gap: 10px;
  margin-top: 22px;
}
.safety-composer label {
  font-size: 13px;
  font-weight: 600;
}
.safety-composer textarea {
  width: 100%;
  resize: vertical;
  line-height: 1.8;
}
.safety-compose-actions {
  gap: 12px;
  flex-wrap: wrap;
}
.safety-compose-actions button {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
}
.safety-clear {
  margin-top: 8px;
}
.safety-chat .notice .text-btn {
  display: block;
  margin-top: 8px;
}
@media (max-width: 1100px) {
  .safety-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .safety-chat {
    grid-row: 1;
  }
  .safety-library {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
  }
  .safety-heading,
  .safety-filters,
  .safety-library > .source-note {
    grid-column: 1/-1;
    margin-bottom: 0;
  }
  .safety-tip {
    margin: 0;
  }
  .safety-empty {
    padding: 22px 10px;
  }
}
@media (max-width: 600px) {
  .safety-layout {
    gap: 24px;
  }
  .safety-chat,
  .safety-tip {
    padding: 20px;
  }
  .safety-library {
    grid-template-columns: minmax(0, 1fr);
  }
  .safety-chat h2 {
    font-size: 20px;
  }
  .safety-chat-icon {
    padding: 10px;
  }
  .safety-compose-actions button {
    flex: 1;
    padding: 12px 10px;
  }
  .safety-filters {
    gap: 7px;
  }
  .safety-filters button {
    font-size: 12px;
  }
  .safety-tip h3 {
    font-size: 18px;
  }
}

.safety-question {
  background: #edf2e7;
  border-radius: 16px 16px 4px 16px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.7;
  width: fit-content;
  max-width: min(100%, 420px);
  margin: 0 0 12px auto;
  padding: 9px 14px;
  color: #234638;
  font-size: 15px;
}
.safety-answer {
  padding: clamp(15px, 2vw, 24px);
  border: 1px solid #cbdcd0;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.8;
  border-left: 4px solid #3e7659;
  background: #fcfefa;
  color: #203d30;
  overflow-wrap: anywhere;
}
.safety-answer.professional {
  border-color: #dcd1ab;
  border-left-color: #947337;
}
.safety-answer.urgent {
  background: #fff2ed;
  border-color: #e8b5a5;
  border-left-color: #a84937;
}
.safety-answer-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.safety-answer-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: #e6f0e8;
  color: #295e46;
  flex: none;
}
.safety-answer-eyebrow {
  display: block;
  color: #506b59;
  font-size: 11px;
  font-weight: 650;
}
.safety-answer-header h3 {
  font-size: clamp(18px, 2vw, 22px);
  color: #173a2a;
  font-weight: 750;
  line-height: 1.35;
}
.safety-answer-copy {
  display: grid;
  gap: 9px;
}
.safety-answer-copy p {
  color: #263e31;
  font-size: 15px;
  line-height: 1.8;
}
.safety-steps {
  margin-top: 16px;
}
.safety-steps h4 {
  margin: 0 0 8px;
  color: #2d4b3b;
  font-size: 14px;
}
.safety-steps ol {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 6px;
}
.safety-steps li {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin: 0;
  padding: 7px 0;
  color: #263e31;
  line-height: 1.65;
}
.safety-step-number {
  display: grid;
  place-items: center;
  flex: none;
  width: 23px;
  height: 23px;
  border-radius: 7px;
  background: #e8f1e9;
  color: #285d47;
  font-size: 12px;
  font-weight: 750;
}
.safety-followup {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #eaf3ed;
  color: #285d47;
}
.safety-followup strong {
  font-size: 12px;
}
.safety-followup p {
  color: #234233;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.65;
}
.safety-answer-footer {
  border-top: 1px solid #e1e7db;
  margin-top: 15px;
  padding-top: 10px;
}
.safety-sources {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #3d604b;
}
.safety-sources a {
  text-decoration: underline;
  text-underline-offset: 2px;
}
.safety-answer-footer .source-note {
  margin: 6px 0 0;
  color: #596a5d;
  line-height: 1.55;
}
</style>
