<template>
  <view class="spell-page">
    <!-- 拼写中 -->
    <template v-if="phase === 'playing' && current">
      <view class="progress-row">
        <view class="progress-bar">
          <view class="progress-inner" :style="{ width: progressPercent }" />
        </view>
        <text class="muted">{{ index + 1 }} / {{ words.length }}</text>
      </view>

      <view class="card card-body">
        <text class="hint">听发音，拼出这个单词</text>
        <view class="sound-row" @tap="playWord">
          <text class="sound-icon">🔊</text>
          <text class="sound-label">点一下再听</text>
        </view>
        <text class="meaning">{{ current.meaningZh || "（想一想它的意思）" }}</text>

        <view class="slots">
          <text
            v-for="(ch, i) in hintChars"
            :key="i"
            class="slot"
            :class="{ revealed: ch !== '_' }"
          >{{ ch }}</text>
        </view>

        <input
          v-model="input"
          class="input"
          placeholder="在这里拼写"
          :disabled="checked"
          @confirm="check"
        />

        <view class="btn-row" v-if="!checked">
          <button class="btn-ghost hint-btn" @tap="useHint">提示首字母</button>
          <button class="btn-primary check-btn" :class="{ disabled: !input.trim() }" @tap="check">
            检查
          </button>
        </view>

        <view v-else class="result-block">
          <text class="result-text" :class="wasRight ? 'good' : 'bad'">
            {{ wasRight ? "✓ 拼对了！" : `✗ 正确拼写：${current.word}` }}
          </text>
          <text v-if="current.exampleEn" class="example">{{ current.exampleEn }}</text>
          <button class="btn-primary next-btn" @tap="next">
            {{ isLast ? "看成绩" : "下一个" }}
          </button>
        </view>
      </view>
    </template>

    <!-- 成绩 -->
    <template v-else-if="phase === 'done'">
      <view class="card done-card">
        <text class="done-emoji">{{ correct >= 8 ? "✍️" : correct >= 5 ? "💪" : "📖" }}</text>
        <text class="done-title">{{ correct }} / {{ words.length }} 拼对</text>
        <text class="muted">获得 {{ correct * 3 }} 经验 · 拼错的词自动进入复习队列</text>
        <button class="btn-primary back-btn" @tap="again">再来一轮</button>
        <button class="btn-ghost back-btn" @tap="goBack">返回词库</button>
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

const phase = ref<"loading" | "playing" | "done">("playing");
const words = ref<VocabEntry[]>([]);
const index = ref(0);
const input = ref("");
const checked = ref(false);
const wasRight = ref(false);
const hintLevel = ref(0);
const correct = ref(0);

const current = computed(() => words.value[index.value]);
const isLast = computed(() => index.value === words.value.length - 1);
const progressPercent = computed(() =>
  words.value.length === 0 ? "0%" : `${Math.round(((index.value + (checked.value ? 1 : 0)) / words.value.length) * 100)}%`
);
const hintChars = computed(() => {
  const word = current.value?.word || "";
  return word.split("").map((ch, i) => (i < hintLevel.value ? ch : "_"));
});

onLoad(async () => {
  // 优先到期词，不足 10 个用词库随机补齐
  const queue = await api.reviewQueue(10);
  let picked = queue.cards;
  if (picked.length < 10) {
    const all = await api.vocabList(1, 50);
    const ids = new Set(picked.map((c) => c.id));
    for (const e of all) {
      if (picked.length >= 10) break;
      if (!ids.has(e.id)) {
        picked = [...picked, e];
        ids.add(e.id);
      }
    }
  }
  words.value = picked;
  phase.value = "playing";
  playWord();
});

function playWord() {
  if (current.value?.word) {
    speak(current.value.word);
  }
}

function useHint() {
  if (hintLevel.value < (current.value?.word.length || 0)) {
    hintLevel.value += 1;
  }
}

function check() {
  if (checked.value || !input.value.trim() || !current.value) return;
  checked.value = true;
  wasRight.value = input.value.trim().toLowerCase() === current.value.word.toLowerCase();
  if (wasRight.value) {
    correct.value += 1;
  }
}

async function next() {
  if (!isLast.value) {
    index.value += 1;
    input.value = "";
    checked.value = false;
    wasRight.value = false;
    hintLevel.value = 0;
    playWord();
    return;
  }
  try {
    await api.recordPractice("spell", 1, correct.value);
  } catch (e) {
    // 上报失败不打断成绩页
  }
  phase.value = "done";
}

async function again() {
  index.value = 0;
  input.value = "";
  checked.value = false;
  wasRight.value = false;
  hintLevel.value = 0;
  correct.value = 0;
  // 洗牌重开
  words.value = [...words.value].sort(() => Math.random() - 0.5);
  phase.value = "playing";
  playWord();
}

function goBack() {
  uni.navigateBack();
}
</script>

<style scoped>
.spell-page {
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
  padding: 56rpx 40rpx;
}

.hint {
  font-size: 22rpx;
  color: #9ca3af;
  margin-bottom: 28rpx;
}

.sound-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: #16a34a;
  color: #ffffff;
  border-radius: 999rpx;
  padding: 18rpx 44rpx;
  margin-bottom: 28rpx;
}

.sound-icon {
  font-size: 32rpx;
}

.sound-label {
  font-size: 26rpx;
}

.meaning {
  font-size: 34rpx;
  font-weight: 700;
  color: #111827;
  margin-bottom: 32rpx;
  text-align: center;
}

.slots {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10rpx;
  margin-bottom: 32rpx;
}

.slot {
  width: 52rpx;
  height: 64rpx;
  border-bottom: 4rpx solid #d1d5db;
  text-align: center;
  font-size: 38rpx;
  font-weight: 700;
  color: #111827;
  line-height: 64rpx;
}

.slot.revealed {
  color: #16a34a;
  border-bottom-color: #16a34a;
}

.input {
  width: 70%;
  background: #f3f4f6;
  border-radius: 16rpx;
  padding: 20rpx 28rpx;
  font-size: 32rpx;
  text-align: center;
  margin-bottom: 28rpx;
}

.btn-row {
  display: flex;
  gap: 20rpx;
  width: 70%;
}

.hint-btn,
.check-btn {
  flex: 1;
}

.result-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
}

.result-text {
  font-size: 32rpx;
  font-weight: 700;
}

.result-text.good {
  color: #15803d;
}

.result-text.bad {
  color: #b91c1c;
}

.example {
  font-size: 24rpx;
  color: #9ca3af;
  font-style: italic;
  text-align: center;
}

.next-btn {
  width: 70%;
  margin-top: 12rpx;
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
  font-size: 40rpx;
  font-weight: 800;
  color: #111827;
}

.back-btn {
  width: 70%;
  margin-top: 8rpx;
}
</style>
