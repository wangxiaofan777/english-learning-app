<template>
  <view class="course-page">
    <!-- 我的课程 -->
    <template v-if="myCourse">
      <view class="hero">
        <view class="hero-top">
          <text class="hero-title">{{ myCourse.titleZh }}</text>
          <text class="chip hero-cefr" v-if="myCourse.cefr">{{ myCourse.cefr }}</text>
        </view>
        <text class="hero-sub">{{ myCourse.description }}</text>
        <view class="progress-row">
          <view class="progress-bar">
            <view class="progress-inner" :style="{ width: progressPercent }" />
          </view>
          <text class="progress-text">{{ myCourse.doneCount }} / {{ myCourse.totalCount }} 课时</text>
        </view>
      </view>

      <!-- 课时清单 -->
      <view class="card lesson-card">
        <text class="section-title">课时安排</text>
        <text class="muted lesson-hint">完成当前课时后解锁下一课</text>
        <view
          v-for="l in myCourse.lessons"
          :key="l.id"
          class="lesson-row"
          :class="{ locked: l.status === 'locked' }"
          @tap="openLesson(l)"
        >
          <view class="lesson-state" :class="l.status">
            <text>{{ l.status === "done" ? "✓" : l.status === "current" ? "▶" : "🔒" }}</text>
          </view>
          <view class="lesson-body">
            <text class="lesson-title">{{ l.titleZh }}</text>
            <text class="muted">
              {{ lessonTypeLabel(l.lessonType) }} · 约 {{ l.minutes }} 分钟
              <template v-if="l.status === 'current'"> · 进行中</template>
            </text>
          </view>
          <text v-if="l.status === 'current'" class="lesson-go">继续 →</text>
        </view>
      </view>

      <view v-if="allDone" class="card done-banner">
        <text class="done-emoji">🎉</text>
        <text class="done-text">这门课已全部完成，去「练口语」自由实战吧！</text>
      </view>
    </template>

    <!-- 未报名：引导选课 -->
    <view v-else class="card hero-empty">
      <text class="title-lg">选一门课程开始</text>
      <text class="muted">完成登记后系统会为你自动制定课程，也可以在下方手动切换</text>
    </view>

    <!-- 全部课程 -->
    <view class="card">
      <text class="section-title">全部课程</text>
      <view
        v-for="c in courses"
        :key="c.id"
        class="course-row"
        :class="{ active: myCourse && myCourse.id === c.id }"
        @tap="pickCourse(c)"
      >
        <view class="course-body">
          <text class="course-title">{{ c.titleZh }}</text>
          <text class="muted">
            {{ trackLabel(c.track) }} · {{ c.lessonCount }} 课时
            <template v-if="c.cefr"> · {{ c.cefr }}</template>
          </text>
          <text class="course-desc">{{ c.description }}</text>
        </view>
        <text class="chip" :class="c.enrolled ? '' : 'chip--gray'">
          {{ myCourse && myCourse.id === c.id ? "学习中" : c.enrolled ? "已报名" : "开始学习" }}
        </text>
      </view>
    </view>

    <!-- 自由练习 -->
    <view class="card free-card" @tap="goHall">
      <view class="free-body">
        <text class="section-title">自由练习</text>
        <text class="muted">不跟课程，随到随练：对话大厅 / 听力精听 / 跟读评分</text>
      </view>
      <text class="free-go">→</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { api } from "../../utils/api";
import type { CourseCard, CourseDetail, LessonView } from "../../utils/types";
import { ensureAuth } from "../../stores/user";
import { lessonTypeLabel, trackLabel } from "../../utils/format";

const myCourse = ref<CourseDetail | null>(null);
const courses = ref<CourseCard[]>([]);

const progressPercent = computed(() =>
  !myCourse.value || myCourse.value.totalCount === 0
    ? "0%"
    : `${Math.round((myCourse.value.doneCount / myCourse.value.totalCount) * 100)}%`
);
const allDone = computed(
  () =>
    !!myCourse.value &&
    myCourse.value.totalCount > 0 &&
    myCourse.value.doneCount >= myCourse.value.totalCount
);

onShow(async () => {
  if (!ensureAuth()) return;
  await refresh();
});

async function refresh() {
  const [current, list] = await Promise.all([api.currentCourse(), api.courses()]);
  myCourse.value = current;
  courses.value = list;
}

/** 按课时类型路由到对应练习页 */
function openLesson(lesson: LessonView) {
  if (lesson.status === "locked") {
    uni.showToast({ title: "先完成前面的课时", icon: "none" });
    return;
  }
  routeToLesson(lesson.lessonType, lesson.scenarioId);
}

async function routeToLesson(
  lessonType: "dialog" | "listening" | "shadowing",
  scenarioId: string
) {
  if (lessonType === "listening") {
    uni.navigateTo({ url: `/pages/listen/listen?id=${scenarioId}` });
  } else if (lessonType === "shadowing") {
    uni.navigateTo({ url: `/pages/practice/shadow?id=${scenarioId}` });
  } else {
    uni.showLoading({ title: "准备场景…" });
    try {
      const conv = await api.createConversation(scenarioId);
      uni.hideLoading();
      uni.navigateTo({ url: `/pages/speak/chat?id=${conv.conversationId}` });
    } catch (e) {
      uni.hideLoading();
    }
  }
}

async function pickCourse(c: CourseCard) {
  if (myCourse.value && myCourse.value.id === c.id) return;
  await api.enrollCourse(c.id);
  await refresh();
  uni.showToast({ title: `已切换到「${c.titleZh}」`, icon: "none" });
}

function goHall() {
  uni.switchTab({ url: "/pages/speak/hall" });
}
</script>

<style scoped>
.course-page {
  min-height: 100vh;
  padding: 24rpx 0 60rpx;
}

.hero {
  background: linear-gradient(135deg, #16a34a, #0f766e);
  color: #ffffff;
  margin: 0 24rpx 24rpx;
  border-radius: 24rpx;
  padding: 40rpx 36rpx;
}

.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hero-title {
  font-size: 40rpx;
  font-weight: 800;
}

.hero-cefr {
  background: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

.hero-sub {
  display: block;
  margin-top: 14rpx;
  font-size: 24rpx;
  opacity: 0.88;
  line-height: 1.6;
}

.progress-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 28rpx;
}

.progress-bar {
  flex: 1;
  height: 14rpx;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 999rpx;
  overflow: hidden;
}

.progress-inner {
  height: 100%;
  background: #ffffff;
  border-radius: 999rpx;
  transition: width 0.2s;
}

.progress-text {
  font-size: 22rpx;
  opacity: 0.9;
  flex-shrink: 0;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #111827;
}

.lesson-hint {
  display: block;
  margin: 8rpx 0 8rpx;
}

.lesson-row {
  display: flex;
  align-items: center;
  gap: 22rpx;
  padding: 26rpx 0;
  border-bottom: 2rpx solid #f3f4f6;
}

.lesson-row:last-child {
  border-bottom: none;
}

.lesson-row.locked {
  opacity: 0.5;
}

.lesson-state {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  background: #f3f4f6;
  color: #6b7280;
  flex-shrink: 0;
}

.lesson-state.done {
  background: #dcfce7;
  color: #15803d;
}

.lesson-state.current {
  background: #16a34a;
  color: #ffffff;
}

.lesson-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.lesson-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
}

.lesson-go {
  font-size: 24rpx;
  color: #16a34a;
  font-weight: 600;
}

.done-banner {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.done-emoji {
  font-size: 44rpx;
}

.done-text {
  font-size: 26rpx;
  color: #15803d;
}

.hero-empty {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 60rpx 32rpx;
}

.course-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 26rpx 0;
  border-bottom: 2rpx solid #f3f4f6;
}

.course-row:last-child {
  border-bottom: none;
}

.course-row.active .course-title {
  color: #16a34a;
}

.course-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.course-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #111827;
}

.course-desc {
  font-size: 22rpx;
  color: #9ca3af;
  line-height: 1.5;
}

.free-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.free-body {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.free-go {
  font-size: 36rpx;
  color: #16a34a;
  font-weight: 700;
}
</style>
