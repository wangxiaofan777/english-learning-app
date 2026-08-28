<template>
  <view class="login-page">
    <view class="hero">
      <view class="hero-badge">AI 英语陪练</view>
      <text class="hero-title">每天 15 分钟</text>
      <text class="hero-title hero-title--accent">在真实场景里开口说英语</text>
      <text class="hero-sub">测评定级 · 场景对话 · 生词巩固 · 连续打卡</text>
    </view>

    <view class="actions">
      <!-- #ifdef MP-WEIXIN -->
      <button class="btn-primary" :class="{ disabled: loading }" @tap="wechatLogin">
        {{ loading ? "登录中…" : "微信一键登录" }}
      </button>
      <!-- #endif -->
      <button class="btn-primary" :class="{ disabled: loading }" @tap="guestLogin">
        {{ loading ? "进入中…" : "游客身份快速体验" }}
      </button>
      <text class="muted agreement">登录即代表同意《用户协议》与《隐私政策》；语音数据仅用于本次学习评估。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { api } from "../../utils/api";
import { setAuth, updateProfile } from "../../stores/user";

const loading = ref(false);

async function enter(result: { token: string; profile: any }) {
  setAuth(result.token, result.profile);
  updateProfile(result.profile);
  if (result.profile.onboardingStep === "goal") {
    uni.redirectTo({ url: "/pages/onboarding/onboarding" });
  } else if (result.profile.onboardingStep === "placement") {
    uni.redirectTo({ url: "/pages/placement/placement" });
  } else {
    uni.switchTab({ url: "/pages/index/index" });
  }
}

async function guestLogin() {
  if (loading.value) return;
  loading.value = true;
  try {
    const result = await api.guestLogin("体验用户");
    await enter(result);
  } finally {
    loading.value = false;
  }
}

// #ifdef MP-WEIXIN
async function wechatLogin() {
  if (loading.value) return;
  loading.value = true;
  try {
    const { code } = await uni.login({ provider: "weixin" });
    const result = await api.wechatLogin(code as unknown as string);
    await enter(result);
  } catch (e) {
    console.warn("wechat login failed", e);
  } finally {
    loading.value = false;
  }
}
// #endif
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #16a34a 0%, #15803d 45%, #f6f7f9 45.1%);
  display: flex;
  flex-direction: column;
}

.hero {
  padding: 200rpx 60rpx 80rpx;
  color: #ffffff;
  display: flex;
  flex-direction: column;
}

.hero-badge {
  align-self: flex-start;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 999rpx;
  font-size: 24rpx;
  padding: 8rpx 24rpx;
  margin-bottom: 32rpx;
}

.hero-title {
  font-size: 52rpx;
  font-weight: 700;
  line-height: 1.4;
}

.hero-title--accent {
  color: #dcfce7;
}

.hero-sub {
  margin-top: 24rpx;
  font-size: 26rpx;
  opacity: 0.85;
}

.actions {
  padding: 0 60rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.actions .btn-primary {
  box-shadow: 0 8rpx 24rpx rgba(22, 163, 74, 0.3);
}

.agreement {
  text-align: center;
  line-height: 1.6;
  margin-top: 16rpx;
}
</style>
