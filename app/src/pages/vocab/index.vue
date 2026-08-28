<template>
  <view class="vocab-page">
    <view class="card due-card" @tap="goReview">
      <view class="due-left">
        <text class="due-title">今日待复习</text>
        <text class="due-sub">按记忆曲线安排，忘了的词会更早出现</text>
      </view>
      <view class="due-right">
        <text class="due-num">{{ dueCount }}</text>
        <text class="muted">个</text>
      </view>
    </view>

    <view class="card quiz-entry" @tap="goQuiz">
      <view class="due-left">
        <text class="due-title">⚡ 词汇速测</text>
        <text class="due-sub">10 道中译英快问快答，答对涨经验</text>
      </view>
      <text class="quiz-go">GO →</text>
    </view>

    <view v-if="entries.length === 0" class="card empty">
      <text class="empty-emoji">🌱</text>
      <text class="empty-title">词库还是空的</text>
      <text class="muted">在对话里点「+ 收藏」，生词会自动进入复习计划</text>
    </view>

    <view v-for="e in entries" :key="e.id" class="card word-card">
      <view class="word-head" @tap="pronounce(e.word)">
        <text class="word">{{ e.word }}</text>
        <text v-if="e.phonetic" class="muted phonetic">{{ e.phonetic }}</text>
        <text class="speak-hint">🔊</text>
      </view>
      <text v-if="e.meaningZh" class="meaning">{{ e.meaningZh }}</text>
      <text v-if="e.exampleEn" class="example" :user-select="true">{{ e.exampleEn }}</text>
      <view class="word-meta">
        <text class="chip chip--gray">{{ sourceLabel(e.source) }}</text>
        <text class="chip" :class="e.fsrsState === 'relearning' ? 'chip--amber' : ''">
          {{ vocabStateLabel(e.fsrsState) }}
        </text>
        <text class="muted next">下次复习 {{ shortDate(e.dueAt) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { api } from "../../utils/api";
import type { VocabEntry } from "../../utils/types";
import { ensureAuth } from "../../stores/user";
import { sourceLabel, vocabStateLabel } from "../../utils/format";
import { speak } from "../../utils/speech";

const entries = ref<VocabEntry[]>([]);
const dueCount = ref(0);

onShow(async () => {
  if (!ensureAuth()) return;
  entries.value = await api.vocabList();
  const queue = await api.reviewQueue(1);
  dueCount.value = queue.dueCount;
});

function goReview() {
  if (dueCount.value === 0) {
    uni.showToast({ title: "现在没有到期的词，先去收藏新词吧", icon: "none" });
    return;
  }
  uni.navigateTo({ url: "/pages/vocab/review" });
}

function goQuiz() {
  if (entries.value.length < 4) {
    uni.showToast({ title: "词库至少收藏 4 个词才能开测", icon: "none" });
    return;
  }
  uni.navigateTo({ url: "/pages/vocab/quiz" });
}

function pronounce(word: string) {
  speak(word);
}

function shortDate(iso: string): string {
  return (iso || "").slice(5, 10).replace("T", " ");
}
</script>

<style scoped>
.vocab-page {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
}

.due-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(120deg, #16a34a, #15803d);
  color: #ffffff;
}

.due-title {
  font-size: 34rpx;
  font-weight: 700;
  display: block;
}

.due-sub {
  font-size: 22rpx;
  opacity: 0.85;
  display: block;
  margin-top: 8rpx;
}

.due-right {
  display: flex;
  align-items: baseline;
  gap: 6rpx;
}

.due-num {
  font-size: 72rpx;
  font-weight: 800;
}

.due-right .muted {
  color: rgba(255, 255, 255, 0.8);
}

.quiz-entry {
  border: 2rpx dashed #16a34a;
}

.quiz-go {
  font-size: 30rpx;
  font-weight: 800;
  color: #16a34a;
}

.word-card .word-head {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}

.word {
  font-size: 36rpx;
  font-weight: 700;
  color: #111827;
}

.phonetic {
  font-size: 24rpx;
}

.speak-hint {
  margin-left: auto;
  font-size: 26rpx;
}

.meaning {
  display: block;
  margin-top: 10rpx;
  font-size: 28rpx;
  color: #374151;
}

.example {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #9ca3af;
  font-style: italic;
}

.word-meta {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 18rpx;
}

.next {
  margin-left: auto;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 90rpx 40rpx;
  text-align: center;
}

.empty-emoji {
  font-size: 64rpx;
}

.empty-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #374151;
}
</style>
