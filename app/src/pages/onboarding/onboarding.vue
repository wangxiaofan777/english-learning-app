<template>
  <view class="ob-page">
    <view class="ob-header">
      <text class="title-lg">定制你的学习计划</text>
      <text class="muted">两步完成，随时可以在「我的」里调整</text>
    </view>

    <view class="card">
      <text class="step-label">1 · 你最想提升什么？</text>
      <view class="goal-grid">
        <view
          v-for="g in goals"
          :key="g.track"
          class="goal-item"
          :class="{ active: goalTrack === g.track }"
          @tap="goalTrack = g.track"
        >
          <text class="goal-emoji">{{ g.emoji }}</text>
          <text class="goal-name">{{ g.name }}</text>
          <text class="goal-desc">{{ g.desc }}</text>
        </view>
      </view>
    </view>

    <view class="card">
      <text class="step-label">2 · 每天能投入多久？</text>
      <view class="minutes-row">
        <view
          v-for="m in minutesOptions"
          :key="m"
          class="minutes-item"
          :class="{ active: dailyMinutes === m }"
          @tap="dailyMinutes = m"
        >
          {{ m }} 分钟
        </view>
      </view>
    </view>

    <view class="footer">
      <button class="btn-primary" :class="{ disabled: !goalTrack || submitting }" @tap="submit">
        {{ submitting ? "生成中…" : "下一步 · 快速测评定级" }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { api } from "../../utils/api";
import { updateProfile } from "../../stores/user";

const goals = [
  { track: "daily", emoji: "☕", name: "日常交流", desc: "点单、问路、闲聊" },
  { track: "work", emoji: "💼", name: "职场口语", desc: "会议、跟进、面试" },
  { track: "travel", emoji: "✈️", name: "出国旅行", desc: "值机、酒店、求助" },
  { track: "exam", emoji: "📝", name: "考试备考", desc: "四六级、雅思词汇" },
];
const minutesOptions = [5, 15, 30];

const goalTrack = ref("");
const dailyMinutes = ref(15);
const submitting = ref(false);

async function submit() {
  if (!goalTrack.value || submitting.value) return;
  submitting.value = true;
  try {
    const result = await api.onboarding(goalTrack.value, dailyMinutes.value);
    updateProfile(result.profile);
    if (result.nextStep === "placement") {
      uni.redirectTo({ url: "/pages/placement/placement" });
    } else {
      uni.switchTab({ url: "/pages/index/index" });
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.ob-page {
  min-height: 100vh;
  padding: 120rpx 0 60rpx;
}

.ob-header {
  padding: 0 40rpx 32rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.step-label {
  font-size: 30rpx;
  font-weight: 600;
  color: #374151;
  margin-bottom: 24rpx;
  display: block;
}

.goal-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
}

.goal-item {
  width: calc(50% - 10rpx);
  background: #f9fafb;
  border: 3rpx solid transparent;
  border-radius: 20rpx;
  padding: 28rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.goal-item.active {
  border-color: #16a34a;
  background: #f0fdf4;
}

.goal-emoji {
  font-size: 40rpx;
}

.goal-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.goal-desc {
  font-size: 22rpx;
  color: #9ca3af;
}

.minutes-row {
  display: flex;
  gap: 20rpx;
}

.minutes-item {
  flex: 1;
  text-align: center;
  background: #f9fafb;
  border: 3rpx solid transparent;
  border-radius: 16rpx;
  padding: 22rpx 0;
  font-size: 28rpx;
  color: #374151;
}

.minutes-item.active {
  border-color: #16a34a;
  background: #f0fdf4;
  color: #15803d;
  font-weight: 600;
}

.footer {
  padding: 0 24rpx;
  position: fixed;
  bottom: 48rpx;
  left: 0;
  right: 0;
}
</style>
