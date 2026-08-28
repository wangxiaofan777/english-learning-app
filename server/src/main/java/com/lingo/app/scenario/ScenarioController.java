package com.lingo.app.scenario;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingo.app.common.ApiResponse;
import com.lingo.app.common.UserContext;
import com.lingo.app.conversation.ConversationEntity;
import com.lingo.app.conversation.mapper.ConversationMapper;
import com.lingo.app.user.UserProfileEntity;
import com.lingo.app.user.mapper.UserProfileMapper;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

  private final ScenarioService scenarioService;
  private final ConversationMapper conversationMapper;
  private final UserProfileMapper profileMapper;

  @GetMapping
  public ApiResponse<?> list(@RequestParam(required = false) String track,
                             @RequestParam(defaultValue = "1") long page,
                             @RequestParam(defaultValue = "20") long size) {
    return ApiResponse.ok(scenarioService.list(track, page, size, practicedIds()));
  }

  @GetMapping("/recommended")
  public ApiResponse<?> recommended() {
    UserProfileEntity profile = profileMapper.selectOne(
        new LambdaQueryWrapper<UserProfileEntity>()
            .eq(UserProfileEntity::getUserId, UserContext.get()));
    String track = profile == null || profile.getGoalTrack() == null
        ? "daily" : profile.getGoalTrack();
    return ApiResponse.ok(scenarioService.recommend(track, practicedIds()));
  }

  @GetMapping("/free-talk")
  public ApiResponse<ScenarioService.ScenarioCard> freeTalk() {
    return ApiResponse.ok(scenarioService.freeTalk());
  }

  @GetMapping("/{id}")
  public ApiResponse<ScenarioService.ScenarioDetail> detail(@PathVariable Long id) {
    return ApiResponse.ok(scenarioService.detail(id));
  }

  private Set<Long> practicedIds() {
    Set<Long> ids = new HashSet<>();
    conversationMapper.selectList(new LambdaQueryWrapper<ConversationEntity>()
            .eq(ConversationEntity::getUserId, UserContext.get())
            .isNotNull(ConversationEntity::getScenarioId))
        .forEach(c -> ids.add(c.getScenarioId()));
    return ids;
  }
}
