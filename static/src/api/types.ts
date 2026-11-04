export interface Food {
  id: string;
  name: string;
  emoji: string;
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  fiber: number;
  sodium: number;
  price: number;
  category: string;
}
export interface Meal {
  id: string;
  date: string;
  slot: string;
  foodId: string;
  grams: number;
  cost: number;
  planRef?: string;
  dishName?: string;
}
export interface Restaurant {
  id: string;
  name: string;
  emoji: string;
  category: string;
  price: number | null;
  rating: number | null;
  distance: number | null;
  address: string;
  hours: string;
  reason: string;
  x: number;
  y: number;
  lng?: number;
  lat?: number;
  source: "demo" | "amap";
  photoUrl?: string | null;
  phone?: string;
  cuisine?: string;
  specialties?: string;
}
export interface PlanItem {
  slot: string;
  foodId: string;
  grams: number;
  dishName?: string;
  priceMin?: number;
  priceMax?: number;
}
export interface Profile {
  name: string;
  campus: string;
  budget: number;
  goal: string;
  preference: string;
  allergies: string;
  kcal: number;
  setupComplete?: true | null;
  taste?: string | null;
  cuisine?: string | null;
  avatar?: string | null;
}
export type ChatPlace = Pick<
  Restaurant,
  | "id"
  | "name"
  | "address"
  | "hours"
  | "price"
  | "rating"
  | "distance"
  | "source"
> & { lng?: number | null; lat?: number | null };
export type HealthLookup = "hospital" | "pharmacy";
export type HealthStatus =
  | "clarify"
  | "assessment"
  | "clinic"
  | "otc"
  | "hydration"
  | "urgent"
  | "places";
export type HealthTopic =
  | "headache"
  | "diarrhea"
  | "stomach"
  | "respiratory"
  | "dizziness"
  | "fever";
export interface ChatHealthOptions {
  healthLookup: HealthLookup;
  healthGPS?: { lng: number; lat: number };
}
export interface ChatRestaurantSearch {
  keyword: string;
  places: ChatPlace[];
  category?: "restaurant" | HealthLookup;
  origin?: "campus" | "location";
}
export type RestaurantMode = "takeaway" | "dine-in";
export interface ChatMeal {
  slot: "早餐" | "午餐" | "晚餐";
  option: string;
  name: string;
  detail: string;
}
export interface Message {
  id: string;
  role: "user" | "assistant";
  text: string;
  kind?: "plan" | "restaurant" | "nutrition" | "health";
  source?: string;
  planItems?: PlanItem[];
  planDate?: string;
  mealSuggestions?: ChatMeal[];
  selectedMeal?: ChatMeal;
  restaurantSearch?: ChatRestaurantSearch;
  restaurantMode?: RestaurantMode;
  healthStatus?: HealthStatus;
  healthTopic?: HealthTopic;
}
export type HealthMessage = Message & {
  role: "assistant";
  kind: "health";
  healthStatus: HealthStatus;
};
export interface Envelope<T> {
  data: T;
  source: string;
}
export type NutrientKey =
  | "kcal"
  | "protein"
  | "carbs"
  | "fat"
  | "fiber"
  | "sodium";
export type NutrientValues = Record<NutrientKey, number | null>;
export interface NutritionEntry {
  id: string;
  name: string;
  grams: number;
  per100: NutrientValues;
  source: "sample" | "label";
  foodId?: string;
}
export interface NutritionRecipe {
  id: string;
  name: string;
  items: NutritionEntry[];
}
export interface AppState {
  profile: Profile;
  meals: Meal[];
  plans: Record<string, PlanItem[]>;
  favorites: string[];
  messages: Message[];
  nutritionRecipes?: NutritionRecipe[];
}
export interface Health {
  database: string;
  qwen: boolean;
  amap: boolean;
  campus: boolean;
  asr: boolean;
}
export interface TakeawayIdea {
  name: string;
  keyword: string;
  category: string;
  reason: string;
  difference: string;
  searchUrl: string;
  source: "ai-meal-idea" | "demo-meal-idea";
}
export interface TakeawayIdeas {
  reply: string;
  cards: TakeawayIdea[];
  provider: "qwen" | "demo";
  budgetTarget: number;
  note: string;
}
export interface TakeawayRequest {
  message: string;
  budget: number;
  preference: string;
  history: { role: "user" | "assistant"; text: string }[];
  excludeKeywords: string[];
}
export type AnalysisDays = 7 | 30;
export interface HabitSummary {
  period: { days: AnalysisDays; start: string; end: string };
  statistics: {
    recordCount: number;
    recordedDays: number;
    breakfastDays: number;
    totalCost: number;
    averageRecordedDayCost: number;
    mealCounts: Record<string, number>;
    daily: { date: string; recordCount: number; totalCost: number }[];
    foods: { foodId: string; name: string; count: number }[];
  };
}
export interface HabitAnalysis extends HabitSummary {
  summary: string;
  observations: {
    title: string;
    detail: string;
    evidence: "coverage" | "breakfast" | "spending" | "distribution";
  }[];
  suggestions: { title: string; action: string }[];
  revision: number;
  provider: "qwen" | "demo";
  generatedAt: string;
  dataNote: string;
}
