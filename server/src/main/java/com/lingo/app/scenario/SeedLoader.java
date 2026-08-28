package com.lingo.app.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingo.app.scenario.mapper.ScenarioLineMapper;
import com.lingo.app.scenario.mapper.ScenarioMapper;
import com.lingo.app.scenario.mapper.ScenarioVocabMapper;
import java.util.List;
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
  private final ObjectMapper objectMapper;

  @Override
  public void run(String... args) throws Exception {
    if (scenarioMapper.selectCount(null) > 0) {
      log.info("scenarios already seeded, skip");
      return;
    }
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
}
