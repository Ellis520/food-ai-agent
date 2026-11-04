import { buildPlan, restaurants } from "./mock";
import { highestRatedFirst } from "./restaurantActions";
import type {
  Envelope,
  Message,
  PlanItem,
  Restaurant,
  AppState,
  Health,
  Meal,
  AnalysisDays,
  HabitAnalysis,
  TakeawayIdeas,
  TakeawayRequest,
  ChatHealthOptions,
  HealthMessage,
} from "./types";
import { demoHabitAnalysis } from "./habits";
import { demoTakeawayIdeas } from "./takeawayIdeas";
import {
  demoSafetyReply,
  type SafetyReply,
  type SafetyTurn,
} from "./foodSafety";
export const isDemo = import.meta.env.VITE_DATA_MODE !== "api";
export const base = (import.meta.env.VITE_API_BASE_URL || "/api").replace(
  /\/$/,
  "",
);

function demoHealthReply(
  text: string,
  options?: ChatHealthOptions,
): HealthMessage | undefined {
  const safety = demoSafetyReply(text);
  if (safety.kind === "urgent")
    return {
      id: crypto.randomUUID(),
      role: "assistant",
      kind: "health",
      healthStatus: "urgent",
      text: safety.answer,
      source: "本地急救提示",
    };
  if (
    options ||
    /身体不适|不(?:太|大|怎么)?舒服|发烧|发热|头痛|腹痛|肚子疼|腹泻|药店|医院|什么药/.test(
      text,
    )
  ) {
    return {
      id: crypto.randomUUID(),
      role: "assistant",
      kind: "health",
      healthStatus: "clarify",
      source: "演示模式",
      text: "连接食刻 AI 后端后，您可以查看症状的初步判断，也可以查询附近医院和药店。",
    };
  }
}
export async function request<T>(
  path: string,
  body?: unknown,
  method?: string,
): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 60000);
  try {
    const response = await fetch(base + path, {
      method: method || (body ? "POST" : "GET"),
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    }).catch(() => {
      throw new Error(
        "无法连接后端。请检查网络后重试，或从试用短链接重新登录。",
      );
    });
    if (!response.ok) {
      const detail = await response.json().catch(() => ({}));
      throw new Error(
        response.status +
          "：" +
          (typeof detail.detail === "string"
            ? detail.detail
            : "接口请求失败，请检查输入或服务配置。"),
      );
    }
    const result: Envelope<T> = await response.json().catch(() => {
      throw new Error(
        "接口未返回有效数据。若正在试用，请从试用短链接重新登录。",
      );
    });
    if (
      !result ||
      typeof result !== "object" ||
      !("data" in result) ||
      typeof result.source !== "string"
    )
      throw new Error("接口响应格式不正确：需要 data 与 source。");
    return result.data;
  } catch (error) {
    if (controller.signal.aborted)
      throw new Error("连接后端超时，请重新连接。");
    throw error;
  } finally {
    clearTimeout(timer);
  }
}
export const api = {
  async health(): Promise<Health> {
    return request("/health");
  },
  async state(): Promise<{ revision: number; state: AppState }> {
    return request("/state");
  },
  async saveState(
    state: AppState,
    revision: number,
  ): Promise<{ revision: number }> {
    return request("/state", { state, revision }, "PUT");
  },
  async chat(
    text: string,
    history: Message[],
    health?: ChatHealthOptions,
  ): Promise<Message> {
    if (!isDemo)
      return request("/chat", {
        message: text,
        history: history.slice(-20),
        ...health,
      });
    const healthReply = demoHealthReply(text, health);
    if (healthReply) return healthReply;
    const kind = /附近|商家|餐厅|店铺|找.*店|地图/.test(text)
      ? "restaurant"
      : /营养|热量/.test(text)
        ? "nutrition"
        : "plan";
    if (kind === "restaurant")
      return {
        id: crypto.randomUUID(),
        role: "assistant",
        text: "这里是示例校园的商家卡片。",
        kind,
        source: "规则演示 · 未调用千问",
        restaurantSearch: {
          keyword: "",
          places: [...restaurants].sort(highestRatedFirst).slice(0, 3),
        },
      };
    return {
      id: crypto.randomUUID(),
      role: "assistant",
      text:
        kind === "nutrition"
          ? "营养分析可根据示例食物每 100g 的数值与实际克重计算。试试调整份量，查看变化。"
          : `收到「${text}」。这是规则驱动的演示回复。饮食规划页可以按预算生成示例三餐，并保存到本地计划。`,
      kind,
      source: "规则演示 · 未调用千问",
    };
  },
  async plan(
    budget: number,
    preference: string,
    day: number,
    taste = "",
    cuisine = "",
    restrictions = "",
  ): Promise<PlanItem[]> {
    return isDemo
      ? buildPlan(budget, preference, day)
      : request("/diet/plan", {
          budget,
          preference,
          day,
          taste,
          cuisine,
          restrictions,
          personalized: true,
        });
  },
  async nearby(): Promise<Restaurant[]> {
    return isDemo ? restaurants : request("/food/nearby");
  },
  async searchRestaurants(
    keyword: string,
    radius: number,
    page: number,
  ): Promise<{ restaurants: Restaurant[]; hasMore: boolean }> {
    if (isDemo)
      return {
        restaurants: restaurants.filter((place) =>
          place.name.includes(keyword),
        ),
        hasMore: false,
      };
    const query = new URLSearchParams({
      keyword,
      radius: String(radius),
      page: String(page),
    });
    return request("/food/search?" + query);
  },
  async safetyAsk(
    message: string,
    history: SafetyTurn[],
    healthHistory: Message[],
    options?: ChatHealthOptions,
  ): Promise<SafetyReply | HealthMessage> {
    if (isDemo) {
      // Keep ordinary food-safety and medication-reference questions in the demo catalog.
      const health = demoHealthReply(message, options);
      return health &&
        (!/什么药/.test(message) ||
          options ||
          healthHistory.length ||
          health.healthStatus === "urgent")
        ? health
        : demoSafetyReply(message);
    }
    return request("/safety/ask", {
      message,
      history: history.slice(-8),
      healthHistory: healthHistory.slice(-8),
      ...options,
    });
  },
  async takeawayIdeas(body: TakeawayRequest): Promise<TakeawayIdeas> {
    return isDemo
      ? demoTakeawayIdeas(body)
      : request("/takeaway/recommend", body);
  },
  async analyze(
    days: AnalysisDays,
    revision: number,
    meals: Meal[],
    end: string,
  ): Promise<HabitAnalysis> {
    return isDemo
      ? demoHabitAnalysis(meals, days, end)
      : request("/diet/analysis", { days, revision });
  },
  async translate(text: string): Promise<string> {
    if (!isDemo) return request("/language/translate", { text, target: "en" });
    const dictionary: Record<string, string> = {
      "请问这道菜有花生吗？": "Does this dish contain peanuts?",
      "请少放辣椒，谢谢。": "Less chili, please. Thank you.",
      "我想要一份素食。": "I would like a vegetarian meal.",
    };
    return (
      dictionary[text] ||
      "演示词库暂不包含这句话。请选择下方常用表达，或配置后端翻译服务。"
    );
  },
};
