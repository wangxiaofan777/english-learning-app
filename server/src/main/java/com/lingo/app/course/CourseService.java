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
   * 按登记信息自动制定课程：优先「目标轨道 + 年龄段」匹配，其次目标轨道，最后任一课程。
   */
  public void autoEnroll(Long userId, String track, String ageBand, String cefr) {
    if (current(userId) != null) {
      return; // 已有进行中的课程，不打乱用户节奏
    }
    List<CourseEntity> all = courseMapper.selectList(
        new LambdaQueryWrapper<CourseEntity>().orderByAsc(CourseEntity::getSortNo));
    if (all.isEmpty()) {
      return;
    }
    CourseEntity pick = all.stream()
        .filter(c -> c.getTrack().equals(track) && ageBand != null && ageBand.equals(c.getAgeBand()))
        .findFirst()
        .orElseGet(() -> all.stream()
            .filter(c -> c.getTrack().equals(track))
            .findFirst()
            .orElse(all.get(0)));
    enroll(userId, pick.getId());
    log.info("auto-enrolled user {} into course {} (track={}, age={}, cefr={})",
        userId, pick.getId(), track, ageBand, cefr);
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
    CourseLessonEntity lesson = lessonsOf(current.id()).stream()
        .filter(l -> l.getLessonType().equals(lessonType)
            && scenarioId != null && scenarioId.equals(l.getScenarioId()))
        .findFirst()
        .orElse(null);
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
    return new CourseDetail(c.getId(), c.getTrack(), c.getAgeBand(), c.getCefr(), c.getTitleZh(),
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

  public record CourseCard(Long id, String track, String ageBand, String cefr, String titleZh,
                           String titleEn, String description, int lessonCount, int doneCount,
                           boolean enrolled) {
  }

  public record LessonView(Long id, Integer idx, String lessonType, Long scenarioId,
                           String titleZh, int minutes, String status) {
  }

  public record CourseDetail(Long id, String track, String ageBand, String cefr, String titleZh,
                             String titleEn, String description, List<LessonView> lessons,
                             Long currentLessonId, int doneCount, int totalCount) {
  }

  public record CompleteResult(boolean newlyDone, Long lessonId, int doneCount, int totalCount,
                               boolean courseFinished) {
  }
}
