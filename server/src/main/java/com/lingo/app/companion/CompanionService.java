package com.lingo.app.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingo.app.common.ApiException;
import com.lingo.app.companion.mapper.CompanionMemoryMapper;
import com.lingo.app.conversation.ConversationEntity;
import com.lingo.app.conversation.LlmClient;
import com.lingo.app.conversation.mapper.ConversationMapper;
import com.lingo.app.conversation.mapper.MessageMapper;
import com.lingo.app.conversation.MessageEntity;
import com.lingo.app.user.UserEntity;
import com.lingo.app.user.UserProfileEntity;
import com.lingo.app.user.mapper.UserMapper;
import com.lingo.app.user.mapper.UserProfileMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 陪练：人设化 + 跨会话记忆的口语搭子。
 * 同一陪练在对话未结束前可以接着上次继续聊，结束后下一轮会带着记忆开场。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanionService {

  private static final int MAX_FACTS = 30;

  private final CompanionMemoryMapper memoryMapper;
  private final ConversationMapper conversationMapper;
  private final MessageMapper messageMapper;
  private final UserProfileMapper profileMapper;
  private final UserMapper userMapper;
  private final LlmClient llmClient;
  private final ObjectMapper objectMapper;
  private final com.lingo.app.study.StudyService studyService;

  /** 陪练列表：人设 + 每个陪练记得你的事 */
  public List<CompanionView> list(Long userId) {
    List<CompanionView> views = new ArrayList<>();
    for (CompanionPersona p : CompanionPersona.ALL) {
      List<String> facts = readFacts(userId, p.key());
      views.add(new CompanionView(p.key(), p.name(), p.nameZh(), p.avatar(), p.tagline(),
          p.styleLabel(), facts.size(), previewOf(facts)));
    }
    return views;
  }

  /** 开始（或继续）与某个陪练的对话：同陪练存在未结束会话时直接续上 */
  @Transactional
  public StartResult start(Long userId, String companionKey) {
    CompanionPersona persona = CompanionPersona.byKey(companionKey);
    if (persona == null) {
      throw ApiException.badRequest("没有这个陪练");
    }

    ConversationEntity conv = conversationMapper.selectOne(
        new LambdaQueryWrapper<ConversationEntity>()
            .eq(ConversationEntity::getUserId, userId)
            .eq(ConversationEntity::getMode, "companion")
            .eq(ConversationEntity::getCompanionKey, companionKey)
            .eq(ConversationEntity::getStatus, "active")
            .last("limit 1"));
    boolean continued = conv != null;
    if (conv == null) {
      conv = new ConversationEntity();
      conv.setUserId(userId);
      conv.setMode("companion");
      conv.setCompanionKey(companionKey);
      conv.setStatus("active");
      conv.setMsgCount(1);
      conversationMapper.insert(conv);

      List<String> facts = readFacts(userId, companionKey);
      String cefr = cefrOf(userId);
      String greeting = llmClient.companionGreeting(persona.name(), persona.greetingFallback(),
          cefr, facts);
      MessageEntity greetingMsg = saveMessage(conv.getId(), 0, "assistant", greeting, null);
      studyService.record(userId, "dialog", 1, 1);
      return new StartResult(conv.getId(), toView(persona, facts),
          List.of(ChatServiceView.of(greetingMsg)), false);
    }

    List<MessageEntity> msgs = messageMapper.selectList(
        new LambdaQueryWrapper<MessageEntity>()
            .eq(MessageEntity::getConversationId, conv.getId())
            .orderByAsc(MessageEntity::getIdx));
    return new StartResult(conv.getId(), toView(persona, readFacts(userId, companionKey)),
        msgs.stream().map(ChatServiceView::of).toList(), true);
  }

  public List<String> memory(Long userId, String companionKey) {
    return readFacts(userId, companionKey);
  }

  /** 忘掉一条记忆（用户可随时管理陪练记得什么） */
  @Transactional
  public List<String> forget(Long userId, String companionKey, String fact) {
    List<String> facts = readFacts(userId, companionKey);
    facts.removeIf(f -> f.equalsIgnoreCase(fact));
    saveFacts(userId, companionKey, facts);
    return facts;
  }

  /** 每轮用户发言后抽取值得长期记住的事实（幂等合并，失败不影响对话） */
  public void updateMemory(Long userId, String companionKey, String userText) {
    try {
      if (userText == null || userText.trim().length() < 6) {
        return;
      }
      List<String> known = readFacts(userId, companionKey);
      List<String> extracted = llmClient.extractFacts(userText, known);
      if (extracted.isEmpty()) {
        return;
      }
      List<String> merged = new ArrayList<>(known);
      boolean changed = false;
      for (String fact : extracted) {
        if (fact == null || fact.isBlank() || fact.length() > 120) {
          continue;
        }
        boolean dup = merged.stream().anyMatch(f -> f.trim().equalsIgnoreCase(fact.trim()));
        if (!dup) {
          merged.add(fact.trim());
          changed = true;
        }
      }
      while (merged.size() > MAX_FACTS) {
        merged.remove(0);
      }
      if (changed) {
        saveFacts(userId, companionKey, merged);
      }
    } catch (Exception e) {
      log.warn("companion memory update failed: user={}", userId, e);
    }
  }

  // ---------- internals ----------

  private CompanionView toView(CompanionPersona p, List<String> facts) {
    return new CompanionView(p.key(), p.name(), p.nameZh(), p.avatar(), p.tagline(),
        p.styleLabel(), facts.size(), previewOf(facts));
  }

  private List<String> previewOf(List<String> facts) {
    return facts.size() > 3 ? facts.subList(facts.size() - 3, facts.size()) : facts;
  }

  private String cefrOf(Long userId) {
    UserProfileEntity profile = profileMapper.selectOne(
        new LambdaQueryWrapper<UserProfileEntity>()
            .eq(UserProfileEntity::getUserId, userId));
    return profile == null ? null : profile.getCefrLevel();
  }

  public String nicknameOf(Long userId) {
    UserEntity user = userMapper.selectById(userId);
    if (user == null || user.getNickname() == null) {
      return null;
    }
    String nick = user.getNickname().trim();
    // 游客昵称不是真名，不喂给 prompt
    if (nick.isEmpty() || nick.startsWith("访客") || nick.toLowerCase(Locale.ROOT).startsWith("guest")) {
      return null;
    }
    return nick;
  }

  public List<String> readFacts(Long userId, String companionKey) {
    CompanionMemoryEntity row = memoryMapper.selectOne(
        new LambdaQueryWrapper<CompanionMemoryEntity>()
            .eq(CompanionMemoryEntity::getUserId, userId)
            .eq(CompanionMemoryEntity::getCompanionKey, companionKey));
    if (row == null || row.getMemoryJson() == null || row.getMemoryJson().isBlank()) {
      return new ArrayList<>();
    }
    try {
      return objectMapper.readValue(row.getMemoryJson(), new TypeReference<List<String>>() {
      });
    } catch (Exception e) {
      log.warn("bad companion memory json: user={}", userId);
      return new ArrayList<>();
    }
  }

  private void saveFacts(Long userId, String companionKey, List<String> facts) {
    CompanionMemoryEntity row = memoryMapper.selectOne(
        new LambdaQueryWrapper<CompanionMemoryEntity>()
            .eq(CompanionMemoryEntity::getUserId, userId)
            .eq(CompanionMemoryEntity::getCompanionKey, companionKey));
    if (row == null) {
      row = new CompanionMemoryEntity();
      row.setUserId(userId);
      row.setCompanionKey(companionKey);
      row.setUpdatedAt(LocalDateTime.now());
      row.setMemoryJson(writeJson(facts));
      memoryMapper.insert(row);
    } else {
      row.setMemoryJson(writeJson(facts));
      row.setUpdatedAt(LocalDateTime.now());
      memoryMapper.updateById(row);
    }
  }

  private String writeJson(List<String> facts) {
    try {
      return objectMapper.writeValueAsString(facts);
    } catch (Exception e) {
      return "[]";
    }
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

  public record CompanionView(String key, String name, String nameZh, String avatar,
                              String tagline, String styleLabel, int memoryCount,
                              List<String> memoryPreview) {
  }

  /** 复用 conversation 包的消息视图结构 */
  public record ChatServiceView(Long id, String role, String content) {
    public static ChatServiceView of(MessageEntity m) {
      return new ChatServiceView(m.getId(), m.getRole(), m.getContent());
    }
  }

  public record StartResult(Long conversationId, CompanionView companion,
                            List<ChatServiceView> messages, boolean continued) {
  }
}
