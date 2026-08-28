package com.lingo.app.conversation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingo.app.common.ApiException;
import com.lingo.app.conversation.LlmClient.ChatContext;
import com.lingo.app.conversation.mapper.ConversationMapper;
import com.lingo.app.conversation.mapper.MessageMapper;
import com.lingo.app.scenario.ScenarioEntity;
import com.lingo.app.scenario.ScenarioService;
import com.lingo.app.scenario.ScenarioVocabEntity;
import com.lingo.app.study.StudyService;
import com.lingo.app.user.UserProfileEntity;
import com.lingo.app.user.mapper.UserProfileMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

  private final ConversationMapper conversationMapper;
  private final MessageMapper messageMapper;
  private final ScenarioService scenarioService;
  private final UserProfileMapper profileMapper;
  private final LlmClient llmClient;
  private final ObjectMapper objectMapper;
  private final StudyService studyService;
  private final com.lingo.app.course.CourseService courseService;

  private static final ExecutorService SSE_POOL = Executors.newFixedThreadPool(32, r -> {
    Thread t = new Thread(r, "sse-worker");
    t.setDaemon(true);
    return t;
  });

  @Transactional
  public CreateResult create(Long userId, Long scenarioId) {
    ScenarioEntity scenario = scenarioService.require(scenarioId);
    ConversationEntity conv = new ConversationEntity();
    conv.setUserId(userId);
    conv.setScenarioId(scenarioId);
    conv.setStatus("active");
    conv.setMsgCount(1);
    conversationMapper.insert(conv);

    List<String> aiLines = scenarioService.aiLines(scenarioId);
    String greeting = aiLines.isEmpty() ? "Hi! I'm your speaking trainer. Shall we begin?"
        : aiLines.get(0);
    MessageEntity greetingMsg = saveMessage(conv.getId(), 0, "assistant", greeting, null);
    studyService.record(userId, "scenario", 1, 1);

    var detail = scenarioService.detail(scenarioId);
    return new CreateResult(conv.getId(), detail.titleZh(), detail.titleEn(), detail.cefr(),
        detail.roleSetting(), detail.vocab(), List.of(toView(greetingMsg)));
  }

  public List<MessageView> messages(Long userId, Long conversationId) {
    ConversationEntity conv = requireOwned(userId, conversationId);
    return messageMapper.selectList(new LambdaQueryWrapper<MessageEntity>()
            .eq(MessageEntity::getConversationId, conv.getId())
            .orderByAsc(MessageEntity::getIdx))
        .stream().map(this::toView).toList();
  }

  /** 对话详情：场景信息 + 全部消息，用于刷新后恢复聊天页 */
  public ConversationDetail detail(Long userId, Long conversationId) {
    ConversationEntity conv = requireOwned(userId, conversationId);
    ScenarioEntity scenario = scenarioService.require(conv.getScenarioId());
    return new ConversationDetail(conv.getId(), scenario.getId(), scenario.getTitleZh(),
        scenario.getTitleEn(), scenario.getCefr(), scenario.getRoleSetting(),
        conv.getStatus(), conv.getMsgCount(), messages(userId, conversationId));
  }

  @Transactional
  public MessageView reply(Long userId, Long conversationId, String text) {
    ConversationEntity conv = requireOwnedActive(userId, conversationId);
    ChatContext ctx = buildContext(conv, text);
    int nextIdx = conv.getMsgCount();

    saveMessage(conv.getId(), nextIdx, "user", text, null);
    String replyText = generateReply(ctx);
    LlmClient.LlmReply feedback = llmClient.feedback(ctx, replyText);
    MessageEntity aiMsg = saveMessage(conv.getId(), nextIdx + 1, "assistant", replyText,
        writeJson(feedback));

    conv.setMsgCount(nextIdx + 2);
    conv.setUpdatedAt(LocalDateTime.now());
    conversationMapper.updateById(conv);
    return toView(aiMsg);
  }

  /** SSE 流式：event=start → delta* → meta → done */
  public SseEmitter streamReply(Long userId, Long conversationId, String text) {
    ConversationEntity conv = requireOwnedActive(userId, conversationId);
    ChatContext ctx = buildContext(conv, text);
    SseEmitter emitter = new SseEmitter(120_000L);

    SSE_POOL.submit(() -> {
      try {
        int nextIdx = conv.getMsgCount();
        send(emitter, "start", objectMapper.writeValueAsString(
            java.util.Map.of("conversationId", String.valueOf(conv.getId()))));

        saveMessage(conv.getId(), nextIdx, "user", text, null);

        StringBuilder buf = new StringBuilder();
        llmClient.streamReply(ctx, delta -> {
          try {
            send(emitter, "delta", objectMapper.writeValueAsString(java.util.Map.of("text", delta)));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
        String replyText = buf.toString().trim();
        if (replyText.isEmpty()) {
          replyText = "Could you say that again?";
        }

        LlmClient.LlmReply feedback = llmClient.feedback(ctx, replyText);
        MessageEntity aiMsg = saveMessage(conv.getId(), nextIdx + 1, "assistant", replyText,
            writeJson(feedback));

        conv.setMsgCount(nextIdx + 2);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);

        send(emitter, "meta", writeJson(toView(aiMsg)));
        send(emitter, "done", "{}");
        emitter.complete();
      } catch (Exception e) {
        log.error("stream reply failed: conv={}", conversationId, e);
        try {
          send(emitter, "error", objectMapper.writeValueAsString(
              java.util.Map.of("message", "AI 回复失败，请重试")));
        } catch (Exception ignored) {
          // 连接已断开时无法再推错误事件
        }
        emitter.complete();
      }
    });
    return emitter;
  }

  @Transactional
  public FinishResult finish(Long userId, Long conversationId) {
    ConversationEntity conv = requireOwned(userId, conversationId);
    var vocab = scenarioService.detail(conv.getScenarioId()).vocab();

    if ("finished".equals(conv.getStatus()) && conv.getCoachJson() != null) {
      return new FinishResult(readRecap(conv), vocab);
    }

    ChatContext ctx = buildContext(conv, null);
    LlmClient.Recap recap = llmClient.recap(ctx);
    conv.setStatus("finished");
    conv.setAiSummary(recap.summary());
    conv.setCoachJson(writeJson(recap));
    conv.setUpdatedAt(LocalDateTime.now());
    conversationMapper.updateById(conv);

    studyService.record(userId, "dialog", Math.max(1, conv.getMsgCount() / 4), 1);
    courseService.completeLesson(userId, "dialog", conv.getScenarioId(), null);
    return new FinishResult(recap, vocab);
  }

  public List<HistoryItem> history(Long userId) {
    List<ConversationEntity> convs = conversationMapper.selectList(
        new LambdaQueryWrapper<ConversationEntity>()
            .eq(ConversationEntity::getUserId, userId)
            .orderByDesc(ConversationEntity::getId)
            .last("limit 30"));
    return convs.stream().map(c -> {
      String title = "";
      try {
        title = scenarioService.require(c.getScenarioId()).getTitleZh();
      } catch (Exception ignored) {
        // 场景可能被下架，标题留空即可
      }
      MessageEntity last = messageMapper.selectOne(new LambdaQueryWrapper<MessageEntity>()
          .eq(MessageEntity::getConversationId, c.getId())
          .orderByDesc(MessageEntity::getIdx)
          .last("limit 1"));
      return new HistoryItem(c.getId(), c.getScenarioId(), title, c.getStatus(),
          last == null ? "" : last.getContent(), c.getCreatedAt());
    }).toList();
  }

  // ---------- internals ----------

  private String generateReply(ChatContext ctx) {
    StringBuilder buf = new StringBuilder();
    llmClient.streamReply(ctx, buf::append);
    String reply = buf.toString().trim();
    return reply.isEmpty() ? "Could you say that again?" : reply;
  }

  private ChatContext buildContext(ConversationEntity conv, String userText) {
    ScenarioEntity scenario = scenarioService.require(conv.getScenarioId());
    UserProfileEntity profile = profileMapper.selectOne(
        new LambdaQueryWrapper<UserProfileEntity>()
            .eq(UserProfileEntity::getUserId, conv.getUserId()));
    List<MessageEntity> msgs = messageMapper.selectList(
        new LambdaQueryWrapper<MessageEntity>()
            .eq(MessageEntity::getConversationId, conv.getId())
            .orderByAsc(MessageEntity::getIdx));
    List<ChatContext.HistoryMsg> history = msgs.stream()
        .map(m -> new ChatContext.HistoryMsg(m.getRole(), m.getContent()))
        .toList();
    int userTurns = (int) msgs.stream().filter(m -> "user".equals(m.getRole())).count();
    List<ScenarioVocabEntity> vocab = scenarioService.vocabOf(scenario.getId());
    return new ChatContext(conv.getUserId(), conv.getId(), scenario.getTopic(),
        profile == null ? null : profile.getCefrLevel(), scenario.getRoleSetting(),
        scenarioService.aiLines(scenario.getId()), vocab, history, userText, userTurns);
  }

  private ConversationEntity requireOwned(Long userId, Long conversationId) {
    ConversationEntity conv = conversationMapper.selectById(conversationId);
    if (conv == null || !conv.getUserId().equals(userId)) {
      throw ApiException.notFound("对话不存在");
    }
    return conv;
  }

  private ConversationEntity requireOwnedActive(Long userId, Long conversationId) {
    ConversationEntity conv = requireOwned(userId, conversationId);
    if (!"active".equals(conv.getStatus())) {
      throw ApiException.badRequest("本轮对话已结束，重新开始一轮吧");
    }
    return conv;
  }

  private MessageEntity saveMessage(Long conversationId, int idx, String role, String content,
                                    String feedbackJson) {
    MessageEntity m = new MessageEntity();
    m.setConversationId(conversationId);
    m.setIdx(idx);
    m.setRole(role);
    m.setContent(content);
    m.setFeedbackJson(feedbackJson);
    messageMapper.insert(m);
    return m;
  }

  private MessageView toView(MessageEntity m) {
    LlmClient.LlmReply feedback = null;
    if (m.getFeedbackJson() != null && !m.getFeedbackJson().isBlank()) {
      try {
        feedback = objectMapper.readValue(m.getFeedbackJson(), LlmClient.LlmReply.class);
      } catch (Exception e) {
        log.warn("bad feedback json on message {}", m.getId());
      }
    }
    return new MessageView(m.getId(), m.getRole(), m.getContent(), feedback);
  }

  private LlmClient.Recap readRecap(ConversationEntity conv) {
    try {
      return objectMapper.readValue(conv.getCoachJson(), LlmClient.Recap.class);
    } catch (Exception e) {
      return new LlmClient.Recap(conv.getAiSummary(), List.of(), List.of());
    }
  }

  private String writeJson(Object o) {
    try {
      return objectMapper.writeValueAsString(o);
    } catch (Exception e) {
      return null;
    }
  }

  private void send(SseEmitter emitter, String event, String json) throws java.io.IOException {
    emitter.send(SseEmitter.event().name(event).data(json, MediaType.APPLICATION_JSON));
  }

  public record CreateResult(Long conversationId, String titleZh, String titleEn, String cefr,
                             String roleSetting, List<ScenarioService.VocabView> vocab,
                             List<MessageView> messages) {
  }

  public record MessageView(Long id, String role, String content, LlmClient.LlmReply feedback) {
  }

  public record HistoryItem(Long id, Long scenarioId, String titleZh, String status,
                            String lastMessage, LocalDateTime createdAt) {
  }

  public record ConversationDetail(Long id, Long scenarioId, String titleZh, String titleEn,
                                   String cefr, String roleSetting, String status, Integer msgCount,
                                   List<MessageView> messages) {
  }

  public record FinishResult(LlmClient.Recap recap, List<ScenarioService.VocabView> vocab) {
  }
}
