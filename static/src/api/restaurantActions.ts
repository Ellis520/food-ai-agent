import type {
  ChatPlace,
  ChatRestaurantSearch,
  HealthMessage,
  Message,
  RestaurantMode,
} from "./types";

export function highestRatedFirst(
  a: Pick<ChatPlace, "rating" | "distance">,
  b: Pick<ChatPlace, "rating" | "distance">,
) {
  return (
    (b.rating ?? -Infinity) - (a.rating ?? -Infinity) ||
    (a.distance ?? Infinity) - (b.distance ?? Infinity)
  );
}

export function placeActionReply(
  search: ChatRestaurantSearch,
  mode: RestaurantMode,
  place?: ChatPlace,
): Message {
  const places = place ? [place] : search.places;
  const subject = search.category === "pharmacy" ? "药店" : "店铺";
  const demo = places.every((item) => item.source === "demo");
  const reply = {
    id: crypto.randomUUID(),
    role: "assistant" as const,
    text: demo
      ? "这是您选择的操作预览。"
      : mode === "takeaway"
        ? `用下面的卡片在美团搜索对应的${subject}。`
        : `打开所选${subject}的高德步行导航。`,
    restaurantSearch: { ...search, places },
    restaurantMode: mode,
  };
  if (search.category === "pharmacy") {
    return {
      ...reply,
      kind: "health",
      healthStatus: "places",
    } satisfies HealthMessage;
  }
  return { ...reply, kind: "restaurant" };
}

export function walkingUrl(
  place: Pick<ChatPlace, "name" | "lng" | "lat" | "source">,
) {
  const { lng, lat } = place;
  if (
    place.source !== "amap" ||
    lng == null ||
    lat == null ||
    !(lng >= 73 && lng <= 136 && lat >= 3 && lat <= 54)
  )
    return "";
  return `https://uri.amap.com/navigation?to=${lng},${lat},${encodeURIComponent(place.name)}&mode=walk&callnative=1`;
}

export function restaurantFollowUp(text: string, search: ChatRestaurantSearch) {
  const normalize = (value: string) =>
    value.normalize("NFKC").replace(/[\s，,。！!？?、：:]+/g, "");
  let choice = normalize(text);
  // Recognize short replies to the previous cards. Leave new searches and
  // other open-ended requests to the chat service.
  const named = search.places
    .filter((place) => choice.includes(normalize(place.name)))
    .sort((first, second) => second.name.length - first.name.length)[0];
  if (named) choice = choice.replace(normalize(named.name), "");
  const ordinal = choice.match(/第([一二三四1234])家/);
  const position = ordinal
    ? Number(ordinal[1]) || "一二三四".indexOf(ordinal[1]) + 1
    : 0;
  if (ordinal) choice = choice.replace(ordinal[0], "");
  const place = named || search.places[position - 1];
  choice = choice.replace(/(?:就)?(?:这|那)家/g, "");
  const takeaway =
    /^(?:我)?(?:想要|想|打算|选择|要|就)?(?:点(?:个|份)?外卖|外卖|送(?:到|来)宿舍|配送|送药上门)(?:吧|了|吃)?$/;
  const dineIn =
    /^(?:我)?(?:想要|想|打算|选择|要|就|帮我)?(?:到店(?:吃(?:饭)?|买(?:药)?)?|去店(?:里|铺)?(?:吃(?:饭)?|买(?:药)?)?|(?:到|去)?现场吃(?:饭)?|堂食|去吃(?:饭)?|给我导航|导航(?:去|到)?|去)(?:吧|了)?$/;
  const mode: RestaurantMode | undefined = takeaway.test(choice)
    ? "takeaway"
    : dineIn.test(choice)
      ? "dine-in"
      : undefined;
  return mode ? { mode, place } : undefined;
}
