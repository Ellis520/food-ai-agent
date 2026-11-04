import type { Food, Restaurant, PlanItem } from "./types";
export const foods: Food[] = [
  {
    id: "oats",
    name: "燕麦牛奶碗",
    emoji: "🥣",
    kcal: 112,
    protein: 4,
    carbs: 17,
    fat: 3,
    fiber: 2,
    sodium: 35,
    price: 8,
    category: "早餐",
  },
  {
    id: "chicken",
    name: "香煎鸡肉杂粮饭",
    emoji: "🍱",
    kcal: 156,
    protein: 12,
    carbs: 18,
    fat: 4,
    fiber: 2,
    sodium: 210,
    price: 18,
    category: "均衡",
  },
  {
    id: "noodle",
    name: "番茄鸡蛋面",
    emoji: "🍜",
    kcal: 105,
    protein: 4,
    carbs: 16,
    fat: 3,
    fiber: 1,
    sodium: 240,
    price: 12,
    category: "热食",
  },
  {
    id: "salad",
    name: "缤纷蔬菜轻食碗",
    emoji: "🥗",
    kcal: 92,
    protein: 5,
    carbs: 9,
    fat: 4,
    fiber: 3,
    sodium: 120,
    price: 16,
    category: "轻食",
  },
  {
    id: "rice",
    name: "清蒸鱼配米饭",
    emoji: "🍚",
    kcal: 135,
    protein: 10,
    carbs: 17,
    fat: 3,
    fiber: 1,
    sodium: 150,
    price: 20,
    category: "均衡",
  },
  {
    id: "egg",
    name: "水煮鸡蛋",
    emoji: "🥚",
    kcal: 144,
    protein: 13,
    carbs: 1,
    fat: 10,
    fiber: 0,
    sodium: 131,
    price: 2,
    category: "早餐",
  },
];
// 全部为人工编写的虚构校园数据，不对应真实商家。
export const restaurants: Restaurant[] = [
  {
    id: "r1",
    name: "一食堂 · 青禾轻食",
    emoji: "🥗",
    category: "轻食",
    price: 16,
    rating: 4.8,
    distance: 280,
    address: "示例校园 · 第一食堂二楼",
    hours: "10:30–20:00",
    reason: "蔬菜与主食自由搭配，适合课间补充能量。",
    x: 28,
    y: 38,
    source: "demo",
  },
  {
    id: "r2",
    name: "南门 · 一碗好面",
    emoji: "🍜",
    category: "热食",
    price: 12,
    rating: 4.7,
    distance: 520,
    address: "示例校园 · 南门美食街",
    hours: "07:00–22:00",
    reason: "一碗热乎的番茄面，照顾你的预算。",
    x: 66,
    y: 68,
    source: "demo",
  },
  {
    id: "r3",
    name: "二食堂 · 谷物厨房",
    emoji: "🍱",
    category: "均衡",
    price: 18,
    rating: 4.9,
    distance: 420,
    address: "示例校园 · 第二食堂一楼",
    hours: "10:00–20:30",
    reason: "鸡肉、杂粮和时蔬，一餐搭配更丰富。",
    x: 72,
    y: 28,
    source: "demo",
  },
];
export function buildPlan(
  budget: number,
  preference: string,
  day = 0,
): PlanItem[] {
  const lunch =
    preference === "素食"
      ? "salad"
      : budget < 35
        ? "noodle"
        : day % 2
          ? "rice"
          : "chicken";
  return [
    { slot: "早餐", foodId: "oats", grams: 250 },
    { slot: "午餐", foodId: lunch, grams: 350 },
    {
      slot: "晚餐",
      foodId:
        preference === "素食" ? "salad" : budget < 35 ? "noodle" : "salad",
      grams: 300,
    },
  ];
}
