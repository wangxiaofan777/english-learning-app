package com.lingo.app.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingo.app.admin.mapper.AdminStatsMapper;
import com.lingo.app.common.ApiResponse;
import com.lingo.app.conversation.mapper.ConversationMapper;
import com.lingo.app.conversation.mapper.MessageMapper;
import com.lingo.app.scenario.GenerationService;
import com.lingo.app.user.UserEntity;
import com.lingo.app.user.mapper.UserMapper;
import com.lingo.app.vocab.mapper.VocabEntryMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理后台仪表盘：全站关键数据的只读汇总 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminStatsController {

  private final GenerationService generationService;
  private final UserMapper userMapper;
  private final ConversationMapper conversationMapper;
  private final MessageMapper messageMapper;
  private final VocabEntryMapper vocabEntryMapper;
  private final AdminStatsMapper statsMapper;

  @GetMapping("/stats")
  public ApiResponse<Map<String, Object>> stats() {
    String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
    Map<String, Object> body = new LinkedHashMap<>();

    Map<String, Object> users = new LinkedHashMap<>();
    users.put("total", userMapper.selectCount(null));
    users.put("guest", userMapper.selectCount(
        new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getIsGuest, true)));
    users.put("wx", userMapper.selectCount(
        new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getIsGuest, false)));
    users.put("todayNew", userMapper.selectCount(
        new LambdaQueryWrapper<UserEntity>()
            .ge(UserEntity::getCreatedAt, LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay())));

    Map<String, Object> study = new LinkedHashMap<>();
    study.put("todayActive", statsMapper.todayActiveUsers(today));
    study.put("todayMinutes", statsMapper.todayMinutes(today));

    Map<String, Object> conversations = new LinkedHashMap<>();
    conversations.put("total", conversationMapper.selectCount(null));
    conversations.put("messages", messageMapper.selectCount(null));

    body.put("users", users);
    body.put("study", study);
    body.put("vocabTotal", vocabEntryMapper.selectCount(null));
    body.put("conversations", conversations);
    body.put("content", generationService.contentStatus());
    return ApiResponse.ok(body);
  }
}
