<template>
  <view class="review-page">
    <!-- 进行中 -->
    <template v-if="phase === 'review' && current">
      <view class="progress-row">
        <view class="progress-bar">
          <view class="progress-inner" :style="{ width: progressPercent }" />
        </view>
        <text class="muted">{{ doneCount }} / {{ total }}</text>
      </view>

      <view class="card card-body">
        <text class="state-hint">{{ reveal ? "还记得它吗？诚实评分最有效" : "先回忆，再点开看释义" }}</text>
        <view class="word-block" @tap="pronounce(current.word)">
          <text class="big-word">{{ current.word }}</text>
          <text v-if="current.phonetic" class="muted">{{ current.phonetic }}</text>
          <text class="speak-hint">🔊 点一下发音</text>
        </view>

        <view v-if="reveal" class="reveal-block">
          <text class="meaning">{{ current.meaningZh || "（暂无释义）" }}</text>
          <text v-if="current.exampleEn" class="example">{{ current.exampleEn }}</text>
          <text v-if="current.exampleZh" class="example muted">{{ current.exampleZh }}</text>
        </view>
        <button v-else class="btn-ghost reveal-btn" @tap="reveal = true">显示释义</button>
      </view>

      <view v-if="reveal" class="ratings">
        <view class="rate-btn rate-1" @tap="grade(1)">
          <text class="rate-emoji">😵</text>
          <text class="rate-name">忘了</text>
        </view>
        <view class="rate-btn rate-2" @tap="grade(2)">
          <text class="rate-emoji">😅</text>
          <text class="rate-name">困难</text>
        </view>
        <view class="rate-btn rate-3" @tap="grade(3)">
          <text class="rate-emoji">🙂</text>
          <text class="rate-name">良好</text>
        </view>
        <view class="rate-btn rate-4" @tap="grade(4)">
          <text class="rate-emoji">😎</text>
          <text class="rate-name">轻松</text>
        </view>
      </view>
    </template>

    <!-- 完成 -->
    <template v-else-if="phase === 'done'">
      <view class="card done-card">
        <text class="done-emoji">🎉</text>
        <text class="done-title">今日复习完成</text>
        <text class="muted">共复习 {{ total }} 个词，明天见！</text>
        <button class="btn-primary back-btn" @tap="goBack">返回</button>
      </view>
    </template>

    <!-- 无任务 -->
    <template v-else-if="phase === 'done' || phase === 'review'">
      <view class="card done-card">
        <text class="done-emoji">☕</text>
        <text class="done-title">没有待复习的词</text>
        <button class="btn-primary back-btn" @tap="goBack">返回</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { api } from "../../utils/api";
import type { VocabEntry } from "../../utils/types";
import { speak } from "../../utils/speech";

const phase = ref<"loading" | "review" | "done">("loading");
const cards = ref<VocabEntry[]>([]);
const index = ref(0);
const reveal = ref(false);
const total = ref(0);
const doneCount = ref(0);
const current = computed(() => cards.value[index.value]);
const progressPercent = computed(() =>
  total.value === 0 ? "0%" : `${Math.round((doneCount.value / total.value) * 100)}%`
);

onLoad(async () => {
  const queue = await api.reviewQueue(15);
  cards.value = queue.cards;
  total.value = queue.cards.length;
  phase.value = queue.cards.length === 0 ? "done" : "review";
});

function pronounce(word: string) {
  speak(word);
}

async function grade(rating: number) {
  if (!current.value) return;
  await api.grade(current.value.id, rating);
  doneCount.value += 1;
  reveal.value = false;
  index.value += 1;
  if (index.value >= cards.value.length) {
    phase.value = "done";
  }
}

function goBack() {
  uni.navigateBack();
}
</script>

<style scoped>
.review-page {
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
  padding: 70rpx 40rpx;
}

.state-hint {
  font-size: 22rpx;
  color: #9ca3af;
  margin-bottom: 40rpx;
}

.word-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.big-word {
  font-size: 64rpx;
  font-weight: 800;
  color: #111827;
}

.speak-hint {
  font-size: 22rpx;
  color: #9ca3af;
}

.reveal-block {
  margin-top: 44rpx;
  border-top: 2rpx dashed #e5e7eb;
  padding-top: 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
}

.meaning {
  font-size: 36rpx;
  color: #111827;
  font-weight: 600;
}

.example {
  font-size: 26rpx;
  color: #6b7280;
  font-style: italic;
}

.reveal-btn {
  margin-top: 44rpx;
}

.ratings {
  display: flex;
  gap: 16rpx;
  padding: 32rpx 24rpx 0;
}

.rate-btn {
  flex: 1;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  box-shadow: 0 2rpx 12rpx rgba(31, 41, 55, 0.05);
}

.rate-emoji {
  font-size: 40rpx;
}

.rate-name {
  font-size: 24rpx;
  color: #374151;
}

.rate-1 {
  border-bottom: 6rpx solid #ef4444;
}

.rate-2 {
  border-bottom: 6rpx solid #f59e0b;
}

.rate-3 {
  border-bottom: 6rpx solid #22c55e;
}

.rate-4 {
  border-bottom: 6rpx solid #0ea5e9;
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
  width: 60%;
  margin-top: 24rpx;
}
</style>
