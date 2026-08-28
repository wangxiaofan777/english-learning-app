package com.lingo.app.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FsrsSchedulerTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 29, 10, 0);

  @Test
  void firstGoodReviewSchedulesNearInitialStability() {
    FsrsScheduler.Result result = FsrsScheduler.review(
        FsrsScheduler.Card.newCard(), 3, NOW, null);
    // 初始稳定性 S(G=3) = w[2] = 3.173 天；0.9 留存率下间隔≈稳定性
    double expectedDays = FsrsScheduler.intervalDays(FsrsScheduler.W.get(2));
    long minutes = java.time.Duration.between(NOW, result.dueAt()).toMinutes();
    assertThat(minutes).isCloseTo((long) (expectedDays * 1440), within(2L));
    assertThat(result.card().state()).isEqualTo("review");
    assertThat(result.card().reps()).isEqualTo(1);
  }

  @Test
  void easyIntervalLongerThanGood() {
    FsrsScheduler.Result good = FsrsScheduler.review(FsrsScheduler.Card.newCard(), 3, NOW, null);
    FsrsScheduler.Result easy = FsrsScheduler.review(FsrsScheduler.Card.newCard(), 4, NOW, null);
    assertThat(easy.dueAt()).isAfter(good.dueAt());
  }

  @Test
  void forgottenCardComesBackQuicklyAndCountsLapse() {
    FsrsScheduler.Result first = FsrsScheduler.review(FsrsScheduler.Card.newCard(), 3, NOW, null);
    FsrsScheduler.Result forgot = FsrsScheduler.review(first.card(), 1,
        NOW.plusDays(4), NOW);
    // FSRS 行为：遗忘后稳定性显著缩短（本参数下约 1 天），远短于遗忘前的间隔
    long minutes = java.time.Duration.between(NOW.plusDays(4), forgot.dueAt()).toMinutes();
    assertThat(minutes).isBetween(60L, 2880L);
    assertThat(forgot.dueAt()).isBefore(NOW.plusDays(4).plusMinutes(firstIntervalMinutes(first)));
    assertThat(forgot.card().state()).isEqualTo("relearning");
    assertThat(forgot.card().lapses()).isEqualTo(1);
  }

  private long firstIntervalMinutes(FsrsScheduler.Result first) {
    return java.time.Duration.between(NOW, first.dueAt()).toMinutes();
  }

  @Test
  void successfulReviewIncreasesStability() {
    FsrsScheduler.Result first = FsrsScheduler.review(FsrsScheduler.Card.newCard(), 3, NOW, null);
    FsrsScheduler.Result second = FsrsScheduler.review(first.card(), 3,
        NOW.plusDays(4), NOW);
    assertThat(second.card().stability()).isGreaterThan(first.card().stability());
  }

  @Test
  void sameDayReviewIsHandled() {
    FsrsScheduler.Result first = FsrsScheduler.review(FsrsScheduler.Card.newCard(), 3, NOW, null);
    FsrsScheduler.Result second = FsrsScheduler.review(first.card(), 3,
        NOW.plusMinutes(30), NOW);
    assertThat(second.card().reps()).isEqualTo(2);
    assertThat(second.dueAt()).isAfter(NOW);
  }

  @Test
  void retrievabilityDecreasesWithTime() {
    // 稳定性的定义：t = s 时留存率恰为 0.9
    double rAtS = FsrsScheduler.retrievability(3.173, 3.173);
    double rLater = FsrsScheduler.retrievability(10, 3.173);
    assertThat(rAtS).isGreaterThan(rLater);
    assertThat(rAtS).isCloseTo(0.9, within(0.01));
  }
}
