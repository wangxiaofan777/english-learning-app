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
      <view class="level-box">
        <text class="level-num">LV.{{ profile.level }}</text>
        <text class="level-title">{{ profile.levelTitle }}</text>
      </view>
    </view>

    <view class="card">
      <view class="stats-title-row">
        <text class="stats-title">学习统计</text>
        <text class="chip">本周 +{{ stats?.weekXp || 0 }} 经验</text>
      </view>
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

      <view class="tool-row">
        <button class="btn-ghost tool-btn" @tap="goCalendar">📅 打卡月历</button>
        <button class="btn-ghost tool-btn" @tap="goPoster">🖼️ 成绩海报</button>
      </view>
    </view>

    <view class="card">
      <text class="stats-title">成就墙 · {{ earnedCount }}/{{ badges.length }}</text>
      <view class="badge-grid">
        <view
          v-for="b in badges"
          :key="b.code"
          class="badge-item"
          :class="{ locked: !b.earned }"
        >
          <text class="badge-icon">{{ b.icon }}</text>
          <text class="badge-name">{{ b.name }}</text>
          <text class="badge-desc">{{ b.description }}</text>
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
import type { Badge, Profile, StatsView } from "../../utils/types";
import { clearAuth, ensureAuth } from "../../stores/user";
import { minutesToText, trackLabel } from "../../utils/format";

const profile = ref<Profile | null>(null);
const stats = ref<StatsView | null>(null);
const badges = ref<Badge[]>([]);

const avatarText = computed(() => (profile.value?.nickname || "L").slice(0, 1));
const earnedCount = computed(() => badges.value.filter((b) => b.earned).length);
const maxMinutes = computed(() =>
  Math.max(10, ...(stats.value?.week || []).map((d) => d.minutes))
);

onShow(async () => {
  if (!ensureAuth()) return;
  const [me, s, b] = await Promise.all([api.me(), api.stats(), api.achievements()]);
  profile.value = me;
  stats.value = s;
  badges.value = b;
});

function barHeight(minutes: number): string {
  const ratio = Math.min(1, minutes / maxMinutes.value);
  return `${Math.max(6, Math.round(ratio * 120))}rpx`;
}

function goCalendar() {
  uni.navigateTo({ url: "/pages/mine/calendar" });
}

function goPoster() {
  uni.navigateTo({ url: "/pages/mine/poster" });
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

.level-box {
  margin-left: auto;
  background: #f0fdf4;
  border-radius: 16rpx;
  padding: 14rpx 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.level-num {
  font-size: 30rpx;
  font-weight: 800;
  color: #16a34a;
}

.level-title {
  font-size: 20rpx;
  color: #15803d;
}

.stats-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.badge-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 18rpx;
}

.badge-item {
  width: calc(33.33% - 12rpx);
  background: #f9fafb;
  border-radius: 16rpx;
  padding: 22rpx 12rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
  text-align: center;
}

.badge-item.locked {
  opacity: 0.35;
  filter: grayscale(1);
}

.badge-icon {
  font-size: 44rpx;
}

.badge-name {
  font-size: 22rpx;
  font-weight: 600;
  color: #111827;
}

.badge-desc {
  font-size: 18rpx;
  color: #9ca3af;
  line-height: 1.4;
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

.tool-row {
  display: flex;
  gap: 16rpx;
  margin-top: 28rpx;
}

.tool-btn {
  flex: 1;
  padding: 16rpx 0;
  font-size: 26rpx;
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
