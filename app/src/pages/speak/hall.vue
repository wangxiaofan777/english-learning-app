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

    <view class="card companion-card" @tap="goCompanion">
      <view class="cb-body">
        <text class="cb-title">🤖 AI 陪练 · 像真人一样聊</text>
        <text class="cb-sub">有个性的陪练搭子，记得你说过的话，说错悄悄纠正</text>
      </view>
      <text class="cb-go">找搭子 →</text>
    </view>

    <view class="card free-talk-card" @tap="startFreeTalk">
      <view class="ft-body">
        <text class="ft-title">🎙️ 自由聊天</text>
        <text class="ft-sub">不限场景随便聊，AI 聊伴接住你说的每句话</text>
      </view>
      <text class="ft-go">开聊 →</text>
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
      </view>
      <view class="scenario-actions">
        <view class="action action--primary" @tap.stop="startChat(s.id)">
          <text>🎙️ 开始对话</text>
        </view>
        <view class="action" @tap.stop="goListen(s.id)">
          <text>🎧 听力精听</text>
        </view>
        <view class="action" @tap.stop="goShadow(s.id)">
          <text>🗣️ 跟读评分</text>
        </view>
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

function goCompanion() {
  uni.navigateTo({ url: "/pages/speak/companion" });
}

async function startFreeTalk() {
  uni.showLoading({ title: "找聊伴…" });
  try {
    const free = await api.freeTalk();
    await startChat(free.id);
  } catch (e) {
    uni.hideLoading();
  }
}

function goListen(scenarioId: string) {
  uni.navigateTo({ url: `/pages/listen/listen?id=${scenarioId}` });
}

function goShadow(scenarioId: string) {
  uni.navigateTo({ url: `/pages/practice/shadow?id=${scenarioId}` });
}
</script>

<style scoped>
.hall-page {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
}

.companion-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(120deg, #4f46e5, #7c3aed);
  color: #ffffff;
}

.cb-body {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.cb-title {
  font-size: 32rpx;
  font-weight: 800;
}

.cb-sub {
  font-size: 22rpx;
  opacity: 0.85;
}

.cb-go {
  font-size: 28rpx;
  font-weight: 700;
  color: #ffffff;
}

.free-talk-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(120deg, #0f766e, #16a34a);
  color: #ffffff;
}

.ft-body {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.ft-title {
  font-size: 32rpx;
  font-weight: 800;
}

.ft-sub {
  font-size: 22rpx;
  opacity: 0.85;
}

.ft-go {
  font-size: 28rpx;
  font-weight: 700;
  color: #ffffff;
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

.scenario-actions {
  display: flex;
  gap: 14rpx;
  margin-top: 22rpx;
  border-top: 2rpx solid #f3f4f6;
  padding-top: 22rpx;
}

.action {
  flex: 1;
  text-align: center;
  font-size: 24rpx;
  color: #16a34a;
  background: #f0fdf4;
  border-radius: 999rpx;
  padding: 14rpx 0;
  font-weight: 600;
}

.action--primary {
  background: #16a34a;
  color: #ffffff;
}

.empty {
  padding: 80rpx 0;
  text-align: center;
}
</style>
