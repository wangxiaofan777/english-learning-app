<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ApiError, api, type ContentStatus, type ScenarioRow } from "../api";

const emit = defineEmits<{ (e: "unauthorized"): void }>();

const status = ref<ContentStatus | null>(null);
const list = ref<ScenarioRow[]>([]);
const total = ref(0);
const page = ref(1);
const source = ref("template");
const busy = ref(false);
const error = ref("");

const genTrack = ref("daily");
const genTopic = ref("");
const genCefr = ref("A2");
const genMsg = ref("");

const TRACKS = [
  { value: "daily", label: "日常生活" },
  { value: "travel", label: "旅行出差" },
  { value: "work", label: "职场办公" },
  { value: "exam", label: "考试备考" },
];
const CEFRS = ["A2", "B1", "B2", "C1"];

async function run(fn: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    await fn();
  } catch (e) {
    if (e instanceof ApiError && e.code === 401) {
      emit("unauthorized");
      return;
    }
    error.value = e instanceof ApiError ? e.message : "操作失败，请稍后再试";
  } finally {
    busy.value = false;
  }
}

const loadStatus = () =>
  api.contentStatus().then((s) => (status.value = s));

const loadList = () =>
  api
    .contentScenarios(source.value, page.value)
    .then((p) => {
      list.value = p.rows;
      total.value = p.total;
    });

function switchSource(next: string) {
  source.value = next;
  page.value = 1;
  run(loadList);
}

function goPage(delta: number) {
  const next = page.value + delta;
  if (next < 1) return;
  page.value = next;
  run(loadList);
}

function generate() {
  const topic = genTopic.value.trim();
  if (!topic) return;
  run(async () => {
    const card = await api.generate(genTrack.value, topic, genCefr.value);
    genMsg.value = `已生成《${card.titleZh}》：${card.lineCount} 句台词 / ${card.vocabCount} 个生词`;
    genTopic.value = "";
    await Promise.all([loadStatus(), loadList()]);
  });
}

function rewriteOne(id: string) {
  run(async () => {
    await api.rewrite(id);
    await Promise.all([loadStatus(), loadList()]);
  });
}

function rewriteBatch() {
  run(async () => {
    const r = await api.rewriteBatch(10);
    genMsg.value = `批量重写完成：本次 ${r.rewritten} 篇，剩余 ${r.remaining} 篇`;
    await Promise.all([loadStatus(), loadList()]);
  });
}

onMounted(() => run(async () => {
  await Promise.all([loadStatus(), loadList()]);
}));
</script>

<template>
  <section>
    <div class="card">
      <h3 class="card-title">新场景生成（LLM 流水线）</h3>
      <div class="form-row">
        <select v-model="genTrack" class="input">
          <option v-for="t in TRACKS" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
        <input
          v-model="genTopic"
          class="input"
          style="width: 260px"
          placeholder="主题，如：机场值机"
          @keydown.enter="generate"
        />
        <select v-model="genCefr" class="input">
          <option v-for="c in CEFRS" :key="c" :value="c">{{ c }}</option>
        </select>
        <button class="btn" :disabled="busy || !genTopic.trim()" @click="generate">
          {{ busy ? "生成中…" : "生成" }}
        </button>
      </div>
      <p v-if="genMsg" class="good">{{ genMsg }}</p>
      <p v-else class="muted">生成即入库并发布到对应轨道，调用真实 LLM 时约需数十秒。</p>
    </div>

    <div class="card">
      <div class="form-row" style="justify-content: space-between">
        <h3 class="card-title" style="margin: 0">
          内容库
          <template v-if="status">
            <span class="chip good" style="margin-left: 8px">精编 {{ status.seed }}</span>
            <span class="chip" style="margin-left: 6px">已重写 {{ status.ai }}</span>
            <span class="chip warn" style="margin-left: 6px">待重写 {{ status.template }}</span>
          </template>
        </h3>
        <button
          class="btn btn-ghost"
          :disabled="busy || !status || status.template === 0"
          @click="rewriteBatch"
        >
          {{ busy ? "处理中…" : "批量重写 10 篇" }}
        </button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>

      <div class="form-row" style="margin: 12px 0">
        <button
          v-for="s in [
            { value: 'template', label: '模板待重写' },
            { value: 'ai', label: 'AI 已重写' },
            { value: 'seed', label: '精编' },
          ]"
          :key="s.value"
          class="tab"
          :class="{ active: source === s.value }"
          @click="switchSource(s.value)"
        >
          {{ s.label }}
        </button>
      </div>

      <table>
        <thead>
          <tr>
            <th>场景</th>
            <th>轨道 / 等级</th>
            <th>来源</th>
            <th>创建时间</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in list" :key="s.id">
            <td>
              <div>{{ s.titleZh }}</div>
              <div class="muted">{{ s.titleEn }}</div>
            </td>
            <td>{{ s.track }} · {{ s.cefr }}</td>
            <td>
              <span class="chip" :class="{ warn: s.source === 'template', good: s.source !== 'template' }">
                {{ s.source === "template" ? "模板" : s.source === "ai" ? "AI" : "精编" }}
              </span>
            </td>
            <td class="muted">{{ (s.createdAt || "").slice(0, 10) }}</td>
            <td>
              <button
                v-if="s.source === 'template'"
                class="btn btn-ghost btn-sm"
                :disabled="busy"
                @click="rewriteOne(s.id)"
              >
                AI 重写
              </button>
            </td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="5" class="muted" style="text-align: center">该来源下暂无场景</td>
          </tr>
        </tbody>
      </table>

      <div class="pager">
        <span class="muted">共 {{ total }} 篇 · 第 {{ page }} 页</span>
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
