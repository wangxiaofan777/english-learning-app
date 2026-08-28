package com.lingo.app.placement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingo.app.common.ApiException;
import com.lingo.app.user.UserProfileEntity;
import com.lingo.app.user.mapper.UserProfileMapper;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlacementService {

  private final UserProfileMapper profileMapper;
  private final ObjectMapper objectMapper;
  private List<PlacementQuestion> questions = List.of();

  @PostConstruct
  void load() {
    try {
      PlacementSeed seed = objectMapper.readValue(
          new ClassPathResource("seed/placement.json").getInputStream(),
          PlacementSeed.class);
      this.questions = seed.getQuestions();
    } catch (Exception e) {
      throw new IllegalStateException("加载测评题库失败", e);
    }
  }

  public List<PlacementQuestion> questions() {
    return questions.stream().map(PlacementQuestion::copyWithoutAnswer).toList();
  }

  public PlacementResult submit(Map<String, String> answers, String spokenText) {
    int score = 0;
    List<String> weakTags = new ArrayList<>();
    for (PlacementQuestion q : questions) {
      String given = answers.get(q.getId());
      if (given != null && given.equalsIgnoreCase(q.getAnswer())) {
        score++;
      } else {
        weakTags.add(q.getCefr() + " " + (q.getType().equals("vocab") ? "词汇" : "句型"));
      }
    }
    String cefr = levelFor(score);
    if (weakTags.isEmpty()) {
      weakTags.add("C1 巩固");
    }

    Long userId = com.lingo.app.common.UserContext.get();
    UserProfileEntity profile = profileMapper.selectOne(
        new LambdaQueryWrapper<UserProfileEntity>().eq(UserProfileEntity::getUserId, userId));
    profile.setCefrLevel(cefr);
    profile.setOnboardingStep("done");
    profile.setWeakTags(toJson(weakTags.stream().distinct().limit(4).toList()));
    profileMapper.updateById(profile);

    String comment = "口语自述暂未评估，可先按「" + cefr + "」开始，系统会随练习自动校准。";
    return new PlacementResult(score, questions.size(), cefr, weakTags, comment);
  }

  /** score → CEFR 映射（静态便于单测） */
  public static String levelFor(int score) {
    if (score <= 2) {
      return "A1";
    }
    if (score <= 4) {
      return "A2";
    }
    if (score <= 6) {
      return "B1";
    }
    if (score <= 8) {
      return "B2";
    }
    return "C1";
  }

  private String toJson(List<String> tags) {
    try {
      return objectMapper.writeValueAsString(tags);
    } catch (Exception e) {
      return "[]";
    }
  }

  @Data
  public static class PlacementSeed {
    private List<PlacementQuestion> questions = new ArrayList<>();
  }

  @Data
  public static class PlacementQuestion {
    private String id;
    private String type;
    private String cefr;
    private String stem;
    private List<Option> options = new ArrayList<>();
    private String answer;

    PlacementQuestion copyWithoutAnswer() {
      PlacementQuestion copy = new PlacementQuestion();
      copy.setId(id);
      copy.setType(type);
      copy.setCefr(cefr);
      copy.setStem(stem);
      copy.setOptions(options);
      return copy;
    }
  }

  @Data
  public static class Option {
    private String key;
    private String text;
  }

  public record PlacementResult(int score, int total, String cefr,
                                List<String> weakTags, String spokenComment) {
  }

  @SuppressWarnings("unused")
  private static final TypeReference<List<String>> TAG_LIST = new TypeReference<>() {
  };
}
