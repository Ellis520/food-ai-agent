export interface CampusTakeawayEntry {
  id: string;
  name: string;
  store: string;
  category: string;
  platform: "美团外卖";
  destination: "item" | "menu";
  url: string;
  keywords: string[];
  description: string;
}

function isOrderLink(entry: CampusTakeawayEntry) {
  try {
    const url = new URL(entry.url);
    if (url.protocol !== "https:" || url.username || url.password || url.port)
      return false;
    if (entry.platform !== "美团外卖") return false;
    if (url.hostname === "dpurl.cn" || url.hostname === "url.meituan.com")
      return url.pathname.length > 1;
    if (
      ![
        "h5.waimai.meituan.com",
        "i.waimai.meituan.com",
        "waimai.meituan.com",
      ].includes(url.hostname)
    )
      return false;
    const shop =
      url.searchParams.get("poi_id_str") || url.searchParams.get("mtShopId");
    return Boolean(
      shop && shop !== "-100" && /\/(menu|food)\/?$/.test(url.pathname),
    );
  } catch {
    return false;
  }
}

export async function loadCampusTakeaway(): Promise<CampusTakeawayEntry[]> {
  const response = await fetch(
    import.meta.env.BASE_URL + "campus-takeaway.json",
  );
  if (!response.ok) throw new Error("校园精选清单暂时无法加载。");
  const data: unknown = await response.json();
  if (!Array.isArray(data)) throw new Error("校园精选清单格式有误。");
  return data.filter(
    (entry): entry is CampusTakeawayEntry =>
      entry &&
      typeof entry === "object" &&
      ["id", "name", "store", "category", "url", "description"].every(
        (field) => typeof entry[field] === "string" && entry[field].trim(),
      ) &&
      (entry.destination === "item" || entry.destination === "menu") &&
      Array.isArray(entry.keywords) &&
      entry.keywords.every((word: unknown) => typeof word === "string") &&
      isOrderLink(entry),
  );
}

function storeKey(name: string) {
  return name
    .normalize("NFKC")
    .toLowerCase()
    .replace(/宅急送/g, "")
    .replace(/[\s()（）·•-]/g, "");
}

export function campusStoreLinks(name: string, catalog: CampusTakeawayEntry[]) {
  const store = storeKey(name);
  return catalog.filter((entry) => storeKey(entry.store) === store);
}
