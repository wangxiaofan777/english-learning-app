<template>
  <view class="hall-page">
    <view class="track-bar">
      <view
        v-for="t in tracks"
        :key="t.key"
        class="track-chip"
        :class="{ active: track === t.key }"
        @tap="switchTrack(t.key)"
      >
        {{ t.name }}
      </view>
    </view>

    <view v-if="scenarios.length === 0" class="card empty">
      <text class="muted">这个轨道的场景准备中，先看看其他轨道吧</text>
    </view>

    <view
      v-for="s in scenarios"
      :key="s.id"
      class="card scenario-card"
      @tap="startChat(s.id)"
    >
      <view class="scenario-top">
        <view class="scenario-titles">
          <text class="scenario-title">{{ s.titleZh }}</text>
          <text class="scenario-en">{{ s.titleEn }}</text>
        </view>
        <text class="chip" :class="{ 'chip--gray': s.practiced }">{{ s.cefr }}</text>
      </view>
      <text class="scenario-intro">{{ s.introZh }}</text>
      <view class="scenario-meta">
        <text class="chip chip--gray">{{ s.lineCount }} 句示范</text>
        <text class="chip chip--gray">{{ s.vocabCount }} 个生词</text>
        <text v-if="s.practiced" class="chip chip--amber">练过</text>
        <text class="start-hint">开始对话 →</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { api } from "../../utils/api";
import type { ScenarioCard } from "../../utils/types";
import { ensureAuth } from "../../stores/user";

const tracks = [
  { key: "", name: "全部" },
  { key: "daily", name: "日常交流" },
  { key: "work", name: "职场口语" },
  { key: "travel", name: "出国旅行" },
];

const track = ref("");
const scenarios = ref<ScenarioCard[]>([]);

onShow(async () => {
  if (!ensureAuth()) return;
  await loadScenarios();
});

async function switchTrack(key: string) {
  track.value = key;
  await loadScenarios();
}

async function loadScenarios() {
  scenarios.value = await api.scenarios(track.value || undefined);
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
.hall-page {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
}

.track-bar {
  display: flex;
  gap: 16rpx;
  padding: 8rpx 24rpx 28rpx;
}

.track-chip {
  background: #ffffff;
  color: #6b7280;
  font-size: 26rpx;
  border-radius: 999rpx;
  padding: 12rpx 32rpx;
  border: 2rpx solid #e5e7eb;
}

.track-chip.active {
  background: #16a34a;
  color: #ffffff;
  border-color: #16a34a;
  font-weight: 600;
}

.scenario-card:active {
  transform: scale(0.99);
}

.scenario-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.scenario-titles {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.scenario-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #111827;
}

.scenario-en {
  font-size: 24rpx;
  color: #9ca3af;
}

.scenario-intro {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #6b7280;
  line-height: 1.6;
}

.scenario-meta {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 20rpx;
  flex-wrap: wrap;
}

.start-hint {
  margin-left: auto;
  font-size: 24rpx;
  color: #16a34a;
  font-weight: 600;
}

.empty {
  padding: 80rpx 0;
  text-align: center;
}
</style>
