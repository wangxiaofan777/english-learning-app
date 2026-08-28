<template>
  <view class="idx-page" v-if="today">
    <view class="hero">
      <view class="hero-left">
        <text class="hero-greeting">{{ greeting }}，{{ nickname }}</text>
        <text class="hero-sub">已连续学习 {{ today.streakDays }} 天 · 今天已学 {{ today.todayMinutes }} 分钟</text>
      </view>
      <view class="streak-badge">
        <text class="streak-num">{{ today.streakDays }}</text>
        <text class="streak-label">天</text>
      </view>
    </view>

    <view class="card plan-card">
      <view class="plan-header">
        <text class="title-lg">今天三件事</text>
        <text class="chip" v-if="today.cefrLevel">{{ today.cefrLevel }} 水平</text>
      </view>

      <view
        v-for="item in today.items"
        :key="item.kind"
        class="plan-item"
        @tap="onItemTap(item)"
      >
        <view class="plan-icon" :class="{ done: item.done }">
          <text>{{ iconFor(item) }}</text>
        </view>
        <view class="plan-body">
          <text class="plan-title" :class="{ strike: item.done }">{{ item.title }}</text>
          <text class="muted">
            <template v-if="item.kind === 'review' && today.dueCount === 0">今天没有待复习的词</template>
            <template v-else-if="item.kind === 'scenario'">{{ item.scenarioTitleZh || "去场景大厅看看" }}</template>
            <template v-else>完成 {{ item.doneCount }} / {{ item.target }}</template>
          </text>
        </view>
        <text class="plan-action">{{ item.done ? "✓" : "去完成" }}</text>
      </view>
    </view>

    <view class="card quick-card">
      <text class="muted">今日待复习</text>
      <text class="due-num">{{ today.dueCount }}</text>
      <text class="muted">个单词</text>
      <button class="btn-ghost" @tap="goReview">去复习</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { api } from "../../utils/api";
import type { TodayItem, TodayView } from "../../utils/types";
import { ensureAuth, user } from "../../stores/user";
import { greetingByHour } from "../../utils/format";

const today = ref<TodayView | null>(null);
const greeting = computed(() => greetingByHour());
const nickname = computed(() => user.profile?.nickname || "同学");

const iconFor = (item: TodayItem) => {
  if (item.kind === "review") return "📖";
  if (item.kind === "dialog") return "🎙️";
  if (item.lessonType === "listening") return "🎧";
  if (item.lessonType === "shadowing") return "🗣️";
  return "🎬";
};

onShow(async () => {
  if (!ensureAuth()) return;
  today.value = await api.today();
});

function onItemTap(item: TodayItem) {
  if (item.kind === "review") {
    goReview();
  } else if (item.kind === "scenario" && (item.scenarioId || item.lessonType === "review")) {
    // 按当前课时类型路由：对话实战 / 听力精听 / 跟读评分 / 单元复习
    if (item.lessonType === "listening") {
      uni.navigateTo({ url: `/pages/listen/listen?id=${item.scenarioId}` });
    } else if (item.lessonType === "shadowing") {
      uni.navigateTo({ url: `/pages/practice/shadow?id=${item.scenarioId}` });
    } else if (item.lessonType === "review") {
      uni.navigateTo({ url: "/pages/vocab/review" });
    } else {
      startChat(item.scenarioId as string);
    }
  } else if (item.kind === "dialog") {
    uni.switchTab({ url: "/pages/speak/hall" });
  }
}

function goReview() {
  if (!today.value || today.value.dueCount === 0) {
    uni.showToast({ title: "今天没有待复习的词，先去练个场景吧", icon: "none" });
    return;
  }
  uni.navigateTo({ url: "/pages/vocab/review" });
}

async function startChat(scenarioId: string) {
  uni.showLoading({ title: "准备场景…" });
  try {
    const conv = await api.createConversation(scenarioId);
    uni.hideLoading();
    uni.navigateTo({ url: `/pages/speak/chat?id=${conv.conversationId}` });
  } catch (e) {
    uni.hideLoading();
  }
}
</script>

<style scoped>
.idx-page {
  min-height: 100vh;
  padding-bottom: 60rpx;
}

.hero {
  background: #16a34a;
  color: #ffffff;
  padding: 48rpx 40rpx 64rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-radius: 0 0 40rpx 40rpx;
}

.hero-left {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.hero-greeting {
  font-size: 40rpx;
  font-weight: 700;
}

.hero-sub {
  font-size: 24rpx;
  opacity: 0.85;
}

.streak-badge {
  background: rgba(255, 255, 255, 0.16);
  border-radius: 20rpx;
  padding: 16rpx 24rpx;
  display: flex;
  align-items: baseline;
  gap: 6rpx;
}

.streak-num {
  font-size: 52rpx;
  font-weight: 800;
}

.streak-label {
  font-size: 22rpx;
}

.plan-card {
  margin-top: -36rpx;
}

.plan-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.plan-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 28rpx 0;
  border-bottom: 2rpx solid #f3f4f6;
}

.plan-item:last-child {
  border-bottom: none;
}

.plan-icon {
  width: 76rpx;
  height: 76rpx;
  border-radius: 20rpx;
  background: #f0fdf4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
}

.plan-icon.done {
  background: #f3f4f6;
  filter: grayscale(1);
  opacity: 0.6;
}

.plan-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.plan-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.plan-title.strike {
  color: #9ca3af;
  text-decoration: line-through;
}

.plan-action {
  font-size: 24rpx;
  color: #16a34a;
  font-weight: 600;
}

.quick-card {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.quick-card .btn-ghost {
  margin-left: auto;
  padding: 12rpx 32rpx;
}

.due-num {
  font-size: 44rpx;
  font-weight: 800;
  color: #16a34a;
}
</style>
