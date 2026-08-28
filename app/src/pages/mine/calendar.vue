<template>
  <view class="cal-page">
    <view class="card">
      <view class="month-row">
        <text class="month-btn" @tap="switchMonth(-1)">←</text>
        <text class="month-title">{{ month }} · 学习 {{ studiedCount }} 天</text>
        <text class="month-btn" @tap="switchMonth(1)">→</text>
      </view>

      <view class="week-header">
        <text v-for="w in weekHeads" :key="w" class="week-cell">{{ w }}</text>
      </view>

      <view class="grid">
        <view v-for="(cell, i) in cells" :key="i" class="cell">
          <template v-if="cell.day > 0">
            <view
              class="day-box"
              :class="{ studied: isStudied(cell), today: isToday(cell) }"
            >
              <text class="day-num">{{ cell.day }}</text>
              <text v-if="isStudied(cell)" class="day-dot">•</text>
            </view>
          </template>
        </view>
      </view>

      <view class="legend">
        <view class="legend-item"><view class="dot studied-dot" /><text class="muted">已学习</text></view>
        <view class="legend-item"><view class="dot today-dot" /><text class="muted">今天</text></view>
        <text class="muted legend-streak">🔥 已连续 {{ streakDays }} 天</text>
      </view>
    </view>

    <view class="card tip-card">
      <text class="muted">绿点代表当天完成过任意练习（打卡、课程、精听、跟读、速测都算）。每天先打开 App 领 5 经验签到，再完成今天的三件事。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { api } from "../../utils/api";
import type { CalendarView } from "../../utils/types";

const weekHeads = ["一", "二", "三", "四", "五", "六", "日"];
const month = ref("");
const days = ref<Map<string, { minutes: number; xp: number }>>(new Map());
const studiedCount = ref(0);
const streakDays = ref(0);

interface Cell {
  day: number;
  date: string;
}

const cells = computed<Cell[]>(() => {
  const [y, m] = month.value.split("-").map(Number);
  const first = new Date(y, m - 1, 1);
  const daysInMonth = new Date(y, m, 0).getDate();
  // 周一为一周开始
  const offset = (first.getDay() + 6) % 7;
  const list: Cell[] = [];
  for (let i = 0; i < offset; i++) {
    list.push({ day: 0, date: "" });
  }
  for (let d = 1; d <= daysInMonth; d++) {
    const date = `${month.value}-${String(d).padStart(2, "0")}`;
    list.push({ day: d, date });
  }
  return list;
});

function isStudied(cell: Cell): boolean {
  return days.value.has(cell.date);
}

function isToday(cell: Cell): boolean {
  return cell.date === new Date().toISOString().slice(0, 10);
}

onLoad(async () => {
  month.value = new Date().toISOString().slice(0, 7);
  await load();
});

async function load() {
  const view: CalendarView = await api.calendar(month.value);
  const map = new Map<string, { minutes: number; xp: number }>();
  for (const d of view.days) {
    map.set(d.date, { minutes: d.minutes, xp: d.xp });
  }
  days.value = map;
  studiedCount.value = view.studiedCount;
  streakDays.value = view.streakDays;
}

async function switchMonth(delta: number) {
  const [y, m] = month.value.split("-").map(Number);
  const next = new Date(y, m - 1 + delta, 1);
  month.value = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, "0")}`;
  await load();
}
</script>

<style scoped>
.cal-page {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
}

.month-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28rpx;
}

.month-btn {
  font-size: 36rpx;
  color: #16a34a;
  padding: 8rpx 24rpx;
}

.month-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #111827;
}

.week-header {
  display: flex;
  margin-bottom: 10rpx;
}

.week-cell {
  flex: 1;
  text-align: center;
  font-size: 22rpx;
  color: #9ca3af;
}

.grid {
  display: flex;
  flex-wrap: wrap;
}

.cell {
  width: calc(100% / 7);
  display: flex;
  justify-content: center;
  padding: 6rpx 0;
}

.day-box {
  width: 64rpx;
  height: 76rpx;
  border-radius: 14rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
}

.day-box.studied {
  background: #dcfce7;
}

.day-box.today {
  border: 3rpx solid #16a34a;
}

.day-num {
  font-size: 26rpx;
  color: #374151;
  line-height: 1.1;
}

.day-dot {
  font-size: 18rpx;
  color: #16a34a;
  line-height: 1;
}

.legend {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 26rpx;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.dot {
  width: 18rpx;
  height: 18rpx;
  border-radius: 6rpx;
}

.studied-dot {
  background: #dcfce7;
  border: 2rpx solid #16a34a;
}

.today-dot {
  border: 2rpx solid #16a34a;
}

.legend-streak {
  margin-left: auto;
}

.tip-card {
  margin-top: 24rpx;
}

.tip-card .muted {
  line-height: 1.7;
}
</style>
