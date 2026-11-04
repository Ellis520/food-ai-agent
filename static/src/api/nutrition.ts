import type {
  Food,
  Meal,
  NutrientKey,
  NutrientValues,
  NutritionEntry,
} from "./types";
export const nutrients = [
  { key: "kcal", label: "热量", unit: "kcal", max: 1000 },
  { key: "protein", label: "蛋白质", unit: "g", max: 100 },
  { key: "carbs", label: "碳水化合物", unit: "g", max: 100 },
  { key: "fat", label: "脂肪", unit: "g", max: 100 },
  { key: "fiber", label: "膳食纤维", unit: "g", max: 100 },
  { key: "sodium", label: "钠", unit: "mg", max: 100000 },
] as const;
export function validateEntry(entry: NutritionEntry) {
  if (!entry.name.trim() || entry.name.length > 60)
    throw new Error("食物名称应为1–60字");
  if (!Number.isFinite(entry.grams) || entry.grams <= 0 || entry.grams > 2000)
    throw new Error("份量应大于0且不超过2000g");
  if (!["sample", "label"].includes(entry.source))
    throw new Error("请选择明确的数据来源");
  let known = 0;
  for (const nutrient of nutrients) {
    const value = entry.per100[nutrient.key];
    if (value === null) continue;
    if (!Number.isFinite(value) || value < 0 || value > nutrient.max)
      throw new Error(
        `${nutrient.label}每100g数值应在0–${nutrient.max}${nutrient.unit}之间`,
      );
    known++;
  }
  if (!known) throw new Error("至少填写一项营养数值；未标注项目请留空");
}
export function sampleEntry(
  food: Food,
  grams: number,
  id: string = crypto.randomUUID(),
): NutritionEntry {
  return {
    id,
    name: food.name,
    grams,
    source: "sample",
    foodId: food.id,
    per100: Object.fromEntries(
      nutrients.map((n) => [n.key, food[n.key]]),
    ) as NutrientValues,
  };
}
export type NutritionAnalysisEntry = (
  | NutritionEntry
  | (Omit<NutritionEntry, "source"> & { source: "unknown" | "estimate" })
) & { aiKeys?: NutrientKey[] };

export type NutritionEstimate = Partial<Record<NutrientKey, number>>;

export function withNutritionEstimate(
  item: NutritionAnalysisEntry,
  estimate?: NutritionEstimate,
): NutritionAnalysisEntry {
  if (!estimate) return item;
  const per100 = { ...item.per100 },
    aiKeys: NutrientKey[] = [];
  for (const { key, max } of nutrients) {
    const value = estimate[key];
    if (
      per100[key] === null &&
      typeof value === "number" &&
      Number.isFinite(value) &&
      value >= 0 &&
      value <= max
    ) {
      per100[key] = value;
      aiKeys.push(key);
    }
  }
  return aiKeys.length ? { ...item, per100, aiKeys } : item;
}

type CategoryReference = {
  example: string;
  grams?: number;
  values: Record<NutrientKey, number>;
};

function reference(
  example: string,
  grams: number | undefined,
  kcal: number,
  protein: number,
  carbs: number,
  fat: number,
  fiber: number,
  sodium: number,
): CategoryReference {
  return {
    example,
    grams,
    values: { kcal, protein, carbs, fat, fiber, sodium },
  };
}

// Estimates from the supplied meal references. Grams are left absent for unweighed servings.
const breakfastReferences = new Map([
  [
    "粉面",
    reference("带肉和汤的牛肉面或肉丝面", 400, 500, 20, 70, 15, 3, 1800),
  ],
  [
    "肠粉",
    reference(
      "加蛋或加肉肠粉（200–250 g 的中间值）",
      225,
      300,
      10,
      40,
      10,
      1,
      800,
    ),
  ],
  ["包子", reference("两个猪肉大包", 150, 400, 12, 50, 15, 2, 600)],
  ["饼", reference("手抓饼、鸡蛋灌饼或葱油饼", 150, 400, 10, 45, 20, 1, 700)],
  ["粥", reference("白米粥", 250, 115, 2.8, 24.8, 0.8, 0.5, 5)],
  [
    "轻食",
    reference("鸡胸肉沙拉（300–350 g 的中间值）", 325, 350, 25, 30, 15, 5, 500),
  ],
]);
const mainMealReferences = new Map([
  [
    "快餐",
    reference("一荤两素盒饭或盖浇饭", undefined, 700, 25, 90, 25, 3, 1200),
  ],
  [
    "粉面",
    reference(
      "牛肉面或肉丝粉（重量沿用早餐粉面参考）",
      400,
      500,
      20,
      70,
      15,
      2,
      1800,
    ),
  ],
  [
    "饺子",
    reference("约 15 个猪肉白菜饺子，含蘸料", 300, 550, 20, 55, 25, 2, 1000),
  ],
  [
    "西餐",
    reference("牛排配意面或薯条和配菜", undefined, 800, 35, 60, 45, 4, 1500),
  ],
  [
    "火锅",
    reference("常规肉菜搭配，不喝汤", undefined, 600, 30, 30, 40, 6, 2000),
  ],
  [
    "烧烤",
    reference(
      "肉串和烤蔬菜，含刷油和调料",
      undefined,
      700,
      30,
      30,
      50,
      3,
      1800,
    ),
  ],
]);

export function categoryReference(slot: string, name: string) {
  const references =
    slot === "早餐"
      ? breakfastReferences
      : slot === "午餐" || slot === "晚餐"
        ? mainMealReferences
        : undefined;
  return references?.get(name.normalize("NFKC").trim());
}

export function recordNutrition(
  meal: Meal,
  food?: Food,
): NutritionAnalysisEntry {
  if (food) return sampleEntry(food, meal.grams, meal.id);
  const name = meal.dishName || "未知食物";
  const ref = categoryReference(meal.slot, name);
  if (!ref?.grams) return unknownEntry(meal.id, name, meal.grams);
  const referenceGrams = ref.grams;
  const per100 = Object.fromEntries(
    nutrients.map(({ key }) => [key, (ref.values[key] * 100) / referenceGrams]),
  ) as NutrientValues;
  return { id: meal.id, name, grams: meal.grams, source: "estimate", per100 };
}

const sourceLabels = {
  sample: "人工示例",
  label: "自填营养表",
  estimate: "分类营养估算",
  unknown: "营养数据未提供",
} as const;
export function nutritionSourceLabel(source: NutritionAnalysisEntry["source"]) {
  return sourceLabels[source];
}

export function unknownEntry(
  id: string,
  name: string,
  grams: number,
): NutritionAnalysisEntry {
  return {
    id,
    name,
    grams,
    source: "unknown",
    per100: Object.fromEntries(
      nutrients.map((n) => [n.key, null]),
    ) as NutrientValues,
  };
}
export function summarizeNutrition(
  items: NutritionAnalysisEntry[],
  recordMode = false,
) {
  if (!recordMode && items.length > 20) throw new Error("每份搭配最多20项食物");
  for (const item of items) {
    if (item.source === "unknown" && recordMode) {
      if (item.aiKeys?.length) validateEntry({ ...item, source: "sample" });
      continue;
    }
    if (item.source === "estimate" && recordMode) {
      validateEntry({ ...item, source: "sample" });
      continue;
    }
    if (item.source !== "sample" && item.source !== "label")
      throw new Error("请填写营养数据后添加到搭配");
    validateEntry({ ...item, source: item.source });
  }
  const values = {} as NutrientValues,
    missing = {} as Record<NutrientKey, number>;
  for (const { key } of nutrients) {
    const known = items.filter((item) => item.per100[key] !== null);
    values[key] = known.length
      ? Math.round(
          known.reduce(
            (sum, item) => sum + (item.per100[key]! * item.grams) / 100,
            0,
          ) * 10,
        ) / 10
      : null;
    missing[key] = items.length - known.length;
  }
  const aiMetrics = nutrients
    .filter(({ key }) => items.some((item) => item.aiKeys?.includes(key)))
    .map(({ key }) => key);
  return {
    values,
    missing,
    aiMetrics,
    totalGrams:
      Math.round(items.reduce((sum, item) => sum + item.grams, 0) * 10) / 10,
    sampleCount: items.filter((item) => item.source === "sample").length,
    labelCount: items.filter((item) => item.source === "label").length,
    estimateCount: items.filter((item) => item.source === "estimate").length,
    unknownCount: items.filter(
      (item) => item.source === "unknown" && !item.aiKeys?.length,
    ).length,
  };
}
export function energyToKcal(value: number, unit: "kcal" | "kJ") {
  if (!Number.isFinite(value) || value < 0) throw new Error("热量应为非负数值");
  return unit === "kJ" ? Math.round((value / 4.184) * 10000) / 10000 : value;
}
export function nutritionBreakdown(
  items: NutritionAnalysisEntry[],
  key: NutrientKey,
  recordMode = false,
) {
  summarizeNutrition(items, recordMode);
  const total = items.reduce(
    (sum, item) =>
      sum +
      (item.per100[key] === null ? 0 : (item.per100[key]! * item.grams) / 100),
    0,
  );
  return items.map((item) => {
    const raw =
      item.per100[key] === null ? null : (item.per100[key]! * item.grams) / 100;
    return {
      id: item.id,
      name: item.name,
      source: item.source,
      aiEstimated: item.aiKeys?.includes(key),
      grams: item.grams,
      per100: item.per100[key],
      value: raw === null ? null : Math.round(raw * 10) / 10,
      share:
        raw === null || total === 0
          ? null
          : Math.round((raw / total) * 1000) / 10,
    };
  });
}
export function nutrition(food: Food, grams: number) {
  if (!Number.isFinite(grams) || grams <= 0 || grams > 2000)
    throw new Error("份量应在 1–2000g 之间");
  return Object.fromEntries(
    (["kcal", "protein", "carbs", "fat", "fiber", "sodium"] as const).map(
      (key) => [key, Math.round(((food[key] * grams) / 100) * 10) / 10],
    ),
  ) as Record<
    "kcal" | "protein" | "carbs" | "fat" | "fiber" | "sodium",
    number
  >;
}
