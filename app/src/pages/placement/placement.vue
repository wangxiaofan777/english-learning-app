<template>
  <view class="pm-page">
    <!-- 答题中 -->
    <template v-if="phase === 'quiz'">
      <view class="progress-wrap">
        <view class="progress-bar">
          <view class="progress-inner" :style="{ width: progressPercent }" />
        </view>
        <text class="muted">{{ current + 1 }} / {{ questions.length }}</text>
      </view>

      <view class="card" v-if="currentQuestion">
        <text class="chip chip--gray">{{ currentQuestion.cefr }} 档</text>
        <text class="stem">{{ currentQuestion.stem }}</text>
        <view class="options">
          <view
            v-for="opt in currentQuestion.options"
            :key="opt.key"
            class="option"
            :class="{ active: answers[currentQuestion.id] === opt.key }"
            @tap="choose(opt.key)"
          >
            <text class="option-key">{{ opt.key }}</text>
            <text class="option-text">{{ opt.text }}</text>
          </view>
        </view>
      </view>

      <view class="footer">
        <button
          class="btn-primary"
          :class="{ disabled: !answered || submitting }"
          @tap="nextOrSubmit"
        >
          {{ isLast ? (submitting ? "评估中…" : "提交测评") : "下一题" }}
        </button>
        <text class="muted skip-hint" @tap="skipAll">跳过测评，按自估水平开始</text>
      </view>
    </template>

    <!-- 结果页 -->
    <template v-else-if="phase === 'result' && result">
      <view class="result-card">
        <text class="muted">你的起始等级</text>
        <text class="cefr-big">{{ result.cefr }}</text>
        <text class="result-score">答对 {{ result.score }} / {{ result.total }} 题</text>
        <view class="tags">
          <text v-for="t in result.weakTags" :key="t" class="chip chip--amber">{{ t }}</text>
        </view>
        <text class="muted comment">{{ result.spokenComment }}</text>
      </view>
      <view class="footer">
        <button class="btn-primary" @tap="startLearning">开始今天的学习</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { api } from "../../utils/api";
import type { PlacementQuestion, PlacementResult } from "../../utils/types";
import { updateProfile } from "../../stores/user";

const phase = ref<"quiz" | "result">("quiz");
const questions = ref<PlacementQuestion[]>([]);
const answers = ref<Record<string, string>>({});
const current = ref(0);
const submitting = ref(false);
const result = ref<PlacementResult | null>(null);

const currentQuestion = computed(() => questions.value[current.value]);
const isLast = computed(() => current.value === questions.value.length - 1);
const answered = computed(
  () => currentQuestion.value && !!answers.value[currentQuestion.value.id]
);
const progressPercent = computed(
  () => `${Math.round(((current.value + 1) / questions.value.length) * 100)}%`
);

async function load() {
  questions.value = await api.placementQuestions();
}
load();

function choose(key: string) {
  if (!currentQuestion.value) return;
  answers.value = { ...answers.value, [currentQuestion.value.id]: key };
}

function nextOrSubmit() {
  if (!answered.value || submitting.value) return;
  if (!isLast.value) {
    current.value += 1;
    return;
  }
  doSubmit();
}

async function doSubmit() {
  submitting.value = true;
  try {
    result.value = await api.placementSubmit(answers.value);
    const profile = await api.me();
    updateProfile(profile);
    phase.value = "result";
  } finally {
    submitting.value = false;
  }
}

function skipAll() {
  uni.showModal({
    title: "跳过测评？",
    content: "将按 A2 水平开始，系统会随你的练习自动校准难度。",
    success: async (res) => {
      if (res.confirm) {
        const fakeAnswers: Record<string, string> = {};
        questions.value.forEach((q, i) => {
          fakeAnswers[q.id] = i < 4 ? "A" : "B";
        });
        result.value = await api.placementSubmit(fakeAnswers);
        const profile = await api.me();
        updateProfile(profile);
        phase.value = "result";
      }
    },
  });
}

function startLearning() {
  uni.switchTab({ url: "/pages/index/index" });
}
</script>

<style scoped>
.pm-page {
  min-height: 100vh;
  padding: 32rpx 0 220rpx;
}

.progress-wrap {
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
  border-radius: 999rpx;
  transition: width 0.2s;
}

.stem {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: #111827;
  margin: 20rpx 0 32rpx;
  line-height: 1.5;
}

.options {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.option {
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: #f9fafb;
  border: 3rpx solid transparent;
  border-radius: 16rpx;
  padding: 24rpx;
}

.option.active {
  border-color: #16a34a;
  background: #f0fdf4;
}

.option-key {
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  background: #e5e7eb;
  color: #374151;
  font-size: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.option.active .option-key {
  background: #16a34a;
  color: #ffffff;
}

.option-text {
  font-size: 30rpx;
  color: #111827;
}

.footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 24rpx 24rpx calc(48rpx + env(safe-area-inset-bottom));
  background: #f6f7f9;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.skip-hint {
  text-align: center;
  padding: 8rpx 0;
}

.result-card {
  margin: 120rpx 40rpx 0;
  background: #ffffff;
  border-radius: 24rpx;
  padding: 64rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
  box-shadow: 0 8rpx 32rpx rgba(31, 41, 55, 0.08);
}

.cefr-big {
  font-size: 96rpx;
  font-weight: 800;
  color: #16a34a;
}

.result-score {
  font-size: 28rpx;
  color: #374151;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
}

.comment {
  text-align: center;
  line-height: 1.6;
}
</style>
