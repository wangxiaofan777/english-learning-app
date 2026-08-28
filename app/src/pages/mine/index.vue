<template>
  <view class="mine-page" v-if="profile">
    <view class="card profile-card">
      <view class="avatar">{{ avatarText }}</view>
      <view class="profile-body">
        <text class="nickname">{{ profile.nickname }}</text>
        <view class="chips">
          <text class="chip" v-if="profile.cefrLevel">{{ profile.cefrLevel }} 水平</text>
          <text class="chip chip--gray">{{ trackLabel(profile.goalTrack) }}</text>
          <text class="chip chip--amber">连续 {{ profile.streakDays }} 天</text>
        </view>
      </view>
    </view>

    <view class="card stats-card">
      <text class="stats-title">学习统计</text>
      <view class="stats-grid">
        <view class="stat-item">
          <text class="stat-num">{{ minutesToText(stats?.totalMinutes || 0) }}</text>
          <text class="stat-label">累计学习</text>
        </view>
        <view class="stat-item">
          <text class="stat-num">{{ stats?.totalDialogs || 0 }}</text>
          <text class="stat-label">对话轮次</text>
        </view>
        <view class="stat-item">
          <text class="stat-num">{{ stats?.wordsLearning || 0 }} / {{ stats?.wordsTotal || 0 }}</text>
          <text class="stat-label">在学 / 生词</text>
        </view>
      </view>

      <text class="stats-title chart-title">最近 7 天</text>
      <view class="chart">
        <view v-for="d in stats?.week || []" :key="d.date" class="chart-col">
          <view class="chart-bar-wrap">
            <view class="chart-bar" :style="{ height: barHeight(d.minutes) }" />
          </view>
          <text class="chart-label">{{ d.date.slice(8) }}日</text>
        </view>
      </view>
    </view>

    <view class="card about-card">
      <view class="about-row">
        <text>学习目标与时长</text>
        <text class="muted">进入「定制计划」调整</text>
      </view>
      <view class="about-row">
        <text>AI 反馈说明</text>
        <text class="muted">对话内容由 AI 生成，供学习参考</text>
      </view>
      <view class="about-row">
        <text>当前版本</text>
        <text class="muted">v0.1.0 · MVP</text>
      </view>
    </view>

    <button class="logout" @tap="logout">退出登录</button>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { api } from "../../utils/api";
import type { Profile, StatsView } from "../../utils/types";
import { clearAuth, ensureAuth } from "../../stores/user";
import { minutesToText, trackLabel } from "../../utils/format";

const profile = ref<Profile | null>(null);
const stats = ref<StatsView | null>(null);

const avatarText = computed(() => (profile.value?.nickname || "L").slice(0, 1));
const maxMinutes = computed(() =>
  Math.max(10, ...(stats.value?.week || []).map((d) => d.minutes))
);

onShow(async () => {
  if (!ensureAuth()) return;
  profile.value = await api.me();
  stats.value = await api.stats();
});

function barHeight(minutes: number): string {
  const ratio = Math.min(1, minutes / maxMinutes.value);
  return `${Math.max(6, Math.round(ratio * 120))}rpx`;
}

function logout() {
  uni.showModal({
    title: "退出登录？",
    content: "学习记录会保留在服务端，下次登录可继续。",
    success: (res) => {
      if (res.confirm) {
        clearAuth();
        uni.reLaunch({ url: "/pages/login/login" });
      }
    },
  });
}
</script>

<style scoped>
.mine-page {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 28rpx;
}

.avatar {
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
  background: #16a34a;
  color: #ffffff;
  font-size: 48rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-body {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.nickname {
  font-size: 36rpx;
  font-weight: 700;
  color: #111827;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

.stats-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #111827;
  display: block;
  margin-bottom: 24rpx;
}

.chart-title {
  margin-top: 36rpx;
}

.stats-grid {
  display: flex;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.stat-num {
  font-size: 32rpx;
  font-weight: 700;
  color: #16a34a;
}

.stat-label {
  font-size: 22rpx;
  color: #9ca3af;
}

.chart {
  display: flex;
  align-items: flex-end;
  gap: 12rpx;
}

.chart-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.chart-bar-wrap {
  height: 130rpx;
  display: flex;
  align-items: flex-end;
}

.chart-bar {
  width: 100%;
  background: #86efac;
  border-radius: 8rpx 8rpx 0 0;
}

.chart-label {
  font-size: 20rpx;
  color: #9ca3af;
}

.about-row {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  font-size: 28rpx;
  color: #374151;
  border-bottom: 2rpx solid #f3f4f6;
}

.about-row:last-child {
  border-bottom: none;
}

.logout {
  margin: 40rpx 24rpx 0;
  background: #ffffff;
  color: #ef4444;
  border-radius: 20rpx;
  font-size: 30rpx;
  padding: 24rpx 0;
}
</style>
