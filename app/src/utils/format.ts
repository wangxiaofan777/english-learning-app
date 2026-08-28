/** 后端枚举值 → 人读标签（统一走这里，禁止页面直接显示码值） */

export function trackLabel(track: string | null | undefined): string {
  const map: Record<string, string> = {
    daily: "日常交流",
    work: "职场口语",
    travel: "出国旅行",
    exam: "考试备考",
  };
  return map[track || ""] || "综合练习";
}

export function vocabStateLabel(state: string | null | undefined): string {
  const map: Record<string, string> = {
    new: "新词",
    review: "巩固中",
    relearning: "重学中",
  };
  return map[state || ""] || "新词";
}

export function sourceLabel(source: string | null | undefined): string {
  const map: Record<string, string> = {
    dialog: "对话收藏",
    listening: "听力收藏",
    daily: "每日推荐",
    placement: "测评建议",
    manual: "手动添加",
  };
  return map[source || ""] || "收藏";
}

export function greetingByHour(): string {
  const h = new Date().getHours();
  if (h < 6) return "夜深了";
  if (h < 12) return "早上好";
  if (h < 14) return "中午好";
  if (h < 18) return "下午好";
  return "晚上好";
}

export function minutesToText(minutes: number): string {
  if (minutes < 60) return `${minutes} 分钟`;
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分`;
}
