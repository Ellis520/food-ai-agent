import type { TakeawayRequest, TakeawayIdeas, TakeawayIdea } from "./types";
export function mealSearchUrl(keyword: string) {
  return (
    "https://h5.waimai.meituan.com/waimai/mindex/searchresults?" +
    new URLSearchParams({
      queryType: "12002",
      keyword,
      entranceId: "0",
      qwTypeId: "0",
      mode: "search",
    }).toString()
  );
}
export function mealLoginUrl(keyword: string) {
  return (
    "https://h5.waimai.meituan.com/login?" +
    new URLSearchParams({
      force: "true",
      back_url: mealSearchUrl(keyword),
    }).toString()
  );
}

const options: Record<string, string[]> = {
  米饭套餐: [
    "黄焖鸡米饭",
    "香菇滑鸡饭",
    "土豆鸡块饭",
    "烧鸭饭",
    "叉烧饭",
    "卤肉饭",
    "咖喱鸡饭",
    "鸡腿饭",
    "番茄炒蛋饭",
    "菌菇盖饭",
    "鱼香肉丝盖饭",
    "宫保鸡丁盖饭",
  ],
  面食粉类: [
    "番茄鸡蛋面",
    "牛肉面",
    "炸酱面",
    "葱油拌面",
    "云吞面",
    "炒河粉",
    "汤米粉",
    "螺蛳粉",
    "热干面",
    "鸡丝凉面",
    "菌菇汤面",
    "蔬菜拌面",
  ],
  小吃粥点: [
    "玉米猪肉饺子",
    "白菜素饺",
    "鲜肉云吞",
    "小笼包",
    "鸡蛋肠粉",
    "皮蛋瘦肉粥",
    "南瓜粥",
    "小米粥",
  ],
  轻食蔬菜: [
    "蔬菜沙拉",
    "鸡肉沙拉",
    "杂粮蔬菜饭",
    "鸡蛋三明治",
    "菌菇沙拉",
    "藜麦蔬菜碗",
  ],
  汉堡披萨: [
    "鸡肉汉堡",
    "牛肉汉堡",
    "鸡肉卷",
    "蔬菜披萨",
    "鸡肉披萨",
    "全麦三明治",
  ],
  饮品甜点: [
    "珍珠奶茶",
    "乌龙奶茶",
    "柠檬茶",
    "水果茶",
    "美式咖啡",
    "拿铁咖啡",
    "酸奶",
    "红豆双皮奶",
    "豆浆",
    "鲜榨橙汁",
  ],
};

export function demoTakeawayIdeas(body: TakeawayRequest): TakeawayIdeas {
  const text = body.message + body.preference;
  let categories = /奶茶|咖啡|果茶|饮品|喝/.test(text)
    ? ["饮品甜点"]
    : /面|粉/.test(text)
      ? ["面食粉类", "小吃粥点"]
      : /轻食|沙拉|清爽|素食/.test(text)
        ? ["轻食蔬菜", "米饭套餐", "面食粉类"]
        : /饭|米饭|套餐/.test(text)
          ? ["米饭套餐"]
          : Object.keys(options);
  let choices = categories.flatMap((category) =>
    options[category].map((keyword) => ({ category, keyword })),
  );
  if (/素食|不吃肉/.test(text))
    choices = choices.filter((item) =>
      /蔬菜|菌菇|番茄炒蛋|白菜素|南瓜|小米|水果|柠檬/.test(item.keyword),
    );
  if (/不要辣|不辣|少辣|清淡/.test(text))
    choices = choices.filter(
      (item) => !/螺蛳|宫保|鱼香|咖喱/.test(item.keyword),
    );
  const excluded = new Set(body.excludeKeywords);
  const cards: TakeawayIdea[] = choices
    .filter((item) => !excluded.has(item.keyword))
    .slice(0, 12)
    .map((item, index) => ({
      ...item,
      name: item.keyword,
      reason:
        "按输入关键词匹配的演示餐食方向，实际口味、配料和价格需在平台确认。",
      difference: ["换一种主料", "尝试不同做法", "同类餐食的另一种选择"][
        index % 3
      ],
      searchUrl: mealSearchUrl(item.keyword),
      source: "demo-meal-idea",
    }));
  return {
    reply: cards.length
      ? "给你多列几种可以比较的餐食方向。这是固定规则演示，点击卡片可去平台搜索。"
      : "当前演示词库没有更多不同的选择了，换个餐食方向再试试。",
    cards,
    provider: "demo",
    budgetTarget: body.budget,
    note: "规则演示，未调用千问或查询实时外卖菜单。链接为关键词搜索页，平台可能要求登录并设置配送地址。",
  };
}
