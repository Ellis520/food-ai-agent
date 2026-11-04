<script setup lang="ts">
import {
  House,
  Sparkles,
  CalendarDays,
  NotebookPen,
  ChartNoAxesCombined,
  MapPinned,
  Bike,
  Leaf,
  Languages,
  UserRound,
  Sprout,
  GraduationCap,
  ChevronRight,
  Menu,
  Info,
  ArrowUpRight,
  ArrowRight,
  Plus,
  Heart,
  Star,
  Navigation,
  CircleCheck,
  Flame,
  Utensils,
  Wallet,
  Mic,
  ArrowUp,
  Check,
  Trash2,
  Search,
  Copy,
  X,
  Volume2,
  Circle,
  LoaderCircle,
  Image as ImageIcon,
} from "lucide-vue-next";
import {
  ShieldCheck,
  CookingPot,
  Refrigerator,
  HeartPulse,
  MessageCircleHeart,
  TriangleAlert,
  Send,
} from "lucide-vue-next";
import { computed } from "vue";
const props = withDefaults(defineProps<{ name: string; size?: number }>(), {
  size: 20,
});
const icons: Record<string, unknown> = {
  Image: ImageIcon,
  LoaderCircle,
  House,
  Sparkles,
  CalendarDays,
  NotebookPen,
  ChartNoAxesCombined,
  MapPinned,
  Bike,
  Leaf,
  Languages,
  UserRound,
  Sprout,
  GraduationCap,
  ChevronRight,
  Menu,
  Info,
  ArrowUpRight,
  ArrowRight,
  Plus,
  Heart,
  Star,
  Navigation,
  CircleCheck,
  Flame,
  Utensils,
  Wallet,
  Mic,
  ArrowUp,
  Check,
  Trash2,
  Search,
  Copy,
  X,
  Volume2,
  Circle,
};
Object.assign(icons, {
  ShieldCheck,
  CookingPot,
  Refrigerator,
  HeartPulse,
  MessageCircleHeart,
  TriangleAlert,
  Send,
});
const component = computed(() => icons[props.name] || Circle);
</script>
<template>
  <component
    :is="component"
    :size="size"
    :stroke-width="1.8"
    aria-hidden="true"
  />
</template>
