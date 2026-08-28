package com.lingo.app.study;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingo.app.scenario.ScenarioService;
import com.lingo.app.conversation.ConversationEntity;
import com.lingo.app.conversation.mapper.ConversationMapper;
import com.lingo.app.study.mapper.StudyLogMapper;
import com.lingo.app.user.UserProfileEntity;
import com.lingo.app.user.mapper.UserProfileMapper;
import com.lingo.app.vocab.VocabEntryEntity;
import com.lingo.app.vocab.mapper.VocabEntryMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudyService {

  private static final Set<String> KINDS =
      Set.of("review", "scenario", "dialog", "listening", "shadowing", "quiz", "daily");

  /** 每种练习的经验值规则：按次给固定值，或按数量给（×count） */
  private static final Map<String, Integer> XP_FIXED =
      Map.of("dialog", 30, "listening", 20, "shadowing", 25, "scenario", 5, "daily", 5);
  private static final Map<String, Integer> XP_PER_COUNT = Map.of("review", 2, "quiz", 3);

  /** 每日一句（按日期轮换） */
  private static final List<String[]> DAILY_SENTENCES = List.of(
      new String[]{"Little by little, one travels far.", "不积跬步，无以至千里。"},
      new String[]{"Practice makes progress, not perfection.", "练习带来的是进步，而不是完美。"},
      new String[]{"A different language is a different vision of life.", "一门不同的语言，是一种不同的人生视野。"},
      new String[]{"Speak clearly, if you can — for who you are matters.", "清晰地表达自己，因为这代表你是谁。"},
      new String[]{"You are never too old to set a new goal.", "设定新目标，永远都不嫌晚。"},
      new String[]{"Small daily improvements are the key to staggering results.", "每天进步一点点，是巨大成就的钥匙。"},
      new String[]{"The limits of my language mean the limits of my world.", "语言的边界，就是世界的边界。"},
      new String[]{"Mistakes are proof that you are trying.", "错误，正是你在努力的证明。"},
      new String[]{"One conversation a day keeps the fear away.", "每天一次开口，胆怯自然远离。"},
      new String[]{"Listen twice, speak once, and remember forever.", "听两遍，说一遍，记一辈子。"},
      new String[]{"Consistency is what transforms average into excellence.", "把平凡变成卓越的，是坚持。"},
      new String[]{"Every word you learn is a door to a new world.", "你学的每个词，都是通往新世界的一扇门。"},
      new String[]{"Don't count the days — make the days count.", "不要数日子，要让日子有意义。"},
      new String[]{"The best time to start was yesterday. The next best is now.", "最好的开始是昨天，其次就是现在。"}
  );

  private final StudyLogMapper studyLogMapper;
  private final UserProfileMapper profileMapper;
  private final VocabEntryMapper vocabMapper;
  private final ConversationMapper conversationMapper;
  private final ScenarioService scenarioService;
  private final com.lingo.app.course.CourseService courseService;

  /** 记录一次学习行为并结算经验值 */
  public void record(Long userId, String kind, int minutes, int count) {
    if (!KINDS.contains(kind)) {
      throw new IllegalArgumentException("unknown study kind: " + kind);
    }
    int xp = XP_FIXED.getOrDefault(kind, 0)
        + XP_PER_COUNT.getOrDefault(kind, 0) * Math.max(0, count);
    StudyLogEntity log = new StudyLogEntity();
    log.setUserId(userId);
    log.setStudyDate(LocalDate.now().toString());
    log.setKind(kind);
    log.setMinutes(minutes);
    log.setCount(count);
    log.setXp(xp);
    studyLogMapper.insert(log);

    UserProfileEntity profile = profileOf(userId);
    profile.setXp((profile.getXp() == null ? 0 : profile.getXp()) + xp);
    profileMapper.updateById(profile);
    updateStreak(userId, LocalDate.now().toString());
  }

  public TodayView today(Long userId) {
    String today = LocalDate.now().toString();
    UserProfileEntity profile = profileOf(userId);
    List<StudyLogEntity> todayRows = studyLogMapper.selectList(
        new LambdaQueryWrapper<StudyLogEntity>()
            .eq(StudyLogEntity::getUserId, userId)
            .eq(StudyLogEntity::getStudyDate, today));
    int todayMinutes = todayRows.stream().mapToInt(r -> r.getMinutes() == null ? 0 : r.getMinutes()).sum();
    int reviewCount = todayRows.stream()
        .filter(r -> "review".equals(r.getKind()))
        .mapToInt(r -> r.getCount() == null ? 0 : r.getCount()).sum();

    long dueCount = vocabMapper.selectCount(new LambdaQueryWrapper<VocabEntryEntity>()
        .eq(VocabEntryEntity::getUserId, userId)
        .le(VocabEntryEntity::getDueAt, LocalDateTime.now()));

    LocalDateTime dayStart = LocalDate.now().atStartOfDay();
    Set<Long> practicedToday = new HashSet<>();
    int dialogsToday = 0;
    for (ConversationEntity c : conversationMapper.selectList(
        new LambdaQueryWrapper<ConversationEntity>()
            .eq(ConversationEntity::getUserId, userId)
            .ge(ConversationEntity::getCreatedAt, dayStart))) {
      if (c.getScenarioId() != null) {
        practicedToday.add(c.getScenarioId());
      }
      dialogsToday++;
    }

    String track = profile.getGoalTrack() == null ? "daily" : profile.getGoalTrack();

    // 每日首次打开：自动打卡 +5 XP
    if (todayRows.stream().noneMatch(r -> "daily".equals(r.getKind()))) {
      record(userId, "daily", 0, 1);
      profile = profileOf(userId);
    }

    Set<Long> practicedAll = new HashSet<>();
    conversationMapper.selectList(new LambdaQueryWrapper<ConversationEntity>()
            .eq(ConversationEntity::getUserId, userId)
            .isNotNull(ConversationEntity::getScenarioId))
        .forEach(c -> practicedAll.add(c.getScenarioId()));

    // 场景任务优先跟随当前课程的当前课时；无课程时回退到轨道推荐
    com.lingo.app.course.CourseService.CourseDetail myCourse = courseService.current(userId);
    com.lingo.app.course.CourseService.LessonView lesson = myCourse == null ? null
        : myCourse.lessons().stream()
            .filter(l -> "current".equals(l.status()) && l.scenarioId() != null)
            .findFirst().orElse(null);

    Long scenarioId;
    String scenarioTitle;
    String lessonType;
    Long lessonId;
    boolean scenarioDone;
    if (lesson != null) {
      scenarioId = lesson.scenarioId();
      scenarioTitle = lesson.titleZh();
      lessonType = lesson.lessonType();
      lessonId = lesson.id();
      scenarioDone = false;
    } else {
      ScenarioService.ScenarioCard recommend = scenarioService.recommend(track, practicedAll);
      scenarioId = recommend == null ? null : recommend.id();
      scenarioTitle = recommend == null ? null : recommend.titleZh();
      lessonType = null;
      lessonId = null;
      scenarioDone = recommend != null && practicedToday.contains(recommend.id());
    }

    List<TodayItem> items = new ArrayList<>();
    int reviewTarget = dueCount == 0 ? 0 : (int) Math.min(15, dueCount);
    items.add(new TodayItem("review", "复习生词", reviewTarget, reviewCount,
        dueCount == 0 ? reviewCount > 0 : reviewCount >= reviewTarget,
        null, null, null, null));
    String scenarioItemTitle = switch (lessonType == null ? "" : lessonType) {
        case "listening" -> "听力精听";
        case "shadowing" -> "跟读评分";
        case "review" -> "单元复习";
        case "dialog" -> "对话实战";
        default -> "学场景";
    };
    items.add(new TodayItem("scenario", scenarioItemTitle, 1, scenarioDone ? 1 : 0, scenarioDone,
        scenarioId, scenarioTitle, lessonType, lessonId));
    items.add(new TodayItem("dialog", "开口对话", 1, dialogsToday, dialogsToday >= 1,
        null, null, null, null));

    return new TodayView(today, profile.getStreakDays(), profile.getCefrLevel(), track,
        dueCount, todayMinutes, items, profile.getXp() == null ? 0 : profile.getXp(),
        dailySentence(LocalDate.now().getDayOfYear()));
  }

  /** 等级公式：LV n 需要 50·(n-1)² 经验（LV1:0, LV2:50, LV3:200, LV4:450…） */
  public static int levelOf(int xp) {
    return (int) Math.floor(Math.sqrt(Math.max(0, xp) / 50.0)) + 1;
  }

  public static String levelTitle(int level) {
    String[] titles = {"英语新手", "词汇学徒", "口语练习生", "场景闯将", "听说达人", "学习高手", "英语大师"};
    return titles[Math.min(level, titles.length) - 1];
  }

  public static String[] dailySentence(int dayOfYear) {
    return DAILY_SENTENCES.get(dayOfYear % DAILY_SENTENCES.size());
  }

  public StatsView stats(Long userId) {
    List<StudyLogEntity> all = studyLogMapper.selectList(
        new LambdaQueryWrapper<StudyLogEntity>().eq(StudyLogEntity::getUserId, userId));
    int totalMinutes = all.stream().mapToInt(r -> r.getMinutes() == null ? 0 : r.getMinutes()).sum();

    long totalDialogs = conversationMapper.selectCount(
        new LambdaQueryWrapper<ConversationEntity>().eq(ConversationEntity::getUserId, userId));
    long wordsTotal = vocabMapper.selectCount(
        new LambdaQueryWrapper<VocabEntryEntity>().eq(VocabEntryEntity::getUserId, userId));
    long wordsLearning = vocabMapper.selectCount(
        new LambdaQueryWrapper<VocabEntryEntity>()
            .eq(VocabEntryEntity::getUserId, userId)
            .gt(VocabEntryEntity::getFsrsReps, 0));

    List<DayMinutes> week = new ArrayList<>();
    int weekXp = 0;
    for (int i = 6; i >= 0; i--) {
      LocalDate date = LocalDate.now().minusDays(i);
      List<StudyLogEntity> dayRows = all.stream()
          .filter(r -> date.toString().equals(r.getStudyDate())).toList();
      int minutes = dayRows.stream().mapToInt(r -> r.getMinutes() == null ? 0 : r.getMinutes()).sum();
      weekXp += dayRows.stream().mapToInt(r -> r.getXp() == null ? 0 : r.getXp()).sum();
      week.add(new DayMinutes(date.toString(), minutes));
    }
    int totalXp = all.stream().mapToInt(r -> r.getXp() == null ? 0 : r.getXp()).sum();
    return new StatsView(totalMinutes, totalDialogs, wordsTotal, wordsLearning, week, weekXp,
        totalXp);
  }

  private void updateStreak(Long userId, String today) {
    UserProfileEntity profile = profileOf(userId);
    if (today.equals(profile.getLastStudyDate())) {
      return;
    }
    profile.setStreakDays(nextStreak(profile.getLastStudyDate(), today,
        profile.getStreakDays() == null ? 0 : profile.getStreakDays()));
    profile.setLastStudyDate(today);
    profileMapper.updateById(profile);
  }

  /** 连续打卡计算（静态便于单测） */
  public static int nextStreak(String lastStudyDate, String today, int currentStreak) {
    if (lastStudyDate == null || lastStudyDate.isBlank()) {
      return 1;
    }
    if (lastStudyDate.equals(today)) {
      return Math.max(1, currentStreak);
    }
    LocalDate last = LocalDate.parse(lastStudyDate);
    if (last.equals(LocalDate.parse(today).minusDays(1))) {
      return currentStreak + 1;
    }
    return 1;
  }

  private UserProfileEntity profileOf(Long userId) {
    UserProfileEntity profile = profileMapper.selectOne(
        new LambdaQueryWrapper<UserProfileEntity>().eq(UserProfileEntity::getUserId, userId));
    if (profile == null) {
      profile = new UserProfileEntity();
      profile.setUserId(userId);
      profile.setDailyMinutes(15);
      profile.setOnboardingStep("done");
      profile.setStreakDays(0);
      profileMapper.insert(profile);
    }
    return profile;
  }

  public record TodayItem(String kind, String title, int target, int doneCount, boolean done,
                          Long scenarioId, String scenarioTitleZh, String lessonType,
                          Long lessonId) {
  }

  public record TodayView(String date, Integer streakDays, String cefrLevel, String goalTrack,
                          long dueCount, int todayMinutes, List<TodayItem> items, int xp,
                          String[] dailySentence) {
  }

  public record DayMinutes(String date, int minutes) {
  }

  public record StatsView(int totalMinutes, long totalDialogs, long wordsTotal,
                          long wordsLearning, List<DayMinutes> week, int weekXp, int totalXp) {
  }
}
