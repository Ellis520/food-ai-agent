import { defineStore } from "pinia";
import { computed, onScopeDispose, ref, watch } from "vue";
import type {
  Meal,
  Profile,
  PlanItem,
  Message,
  NutritionRecipe,
} from "../api/types";
import { recordNutrition } from "../api/nutrition";
import { foods } from "../api/mock";
import { api, isDemo } from "../api";
import type { Health, AppState } from "../api/types";
export function dateKey(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
const KEY = "foodmoment-v1";
const DEFAULT_PROFILE: Profile = {
  name: "小食同学",
  campus: "青禾大学 · 示例校园",
  budget: 45,
  goal: "均衡饮食",
  preference: "不限",
  allergies: "",
  kcal: 2000,
};
export const useApp = defineStore("app", () => {
  const today = ref(dateKey());
  let midnightTimer: ReturnType<typeof setTimeout>;
  function refreshToday() {
    clearTimeout(midnightTimer);
    const now = new Date();
    today.value = dateKey(now);
    const midnight = new Date(now);
    midnight.setHours(24, 0, 0, 0);
    midnightTimer = setTimeout(
      refreshToday,
      midnight.getTime() - now.getTime(),
    );
  }
  refreshToday();
  document.addEventListener("visibilitychange", refreshToday);
  window.addEventListener("pageshow", refreshToday);
  onScopeDispose(() => {
    clearTimeout(midnightTimer);
    document.removeEventListener("visibilitychange", refreshToday);
    window.removeEventListener("pageshow", refreshToday);
  });
  const meals = ref<Meal[]>([
    {
      id: "seed-1",
      date: today.value,
      slot: "早餐",
      foodId: "oats",
      grams: 250,
      cost: 8,
    },
    {
      id: "seed-2",
      date: today.value,
      slot: "午餐",
      foodId: "chicken",
      grams: 350,
      cost: 18,
    },
  ]);
  const profile = ref<Profile>({ ...DEFAULT_PROFILE });
  const needsProfileSetup = ref(true);
  const plans = ref<Record<string, PlanItem[]>>({});
  const favorites = ref<string[]>([]);
  const messages = ref<Message[]>([]);
  const nutritionRecipes = ref<NutritionRecipe[]>([]);
  const toast = ref("");
  const storageError = ref("");
  const ready = ref(isDemo),
    syncStatus = ref(isDemo ? "本地演示" : "正在连接数据库"),
    services = ref<Health>(),
    connectionError = ref("");
  const revision = ref(0);
  let hydrating = false,
    saving = false,
    dirty = false,
    locked = false,
    pendingSync: Promise<void> | undefined;
  function snapshot(): AppState {
    return JSON.parse(
      JSON.stringify({
        profile: profile.value,
        meals: meals.value,
        plans: plans.value,
        favorites: favorites.value,
        messages: messages.value.slice(-500),
        nutritionRecipes: nutritionRecipes.value,
      }),
    );
  }
  async function refreshServices() {
    if (!isDemo)
      try {
        services.value = await api.health();
      } catch (e) {
        notify((e as Error).message);
      }
  }
  async function initialize() {
    if (isDemo) return;
    ready.value = false;
    connectionError.value = "";
    hydrating = true;
    try {
      const result = await api.state();
      revision.value = result.revision;
      profile.value = result.state.profile;
      needsProfileSetup.value = profile.value.setupComplete !== true;
      meals.value = result.state.meals;
      plans.value = result.state.plans;
      favorites.value = result.state.favorites;
      messages.value = result.state.messages;
      nutritionRecipes.value = result.state.nutritionRecipes || [];
      locked = false;
      dirty = false;
      syncStatus.value = "已同步至 SQLite";
      hydrating = false;
      ready.value = true;
      await refreshServices();
    } catch (e) {
      connectionError.value = (e as Error).message;
    } finally {
      hydrating = false;
    }
  }
  function sync(): Promise<void> {
    if (!pendingSync)
      pendingSync = savePendingState().finally(() => {
        pendingSync = undefined;
      });
    return pendingSync.then(() => {
      if (!isDemo && ready.value && dirty && !locked && !storageError.value)
        return sync();
    });
  }
  async function savePendingState() {
    if (isDemo || !ready.value || saving || locked || !dirty) return;
    saving = true;
    syncStatus.value = "正在保存…";
    try {
      while (dirty) {
        dirty = false;
        const result = await api.saveState(snapshot(), revision.value);
        revision.value = result.revision;
      }
      storageError.value = "";
      syncStatus.value = "已同步至 SQLite";
    } catch (e) {
      dirty = true;
      storageError.value = (e as Error).message;
      locked = storageError.value.startsWith("409");
      syncStatus.value = "保存失败，数据仍在当前页面";
    } finally {
      saving = false;
    }
  }
  if (isDemo)
    try {
      const data = JSON.parse(localStorage.getItem(KEY) || "null");
      if (data?.version === 1) {
        if (Array.isArray(data.meals))
          meals.value = data.meals.filter(
            (m: Meal) =>
              (foods.some((f) => f.id === m.foodId) ||
                (!m.foodId &&
                  typeof m.dishName === "string" &&
                  m.dishName.trim())) &&
              Number.isFinite(m.grams) &&
              m.grams > 0,
          );
        if (data.profile) profile.value = { ...profile.value, ...data.profile };
        plans.value = data.plans || {};
        favorites.value = data.favorites || [];
        messages.value = data.messages || [];
      }
    } catch {
      storageError.value = "本地数据无法读取，已使用示例初始数据。";
    }
  if (isDemo)
    try {
      const saved = JSON.parse(localStorage.getItem(KEY) || "null");
      if (saved?.version === 1 && Array.isArray(saved.nutritionRecipes))
        nutritionRecipes.value = saved.nutritionRecipes.slice(0, 30);
    } catch {}
  if (isDemo) needsProfileSetup.value = profile.value.setupComplete !== true;
  watch(
    [meals, profile, plans, favorites, messages, nutritionRecipes],
    () => {
      if (hydrating) return;
      if (!isDemo) {
        dirty = true;
        void sync();
        return;
      }
      try {
        localStorage.setItem(
          KEY,
          JSON.stringify({ version: 1, ...snapshot() }),
        );
        storageError.value = "";
      } catch {
        storageError.value = "浏览器无法保存数据，请检查存储空间或隐私设置。";
      }
    },
    { deep: true, flush: "sync" },
  );
  async function saveProfile(value: Profile) {
    profile.value = { ...value, setupComplete: true };
    await sync();
    if (storageError.value) return false;
    needsProfileSetup.value = false;
    return true;
  }
  const todayMeals = computed(() =>
    meals.value.filter((m) => m.date === today.value),
  );
  const todayNutrition = computed(() =>
    todayMeals.value.map((meal) =>
      recordNutrition(
        meal,
        foods.find((food) => food.id === meal.foodId),
      ),
    ),
  );
  const calories = computed(() =>
    Math.round(
      todayNutrition.value.reduce(
        (sum, item) => sum + ((item.per100.kcal ?? 0) * item.grams) / 100,
        0,
      ),
    ),
  );
  const caloriesIncomplete = computed(() =>
    todayNutrition.value.some((item) => item.per100.kcal === null),
  );
  const spent = computed(() =>
    todayMeals.value.reduce((s, m) => s + m.cost, 0),
  );
  let timer: ReturnType<typeof setTimeout>;
  function notify(text: string) {
    toast.value = text;
    clearTimeout(timer);
    timer = setTimeout(() => (toast.value = ""), 3500);
  }
  function toggleFavorite(id: string) {
    favorites.value = favorites.value.includes(id)
      ? favorites.value.filter((x) => x !== id)
      : [...favorites.value, id];
  }
  function clearData() {
    meals.value = [];
    plans.value = {};
    favorites.value = [];
    messages.value = [];
    nutritionRecipes.value = [];
    notify("饮食记录、计划、收藏、对话与营养搭配已清空");
  }
  return {
    today,
    meals,
    profile,
    needsProfileSetup,
    saveProfile,
    plans,
    favorites,
    messages,
    nutritionRecipes,
    toast,
    storageError,
    todayMeals,
    calories,
    caloriesIncomplete,
    spent,
    notify,
    toggleFavorite,
    clearData,
    ready,
    connectionError,
    syncStatus,
    services,
    initialize,
    sync,
    refreshServices,
    revision,
  };
});
