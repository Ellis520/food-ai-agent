const brands =
  /肯德基|kfc|麦当劳|塔斯汀|华莱士|瑞幸|库迪|星巴克|蜜雪冰城|霸王茶姬|茶百道|古茗|喜茶|奈雪|益禾堂|一点点|萨莉亚|必胜客/gi;
const foods =
  /珍珠奶茶|奶茶|果茶|柠檬茶|咖啡|炸鸡|汉堡|披萨|烧腊|烧鸭饭|叉烧饭|黄焖鸡|猪脚饭|盖饭|米饭|肠粉|粥|螺蛳粉|牛肉面|拉面|汤面|面食|米线|米粉|麻辣烫|麻辣香锅|沙拉|轻食|饺子|馄饨|云吞|寿司|烧烤|烤鱼|火锅|蛋糕|甜品|快餐|小吃/g;

export function takeawayKeywords(request: string) {
  const text = request
    .normalize("NFKC")
    .trim()
    .replace(
      /(?:不吃|不喝|不要|避开|不想吃)[^，。；、,.!?！？]*?(?=想吃|想喝|来点|[，。；、,.!?！？]|$)/g,
      "",
    );
  const selectedBrands = Array.from(text.matchAll(brands), (match) =>
    match[0].toLowerCase() === "kfc" ? "肯德基" : match[0],
  );
  const selectedFoods = Array.from(text.matchAll(foods), (match) =>
    match[0] === "珍珠奶茶" ? "奶茶" : match[0],
  );
  if (selectedBrands.length || selectedFoods.length)
    return [
      ...new Set(selectedBrands.length ? selectedBrands : selectedFoods),
    ].slice(0, 2);
  const keyword = text
    .replace(/\d+(?:\.\d+)?\s*(?:元|块)(?:以内|以下|左右|上下)?/g, "")
    .replace(
      /(?:帮我|推荐|想吃|想喝|我想|找一家|找几家|找点|来点|附近的?|校园周边|外卖|预算|不超过|价格优先|评分优先|距离优先|便宜一点|随便|都可以)/g,
      "",
    )
    .replace(/^[\s，。、！？；]+|[\s，。、！？；]+$/g, "")
    .slice(0, 60);
  return [keyword];
}

export function requestedTakeawaySort(request: string) {
  if (/距离优先|最近|近一点/.test(request)) return "distance";
  if (/价格优先|便宜|低价/.test(request)) return "price";
  if (/评分优先|评分高|高评分|好评/.test(request)) return "rating";
}
