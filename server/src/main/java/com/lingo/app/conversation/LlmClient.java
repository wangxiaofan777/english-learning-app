package com.lingo.app.conversation;

import com.lingo.app.scenario.ScenarioVocabEntity;
import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 抽象：MockLlmClient（无密钥默认）与 OpenAiCompatClient（DeepSeek/GLM/Qwen 等）二选一。
 */
public interface LlmClient {

  /** 流式生成口语回复（纯文本，≤2 句），onDelta 逐段回调 */
  String streamReply(ChatContext ctx, Consumer<String> onDelta);

  /** 针对最终回复产出教学反馈（更地道说法 / 语法修正 / 生词提示） */
  LlmReply feedback(ChatContext ctx, String assistantReply);

  /** 对话结束后的教练复盘 */
  Recap recap(ChatContext ctx);

  record ChatContext(Long userId, Long conversationId, String topic, String cefr,
                     String roleSetting, List<String> aiLines, List<ScenarioVocabEntity> vocab,
                     List<HistoryMsg> history, String userText, int userTurnCount) {

    public record HistoryMsg(String role, String content) {
    }
  }

  record LlmReply(String reply, String betterWay, GrammarFix grammarFix,
                  List<VocabHint> vocabHints) {
  }

  record GrammarFix(String original, String corrected, String explain) {
  }

  record VocabHint(String word, String meaningZh) {
  }

  record Recap(String summary, List<String> strengths, List<String> suggestions) {
  }
}
