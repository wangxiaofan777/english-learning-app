package com.lingo.app.scenario;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingo.app.course.CourseEntity;
import com.lingo.app.course.CourseLessonEntity;
import com.lingo.app.course.mapper.CourseLessonMapper;
import com.lingo.app.course.mapper.CourseMapper;
import com.lingo.app.course.mapper.LessonProgressMapper;
import com.lingo.app.course.mapper.UserCourseMapper;
import com.lingo.app.scenario.mapper.ScenarioLineMapper;
import com.lingo.app.scenario.mapper.ScenarioMapper;
import com.lingo.app.scenario.mapper.ScenarioVocabMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeedLoader implements CommandLineRunner {

  private final ScenarioMapper scenarioMapper;
  private final ScenarioLineMapper lineMapper;
  private final ScenarioVocabMapper vocabMapper;
  private final CourseMapper courseMapper;
  private final CourseLessonMapper courseLessonMapper;
  private final UserCourseMapper userCourseMapper;
  private final LessonProgressMapper lessonProgressMapper;
  private final GenerationService generationService;
  private final ObjectMapper objectMapper;

  @Override
  public void run(String... args) throws Exception {
    if (scenarioMapper.selectCount(null) == 0) {
      seedScenarios();
    } else {
      log.info("scenarios already seeded, skip");
    }
    // 课程目录（6 方向 × 3 周期 = 18 门）；数量不足视为旧版种子，重建目录
    if (courseMapper.selectCount(null) < 18) {
      reseedCourseCatalog();
    } else {
      log.info("course catalog already seeded, skip");
    }
  }

  private void seedScenarios() throws Exception {
    ScenarioSeed seed = objectMapper.readValue(
        new ClassPathResource("seed/scenarios.json").getInputStream(), ScenarioSeed.class);
    int sortNo = 0;
    for (SeedScenario s : seed.getScenarios()) {
      ScenarioEntity entity = new ScenarioEntity();
      entity.setTrack(s.getTrack());
      entity.setTopic(s.getTopic());
      entity.setTitleZh(s.getTitleZh());
      entity.setTitleEn(s.getTitleEn());
      entity.setCefr(s.getCefr());
      entity.setRoleSetting(s.getRoleSetting());
      entity.setIntroZh(s.getIntroZh());
      entity.setSource("seed");
      entity.setStatus("published");
      entity.setSortNo(sortNo++);
      scenarioMapper.insert(entity);

      int idx = 0;
      for (SeedLine l : s.getLines()) {
        ScenarioLineEntity line = new ScenarioLineEntity();
        line.setScenarioId(entity.getId());
        line.setIdx(idx++);
        line.setSpeaker(l.getSpeaker());
        line.setEn(l.getEn());
        line.setZh(l.getZh());
        lineMapper.insert(line);
      }
      for (SeedVocab v : s.getVocab()) {
        ScenarioVocabEntity vocab = new ScenarioVocabEntity();
        vocab.setScenarioId(entity.getId());
        vocab.setWord(v.getWord());
        vocab.setPhonetic(v.getPhonetic());
        vocab.setMeaningZh(v.getMeaningZh());
        vocab.setExampleEn(v.getExampleEn());
        vocab.setExampleZh(v.getExampleZh());
        vocabMapper.insert(vocab);
      }
    }
    // 自由聊天场景（不进大厅列表，由「自由聊天」入口直达）
    if (scenarioMapper.selectCount(new LambdaQueryWrapper<ScenarioEntity>()
        .eq(ScenarioEntity::getTrack, "free")) == 0) {
      ScenarioEntity free = new ScenarioEntity();
      free.setTrack("free");
      free.setTopic("自由聊天");
      free.setTitleZh("自由聊天");
      free.setTitleEn("Free Talk");
      free.setCefr("A2");
      free.setRoleSetting("FREE_TALK: You are a friendly English chat buddy. No script — "
          + "just natural conversation about the learner's day, hobbies and opinions, "
          + "always ending with a follow-up question.");
      free.setIntroZh("不限场景，想到什么聊什么");
      free.setSource("seed");
      free.setStatus("published");
      free.setSortNo(-1);
      scenarioMapper.insert(free);
      log.info("seeded free-talk scenario");
    }
  }

  /** 重建课程目录：清掉旧课程/报名/进度，按大纲重新生成 */
  private void reseedCourseCatalog() throws Exception {
    courseLessonMapper.delete(null);
    courseMapper.delete(null);
    userCourseMapper.delete(null);
    lessonProgressMapper.delete(null);
    seedCourses();
  }

  /**
   * 课程目录：按大纲（6 学段方向 × 3/6/12 个月周期）生成课程与课时。
   * 课时场景按主题复用手写场景，缺失的用模板生成（零 LLM 调用，建课秒级完成）。
   */
  private void seedCourses() throws Exception {
    Syllabus syllabus = objectMapper.readValue(
        new ClassPathResource("seed/syllabus.json").getInputStream(), Syllabus.class);
    Map<String, ScenarioEntity> byTitle = scenarioMapper.selectList(null).stream()
        .collect(Collectors.toMap(ScenarioEntity::getTitleZh, Function.identity(), (a, b) -> a));

    int sortNo = 0;
    int courseCount = 0;
    int lessonCount = 0;
    for (SyllabusDirection d : syllabus.getDirections()) {
      // 主题 → 场景
      List<ScenarioEntity> topicScenarios = new java.util.ArrayList<>();
      for (String topic : d.getTopics()) {
        ScenarioEntity existing = byTitle.get(topic);
        if (existing == null) {
          generationService.generateTemplate("course", topic, d.getCefr());
          existing = scenarioMapper.selectOne(new LambdaQueryWrapper<ScenarioEntity>()
              .eq(ScenarioEntity::getTitleZh, topic).last("limit 1"));
        }
        topicScenarios.add(existing);
      }

      // 周期计划：3 个月=12 话题×3 课时；6 个月=24×3；12 个月=24×4（含单元复习课）
      int[][] plans = {{3, 12, 3}, {6, 24, 3}, {12, 24, 4}};
      for (int[] plan : plans) {
        int months = plan[0];
        int topicCount = plan[1];
        boolean withReview = months == 12;
        List<ScenarioEntity> picked = topicScenarios.subList(0, topicCount);
        int totalLessons = picked.size() * (withReview ? 4 : 3);
        int weeks = months * 4;
        int lessonsPerWeek = Math.round((float) totalLessons / weeks);

        CourseEntity course = new CourseEntity();
        course.setTrack(d.getCode());
        course.setAgeBand(d.getAgeBand());
        course.setCefr(d.getCefr());
        course.setExamTag(d.getExam());
        course.setMonths(months);
        course.setTitleZh(d.getTitle() + " · " + months + " 个月");
        course.setTitleEn(d.getTitle() + " (" + months + "-month)");
        course.setDescription("共 " + totalLessons + " 课时 · " + weeks + " 周 · 每周约 "
            + lessonsPerWeek + " 课时 · 对标" + d.getExam()
            + "。话题由易到难，对话、精听、跟读交替编排"
            + (withReview ? "，每单元附复习课。" : "。"));
        course.setSortNo(sortNo++);
        courseMapper.insert(course);
        courseCount++;

        int idx = 0;
        for (ScenarioEntity scenario : picked) {
          String[] cycle = withReview
              ? new String[]{"dialog", "listening", "shadowing", "review"}
              : new String[]{"dialog", "listening", "shadowing"};
          for (String type : cycle) {
            CourseLessonEntity lesson = new CourseLessonEntity();
            lesson.setCourseId(course.getId());
            lesson.setIdx(idx);
            lesson.setLessonType(type);
            lesson.setScenarioId("review".equals(type) ? null : scenario.getId());
            lesson.setTitleZh("第 " + (idx + 1) + " 课 · " + typeLabel(type)
                + ("review".equals(type) ? "" : " · " + scenario.getTitleZh()));
            lesson.setMinutes(10);
            courseLessonMapper.insert(lesson);
            idx++;
            lessonCount++;
          }
        }
      }
    }
    log.info("seeded course catalog: {} courses, {} lessons", courseCount, lessonCount);
  }

  private String typeLabel(String type) {
    return switch (type) {
      case "listening" -> "听力精听";
      case "shadowing" -> "跟读评分";
      case "review" -> "单元复习";
      default -> "对话实战";
    };
  }

  @Data
  public static class ScenarioSeed {
    private List<SeedScenario> scenarios = new java.util.ArrayList<>();
  }

  @Data
  public static class SeedScenario {
    private String track;
    private String topic;
    private String titleZh;
    private String titleEn;
    private String cefr;
    private String roleSetting;
    private String introZh;
    private List<SeedLine> lines = new java.util.ArrayList<>();
    private List<SeedVocab> vocab = new java.util.ArrayList<>();
  }

  @Data
  public static class SeedLine {
    private String speaker;
    private String en;
    private String zh;
  }

  @Data
  public static class SeedVocab {
    private String word;
    private String phonetic;
    private String meaningZh;
    private String exampleEn;
    private String exampleZh;
  }

  @Data
  public static class Syllabus {
    private List<SyllabusDirection> directions = new java.util.ArrayList<>();
  }

  @Data
  public static class SyllabusDirection {
    private String code;
    private String stage;
    private String exam;
    private String ageBand;
    private String cefr;
    private String title;
    private List<String> topics = new java.util.ArrayList<>();
  }
}
