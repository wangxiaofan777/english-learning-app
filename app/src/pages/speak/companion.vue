<template>
  <view class="companion-page">
    <!-- ① 选择陪练 -->
    <template v-if="!conversationId">
      <view class="picker-head">
        <text class="title-lg">AI 陪练搭子</text>
        <text class="picker-sub">像真人一样陪你聊：有性格、会记得你说过的话，说错了悄悄纠正你</text>
      </view>

      <view
        v-for="p in personas"
        :key="p.key"
        class="card persona-card"
        @tap="start(p.key)"
      >
        <view class="persona-top">
          <view class="persona-avatar">{{ p.avatar }}</view>
          <view class="persona-titles">
            <view class="persona-name-row">
              <text class="persona-name">{{ p.name }}</text>
              <text class="persona-namezh">{{ p.nameZh }}</text>
              <text class="chip chip--indigo">{{ p.styleLabel }}</text>
            </view>
            <text class="persona-tagline">{{ p.tagline }}</text>
          </view>
        </view>
        <view v-if="p.memoryCount > 0" class="persona-memory">
          <text class="persona-memory-text">🧠 记得你 {{ p.memoryCount }} 件事：{{ p.memoryPreview.join(" · ") }}</text>
        </view>
        <view class="persona-go">找 {{ p.name }} 开聊 →</view>
      </view>
    </template>

    <!-- ② 聊天 -->
    <template v-else>
      <!-- 陪练信息 -->
      <view class="card head-card">
        <view class="head-row">
          <view class="persona-avatar small">{{ companion?.avatar }}</view>
          <view class="head-titles">
            <text class="head-name">{{ companion?.name }} · {{ companion?.nameZh }}</text>
            <text class="head-status">在线 · 会记得你说过的话</text>
          </view>
          <view class="head-actions">
            <text class="head-btn" :class="{ off: !autoVoice }" @tap="toggleVoice">{{ autoVoice ? "🔊" : "🔇" }}</text>
            <text class="head-btn" @tap="openMemory">🧠</text>
          </view>
        </view>
        <view class="finish-row">
          <text class="muted">{{ status === "finished" ? "本轮已结束" : "随时可以结束并复盘" }}</text>
          <text v-if="status !== 'finished'" class="finish-link" @tap="finish">结束并复盘</text>
        </view>
      </view>

      <!-- 消息流 -->
      <scroll-view class="msgs" scroll-y :scroll-into-view="lastMsgId" scroll-with-animation>
        <view
          v-for="m in messages"
          :key="m.id"
          :id="`msg-${m.id}`"
          class="msg-row"
          :class="{ mine: m.role === 'user' }"
        >
          <view v-if="m.role === 'assistant'" class="ai-avatar">{{ companion?.avatar }}</view>
          <view class="bubble" :class="{ 'bubble--ai': m.role === 'assistant' }">
            <view class="bubble-text-row">
              <text class="bubble-text" :user-select="true">{{ m.content }}</text>
              <text
                v-if="m.role === 'assistant' && ttsOk && m.content"
                class="speak-btn"
                @tap="speak(m.content)"
              >🔊</text>
            </view>

            <!-- 小声说：偶发的轻量纠正，不打断聊天感 -->
            <view v-if="hasTip(m)" class="whisper">
              <text class="whisper-label">小声说</text>
              <text class="whisper-text" :user-select="true">{{ tipText(m) }}</text>
            </view>
          </view>
        </view>

        <!-- 正在输入 -->
        <view v-if="typing" class="msg-row">
          <view class="ai-avatar">{{ companion?.avatar }}</view>
          <view class="bubble bubble--ai typing-bubble">
            <text class="typing-name">{{ companion?.name }} 正在输入</text>
            <view class="dots">
              <view class="dot" /><view class="dot" /><view class="dot" />
            </view>
          </view>
        </view>
        <view class="msg-anchor" id="msg-anchor" />
      </scroll-view>

      <!-- 输入区 -->
      <view class="input-bar">
        <template v-if="status !== 'finished'">
          <!-- #ifdef MP-WEIXIN -->
          <view
            v-if="micUsable"
            class="mic-btn"
            :class="{ recording: recording }"
            @touchstart.prevent="startRecord"
            @touchend.prevent="stopRecord"
          >
            <text>{{ recording ? "松开" : "🎙️" }}</text>
          </view>
          <!-- #endif -->
          <!-- #ifdef H5 -->
          <view
            v-if="h5MicUsable"
            class="mic-btn"
            :class="{ recording: recording }"
            @tap="h5TapMic"
          >
            <text>{{ recording ? "🛑" : "🎙️" }}</text>
          </view>
          <!-- #endif -->
          <input
            v-model="draft"
            class="input"
            :disabled="streaming"
            placeholder="用英语随便聊…"
            confirm-type="send"
            @confirm="send"
          />
          <button class="send-btn send-btn--indigo" :class="{ disabled: !draft.trim() || streaming }" @tap="send">
            {{ streaming ? "…" : "发送" }}
          </button>
        </template>
        <template v-else>
          <button class="btn-ghost restart" @tap="backToPicker">换个陪练 / 再来一轮</button>
        </template>
      </view>
    </template>

    <!-- 记忆面板 -->
    <view v-if="memoryOpen" class="mask" @tap="memoryOpen = false">
      <view class="panel" @tap.stop>
        <text class="title-lg">{{ companion?.name }} 记得你</text>
        <text class="muted">陪练会记住你聊到的身份、爱好和计划，下次开场会接着聊。点一下可以让它忘掉。</text>
        <scroll-view scroll-y class="memory-list">
          <view v-for="(f, i) in memoryFacts" :key="f" class="memory-row" @tap="forget(i)">
            <text class="memory-text">{{ f }}</text>
            <text class="memory-del">忘掉</text>
          </view>
          <view v-if="memoryFacts.length === 0" class="memory-empty">
            <text class="muted">还没有记住什么，多聊聊它就认识你了</text>
          </view>
        </scroll-view>
        <button class="btn-primary" @tap="memoryOpen = false">好的</button>
      </view>
    </view>

    <!-- 复盘弹层 -->
    <view v-if="recap" class="mask" @tap="recap = null">
      <view class="panel" @tap.stop>
        <text class="title-lg">本轮复盘</text>
        <text class="recap-summary">{{ recap.recap.summary }}</text>
        <text class="recap-label">做得好的</text>
        <text v-for="s in recap.recap.strengths" :key="s" class="recap-line good">✓ {{ s }}</text>
        <text class="recap-label">下一步建议</text>
        <text v-for="s in recap.recap.suggestions" :key="s" class="recap-line">· {{ s }}</text>
        <button class="btn-primary" @tap="closeRecap">完成</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { nextTick, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { api } from "../../utils/api";
import { streamMessage } from "../../utils/sse";
import { speak, ttsAvailable, startRecognition } from "../../utils/speech";
// #ifdef MP-WEIXIN
import { createRecorder } from "../../utils/speech";
// #endif
import type {
  CompanionMessage,
  CompanionStartResult,
  CompanionView,
  FinishResult,
} from "../../utils/types";
import { ensureAuth } from "../../stores/user";

const personas = ref<CompanionView[]>([]);
const companion = ref<CompanionView | null>(null);
const conversationId = ref("");
const messages = ref<CompanionMessage[]>([]);
const draft = ref("");
const streaming = ref(false);
const typing = ref(false);
const status = ref("active");
const lastMsgId = ref("");
const memoryOpen = ref(false);
const memoryFacts = ref<string[]>([]);
const recap = ref<FinishResult | null>(null);
const recording = ref(false);

const ttsOk = ttsAvailable();
const autoVoice = ref(uni.getStorageSync("companion_auto_voice") !== "off");

let seq = 0;
let recorder: ReturnType<typeof createRecorder> = null;
// #ifdef MP-WEIXIN
recorder = createRecorder({
  onResult: (text) => {
    draft.value = text;
    send();
  },
  onError: (msg) => uni.showToast({ title: msg, icon: "none" }),
});
// #endif

/** 麦克风按钮：微信端按住说话，H5 端点一下开始/停止 */
const micUsable = ref(false);
const h5MicUsable = ref(false);
// #ifdef MP-WEIXIN
micUsable.value = true;
// #endif
// #ifdef H5
h5MicUsable.value = true;
let h5Rec: { stop: () => void } | null = null;
// #endif

onLoad(async (query) => {
  if (!ensureAuth()) return;
  const key = query?.key as string;
  if (key) {
    await start(key);
  } else {
    personas.value = await api.companionList();
  }
});

async function start(key: string) {
  uni.showLoading({ title: "叫人去了…" });
  try {
    const result = await api.companionStart(key);
    uni.hideLoading();
    enterChat(result);
  } catch (e) {
    uni.hideLoading();
  }
}

function enterChat(result: CompanionStartResult) {
  companion.value = result.companion;
  conversationId.value = result.conversationId;
  messages.value = result.messages.map((m) => ({ ...m, feedback: m.feedback ?? null }));
  status.value = "active";
  memoryFacts.value = [];
  scrollBottom();
}

async function send() {
  const text = draft.value.trim();
  if (!text || streaming.value || status.value === "finished") return;
  streaming.value = true;
  typing.value = true;
  draft.value = "";

  messages.value.push({ id: `local-u-${seq}`, role: "user", content: text, feedback: null });
  const aiMsg: CompanionMessage = {
    id: `local-ai-${seq}`,
    role: "assistant",
    content: "",
    feedback: null,
  };
  seq += 1;
  messages.value.push(aiMsg);
  scrollBottom();

  streamMessage(conversationId.value, text, {
    onDelta: (chunk) => {
      aiMsg.content += chunk;
      scrollBottom();
    },
    onMeta: (meta) => {
      aiMsg.id = meta.id;
      aiMsg.feedback = meta.feedback ?? null;
    },
    onDone: () => {
      streaming.value = false;
      typing.value = false;
      if (!aiMsg.content) {
        aiMsg.content = "（网络好像不太好，再说一次？）";
      }
      // 像真人一样：说完就开口念出来
      if (autoVoice.value && aiMsg.content) {
        speak(aiMsg.content);
      }
      scrollBottom();
    },
    onError: (msg) => {
      streaming.value = false;
      typing.value = false;
      aiMsg.content = aiMsg.content || "⚠️ " + msg;
      scrollBottom();
    },
  });
}

function hasTip(m: CompanionMessage): boolean {
  if (!m.feedback || m.role !== "assistant") return false;
  return Boolean(
    (m.feedback.betterWay || "").trim() ||
      (m.feedback.grammarFix && m.feedback.grammarFix.corrected)
  );
}

function tipText(m: CompanionMessage): string {
  const fb = m.feedback!;
  if (fb.grammarFix && fb.grammarFix.corrected) {
    return `${fb.grammarFix.original} → ${fb.grammarFix.corrected}（${fb.grammarFix.explain}）`;
  }
  return `试试这样说更地道：「${fb.betterWay}」`;
}

async function finish() {
  if (!conversationId.value) return;
  recap.value = await api.finishConversation(conversationId.value);
  status.value = "finished";
}

function closeRecap() {
  recap.value = null;
  backToPicker();
}

function backToPicker() {
  conversationId.value = "";
  messages.value = [];
  companion.value = null;
  api
    .companionList()
    .then((list) => (personas.value = list))
    .catch(() => undefined);
}

async function openMemory() {
  if (!companion.value) return;
  try {
    memoryFacts.value = await api.companionMemory(companion.value.key);
  } catch (e) {
    memoryFacts.value = [];
  }
  memoryOpen.value = true;
}

async function forget(index: number) {
  if (!companion.value) return;
  const fact = memoryFacts.value[index];
  memoryFacts.value = await api.companionForget(companion.value.key, fact);
  // 刷新选择页里的记忆预览
  const fresh = await api.companionList();
  personas.value = fresh;
  const mine = fresh.find((p) => p.key === companion.value?.key);
  if (mine) companion.value = mine;
}

function toggleVoice() {
  autoVoice.value = !autoVoice.value;
  uni.setStorageSync("companion_auto_voice", autoVoice.value ? "on" : "off");
  if (!autoVoice.value) {
    uni.showToast({ title: "已关闭自动朗读", icon: "none" });
  }
}

// #ifdef MP-WEIXIN
function startRecord() {
  if (!recorder || streaming.value) return;
  recording.value = true;
  recorder.start();
}

function stopRecord() {
  if (!recorder) return;
  recording.value = false;
  recorder.stop();
}
// #endif

// #ifdef H5
function h5TapMic() {
  if (recording.value) {
    h5Rec?.stop();
    return;
  }
  if (streaming.value) return;
  h5Rec = startRecognition({
    onResult: (text) => {
      draft.value = text;
      send();
    },
    onError: (msg) => uni.showToast({ title: msg, icon: "none" }),
    onEnd: () => {
      recording.value = false;
    },
  });
  if (h5Rec) {
    recording.value = true;
  }
}
// #endif

function scrollBottom() {
  nextTick(() => {
    lastMsgId.value = "";
    nextTick(() => {
      lastMsgId.value = "msg-anchor";
    });
  });
}
</script>

<style scoped>
.companion-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ---------- 选择陪练 ---------- */
.picker-head {
  padding: 28rpx 32rpx 8rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.picker-sub {
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.6;
}

.persona-card {
  margin-top: 20rpx;
}

.persona-top {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.persona-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #eef2ff, #fae8ff);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52rpx;
  flex-shrink: 0;
}

.persona-avatar.small {
  width: 72rpx;
  height: 72rpx;
  font-size: 38rpx;
  border-radius: 20rpx;
}

.persona-titles {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  flex: 1;
}

.persona-name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.persona-name {
  font-size: 32rpx;
  font-weight: 800;
  color: #111827;
}

.persona-namezh {
  font-size: 24rpx;
  color: #6b7280;
}

.chip--indigo {
  color: #4f46e5;
  background: #eef2ff;
}

.persona-tagline {
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.5;
}

.persona-memory {
  margin-top: 18rpx;
  background: #f5f3ff;
  border-radius: 14rpx;
  padding: 14rpx 20rpx;
}

.persona-memory-text {
  font-size: 22rpx;
  color: #6d28d9;
  line-height: 1.5;
}

.persona-go {
  margin-top: 18rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: #4f46e5;
}

/* ---------- 聊天 ---------- */
.head-card {
  margin-top: 16rpx;
  padding-bottom: 16rpx;
}

.head-row {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.head-titles {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.head-name {
  font-size: 30rpx;
  font-weight: 700;
  color: #111827;
}

.head-status {
  font-size: 22rpx;
  color: #16a34a;
}

.head-actions {
  display: flex;
  gap: 10rpx;
}

.head-btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background: #f5f3ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
}

.head-btn.off {
  background: #f3f4f6;
  opacity: 0.6;
}

.finish-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 18rpx;
}

.finish-link {
  font-size: 26rpx;
  color: #4f46e5;
  font-weight: 600;
}

.msgs {
  flex: 1;
  padding: 8rpx 24rpx 24rpx;
  box-sizing: border-box;
  height: 0;
}

.msg-row {
  display: flex;
  margin-top: 24rpx;
  align-items: flex-end;
  gap: 12rpx;
}

.msg-row.mine {
  justify-content: flex-end;
}

.ai-avatar {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #eef2ff, #fae8ff);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  flex-shrink: 0;
}

.bubble {
  max-width: 78%;
  background: #ffffff;
  border-radius: 20rpx 20rpx 20rpx 6rpx;
  padding: 20rpx 24rpx;
  box-shadow: 0 2rpx 10rpx rgba(31, 41, 55, 0.05);
}

.mine .bubble {
  background: #4f46e5;
  border-radius: 20rpx 20rpx 6rpx 20rpx;
}

.bubble--ai {
  background: #ffffff;
}

.bubble-text-row {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}

.bubble-text {
  font-size: 30rpx;
  line-height: 1.55;
  color: #111827;
  word-break: break-all;
}

.mine .bubble-text {
  color: #ffffff;
}

.speak-btn {
  font-size: 26rpx;
}

.whisper {
  margin-top: 14rpx;
  border-top: 2rpx dashed #e0e7ff;
  padding-top: 10rpx;
  display: flex;
  align-items: flex-start;
  gap: 10rpx;
}

.whisper-label {
  flex-shrink: 0;
  font-size: 20rpx;
  color: #4f46e5;
  background: #eef2ff;
  border-radius: 8rpx;
  padding: 4rpx 12rpx;
  margin-top: 2rpx;
}

.whisper-text {
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.5;
}

/* 正在输入 */
.typing-bubble {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 18rpx 24rpx;
}

.typing-name {
  font-size: 22rpx;
  color: #9ca3af;
}

.dots {
  display: flex;
  gap: 6rpx;
}

.dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #a5b4fc;
  animation: dot-bounce 1.2s infinite ease-in-out;
}

.dot:nth-child(2) {
  animation-delay: 0.15s;
}

.dot:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes dot-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-6rpx);
    opacity: 1;
  }
}

/* 输入区 */
.input-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  border-top: 2rpx solid #eef0f2;
}

.mic-btn {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: #eef2ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
}

.mic-btn.recording {
  background: #e0e7ff;
  transform: scale(1.06);
}

.input {
  flex: 1;
  background: #f3f4f6;
  border-radius: 999rpx;
  padding: 18rpx 28rpx;
  font-size: 28rpx;
}

.send-btn--indigo {
  background: #4f46e5;
  color: #ffffff;
  border-radius: 999rpx;
  font-size: 28rpx;
  padding: 0 36rpx;
  height: 76rpx;
  line-height: 76rpx;
}

.send-btn--indigo.disabled {
  background: #c7d2fe;
}

.restart {
  flex: 1;
}

/* ---------- 弹层 ---------- */
.mask {
  position: fixed;
  inset: 0;
  background: rgba(17, 24, 39, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 99;
}

.panel {
  width: 86%;
  max-height: 76vh;
  background: #ffffff;
  border-radius: 28rpx;
  padding: 44rpx 36rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.memory-list {
  max-height: 44vh;
  margin-top: 8rpx;
}

.memory-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f9fafb;
  border-radius: 14rpx;
  padding: 16rpx 20rpx;
  margin-top: 12rpx;
}

.memory-text {
  font-size: 26rpx;
  color: #374151;
  line-height: 1.5;
  margin-right: 16rpx;
}

.memory-del {
  flex-shrink: 0;
  font-size: 22rpx;
  color: #dc2626;
  font-weight: 600;
}

.memory-empty {
  padding: 40rpx 0;
  text-align: center;
}

.recap-summary {
  font-size: 30rpx;
  color: #374151;
  line-height: 1.6;
}

.recap-label {
  font-size: 24rpx;
  color: #9ca3af;
  margin-top: 12rpx;
}

.recap-line {
  font-size: 28rpx;
  color: #374151;
  line-height: 1.6;
}

.recap-line.good {
  color: #15803d;
}
</style>
