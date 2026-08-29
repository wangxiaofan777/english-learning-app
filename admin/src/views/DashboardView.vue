<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ApiError, api, type Stats } from "../api";

const emit = defineEmits<{ (e: "unauthorized"): void }>();

const stats = ref<Stats | null>(null);
const error = ref("");
const busy = ref(false);

async function load() {
  busy.value = true;
  error.value = "";
  try {
    stats.value = await api.stats();
  } catch (e) {
    if (e instanceof ApiError && e.code === 401) {
      emit("unauthorized");
      return;
    }
    error.value = e instanceof ApiError ? e.message : "加载失败";
  } finally {
    busy.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section>
    <div class="form-row" style="justify-content: space-between">
      <h2 class="card-title" style="margin: 0">运营概览</h2>
      <button class="btn btn-ghost" :disabled="busy" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <template v-if="stats">
      <div class="stat-grid" style="margin-top: 16px">
        <div class="stat">
          <div class="num">{{ stats.users.total }}</div>
          <div class="label">注册用户</div>
          <div class="sub">今日新增 {{ stats.users.todayNew }} · 游客 {{ stats.users.guest }} / 微信 {{ stats.users.wx }}</div>
        </div>
        <div class="stat">
          <div class="num">{{ stats.study.todayActive }}</div>
          <div class="label">今日活跃</div>
          <div class="sub">累计学习 {{ stats.study.todayMinutes }} 分钟</div>
        </div>
        <div class="stat">
          <div class="num">{{ stats.conversations.total }}</div>
          <div class="label">会话总数</div>
          <div class="sub">消息 {{ stats.conversations.messages }} 条 · 生词 {{ stats.vocabTotal }} 个</div>
        </div>
        <div class="stat">
          <div class="num">{{ stats.content.total }}</div>
          <div class="label">场景总数</div>
          <div class="sub">精编 {{ stats.content.seed }} · 已重写 {{ stats.content.ai }} · 待重写 {{ stats.content.template }}</div>
        </div>
      </div>

      <div class="card" style="margin-top: 16px">
        <h3 class="card-title">内容真实化进度</h3>
        <div class="form-row">
          <span class="chip good">精编 {{ stats.content.seed }}</span>
          <span class="chip">AI 重写 {{ stats.content.ai }}</span>
          <span class="chip warn">模板待重写 {{ stats.content.template }}</span>
          <span class="muted">
            真实内容占比 {{ stats.content.total ? Math.round(((stats.content.seed + stats.content.ai) / stats.content.total) * 100) : 0 }}%
          </span>
        </div>
      </div>
    </template>
  </section>
</template>
