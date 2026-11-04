<script setup lang="ts">
import { computed, ref } from "vue";
import { mealSearchUrl, mealLoginUrl } from "../api/takeawayIdeas";
const props = withDefaults(
    defineProps<{
      keyword: string;
      termKind?: "菜名" | "店名";
      channel?: "外卖" | "看病买药";
    }>(),
    { termKind: "菜名", channel: "外卖" },
  ),
  status = ref(""),
  help = ref<HTMLDetailsElement>();
const url = computed(() => mealSearchUrl(props.keyword)),
  loginUrl = computed(() => mealLoginUrl(props.keyword));
async function copy(kind: "name" | "url") {
  status.value = "";
  try {
    await navigator.clipboard.writeText(
      kind === "name" ? props.keyword : url.value,
    );
    status.value =
      kind === "name"
        ? `已复制「${props.keyword}」。打开手机美团的${props.channel}频道，搜索这个${props.termKind}。`
        : "已复制美团网页结果链接。";
  } catch {
    if (help.value) help.value.open = true;
    status.value = `当前浏览器无法复制，请手动选择下方的${props.termKind}或链接。`;
  }
}
</script>
<template>
  <div class="takeaway-search-options">
    <button type="button" class="secondary app-search" @click="copy('name')">
      复制{{ termKind }}，去 App 搜
    </button>
    <p v-if="status" role="status" class="search-status">{{ status }}</p>
    <details ref="help" class="platform-help">
      <summary>美团网页结果打不开？</summary>
      <p>
        如果结果页显示网络异常或 403，可以在手机美团的{{
          channel
        }}频道搜索下方的{{ termKind }}。
      </p>
      <label
        >搜索{{ termKind
        }}<input :value="keyword" readonly :aria-label="'备用搜索' + termKind"
      /></label>
      <div class="platform-help-actions">
        <a
          href="https://waimai.meituan.com/mobile/download"
          target="_blank"
          rel="noopener noreferrer"
          >美团外卖 App 官方下载 ↗</a
        >
        <a :href="loginUrl" target="_blank" rel="noopener noreferrer"
          >网页需要登录时 ↗</a
        >
        <button type="button" class="text-btn" @click="copy('url')">
          复制网页结果链接
        </button>
      </div>
      <label
        >网页结果链接<input :value="url" readonly aria-label="备用美团搜索链接"
      /></label>
    </details>
  </div>
</template>
<style scoped>
.takeaway-search-options {
  margin-top: 10px;
}
.app-search {
  width: 100%;
  justify-content: center;
  font-size: 12px;
}
.platform-help,
.search-status {
  font-size: 12px;
  color: #567050;
  line-height: 1.7;
  margin-top: 10px;
  overflow-wrap: anywhere;
}
.platform-help summary {
  cursor: pointer;
}
.platform-help p {
  margin: 9px 0;
}
.platform-help-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  margin-top: 10px;
}
.platform-help-actions > * {
  font-size: 12px;
  color: #456c52;
}
.platform-help label {
  display: block;
  margin-top: 8px;
}
.platform-help input {
  font-size: 12px;
  padding: 8px;
  margin-top: 4px;
  width: 100%;
  min-width: 0;
  background: #fafbf7;
}
</style>
