package com.lingo.app.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingo.app.common.ApiResponse;
import com.lingo.app.user.UserEntity;
import com.lingo.app.user.UserProfileEntity;
import com.lingo.app.user.mapper.UserMapper;
import com.lingo.app.user.mapper.UserProfileMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理（只读）：列表不返回 openId/unionId/phone 等敏感标识，降低后台泄露面。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminUserController {

  private final UserMapper userMapper;
  private final UserProfileMapper userProfileMapper;

  @GetMapping("/users")
  public ApiResponse<Map<String, Object>> users(
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long size,
      @RequestParam(required = false) String q) {
    LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
    if (q != null && !q.isBlank()) {
      wrapper.and(w -> w.like(UserEntity::getNickname, q.trim())
          .or().like(UserEntity::getOpenId, q.trim()));
    }
    wrapper.orderByDesc(UserEntity::getId);
    Page<UserEntity> result =
        userMapper.selectPage(Page.of(page, Math.min(size, 100)), wrapper);

    List<Long> userIds = result.getRecords().stream().map(UserEntity::getId).toList();
    Map<Long, UserProfileEntity> profiles = userIds.isEmpty() ? Map.of()
        : userProfileMapper.selectList(new LambdaQueryWrapper<UserProfileEntity>()
                .in(UserProfileEntity::getUserId, userIds))
            .stream()
            .collect(Collectors.toMap(UserProfileEntity::getUserId, Function.identity()));

    List<Map<String, Object>> rows = result.getRecords().stream().map(u -> {
      UserProfileEntity p = profiles.get(u.getId());
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", u.getId());
      row.put("nickname", u.getNickname());
      row.put("guest", Boolean.TRUE.equals(u.getIsGuest()));
      row.put("createdAt", u.getCreatedAt());
      if (p != null) {
        row.put("goalTrack", p.getGoalTrack());
        row.put("cefrLevel", p.getCefrLevel());
        row.put("streakDays", p.getStreakDays());
        row.put("xp", p.getXp());
        row.put("lastStudyDate", p.getLastStudyDate());
      }
      return row;
    }).toList();

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("total", result.getTotal());
    body.put("page", result.getCurrent());
    body.put("size", result.getSize());
    body.put("rows", rows);
    return ApiResponse.ok(body);
  }
}
