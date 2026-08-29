package com.lingo.app.conversation;

import com.lingo.app.scenario.ScenarioVocabEntity;
import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 抽象：MockLlmClient（无密钥默认）与 LangChainLlmClient（经 LangChain4j 接入
 * DeepSeek/GLM/Qwen 等 OpenAI 兼容模型）二选一。
 */
public interface LlmClient {

  /** 流式生成口语回复（纯文本，≤2 句），onDelta 逐段回调 */
  String streamReply(ChatContext ctx, Consumer<String> onDelta);

  /** 针对最终回复产出教学反馈（更地道说法 / 语法修正 / 生词提示） */
  LlmReply feedback(ChatContext ctx, String assistantReply);

  /** 对话结束后的教练复盘 */
  Recap recap(ChatContext ctx);

  /** AI 陪练的个性化开场白：结合人设与长期记忆 */
  String companionGreeting(String companionName, String fallback, String cefr,
                           List<String> memories);

  /** 从用户发言里抽取值得长期记住的事实（第三人称英文短句），无可记内容返回空列表 */
  List<String> extractFacts(String userText, List<String> knownFacts);

  record ChatContext(Long userId, Long conversationId, String topic, String cefr,
                     String roleSetting, List<String> aiLines, List<ScenarioVocabEntity> vocab,
                     List<HistoryMsg> history, String userText, int userTurnCount,
                     String mode, String companionName, String userName,
                     List<String> memories) {

    public boolean companion() {
      return "companion".equals(mode);
    }

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
