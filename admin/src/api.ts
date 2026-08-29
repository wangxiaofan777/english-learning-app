/**
 * 管理后台 API 封装：登录态由 HttpOnly Cookie（admin_session）承载，
 * 前端不保存任何令牌；401 统一抛 ApiError(401) 交由 App 回到登录页。
 */

export class ApiError extends Error {
  constructor(public code: number, message: string) {
    super(message);
  }
}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  let res: Response;
  try {
    res = await fetch(url, {
      method,
      credentials: "same-origin",
      headers: body !== undefined ? { "Content-Type": "application/json" } : undefined,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(-1, "网络异常，请稍后再试");
  }
  let payload: { code?: number; message?: string; data?: T };
  try {
    payload = await res.json();
  } catch {
    throw new ApiError(res.status, `请求失败（HTTP ${res.status}）`);
  }
  if (res.status === 401 || payload.code === 401) {
    throw new ApiError(401, payload.message || "登录已失效，请重新登录");
  }
  if (payload.code !== 0) {
    throw new ApiError(payload.code ?? res.status, payload.message || "请求失败");
  }
  return payload.data as T;
}

// ---------- 类型 ----------

export interface Stats {
  users: { total: number; guest: number; wx: number; todayNew: number };
  study: { todayActive: number; todayMinutes: number };
  vocabTotal: number;
  conversations: { total: number; messages: number };
  content: { total: number; seed: number; ai: number; template: number };
}

export interface ScenarioRow {
  id: string;
  track: string;
  topic: string;
  titleZh: string;
  titleEn: string;
  cefr: string;
  introZh: string;
  source: string;
  status: string;
  createdAt: string;
}

export interface ContentStatus {
  total: number;
  seed: number;
  ai: number;
  template: number;
}

export interface UserRow {
  id: string;
  nickname: string;
  guest: boolean;
  createdAt: string;
  goalTrack?: string;
  cefrLevel?: string;
  streakDays?: number;
  xp?: number;
  lastStudyDate?: string;
}

export interface Paged<T> {
  total: number;
  page: number;
  size: number;
  rows: T[];
}

// 服务端把 Long 一律序列化为字符串（防 JS 精度丢失），统计/分页数字统一在此还原
const n = (v: unknown): number => (typeof v === "number" ? v : Number(v) || 0);

// ---------- 接口 ----------

export const api = {
  login: (password: string) =>
    request<{ expiresIn: number }>("POST", "/api/v1/admin/auth/login", { password }),
  logout: () => request<void>("POST", "/api/v1/admin/auth/logout"),
  me: () => request<{ authenticated: boolean }>("GET", "/api/v1/admin/auth/me"),

  stats: async (): Promise<Stats> => {
    const s = await request<Record<string, any>>("GET", "/api/v1/admin/stats");
    return {
      users: {
        total: n(s.users?.total),
        guest: n(s.users?.guest),
        wx: n(s.users?.wx),
        todayNew: n(s.users?.todayNew),
      },
      study: {
        todayActive: n(s.study?.todayActive),
        todayMinutes: n(s.study?.todayMinutes),
      },
      vocabTotal: n(s.vocabTotal),
      conversations: {
        total: n(s.conversations?.total),
        messages: n(s.conversations?.messages),
      },
      content: {
        total: n(s.content?.total),
        seed: n(s.content?.seed),
        ai: n(s.content?.ai),
        template: n(s.content?.template),
      },
    };
  },

  contentStatus: async (): Promise<ContentStatus> => {
    const s = await request<Record<string, any>>("GET", "/api/v1/admin/content/status");
    return { total: n(s.total), seed: n(s.seed), ai: n(s.ai), template: n(s.template) };
  },
  contentScenarios: async (source: string, page: number): Promise<Paged<ScenarioRow>> => {
    const p = await request<Record<string, any>>(
      "GET",
      `/api/v1/admin/content/scenarios?source=${source}&page=${page}&size=20`
    );
    return { total: n(p.total), page: n(p.page), size: n(p.size), rows: p.rows ?? [] };
  },
  generate: (track: string, topic: string, cefr: string) =>
    request<{ id: string; titleZh: string; lineCount: number; vocabCount: number }>(
      "POST",
      "/api/v1/admin/scenarios/generate",
      { track, topic, cefr }
    ),
  rewrite: (scenarioId: string) =>
    request<{ id: string; titleZh: string }>("POST", "/api/v1/admin/content/rewrite", {
      scenarioId,
    }),
  rewriteBatch: async (limit: number): Promise<{ rewritten: number; remaining: number }> => {
    const r = await request<Record<string, any>>("POST", "/api/v1/admin/content/rewrite-batch", {
      limit,
    });
    return { rewritten: n(r.rewritten), remaining: n(r.remaining) };
  },

  users: async (page: number, q: string): Promise<Paged<UserRow>> => {
    const p = await request<Record<string, any>>(
      "GET",
      `/api/v1/admin/users?page=${page}&size=20${q ? `&q=${encodeURIComponent(q)}` : ""}`
    );
    return { total: n(p.total), page: n(p.page), size: n(p.size), rows: p.rows ?? [] };
  },
};
