import { createRouter, createWebHashHistory } from "vue-router";
import HomeView from "../views/HomeView.vue";
import ChatView from "../views/ChatView.vue";
import PlanView from "../views/PlanView.vue";
import RecordsView from "../views/RecordsView.vue";
import HabitsView from "../views/HabitsView.vue";
import MapView from "../views/MapView.vue";
import TakeawayView from "../views/TakeawayView.vue";
import CommunityView from "../views/CommunityView.vue";
import NutritionView from "../views/NutritionView.vue";
import LanguageView from "../views/LanguageView.vue";
import ProfileView from "../views/ProfileView.vue";

export const pages = [
  {
    path: "/",
    name: "首页概览",
    icon: "House",
    group: "日常食刻",
    component: HomeView,
  },
  {
    path: "/chat",
    name: "AI 聊天",
    icon: "Sparkles",
    group: "日常食刻",
    component: ChatView,
  },
  {
    path: "/plan",
    name: "饮食规划",
    icon: "CalendarDays",
    group: "日常食刻",
    component: PlanView,
  },
  {
    path: "/records",
    name: "饮食记录",
    icon: "NotebookPen",
    group: "日常食刻",
    component: RecordsView,
  },
  {
    path: "/habits",
    name: "饮食习惯",
    icon: "ChartNoAxesCombined",
    group: "日常食刻",
    component: HabitsView,
  },
  {
    path: "/map",
    name: "校园美食地图",
    icon: "MapPinned",
    group: "探索更多",
    component: MapView,
  },
  {
    path: "/takeaway",
    name: "外卖助手",
    icon: "Bike",
    group: "探索更多",
    component: TakeawayView,
  },
  {
    path: "/community",
    name: "美食圈",
    icon: "MessageCircleHeart",
    group: "探索更多",
    component: CommunityView,
  },
  {
    path: "/nutrition",
    name: "营养分析",
    icon: "Leaf",
    group: "探索更多",
    component: NutritionView,
  },
  {
    path: "/language",
    name: "饮食小贴士",
    icon: "ShieldCheck",
    group: "探索更多",
    component: LanguageView,
  },
  {
    path: "/profile",
    name: "个人中心",
    icon: "UserRound",
    group: "我的",
    component: ProfileView,
  },
];
export default createRouter({
  history: createWebHashHistory(),
  routes: [
    ...pages.map((p) => ({
      path: p.path,
      component: p.component,
      meta: { title: p.name },
    })),
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
  scrollBehavior: () => ({ top: 0 }),
});
