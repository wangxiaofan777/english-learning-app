<template>
  <view class="shadow-page">
    <!-- 跟读中 -->
    <template v-if="phase === 'practicing' && line">
      <view class="top-row">
        <text class="muted">{{ idx + 1 }} / {{ lines.length }} 句</text>
        <text class="chip" v-if="asrOk">🎙️ 识别打分</text>
        <text class="chip chip--gray" v-else>自评模式</text>
      </view>
      <view class="progress-bar">
        <view class="progress-inner" :style="{ width: progressPercent }" />
      </view>

      <view class="card card-body">
        <text class="step-hint">① 听示范</text>
        <view class="target-block">
          <text class="line-en" :user-select="true">{{ line.en }}</text>
          <text class="line-zh">{{ line.zh }}</text>
          <view class="demo-btn" @tap="playDemo">
            <text>{{ demoPlaying ? "播放中…" : "▶ 播放示范" }}</text>
          </view>
        </view>

        <text class="step-hint">② 你来说</text>

        <!-- 有识别能力：录音 → 打分；识别失败则降级自评 -->
        <template v-if="asrOk && !result && !selfFallback">
          <view
            class="record-btn"
            :class="{ recording }"
            @tap="toggleRecord"
          >
            <text class="record-icon">{{ recording ? "⏹" : "🎙️" }}</text>
            <text class="record-label">{{ recording ? "说完点这里结束" : "点这里开始跟读" }}</text>
          </view>
          <view v-if="recordingFailed" class="self-fallback" @tap="selfFallback = true">
            <text class="muted">识别不了？切换到自评模式</text>
          </view>
        </template>

        <!-- 识别结果：逐词高亮 -->
        <template v-if="result">
          <view class="score-block">
            <view class="score-ring" :class="scoreLevel">
              <text class="score-num">{{ result.score }}</text>
            </view>
            <text class="score-label">{{ scoreText }}</text>
            <view class="words">
              <text
                v-for="(w, k) in result.words"
                :key="k"
                class="word"
                :class="w.hit ? 'hit' : 'miss'"
              >{{ w.word }}</text>
            </view>
            <view v-if="result.extra.length" class="extra-row">
              <text class="muted">多说了：</text>
              <text v-for="(w, k) in result.extra" :key="'e' + k" class="word extra">{{ w }}</text>
            </view>
            <text v-if="saidText && saidText !== '（自评）'" class="said-text muted">识别到：{{ saidText }}</text>
          </view>
        </template>

        <!-- 无识别能力或降级：自评 -->
        <template v-if="(!asrOk || selfFallback) && !result">
          <view class="self-row">
            <view class="self-btn" @tap="selfGrade(3)">
              <text>😎 读得顺</text>
            </view>
            <view class="self-btn" @tap="selfGrade(2)">
              <text>🙂 磕磕绊绊</text>
            </view>
            <view class="self-btn" @tap="selfGrade(1)">
              <text>😅 再练练</text>
            </view>
          </view>
        </template>
      </view>

      <view class="nav-row">
        <button class="btn-ghost" @tap="reRecord" v-if="result">重录这句</button>
        <button class="btn-primary nav-next" @tap="next">{{ isLast ? "完成跟读" : "下一句" }}</button>
      </view>
    </template>

    <!-- 完成 -->
    <template v-else-if="phase === 'done'">
      <view class="card done-card">
        <text class="done-emoji">🗣️</text>
        <text class="done-title">跟读完成</text>
        <text class="muted">平均得分 {{ averageScore }}。红词就是明天复习的重点。</text>
        <button class="btn-primary back-btn" @tap="goBack">返回大厅</button>
      </view>
    </template>

    <view v-else class="card empty">
      <text class="muted">场景加载中…</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad, onUnload } from "@dcloudio/uni-app";
import { api } from "../../utils/api";
import { speakAsync } from "../../utils/speech";
import { startRecognition, asrAvailable } from "../../utils/speech";
import { scoreShadowing, type ShadowResult } from "../../utils/score";
import type { ScenarioDetail } from "../../utils/types";

const scenarioId = ref("");
const detail = ref<ScenarioDetail | null>(null);
const idx = ref(0);
const demoPlaying = ref(false);
const recording = ref(false);
const result = ref<ShadowResult | null>(null);
const saidText = ref("");
const scores = ref<number[]>([]);
const phase = ref<"loading" | "practicing" | "done">("loading");
const asrOk = asrAvailable();
const selfFallback = ref(false);
const recordingFailed = ref(false);

let recognizer: { stop: () => void } | null = null;
let stopRequested = false;

const lines = computed(() => detail.value?.lines || []);
const line = computed(() => lines.value[idx.value]);
const isLast = computed(() => idx.value === lines.value.length - 1);
const progressPercent = computed(() =>
  lines.value.length === 0 ? "0%" : `${Math.round(((idx.value + 1) / lines.value.length) * 100)}%`
);
const averageScore = computed(() =>
  scores.value.length === 0
    ? 0
    : Math.round(scores.value.reduce((a, b) => a + b, 0) / scores.value.length)
);
const scoreLevel = computed(() =>
  !result.value ? "" : result.value.score >= 80 ? "good" : result.value.score >= 50 ? "mid" : "low"
);
const scoreText = computed(() => {
  const s = result.value?.score ?? 0;
  if (s >= 80) return "很地道！";
  if (s >= 50) return "不错，红词多练几遍";
  return "再来一次，慢一点更清楚";
});

onLoad(async (query) => {
  scenarioId.value = (query?.id as string) || "";
  detail.value = await api.scenarioDetail(scenarioId.value);
  phase.value = "practicing";
});

onUnload(() => {
  stopRequested = true;
  recognizer?.stop();
});

async function playDemo() {
  if (!line.value || demoPlaying.value) return;
  demoPlaying.value = true;
  await speakAsync(line.value.en);
  if (!stopRequested) {
    demoPlaying.value = false;
  }
}

function toggleRecord() {
  if (recording.value) {
    recognizer?.stop();
    return;
  }
  recording.value = true;
  recognizer = startRecognition({
    onResult: (text) => {
      saidText.value = text;
      recordingFailed.value = false;
      result.value = scoreShadowing(line.value?.en || "", text);
    },
    onError: (msg) => {
      recordingFailed.value = true;
      uni.showToast({ title: msg, icon: "none" });
    },
    onEnd: () => {
      recording.value = false;
      if (!result.value && saidText.value) {
        result.value = scoreShadowing(line.value?.en || "", saidText.value);
      }
    },
  });
  if (!recognizer) {
    recording.value = false;
    uni.showToast({ title: "当前环境不支持录音识别", icon: "none" });
  }
}

/** 识别不可用时的自评：3/2/1 映射为分数档 */
function selfGrade(level: number) {
  const base = level === 3 ? 85 : level === 2 ? 60 : 30;
  saidText.value = "（自评）";
  result.value = { score: base, words: [], extra: [] };
}

function reRecord() {
  result.value = null;
  saidText.value = "";
  selfFallback.value = false;
  recordingFailed.value = false;
}

async function next() {
  if (result.value) {
    scores.value.push(result.value.score);
  }
  result.value = null;
  saidText.value = "";
  selfFallback.value = false;
  recordingFailed.value = false;
  if (!isLast.value) {
    idx.value += 1;
    playDemo();
    return;
  }
  const minutes = Math.max(1, Math.round(lines.value.length / 4));
  try {
    await api.recordPractice("shadowing", minutes, lines.value.length);
  } catch (e) {
    // 计时失败不打断完成页
  }
  phase.value = "done";
}

function goBack() {
  uni.switchTab({ url: "/pages/speak/hall" });
}
</script>

<style scoped>
.shadow-page {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
}

.top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 40rpx 20rpx;
}

.progress-bar {
  height: 12rpx;
  background: #e5e7eb;
  border-radius: 999rpx;
  overflow: hidden;
  margin: 0 40rpx 28rpx;
}

.progress-inner {
  height: 100%;
  background: #16a34a;
  transition: width 0.2s;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.step-hint {
  font-size: 24rpx;
  color: #9ca3af;
}

.target-block {
  background: #f0fdf4;
  border-radius: 20rpx;
  padding: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.line-en {
  font-size: 34rpx;
  font-weight: 600;
  color: #111827;
  line-height: 1.5;
}

.line-zh {
  font-size: 24rpx;
  color: #6b7280;
}

.demo-btn {
  align-self: flex-start;
  background: #16a34a;
  color: #ffffff;
  border-radius: 999rpx;
  font-size: 24rpx;
  padding: 12rpx 32rpx;
  margin-top: 8rpx;
}

.record-btn {
  border: 3rpx dashed #16a34a;
  border-radius: 24rpx;
  padding: 48rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  background: #ffffff;
}

.record-btn.recording {
  background: #dcfce7;
  border-style: solid;
}

.record-icon {
  font-size: 60rpx;
}

.record-label {
  font-size: 26rpx;
  color: #374151;
}

.self-fallback {
  text-align: center;
  padding: 4rpx 0;
}

.score-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.score-ring {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.score-ring.good {
  background: #dcfce7;
}

.score-ring.mid {
  background: #fef3c7;
}

.score-ring.low {
  background: #fee2e2;
}

.score-num {
  font-size: 56rpx;
  font-weight: 800;
  color: #111827;
}

.score-label {
  font-size: 26rpx;
  color: #374151;
}

.words {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12rpx;
}

.word {
  font-size: 28rpx;
  padding: 6rpx 16rpx;
  border-radius: 10rpx;
}

.word.hit {
  background: #dcfce7;
  color: #15803d;
}

.word.miss {
  background: #fee2e2;
  color: #b91c1c;
  text-decoration: line-through;
}

.word.extra {
  background: #f3f4f6;
  color: #6b7280;
}

.extra-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8rpx;
}

.said-text {
  font-size: 22rpx;
}

.self-row {
  display: flex;
  gap: 16rpx;
}

.self-btn {
  flex: 1;
  background: #f9fafb;
  border-radius: 18rpx;
  padding: 28rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #374151;
}

.nav-row {
  display: flex;
  gap: 20rpx;
  padding: 24rpx 24rpx 0;
}

.nav-row .btn-ghost,
.nav-row .btn-primary {
  flex: 1;
}

.nav-next {
  flex: 2;
}

.done-card {
  margin-top: 140rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 90rpx 40rpx;
}

.done-emoji {
  font-size: 80rpx;
}

.done-title {
  font-size: 38rpx;
  font-weight: 700;
  color: #111827;
}

.back-btn {
  width: 70%;
  margin-top: 8rpx;
}

.empty {
  margin-top: 200rpx;
  text-align: center;
}
</style>
