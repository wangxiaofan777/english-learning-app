-- Lingo schema (PostgreSQL / H2 PostgreSQL-mode compatible)
CREATE TABLE IF NOT EXISTS t_user (
  id         BIGINT PRIMARY KEY,
  open_id    VARCHAR(64),
  union_id   VARCHAR(64),
  phone      VARCHAR(20),
  nickname   VARCHAR(64),
  avatar     VARCHAR(255),
  is_guest   BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_open_id ON t_user (open_id);

CREATE TABLE IF NOT EXISTS t_user_profile (
  id               BIGINT PRIMARY KEY,
  user_id          BIGINT NOT NULL,
  goal_track       VARCHAR(32),
  daily_minutes    INT DEFAULT 15,
  cefr_level       VARCHAR(8),
  weak_tags        TEXT,
  onboarding_step  VARCHAR(16) DEFAULT 'goal',
  streak_days      INT DEFAULT 0,
  xp               INT DEFAULT 0,
  last_study_date  VARCHAR(10),
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_profile_user ON t_user_profile (user_id);

CREATE TABLE IF NOT EXISTS t_scenario (
  id           BIGINT PRIMARY KEY,
  track        VARCHAR(32) NOT NULL,
  topic        VARCHAR(64),
  title_zh     VARCHAR(128),
  title_en     VARCHAR(128),
  cefr         VARCHAR(8),
  role_setting TEXT,
  intro_zh     VARCHAR(255),
  source       VARCHAR(16) DEFAULT 'seed',
  status       VARCHAR(16) DEFAULT 'published',
  sort_no      INT DEFAULT 0,
  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_scenario_track ON t_scenario (track);

CREATE TABLE IF NOT EXISTS t_scenario_line (
  id          BIGINT PRIMARY KEY,
  scenario_id BIGINT NOT NULL,
  idx         INT,
  speaker     VARCHAR(16),
  en          TEXT,
  zh          TEXT,
  audio_url   VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_line_scenario ON t_scenario_line (scenario_id);

CREATE TABLE IF NOT EXISTS t_scenario_vocab (
  id          BIGINT PRIMARY KEY,
  scenario_id BIGINT NOT NULL,
  word        VARCHAR(64),
  phonetic    VARCHAR(64),
  meaning_zh  VARCHAR(255),
  example_en  TEXT,
  example_zh  TEXT
);
CREATE INDEX IF NOT EXISTS idx_svocab_scenario ON t_scenario_vocab (scenario_id);

CREATE TABLE IF NOT EXISTS t_conversation (
  id            BIGINT PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  scenario_id   BIGINT,
  mode          VARCHAR(16) DEFAULT 'scene',
  companion_key VARCHAR(32),
  status        VARCHAR(16) DEFAULT 'active',
  ai_summary    TEXT,
  coach_json    TEXT,
  msg_count     INT DEFAULT 0,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_conv_user ON t_conversation (user_id);

-- AI 陪练的长期记忆：每个用户 × 每个陪练人设一份 JSON 事实清单
CREATE TABLE IF NOT EXISTS t_companion_memory (
  id            BIGINT PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  companion_key VARCHAR(32) NOT NULL,
  memory_json   TEXT,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_companion_memory ON t_companion_memory (user_id, companion_key);

CREATE TABLE IF NOT EXISTS t_message (
  id              BIGINT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  idx             INT,
  role            VARCHAR(16),
  content         TEXT,
  feedback_json   TEXT,
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_msg_conv ON t_message (conversation_id);

CREATE TABLE IF NOT EXISTS t_vocab_entry (
  id             BIGINT PRIMARY KEY,
  user_id        BIGINT NOT NULL,
  word           VARCHAR(64) NOT NULL,
  phonetic       VARCHAR(64),
  meaning_zh     VARCHAR(255),
  example_en     TEXT,
  example_zh     TEXT,
  source         VARCHAR(24),
  scenario_id    BIGINT,
  fsrs_state     VARCHAR(16) DEFAULT 'new',
  fsrs_stability DOUBLE PRECISION,
  fsrs_difficulty DOUBLE PRECISION,
  fsrs_reps      INT DEFAULT 0,
  fsrs_lapses    INT DEFAULT 0,
  due_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  last_review_at TIMESTAMP,
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_vocab_user_word ON t_vocab_entry (user_id, word);
CREATE INDEX IF NOT EXISTS idx_vocab_due ON t_vocab_entry (user_id, due_at);

CREATE TABLE IF NOT EXISTS t_review_log (
  id          BIGINT PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  vocab_id    BIGINT NOT NULL,
  rating      INT,
  stability   DOUBLE PRECISION,
  difficulty  DOUBLE PRECISION,
  reviewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_study_log (
  id         BIGINT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  study_date VARCHAR(10),
  kind       VARCHAR(16),
  minutes    INT DEFAULT 0,
  count      INT DEFAULT 1,
  xp         INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_study_user_date ON t_study_log (user_id, study_date);

-- ---------- 课程体系 ----------
CREATE TABLE IF NOT EXISTS t_course (
  id          BIGINT PRIMARY KEY,
  track       VARCHAR(32) NOT NULL,
  age_band    VARCHAR(16),
  cefr        VARCHAR(8),
  exam_tag    VARCHAR(32),
  months      INT DEFAULT 3,
  title_zh    VARCHAR(128),
  title_en    VARCHAR(128),
  description TEXT,
  sort_no     INT DEFAULT 0,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_course_lesson (
  id          BIGINT PRIMARY KEY,
  course_id   BIGINT NOT NULL,
  idx         INT,
  lesson_type VARCHAR(16),
  scenario_id BIGINT,
  title_zh    VARCHAR(128),
  minutes     INT DEFAULT 10
);
CREATE INDEX IF NOT EXISTS idx_lesson_course ON t_course_lesson (course_id);

CREATE TABLE IF NOT EXISTS t_user_course (
  id         BIGINT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  course_id  BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_course ON t_user_course (user_id, course_id);

CREATE TABLE IF NOT EXISTS t_lesson_progress (
  id           BIGINT PRIMARY KEY,
  user_id      BIGINT NOT NULL,
  lesson_id    BIGINT NOT NULL,
  course_id    BIGINT NOT NULL,
  status       VARCHAR(16) DEFAULT 'done',
  score        INT,
  completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lesson_progress ON t_lesson_progress (user_id, lesson_id);

-- 历史库平滑迁移
ALTER TABLE t_user_profile ADD COLUMN IF NOT EXISTS age_band VARCHAR(16);
ALTER TABLE t_user_profile ADD COLUMN IF NOT EXISTS xp INT DEFAULT 0;
ALTER TABLE t_study_log ADD COLUMN IF NOT EXISTS xp INT DEFAULT 0;
ALTER TABLE t_course ADD COLUMN IF NOT EXISTS exam_tag VARCHAR(32);
ALTER TABLE t_course ADD COLUMN IF NOT EXISTS months INT DEFAULT 3;
ALTER TABLE t_user ALTER COLUMN is_guest DROP DEFAULT;
ALTER TABLE t_user ALTER COLUMN is_guest SET DATA TYPE BOOLEAN USING is_guest::int::boolean;
ALTER TABLE t_user ALTER COLUMN is_guest SET DEFAULT FALSE;

-- 旧库漂移修复：t_conversation.mode / companion_key 为后加列，建表更早的数据卷需要补齐
ALTER TABLE t_conversation ADD COLUMN IF NOT EXISTS mode VARCHAR(16) DEFAULT 'scene';
ALTER TABLE t_conversation ADD COLUMN IF NOT EXISTS companion_key VARCHAR(32);
