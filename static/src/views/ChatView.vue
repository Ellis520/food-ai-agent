<script setup lang="ts">
import { computed, ref, nextTick } from "vue";
import { useApp } from "../stores/app";
import { api, isDemo } from "../api";
import VoiceInput from "../components/VoiceInput.vue";
import { foods } from "../api/mock";
import type {
  ChatHealthOptions,
  ChatMeal,
  ChatPlace,
  ChatRestaurantSearch,
  RestaurantMode,
} from "../api/types";
import {
  placeActionReply,
  restaurantFollowUp,
  walkingUrl,
} from "../api/restaurantActions";
import ChatRestaurantCards from "../components/ChatRestaurantCards.vue";
import ChatMealCards from "../components/ChatMealCards.vue";
import ChatHealthActions from "../components/ChatHealthActions.vue";
import ChatHealthAssessment from "../components/ChatHealthAssessment.vue";
import Icon from "../components/Icon.vue";
import PageHeader from "../components/PageHeader.vue";
const app = useApp(),
  input = ref(""),
  busy = ref(false),
  error = ref(""),
  voiceBusy = ref(false),
  voiceOriginal = ref(""),
  list = ref<HTMLElement>();
const composerInput = ref<HTMLInputElement>();
const describingSymptoms = ref(false);
const latestAssistant = computed(() =>
  app.messages.filter((message) => message.role === "assistant").at(-1),
);
const userInitial = computed(
  () => Array.from(app.profile.name.trim() || "食")[0],
);
let failedLookup: ChatHealthOptions | undefined;
function voiceStart() {
  voiceOriginal.value = input.value;
}
function voiceDraft(text: string) {
  input.value = (voiceOriginal.value + text).slice(0, 2000);
}

async function scrollToReply() {
  await nextTick();
  const panel = list.value;
  if (!panel) return;
  const reply = panel.querySelector(".message.assistant:last-of-type");
  const start =
    reply && reply.getBoundingClientRect().height > panel.clientHeight
      ? panel.scrollTop +
        reply.getBoundingClientRect().top -
        panel.getBoundingClientRect().top
      : panel.scrollHeight;
  panel.scrollTo({ top: start, behavior: "smooth" });
}

function chooseRestaurant(
  search: ChatRestaurantSearch,
  mode: RestaurantMode,
  place: ChatPlace,
) {
  if (busy.value || voiceBusy.value) return;
  const label =
    mode === "takeaway"
      ? "点外卖"
      : search.category === "pharmacy"
        ? "到店买药"
        : "到店吃";
  error.value = "";
  app.messages.push({
    id: crypto.randomUUID(),
    role: "user",
    text: `${label} · ${place.name}`,
  });
  app.messages.push(placeActionReply(search, mode, place));
  void scrollToReply();
}

function chooseMeal(meal: ChatMeal) {
  if (busy.value || voiceBusy.value) return;
  error.value = "";
  recordUserMessage(`我选「${meal.option}」`);
  app.messages.push({
    id: crypto.randomUUID(),
    role: "assistant",
    text: `需要我帮您查找校园附近与「${meal.option}」相关的店铺吗？`,
    selectedMeal: { ...meal, name: meal.option, detail: "" },
  });
  void scrollToReply();
}

async function findMealShops(meal: ChatMeal) {
  if (busy.value || voiceBusy.value) return;
  error.value = "";
  recordUserMessage(`找店铺 · ${meal.option}`);
  busy.value = true;
  try {
    const keyword = meal.option;
    const { restaurants } = await api.searchRestaurants(keyword, 5000, 1);
    const places: ChatPlace[] = Array.from(
      new Map(
        restaurants
          .filter((place) => walkingUrl(place))
          .map((place) => [place.id, place]),
      ).values(),
    )
      .slice(0, 5)
      .map(
        ({
          id,
          name,
          address,
          hours,
          price,
          rating,
          distance,
          lng,
          lat,
          source,
        }) => ({
          id,
          name,
          address,
          hours,
          price,
          rating,
          distance,
          lng,
          lat,
          source,
        }),
      );
    if (!places.length) {
      app.messages.push({
        id: crypto.randomUUID(),
        role: "assistant",
        text: isDemo
          ? "演示模式暂不提供真实店铺导航。"
          : "抱歉，暂时没查到有可用导航的相关店铺。您可以试试美团外卖搜索。",
      });
      return;
    }
    const text =
      "找到了校园附近的相关店铺。您可以选一家导航，菜品供应请向店铺确认。";
    app.messages.push({
      id: crypto.randomUUID(),
      role: "assistant",
      text,
      kind: "restaurant",
      restaurantMode: "dine-in",
      restaurantSearch: {
        keyword,
        places,
        category: "restaurant",
        origin: "campus",
      },
    });
  } catch {
    app.messages.push({
      id: crypto.randomUUID(),
      role: "assistant",
      text: "附近店铺暂时查不到。您可以稍后再试，也可以在上方卡片里点外卖。",
    });
  } finally {
    busy.value = false;
    await scrollToReply();
  }
}

function describeSymptoms() {
  describingSymptoms.value = true;
  composerInput.value?.focus();
}

function lookupHealth(options: ChatHealthOptions) {
  const subject = options.healthLookup === "hospital" ? "医院" : "药店";
  return send(`查附近${subject}`, options);
}

function recordUserMessage(text: string) {
  const messages = app.messages;
  let first = messages.length;
  while (
    first > 0 &&
    messages[first - 1].role === "user" &&
    messages[first - 1].text === text
  )
    first--;
  if (first === messages.length) {
    messages.push({ id: crypto.randomUUID(), role: "user", text });
  } else if (first + 1 < messages.length) {
    // Older clients added a new bubble on every failed retry.
    messages.splice(first + 1);
  }
}

function clearChat() {
  if (busy.value) return;
  app.messages = [];
  error.value = "";
  failedLookup = undefined;
}

async function send(value = input.value, health?: ChatHealthOptions) {
  if (!value.trim() || busy.value || voiceBusy.value) return;
  const latest = latestAssistant.value?.restaurantSearch;
  const followUp =
    !health &&
    latest &&
    latest.category !== "hospital" &&
    restaurantFollowUp(value, latest);
  input.value = "";
  error.value = "";
  failedLookup = undefined;
  recordUserMessage(value);
  if (latest && followUp) {
    app.messages.push(placeActionReply(latest, followUp.mode, followUp.place));
    await scrollToReply();
    return;
  }
  busy.value = true;
  try {
    const reply = await api.chat(value, app.messages, health);
    const search = reply.restaurantSearch;
    const mode =
      !health &&
      search &&
      search.category !== "hospital" &&
      restaurantFollowUp(value, search);
    app.messages.push(
      search && mode ? placeActionReply(search, mode.mode, mode.place) : reply,
    );
    describingSymptoms.value = false;
  } catch (problem) {
    failedLookup = health;
    error.value = (problem as Error).message;
  } finally {
    busy.value = false;
    await scrollToReply();
  }
}
</script>
<template>
  <PageHeader
    title="AI 聊天"
    description="问下一餐吃什么，或让它找附近的店。"
  />
  <section class="chat-layout">
    <div class="chat-main panel">
      <div class="chat-heading">
        <span class="brand-mark"><Icon name="Sprout" /></span>
        <div>
          <strong>食刻小助手</strong>
          <p>
            {{
              isDemo
                ? "规则演示"
                : app.services?.qwen
                  ? "千问已配置 · 可发起真实请求"
                  : "千问尚未配置"
            }}
          </p>
        </div>
        <button class="text-btn" :disabled="busy" @click="clearChat">
          清空对话
        </button>
      </div>
      <div ref="list" class="message-list" aria-live="polite">
        <div class="welcome-message">
          <h2>今天吃什么</h2>
          <p>可以问一餐怎么吃，或找校园附近的店。</p>
          <div class="prompt-grid">
            <button
              v-for="q in [
                '预算 20 元，午餐怎么吃？',
                '帮我找校园附近的餐厅',
                '帮我了解今天的营养摄入',
              ]"
              :key="q"
              :disabled="busy || voiceBusy"
              @click="send(q)"
            >
              {{ q }}
            </button>
          </div>
        </div>
        <article
          v-for="message in app.messages"
          :key="message.id"
          class="message"
          :class="[
            message.role,
            { 'health-message': message.kind === 'health' },
          ]"
        >
          <span
            v-if="message.role === 'user'"
            class="avatar message-avatar"
            aria-hidden="true"
            ><img
              v-if="app.profile.avatar"
              :src="app.profile.avatar"
              alt=""
            /><template v-else>{{ userInitial }}</template></span
          >
          <span v-else class="brand-mark message-bot-avatar" aria-hidden="true"
            ><Icon name="Sprout" :size="16"
          /></span>
          <div class="message-main">
            <span v-if="message.role === 'assistant'" class="message-label"
              >食刻小助手</span
            >
            <ChatHealthAssessment
            v-if="
              message.role === 'assistant' &&
              message.kind === 'health' &&
              (message.healthStatus === 'clarify' ||
                message.text.startsWith('初步判断\n'))
            "
            :text="message.text"
            :clarify="message.healthStatus === 'clarify'"
          />
          <p
            v-else-if="
              message.role === 'assistant' &&
              message.text &&
              !message.selectedMeal
            "
            class="assistant-copy"
          >
            <template
              v-for="(part, index) in message.text.split(/\*\*([^*\n]+)\*\*/g)"
              :key="index"
              ><strong v-if="index % 2">{{ part }}</strong
              ><template v-else>{{ part }}</template></template
            >
          </p>
          <p v-else-if="message.role === 'user'">{{ message.text }}</p>
          <ChatMealCards
            v-if="message.mealSuggestions?.length || message.selectedMeal"
            :meals="message.mealSuggestions"
            :selected="message.selectedMeal"
            :disabled="busy || voiceBusy"
            @choose="chooseMeal"
            @find-shops="findMealShops"
            ><RouterLink
              v-if="message.planItems"
              :to="{ path: '/plan', query: { fromChat: message.id } }"
              class="text-btn meal-plan-link"
              >查看三餐计划</RouterLink
            ></ChatMealCards
          ><ChatRestaurantCards
            v-else-if="message.restaurantSearch?.places.length"
            :search="message.restaurantSearch"
            :mode="message.restaurantMode"
            :disabled="busy || voiceBusy"
            @choose="
              (mode, place) =>
                chooseRestaurant(message.restaurantSearch!, mode, place)
            "
          /><ChatHealthActions
            v-else-if="
              message.kind === 'health' &&
              message.healthStatus &&
              message.id === latestAssistant?.id
            "
            :status="message.healthStatus"
            :topic="message.healthTopic"
            :disabled="busy || voiceBusy"
            :demo="isDemo"
            @lookup="lookupHealth"
            @describe="describeSymptoms"
          />
          <div
            v-else-if="message.kind && message.kind !== 'health'"
            class="structured-card"
          >
            <span class="tag">{{ message.source || "后端结构化回复" }}</span>
            <h3>
              {{
                message.kind === "restaurant"
                  ? "校园好味探索"
                  : message.kind === "nutrition"
                    ? "每一口都有数"
                    : "我的三餐灵感"
              }}
            </h3>
            <div v-if="message.planItems" class="plan-summary">
              <p class="small">{{ message.planDate }} · 待确认</p>
              <div
                v-for="meal in message.planItems"
                :key="meal.slot"
                class="between"
              >
                <strong>{{ meal.slot }}</strong
                ><span
                  >{{ foods.find((f) => f.id === meal.foodId)?.name }} ·
                  {{ meal.grams }}g</span
                >
              </div>
            </div>
            <p>
              {{
                message.kind === "restaurant"
                  ? "在校园美食地图继续筛选商家"
                  : message.kind === "nutrition"
                    ? "克重换算 · 六项营养数值"
                    : "预算筛选 · 三餐计划 · 本地保存"
              }}
            </p>
            <RouterLink
              :to="
                message.kind === 'restaurant'
                  ? '/map'
                  : message.kind === 'nutrition'
                    ? '/nutrition'
                    : {
                        path: '/plan',
                        query: message.planItems
                          ? { fromChat: message.id }
                          : {},
                      }
              "
              class="text-btn"
              >去看看</RouterLink
            >
          </div>
          </div>
        </article>
        <p v-if="busy" class="notice">正在等待回复…</p>
      </div>
      <p v-if="error" class="notice error" role="alert">
        {{ error }}
        <button
          @click="
            send(
              app.messages.filter((m) => m.role === 'user').at(-1)?.text,
              failedLookup,
            )
          "
          class="text-btn"
        >
          重试
        </button>
      </p>
      <form class="chat-input" @submit.prevent="send()">
        <VoiceInput
          :disabled="busy"
          label="按住语音输入"
          @start="voiceStart"
          @draft="voiceDraft"
          @transcript="voiceDraft"
          @busy="voiceBusy = $event"
          @cancel="input = voiceOriginal"
        /><input
          ref="composerInput"
          v-model="input"
          :readonly="voiceBusy"
          aria-label="聊天消息"
          :placeholder="
            describingSymptoms
              ? '年龄、主要症状、多久了、体温、已用药…'
              : '说说你的饮食小想法…'
          "
          maxlength="2000"
        /><button
          class="primary compact"
          :disabled="busy || voiceBusy || !input.trim()"
          aria-label="发送消息"
        >
          <Icon name="ArrowUp" />
        </button>
      </form>
      <p class="source-note">
        {{
          isDemo
            ? "演示回复由关键词规则生成，不代表真实 AI 或个性化营养建议。"
            : "千问回复来自真实接口；服务失败会报错。语音校正结果先填入输入框，由你确认发送。"
        }}
      </p>
    </div>
    <aside class="chat-aside panel">
      <h2>也可以去</h2>
      <RouterLink
        v-for="item in [
          ['/plan', 'CalendarDays', '规划三餐', '排出早餐、午餐和晚餐'],
          ['/map', 'MapPinned', '校园地图', '找附近的店'],
          ['/language', 'ShieldCheck', '饮食小贴士', '问安全和药店'],
        ]"
        :key="item[0]"
        :to="item[0]"
        class="aside-link"
        ><Icon :name="item[1]" /><span
          ><strong>{{ item[2] }}</strong
          ><small>{{ item[3] }}</small></span
        ></RouterLink>
      <div class="sage notice">
        🌱 小提示：生成的建议需要你确认。任何饮食禁忌，请务必向商家再次核实。
      </div>
    </aside>
  </section>
</template>

<style scoped>
.health-message {
  max-width: min(100%, 740px);
}
.message-main > .assistant-copy {
  font-size: 14px;
  line-height: 1.8;
  color: #354f3f;
}
.assistant-copy strong {
  color: #233d30;
  font-weight: 750;
}
.plan-summary {
  margin: 12px 0;
  padding: 12px;
  background: #f2f5e9;
  border-radius: 8px;
}
.plan-summary .between {
  font-size: 11px;
  padding: 6px 0;
  gap: 16px;
}
.plan-summary span {
  color: #7e8f6e;
}
.meal-plan-link {
  display: inline-block;
  margin-top: 12px;
}
</style>
