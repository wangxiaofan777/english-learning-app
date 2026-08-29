package com.lingo.app.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingo.app.common.ApiException;
import com.lingo.app.common.LingoProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM 接入层：统一经 LangChain4j（OpenAI 兼容协议，DeepSeek / GLM / Qwen / OpenAI 均适用），
 * 不直接拼 HTTP 请求。流式用 OpenAiStreamingChatModel，一次性补全用 OpenAiChatModel，
 * 不同温度的模型按需懒加载并缓存；未配置密钥时由 LlmConfig 切换到 MockLlmClient。
 */
@Slf4j
@Component
public class LangChainLlmClient implements LlmClient {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

  private final LingoProperties props;
  private final ObjectMapper objectMapper;
  /** 同一温度复用同一个模型实例 */
  private final Map<Double, ChatModel> completeModels = new ConcurrentHashMap<>();
  private volatile StreamingChatModel streamModel;

  public LangChainLlmClient(LingoProperties props, ObjectMapper objectMapper) {
    this.props = props;
    this.objectMapper = objectMapper;
  }

  @Override
  public String streamReply(ChatContext ctx, Consumer<String> onDelta) {
    StringBuilder full = new StringBuilder();
    streamRequest(buildMessages(ctx), chunk -> {
      full.append(chunk);
      onDelta.accept(chunk);
    });
    return full.toString();
  }

  @Override
  public LlmClient.LlmReply feedback(ChatContext ctx, String assistantReply) {
    if (ctx.companion()) {
      return companionFeedback(ctx, assistantReply);
    }
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

  /**
   * 陪练模式的反馈：像朋友发来的悄悄话，只在「有值得说的」时候才出现，
   * 一轮最多一条提示，没有就整条留空（前端不渲染）。
   */
  private LlmClient.LlmReply companionFeedback(ChatContext ctx, String assistantReply) {
    String system = """
        You are %s, an English practice companion. Look at the learner's LAST sentence only.
        If it has a notable grammar mistake or a clearly more idiomatic upgrade, output JSON:
        {"betterWay": "更地道的英文说法", "grammarFix": {"original": "...", "corrected": "...",
         "explain": "一句中文解释"} or null, "vocabHints": []}
        If their sentence is already fine, output {"betterWay": "", "grammarFix": null,
        "vocabHints": []}. Be sparing — empty is the common case. Level: %s"""
        .formatted(ctx.companionName(), ctx.cefr() == null ? "A2" : ctx.cefr());
    String user = "学员最后一句：%s".formatted(ctx.userText() == null ? "" : ctx.userText());
    try {
      String json = complete(List.of(Map.of("role", "system", "content", system),
          Map.of("role", "user", "content", user)), 0.2);
      JsonNode node = parseJsonSafely(json);
      String betterWay = node.path("betterWay").asText("");
      LlmClient.GrammarFix fix = readGrammarFix(node.get("grammarFix"));
      if (betterWay.isBlank() && fix == null) {
        return new LlmClient.LlmReply(assistantReply, "", null, List.of());
      }
      return new LlmClient.LlmReply(assistantReply, betterWay, fix, List.of());
    } catch (Exception e) {
      return new LlmClient.LlmReply(assistantReply, "", null, List.of());
    }
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

  @Override
  public String companionGreeting(String companionName, String fallback, String cefr,
                                  List<String> memories) {
    String memoryLine = memories.isEmpty() ? "(first time meeting them — greet them like a "
        + "friend would, keep it light; do NOT invent past events or details you were never told)"
        : "Things you remember about them from previous chats:\n- "
            + String.join("\n- ", memories)
            + "\nReference ONE of these casually; do NOT invent details beyond these facts.";
    String system = """
        You are %s, an English speaking practice companion. Write ONE short opening line
        (1-2 sentences, spoken English) to greet the learner as they open the chat.
        The line is spoken BY you (%s) TO the learner — address them directly
        (use "you"/"your"), never speak as if you were the learner, never sign your name.
        Sound like a real friend: warm, specific, no corporate tone, no emoji.
        %s
        If you remember something, reference ONE thing casually and ask about it
        (like "how did the interview go?"). Learner level: %s.
        Output the greeting line only.""".formatted(companionName, companionName, memoryLine,
        cefr == null ? "A2" : cefr);
    try {
      String json = complete(List.of(
          Map.of("role", "system", "content", system),
          Map.of("role", "user", "content", "Write the greeting now.")), 0.9);
      return json.trim().isEmpty() ? fallback : json.trim();
    } catch (Exception e) {
      return fallback;
    }
  }

  @Override
  public List<String> extractFacts(String userText, List<String> knownFacts) {
    String system = """
        Extract durable facts about the USER from what they said, as short third-person
        English sentences (e.g. "User's name is Amy", "User works as a nurse",
        "User is preparing for IELTS", "User has a trip to Japan next month").
        Only durable things (identity, job, family, hobbies, goals, plans) — not today's
        small talk. Skip anything already in the known list. Output a JSON array of
        strings, no markdown. Output [] when there is nothing durable.""";
    String user = "Known facts: %s\nUser said: %s".formatted(knownFacts, userText);
    try {
      String json = complete(List.of(
          Map.of("role", "system", "content", system),
          Map.of("role", "user", "content", user)), 0.2);
      JsonNode node = parseJsonSafely(json);
      List<String> facts = new ArrayList<>();
      if (node.isArray()) {
        node.forEach(n -> facts.add(n.asText()));
      }
      return facts;
    } catch (Exception e) {
      return List.of();
    }
  }

  /** 一次性补全（供本类与内容生成流水线复用），返回 assistant 文本 */
  public String complete(List<Map<String, String>> messages, double temperature) {
    try {
      List<ChatMessage> chatMessages = new ArrayList<>();
      for (Map<String, String> m : messages) {
        String role = m.getOrDefault("role", "user");
        String content = m.getOrDefault("content", "");
        switch (role) {
          case "system" -> chatMessages.add(SystemMessage.from(content));
          case "assistant" -> chatMessages.add(AiMessage.from(content));
          default -> chatMessages.add(UserMessage.from(content));
        }
      }
      ChatResponse response = completeModels
          .computeIfAbsent(temperature, this::buildChatModel)
          .chat(chatMessages);
      String text = response.aiMessage().text();
      return text == null ? "" : text;
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("LLM complete error", e);
      throw ApiException.badRequest("AI 服务暂时不可用，请稍后重试");
    }
  }

  // ---------- internals ----------

  private ChatModel buildChatModel(double temperature) {
    return OpenAiChatModel.builder()
        .baseUrl(baseUrl())
        .apiKey(props.getLlm().getApiKey())
        .modelName(props.getLlm().getModel())
        .temperature(temperature)
        .timeout(REQUEST_TIMEOUT)
        .build();
  }

  private StreamingChatModel buildStreamModel() {
    return OpenAiStreamingChatModel.builder()
        .baseUrl(baseUrl())
        .apiKey(props.getLlm().getApiKey())
        .modelName(props.getLlm().getModel())
        .temperature(props.getLlm().getTemperature() == null ? 0.7 : props.getLlm().getTemperature())
        .timeout(REQUEST_TIMEOUT)
        .build();
  }

  private StreamingChatModel streamModel() {
    if (streamModel == null) {
      synchronized (this) {
        if (streamModel == null) {
          streamModel = buildStreamModel();
        }
      }
    }
    return streamModel;
  }

  private void streamRequest(List<ChatMessage> messages, Consumer<String> onDelta) {
    try {
      AtomicReference<Throwable> failure = new AtomicReference<>();
      streamModel().chat(messages, new StreamingChatResponseHandler() {
        @Override
        public void onPartialResponse(String token) {
          onDelta.accept(token);
        }

        @Override
        public void onCompleteResponse(ChatResponse response) {
          // 完整结果由调用方累积的 delta 拼出，无需处理
        }

        @Override
        public void onError(Throwable error) {
          failure.set(error);
        }
      });
      Throwable error = failure.get();
      if (error != null) {
        log.error("LLM stream error", error);
        throw ApiException.badRequest("AI 服务暂时不可用，请稍后重试");
      }
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("LLM stream error", e);
      throw ApiException.badRequest("AI 服务暂时不可用，请稍后重试");
    }
  }

  private List<ChatMessage> buildMessages(ChatContext ctx) {
    List<ChatMessage> messages = new ArrayList<>();
    messages.add(SystemMessage.from(
        ctx.companion() ? companionSystemPrompt(ctx) : sceneSystemPrompt(ctx)));
    for (ChatContext.HistoryMsg m : ctx.history()) {
      if ("user".equals(m.role())) {
        messages.add(UserMessage.from(m.content()));
      } else {
        messages.add(AiMessage.from(m.content()));
      }
    }
    if (ctx.userText() != null && !ctx.userText().isBlank()) {
      messages.add(UserMessage.from(ctx.userText()));
    }
    return messages;
  }

  /** 场景角色扮演（原有行为）：按脚本人设推进，回复短、总以提问收尾 */
  private String sceneSystemPrompt(ChatContext ctx) {
    return """
        You are role-playing in an English speaking practice session.
        Scene: %s
        Your role: %s
        The learner's level is %s. Reply in natural spoken English, at most 2 short sentences,
        and always end with a short question or prompt to keep the conversation going.
        Do not switch to Chinese. Do not teach inside the reply."""
        .formatted(ctx.topic(), ctx.roleSetting(), ctx.cefr() == null ? "A2" : ctx.cefr());
  }

  /**
   * AI 陪练（像真人）：先反应再回应、长短句混合、不必每句都提问、
   * 偶尔把自己的经历带进来、偶尔自然地复述纠错（recast）、能引用长期记忆。
   */
  private String companionSystemPrompt(ChatContext ctx) {
    StringBuilder p = new StringBuilder();
    p.append("""
        You are %s, the learner's English speaking companion — a real-feeling person, not an
        assistant. Character: %s
        The learner's English level is %s. Speak natural spoken English at their level.

        How you talk like a real person:
        - REACT first ("Oh really?", "No way!", "Haha nice", "That makes sense"), then respond.
        - 1-3 short sentences. Vary the length and rhythm between messages; never lecture.
        - Do NOT end every message with a question — real people don't. Roughly half.
        - Occasionally (when it fits) share a tiny bit of your own life or opinion, one line max.
        - Weave in the learner's name (%s) only now and then, never every message.
        - No emoji, no lists, no "As an AI", no encouragement-padding like "Great job practicing!".
        - If the learner makes a small language mistake, sometimes recast it naturally inside
          your reply (repeat their idea with the fix), never point it out or explain grammar.
        - No Chinese in your replies.
        """.formatted(ctx.companionName(), ctx.roleSetting(),
        ctx.cefr() == null ? "A2" : ctx.cefr(),
        ctx.userName() == null ? "them" : ctx.userName()));
    if (ctx.memories() != null && !ctx.memories().isEmpty()) {
      p.append("Things you remember about them from previous chats:\n");
      for (String m : ctx.memories()) {
        p.append("- ").append(m).append("\n");
      }
      p.append("""
        Use this memory only when it naturally fits — never dump it, never say "I remember
        you told me" more than once in a while, and don't repeat what they just said.
        """);
    }
    return p.toString();
  }

  private String baseUrl() {
    String base = props.getLlm().getBaseUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base;
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
