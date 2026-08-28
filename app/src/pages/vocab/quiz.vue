<template>
  <view class="quiz-page">
    <!-- 答题中 -->
    <template v-if="phase === 'playing' && current">
      <view class="progress-row">
        <view class="progress-bar">
          <view class="progress-inner" :style="{ width: progressPercent }" />
        </view>
        <text class="muted">{{ index + 1 }} / {{ questions.length }}</text>
      </view>

      <view class="card card-body">
        <text class="hint">{{ answered ? (correct ? "✓ 答对了！" : "✗ 正确答案是绿色那个") : "选出正确的英文单词" }}</text>
        <text class="prompt">{{ current.meaningZh || current.word }}</text>
        <view class="options">
          <view
            v-for="opt in current.options"
            :key="opt"
            class="option"
            :class="optionClass(opt)"
            @tap="choose(opt)"
          >
            <text>{{ opt }}</text>
          </view>
        </view>
      </view>

      <view class="nav-row">
        <button class="btn-primary" :class="{ disabled: !answered }" @tap="next">
          {{ isLast ? "看成绩" : "下一题" }}
        </button>
      </view>
    </template>

    <!-- 成绩 -->
    <template v-else-if="phase === 'done'">
      <view class="card done-card">
        <text class="done-emoji">{{ correct >= 8 ? "🏆" : correct >= 5 ? "💪" : "📖" }}</text>
        <text class="done-title">{{ correct }} / {{ questions.length }} 正确</text>
        <text class="muted">获得 {{ correct * 3 }} 经验 · 答错的词已经等你复习了</text>
        <button class="btn-primary back-btn" @tap="again">再来一场</button>
        <button class="btn-ghost back-btn" @tap="goBack">返回词库</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { api } from "../../utils/api";
import type { VocabEntry } from "../../utils/types";

interface QuizQuestion {
  word: string;
  meaningZh: string | null;
  options: string[];
  answer: string;
}

const phase = ref<"loading" | "playing" | "done">("playing");
const questions = ref<QuizQuestion[]>([]);
const index = ref(0);
const chosen = ref("");
const correct = ref(0);

const current = computed(() => questions.value[index.value]);
const answered = computed(() => !!chosen.value);
const isLast = computed(() => index.value === questions.value.length - 1);
const progressPercent = computed(() =>
  questions.value.length === 0 ? "0%" : `${Math.round((index.value / questions.value.length) * 100)}%`
);

onLoad(async () => {
  await buildQuiz();
});

async function buildQuiz() {
  const pool = await api.vocabList(1, 50);
  questions.value = buildQuestions(pool);
  index.value = 0;
  chosen.value = "";
  correct.value = 0;
  phase.value = "playing";
}

function buildQuestions(pool: VocabEntry[]): QuizQuestion[] {
  const usable = pool.filter((e) => e.word);
  const shuffled = shuffle(usable).slice(0, 10);
  return shuffled.map((entry) => {
    const distractors = shuffle(usable.filter((e) => e.id !== entry.id))
      .slice(0, 3)
      .map((e) => e.word);
    const options = shuffle([entry.word, ...distractors]);
    return { word: entry.word, meaningZh: entry.meaningZh, options, answer: entry.word };
  });
}

function shuffle<T>(arr: T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function optionClass(opt: string) {
  if (!answered.value) return {};
  return {
    right: opt === current.value.answer,
    wrong: opt === chosen.value && opt !== current.value.answer,
  };
}

function choose(opt: string) {
  if (answered.value) return;
  chosen.value = opt;
  if (opt === current.value.answer) {
    correct.value += 1;
  }
}

async function next() {
  if (!answered.value) return;
  if (!isLast.value) {
    index.value += 1;
    chosen.value = "";
    return;
  }
  // 结算：经验按答对数发放（服务端 3 XP/题）
  try {
    await api.recordPractice("quiz", 1, correct.value);
  } catch (e) {
    // 上报失败不打断成绩页
  }
  phase.value = "done";
}

async function again() {
  await buildQuiz();
}

function goBack() {
  uni.navigateBack();
}
</script>

<style scoped>
.quiz-page {
  min-height: 100vh;
  padding: 32rpx 0 60rpx;
}

.progress-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 0 40rpx 24rpx;
}

.progress-bar {
  flex: 1;
  height: 12rpx;
  background: #e5e7eb;
  border-radius: 999rpx;
  overflow: hidden;
}

.progress-inner {
  height: 100%;
  background: #16a34a;
  transition: width 0.2s;
}

.card-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 40rpx;
}

.hint {
  font-size: 22rpx;
  color: #9ca3af;
  margin-bottom: 32rpx;
}

.prompt {
  font-size: 44rpx;
  font-weight: 700;
  color: #111827;
  margin-bottom: 44rpx;
  text-align: center;
}

.options {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.option {
  background: #f9fafb;
  border: 3rpx solid transparent;
  border-radius: 16rpx;
  padding: 26rpx 0;
  text-align: center;
  font-size: 32rpx;
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

.nav-row {
  padding: 24rpx 24rpx 0;
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
  font-size: 40rpx;
  font-weight: 800;
  color: #111827;
}

.back-btn {
  width: 70%;
  margin-top: 8rpx;
}
</style>
