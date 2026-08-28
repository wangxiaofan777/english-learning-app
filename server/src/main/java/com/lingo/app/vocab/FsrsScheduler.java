package com.lingo.app.vocab;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FSRS-5 间隔重复算法的内置简化实现（默认 19 参数）。
 * rating：1=忘了 2=困难 3=良好 4=轻松。
 * 稳定性 s 的定义：以 90% 记忆留存率可记住的天数。
 */
public final class FsrsScheduler {

  public static final List<Double> W = List.of(
      0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575,
      0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621);

  private static final double DECAY = -0.5;
  private static final double FACTOR = 19.0 / 81.0;
  private static final double REQUEST_RETENTION = 0.9;

  private FsrsScheduler() {
  }

  public record Card(String state, double stability, double difficulty, int reps, int lapses) {

    public static Card newCard() {
      return new Card("new", 0, 0, 0, 0);
    }
  }

  public record Result(Card card, LocalDateTime dueAt) {
  }

  public static Result review(Card card, int rating, LocalDateTime now,
                              LocalDateTime lastReviewAt) {
    if (rating < 1 || rating > 4) {
      throw new IllegalArgumentException("rating must be 1-4");
    }
    double s = card.stability();
    double d = card.difficulty();

    double nextS;
    if (card.state().equals("new") || s <= 0 || d <= 0) {
      nextS = W.get(rating - 1);
      d = initialDifficulty(rating);
    } else {
      double elapsedDays = lastReviewAt == null ? 0
          : Math.max(0, Duration.between(lastReviewAt, now).toMillis() / 86400000.0);
      if (elapsedDays < 0.02) {
        // 同日多次复习：短期稳定性
        nextS = s * Math.exp(W.get(17) * (rating - 3 + W.get(18)));
      } else if (rating == 1) {
        double r = retrievability(elapsedDays, s);
        nextS = W.get(11) * Math.pow(d, -W.get(12)) * (Math.pow(s + 1, W.get(13)) - 1)
            * Math.exp(W.get(14) * (1 - r));
        nextS = Math.min(nextS, s);
      } else {
        double r = retrievability(elapsedDays, s);
        double hardPenalty = rating == 2 ? W.get(15) : 1;
        double easyBonus = rating == 4 ? W.get(16) : 1;
        nextS = s * (1 + Math.exp(W.get(7)) * (11 - rating) * Math.pow(s, -W.get(8))
            * (Math.exp(W.get(9) * (1 - r)) - 1) * hardPenalty * easyBonus);
      }
      // 难度更新 + 向初始难度回归
      d = d - W.get(5) * (rating - 3);
      double d0 = initialDifficulty(4);
      d = clamp(W.get(6) * d0 + (1 - W.get(6)) * d, 1, 10);
    }

    nextS = clamp(nextS, 0.1, 36500);
    int lapses = card.lapses() + (rating == 1 ? 1 : 0);
    String state = rating == 1 ? "relearning" : "review";
    Card updated = new Card(state, nextS, d, card.reps() + 1, lapses);

    long intervalMinutes = Math.max(rating == 1 ? 10 : 60,
        (long) Math.round(intervalDays(nextS) * 1440));
    return new Result(updated, now.plusMinutes(intervalMinutes));
  }

  /** 可提取概率 R(t,s) = (1 + FACTOR·t/s)^DECAY */
  public static double retrievability(double elapsedDays, double stability) {
    if (stability <= 0) {
      return 0;
    }
    return Math.pow(1 + FACTOR * Math.max(0, elapsedDays) / stability, DECAY);
  }

  /** 由稳定性换算复习间隔（天）；在 0.9 留存率下恰等于 s */
  public static double intervalDays(double stability) {
    return stability / FACTOR * (Math.pow(REQUEST_RETENTION, 1 / DECAY) - 1);
  }

  private static double initialDifficulty(int rating) {
    return clamp(W.get(4) - (rating - 3), 1, 10);
  }

  private static double clamp(double v, double min, double max) {
    return Math.max(min, Math.min(max, v));
  }
}
