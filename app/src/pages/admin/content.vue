<template>
  <view class="admin-page">
    <view class="card">
      <text class="section-title">内容运营台</text>
      <text class="muted">用于把课程模板场景重写为真实 LLM 内容。Token 即服务端 ADMIN_TOKEN。</text>
      <view class="token-row">
        <input v-model="token" class="token-input" placeholder="Admin Token" password />
        <button class="btn-primary save-btn" @tap="loadStatus">连接</button>
      </view>
      <view v-if="status" class="status-row">
        <view class="stat"><text class="num">{{ status.total }}</text><text class="muted">总场景</text></view>
        <view class="stat"><text class="num good">{{ status.seed }}</text><text class="muted">精编</text></view>
        <view class="stat"><text class="num good">{{ status.ai }}</text><text class="muted">已重写</text></view>
        <view class="stat"><text class="num warn">{{ status.template }}</text><text class="muted">待重写</text></view>
      </view>
      <view v-if="status" class="actions-row">
        <button class="btn-primary" :class="{ disabled: busy }" @tap="batchRewrite">
          {{ busy ? "重写中…" : `批量重写 10 篇（${status.template} 篇待处理）` }}
        </button>
      </view>
    </view>

    <view class="card" v-if="list.length">
      <text class="section-title">模板场景（{{ list.length }} 篇本页）</text>
      <view v-for="s in list" :key="s.id" class="row">
        <view class="row-body">
          <text class="row-title">{{ s.titleZh }}</text>
          <text class="muted">{{ s.track }} · {{ s.cefr }} · {{ s.source }}</text>
        </view>
        <button class="btn-ghost mini" :class="{ disabled: busy }" @tap="rewriteOne(s.id)">
          AI 重写
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { api } from "../../utils/api";
import type { AdminScenarioRow, AdminStatus } from "../../utils/types";

const TOKEN_KEY = "lingo_admin_token";
const token = ref(uni.getStorageSync(TOKEN_KEY) || "");
const status = ref<AdminStatus | null>(null);
const list = ref<AdminScenarioRow[]>([]);
const busy = ref(false);

async function loadStatus() {
  uni.setStorageSync(TOKEN_KEY, token.value);
  try {
    status.value = await api.adminStatus(token.value);
    list.value = await api.adminScenarios(token.value, "template", 1);
  } catch (e) {
    // 错误 toast 已由请求层给出
  }
}

async function rewriteOne(id: string) {
  if (busy.value) return;
  busy.value = true;
  try {
    await api.adminRewrite(token.value, id);
    uni.showToast({ title: "已重写", icon: "success" });
    await loadStatus();
  } finally {
    busy.value = false;
  }
}

async function batchRewrite() {
  if (busy.value) return;
  busy.value = true;
  try {
    const result = await api.adminRewriteBatch(token.value, 10);
    uni.showToast({
      title: `本次重写 ${result.rewritten} 篇，剩余 ${result.remaining} 篇`,
      icon: "none",
    });
    await loadStatus();
    list.value = await api.adminScenarios(token.value, "template", 1);
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #111827;
  display: block;
  margin-bottom: 10rpx;
}

.token-row {
  display: flex;
  gap: 16rpx;
  margin: 20rpx 0;
}

.token-input {
  flex: 1;
  background: #f3f4f6;
  border-radius: 14rpx;
  padding: 18rpx 24rpx;
  font-size: 26rpx;
}

.save-btn {
  padding: 0 40rpx;
  height: 76rpx;
  line-height: 76rpx;
}

.status-row {
  display: flex;
  gap: 12rpx;
  margin: 20rpx 0;
}

.stat {
  flex: 1;
  background: #f9fafb;
  border-radius: 14rpx;
  padding: 18rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.num {
  font-size: 34rpx;
  font-weight: 800;
  color: #111827;
}

.num.good {
  color: #16a34a;
}

.num.warn {
  color: #f59e0b;
}

.actions-row {
  margin-top: 8rpx;
}

.row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 0;
  border-bottom: 2rpx solid #f3f4f6;
}

.row:last-child {
  border-bottom: none;
}

.row-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.row-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
}

.mini {
  padding: 8rpx 24rpx;
  font-size: 24rpx;
}
</style>
