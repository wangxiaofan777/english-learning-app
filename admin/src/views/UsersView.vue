<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ApiError, api, type UserRow } from "../api";

const emit = defineEmits<{ (e: "unauthorized"): void }>();

const rows = ref<UserRow[]>([]);
const total = ref(0);
const page = ref(1);
const q = ref("");
const searched = ref("");
const busy = ref(false);
const error = ref("");

async function load() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    const p = await api.users(page.value, searched.value);
    rows.value = p.rows;
    total.value = p.total;
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

function search() {
  searched.value = q.value.trim();
  page.value = 1;
  load();
}

function goPage(delta: number) {
  const next = page.value + delta;
  if (next < 1) return;
  page.value = next;
  load();
}

onMounted(load);
</script>

<template>
  <section>
    <div class="card">
      <div class="form-row" style="justify-content: space-between">
        <h3 class="card-title" style="margin: 0">用户管理</h3>
        <form class="form-row" @submit.prevent="search">
          <input
            v-model="q"
            class="input"
            style="width: 220px"
            placeholder="昵称 / OpenID 搜索"
            @keydown.enter="search"
          />
          <button class="btn" type="submit" :disabled="busy">查询</button>
        </form>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <p class="muted" style="margin-top: 8px">
        出于隐私保护，列表不展示 OpenID / 手机号等敏感标识；搜索命中 OpenID 时仅返回对应行。
      </p>

      <table style="margin-top: 8px">
        <thead>
          <tr>
            <th>ID</th>
            <th>昵称</th>
            <th>类型</th>
            <th>目标轨道</th>
            <th>CEFR</th>
            <th>连续打卡</th>
            <th>XP</th>
            <th>最近学习</th>
            <th>注册时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in rows" :key="u.id">
            <td class="muted">{{ u.id }}</td>
            <td>{{ u.nickname || "—" }}</td>
            <td>
              <span class="chip" :class="{ warn: u.guest }">{{ u.guest ? "游客" : "微信" }}</span>
            </td>
            <td>{{ u.goalTrack || "—" }}</td>
            <td>{{ u.cefrLevel || "—" }}</td>
            <td>{{ u.streakDays ?? "—" }}</td>
            <td>{{ u.xp ?? "—" }}</td>
            <td class="muted">{{ u.lastStudyDate || "—" }}</td>
            <td class="muted">{{ (u.createdAt || "").slice(0, 10) }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="9" class="muted" style="text-align: center">暂无用户</td>
          </tr>
        </tbody>
      </table>

      <div class="pager">
        <span class="muted">共 {{ total }} 人 · 第 {{ page }} 页</span>
        <button class="btn btn-ghost btn-sm" :disabled="page <= 1 || busy" @click="goPage(-1)">
          上一页
        </button>
        <button class="btn btn-ghost btn-sm" :disabled="busy || page * 20 >= total" @click="goPage(1)">
          下一页
        </button>
      </div>
    </div>
  </section>
</template>
