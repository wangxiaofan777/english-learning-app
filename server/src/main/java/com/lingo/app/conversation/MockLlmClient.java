package com.lingo.app.conversation;

import com.lingo.app.scenario.ScenarioVocabEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 无 LLM 密钥时的兜底实现：按场景脚本推进对话 + 模板化反馈，
 * 让全流程在零配置下可完整体验。
 */
@Slf4j
@Component
public class MockLlmClient implements LlmClient {

  private static final long TYPE_DELAY_MS = 25;

  private static final List<String> FREE_TALK_LINES = List.of(
      "That's interesting! What got you into that?",
      "Nice! How often do you do that?",
      "I see. What do you enjoy most about it?",
      "Sounds fun! What happened next?",
      "Good point! Do your friends think the same way?",
      "Wow, really? How did that make you feel?",
      "Haha, classic! What would you do differently next time?",
      "Interesting choice! If you had to pick one favorite, which one?",
      "I love that. What's the plan for the weekend?",
      "Totally get it. So what's keeping you busy these days?");

  /** 陪练模式的兜底闲聊：先反应、再说自己、半数带追问 */
  private static final List<String> CHATTER_LINES = List.of(
      "Oh nice, I didn't expect that! What happened next?",
      "Haha, that's so real. I had a day like that last week.",
      "Hmm, I see what you mean. I'd probably do the same thing.",
      "Wait, seriously? Tell me more!",
      "That sounds fun actually. I might steal that idea for my weekend.",
      "Makes sense to me. Honestly, I think you're onto something.",
      "No way! Good for you.",
      "Ahh okay. Anyway, how's the rest of your day looking?");

  @Override
  public String streamReply(ChatContext ctx, Consumer<String> onDelta) {
    String reply = scriptedReply(ctx);
    for (String word : reply.split("(?<= )")) {
      onDelta.accept(word);
      sleep();
    }
    return reply;
  }

  @Override
  public LlmReply feedback(ChatContext ctx, String assistantReply) {
    String userText = ctx.userText() == null ? "" : ctx.userText().trim();
    LlmClient.GrammarFix fix = grammarFix(userText);
    // 陪练模式只在发现明确错误时小声提醒一次，其余时候保持朋友感
    if (ctx.companion()) {
      return new LlmReply(assistantReply, null, fix, List.of());
    }
    String betterWay = null;
    if (userText.toLowerCase().contains("i want")) {
      betterWay = "服务场景里说 " + userText.replaceFirst("(?i)I want", "I'd like")
          + " 会更礼貌自然。";
    } else if (userText.toLowerCase().contains("how to")) {
      betterWay = "问「怎么做某事」更地道的说法是 “How can I …” 或 “Could you tell me how I can …”。";
    } else if (!userText.isEmpty()) {
      betterWay = "听起来不错！开头加一句 “Excuse me,” 或 “Hi there,” 会更自然。";
    }

    List<VocabHint> hints = new ArrayList<>();
    if (ctx.vocab() != null && !ctx.vocab().isEmpty()) {
      int base = ctx.userTurnCount();
      hints.add(toHint(ctx.vocab().get(base % ctx.vocab().size())));
      if (ctx.vocab().size() > 1) {
        hints.add(toHint(ctx.vocab().get((base + 1) % ctx.vocab().size())));
      }
    }
    return new LlmReply(assistantReply, betterWay, fix, hints);
  }

  @Override
  public Recap recap(ChatContext ctx) {
    int turns = ctx.userTurnCount();
    return new Recap(
        "你围绕「" + ctx.topic() + "」完成了 " + turns + " 轮对话练习，能清楚表达自己的需求。",
        List.of(
            "敢于开口，整段对话没有中断",
            "场景目标句型都用上了",
            "回答节奏流畅，没有长时间停顿"),
        List.of(
            "试着把回答从 1 句扩展到 2 句：观点 + 理由",
            "复习本轮收藏的生词，明天会安排巩固"));
  }

  @Override
  public String companionGreeting(String companionName, String fallback, String cefr,
                                  List<String> memories) {
    if (memories != null && !memories.isEmpty()) {
      String last = memories.get(memories.size() - 1);
      return "Hey! Good to see you again — last time you told me: \"" + last
          + "\". What's new since then?";
    }
    return fallback;
  }

  @Override
  public List<String> extractFacts(String userText, List<String> knownFacts) {
    List<String> facts = new ArrayList<>();
    String text = userText == null ? "" : userText.trim();
    String lower = text.toLowerCase();
    java.util.regex.Matcher m = java.util.regex.Pattern
        .compile("(?:my name is|i am|i'm)\\s+([a-z]{2,20})\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
        .matcher(text);
    if (m.find() && !List.of("from", "going", "trying", "here", "not", "just", "really", "so")
        .contains(m.group(1).toLowerCase())) {
      facts.add("User's name is " + m.group(1).substring(0, 1).toUpperCase()
          + m.group(1).substring(1));
    }
    m = java.util.regex.Pattern.compile("i (?:really )?(?:like|love|enjoy)\\s+([^.,!?]{3,40})", java.util.regex.Pattern.CASE_INSENSITIVE)
        .matcher(text);
    if (m.find()) {
      facts.add("User likes " + m.group(1).trim());
    }
    m = java.util.regex.Pattern.compile("i (?:work as a|work as an|work at|work in|am a|'m a)\\s+([^.,!?]{3,40})", java.util.regex.Pattern.CASE_INSENSITIVE)
        .matcher(text);
    if (m.find()) {
      facts.add("User works as " + m.group(1).trim());
    }
    m = java.util.regex.Pattern.compile("(?:i am|i'm) (?:studying|learning|preparing for)\\s+([^.,!?]{3,40})", java.util.regex.Pattern.CASE_INSENSITIVE)
        .matcher(text);
    if (m.find()) {
      facts.add("User is studying " + m.group(1).trim());
    }
    if (lower.contains("my job") || lower.contains("my boss") || lower.contains("my company")) {
      facts.add("User has talked about their job recently");
    }
    return facts;
  }

  private String scriptedReply(ChatContext ctx) {
    // 陪练模式：人设口吻的反应句轮换，模拟真实聊天节奏
    if (ctx.companion()) {
      return CHATTER_LINES.get(Math.max(0, ctx.userTurnCount() - 1) % CHATTER_LINES.size());
    }
    // 自由聊天：不按脚本，轮流抛出自然的追问，让对话持续
    if ("自由聊天".equals(ctx.topic())) {
      return FREE_TALK_LINES.get(Math.max(0, ctx.userTurnCount() - 1) % FREE_TALK_LINES.size());
    }
    List<String> lines = ctx.aiLines();
    if (lines == null || lines.isEmpty()) {
      return "Great job! Tell me more.";
    }
    // 开场白已占用第 0 条，第 1 轮用户发言后从第 1 条开始推进
    int turn = Math.max(1, ctx.userTurnCount());
    return lines.get(turn % lines.size());
  }

  private LlmClient.GrammarFix grammarFix(String text) {
    if (text.contains(" i ") || (text.startsWith("i ") && !text.startsWith("I "))) {
      return new LlmClient.GrammarFix(text, text.replaceFirst("\\bi\\b", "I"),
          "英文中的「我」任何时候都要大写为 I。");
    }
    if (text.toLowerCase().startsWith("i am agree")) {
      return new LlmClient.GrammarFix(text, "I agree", "agree 是动词，前面不需要 be 动词 am/is/are。");
    }
    return null;
  }

  private LlmClient.VocabHint toHint(ScenarioVocabEntity v) {
    return new LlmClient.VocabHint(v.getWord(), v.getMeaningZh());
  }

  private void sleep() {
    try {
      Thread.sleep(TYPE_DELAY_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
