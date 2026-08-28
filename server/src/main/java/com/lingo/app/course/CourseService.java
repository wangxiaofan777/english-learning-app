package com.lingo.app.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingo.app.common.ApiException;
import com.lingo.app.course.mapper.CourseLessonMapper;
import com.lingo.app.course.mapper.CourseMapper;
import com.lingo.app.course.mapper.LessonProgressMapper;
import com.lingo.app.course.mapper.UserCourseMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 课程体系：登记（年龄段+目标+等级）→ 自动制定/报名课程 → 课时线性解锁 → 完课记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

  private final CourseMapper courseMapper;
  private final CourseLessonMapper lessonMapper;
  private final UserCourseMapper userCourseMapper;
  private final LessonProgressMapper progressMapper;

  // ---------- 查询 ----------

  public List<CourseCard> listForUser(Long userId) {
    Set<Long> enrolled = enrolledCourseIds(userId);
    Set<Long> doneLessonIds = doneLessonIds(userId);
    List<CourseEntity> courses = courseMapper.selectList(
        new LambdaQueryWrapper<CourseEntity>().orderByAsc(CourseEntity::getSortNo));
    List<CourseCard> cards = new ArrayList<>();
    for (CourseEntity c : courses) {
      List<CourseLessonEntity> lessons = lessonsOf(c.getId());
      long done = lessons.stream().filter(l -> doneLessonIds.contains(l.getId())).count();
      cards.add(new CourseCard(c.getId(), c.getTrack(), c.getAgeBand(), c.getCefr(),
          c.getExamTag(), c.getMonths() == null ? 3 : c.getMonths(),
          c.getTitleZh(), c.getTitleEn(), c.getDescription(), lessons.size(), (int) done,
          enrolled.contains(c.getId())));
    }
    return cards;
  }

  public CourseDetail detail(Long userId, Long courseId) {
    CourseEntity c = courseMapper.selectById(courseId);
    if (c == null) {
      throw ApiException.notFound("课程不存在");
    }
    return buildDetail(userId, c);
  }

  /** 当前学习中的课程（最近报名的一个）；未报名返回 null */
  public CourseDetail current(Long userId) {
    UserCourseEntity uc = userCourseMapper.selectList(
            new LambdaQueryWrapper<UserCourseEntity>()
                .eq(UserCourseEntity::getUserId, userId)
                .orderByDesc(UserCourseEntity::getId)
                .last("limit 1"))
        .stream().findFirst().orElse(null);
    if (uc == null) {
      return null;
    }
    CourseEntity c = courseMapper.selectById(uc.getCourseId());
    if (c == null) {
      return null;
    }
    return buildDetail(userId, c);
  }

  // ---------- 报名与制定 ----------

  public CourseCard enroll(Long userId, Long courseId) {
    CourseEntity c = courseMapper.selectById(courseId);
    if (c == null) {
      throw ApiException.notFound("课程不存在");
    }
    UserCourseEntity uc = new UserCourseEntity();
    uc.setUserId(userId);
    uc.setCourseId(courseId);
    try {
      userCourseMapper.insert(uc);
    } catch (DuplicateKeyException e) {
      // 已报名，视为成功（重复报名幂等）
    }
    List<CourseCard> cards = listForUser(userId);
    return cards.stream().filter(x -> x.id().equals(courseId)).findFirst().orElseThrow();
  }

  /**
   * 按登记信息自动制定课程：年龄段 + 目标 → 学段方向；默认 3 个月周期（快速见效，可随时切换）。
   * 方向映射：少儿→小学(KET)；青少年按等级→初中(中考)/高中(高考)；
   * 成人：职场→职场(BEC)、考试→四六级、旅行/日常→出境生活。
   */
  public void autoEnroll(Long userId, String track, String ageBand, String cefr) {
    if (current(userId) != null) {
      return; // 已有进行中的课程，不打乱用户节奏
    }
    String direction = resolveDirection(track, ageBand, cefr);
    List<CourseEntity> candidates = courseMapper.selectList(
        new LambdaQueryWrapper<CourseEntity>()
            .eq(CourseEntity::getTrack, direction)
            .eq(CourseEntity::getMonths, 3));
    CourseEntity pick = candidates.isEmpty()
        ? courseMapper.selectList(new LambdaQueryWrapper<CourseEntity>()
            .eq(CourseEntity::getTrack, direction)).stream().findFirst().orElse(null)
        : candidates.get(0);
    if (pick == null) {
      pick = courseMapper.selectList(new LambdaQueryWrapper<CourseEntity>()
          .orderByAsc(CourseEntity::getSortNo)).stream().findFirst().orElse(null);
    }
    if (pick == null) {
      return;
    }
    enroll(userId, pick.getId());
    log.info("auto-enrolled user {} into course {} (direction={}, age={}, cefr={})",
        userId, pick.getId(), direction, ageBand, cefr);
  }

  private String resolveDirection(String track, String ageBand, String cefr) {
    String level = cefr == null ? "A2" : cefr;
    if ("child".equals(ageBand)) {
      return "primary";
    }
    if ("teen".equals(ageBand)) {
      return level.startsWith("B") ? "senior" : "junior";
    }
    return switch (track == null ? "" : track) {
      case "work" -> "work";
      case "exam" -> "cet";
      case "travel", "daily" -> "travel";
      default -> "travel";
    };
  }

  // ---------- 完课 ----------

  /**
   * 按练习结果标记课时完成（对话/精听/跟读结束由调用方触发），幂等。
   */
  public CompleteResult completeLesson(Long userId, String lessonType, Long scenarioId,
                                       Integer score) {
    CourseDetail current = current(userId);
    if (current == null || current.currentLessonId() == null) {
      return new CompleteResult(false, null, 0, 0, false);
    }
    List<CourseLessonEntity> all = lessonsOf(current.id());
    CourseLessonEntity lesson;
    if (scenarioId != null) {
      // 练习带场景：按类型 + 场景匹配（自由练习同场景也可推进课时）
      lesson = all.stream()
          .filter(l -> l.getLessonType().equals(lessonType)
              && scenarioId.equals(l.getScenarioId()))
          .findFirst()
          .orElse(null);
    } else {
      // 无场景（如单元复习）：只标记当前课时，避免跳关
      lesson = all.stream()
          .filter(l -> l.getId().equals(current.currentLessonId())
              && l.getLessonType().equals(lessonType))
          .findFirst()
          .orElse(null);
    }
    if (lesson == null) {
      return new CompleteResult(false, null, current.doneCount(), current.totalCount(), false);
    }
    boolean newlyDone = false;
    if (!doneLessonIds(userId).contains(lesson.getId())) {
      LessonProgressEntity p = new LessonProgressEntity();
      p.setUserId(userId);
      p.setLessonId(lesson.getId());
      p.setCourseId(current.id());
      p.setStatus("done");
      p.setScore(score);
      try {
        progressMapper.insert(p);
        newlyDone = true;
      } catch (DuplicateKeyException e) {
        // 并发重复完成，忽略
      }
    }
    CourseDetail after = buildDetail(userId, courseMapper.selectById(current.id()));
    return new CompleteResult(newlyDone, lesson.getId(), after.doneCount(), after.totalCount(),
        after.doneCount() >= after.totalCount());
  }

  // ---------- 内部 ----------

  private CourseDetail buildDetail(Long userId, CourseEntity c) {
    Set<Long> doneLessonIds = doneLessonIds(userId);
    List<CourseLessonEntity> lessons = lessonsOf(c.getId());
    Long currentLessonId = lessons.stream()
        .filter(l -> !doneLessonIds.contains(l.getId()))
        .min(Comparator.comparing(CourseLessonEntity::getIdx, Comparator.nullsLast(Integer::compareTo)))
        .map(CourseLessonEntity::getId)
        .orElse(null);
    List<LessonView> views = lessons.stream()
        .map(l -> {
          String status = doneLessonIds.contains(l.getId()) ? "done"
              : l.getId().equals(currentLessonId) ? "current" : "locked";
          return new LessonView(l.getId(), l.getIdx(), l.getLessonType(), l.getScenarioId(),
              l.getTitleZh(), l.getMinutes() == null ? 10 : l.getMinutes(), status);
        })
        .toList();
    long done = views.stream().filter(v -> "done".equals(v.status())).count();
    return new CourseDetail(c.getId(), c.getTrack(), c.getAgeBand(), c.getCefr(),
        c.getExamTag(), c.getMonths() == null ? 3 : c.getMonths(), c.getTitleZh(),
        c.getTitleEn(), c.getDescription(), views, currentLessonId, (int) done, views.size());
  }

  private List<CourseLessonEntity> lessonsOf(Long courseId) {
    return lessonMapper.selectList(new LambdaQueryWrapper<CourseLessonEntity>()
        .eq(CourseLessonEntity::getCourseId, courseId)
        .orderByAsc(CourseLessonEntity::getIdx));
  }

  private Set<Long> enrolledCourseIds(Long userId) {
    Set<Long> ids = new HashSet<>();
    userCourseMapper.selectList(new LambdaQueryWrapper<UserCourseEntity>()
            .eq(UserCourseEntity::getUserId, userId))
        .forEach(uc -> ids.add(uc.getCourseId()));
    return ids;
  }

  private Set<Long> doneLessonIds(Long userId) {
    Set<Long> ids = new HashSet<>();
    progressMapper.selectList(new LambdaQueryWrapper<LessonProgressEntity>()
            .eq(LessonProgressEntity::getUserId, userId))
        .forEach(p -> ids.add(p.getLessonId()));
    return ids;
  }

  // ---------- DTO ----------

  public record CourseCard(Long id, String track, String ageBand, String cefr, String examTag,
                           int months, String titleZh, String titleEn, String description,
                           int lessonCount, int doneCount, boolean enrolled) {
  }

  public record LessonView(Long id, Integer idx, String lessonType, Long scenarioId,
                           String titleZh, int minutes, String status) {
  }

  public record CourseDetail(Long id, String track, String ageBand, String cefr, String examTag,
                             int months, String titleZh, String titleEn, String description,
                             List<LessonView> lessons, Long currentLessonId, int doneCount,
                             int totalCount) {
  }

  public record CompleteResult(boolean newlyDone, Long lessonId, int doneCount, int totalCount,
                               boolean courseFinished) {
  }
}
