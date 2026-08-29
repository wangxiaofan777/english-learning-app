<template>
  <view class="chat-page">
    <!-- 场景信息 -->
    <view class="card scene-card">
      <view class="scene-head" @tap="showVocabPanel = !showVocabPanel">
        <view class="scene-titles">
          <text class="scene-title">{{ detail?.titleZh || "…" }}</text>
          <text class="scene-en">{{ detail?.titleEn }}</text>
        </view>
        <text class="chip">{{ detail?.cefr }}</text>
      </view>

      <view v-if="showVocabPanel" class="vocab-panel">
        <text class="muted">本场景核心表达（点击收藏）</text>
        <view
          v-for="v in detail?.vocab || []"
          :key="v.word"
          class="vocab-row"
          @tap="collect(v.word, v.meaningZh)"
        >
          <view class="vocab-text">
            <text class="vocab-word">{{ v.word }} <text class="muted">{{ v.phonetic }}</text></text>
            <text class="vocab-meaning">{{ v.meaningZh }}</text>
          </view>
          <text class="collect-btn">+ 收藏</text>
        </view>
      </view>

      <view class="finish-row">
        <view class="finish-left">
          <text class="muted">{{ detail?.status === "finished" ? "本轮已结束" : "说够 3 轮后来复盘" }}</text>
          <text class="finish-link listen-link" @tap="goListen">🎧 听本场景</text>
        </view>
        <text class="finish-link" @tap="finish">结束并复盘</text>
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
        <view class="bubble" :class="{ 'bubble--ai': m.role === 'assistant' }">
          <view class="bubble-text-row">
            <text class="bubble-text" :user-select="true">{{ m.content }}</text>
            <text
              v-if="m.role === 'assistant' && ttsOk"
              class="speak-btn"
              @tap="speak(m.content)"
            >🔊</text>
          </view>

          <!-- 教学反馈 -->
          <view v-if="m.feedback" class="feedback">
            <view v-if="m.feedback.betterWay" class="fb-item">
              <text class="fb-label">更地道</text>
              <text class="fb-text" :user-select="true">{{ m.feedback.betterWay }}</text>
            </view>
            <view v-if="m.feedback.grammarFix" class="fb-item">
              <text class="fb-label fb-label--amber">语法</text>
              <text class="fb-text" :user-select="true">
                {{ m.feedback.grammarFix.original }} → {{ m.feedback.grammarFix.corrected }}
                （{{ m.feedback.grammarFix.explain }}）
              </text>
            </view>
            <view v-if="m.feedback.vocabHints?.length" class="fb-item">
              <text class="fb-label">生词</text>
              <view class="fb-chips">
                <text
                  v-for="h in m.feedback.vocabHints"
                  :key="h.word"
                  class="chip"
                  @tap="collect(h.word, h.meaningZh)"
                >{{ h.word }} + 收藏</text>
              </view>
            </view>
          </view>
        </view>
      </view>
      <view class="msg-anchor" id="msg-anchor" />
    </scroll-view>

    <!-- 输入区 -->
    <view class="input-bar">
      <template v-if="detail?.status !== 'finished'">
        <!-- #ifdef MP-WEIXIN -->
        <view
          v-if="recorder"
          class="mic-btn"
          :class="{ recording: recording }"
          @touchstart.prevent="startRecord"
          @touchend.prevent="stopRecord"
        >
          <text>{{ recording ? "松开" : "🎙️" }}</text>
        </view>
        <!-- #endif -->
        <input
          v-model="draft"
          class="input"
          :disabled="streaming"
          placeholder="用英语说出来…"
          confirm-type="send"
          @confirm="send"
        />
        <button class="send-btn" :class="{ disabled: !draft.trim() || streaming }" @tap="send">
          {{ streaming ? "…" : "发送" }}
        </button>
      </template>
      <template v-else>
        <button class="btn-ghost restart" @tap="restart">重新开一轮</button>
      </template>
    </view>

    <!-- 复盘弹层 -->
    <view v-if="recap" class="mask" @tap="recap = null">
      <view class="recap-panel" @tap.stop>
        <text class="title-lg">本轮复盘</text>
        <text class="recap-summary">{{ recap.recap.summary }}</text>

        <text class="recap-label">做得好的</text>
        <text v-for="s in recap.recap.strengths" :key="s" class="recap-line good">✓ {{ s }}</text>

        <text class="recap-label">下一步建议</text>
        <text v-for="s in recap.recap.suggestions" :key="s" class="recap-line">· {{ s }}</text>

        <view v-if="recap.vocab.length" class="recap-vocab">
          <text class="recap-label">收藏本场景生词</text>
          <view class="fb-chips">
            <text
              v-for="v in recap.vocab"
              :key="v.word"
              class="chip"
              @tap="collect(v.word, v.meaningZh)"
            >{{ v.word }} +</text>
          </view>
        </view>

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
import { speak, ttsAvailable } from "../../utils/speech";
// #ifdef MP-WEIXIN
import { createRecorder } from "../../utils/speech";
// #endif
import type { ConversationDetail, FinishResult, MessageView } from "../../utils/types";

const detail = ref<ConversationDetail | null>(null);
const messages = ref<MessageView[]>([]);
const draft = ref("");
const streaming = ref(false);
const showVocabPanel = ref(false);
const recap = ref<FinishResult | null>(null);
const lastMsgId = ref("");
const ttsOk = ttsAvailable();
const recording = ref(false);

let conversationId = "";
let recorder: ReturnType<typeof createRecorder> = null;
let seq = 0;

// #ifdef MP-WEIXIN
recorder = createRecorder({
  onResult: (text) => {
    draft.value = text;
    send();
  },
  onError: (msg) => uni.showToast({ title: msg, icon: "none" }),
});
// #endif

onLoad(async (query) => {
  conversationId = (query?.id as string) || "";
  if (!conversationId) {
    uni.navigateBack();
    return;
  }
  detail.value = await api.conversationDetail(conversationId);
  messages.value = detail.value.messages;
  scrollBottom();
});

async function send() {
  const text = draft.value.trim();
  if (!text || streaming.value || detail.value?.status === "finished") return;
  streaming.value = true;
  draft.value = "";

  messages.value.push({ id: `local-u-${seq}`, role: "user", content: text, feedback: null });
  const aiMsg: MessageView = {
    id: `local-ai-${seq}`,
    role: "assistant",
    content: "",
    feedback: null,
  };
  seq += 1;
  messages.value.push(aiMsg);
  scrollBottom();

  streamMessage(conversationId, text, {
    onDelta: (chunk) => {
      aiMsg.content += chunk;
      scrollBottom();
    },
    onMeta: (meta) => {
      aiMsg.id = meta.id;
      aiMsg.feedback = meta.feedback;
    },
    onDone: () => {
      streaming.value = false;
      if (!aiMsg.content) {
        aiMsg.content = "（回复为空，重试一下）";
      }
      scrollBottom();
    },
    onError: (msg) => {
      streaming.value = false;
      aiMsg.content = aiMsg.content || "⚠️ " + msg;
      scrollBottom();
    },
  });
}

async function collect(word: string, meaningZh?: string) {
  await api.addVocab(word, meaningZh, "dialog", detail.value?.scenarioId);
  uni.showToast({ title: `已收藏「${word}」`, icon: "none" });
}

async function finish() {
  if (!conversationId) return;
  recap.value = await api.finishConversation(conversationId);
  if (detail.value) {
    detail.value.status = "finished";
  }
}

function closeRecap() {
  recap.value = null;
  uni.navigateBack();
}

function goListen() {
  if (detail.value?.scenarioId) {
    uni.navigateTo({ url: `/pages/listen/listen?id=${detail.value.scenarioId}` });
  }
}

async function restart() {
  // 真正重开一轮：用同一场景创建新会话并跳转（旧会话已 finished，无法续聊）
  if (!detail.value?.scenarioId) return;
  uni.showLoading({ title: "准备新一轮…" });
  try {
    const conv = await api.createConversation(detail.value.scenarioId);
    uni.hideLoading();
    uni.redirectTo({ url: `/pages/speak/chat?id=${conv.conversationId}` });
  } catch (e) {
    uni.hideLoading();
  }
}

function scrollBottom() {
  nextTick(() => {
    lastMsgId.value = "";
    nextTick(() => {
      lastMsgId.value = "msg-anchor";
    });
  });
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
</script>

<style scoped>
.chat-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.scene-card {
  margin-top: 16rpx;
  padding-bottom: 16rpx;
}

.scene-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.scene-titles {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.scene-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #111827;
}

.scene-en {
  font-size: 22rpx;
  color: #9ca3af;
}

.vocab-panel {
  margin-top: 20rpx;
  border-top: 2rpx solid #f3f4f6;
  padding-top: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.vocab-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f9fafb;
  border-radius: 14rpx;
  padding: 16rpx 20rpx;
}

.vocab-text {
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}

.vocab-word {
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
}

.vocab-meaning {
  font-size: 22rpx;
  color: #6b7280;
}

.collect-btn {
  font-size: 24rpx;
  color: #16a34a;
  font-weight: 600;
}

.finish-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20rpx;
}

.finish-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.listen-link {
  color: #b45309;
}

.finish-link {
  font-size: 26rpx;
  color: #16a34a;
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
}

.msg-row.mine {
  justify-content: flex-end;
}

.bubble {
  max-width: 82%;
  background: #ffffff;
  border-radius: 20rpx 20rpx 20rpx 6rpx;
  padding: 20rpx 24rpx;
  box-shadow: 0 2rpx 10rpx rgba(31, 41, 55, 0.05);
}

.mine .bubble {
  background: #16a34a;
  border-radius: 20rpx 20rpx 6rpx 20rpx;
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

.feedback {
  margin-top: 16rpx;
  border-top: 2rpx dashed #e5e7eb;
  padding-top: 12rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.fb-item {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}

.fb-label {
  flex-shrink: 0;
  font-size: 20rpx;
  color: #15803d;
  background: #ecfdf3;
  border-radius: 8rpx;
  padding: 4rpx 12rpx;
  margin-top: 2rpx;
}

.fb-label--amber {
  color: #b45309;
  background: #fef3c7;
}

.fb-text {
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.5;
}

.fb-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

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
  background: #f0fdf4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
}

.mic-btn.recording {
  background: #dcfce7;
  transform: scale(1.06);
}

.input {
  flex: 1;
  background: #f3f4f6;
  border-radius: 999rpx;
  padding: 18rpx 28rpx;
  font-size: 28rpx;
}

.send-btn {
  background: #16a34a;
  color: #ffffff;
  border-radius: 999rpx;
  font-size: 28rpx;
  padding: 0 36rpx;
  height: 76rpx;
  line-height: 76rpx;
}

.send-btn.disabled {
  background: #a7d7b9;
}

.restart {
  flex: 1;
}

.mask {
  position: fixed;
  inset: 0;
  background: rgba(17, 24, 39, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 99;
}

.recap-panel {
  width: 86%;
  background: #ffffff;
  border-radius: 28rpx;
  padding: 44rpx 36rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
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

.recap-vocab {
  margin-top: 8rpx;
}
</style>
