<template>
  <view class="listen-page">
    <!-- 逐句精听中 -->
    <template v-if="phase === 'listening' && line">
      <view class="top-row">
        <text class="muted">{{ idx + 1 }} / {{ lines.length }} 句</text>
        <view class="blind-toggle" @tap="blind = !blind">
          <text class="chip" :class="blind ? '' : 'chip--gray'">
            {{ blind ? "🎧 盲听模式：开" : "盲听模式：关" }}
          </text>
        </view>
      </view>

      <view class="progress-bar">
        <view class="progress-inner" :style="{ width: progressPercent }" />
      </view>

      <view class="card listen-card">
        <text class="mode-hint">{{ blind ? "先盲听，猜猜这句话说了什么" : "听一句，看一句" }}</text>

        <view
          class="player"
          :class="{ playing }"
          @tap="playCurrent"
        >
          <text class="player-icon">{{ playing ? "⏸" : "▶" }}</text>
          <text class="player-label">{{ playing ? "正在播放…" : "播放本句" }}</text>
        </view>

        <view class="text-zone">
          <template v-if="revealed || !blind">
            <text class="line-en" :user-select="true">{{ line.en }}</text>
            <text class="line-zh">{{ line.zh }}</text>
          </template>
          <template v-else>
            <view class="hidden-text" @tap="reveal">
              <text class="hidden-icon">🙈</text>
              <text class="hidden-label">点这里显示字幕</text>
            </view>
          </template>
        </view>
      </view>

      <view class="nav-row">
        <button class="btn-ghost" :class="{ disabled: idx === 0 }" @tap="prev">上一句</button>
        <button class="btn-primary nav-next" @tap="next">{{ isLast ? "完成精听" : "下一句" }}</button>
      </view>

      <view class="card tip-card">
        <text class="muted">精听方法：盲听抓大意 → 逐句听清 → 看字幕确认生词 → 再盲听整句复述。练完可以点「跟读」开口模仿。</text>
      </view>
    </template>

    <!-- 理解小测 -->
    <template v-else-if="phase === 'quiz' && quizQuestion">
      <view class="top-row">
        <text class="muted">理解小测 {{ quizIdx + 1 }} / {{ quizQuestions.length }}</text>
        <text class="chip">🎧 刚听过的内容</text>
      </view>
      <view class="card quiz-card">
        <text class="quiz-hint">这句英文的意思是？</text>
        <text class="quiz-en">{{ quizQuestion.en }}</text>
        <view class="quiz-options">
          <view
            v-for="opt in quizQuestion.options"
            :key="opt"
            class="quiz-option"
            :class="quizClass(opt)"
            @tap="chooseQuiz(opt)"
          >
            <text>{{ opt }}</text>
          </view>
        </view>
      </view>
      <view class="nav-row">
        <button class="btn-primary nav-next" :class="{ disabled: !quizChosen }" @tap="quizNext">
          {{ quizIdx === quizQuestions.length - 1 ? "完成" : "下一题" }}
        </button>
      </view>
    </template>

    <!-- 完成 -->
    <template v-else-if="phase === 'done'">
      <view class="card done-card">
        <text class="done-emoji">👂</text>
        <text class="done-title">精听完成</text>
        <text class="muted">{{ lines.length }} 句都听完了，耳朵热了吗？</text>
        <button class="btn-primary back-btn" @tap="goShadow">挑战跟读这一段</button>
        <button class="btn-ghost back-btn" @tap="goBack">返回大厅</button>
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
import type { ScenarioDetail } from "../../utils/types";

const scenarioId = ref("");
const detail = ref<ScenarioDetail | null>(null);
const idx = ref(0);
const blind = ref(true);
const revealed = ref(false);
const playing = ref(false);
const phase = ref<"loading" | "listening" | "quiz" | "done">("loading");
let stopRequested = false;

interface QuizQ {
  en: string;
  answer: string;
  options: string[];
}
const quizQuestions = ref<QuizQ[]>([]);
const quizIdx = ref(0);
const quizChosen = ref("");
const quizCorrect = ref(0);
const quizQuestion = computed(() => quizQuestions.value[quizIdx.value]);

const lines = computed(() => detail.value?.lines || []);
const line = computed(() => lines.value[idx.value]);
const isLast = computed(() => idx.value === lines.value.length - 1);
const progressPercent = computed(() =>
  lines.value.length === 0 ? "0%" : `${Math.round(((idx.value + 1) / lines.value.length) * 100)}%`
);

onLoad(async (query) => {
  scenarioId.value = (query?.id as string) || "";
  detail.value = await api.scenarioDetail(scenarioId.value);
  phase.value = "listening";
  // 进入即播第一句，直接进入"听"的状态
  playCurrent();
});

onUnload(() => {
  stopRequested = true;
  // #ifdef H5
  try {
    speechSynthesis.cancel();
  } catch (e) {
    // 页面卸载时尽力停止朗读
  }
  // #endif
});

async function playCurrent() {
  if (!line.value || playing.value) return;
  playing.value = true;
  await speakAsync(line.value.en);
  if (!stopRequested) {
    playing.value = false;
  }
}

function reveal() {
  revealed.value = true;
}

function prev() {
  if (idx.value === 0) return;
  idx.value -= 1;
  revealed.value = false;
  playCurrent();
}

async function next() {
  if (!isLast.value) {
    idx.value += 1;
    revealed.value = false;
    playCurrent();
    return;
  }
  buildQuiz();
  phase.value = "quiz";
}

function shuffle<T>(arr: T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/** 理解小测：听过的句子选中文意思 */
function buildQuiz() {
  const ls = lines.value;
  const picked = shuffle(ls).slice(0, Math.min(3, ls.length));
  quizQuestions.value = picked.map((l) => {
    const others = shuffle(ls.filter((x) => x !== l).map((x) => x.zh)).slice(0, 3);
    return { en: l.en, answer: l.zh, options: shuffle([l.zh, ...others]) };
  });
  quizIdx.value = 0;
  quizChosen.value = "";
  quizCorrect.value = 0;
}

function quizClass(opt: string) {
  if (!quizChosen.value) return {};
  return {
    right: opt === quizQuestion.value.answer,
    wrong: opt === quizChosen.value && opt !== quizQuestion.value.answer,
  };
}

function chooseQuiz(opt: string) {
  if (quizChosen.value) return;
  quizChosen.value = opt;
  if (opt === quizQuestion.value.answer) {
    quizCorrect.value += 1;
  }
}

async function quizNext() {
  if (!quizChosen.value) return;
  if (quizIdx.value < quizQuestions.value.length - 1) {
    quizIdx.value += 1;
    quizChosen.value = "";
    return;
  }
  // 完成：计入学习时长 + 课时 + 按小测表现结算
  const minutes = Math.max(1, Math.round(lines.value.length / 4));
  try {
    await api.recordPractice("listening", minutes, lines.value.length + quizCorrect.value);
    await api.completeLesson("listening", scenarioId.value);
  } catch (e) {
    // 计时失败不打断完成页
  }
  phase.value = "done";
}

function goShadow() {
  uni.redirectTo({ url: `/pages/practice/shadow?id=${scenarioId.value}` });
}

function goBack() {
  uni.switchTab({ url: "/pages/speak/hall" });
}
</script>

<style scoped>
.listen-page {
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

.listen-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56rpx 40rpx;
}

.mode-hint {
  font-size: 24rpx;
  color: #9ca3af;
  margin-bottom: 40rpx;
}

.player {
  width: 220rpx;
  height: 220rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #22c55e, #15803d);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  box-shadow: 0 12rpx 40rpx rgba(22, 163, 74, 0.35);
}

.player.playing {
  animation: pulse 1.2s infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.06); }
}

.player-icon {
  font-size: 64rpx;
}

.player-label {
  font-size: 22rpx;
  margin-top: 8rpx;
  opacity: 0.9;
}

.text-zone {
  margin-top: 48rpx;
  min-height: 180rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  width: 100%;
}

.line-en {
  font-size: 36rpx;
  font-weight: 600;
  color: #111827;
  text-align: center;
  line-height: 1.5;
}

.line-zh {
  font-size: 26rpx;
  color: #9ca3af;
  text-align: center;
}

.hidden-text {
  background: #f9fafb;
  border: 2rpx dashed #d1d5db;
  border-radius: 20rpx;
  padding: 40rpx 60rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.hidden-icon {
  font-size: 52rpx;
}

.hidden-label {
  font-size: 24rpx;
  color: #6b7280;
}

.nav-row {
  display: flex;
  gap: 20rpx;
  padding: 8rpx 24rpx 0;
}

.nav-row .btn-ghost,
.nav-row .btn-primary {
  flex: 1;
}

.btn-ghost.disabled {
  opacity: 0.4;
}

.nav-next {
  flex: 2;
}

.tip-card {
  margin-top: 32rpx;
}

.quiz-card {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  padding: 44rpx 36rpx;
}

.quiz-hint {
  font-size: 24rpx;
  color: #9ca3af;
}

.quiz-en {
  font-size: 34rpx;
  font-weight: 600;
  color: #111827;
  line-height: 1.5;
}

.quiz-options {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  margin-top: 8rpx;
}

.quiz-option {
  background: #f9fafb;
  border: 3rpx solid transparent;
  border-radius: 16rpx;
  padding: 24rpx;
  font-size: 28rpx;
  color: #111827;
}

.quiz-option.right {
  border-color: #16a34a;
  background: #f0fdf4;
  color: #15803d;
  font-weight: 600;
}

.quiz-option.wrong {
  border-color: #ef4444;
  background: #fef2f2;
  color: #b91c1c;
}

.tip-card .muted {
  line-height: 1.7;
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
