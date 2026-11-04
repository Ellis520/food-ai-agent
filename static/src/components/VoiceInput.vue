<script setup lang="ts">
import { computed, ref, onBeforeUnmount, onMounted } from "vue";
import { AudioLines, Mic } from "lucide-vue-next";
import { useApp } from "../stores/app";
import { base, isDemo } from "../api";
defineProps<{ disabled?: boolean; label: string }>();
const emit = defineEmits<{
  start: [];
  draft: [text: string];
  transcript: [text: string];
  busy: [value: boolean];
  cancel: [];
}>();
const app = useApp(),
  recording = ref(false),
  waiting = ref(false),
  connecting = ref(false),
  partial = ref(""),
  finalText = ref(""),
  error = ref("");
const status = computed(
  () =>
    error.value ||
    (recording.value
      ? "正在录音 · 松开结束"
      : connecting.value
        ? "正在开启麦克风…"
        : waiting.value
          ? "正在校正语音…"
          : ""),
);
let pointer: number | undefined, key: string | undefined;
let socket: WebSocket | undefined,
  stream: MediaStream | undefined,
  context: AudioContext | undefined,
  node: ScriptProcessorNode | undefined;
let finalTimer: ReturnType<typeof setTimeout>,
  deadline: ReturnType<typeof setTimeout>,
  generation = 0,
  active = false;
function text() {
  return (finalText.value + partial.value)
    .replace(/(?<=[\u3400-\u9fff])\s+(?=[\u3400-\u9fff])/g, "")
    .trimStart();
}
function releaseAudio() {
  node?.disconnect();
  stream?.getTracks().forEach((t) => t.stop());
  if (context && context.state !== "closed")
    void context.close().catch(() => {});
  node = undefined;
  stream = undefined;
  context = undefined;
  recording.value = false;
}
function cleanup() {
  clearTimeout(finalTimer);
  clearTimeout(deadline);
  releaseAudio();
  if (socket) {
    socket.onmessage = null;
    if (!connecting.value) {
      socket.onclose = null;
      socket.onerror = null;
    }
    socket.close();
    socket = undefined;
  }
  waiting.value = false;
  connecting.value = false;
  emit("busy", false);
}
function cancel() {
  const restore = active;
  generation++;
  active = false;
  cleanup();
  if (restore) emit("cancel");
}
function fail(message: string, id: number) {
  if (id !== generation) return;
  generation++;
  active = false;
  error.value = message;
  cleanup();
}
function complete(id: number) {
  if (id !== generation) return;
  const result = finalText.value.trim();
  generation++;
  active = false;
  cleanup();
  if (result) emit("transcript", result);
  else error.value = "未收到离线校正结果，可手动修改已显示的文字或重新录音。";
}
async function start() {
  if (connecting.value || recording.value || waiting.value) return;
  error.value = "";
  if (isDemo || !app.services?.asr) {
    error.value = isDemo ? "演示模式暂不支持录音。" : "语音服务尚未配置。";
    return;
  }
  if (!navigator.mediaDevices?.getUserMedia) {
    error.value = "此页面无法使用麦克风，请通过 HTTPS 或电脑 localhost 打开。";
    return;
  }
  const id = ++generation;
  active = true;
  partial.value = "";
  finalText.value = "";
  connecting.value = true;
  emit("start");
  emit("busy", true);
  try {
    const currentStream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true },
    });
    if (id !== generation) {
      currentStream.getTracks().forEach((t) => t.stop());
      return;
    }
    stream = currentStream;
    const url = new URL(base + "/asr/stream", location.href);
    url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
    const currentSocket = new WebSocket(url);
    socket = currentSocket;
    await new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(
        () => reject(new Error("语音连接超时")),
        10000,
      );
      currentSocket.onopen = () => {
        clearTimeout(timeout);
        resolve();
      };
      currentSocket.onerror = () => {
        clearTimeout(timeout);
        reject(new Error("语音服务无法连接"));
      };
      currentSocket.onclose = () => {
        clearTimeout(timeout);
        reject(new Error("语音连接已关闭"));
      };
    });
    if (id !== generation) {
      currentSocket.close();
      return;
    }
    currentSocket.onmessage = (event) => {
      if (id !== generation) return;
      try {
        const data = JSON.parse(event.data);
        if (data.error) {
          fail(String(data.error), id);
          return;
        }
        const piece = typeof data.text === "string" ? data.text : "";
        if (data.mode === "2pass-offline" || data.mode === "offline") {
          finalText.value += piece;
          partial.value = "";
          emit("draft", text());
          if (waiting.value) {
            clearTimeout(finalTimer);
            finalTimer = setTimeout(() => complete(id), 1800);
          }
        } else if (piece) {
          partial.value += piece;
          emit("draft", text());
        }
      } catch {
        fail("语音响应格式错误，已识别文字仍可手动修改。", id);
      }
    };
    currentSocket.onerror = () =>
      fail("语音连接异常，已识别文字仍可手动修改。", id);
    currentSocket.onclose = () => {
      if (id !== generation) return;
      if (waiting.value && finalText.value) complete(id);
      else fail("语音服务已断开，可重新录音或手动修改文字。", id);
    };
    const currentContext = new AudioContext({ sampleRate: 16000 });
    context = currentContext;
    await currentContext.resume();
    if (id !== generation) {
      if (currentContext.state !== "closed") await currentContext.close();
      return;
    }
    const source = currentContext.createMediaStreamSource(currentStream);
    node = currentContext.createScriptProcessor(4096, 1, 1);
    const mute = currentContext.createGain();
    mute.gain.value = 0;
    node.onaudioprocess = (event) => {
      if (
        id !== generation ||
        currentSocket.readyState !== WebSocket.OPEN ||
        !recording.value
      )
        return;
      const data = event.inputBuffer.getChannelData(0),
        pcm = new ArrayBuffer(data.length * 2),
        view = new DataView(pcm);
      data.forEach((value, i) =>
        view.setInt16(
          i * 2,
          Math.max(-1, Math.min(1, value)) * (value < 0 ? 32768 : 32767),
          true,
        ),
      );
      currentSocket.send(pcm);
    };
    source.connect(node);
    node.connect(mute);
    mute.connect(currentContext.destination);
    connecting.value = false;
    recording.value = true;
    deadline = setTimeout(stop, 110000);
  } catch (e) {
    fail(
      e instanceof DOMException && e.name === "NotAllowedError"
        ? "请在浏览器中允许使用麦克风，再按住录音。"
        : (e as Error).message,
      id,
    );
  }
}
function stop() {
  if (!recording.value) return;
  const id = generation;
  releaseAudio();
  waiting.value = true;
  clearTimeout(deadline);
  if (socket?.readyState !== WebSocket.OPEN) {
    fail("语音连接已关闭，可手动修改文字。", id);
    return;
  }
  socket.send(JSON.stringify({ stop: true }));
  deadline = setTimeout(() => complete(id), 90000);
}
function releaseHold() {
  if (connecting.value) {
    cancel();
    error.value = "语音尚未就绪，请再按住试一次。";
  } else stop();
}
function pointerDown(event: PointerEvent) {
  if (
    !event.isPrimary ||
    event.button !== 0 ||
    pointer !== undefined ||
    key !== undefined ||
    active
  )
    return;
  const button = event.currentTarget as HTMLButtonElement;
  button.setPointerCapture(event.pointerId);
  pointer = event.pointerId;
  void start();
}
function pointerUp(event: PointerEvent) {
  if (pointer !== event.pointerId) return;
  pointer = undefined;
  releaseHold();
}
function pointerCancel(event: PointerEvent) {
  if (pointer !== event.pointerId) return;
  pointer = undefined;
  cancel();
}
function keyDown(event: KeyboardEvent) {
  if (event.key === "Escape" && active) {
    event.preventDefault();
    pointer = undefined;
    key = undefined;
    cancel();
  }
  if (event.key !== " " && event.key !== "Enter") return;
  event.preventDefault();
  if (event.repeat || pointer !== undefined || key !== undefined || active)
    return;
  key = event.key;
  void start();
}
function keyUp(event: KeyboardEvent) {
  if (key !== event.key) return;
  event.preventDefault();
  key = undefined;
  releaseHold();
}
function blur() {
  if (key === undefined) return;
  key = undefined;
  cancel();
}
function accessibleClick(event: MouseEvent) {
  if (event.detail !== 0 || pointer !== undefined || key !== undefined) return;
  if (recording.value || connecting.value) releaseHold();
  else void start();
}
function visibilityChanged() {
  if (!document.hidden || (!recording.value && !connecting.value)) return;
  pointer = undefined;
  key = undefined;
  cancel();
}
onMounted(() =>
  document.addEventListener("visibilitychange", visibilityChanged),
);
onBeforeUnmount(() => {
  document.removeEventListener("visibilitychange", visibilityChanged);
  cancel();
});
</script>
<template>
  <div class="voice-control">
    <button
      type="button"
      class="icon-btn voice-key"
      :disabled="disabled"
      :aria-label="label"
      title="按住录音，松开结束"
      :class="{ recording, working: connecting || waiting }"
      @pointerdown="pointerDown"
      @pointerup="pointerUp"
      @pointercancel="pointerCancel"
      @lostpointercapture="pointerCancel"
      @contextmenu.prevent
      @keydown="keyDown"
      @keyup="keyUp"
      @blur="blur"
      @click="accessibleClick"
    >
      <component
        :is="recording ? AudioLines : Mic"
        :size="20"
        :stroke-width="1.8"
        aria-hidden="true"
      />
    </button>
    <span
      class="voice-status"
      :class="{ visible: status, 'voice-error': error }"
      role="status"
      >{{ status }}</span
    >
  </div>
</template>

<style scoped>
.voice-control {
  position: relative;
  flex: none;
  display: inline-flex;
}
.voice-key {
  width: 44px;
  height: 44px;
  justify-content: center;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
}
.voice-key.recording {
  background: #3d644e;
  color: #fff;
}
.voice-key.working {
  background: #edf2e8;
  color: #3d644e;
}
.voice-status {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  pointer-events: none;
  z-index: 2;
}
.voice-status.visible {
  width: max-content;
  max-width: min(240px, 70vw);
  padding: 6px 10px;
  border-radius: 8px;
  background: #284c3a;
  color: #fff;
  font-size: 12px;
  line-height: 1.5;
  box-shadow: 0 2px 10px #1d3a2622;
}
.voice-status.voice-error {
  background: #783d2e;
}
</style>
