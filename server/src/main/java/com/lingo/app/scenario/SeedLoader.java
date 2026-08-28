package com.lingo.app.scenario;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingo.app.course.CourseEntity;
import com.lingo.app.course.CourseLessonEntity;
import com.lingo.app.course.mapper.CourseLessonMapper;
import com.lingo.app.course.mapper.CourseMapper;
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
  private final ObjectMapper objectMapper;

  @Override
  public void run(String... args) throws Exception {
    if (scenarioMapper.selectCount(null) == 0) {
      seedScenarios();
    } else {
      log.info("scenarios already seeded, skip");
    }
    if (courseMapper.selectCount(null) == 0) {
      seedCourses();
    } else {
      log.info("courses already seeded, skip");
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
    log.info("seeded {} scenarios", seed.getScenarios().size());
  }

  /** 课程种入：课时按主题关联到已存在的场景 */
  private void seedCourses() throws Exception {
    CourseSeed seed = objectMapper.readValue(
        new ClassPathResource("seed/courses.json").getInputStream(), CourseSeed.class);
    Map<String, ScenarioEntity> byTitle = scenarioMapper.selectList(null).stream()
        .collect(Collectors.toMap(ScenarioEntity::getTitleZh, Function.identity(), (a, b) -> a));
    int sortNo = 0;
    int lessonCount = 0;
    for (SeedCourse c : seed.getCourses()) {
      CourseEntity entity = new CourseEntity();
      entity.setTrack(c.getTrack());
      entity.setAgeBand(c.getAgeBand());
      entity.setCefr(c.getCefr());
      entity.setTitleZh(c.getTitleZh());
      entity.setTitleEn(c.getTitleEn());
      entity.setDescription(c.getDescription());
      entity.setSortNo(sortNo++);
      courseMapper.insert(entity);

      int idx = 0;
      for (SeedLesson l : c.getLessons()) {
        ScenarioEntity scenario = byTitle.get(l.getTopic());
        if (scenario == null) {
          log.warn("course lesson topic not found, skipped: {}", l.getTopic());
          continue;
        }
        CourseLessonEntity lesson = new CourseLessonEntity();
        lesson.setCourseId(entity.getId());
        lesson.setIdx(idx++);
        lesson.setLessonType(l.getType());
        lesson.setScenarioId(scenario.getId());
        lesson.setTitleZh(l.getTitleZh());
        lesson.setMinutes(10);
        courseLessonMapper.insert(lesson);
        lessonCount++;
      }
    }
    log.info("seeded {} courses with {} lessons", seed.getCourses().size(), lessonCount);
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
  public static class CourseSeed {
    private List<SeedCourse> courses = new java.util.ArrayList<>();
  }

  @Data
  public static class SeedCourse {
    private String track;
    private String ageBand;
    private String cefr;
    private String titleZh;
    private String titleEn;
    private String description;
    private List<SeedLesson> lessons = new java.util.ArrayList<>();
  }

  @Data
  public static class SeedLesson {
    private String type;
    private String topic;
    private String titleZh;
  }
}
