/** 与后端 API 契约对应的类型定义（见 docs/api.md） */

export interface Profile {
  userId: string;
  nickname: string;
  isGuest: boolean;
  ageBand: string | null;
  goalTrack: string;
  dailyMinutes: number;
  cefrLevel: string | null;
  weakTags: string[];
  onboardingStep: string;
  streakDays: number;
}

export interface LoginResult {
  token: string;
  onboardingStep: string;
  profile: Profile;
}

export interface PlacementQuestion {
  id: string;
  type: string;
  cefr: string;
  stem: string;
  options: { key: string; text: string }[];
}

export interface PlacementResult {
  score: number;
  total: number;
  cefr: string;
  weakTags: string[];
  spokenComment: string;
}

export interface ScenarioCard {
  id: string;
  track: string;
  topic: string;
  titleZh: string;
  titleEn: string;
  cefr: string;
  introZh: string;
  lineCount: number;
  vocabCount: number;
  practiced: boolean;
}

export interface ScenarioVocab {
  word: string;
  phonetic: string;
  meaningZh: string;
  exampleEn: string;
  exampleZh: string;
}

export interface ScenarioDetail extends ScenarioCard {
  roleSetting: string;
  lines: { idx: number; speaker: string; en: string; zh: string; audioUrl: string | null }[];
  vocab: ScenarioVocab[];
}

export interface GrammarFix {
  original: string;
  corrected: string;
  explain: string;
}

export interface VocabHint {
  word: string;
  meaningZh: string;
}

export interface Feedback {
  betterWay: string;
  grammarFix: GrammarFix | null;
  vocabHints: VocabHint[];
}

export interface MessageView {
  id: string;
  role: "user" | "assistant";
  content: string;
  feedback: Feedback | null;
}

export interface CreateConversationResult {
  conversationId: string;
  titleZh: string;
  titleEn: string;
  cefr: string;
  roleSetting: string;
  vocab: ScenarioVocab[];
  messages: MessageView[];
}

export interface ConversationDetail {
  id: string;
  scenarioId: string;
  titleZh: string;
  titleEn: string;
  cefr: string;
  roleSetting: string;
  status: string;
  msgCount: number;
  messages: MessageView[];
}

export interface Recap {
  summary: string;
  strengths: string[];
  suggestions: string[];
}

export interface FinishResult {
  recap: Recap;
  vocab: ScenarioVocab[];
}

export interface VocabEntry {
  id: string;
  word: string;
  phonetic: string | null;
  meaningZh: string | null;
  exampleEn: string | null;
  exampleZh: string | null;
  source: string;
  fsrsState: string;
  dueAt: string;
  lastReviewAt: string | null;
}

export interface TodayItem {
  kind: "review" | "scenario" | "dialog";
  title: string;
  target: number;
  doneCount: number;
  done: boolean;
  scenarioId: string | null;
  scenarioTitleZh: string | null;
  lessonType: "dialog" | "listening" | "shadowing" | null;
  lessonId: string | null;
}

export interface TodayView {
  date: string;
  streakDays: number;
  cefrLevel: string | null;
  goalTrack: string;
  dueCount: number;
  todayMinutes: number;
  items: TodayItem[];
}

export interface StatsView {
  totalMinutes: number;
  totalDialogs: number;
  wordsTotal: number;
  wordsLearning: number;
  week: { date: string; minutes: number }[];
}

export interface CourseCard {
  id: string;
  track: string;
  ageBand: string | null;
  cefr: string | null;
  examTag: string | null;
  months: number;
  titleZh: string;
  titleEn: string;
  description: string;
  lessonCount: number;
  doneCount: number;
  enrolled: boolean;
}

export type LessonStatus = "done" | "current" | "locked";

export interface LessonView {
  id: string;
  idx: number;
  lessonType: "dialog" | "listening" | "shadowing" | "review";
  scenarioId: string | null;
  titleZh: string;
  minutes: number;
  status: LessonStatus;
}

export interface CourseDetail {
  id: string;
  track: string;
  ageBand: string | null;
  cefr: string | null;
  examTag: string | null;
  months: number;
  titleZh: string;
  titleEn: string;
  description: string;
  lessons: LessonView[];
  currentLessonId: string | null;
  doneCount: number;
  totalCount: number;
}
