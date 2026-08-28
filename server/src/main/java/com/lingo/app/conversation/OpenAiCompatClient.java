package com.lingo.app.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingo.app.common.ApiException;
import com.lingo.app.common.LingoProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容协议客户端（DeepSeek / GLM / Qwen / OpenAI 均适用）。
 * 流式用 JDK HttpClient + BodyHandlers.ofLines 解析 SSE，无需额外依赖。
 */
@Slf4j
@Component
public class OpenAiCompatClient implements LlmClient {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

  private final LingoProperties props;
  private final ObjectMapper objectMapper;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(CONNECT_TIMEOUT)
      .build();

  public OpenAiCompatClient(LingoProperties props, ObjectMapper objectMapper) {
    this.props = props;
    this.objectMapper = objectMapper;
  }

  @Override
  public String streamReply(ChatContext ctx, Consumer<String> onDelta) {
    List<Map<String, String>> messages = buildMessages(ctx, false);
    StringBuilder full = new StringBuilder();
    streamRequest(messages, chunk -> {
      full.append(chunk);
      onDelta.accept(chunk);
    });
    return full.toString();
  }

  @Override
  public LlmClient.LlmReply feedback(ChatContext ctx, String assistantReply) {
    String system = """
        你是英语口语教练。根据对话历史和你的回复，输出 JSON（不要 markdown 代码块）：
        {"betterWay": "针对学员最后一句，给一个更地道自然的英文说法（无则空字符串）",
         "grammarFix": {"original": "...", "corrected": "...", "explain": "中文解释"} 或 null,
         "vocabHints": [{"word": "...", "meaningZh": "中文释义"}] （0-2 个，取自你的回复中的实用词）}
        学员水平：%s""".formatted(ctx.cefr() == null ? "A2" : ctx.cefr());
    String user = "学员最后一句：%s\n你的回复：%s".formatted(ctx.userText(), assistantReply);
    String json = complete(List.of(Map.of("role", "system", "content", system),
        Map.of("role", "user", "content", user)), 0.3);
    JsonNode node = parseJsonSafely(json);
    return new LlmClient.LlmReply(
        assistantReply,
        node.path("betterWay").asText(""),
        readGrammarFix(node.get("grammarFix")),
        readVocabHints(node.get("vocabHints")));
  }

  @Override
  public LlmClient.Recap recap(ChatContext ctx) {
    String system = """
        你是英语口语教练，用中文复盘学员刚才的英语对话练习。输出 JSON（不要 markdown 代码块）：
        {"summary": "一句话总结练习了什么、表现如何",
         "strengths": ["优点1", "优点2", "优点3"],
         "suggestions": ["建议1", "建议2"]}""";
    StringBuilder transcript = new StringBuilder();
    for (ChatContext.HistoryMsg m : ctx.history()) {
      transcript.append(m.role().equals("user") ? "学员: " : "AI: ").append(m.content()).append("\n");
    }
    String json = complete(List.of(Map.of("role", "system", "content", system),
        Map.of("role", "user", "content", "场景：%s\n对话记录：\n%s".formatted(ctx.topic(), transcript))), 0.4);
    JsonNode node = parseJsonSafely(json);
    return new LlmClient.Recap(
        node.path("summary").asText("完成了一次对话练习"),
        readStrings(node.get("strengths")),
        readStrings(node.get("suggestions")));
  }

  private List<Map<String, String>> buildMessages(ChatContext ctx, boolean jsonMode) {
    List<Map<String, String>> messages = new ArrayList<>();
    String system = """
        You are role-playing in an English speaking practice session.
        Scene: %s
        Your role: %s
        The learner's level is %s. Reply in natural spoken English, at most 2 short sentences,
        and always end with a short question or prompt to keep the conversation going.
        Do not switch to Chinese. Do not teach inside the reply.%s"""
        .formatted(ctx.topic(), ctx.roleSetting(), ctx.cefr() == null ? "A2" : ctx.cefr(),
            jsonMode ? "" : "");
    messages.add(Map.of("role", "system", "content", system));
    for (ChatContext.HistoryMsg m : ctx.history()) {
      messages.add(Map.of(m.role().equals("user") ? "user" : "assistant", m.content()));
    }
    if (ctx.userText() != null && !ctx.userText().isBlank()) {
      messages.add(Map.of("user", ctx.userText()));
    }
    return messages;
  }

  /** 一次性补全（非流式），返回 choices[0].message.content */
  public String complete(List<Map<String, String>> messages, double temperature) {
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", props.getLlm().getModel());
      body.put("messages", messages);
      body.put("temperature", temperature);
      body.put("stream", false);

      HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint()))
          .timeout(REQUEST_TIMEOUT)
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + props.getLlm().getApiKey())
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();
      HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() != 200) {
        log.error("LLM complete failed {}: {}", resp.statusCode(), resp.body());
        throw ApiException.badRequest("AI 服务暂时不可用，请稍后重试");
      }
      JsonNode node = objectMapper.readTree(resp.body());
      return node.path("choices").path(0).path("message").path("content").asText("");
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("LLM complete error", e);
      throw ApiException.badRequest("AI 服务暂时不可用，请稍后重试");
    }
  }

  private void streamRequest(List<Map<String, String>> messages, Consumer<String> onDelta) {
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", props.getLlm().getModel());
      body.put("messages", messages);
      body.put("temperature", props.getLlm().getTemperature() == null ? 0.7 : props.getLlm().getTemperature());
      body.put("stream", true);

      HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint()))
          .timeout(REQUEST_TIMEOUT)
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + props.getLlm().getApiKey())
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();
      HttpResponse<java.util.stream.Stream<String>> resp =
          http.send(request, HttpResponse.BodyHandlers.ofLines());
      if (resp.statusCode() != 200) {
        throw ApiException.badRequest("AI 服务暂时不可用，请稍后重试");
      }
      resp.body().forEach(line -> {
        if (line == null || !line.startsWith("data:")) {
          return;
        }
        String payload = line.substring(5).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
          return;
        }
        try {
          JsonNode node = objectMapper.readTree(payload);
          String delta = node.path("choices").path(0).path("delta").path("content").asText("");
          if (!delta.isEmpty()) {
            onDelta.accept(delta);
          }
        } catch (Exception e) {
          log.warn("skip bad SSE chunk: {}", payload);
        }
      });
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("LLM stream error", e);
      throw ApiException.badRequest("AI 服务暂时不可用，请稍后重试");
    }
  }

  private String endpoint() {
    String base = props.getLlm().getBaseUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + "/chat/completions";
  }

  private JsonNode parseJsonSafely(String raw) {
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
      log.warn("LLM JSON parse failed: {}", raw);
      return objectMapper.createObjectNode();
    }
  }

  private LlmClient.GrammarFix readGrammarFix(JsonNode node) {
    if (node == null || node.isNull() || !node.has("corrected")) {
      return null;
    }
    return new LlmClient.GrammarFix(
        node.path("original").asText(""), node.path("corrected").asText(""),
        node.path("explain").asText(""));
  }

  private List<LlmClient.VocabHint> readVocabHints(JsonNode node) {
    List<LlmClient.VocabHint> hints = new ArrayList<>();
    if (node != null && node.isArray()) {
      node.forEach(n -> hints.add(new LlmClient.VocabHint(
          n.path("word").asText(""), n.path("meaningZh").asText(""))));
    }
    return hints;
  }

  private List<String> readStrings(JsonNode node) {
    List<String> list = new ArrayList<>();
    if (node != null && node.isArray()) {
      node.forEach(n -> list.add(n.asText()));
    }
    return list;
  }
}
