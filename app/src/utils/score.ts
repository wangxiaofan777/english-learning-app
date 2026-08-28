/**
 * 跟读评分：把识别文本与目标句做词级 LCS 对齐，
 * 输出 0-100 相似度与逐词命中情况（供高亮展示）。
 * 专业发音评测引擎（音素级）为 V1 规划，见设计文档 §7.1。
 */

export interface ShadowResult {
  score: number;
  /** 目标句逐词状态，与 words 对齐 */
  words: { word: string; hit: boolean }[];
  /** 多说出来的词 */
  extra: string[];
}

export function normalizeWords(text: string): string[] {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9' ]+/g, " ")
    .split(/\s+/)
    .filter(Boolean);
}

export function scoreShadowing(target: string, said: string): ShadowResult {
  const t = normalizeWords(target);
  const s = normalizeWords(said);
  const n = t.length;
  const m = s.length;
  if (n === 0) {
    return { score: 0, words: [], extra: s };
  }
  if (m === 0) {
    return { score: 0, words: t.map((w) => ({ word: w, hit: false })), extra: [] };
  }

  // LCS 动态规划
  const dp: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0));
  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      dp[i][j] = t[i - 1] === s[j - 1]
        ? dp[i - 1][j - 1] + 1
        : Math.max(dp[i - 1][j], dp[i][j - 1]);
    }
  }

  // 回溯标记目标词命中
  const hit = new Array<boolean>(n).fill(false);
  const extra: string[] = [];
  let i = n;
  let j = m;
  while (i > 0 && j > 0) {
    if (t[i - 1] === s[j - 1]) {
      hit[i - 1] = true;
      i--;
      j--;
    } else if (dp[i - 1][j] >= dp[i][j - 1]) {
      i--;
    } else {
      extra.push(s[j - 1]);
      j--;
    }
  }
  while (j > 0) {
    extra.push(s[j - 1]);
    j--;
  }

  let matches = 0;
  for (const h of hit) {
    if (h) matches++;
  }
  // 多说的词按比例轻微扣分，避免复读整段拿高分
  const penalty = Math.min(0.15, extra.length / Math.max(1, m) * 0.5);
  const base = matches / n;
  const score = Math.max(0, Math.min(100, Math.round((base - penalty) * 100)));

  return {
    score,
    words: t.map((w, k) => ({ word: w, hit: hit[k] })),
    extra: extra.reverse(),
  };
}
