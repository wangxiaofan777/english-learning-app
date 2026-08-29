<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ApiError, api } from "./api";
import DashboardView from "./views/DashboardView.vue";
import ContentView from "./views/ContentView.vue";
import UsersView from "./views/UsersView.vue";

type View = "dashboard" | "content" | "users";

const authed = ref(false);
const view = ref<View>("dashboard");
const password = ref("");
const busy = ref(false);
const loginError = ref("");

onMounted(async () => {
  // Cookie 仍在有效期则直接进入控制台
  try {
    await api.me();
    authed.value = true;
  } catch {
    authed.value = false;
  }
});

async function login() {
  if (busy.value || !password.value) return;
  busy.value = true;
  loginError.value = "";
  try {
    await api.login(password.value);
    password.value = "";
    view.value = "dashboard";
    authed.value = true;
  } catch (e) {
    loginError.value = e instanceof ApiError ? e.message : "登录失败，请稍后再试";
  } finally {
    busy.value = false;
  }
}

async function logout() {
  try {
    await api.logout();
  } finally {
    authed.value = false;
  }
}

function onUnauthorized() {
  authed.value = false;
  view.value = "dashboard";
}
</script>

<template>
  <div v-if="!authed" class="login-wrap">
    <form class="login-card" @submit.prevent="login">
      <h1>TalkBuddy 管理后台</h1>
      <p class="muted">使用 .env 中的 ADMIN_TOKEN 作为管理密码登录，会话 2 小时有效。</p>
      <input
        v-model="password"
        class="input"
        type="password"
        placeholder="管理密码（ADMIN_TOKEN）"
        autocomplete="current-password"
        @keydown.enter="login"
      />
      <p v-if="loginError" class="error">{{ loginError }}</p>
      <button class="btn" type="submit" :disabled="busy || !password">
        {{ busy ? "登录中…" : "登录" }}
      </button>
    </form>
  </div>

  <template v-else>
    <header class="topbar">
      <div class="brand">TalkBuddy <small>管理后台</small></div>
      <nav class="tabs">
        <button
          class="tab"
          :class="{ active: view === 'dashboard' }"
          @click="view = 'dashboard'"
        >
          仪表盘
        </button>
        <button
          class="tab"
          :class="{ active: view === 'content' }"
          @click="view = 'content'"
        >
          内容管理
        </button>
        <button class="tab" :class="{ active: view === 'users' }" @click="view = 'users'">
          用户管理
        </button>
      </nav>
      <button class="btn btn-ghost" @click="logout">退出登录</button>
    </header>

    <main class="container">
      <DashboardView v-if="view === 'dashboard'" @unauthorized="onUnauthorized" />
      <ContentView v-else-if="view === 'content'" @unauthorized="onUnauthorized" />
      <UsersView v-else-if="view === 'users'" @unauthorized="onUnauthorized" />
    </main>
  </template>
</template>
