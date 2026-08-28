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
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudyService {

  private final StudyLogMapper studyLogMapper;
  private final UserProfileMapper profileMapper;
  private final VocabEntryMapper vocabMapper;
  private final ConversationMapper conversationMapper;
  private final ScenarioService scenarioService;
  private final com.lingo.app.course.CourseService courseService;

  public void record(Long userId, String kind, int minutes, int count) {
    StudyLogEntity log = new StudyLogEntity();
    log.setUserId(userId);
    log.setStudyDate(LocalDate.now().toString());
    log.setKind(kind);
    log.setMinutes(minutes);
    log.setCount(count);
    studyLogMapper.insert(log);
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
        dueCount, todayMinutes, items);
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
    for (int i = 6; i >= 0; i--) {
      LocalDate date = LocalDate.now().minusDays(i);
      int minutes = all.stream()
          .filter(r -> date.toString().equals(r.getStudyDate()))
          .mapToInt(r -> r.getMinutes() == null ? 0 : r.getMinutes()).sum();
      week.add(new DayMinutes(date.toString(), minutes));
    }
    return new StatsView(totalMinutes, totalDialogs, wordsTotal, wordsLearning, week);
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
                          long dueCount, int todayMinutes, List<TodayItem> items) {
  }

  public record DayMinutes(String date, int minutes) {
  }

  public record StatsView(int totalMinutes, long totalDialogs, long wordsTotal,
                          long wordsLearning, List<DayMinutes> week) {
  }
}
