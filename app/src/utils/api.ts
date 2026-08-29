import type {
  Badge,
  CalendarView,
  CompanionStartResult,
  CompanionView,
  ConversationDetail,
  CourseCard,
  CourseDetail,
  CreateConversationResult,
  FinishResult,
  LoginResult,
  MessageView,
  PlacementQuestion,
  PlacementResult,
  Profile,
  ScenarioCard,
  ScenarioDetail,
  StatsView,
  TodayView,
  VocabEntry,
} from "./types";

// H5 走同源 nginx 反代（/api → server:8080）；小程序/App 构建时用 VITE_API_BASE_URL 注入后端地址
// （本地开发写在 app/.env.development，生产写在 app/.env.production，必须是 HTTPS 合法域名）
let BASE_URL = "";
// #ifndef H5
BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";
// #endif

const TOKEN_KEY = "lingo_token";

export function getToken(): string {
  return uni.getStorageSync(TOKEN_KEY) || "";
}

export function setToken(token: string) {
  uni.setStorageSync(TOKEN_KEY, token);
}

export function clearToken() {
  uni.removeStorageSync(TOKEN_KEY);
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

function request<T>(method: "GET" | "POST", url: string, body?: unknown): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const header: Record<string, string> = { "Content-Type": "application/json" };
    const token = getToken();
    if (token) {
      header.Authorization = `Bearer ${token}`;
    }
    uni.request({
      url: BASE_URL + url,
      method,
      data: body as AnyObject | undefined,
      header,
      timeout: 30000,
      success: (res) => {
        const payload = res.data as ApiResponse<T>;
        if (payload && payload.code === 0) {
          resolve(payload.data);
        } else {
          if (res.statusCode === 401) {
            clearToken();
            uni.reLaunch({ url: "/pages/login/login" });
          }
          const message = payload ? payload.message : `请求失败(${res.statusCode})`;
          uni.showToast({ title: message, icon: "none" });
          reject(new Error(message));
        }
      },
      fail: (err) => {
        uni.showToast({ title: "网络异常，请稍后重试", icon: "none" });
        reject(err);
      },
    });
  });
}

export const api = {
  guestLogin: (nickname?: string) =>
    request<LoginResult>("POST", "/api/v1/auth/guest", { nickname }),
  wechatLogin: (code: string, nickname?: string) =>
    request<LoginResult>("POST", "/api/v1/auth/wechat", { code, nickname }),
  me: () => request<Profile>("GET", "/api/v1/me"),
  onboarding: (ageBand: string | null, goalTrack: string, dailyMinutes: number) =>
    request<{ nextStep: string; profile: Profile }>("POST", "/api/v1/onboarding", {
      ageBand,
      goalTrack,
      dailyMinutes,
    }),
  placementQuestions: () =>
    request<PlacementQuestion[]>("GET", "/api/v1/placement/questions"),
  placementSubmit: (answers: Record<string, string>, spokenText?: string) =>
    request<PlacementResult>("POST", "/api/v1/placement/submit", { answers, spokenText }),
  scenarios: (track?: string) =>
    request<ScenarioCard[]>("GET", `/api/v1/scenarios?size=50${track ? `&track=${track}` : ""}`),
  recommended: () => request<ScenarioCard | null>("GET", "/api/v1/scenarios/recommended"),
  scenarioDetail: (id: string) => request<ScenarioDetail>("GET", `/api/v1/scenarios/${id}`),
  createConversation: (scenarioId: string) =>
    request<CreateConversationResult>("POST", "/api/v1/conversations", { scenarioId }),
  conversationDetail: (id: string) =>
    request<ConversationDetail>("GET", `/api/v1/conversations/${id}`),
  reply: (conversationId: string, content: string) =>
    request<MessageView>("POST", `/api/v1/conversations/${conversationId}/messages`, { content }),
  finishConversation: (conversationId: string) =>
    request<FinishResult>("POST", `/api/v1/conversations/${conversationId}/finish`),
  addVocab: (word: string, meaningZh?: string, source?: string, scenarioId?: string) =>
    request<VocabEntry>("POST", "/api/v1/vocab", { word, meaningZh, source, scenarioId }),
  vocabList: (page = 1, size = 50) =>
    request<VocabEntry[]>("GET", `/api/v1/vocab?page=${page}&size=${size}`),
  reviewQueue: (limit = 15) =>
    request<{ cards: VocabEntry[]; dueCount: number }>("GET", `/api/v1/vocab/queue?limit=${limit}`),
  grade: (id: string, rating: number) =>
    request<{ id: string; word: string; nextDueAt: string }>("POST", `/api/v1/vocab/${id}/grade`, {
      rating,
    }),
  today: () => request<TodayView>("GET", "/api/v1/today"),
  stats: () => request<StatsView>("GET", "/api/v1/stats"),
  achievements: () => request<Badge[]>("GET", "/api/v1/achievements"),
  freeTalk: () => request<ScenarioCard>("GET", "/api/v1/scenarios/free-talk"),
  companionList: () => request<CompanionView[]>("GET", "/api/v1/companion"),
  companionStart: (companionKey: string) =>
    request<CompanionStartResult>("POST", "/api/v1/companion/start", { companionKey }),
  companionMemory: (companionKey: string) =>
    request<string[]>("GET", `/api/v1/companion/memory?companionKey=${encodeURIComponent(companionKey)}`),
  companionForget: (companionKey: string, fact: string) =>
    request<string[]>("POST", "/api/v1/companion/forget", { companionKey, fact }),
  calendar: (month?: string) =>
    request<CalendarView>("GET", `/api/v1/study/calendar${month ? `?month=${month}` : ""}`),
  recordPractice: (kind: string, minutes = 1, count = 1) =>
    request<void>("POST", "/api/v1/study/record", { kind, minutes, count }),
  courses: () => request<CourseCard[]>("GET", "/api/v1/courses"),
  currentCourse: () => request<CourseDetail | null>("GET", "/api/v1/courses/current"),
  enrollCourse: (id: string) => request<CourseCard>("POST", `/api/v1/courses/${id}/enroll`),
  completeLesson: (lessonType: string, scenarioId: string | null, score?: number) =>
    request<{ newlyDone: boolean; doneCount: number; totalCount: number; courseFinished: boolean }>(
      "POST",
      "/api/v1/courses/complete",
      { lessonType, scenarioId, score }
    ),
};

type AnyObject = Record<string, unknown>;
