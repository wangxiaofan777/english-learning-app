package com.lingo.app.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingo.app.common.ApiException;
import com.lingo.app.common.LingoProperties;
import com.lingo.app.conversation.OpenAiCompatClient;
import com.lingo.app.scenario.mapper.ScenarioLineMapper;
import com.lingo.app.scenario.mapper.ScenarioMapper;
import com.lingo.app.scenario.mapper.ScenarioVocabMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 场景内容流水线：AI 批量生成 → schema 校验 → 入库。
 * 未配置 LLM 时用模板兜底，保证功能可演示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationService {

  private static final String SYSTEM_PROMPT = """
      你是英语教学内容设计师。根据给定的学习轨道、主题和 CEFR 等级，设计一个口语练习场景。
      只输出 JSON（不要 markdown 代码块），结构如下：
      {"titleZh":"场景中文名","titleEn":"Scene title in English","topic":"主题",
       "roleSetting":"英文的 AI 角色设定（它扮演谁、场景在哪、语气如何）",
       "introZh":"一句话中文介绍，面向学习者",
       "lines":[{"speaker":"ai|user","en":"英文台词","zh":"中文翻译"}],
       "vocab":[{"word":"word","phonetic":"/.../","meaningZh":"中文释义","exampleEn":"...","exampleZh":"..."}]}
      要求：
      - lines 共 8 条，ai 和 user 交替，第一条必须是 ai 的开场白，user 台词给出示范说法
      - 难度严格匹配 CEFR 等级，台词口语化、实用
      - vocab 给 4 个本场景的核心词或表达
      """;

  private final ScenarioMapper scenarioMapper;
  private final ScenarioLineMapper lineMapper;
  private final ScenarioVocabMapper vocabMapper;
  private final LingoProperties props;
  private final OpenAiCompatClient llmClient;
  private final ObjectMapper objectMapper;

  public ScenarioCard generate(String track, String topic, String cefr) {
    JsonNode node;
    if (props.llmEnabled()) {
      String user = "学习轨道：%s\n主题：%s\nCEFR 等级：%s".formatted(track, topic, cefr);
      String raw = llmClient.complete(List.of(
          Map.of("role", "system", "content", SYSTEM_PROMPT),
          Map.of("role", "user", "content", user)), 0.8);
      node = parseJson(raw);
    } else {
      node = mockGeneration(track, topic, cefr);
    }
    return persist(node, track, topic, cefr);
  }

  private ScenarioCard persist(JsonNode node, String track, String topic, String cefr) {
    JsonNode lines = node.path("lines");
    JsonNode vocab = node.path("vocab");
    if (!lines.isArray() || lines.size() < 4 || !vocab.isArray() || vocab.isEmpty()) {
      throw ApiException.badRequest("AI 生成内容不完整，请重试");
    }
    ScenarioEntity entity = new ScenarioEntity();
    entity.setTrack(track);
    entity.setTopic(node.path("topic").asText(topic));
    entity.setTitleZh(node.path("titleZh").asText(topic));
    entity.setTitleEn(node.path("titleEn").asText(topic));
    entity.setCefr(cefr);
    entity.setRoleSetting(node.path("roleSetting").asText(
        "You are a friendly conversation partner helping the learner practice."));
    entity.setIntroZh(node.path("introZh").asText(""));
    entity.setSource("ai");
    entity.setStatus("published");
    entity.setSortNo(100);
    scenarioMapper.insert(entity);

    int idx = 0;
    for (JsonNode l : lines) {
      ScenarioLineEntity line = new ScenarioLineEntity();
      line.setScenarioId(entity.getId());
      line.setIdx(idx++);
      line.setSpeaker("user".equalsIgnoreCase(l.path("speaker").asText("ai")) ? "user" : "ai");
      line.setEn(l.path("en").asText());
      line.setZh(l.path("zh").asText());
      lineMapper.insert(line);
    }
    for (JsonNode v : vocab) {
      ScenarioVocabEntity sv = new ScenarioVocabEntity();
      sv.setScenarioId(entity.getId());
      sv.setWord(v.path("word").asText());
      sv.setPhonetic(v.path("phonetic").asText(""));
      sv.setMeaningZh(v.path("meaningZh").asText(""));
      sv.setExampleEn(v.path("exampleEn").asText(""));
      sv.setExampleZh(v.path("exampleZh").asText(""));
      vocabMapper.insert(sv);
    }
    return new ScenarioCard(entity.getId(), entity.getTitleZh(), entity.getTitleEn(),
        idx, vocab.size());
  }

  private JsonNode mockGeneration(String track, String topic, String cefr) {
    Map<String, Object> mock = Map.of(
        "titleZh", topic,
        "titleEn", "Practice: " + topic,
        "topic", topic,
        "roleSetting", "You are a friendly partner helping the learner practice " + topic
            + ". Keep the conversation natural and encouraging.",
        "introZh", "围绕「" + topic + "」的口语练习场景（演示模板生成）",
        "lines", List.of(
            Map.of("speaker", "ai", "en", "Hi! I heard you want to practice " + topic
                + ". What would you like to say first?", "zh", "你好！听说你想练习「" + topic + "」。先说说看？"),
            Map.of("speaker", "user", "en", "Excuse me, could you help me with " + topic + "?",
                "zh", "打扰一下，你能帮我处理「" + topic + "」吗？"),
            Map.of("speaker", "ai", "en", "Of course! Let's take it step by step. What is your main concern?",
                "zh", "当然可以！我们一步步来。你最关心的是什么？"),
            Map.of("speaker", "user", "en", "I'm not sure how to start the conversation.",
                "zh", "我不知道怎么开始对话。"),
            Map.of("speaker", "ai", "en", "No worries. A simple opener works well. Now it's your turn.",
                "zh", "没关系，简单的开场就很有效。现在轮到你试试。"),
            Map.of("speaker", "user", "en", "Excuse me, could you help me with " + topic + "?",
                "zh", "打扰一下，你能帮我处理「" + topic + "」吗？"),
            Map.of("speaker", "ai", "en", "Perfect! That sounds natural. Shall we role-play the whole scene now?",
                "zh", "很棒！听起来很自然。现在我们来完整演练一遍？"),
            Map.of("speaker", "user", "en", "Yes, I'm ready!",
                "zh", "好，我准备好了！")),
        "vocab", List.of(
            Map.of("word", "excuse me", "phonetic", "/ɪkˈskjuːz miː/", "meaningZh", "打扰一下（礼貌开场）",
                "exampleEn", "Excuse me, could you help me?", "exampleZh", "打扰一下，能帮个忙吗？"),
            Map.of("word", "step by step", "phonetic", "/step baɪ step/", "meaningZh", "一步步地",
                "exampleEn", "Let's take it step by step.", "exampleZh", "我们一步一步来。"),
            Map.of("word", "role-play", "phonetic", "/rəʊl pleɪ/", "meaningZh", "角色扮演演练",
                "exampleEn", "Let's role-play the scene.", "exampleZh", "我们来演练一遍这个场景。"),
            Map.of("word", "concern", "phonetic", "/kənˈsɜːn/", "meaningZh", "关心的事；顾虑",
                "exampleEn", "My main concern is the time.", "exampleZh", "我最关心的是时间。")));
    return objectMapper.valueToTree(mock);
  }

  private JsonNode parseJson(String raw) {
    try {
      String text = raw.trim();
      if (text.startsWith("```")) {
        text = text.replaceFirst("^```(json)?", "").replaceFirst("```$", "").trim();
      }
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start >= 0 && end > start) {
        text = text.substring(start, end + 1);
      }
      return objectMapper.readTree(text);
    } catch (Exception e) {
      log.warn("generated scenario JSON parse failed: {}", raw);
      throw ApiException.badRequest("AI 生成内容解析失败，请重试");
    }
  }

  public record ScenarioCard(Long id, String titleZh, String titleEn, int lineCount,
                             int vocabCount) {
  }

  @SuppressWarnings("unused")
  private static List<String> noop() {
    return new ArrayList<>();
  }
}
