package com.lingo.app.study;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StreakTest {

  @Test
  void firstEverStudyStartsStreakAtOne() {
    assertThat(StudyService.nextStreak(null, "2026-08-29", 0)).isEqualTo(1);
  }

  @Test
  void studyingAgainSameDayKeepsStreak() {
    assertThat(StudyService.nextStreak("2026-08-29", "2026-08-29", 5)).isEqualTo(5);
  }

  @Test
  void consecutiveDayIncrements() {
    assertThat(StudyService.nextStreak("2026-08-28", "2026-08-29", 5)).isEqualTo(6);
  }

  @Test
  void gapResetsStreakToOne() {
    assertThat(StudyService.nextStreak("2026-08-20", "2026-08-29", 9)).isEqualTo(1);
  }
}
