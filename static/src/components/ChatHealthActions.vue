<script setup lang="ts">
import { computed, ref } from "vue";
import type {
  ChatHealthOptions,
  HealthLookup,
  HealthStatus,
  HealthTopic,
} from "../api/types";
import Icon from "./Icon.vue";

const props = defineProps<{
  status: HealthStatus;
  topic?: HealthTopic;
  disabled: boolean;
  demo: boolean;
}>();
const emit = defineEmits<{
  lookup: [options: ChatHealthOptions];
  describe: [];
}>();
const useLocation = ref(false);
const locating = ref(false);
const locationError = ref("");
const references: Record<HealthTopic, { label: string; url: string }> = {
  headache: {
    label: "NHS · 头痛与就医信号",
    url: "https://www.nhs.uk/symptoms/headaches/",
  },
  diarrhea: {
    label: "国家疾控局 · 腹泻与补液健康提示",
    url: "https://wjw.fujian.gov.cn/xxgk/gzdt/mtbd/202503/t20250328_6788512.htm",
  },
  stomach: {
    label: "NHS · 腹痛与就医信号",
    url: "https://www.nhs.uk/symptoms/stomach-ache/",
  },
  respiratory: {
    label: "NHS · 感冒症状与就医建议",
    url: "https://www.nhs.uk/conditions/common-cold/",
  },
  dizziness: {
    label: "NHS · 头晕与常见原因",
    url: "https://www.nhs.uk/symptoms/dizziness/",
  },
  fever: {
    label: "NHS · 成人发热",
    url: "https://www.nhs.uk/symptoms/fever-in-adults/",
  },
};
const reference = computed(() =>
  props.topic ? references[props.topic] : undefined,
);

async function lookup(kind: HealthLookup) {
  if (props.disabled || locating.value) return;
  locationError.value = "";
  if (!useLocation.value) {
    emit("lookup", { healthLookup: kind });
    return;
  }
  if (!navigator.geolocation) {
    locationError.value =
      "当前浏览器无法定位。您可以取消勾选，按校园位置查找。";
    return;
  }
  locating.value = true;
  try {
    const position = await new Promise<GeolocationPosition>(
      (resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, {
          timeout: 10000,
          maximumAge: 30000,
        });
      },
    );
    emit("lookup", {
      healthLookup: kind,
      healthGPS: {
        lng: position.coords.longitude,
        lat: position.coords.latitude,
      },
    });
  } catch {
    locationError.value =
      "未获取到当前位置。请您检查浏览器定位权限，或取消勾选，按校园位置查找。";
  } finally {
    locating.value = false;
  }
}
</script>

<template>
  <section
    v-if="status === 'urgent'"
    class="structured-card health-urgent"
    aria-label="紧急求助"
  >
    <h3>如果您现在有危险症状，请立即求助</h3>
    <a class="primary" href="tel:120">拨打 120（中国大陆）</a>
    <p>
      <a
        href="https://www.beijing.gov.cn/fuwu/bmfw/sy/jrts/202607/t20260713_4759730.html"
        target="_blank"
        rel="noopener noreferrer"
        >国家卫健委 120 急救指南 ↗</a
      >
    </p>
  </section>
  <section
    v-else-if="!demo"
    class="structured-card health-actions"
    aria-label="健康咨询下一步"
  >
    <h3>您可能需要的帮助</h3>
    <div class="health-buttons">
      <button
        type="button"
        :class="status === 'clinic' ? 'primary' : 'secondary'"
        :disabled="disabled || locating"
        @click="lookup('hospital')"
      >
        <Icon name="HeartPulse" :size="16" /> 查附近医院
      </button>
      <button
        v-if="status !== 'clinic'"
        type="button"
        class="secondary"
        :disabled="disabled || locating"
        @click="lookup('pharmacy')"
      >
        <Icon name="MapPinned" :size="16" /> 查附近药店
      </button>
      <button
        type="button"
        class="secondary"
        :disabled="disabled || locating"
        @click="emit('describe')"
      >
        补充症状
      </button>
    </div>
    <label class="health-location"
      ><input
        v-model="useLocation"
        type="checkbox"
        :disabled="disabled || locating"
      />
      使用当前位置查找</label
    >
    <p class="source-note">
      {{
        locating
          ? "正在获取位置…"
          : useLocation
            ? "经您授权后，使用当前位置查找。"
            : "默认以校园为搜索中心。"
      }}
    </p>
    <p v-if="locationError" role="alert" class="error">{{ locationError }}</p>
    <div
      v-if="reference || status === 'otc' || status === 'hydration'"
      class="health-references"
    >
      <a
        v-if="reference && status !== 'hydration'"
        :href="reference.url"
        target="_blank"
        rel="noopener noreferrer"
        >{{ reference.label }} ↗</a
      >
      <a
        v-if="status === 'otc'"
        href="https://wjw.beijing.gov.cn/bmfw_20143/jkzs/jksh/202212/t20221230_2887410.html"
        target="_blank"
        rel="noopener noreferrer"
        >北京市卫健委 · 解热镇痛用药科普 ↗</a
      >
      <a
        v-if="status === 'hydration'"
        href="https://wjw.fujian.gov.cn/xxgk/gzdt/mtbd/202503/t20250328_6788512.htm"
        target="_blank"
        rel="noopener noreferrer"
        >国家疾控局 · 腹泻与补液健康提示 ↗</a
      >
    </div>
  </section>
</template>

<style scoped>
.health-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.health-buttons button,
.health-urgent > .primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}
.health-location {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
  margin: 12px 0 4px;
  font-size: 13px;
}
.health-location input {
  width: auto;
}
.health-actions .source-note,
.health-urgent p {
  margin: 5px 0 0;
}
.health-urgent {
  background: #fff5ed;
  border-color: #db9c82;
}
.health-actions {
  background: #fffefb;
  border-color: #dde5db;
  padding: 14px 16px;
}
.health-actions h3 {
  margin: 0 0 9px;
  font-size: 15px;
  color: #304d3e;
}
.health-buttons button {
  min-height: 36px;
  padding: 7px 10px;
}
.health-references {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 12px;
  border-top: 1px solid #e5ebe1;
  padding-top: 8px;
  margin-top: 9px;
  font-size: 12px;
  color: #476753;
}
.health-references a {
  text-decoration: underline;
  text-underline-offset: 2px;
}
</style>
