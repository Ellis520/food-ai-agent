import type { Meal, AnalysisDays, HabitSummary, HabitAnalysis } from "./types";
import { foods } from "./mock";

export function summarizeHabits(
  meals: Meal[],
  days: AnalysisDays,
  end: string,
): HabitSummary {
  const dates = Array.from({ length: days }, (_, i) => {
    const date = new Date(end + "T12:00:00");
    date.setDate(date.getDate() - days + 1 + i);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
  });
  const records = meals.filter((meal) => dates.includes(meal.date)),
    recordedDays = new Set(records.map((meal) => meal.date)).size;
  const customFoods = new Map<string, number>();
  for (const meal of records) {
    if (!meal.foodId && meal.dishName)
      customFoods.set(meal.dishName, (customFoods.get(meal.dishName) ?? 0) + 1);
  }
  const totalCost = Number(
    records.reduce((sum, meal) => sum + meal.cost, 0).toFixed(2),
  );
  return {
    period: { days, start: dates[0], end },
    statistics: {
      recordCount: records.length,
      recordedDays,
      breakfastDays: new Set(
        records.filter((meal) => meal.slot === "早餐").map((meal) => meal.date),
      ).size,
      totalCost,
      averageRecordedDayCost: recordedDays
        ? Number((totalCost / recordedDays).toFixed(2))
        : 0,
      mealCounts: Object.fromEntries(
        ["早餐", "午餐", "晚餐", "加餐"].map((slot) => [
          slot,
          records.filter((meal) => meal.slot === slot).length,
        ]),
      ),
      daily: dates.map((date) => ({
        date,
        recordCount: records.filter((meal) => meal.date === date).length,
        totalCost: Number(
          records
            .filter((meal) => meal.date === date)
            .reduce((sum, meal) => sum + meal.cost, 0)
            .toFixed(2),
        ),
      })),
      foods: [
        ...foods
          .filter((food) => records.some((meal) => meal.foodId === food.id))
          .map((food) => ({
            foodId: food.id,
            name: food.name,
            count: records.filter((meal) => meal.foodId === food.id).length,
          })),
        ...Array.from(customFoods, ([name, count]) => ({
          foodId: "custom:" + name,
          name,
          count,
        })),
      ],
    },
  };
}

export function demoHabitAnalysis(
  meals: Meal[],
  days: AnalysisDays,
  end: string,
): HabitAnalysis {
  const summary = summarizeHabits(meals, days, end);
  return {
    ...summary,
    provider: "demo",
    revision: 0,
    generatedAt: new Date().toISOString(),
    summary:
      "这是根据当前演示记录生成的固定规则回顾，未调用千问。记录可能不完整，不据此判断真实饮食规律。",
    observations: [
      {
        title: "从记录覆盖开始",
        detail:
          "先核对所选时间内的记录是否完整，缺少记录的日期无法判断是否用餐。",
        evidence: "coverage",
      },
      {
        title: "回看实际花费",
        detail: "这里只统计录入的金额，不代表完整生活开销。",
        evidence: "spending",
      },
    ],
    suggestions: [
      {
        title: "补齐想回顾的一餐",
        action: "如果某餐实际吃过但尚未记录，可以补填食物、份量和花费。",
      },
      {
        title: "安排下一餐",
        action: "到规划页按自己的预算安排下一餐，并在实际用餐后确认记录。",
      },
    ],
    dataNote: "规则演示，未调用真实 AI；食物目录为样本，未记录不代表未用餐。",
  };
}
