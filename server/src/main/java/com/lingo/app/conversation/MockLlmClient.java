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
    String betterWay = null;
    if (userText.toLowerCase().contains("i want")) {
      betterWay = "服务场景里说 " + userText.replaceFirst("(?i)I want", "I'd like")
          + " 会更礼貌自然。";
    } else if (userText.toLowerCase().contains("how to")) {
      betterWay = "问「怎么做某事」更地道的说法是 “How can I …” 或 “Could you tell me how I can …”。";
    } else if (!userText.isEmpty()) {
      betterWay = "听起来不错！开头加一句 “Excuse me,” 或 “Hi there,” 会更自然。";
    }

    LlmClient.GrammarFix fix = grammarFix(userText);

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

  private String scriptedReply(ChatContext ctx) {
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
