<template>
  <view class="boss-page">
    <!-- 战斗中 -->
    <template v-if="phase === 'fighting'">
      <view class="timer-row">
        <view class="timer-bar">
          <view class="timer-inner" :style="{ width: timePercent }" :class="{ danger: timeLeft <= 10 }" />
        </view>
        <text class="timer-text" :class="{ danger: timeLeft <= 10 }">{{ timeLeft }}s</text>
      </view>

      <view class="card card-body">
        <text class="round-hint">第 {{ index + 1 }} / {{ questions.length }} 题 · 答对 {{ correct }}</text>
        <text class="type-chip">{{ typeLabel }}</text>
        <template v-if="current?.type === 'zh2en'">
          <text class="prompt">{{ current.prompt }}</text>
          <text class="sub-hint">选出正确的英文</text>
        </template>
        <template v-else-if="current?.type === 'listen'">
          <view class="listen-btn" @tap="playCurrentAudio">
            <text>🔊 点我再听一次</text>
          </view>
          <text class="sub-hint">听发音，选出中文意思</text>
        </template>
        <template v-else>
          <text class="prompt">{{ current.prompt }}</text>
          <text class="sub-hint">选出对应的英文句子</text>
        </template>

        <view class="options">
          <view
            v-for="opt in current?.options || []"
            :key="opt"
            class="option"
            :class="optionClass(opt)"
            @tap="choose(opt)"
          >
            <text>{{ opt }}</text>
          </view>
        </view>
      </view>
    </template>

    <!-- 通关 -->
    <template v-else-if="phase === 'passed'">
      <view class="card done-card">
        <text class="done-emoji">⚔️</text>
        <text class="done-title">Boss 已击败！</text>
        <text class="muted">{{ correct }} / {{ questions.length }} · 获得 40 经验 · 单元课时完成</text>
        <button class="btn-primary back-btn" @tap="goBack">返回课程</button>
      </view>
    </template>

    <!-- 失败 -->
    <template v-else-if="phase === 'failed'">
      <view class="card done-card">
        <text class="done-emoji">🛡️</text>
        <text class="done-title">差一点点</text>
        <text class="muted">{{ correct }} / {{ questions.length }} · 通关需要答对 6 题，Boss 血量还剩一点</text>
        <button class="btn-primary back-btn" @tap="restart">再战</button>
        <button class="btn-ghost back-btn" @tap="goBack">先回去练练</button>
      </view>
    </template>

    <view v-else class="card empty">
      <text class="muted">正在集结题目…</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad, onUnload } from "@dcloudio/uni-app";
import { api } from "../../utils/api";
import { speak } from "../../utils/speech";
import type { ScenarioDetail } from "../../utils/types";

interface BossQuestion {
  type: "zh2en" | "listen" | "read";
  prompt: string;
  speakText?: string;
  answer: string;
  options: string[];
}

const scenarioId = ref("");
const phase = ref<"loading" | "fighting" | "passed" | "failed">("loading");
const questions = ref<BossQuestion[]>([]);
const index = ref(0);
const chosen = ref("");
const correct = ref(0);
const timeLeft = ref(60);
const TIME_LIMIT = 60;
const PASS_LINE = 6;

let timer: ReturnType<typeof setInterval> | null = null;

const current = computed(() => questions.value[index.value]);
const typeLabel = computed(() =>
  current.value?.type === "zh2en" ? "词汇突击"
    : current.value?.type === "listen" ? "听音辨义"
    : "句子反击"
);
const timePercent = computed(() => `${(timeLeft.value / TIME_LIMIT) * 100}%`);

onLoad(async (query) => {
  scenarioId.value = (query?.id as string) || "";
  const detail: ScenarioDetail = await api.scenarioDetail(scenarioId.value);
  questions.value = buildQuestions(detail);
  startFight();
});

onUnload(() => stopTimer());

function buildQuestions(detail: ScenarioDetail): BossQuestion[] {
  const pool: BossQuestion[] = [];
  for (const v of detail.vocab) {
    const distractors = shuffle(detail.vocab.filter((x) => x.word !== v.word))
      .slice(0, 3).map((x) => x.word);
    if (distractors.length < 3) continue;
    pool.push({
      type: "zh2en",
      prompt: v.meaningZh || v.word,
      answer: v.word,
      options: shuffle([v.word, ...distractors]),
    });
  }
  const others = detail.lines.map((l) => l.zh);
  for (const l of detail.lines) {
    const dz = shuffle(others.filter((x) => x !== l.zh)).slice(0, 3);
    const de = shuffle(detail.lines.filter((x) => x.en !== l.en)).slice(0, 3).map((x) => x.en);
    if (dz.length >= 3) {
      pool.push({
        type: "listen",
        prompt: "🔊",
        speakText: l.en,
        answer: l.zh,
        options: shuffle([l.zh, ...dz]),
      });
    }
    if (de.length >= 3) {
      pool.push({
        type: "read",
        prompt: l.zh,
        answer: l.en,
        options: shuffle([l.en, ...de]),
      });
    }
  }
  return shuffle(pool).slice(0, 10);
}

function shuffle<T>(arr: T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function startFight() {
  index.value = 0;
  chosen.value = "";
  correct.value = 0;
  timeLeft.value = TIME_LIMIT;
  phase.value = "fighting";
  if (current.value?.type === "listen") {
    speak(current.value.speakText || "");
  }
  stopTimer();
  timer = setInterval(() => {
    timeLeft.value -= 1;
    if (timeLeft.value <= 0) {
      settle();
    }
  }, 1000);
}

function stopTimer() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

function playCurrentAudio() {
  if (current.value?.speakText) {
    speak(current.value.speakText);
  }
}

function optionClass(opt: string) {
  if (!chosen.value) return {};
  return {
    right: opt === current.value.answer,
    wrong: opt === chosen.value && opt !== current.value.answer,
  };
}

function choose(opt: string) {
  if (chosen.value) return;
  chosen.value = opt;
  if (opt === current.value.answer) {
    correct.value += 1;
  }
  setTimeout(() => {
    chosen.value = "";
    if (index.value < questions.value.length - 1) {
      index.value += 1;
      if (current.value?.type === "listen") {
        speak(current.value.speakText || "");
      }
    } else {
      settle();
    }
  }, 450);
}

async function settle() {
  stopTimer();
  const passed = correct.value >= PASS_LINE;
  if (passed) {
    try {
      await api.recordPractice("boss", 1, correct.value);
      await api.completeLesson("boss", scenarioId.value, correct.value);
    } catch (e) {
      // 上报失败不打结案
    }
    phase.value = "passed";
  } else {
    phase.value = "failed";
  }
}

function restart() {
  const detailQuestions = questions.value;
  questions.value = shuffle(detailQuestions);
  startFight();
}

function goBack() {
  uni.navigateBack();
}
</script>

<style scoped>
.boss-page {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
}

.timer-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 8rpx 40rpx 24rpx;
}

.timer-bar {
  flex: 1;
  height: 16rpx;
  background: #fee2e2;
  border-radius: 999rpx;
  overflow: hidden;
}

.timer-inner {
  height: 100%;
  background: #16a34a;
  transition: width 0.9s linear;
}

.timer-inner.danger {
  background: #ef4444;
}

.timer-text {
  font-size: 30rpx;
  font-weight: 800;
  color: #111827;
  width: 80rpx;
  text-align: right;
}

.timer-text.danger {
  color: #ef4444;
}

.card-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 50rpx 40rpx;
}

.round-hint {
  font-size: 22rpx;
  color: #9ca3af;
  margin-bottom: 12rpx;
}

.type-chip {
  font-size: 24rpx;
  color: #b45309;
  background: #fef3c7;
  border-radius: 999rpx;
  padding: 6rpx 24rpx;
  margin-bottom: 28rpx;
}

.prompt {
  font-size: 40rpx;
  font-weight: 700;
  color: #111827;
  margin-bottom: 12rpx;
  text-align: center;
}

.sub-hint {
  font-size: 22rpx;
  color: #9ca3af;
  margin-bottom: 32rpx;
}

.listen-btn {
  background: #16a34a;
  color: #ffffff;
  border-radius: 999rpx;
  padding: 18rpx 44rpx;
  font-size: 28rpx;
  margin-bottom: 16rpx;
}

.options {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.option {
  background: #f9fafb;
  border: 3rpx solid transparent;
  border-radius: 16rpx;
  padding: 24rpx 0;
  text-align: center;
  font-size: 30rpx;
  color: #111827;
}

.option.right {
  border-color: #16a34a;
  background: #f0fdf4;
  color: #15803d;
  font-weight: 700;
}

.option.wrong {
  border-color: #ef4444;
  background: #fef2f2;
  color: #b91c1c;
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
  font-size: 90rpx;
}

.done-title {
  font-size: 42rpx;
  font-weight: 800;
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
